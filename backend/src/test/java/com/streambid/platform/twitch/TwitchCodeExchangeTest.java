package com.streambid.platform.twitch;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TwitchCodeExchangeTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TwitchProperties properties = new TwitchProperties("client", "secret", "", "https://example.test/token");
    private final TwitchOAuthProperties oauth = new TwitchOAuthProperties(URI.create("http://localhost:8080/callback"));
    private final Instant now = Instant.parse("2026-11-03T14:00:00Z");

    @Test
    void exchangesCodeInFormBodyAndRedactsReturnedTokens() {
        server.expect(requestTo(properties.tokenUrl())).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("code=private-code")))
                .andExpect(content().string(containsString("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fcallback")))
                .andRespond(withSuccess("""
                        {"access_token":"private-access","refresh_token":"private-refresh","expires_in":3600,"token_type":"bearer"}
                        """, MediaType.APPLICATION_JSON));
        var exchange = new TwitchCodeExchange(builder.build(), properties, oauth, Clock.fixed(now, ZoneOffset.UTC));
        var tokens = exchange.exchange("private-code");
        assertThat(tokens.expiresAt()).isEqualTo(now.plusSeconds(3600));
        assertThat(tokens.accessToken()).isEqualTo("private-access");
        assertThat(tokens.toString()).doesNotContain("private-access", "private-refresh");
        server.verify();
    }

    @Test
    void rejectsInvalidCodeWithoutExposingUpstreamBody() {
        server.expect(requestTo(properties.tokenUrl())).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .body("private-code and sensitive upstream details"));
        var exchange = new TwitchCodeExchange(builder.build(), properties, oauth, Clock.systemUTC());
        assertThatThrownBy(() -> exchange.exchange("private-code"))
                .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("private-code");
        server.verify();
    }
}
