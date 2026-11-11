package com.streambid.platform.twitch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.streambid.platform.PlatformUnavailableException;
import com.streambid.platform.StreamingPlatform;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchTokenService {

    private final RestClient client;
    private final TwitchProperties properties;
    private final Clock clock;
    private String cachedToken;
    private Instant refreshAt = Instant.MIN;

    public TwitchTokenService(RestClient twitchRestClient, TwitchProperties properties, Clock twitchClock) {
        this.client = twitchRestClient;
        this.properties = properties;
        this.clock = twitchClock;
    }

    public synchronized String accessToken() {
        Instant now = clock.instant();
        if (cachedToken != null && now.isBefore(refreshAt)) {
            return cachedToken;
        }
        if (!StringUtils.hasText(properties.clientId()) || !StringUtils.hasText(properties.clientSecret())) {
            throw new IllegalStateException("Twitch credentials are required");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("grant_type", "client_credentials");
        TokenResponse response;
        try {
            response = client.post().uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(TokenResponse.class);
        } catch (RestClientException exception) {
            throw unavailable();
        }
        if (response == null || !StringUtils.hasText(response.accessToken()) || response.expiresIn() <= 0
                || !"bearer".equalsIgnoreCase(response.tokenType())) {
            throw unavailable();
        }
        cachedToken = response.accessToken();
        refreshAt = now.plusSeconds(response.expiresIn() - Math.min(30, response.expiresIn() / 10));
        return cachedToken;
    }

    public synchronized void invalidate(String rejectedToken) {
        if (cachedToken != null && cachedToken.equals(rejectedToken)) {
            cachedToken = null;
        }
    }
    private PlatformUnavailableException unavailable() {
        return new PlatformUnavailableException(StreamingPlatform.TWITCH,
                new IllegalStateException("Token request failed"));
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("expires_in") long expiresIn,
                                 @JsonProperty("token_type") String tokenType) {
        @Override
        public String toString() {
            return "TokenResponse[redacted]";
        }
    }
}
