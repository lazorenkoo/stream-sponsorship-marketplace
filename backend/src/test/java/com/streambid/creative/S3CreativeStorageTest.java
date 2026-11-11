package com.streambid.creative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Testcontainers
class S3CreativeStorageTest {
    @Container
    static final GenericContainer<?> STORAGE = new GenericContainer<>("pgsty/silo:RELEASE.2026-09-16T00-00-00Z")
            .withEnv("MINIO_ROOT_USER", "streambid-test")
            .withEnv("MINIO_ROOT_PASSWORD", "streambid-test-password")
            .withCommand("server", "/data").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Test
    void privateObjectsRoundTripWithServerMimeTypeAndCanBeDeleted() throws Exception {
        var endpoint = URI.create("http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(9000));
        try (var client = S3Client.builder().endpointOverride(endpoint).forcePathStyle(true).region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("streambid-test", "streambid-test-password")))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(30))).build()) {
            String bucket = "streambid-creatives";
            client.createBucket(request -> request.bucket(bucket));
            var storage = new S3CreativeStorage(client, bucket);
            UUID id = UUID.randomUUID();
            byte[] bytes = java.util.Base64.getDecoder().decode("UklGRh4AAABXRUJQVlA4TBEAAAAvAUAAAAdQslKUtv+BiOh/AAA=");
            storage.put(id, CreativeFormat.WEBP, bytes);
            assertThat(storage.read(id, CreativeFormat.WEBP)).containsExactly(bytes);
            String key = "creatives/" + id + ".webp";
            var metadata = client.headObject(request -> request.bucket(bucket).key(key));
            assertThat(metadata.contentType()).isEqualTo("image/webp");
            assertThat(metadata.contentLength()).isEqualTo(bytes.length);
            try (var http = HttpClient.newHttpClient()) {
                var response = http.send(HttpRequest.newBuilder(endpoint.resolve("/" + bucket + "/" + key)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
                assertThat(response.statusCode()).isEqualTo(403);
            }
            assertThatThrownBy(() -> storage.put(UUID.randomUUID(), CreativeFormat.PNG, new byte[10485761]))
                    .isInstanceOf(IllegalArgumentException.class);
            storage.delete(id, CreativeFormat.WEBP);
            assertThatThrownBy(() -> storage.read(id, CreativeFormat.WEBP)).isInstanceOf(S3Exception.class)
                    .satisfies(exception -> assertThat(((S3Exception) exception).statusCode()).isEqualTo(404));
            storage.delete(id, CreativeFormat.WEBP);
        }
    }
}
