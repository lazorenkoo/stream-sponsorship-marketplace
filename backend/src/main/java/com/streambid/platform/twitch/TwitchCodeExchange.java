package com.streambid.platform.twitch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.streambid.platform.PlatformUnavailableException;
import com.streambid.platform.StreamingPlatform;
import java.time.Clock;
import java.time.DateTimeException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchCodeExchange {

    private final RestClient client;
    private final TwitchProperties properties;
    private final TwitchOAuthProperties oauth;
    private final Clock clock;

    public TwitchCodeExchange(RestClient twitchRestClient, TwitchProperties properties,
                              TwitchOAuthProperties oauth, Clock twitchClock) {
        this.client = twitchRestClient;
        this.properties = properties;
        this.oauth = oauth;
        this.clock = twitchClock;
    }

    public TwitchUserTokens exchange(String code) {
        if (!StringUtils.hasText(code) || code.length() > 2048) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid authorization code");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", oauth.redirectUri().toString());
        try {
            var response = client.post().uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(TokenResponse.class);
            if (response == null || !StringUtils.hasText(response.accessToken())
                    || !StringUtils.hasText(response.refreshToken()) || response.expiresIn() <= 0
                    || !"bearer".equalsIgnoreCase(response.tokenType())) {
                throw unavailable();
            }
            return new TwitchUserTokens(response.accessToken(), response.refreshToken(),
                    clock.instant().plusSeconds(response.expiresIn()));
        } catch (HttpClientErrorException.BadRequest exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Twitch authorization failed");
        } catch (RestClientException | DateTimeException exception) {
            throw unavailable();
        }
    }

    private PlatformUnavailableException unavailable() {
        return new PlatformUnavailableException(StreamingPlatform.TWITCH, new IllegalStateException("Code exchange failed"));
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("refresh_token") String refreshToken,
                                 @JsonProperty("expires_in") long expiresIn,
                                 @JsonProperty("token_type") String tokenType) {
        @Override
        public String toString() {
            return "TokenResponse[redacted]";
        }
    }
}
