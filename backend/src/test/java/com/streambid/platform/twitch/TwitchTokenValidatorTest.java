package com.streambid.platform.twitch;

import com.streambid.platform.PlatformUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TwitchTokenValidatorTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final Instant now = Instant.parse("2026-11-03T20:00:00Z");
    private final TwitchTokenValidator validator = new TwitchTokenValidator(builder.build(),
            new TwitchProperties("our-client", "secret", "", ""), Clock.fixed(now, ZoneOffset.UTC), "https://example.test/validate");

    @Test
    void validatesStoredIdentityAndReturnsActualExpiry() {
        server.expect(requestTo("https://example.test/validate")).andExpect(header("Authorization", "Bearer access"))
                .andRespond(withSuccess("""
                        {"client_id":"our-client","user_id":"stored-user","expires_in":1200}
                        """, MediaType.APPLICATION_JSON));
        assertThat(validator.validate("access", "stored-user")).isEqualTo(now.plusSeconds(1200));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"other-user", ""})
    void rejectsDifferentIdentity(String userId) {
        server.expect(requestTo("https://example.test/validate")).andRespond(withSuccess(
                "{\"client_id\":\"our-client\",\"user_id\":\"" + userId + "\",\"expires_in\":1200}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> validator.validate("access", "stored-user"))
                .isInstanceOf(TwitchReauthorizationRequiredException.class);
        server.verify();
    }

    @Test
    void distinguishesRejectedTokenFromTemporaryFailure() {
        server.expect(requestTo("https://example.test/validate")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("https://example.test/validate")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> validator.validate("access", "stored-user"))
                .isInstanceOf(TwitchAccessTokenRejectedException.class);
        assertThatThrownBy(() -> validator.validate("access", "stored-user"))
                .isInstanceOf(PlatformUnavailableException.class);
        server.verify();
    }
}
