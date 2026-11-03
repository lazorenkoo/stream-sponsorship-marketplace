package com.streambid.channel;

import com.streambid.platform.StreamingPlatform;
import com.streambid.user.UserAccount;
import com.streambid.user.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Base64;
import com.streambid.platform.twitch.TwitchCredentialRefresh;
import com.streambid.platform.twitch.TwitchCodeExchange;
import com.streambid.platform.twitch.TwitchReauthorizationRequiredException;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.*;
import com.streambid.platform.ChannelInfo;
import com.streambid.platform.twitch.TwitchConnectionStore;
import com.streambid.platform.twitch.TwitchUserTokens;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
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
@Import(ChannelRepositoryTest.StoreConfiguration.class)
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

    @TestConfiguration
    static class StoreConfiguration {
        @Bean
        TwitchCodeExchange tokenExchange() {
            return mock(TwitchCodeExchange.class);
        }
        @Bean
        TwitchCredentialRefresh refresh(ChannelCredentialsRepository credentials, TwitchCodeExchange exchange) {
            return new TwitchCredentialRefresh(credentials,
                    new TokenCipher(Base64.getEncoder().encodeToString(new byte[32])), exchange);
        }
        @Bean
        TwitchConnectionStore store(ChannelRepository channels, ChannelCredentialsRepository credentials, UserRepository users) {
            return new TwitchConnectionStore(channels, credentials, users,
                    new TokenCipher(Base64.getEncoder().encodeToString(new byte[32])), Clock.systemUTC());
        }
    }

    @Autowired
    TwitchConnectionStore store;

    @Autowired
    DSLContext database;

    @Autowired TwitchCredentialRefresh refreshService;
    @Autowired TwitchCodeExchange exchange;

    @BeforeEach
    void resetExchangeMock() {
        reset(exchange);
    }

    private ConnectedChannel channelForRefresh(String email) {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, email, "hash", "Refresh"));
        return store.save(owner, new ChannelInfo(email, "Refresh", null),
                new TwitchUserTokens("old-access", "old-refresh", java.time.Instant.now().plusSeconds(3600)));
    }

    @Test
    void storesRotationOnceAndIgnoresStaleRejectedToken() {
        var channel = channelForRefresh("rotation@example.com");
        var rotated = new TwitchUserTokens("new-access", "new-refresh",
                java.time.Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        when(exchange.refresh("old-refresh")).thenReturn(rotated);
        assertThat(refreshService.refreshRejected(channel.id(), "old-access")).isEqualTo(rotated);
        assertThat(refreshService.refreshRejected(channel.id(), "old-access")).isEqualTo(rotated);
        verify(exchange, times(1)).refresh("old-refresh");
        var cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
        var saved = credentials.find(channel.id()).orElseThrow();
        assertThat(cipher.decrypt(saved.refreshTokenCiphertext(), channel.id() + ":refresh")).isEqualTo("new-refresh");
    }

    @Test
    void revokedRefreshRemovesCredentialsAndStopsFurtherAttempts() {
        var channel = channelForRefresh("revoked@example.com");
        when(exchange.refresh("old-refresh")).thenThrow(new TwitchReauthorizationRequiredException());
        assertThatThrownBy(() -> refreshService.refreshRejected(channel.id(), "old-access"))
                .isInstanceOf(TwitchReauthorizationRequiredException.class);
        assertThat(credentials.find(channel.id())).isEmpty();
        assertThatThrownBy(() -> refreshService.refreshRejected(channel.id(), "old-access"))
                .isInstanceOf(TwitchReauthorizationRequiredException.class);
        verify(exchange, times(1)).refresh("old-refresh");
    }
    @Test
    void reconnectKeepsChannelIdAndDoesNotTransferOwnership() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        users.save(new UserAccount(owner, "connect@example.com", "hash", "Connect"));
        users.save(new UserAccount(other, "blocked@example.com", "hash", "Blocked"));
        var identity = new ChannelInfo("verified", "Streamer", null);
        var tokens = new TwitchUserTokens("access", "refresh", java.time.Instant.now().plusSeconds(3600));
        var first = store.save(owner, identity, tokens);
        var second = store.save(owner, identity, tokens);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(channels.findByUser(owner)).hasSize(1);
        assertThat(users.findRoles(owner)).contains("STREAMER");
        assertThatThrownBy(() -> store.save(other, identity, tokens)).isInstanceOf(ResponseStatusException.class);
        assertThat(channels.findByUser(other)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void encryptionFailureRollsBackNewChannel() {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "rollback@example.com", "hash", "Rollback"));
        try {
            var tokens = new TwitchUserTokens("access", "", java.time.Instant.now().plusSeconds(3600));
            assertThatThrownBy(() -> store.save(owner, new ChannelInfo("rollback-channel", "Rollback", null), tokens))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(channels.findByUser(owner)).isEmpty();
            assertThat(users.findRoles(owner)).isEmpty();
        } finally {
            database.deleteFrom(DSL.table(DSL.name("users")))
                    .where(DSL.field(DSL.name("id"), UUID.class).eq(owner)).execute();
        }
    }
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
