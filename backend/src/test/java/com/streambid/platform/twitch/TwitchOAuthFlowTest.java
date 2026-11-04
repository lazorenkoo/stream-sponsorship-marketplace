package com.streambid.platform.twitch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streambid.auth.RegisterRequest;
import com.streambid.auth.RegistrationService;
import com.streambid.channel.ChannelRepository;
import com.streambid.channel.ChannelCredentialsRepository;
import com.streambid.security.TokenCipher;
import com.streambid.user.UserRole;
import com.streambid.platform.ChannelInfo;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"twitch.enabled=true", "twitch.validation.enabled=false", "twitch.client-id=test-client", "twitch.client-secret=test-secret",
        "twitch.api-url=https://example.test/helix", "twitch.token-url=https://example.test/token",
        "twitch.validate-url=https://example.test/validate", "security.token-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Import(TwitchOAuthFlowTest.HttpMocks.class)
class TwitchOAuthFlowTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class HttpMocks {
        @Bean
        RestClient.Builder mockBuilder() {
            return RestClient.builder().baseUrl("https://example.test/helix");
        }
        @Bean
        MockRestServiceServer server(RestClient.Builder mockBuilder) {
            return MockRestServiceServer.bindTo(mockBuilder).build();
        }
        @Bean
        @Primary
        RestClient mockClient(RestClient.Builder mockBuilder, MockRestServiceServer server) {
            return mockBuilder.build();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired MockRestServiceServer server;
    @Autowired RegistrationService registration;
    @Autowired ObjectMapper json;
    @Autowired ChannelRepository channels;
    @Autowired ChannelCredentialsRepository credentials;
    @Autowired TokenCipher cipher;

    @Autowired TwitchOAuthState states;

    @BeforeEach
    void resetMockServer() {
        server.reset();
    }

    @AfterEach
    void verifyMockServer() {
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-state", "denied", "missing-code", "other-session"})
    void rejectsInvalidCallbackWithoutNetworkOrSavedChannels(String failure) throws Exception {
        var owner = registration.register(new RegisterRequest(
                "invalid-flow@example.com", "strong-password", "Invalid Flow", UserRole.ADVERTISER));
        var session = new MockHttpSession();
        String state = states.begin(session, owner.id());
        var callbackSession = failure.equals("other-session") ? new MockHttpSession() : session;
        var request = get("/api/channels/connect/twitch/callback").session(callbackSession).with(user(owner.email()))
                .param("state", failure.equals("wrong-state") ? "invalid-state" : state);
        if (!failure.equals("missing-code")) {
            request.param("code", "private-code");
        }
        if (failure.equals("denied")) {
            request.param("error", "private-provider-error");
        }
        var result = mvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private-code", "private-provider-error");
        assertThat(channels.findByUser(owner.id())).isEmpty();
    }

    @Autowired TwitchConnectionStore connectionStore;
    @Autowired com.streambid.statistics.ChannelStatisticsCollector statisticsCollector;
    @Autowired com.streambid.statistics.ChannelMeasurementRepository measurements;
    @Autowired TwitchTokenService appTokens;

    @Test
    void collectorPersistsHelixStatesAndStopsAfterDisconnect() {
        var owner = registration.register(new RegisterRequest(
                "collector-flow@example.com", "strong-password", "Collector", UserRole.STREAMER));
        var channel = connectionStore.save(owner.id(), new ChannelInfo("collector-channel", "Collector", null),
                new TwitchUserTokens("user-access", "user-refresh", Instant.now().plusSeconds(3600)));
        appTokens.invalidate("collector-app");
        server.expect(requestTo("https://example.test/token")).andRespond(withSuccess("""
                {"access_token":"collector-app","expires_in":3600,"token_type":"bearer"}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/helix/streams?user_id=collector-channel"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header(
                        "Authorization", "Bearer collector-app"))
                .andRespond(withSuccess("""
                        {"data":[{"user_id":"collector-channel","type":"live","viewer_count":7}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/helix/streams?user_id=collector-channel"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/helix/users?id=collector-channel"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"collector-channel","display_name":"Collector"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/helix/streams?user_id=collector-channel"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .header("Ratelimit-Reset", Long.toString(Instant.now().plusSeconds(60).getEpochSecond())));
        assertThat(statisticsCollector.collect(channel)).isTrue();
        assertThat(statisticsCollector.collect(channel)).isTrue();
        assertThat(statisticsCollector.collect(channel)).isTrue();
        var history = measurements.history(channel.id(), owner.id(), null, 10);
        assertThat(history).hasSize(3);
        assertThat(history).extracting(com.streambid.statistics.ChannelMeasurement::state)
                .containsExactly(com.streambid.platform.StreamState.UNKNOWN,
                        com.streambid.platform.StreamState.OFFLINE, com.streambid.platform.StreamState.LIVE);
        assertThat(history.get(0).viewers()).isNull();
        assertThat(history.get(1).viewers()).isNull();
        assertThat(history.get(2).viewers()).isEqualTo(7);
        credentials.delete(channel.id());
        assertThat(statisticsCollector.collect(channel)).isFalse();
        assertThat(measurements.history(channel.id(), owner.id(), null, 10)).hasSize(3);
    }


    @Test
    void disconnectCancelsPendingAuthorizationInSameSession() throws Exception {
        var owner = registration.register(new RegisterRequest(
                "cancel@example.com", "strong-password", "Cancel", UserRole.STREAMER));
        var channel = connectionStore.save(owner.id(), new ChannelInfo("cancel-channel", "Cancel", null),
                new TwitchUserTokens("old-access", "old-refresh", Instant.now().plusSeconds(3600)));
        var session = new MockHttpSession();
        String state = states.begin(session, owner.id());
        mvc.perform(delete("/api/channels/" + channel.id()).session(session).with(user(owner.email())).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/channels/connect/twitch/callback").session(session).with(user(owner.email()))
                .param("state", state).param("code", "pending-code")).andExpect(status().isBadRequest());
        assertThat(credentials.find(channel.id())).isEmpty();
        assertThat(channels.findByUser(owner.id())).hasSize(1);
    }
    @Test
    void mismatchedIdentityDoesNotPersistChannelOrCredentials() throws Exception {
        var owner = registration.register(new RegisterRequest(
                "mismatch@example.com", "strong-password", "Mismatch", UserRole.ADVERTISER));
        var session = new MockHttpSession();
        String state = states.begin(session, owner.id());
        server.expect(requestTo("https://example.test/token")).andRespond(withSuccess("""
                {"access_token":"private-access","refresh_token":"private-refresh","expires_in":3600,"token_type":"bearer"}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/validate")).andRespond(withSuccess("""
                {"client_id":"test-client","user_id":"verified-user","expires_in":3500}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/helix/users")).andRespond(withSuccess("""
                {"data":[{"id":"different-user","display_name":"Wrong"}]}
                """, MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/channels/connect/twitch/callback").session(session).with(user(owner.email()))
                .param("state", state).param("code", "private-code")).andExpect(status().isBadRequest());
        assertThat(channels.findByUser(owner.id())).isEmpty();
        mvc.perform(get("/api/channels/connect/twitch/callback").session(session).with(user(owner.email()))
                .param("state", state).param("code", "private-code")).andExpect(status().isBadRequest());
    }
    @Test
    void connectsVerifiedChannelWithEncryptedTokensAndRejectsReplay() throws Exception {
        var owner = registration.register(new RegisterRequest(
                "flow@example.com", "strong-password", "Flow", UserRole.ADVERTISER));
        var login = mvc.perform(post("/api/auth/login").with(csrf())
                .param("email", owner.email()).param("password", "strong-password"))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        var begin = mvc.perform(post("/api/channels/connect/twitch").session(session).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        String url = json.readTree(begin.getResponse().getContentAsString()).get("authorizationUrl").asText();
        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        server.expect(requestTo("https://example.test/token")).andRespond(withSuccess("""
                {"access_token":"private-access","refresh_token":"private-refresh","expires_in":3600,"token_type":"bearer"}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/validate")).andRespond(withSuccess("""
                {"client_id":"test-client","user_id":"verified-channel","expires_in":3500}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/helix/users")).andRespond(withSuccess("""
                {"data":[{"id":"verified-channel","display_name":"Streamer"}]}
                """, MediaType.APPLICATION_JSON));
        var result = mvc.perform(get("/api/channels/connect/twitch/callback").session(session)
                .param("code", "private-code").param("state", state))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.externalChannelId").value("verified-channel")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private-access", "private-refresh");
        var channel = channels.findByUser(owner.id()).getFirst();
        var stored = credentials.find(channel.id()).orElseThrow();
        assertThat(cipher.decrypt(stored.accessTokenCiphertext(), channel.id() + ":access")).isEqualTo("private-access");
        var context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getAuthorities()).extracting("authority").contains("ROLE_STREAMER");
        mvc.perform(get("/api/channels/connect/twitch/callback").session(session)
                .param("code", "private-code").param("state", state)).andExpect(status().isBadRequest());
        assertThat(channels.findByUser(owner.id())).hasSize(1);
        server.verify();
    }

    @Test
    void rejectsAnonymousCallbackAndConnectWithoutCsrf() throws Exception {
        mvc.perform(get("/api/channels/connect/twitch/callback")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/channels/connect/twitch")).andExpect(status().isForbidden());
    }
}
