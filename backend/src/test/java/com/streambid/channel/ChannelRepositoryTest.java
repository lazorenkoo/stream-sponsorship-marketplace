package com.streambid.channel;

import com.streambid.platform.StreamingPlatform;
import com.streambid.user.UserAccount;
import com.streambid.user.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Base64;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.streambid.platform.PlatformUnavailableException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    @Autowired ChannelDisconnectService disconnectService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void disconnectWaitsForRefreshAndRemovesRotatedCredentials() throws Exception {
        String email = "disconnect-refresh@example.com";
        var channel = channelForRefresh(email);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var disconnectStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var rotated = new TwitchUserTokens("rotated-access", "rotated-refresh",
                java.time.Instant.now().plusSeconds(3600));
        when(exchange.refresh("old-refresh")).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Refresh test timed out");
            }
            return rotated;
        });
        try {
            var refreshing = executor.submit(() -> refreshService.refreshRejected(channel.id(), "old-access"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var disconnecting = executor.submit(() -> {
                disconnectStarted.countDown();
                disconnectService.disconnect(channel.id(), email);
            });
            assertThat(disconnectStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> disconnecting.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(refreshing.get(5, TimeUnit.SECONDS)).isEqualTo(rotated);
            disconnecting.get(5, TimeUnit.SECONDS);
            assertThat(credentials.find(channel.id())).isEmpty();
            assertThat(channels.find(channel.id())).isPresent();
            assertThatThrownBy(() -> refreshService.refreshRejected(channel.id(), "rotated-access"))
                    .isInstanceOf(TwitchReauthorizationRequiredException.class);
            verify(exchange, times(1)).refresh("old-refresh");
            assertThat(credentials.find(channel.id())).isEmpty();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            database.deleteFrom(DSL.table(DSL.name("users")))
                    .where(DSL.field(DSL.name("id"), UUID.class).eq(channel.userId())).execute();
        }
    }
    @Autowired com.streambid.statistics.ChannelMeasurementRepository measurements;

    @Test
    void measurementHistoryIsOwnedPagedAndPreservedAfterDisconnect() {
        var channel = channelForRefresh("measurements@example.com");
        var now = java.time.Instant.parse("2026-11-04T10:15:00Z");
        var first = new com.streambid.statistics.ChannelMeasurement(UUID.randomUUID(), channel.id(), now,
                com.streambid.platform.StreamState.LIVE, 42L);
        var second = new com.streambid.statistics.ChannelMeasurement(UUID.randomUUID(), channel.id(), now.plusSeconds(60),
                com.streambid.platform.StreamState.UNKNOWN, null);
        assertThat(measurements.saveIfConnected(first)).isTrue();
        assertThat(measurements.saveIfConnected(first)).isFalse();
        assertThat(measurements.saveIfConnected(second)).isTrue();
        assertThat(measurements.history(channel.id(), UUID.randomUUID(), null, 10)).isEmpty();
        assertThat(measurements.history(channel.id(), channel.userId(), null, 1)).containsExactly(second);
        assertThat(measurements.history(channel.id(), channel.userId(), second.measuredAt(), 10)).containsExactly(first);
        credentials.delete(channel.id());
        var afterDisconnect = new com.streambid.statistics.ChannelMeasurement(UUID.randomUUID(), channel.id(),
                now.plusSeconds(120), com.streambid.platform.StreamState.LIVE, 10L);
        assertThat(measurements.saveIfConnected(afterDisconnect)).isFalse();
        assertThat(measurements.history(channel.id(), channel.userId(), null, 10)).containsExactly(second, first);
        assertThatThrownBy(() -> measurements.history(channel.id(), channel.userId(), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> measurements.history(channel.id(), channel.userId(), null, 201))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void collectorCandidatesUseKeysetAndExcludeDisconnectedChannels() {
        var first = channelForRefresh("candidate-one@example.com");
        var second = channelForRefresh("candidate-two@example.com");
        var page = channels.connectedAfter(null, 1);
        assertThat(page).hasSize(1);
        var next = channels.connectedAfter(page.getFirst().id(), 1);
        assertThat(next).hasSize(1);
        assertThat(java.util.Set.of(page.getFirst().id(), next.getFirst().id()))
                .containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(channels.connectedAfter(next.getFirst().id(), 1)).isEmpty();
        credentials.delete(first.id());
        assertThat(channels.connectedAfter(null, 200)).containsExactly(second);
        assertThatThrownBy(() -> channels.connectedAfter(null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> channels.connectedAfter(null, 201)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseRejectsInvalidMeasurementStatesAndViewers() {
        var channel = channelForRefresh("measurement-constraints@example.com");
        try {
            String sql = "INSERT INTO channel_measurements (id, channel_id, measured_at, state, viewers) "
                    + "VALUES (?, ?, CURRENT_TIMESTAMP, ?, CAST(? AS bigint))";
            for (Object[] invalid : new Object[][] {{"LIVE", -1L}, {"OFFLINE", 0L}, {"UNKNOWN", 1L}, {"INVALID", null}}) {
                assertThatThrownBy(() -> database.execute(sql, UUID.randomUUID(), channel.id(), invalid[0], invalid[1]))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            }
            assertThat(measurements.history(channel.id(), channel.userId(), null, 10)).isEmpty();
        } finally {
            database.deleteFrom(DSL.table(DSL.name("users")))
                    .where(DSL.field(DSL.name("id"), UUID.class).eq(channel.userId())).execute();
        }
    }
    @Test
    void measurementNanosecondsRoundTripAtDatabasePrecision() {
        var channel = channelForRefresh("precision@example.com");
        var instant = java.time.Instant.parse("2026-11-04T11:33:00.123456789Z");
        var measurement = new com.streambid.statistics.ChannelMeasurement(UUID.randomUUID(), channel.id(), instant,
                com.streambid.platform.StreamState.LIVE, 0L);
        assertThat(measurement.measuredAt()).isEqualTo(java.time.Instant.parse("2026-11-04T11:33:00.123456Z"));
        assertThat(measurements.saveIfConnected(measurement)).isTrue();
        assertThat(measurements.history(channel.id(), channel.userId(), null, 10)).containsExactly(measurement);
        var sameMicrosecond = new com.streambid.statistics.ChannelMeasurement(UUID.randomUUID(), channel.id(),
                instant.plusNanos(100), com.streambid.platform.StreamState.LIVE, 1L);
        assertThat(measurements.saveIfConnected(sameMicrosecond)).isFalse();
        assertThat(measurements.history(channel.id(), channel.userId(), instant, 10)).isEmpty();
        assertThat(measurements.history(channel.id(), channel.userId(), measurement.measuredAt().plusNanos(1000), 10))
                .containsExactly(measurement);
    }
    @Test
    void measurementApiChecksOwnerAndPagesHistoricalData() throws Exception {
        var channel = channelForRefresh("history-api@example.com");
        users.save(new UserAccount(UUID.randomUUID(), "history-foreign@example.com", "hash", "Foreign"));
        var now = java.time.Instant.parse("2026-11-04T12:20:00Z");
        for (int index = 0; index < 2; index++) {
            measurements.saveIfConnected(new com.streambid.statistics.ChannelMeasurement(UUID.randomUUID(), channel.id(),
                    now.plusSeconds(index), com.streambid.platform.StreamState.LIVE, (long) index));
        }
        String path = "/api/channels/" + channel.id() + "/measurements";
        credentials.delete(channel.id());
        mvc.perform(get(path).with(user("history-api@example.com")).param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].viewers").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].accessTokenCiphertext").doesNotExist())
                .andExpect(jsonPath("$[0].userId").doesNotExist());
        mvc.perform(get(path).with(user("history-api@example.com")).param("before", now.plusSeconds(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].viewers").value(0));
        mvc.perform(get(path).with(user("history-foreign@example.com"))).andExpect(status().isNotFound());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/channels/" + UUID.randomUUID() + "/measurements")
                .with(user("history-api@example.com"))).andExpect(status().isNotFound());
        for (String invalid : java.util.List.of("0", "201", "text")) {
            mvc.perform(get(path).with(user("history-api@example.com")).param("limit", invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(path).with(user("history-api@example.com")).param("before", "invalid-time"))
                .andExpect(status().isBadRequest());
    }
    @Test
    void statisticsSummaryCountsKnownViewersAndUsesExclusiveEnd() {
        var channel = channelForRefresh("summary-range@example.com");
        var from = java.time.Instant.parse("2026-11-04T13:10:00Z");
        var states = new com.streambid.platform.StreamState[] {
                com.streambid.platform.StreamState.LIVE, com.streambid.platform.StreamState.LIVE,
                com.streambid.platform.StreamState.LIVE, com.streambid.platform.StreamState.OFFLINE,
                com.streambid.platform.StreamState.UNKNOWN, com.streambid.platform.StreamState.LIVE};
        Long[] viewers = {0L, 10L, null, null, null, 1000L};
        for (int index = 0; index < states.length; index++) {
            measurements.saveIfConnected(new com.streambid.statistics.ChannelMeasurement(UUID.randomUUID(), channel.id(),
                    from.plusSeconds(index), states[index], viewers[index]));
        }
        var summary = measurements.summarize(channel.id(), channel.userId(), from, from.plusSeconds(5));
        assertThat(summary.totalSamples()).isEqualTo(5);
        assertThat(summary.liveSamples()).isEqualTo(3);
        assertThat(summary.offlineSamples()).isEqualTo(1);
        assertThat(summary.unknownSamples()).isEqualTo(1);
        assertThat(summary.knownViewerSamples()).isEqualTo(2);
        assertThat(summary.averageViewers()).isEqualByComparingTo("5");
        assertThat(summary.peakViewers()).isEqualTo(10);
        var unknown = measurements.summarize(channel.id(), channel.userId(), from.plusSeconds(3), from.plusSeconds(5));
        assertThat(unknown.averageViewers()).isNull();
        assertThat(unknown.peakViewers()).isNull();
        var foreign = measurements.summarize(channel.id(), UUID.randomUUID(), from, from.plusSeconds(5));
        assertThat(foreign.totalSamples()).isZero();
        assertThat(foreign.averageViewers()).isNull();
        assertThat(measurements.summarize(channel.id(), channel.userId(), from.minusSeconds(1), from).totalSamples()).isZero();
        assertThatThrownBy(() -> measurements.summarize(channel.id(), channel.userId(), from, from))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> measurements.summarize(channel.id(), channel.userId(), from, from.plusSeconds(32 * 86400)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void summaryApiChecksOwnershipRangeAndEmptyMetrics() throws Exception {
        var channel = channelForRefresh("summary-api@example.com");
        String path = "/api/channels/" + channel.id() + "/statistics";
        String from = "2026-11-04T13:31:00.123456789Z";
        String to = "2026-11-04T14:31:00.123456789Z";
        mvc.perform(get(path).with(user("summary-api@example.com")).param("from", from).param("to", to))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalSamples").value(0))
                .andExpect(jsonPath("$.averageViewers").doesNotExist()).andExpect(jsonPath("$.peakViewers").doesNotExist())
                .andExpect(jsonPath("$.from").value("2026-11-04T13:31:00.123456Z"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"));
        users.save(new UserAccount(UUID.randomUUID(), "summary-outsider@example.com", "hash", "Foreign"));
        mvc.perform(get(path).with(user("summary-outsider@example.com")).param("from", from).param("to", to))
                .andExpect(status().isNotFound());
        mvc.perform(get(path).param("from", from).param("to", to)).andExpect(status().isUnauthorized());
        for (String invalidTo : java.util.List.of(from, "invalid", "2026-12-10T00:00:00Z",
                "2026-11-04T13:31:00.123456999Z")) {
            mvc.perform(get(path).with(user("summary-api@example.com")).param("from", from).param("to", invalidTo))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(path).with(user("summary-api@example.com"))).andExpect(status().isBadRequest());
    }
    @Test
    void hourlyStatisticsUsesUtcBoundariesAndLeavesGapsEmpty() {
        var channel = channelForRefresh("hourly@example.com");
        var start = java.time.Instant.parse("2026-11-03T23:00:00Z");
        var times = java.util.List.of(start.plusSeconds(3599), start.plusSeconds(3600), start.plusSeconds(3660),
                start.plusSeconds(10800));
        Long[] viewers = {null, 0L, 20L, 999L};
        for (int index = 0; index < times.size(); index++) {
            measurements.saveIfConnected(new com.streambid.statistics.ChannelMeasurement(UUID.randomUUID(), channel.id(),
                    times.get(index), index == 0 ? com.streambid.platform.StreamState.UNKNOWN
                            : com.streambid.platform.StreamState.LIVE, viewers[index]));
        }
        var buckets = measurements.hourly(channel.id(), channel.userId(), start, start.plusSeconds(10800));
        assertThat(buckets).hasSize(2);
        assertThat(buckets.getFirst().hour()).isEqualTo(start);
        assertThat(buckets.getFirst().unknownSamples()).isEqualTo(1);
        assertThat(buckets.getFirst().knownViewerSamples()).isZero();
        assertThat(buckets.getFirst().averageViewers()).isNull();
        assertThat(buckets.getFirst().peakViewers()).isNull();
        assertThat(buckets.getLast().hour()).isEqualTo(start.plusSeconds(3600));
        assertThat(buckets.getLast().totalSamples()).isEqualTo(2);
        assertThat(buckets.getLast().liveSamples()).isEqualTo(2);
        assertThat(buckets.getLast().knownViewerSamples()).isEqualTo(2);
        assertThat(buckets.getLast().averageViewers()).isEqualByComparingTo("10");
        assertThat(buckets.getLast().peakViewers()).isEqualTo(20);
        assertThat(measurements.hourly(channel.id(), UUID.randomUUID(), start, start.plusSeconds(10800))).isEmpty();
        assertThat(measurements.hourly(channel.id(), channel.userId(), start.minusSeconds(3600), start)).isEmpty();
    }
    @Test
    void hourlyApiRequiresOwnerAndReturnsUtcBuckets() throws Exception {
        var channel = channelForRefresh("hourly-api@example.com");
        var from = java.time.Instant.parse("2026-11-04T00:00:00Z");
        measurements.saveIfConnected(new com.streambid.statistics.ChannelMeasurement(UUID.randomUUID(), channel.id(),
                from.plusSeconds(1), com.streambid.platform.StreamState.UNKNOWN, null));
        String path = "/api/channels/" + channel.id() + "/statistics/hourly";
        String to = from.plusSeconds(7200).toString();
        mvc.perform(get(path).with(user("hourly-api@example.com")).param("from", from.toString()).param("to", to))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].hour").value(from.toString()))
                .andExpect(jsonPath("$[0].unknownSamples").value(1))
                .andExpect(jsonPath("$[0].averageViewers").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"));
        users.save(new UserAccount(UUID.randomUUID(), "hourly-foreign@example.com", "hash", "Foreign"));
        mvc.perform(get(path).with(user("hourly-foreign@example.com")).param("from", from.toString()).param("to", to))
                .andExpect(status().isNotFound());
        mvc.perform(get(path).param("from", from.toString()).param("to", to)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("hourly-api@example.com"))).andExpect(status().isBadRequest());
        mvc.perform(get(path).with(user("hourly-api@example.com")).param("from", to).param("to", from.toString()))
                .andExpect(status().isBadRequest());
    }
    @Test
    void retentionDeletesOnlyExpiredMeasurementsInBoundedBatches() {
        var channel = channelForRefresh("retention@example.com");
        var cutoff = java.time.Instant.parse("2026-11-04T14:43:00Z");
        for (int offset : new int[] {-2, -1, 0, 1}) {
            measurements.saveIfConnected(new com.streambid.statistics.ChannelMeasurement(UUID.randomUUID(), channel.id(),
                    cutoff.plusSeconds(offset), com.streambid.platform.StreamState.UNKNOWN, null));
        }
        assertThat(measurements.deleteBefore(cutoff, 1)).isEqualTo(1);
        assertThat(measurements.history(channel.id(), channel.userId(), null, 10)).hasSize(3);
        assertThat(measurements.deleteBefore(cutoff, 1000)).isEqualTo(1);
        assertThat(measurements.deleteBefore(cutoff, 1000)).isZero();
        assertThat(measurements.history(channel.id(), channel.userId(), null, 10))
                .extracting(com.streambid.statistics.ChannelMeasurement::measuredAt)
                .containsExactly(cutoff.plusSeconds(1), cutoff);
        assertThat(channels.find(channel.id())).isPresent();
        assertThat(credentials.find(channel.id())).isPresent();
        assertThatThrownBy(() -> measurements.deleteBefore(cutoff, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> measurements.deleteBefore(cutoff, 1001)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void statisticsMetadataReportsActualAvailabilityAndDisabledRetention() throws Exception {
        var channel = channelForRefresh("metadata@example.com");
        String path = "/api/channels/" + channel.id() + "/statistics/metadata";
        mvc.perform(get(path).with(user("metadata@example.com"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.availability.firstMeasuredAt").doesNotExist())
                .andExpect(jsonPath("$.retentionEnabled").value(false)).andExpect(jsonPath("$.retentionDays").doesNotExist());
        var now = java.time.Instant.parse("2026-11-04T15:37:00Z");
        measurements.saveIfConnected(new com.streambid.statistics.ChannelMeasurement(UUID.randomUUID(), channel.id(), now,
                com.streambid.platform.StreamState.UNKNOWN, null));
        mvc.perform(get(path).with(user("metadata@example.com"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.availability.firstMeasuredAt").value(now.toString()))
                .andExpect(jsonPath("$.availability.lastMeasuredAt").value(now.toString()))
                .andExpect(jsonPath("$.collectionEnabled").value(false)).andExpect(jsonPath("$.maxRangeDays").value(31));
        users.save(new UserAccount(UUID.randomUUID(), "metadata-foreign@example.com", "hash", "Foreign"));
        mvc.perform(get(path).with(user("metadata-foreign@example.com"))).andExpect(status().isNotFound());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    @Autowired ChannelListingRepository listings;

    @Test
    void channelListingIsHiddenByDefaultOwnedAndRequiresAuthorization() {
        var channel = channelForRefresh("listing@example.com");
        var initial = listings.readOwned(channel.id(), channel.userId()).orElseThrow();
        assertThat(initial.listed()).isFalse();
        assertThat(initial.updatedAt()).isNull();
        assertThat(listings.readOwned(channel.id(), UUID.randomUUID())).isEmpty();
        assertThat(listings.setOwned(channel.id(), UUID.randomUUID(), true)).isEmpty();
        var listed = listings.setOwned(channel.id(), channel.userId(), true).orElseThrow();
        assertThat(listed.listed()).isTrue();
        assertThat(listed.updatedAt()).isNotNull();
        assertThat(listings.readOwned(channel.id(), channel.userId())).contains(listed);
        credentials.delete(channel.id());
        assertThat(listings.setOwned(channel.id(), channel.userId(), true)).isEmpty();
        assertThat(listings.setOwned(channel.id(), channel.userId(), false).orElseThrow().listed()).isFalse();
        assertThat(channels.find(channel.id())).isPresent();
    }
    @Test
    void listingApiIsOwnerOnlyAndDisconnectClearsVisibility() throws Exception {
        var channel = channelForRefresh("listing-api@example.com");
        users.save(new UserAccount(UUID.randomUUID(), "listing-foreign@example.com", "hash", "Foreign"));
        String path = "/api/channels/" + channel.id() + "/listing";
        mvc.perform(get(path).with(user("listing-api@example.com"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.listed").value(false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .with(user("listing-foreign@example.com")).with(csrf()).contentType("application/json").content("{\"listed\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .with(user("listing-api@example.com")).contentType("application/json").content("{\"listed\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .with(user("listing-api@example.com")).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .with(user("listing-api@example.com")).with(csrf()).contentType("application/json").content("{\"listed\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.listed").value(true));
        disconnectService.disconnect(channel.id(), "listing-api@example.com");
        assertThat(listings.readOwned(channel.id(), channel.userId()).orElseThrow().listed()).isFalse();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .with(user("listing-api@example.com")).with(csrf()).contentType("application/json").content("{\"listed\":true}"))
                .andExpect(status().isConflict());
        store.save(channel.userId(), new ChannelInfo(channel.externalChannelId(), "Reconnected", null),
                new TwitchUserTokens("new-access", "new-refresh", java.time.Instant.now().plusSeconds(3600)));
        assertThat(listings.readOwned(channel.id(), channel.userId()).orElseThrow().listed()).isFalse();
    }
    @Autowired com.streambid.campaign.CampaignCandidateRepository candidates;

    @Test
    void candidateSelectionRequiresListingCredentialsAndSelectedPlatform() {
        var first = channelForRefresh("candidate-visible-one@example.com");
        var second = channelForRefresh("candidate-visible-two@example.com");
        var hidden = channelForRefresh("candidate-hidden@example.com");
        var disconnected = channelForRefresh("candidate-disconnected@example.com");
        var otherPlatform = channelForRefresh("candidate-youtube@example.com");
        for (var channel : java.util.List.of(first, second, disconnected, otherPlatform)) {
            listings.setOwned(channel.id(), channel.userId(), true).orElseThrow();
        }
        credentials.delete(disconnected.id());
        database.execute("UPDATE connected_channels SET platform = 'YOUTUBE' WHERE id = ?", otherPlatform.id());
        var selected = java.util.Set.of(StreamingPlatform.TWITCH);
        var page = candidates.find(selected, null, 1);
        assertThat(page).hasSize(1);
        var next = candidates.find(selected, page.getFirst().channelId(), 1);
        assertThat(next).hasSize(1);
        assertThat(java.util.Set.of(page.getFirst().channelId(), next.getFirst().channelId()))
                .containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(candidates.find(selected, next.getFirst().channelId(), 1)).isEmpty();
        assertThat(candidates.find(java.util.Set.of(StreamingPlatform.YOUTUBE), null, 100))
                .extracting(com.streambid.campaign.CampaignCandidate::channelId).containsExactly(otherPlatform.id());
        assertThat(candidates.find(java.util.Set.of(), null, 100)).isEmpty();
        assertThat(candidates.find(selected, null, 100)).extracting(com.streambid.campaign.CampaignCandidate::channelId)
                .doesNotContain(hidden.id(), disconnected.id(), otherPlatform.id());
        assertThatThrownBy(() -> candidates.find(selected, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> candidates.find(selected, null, 101)).isInstanceOf(IllegalArgumentException.class);
    }
    @Autowired com.streambid.campaign.CampaignDraftService campaignDrafts;
    @Autowired com.streambid.campaign.CampaignTargetingService campaignTargets;

    @Test
    void candidatePreviewIsAdvertiserOwnedAndExposesOnlyListedChannelSummary() throws Exception {
        var visible = channelForRefresh("preview-visible@example.com");
        channelForRefresh("preview-hidden@example.com");
        UUID advertiser = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        users.save(new UserAccount(advertiser, "preview-owner@example.com", "hash", "Advertiser"));
        users.save(new UserAccount(other, "preview-other@example.com", "hash", "Other"));
        users.addRole(advertiser, com.streambid.user.UserRole.ADVERTISER);
        users.addRole(other, com.streambid.user.UserRole.ADVERTISER);
        var campaign = campaignDrafts.create("preview-owner@example.com", new com.streambid.campaign.CampaignDraftRequest("Draft", ""));
        String path = "/api/campaigns/" + campaign.id() + "/candidates";
        mvc.perform(get(path).with(user("preview-owner@example.com"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(0));
        campaignTargets.replace("preview-owner@example.com", campaign.id(), new com.streambid.campaign.CampaignTargetingRequest(
                java.util.Set.of(StreamingPlatform.TWITCH), 0L));
        listings.setOwned(visible.id(), visible.userId(), true).orElseThrow();
        var result = mvc.perform(get(path).with(user("preview-owner@example.com"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignVersion").value(1)).andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].channelId").value(visible.id().toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("userId", "advertiserId", "externalChannelId",
                "accessToken", "refreshToken", "old-access", "old-refresh");
        mvc.perform(get(path).with(user("preview-other@example.com"))).andExpect(status().isNotFound());
        mvc.perform(get(path).with(user("preview-visible@example.com"))).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String invalid : new String[] {"0", "101", "text"}) {
            mvc.perform(get(path).with(user("preview-owner@example.com")).param("limit", invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(path).with(user("preview-owner@example.com")).param("after", "invalid"))
                .andExpect(status().isBadRequest());
        campaignDrafts.archive("preview-owner@example.com", campaign.id(), new com.streambid.campaign.CampaignArchiveRequest(1L));
        mvc.perform(get(path).with(user("preview-owner@example.com"))).andExpect(status().isConflict());
    }
    private ConnectedChannel channelForRefresh(String email) {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, email, "hash", "Refresh"));
        return store.save(owner, new ChannelInfo(email, "Refresh", null),
                new TwitchUserTokens("old-access", "old-refresh", java.time.Instant.now().plusSeconds(3600)));
    }

    @Test
    void disconnectIsOwnerOnlyCsrfProtectedAndIdempotent() throws Exception {
        var channel = channelForRefresh("disconnect@example.com");
        users.save(new UserAccount(UUID.randomUUID(), "foreign@example.com", "hash", "Foreign"));
        String path = "/api/channels/" + channel.id();
        mvc.perform(delete(path).with(user("foreign@example.com")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path).with(user("disconnect@example.com"))).andExpect(status().isForbidden());
        mvc.perform(delete(path).with(csrf())).andExpect(status().isUnauthorized());
        assertThat(credentials.find(channel.id())).isPresent();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(delete(path).with(user("disconnect@example.com")).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        assertThat(credentials.find(channel.id())).isEmpty();
        assertThat(channels.find(channel.id())).isPresent();
        assertThat(credentials.dueForValidation(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1), null, 50)).isEmpty();
        mvc.perform(get("/api/channels").with(user("disconnect@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].authorizationRequired").value(true));
    }
    @Test
    void ownChannelSummaryReportsRequiredAuthorizationWithoutCredentials() throws Exception {
        var channel = channelForRefresh("summary@example.com");
        var connected = mvc.perform(get("/api/channels").with(user("summary@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].authorizationRequired").value(false))
                .andExpect(jsonPath("$[0].lastValidatedAt").isNotEmpty()).andReturn();
        assertThat(connected.getResponse().getContentAsString())
                .doesNotContain("old-access", "old-refresh", "accessTokenCiphertext", "refreshTokenCiphertext");
        credentials.delete(channel.id());
        mvc.perform(get("/api/channels").with(user("summary@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(channel.id().toString()))
                .andExpect(jsonPath("$[0].authorizationRequired").value(true))
                .andExpect(jsonPath("$[0].lastValidatedAt").doesNotExist());
    }
    @Test
    void validationSelectionHonorsCutoffAndKeysetCursor() {
        var channel = channelForRefresh("due@example.com");
        var saved = credentials.find(channel.id()).orElseThrow();
        assertThat(credentials.dueForValidation(saved.lastValidatedAt().plusSeconds(1), null, 50))
                .containsExactly(channel.id());
        assertThat(credentials.dueForValidation(saved.lastValidatedAt().minusSeconds(1), null, 50)).isEmpty();
        assertThat(credentials.dueForValidation(saved.lastValidatedAt().plusSeconds(1), channel.id(), 50)).isEmpty();
    }
    @Test
    void staleValidationCannotOverwriteReconnectedCredentials() {
        var channel = channelForRefresh("validation@example.com");
        var old = credentials.find(channel.id()).orElseThrow();
        store.save(channel.userId(), new ChannelInfo(channel.externalChannelId(), "New name", null),
                new TwitchUserTokens("new-access", "new-refresh", java.time.Instant.now().plusSeconds(3600)));
        var current = credentials.find(channel.id()).orElseThrow();
        assertThat(credentials.markValidated(old, old.lastValidatedAt().plusHours(1), old.expiresAt())).isFalse();
        credentials.deleteIfUnchanged(old);
        assertThat(credentials.find(channel.id())).contains(current);
    }
    @Test
    void temporaryRefreshFailurePreservesStoredCredentials() {
        var channel = channelForRefresh("temporary@example.com");
        var before = credentials.find(channel.id()).orElseThrow();
        when(exchange.refresh("old-refresh")).thenThrow(new PlatformUnavailableException(
                StreamingPlatform.TWITCH, new IllegalStateException("Temporary failure")));
        assertThatThrownBy(() -> refreshService.refreshRejected(channel.id(), "old-access"))
                .isInstanceOf(PlatformUnavailableException.class);
        assertThat(credentials.find(channel.id())).contains(before);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRejectedRequestsPerformOnlyOneRefresh() throws Exception {
        var channel = channelForRefresh("concurrent@example.com");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var rotated = new TwitchUserTokens("concurrent-access", "concurrent-refresh",
                java.time.Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(exchange.refresh("old-refresh")).thenAnswer(invocation -> {
            if (calls.incrementAndGet() > 1) {
                throw new AssertionError("Concurrent refresh was not serialized");
            }
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Refresh test timed out");
            }
            return rotated;
        });
        try {
            var first = executor.submit(() -> refreshService.refreshRejected(channel.id(), "old-access"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return refreshService.refreshRejected(channel.id(), "old-access");
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(rotated);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(rotated);
            verify(exchange, times(1)).refresh("old-refresh");
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            database.deleteFrom(DSL.table(DSL.name("users")))
                    .where(DSL.field(DSL.name("id"), UUID.class).eq(channel.userId())).execute();
        }
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
