package com.streambid.platform.twitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.streambid.platform.ChannelInfo;
import com.streambid.platform.PlatformUnavailableException;
import com.streambid.platform.StreamingPlatform;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchIdentityService {

    private final RestClient client;
    private final TwitchProperties properties;
    private final Clock clock;
    private final String validateUrl;

    public TwitchIdentityService(RestClient twitchRestClient, TwitchProperties properties, Clock twitchClock,
                                 @Value("${twitch.validate-url:https://id.twitch.tv/oauth2/validate}") String validateUrl) {
        this.client = twitchRestClient;
        this.properties = properties;
        this.clock = twitchClock;
        this.validateUrl = validateUrl;
    }

    public ChannelInfo identify(TwitchUserTokens tokens) {
        if (!tokens.expiresAt().isAfter(clock.instant())) {
            throw invalidIdentity();
        }
        try {
            JsonNode validation = client.get().uri(validateUrl)
                    .headers(headers -> headers.setBearerAuth(tokens.accessToken()))
                    .retrieve().body(JsonNode.class);
            if (validation == null || !properties.clientId().equals(validation.path("client_id").asText())
                    || !validation.path("user_id").isTextual() || validation.get("user_id").asText().isBlank()
                    || validation.get("user_id").asText().length() > 255
                    || !validation.path("expires_in").isIntegralNumber()
                    || !validation.get("expires_in").canConvertToLong() || validation.get("expires_in").longValue() <= 0) {
                throw invalidIdentity();
            }
            String userId = validation.get("user_id").asText();
            JsonNode body = client.get().uri("/users").header("Client-Id", properties.clientId())
                    .headers(headers -> headers.setBearerAuth(tokens.accessToken()))
                    .retrieve().body(JsonNode.class);
            if (body == null || !body.path("data").isArray() || body.get("data").size() != 1) {
                throw invalidIdentity();
            }
            JsonNode user = body.get("data").get(0);
            if (!userId.equals(user.path("id").asText()) || !user.path("display_name").isTextual()
                    || user.get("display_name").asText().isBlank() || user.get("display_name").asText().length() > 255) {
                throw invalidIdentity();
            }
            return new ChannelInfo(userId, user.get("display_name").asText(), user.path("profile_image_url").asText(null));
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw invalidIdentity();
        } catch (RestClientException exception) {
            throw new PlatformUnavailableException(StreamingPlatform.TWITCH, new IllegalStateException("Identity request failed"));
        }
    }

    private ResponseStatusException invalidIdentity() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Twitch identity could not be verified");
    }
}
