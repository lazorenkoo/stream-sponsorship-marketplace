package com.streambid.platform.twitch;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwitchIdentityServiceTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test/helix");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TwitchProperties properties = new TwitchProperties("our-client", "secret", "", "");
    private final Instant now = Instant.parse("2026-11-03T15:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final TwitchUserTokens tokens = new TwitchUserTokens("user-token", "refresh", now.plusSeconds(3600));

    @Test
    void getsIdentityFromUserTokenInsteadOfSuppliedChannelId() {
        server.expect(requestTo("https://example.test/validate"))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withSuccess("""
                        {"client_id":"our-client","user_id":"verified-user","expires_in":3500}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/helix/users"))
                .andExpect(header("Client-Id", "our-client"))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"verified-user","display_name":"Verified"}]}
                        """, MediaType.APPLICATION_JSON));
        var service = new TwitchIdentityService(builder.build(), properties, clock, "https://example.test/validate");
        assertThat(service.identify(tokens).channelId()).isEqualTo("verified-user");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"client_id\":\"other-client\",\"user_id\":\"user\",\"expires_in\":3500}",
            "{\"client_id\":\"our-client\",\"expires_in\":3500}",
            "{\"client_id\":\"our-client\",\"user_id\":\"user\",\"expires_in\":0}"
    })
    void refusesOtherClientAppTokensAndExpiredTokens(String body) {
        server.expect(requestTo("https://example.test/validate"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var service = new TwitchIdentityService(builder.build(), properties, clock, "https://example.test/validate");
        assertThatThrownBy(() -> service.identify(tokens)).isInstanceOf(ResponseStatusException.class);
        server.verify();
    }
}
