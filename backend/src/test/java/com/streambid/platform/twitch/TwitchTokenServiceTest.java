package com.streambid.platform.twitch;

import com.streambid.platform.PlatformUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    void fetchesAndCachesAppToken() {
        server.expect(requestTo(properties.tokenUrl())).andExpect(method(HttpMethod.POST))
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
