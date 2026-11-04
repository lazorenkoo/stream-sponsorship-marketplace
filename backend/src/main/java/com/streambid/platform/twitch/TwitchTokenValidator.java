package com.streambid.platform.twitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.streambid.platform.PlatformUnavailableException;
import com.streambid.platform.StreamingPlatform;
import java.time.Clock;
import java.time.Instant;
import java.time.DateTimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchTokenValidator {

    private final RestClient client;
    private final TwitchProperties properties;
    private final Clock clock;
    private final String validateUrl;

    public TwitchTokenValidator(RestClient twitchRestClient, TwitchProperties properties, Clock twitchClock,
                                 @Value("${twitch.validate-url:https://id.twitch.tv/oauth2/validate}") String validateUrl) {
        this.client = twitchRestClient;
        this.properties = properties;
        this.clock = twitchClock;
        this.validateUrl = validateUrl;
    }

    public Instant validate(String accessToken, String expectedUserId) {
        try {
            JsonNode body = client.get().uri(validateUrl).headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve().body(JsonNode.class);
            if (body == null || !body.path("client_id").isTextual() || !body.path("expires_in").isIntegralNumber()
                    || !body.get("expires_in").canConvertToLong()) {
                throw unavailable();
            }
            if (!properties.clientId().equals(body.get("client_id").asText())
                    || !expectedUserId.equals(body.path("user_id").asText(null))) {
                throw new TwitchReauthorizationRequiredException();
            }
            if (body.get("expires_in").longValue() <= 0) {
                throw new TwitchAccessTokenRejectedException();
            }
            return clock.instant().plusSeconds(body.get("expires_in").longValue());
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new TwitchAccessTokenRejectedException();
        } catch (RestClientException | DateTimeException exception) {
            throw unavailable();
        }
    }

    private PlatformUnavailableException unavailable() {
        return new PlatformUnavailableException(StreamingPlatform.TWITCH, new IllegalStateException("Token validation failed"));
    }
}
