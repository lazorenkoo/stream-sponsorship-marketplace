package com.streambid.channel;

import com.streambid.platform.StreamingPlatform;
import com.streambid.user.UserAccount;
import com.streambid.user.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Base64;
import com.streambid.security.TokenCipher;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class ChannelRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    ChannelRepository channels;

    @Autowired
    UserRepository users;

    @Autowired
    MockMvc mvc;

    @Autowired
    ChannelCredentialsRepository credentials;

    @Test
    void storesOnlyEncryptedCredentialsBoundToChannel() {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "encrypted@example.com", "hash", "Encrypted"));
        var now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        var channel = new ConnectedChannel(UUID.randomUUID(), owner, StreamingPlatform.TWITCH,
                "encrypted-channel", "Encrypted", now);
        channels.saveVerified(channel);
        var cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
        String access = cipher.encrypt("private-access", channel.id() + ":access");
        String refresh = cipher.encrypt("private-refresh", channel.id() + ":refresh");
        var saved = new ChannelCredentials(channel.id(), access, refresh, now.plusHours(1), now);
        credentials.save(saved);
        var loaded = credentials.find(channel.id()).orElseThrow();
        assertThat(loaded).isEqualTo(saved);
        assertThat(loaded.accessTokenCiphertext()).doesNotContain("private-access");
        assertThat(loaded.refreshTokenCiphertext()).doesNotContain("private-refresh");
        assertThat(cipher.decrypt(loaded.accessTokenCiphertext(), channel.id() + ":access")).isEqualTo("private-access");
        assertThat(loaded.toString()).isEqualTo("ChannelCredentials[redacted]");
    }
    @Test
    void channelApiUsesAuthenticatedOwnerInsteadOfQueryParameter() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        users.save(new UserAccount(owner, "mine@example.com", "hash", "Mine"));
        users.save(new UserAccount(other, "theirs@example.com", "hash", "Theirs"));
        var time = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        channels.saveVerified(new ConnectedChannel(UUID.randomUUID(), owner, StreamingPlatform.TWITCH,
                "mine", "My channel", time));
        channels.saveVerified(new ConnectedChannel(UUID.randomUUID(), other, StreamingPlatform.TWITCH,
                "theirs", "Other channel", time));
        mvc.perform(get("/api/channels").param("userId", other.toString()).with(user("mine@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].externalChannelId").value("mine"));
        mvc.perform(get("/api/channels").with(user("theirs@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].externalChannelId").value("theirs"));
    }

    @Test
    void channelApiRejectsAnonymousRequests() throws Exception {
        mvc.perform(get("/api/channels")).andExpect(status().isUnauthorized());
    }
    @Test
    void readsOnlyOwnersChannelsAndRejectsDuplicateOwnership() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        users.save(new UserAccount(owner, "owner@example.com", "hash", "Owner"));
        users.save(new UserAccount(other, "other@example.com", "hash", "Other"));
        var channel = new ConnectedChannel(UUID.randomUUID(), owner, StreamingPlatform.TWITCH,
                "opaque-id", "Streamer", OffsetDateTime.now(ZoneOffset.UTC).withNano(0));
        channels.saveVerified(channel);
        assertThat(channels.findByUser(owner)).containsExactly(channel);
        assertThat(channels.findByUser(other)).isEmpty();
        var duplicate = new ConnectedChannel(UUID.randomUUID(), other, StreamingPlatform.TWITCH,
                "opaque-id", "Streamer", channel.verifiedAt());
        assertThatThrownBy(() -> channels.saveVerified(duplicate)).isInstanceOf(DuplicateKeyException.class);
    }
}
