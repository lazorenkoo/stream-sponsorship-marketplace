package com.streambid.platform.twitch;

import java.net.URI;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwitchAuthorizationServiceTest {

    @Test
    void buildsCodeAuthorizationBoundToCurrentSessionWithoutClientSecret() {
        var state = new TwitchOAuthState(Clock.systemUTC());
        var properties = new TwitchProperties("client-id", "private-secret", "", "");
        var oauth = new TwitchOAuthProperties(URI.create("http://localhost:8080/callback"));
        var service = new TwitchAuthorizationService(properties, oauth, state);
        var session = new MockHttpSession();
        UUID owner = UUID.randomUUID();
        String url = service.authorizationUrl(session, owner);
        var query = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
        assertThat(url).startsWith("https://id.twitch.tv/oauth2/authorize?").doesNotContain("private-secret");
        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(query.getFirst("client_id")).isEqualTo("client-id");
        assertThatCode(() -> state.consume(session, owner, query.getFirst("state"))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/callback", "/callback", "https://user@example.com/callback", "https://example.com/#fragment"})
    void rejectsUnsafeRedirectConfiguration(String redirect) {
        assertThatThrownBy(() -> new TwitchOAuthProperties(URI.create(redirect)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
