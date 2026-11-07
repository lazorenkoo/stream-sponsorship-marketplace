package com.streambid.creative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.streambid.user.UserAccount;
import com.streambid.user.UserRepository;
import com.streambid.user.UserRole;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(CreativeUploadIntegrationTest.StorageConfiguration.class)
class CreativeUploadIntegrationTest {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17");
    @Container static final GenericContainer<?> STORAGE = new GenericContainer<>("pgsty/silo:RELEASE.2026-09-16T00-00-00Z")
            .withEnv("MINIO_ROOT_USER", "streambid-test").withEnv("MINIO_ROOT_PASSWORD", "streambid-test-password")
            .withCommand("server", "/data").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
    }

    @TestConfiguration
    static class StorageConfiguration {
        @Bean(destroyMethod = "close")
        S3Client testCreativeClient() {
            var client = S3Client.builder().endpointOverride(URI.create("http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(9000)))
                    .forcePathStyle(true).region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("streambid-test", "streambid-test-password")))
                    .httpClientBuilder(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder())
                    .overrideConfiguration(config -> config.apiCallTimeout(java.time.Duration.ofSeconds(30))).build();
            client.createBucket(request -> request.bucket("streambid-uploads"));
            return client;
        }

        @Bean
        S3CreativeStorage testCreativeStorage(S3Client testCreativeClient) {
            return new S3CreativeStorage(testCreativeClient, "streambid-uploads");
        }
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AdCreativeRepository creatives;
    @Autowired S3CreativeStorage storage;
    @Autowired org.jooq.DSLContext database;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    @Test
    void multipartUploadPersistsRealObjectAndPrivateMetadata() throws Exception {
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        users.save(new UserAccount(owner, "real-upload@example.com", "hash", "Advertiser"));
        users.addRole(owner, UserRole.ADVERTISER);
        users.save(new UserAccount(other, "other-upload@example.com", "hash", "Other"));
        byte[] bytes = java.util.Base64.getDecoder().decode("UklGRh4AAABXRUJQVlA4TBEAAAAvAUAAAAdQslKUtv+BiOh/AAA=");
        try {
            var result = mvc.perform(multipart("/api/creatives").file(new MockMultipartFile("file", "banner.jpg", "image/jpeg", bytes))
                    .param("title", "Real upload").with(user("real-upload@example.com")).with(csrf()))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
            UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
            assertThat(creatives.findOwned(id, owner)).isPresent();
            assertThat(storage.read(id, CreativeFormat.WEBP)).containsExactly(bytes);
            mvc.perform(get("/api/creatives/" + id + "/content").with(user("real-upload@example.com")))
                    .andExpect(status().isOk()).andExpect(content().bytes(bytes)).andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get("/api/creatives/" + id + "/content").with(user("other-upload@example.com")))
                    .andExpect(status().isNotFound());
        } finally {
            for (var creative : creatives.listOwned(owner, null, 100)) {
                storage.delete(creative.id(), creative.format());
                database.execute("DELETE FROM ad_creatives WHERE id = ?", creative.id());
            }
            database.execute("DELETE FROM users WHERE id IN (?, ?)", owner, other);
        }
    }
}
