package com.streambid.platform.twitch;

import com.streambid.platform.PlatformUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class TwitchTokenServiceTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TwitchProperties properties = new TwitchProperties(
            "test-client", "test-secret", "https://example.test/helix", "https://example.test/token");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void staleRejectionDoesNotInvalidateNewerToken() {
        server.expect(requestTo(properties.tokenUrl())).andRespond(withSuccess("""
                {"access_token":"first-token","expires_in":3600,"token_type":"bearer"}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(properties.tokenUrl())).andRespond(withSuccess("""
                {"access_token":"next-token","expires_in":3600,"token_type":"bearer"}
                """, MediaType.APPLICATION_JSON));
        var tokens = new TwitchTokenService(builder.build(), properties, clock);
        assertThat(tokens.accessToken()).isEqualTo("first-token");
        tokens.invalidate("first-token");
        assertThat(tokens.accessToken()).isEqualTo("next-token");
        tokens.invalidate("first-token");
        assertThat(tokens.accessToken()).isEqualTo("next-token");
        server.verify();
    }
    @Test
    void refreshesBeforeTokenExpiry() {
        Clock movingClock = mock(Clock.class);
        Instant start = clock.instant();
        when(movingClock.instant()).thenReturn(start);
        server.expect(requestTo(properties.tokenUrl())).andRespond(withSuccess("""
                {"access_token":"first-token","expires_in":3600,"token_type":"bearer"}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(properties.tokenUrl())).andRespond(withSuccess("""
                {"access_token":"next-token","expires_in":3600,"token_type":"bearer"}
                """, MediaType.APPLICATION_JSON));
        var tokens = new TwitchTokenService(builder.build(), properties, movingClock);
        assertThat(tokens.accessToken()).isEqualTo("first-token");
        when(movingClock.instant()).thenReturn(start.plusSeconds(3569));
        assertThat(tokens.accessToken()).isEqualTo("first-token");
        when(movingClock.instant()).thenReturn(start.plusSeconds(3570));
        assertThat(tokens.accessToken()).isEqualTo("next-token");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"access_token\":\"x\",\"expires_in\":0,\"token_type\":\"bearer\"}",
            "{\"access_token\":\"x\",\"expires_in\":3600,\"token_type\":\"other\"}",
            "not-json"
    })
    void rejectsInvalidTokenResponses(String body) {
        server.expect(requestTo(properties.tokenUrl())).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var tokens = new TwitchTokenService(builder.build(), properties, clock);
        assertThatThrownBy(tokens::accessToken).isInstanceOf(PlatformUnavailableException.class);
        server.verify();
    }

    @Test
    void retriesAfterFailureInsteadOfCachingIt() {
        server.expect(requestTo(properties.tokenUrl())).andRespond(withServerError());
        server.expect(requestTo(properties.tokenUrl())).andRespond(withSuccess("""
                {"access_token":"recovered","expires_in":3600,"token_type":"bearer"}
                """, MediaType.APPLICATION_JSON));
        var tokens = new TwitchTokenService(builder.build(), properties, clock);
        assertThatThrownBy(tokens::accessToken).isInstanceOf(PlatformUnavailableException.class);
        assertThat(tokens.accessToken()).isEqualTo("recovered");
        server.verify();
    }
    @Test
    void fetchesAndCachesAppToken() {
        server.expect(requestTo(properties.tokenUrl())).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("client_id=test-client&client_secret=test-secret&grant_type=client_credentials"))
                .andRespond(withSuccess("""
                        {"access_token":"test-token","expires_in":3600,"token_type":"bearer"}
                        """, MediaType.APPLICATION_JSON));
        var tokens = new TwitchTokenService(builder.build(), properties, clock);
        assertThat(tokens.accessToken()).isEqualTo("test-token");
        assertThat(tokens.accessToken()).isEqualTo("test-token");
        server.verify();
    }

    @Test
    void hidesUpstreamFailureDetails() {
        server.expect(requestTo(properties.tokenUrl())).andRespond(withServerError());
        var tokens = new TwitchTokenService(builder.build(), properties, clock);
        assertThatThrownBy(tokens::accessToken).isInstanceOf(PlatformUnavailableException.class)
                .hasMessageNotContaining("test-secret");
        server.verify();
    }
}
