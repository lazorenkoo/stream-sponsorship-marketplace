package com.streambid.platform.twitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.streambid.platform.ChannelInfo;
import com.streambid.platform.ChannelNotFoundException;
import com.streambid.platform.PlatformUnavailableException;
import com.streambid.platform.StreamState;
import com.streambid.platform.StreamStatus;
import com.streambid.platform.StreamingPlatform;
import com.streambid.platform.StreamingPlatformProvider;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Service
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchProvider implements StreamingPlatformProvider {

    private final RestClient client;
    private final TwitchTokenService tokens;
    private final TwitchProperties properties;
    private final Clock clock;

    public TwitchProvider(RestClient twitchRestClient, TwitchTokenService tokens,
                          TwitchProperties properties, Clock twitchClock) {
        this.client = twitchRestClient;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = twitchClock;
    }

    @Override
    public StreamingPlatform platform() {
        return StreamingPlatform.TWITCH;
    }

    @Override
    public ChannelInfo getChannel(String channelId) {
        JsonNode data = getData("/users", "id", channelId);
        if (data.isEmpty()) {
            throw new ChannelNotFoundException(platform());
        }
        JsonNode user = data.get(0);
        if (!channelId.equals(user.path("id").asText()) || !user.path("display_name").isTextual()) {
            throw unavailable();
        }
        return new ChannelInfo(channelId, user.get("display_name").asText(),
                user.path("profile_image_url").asText(null));
    }

    @Override
    public StreamStatus getStreamStatus(String channelId) {
        JsonNode data = getData("/streams", "user_id", channelId);
        if (data.isEmpty()) {
            getChannel(channelId);
            return new StreamStatus(channelId, StreamState.OFFLINE, null, clock.instant());
        }
        JsonNode stream = data.get(0);
        JsonNode viewers = stream.get("viewer_count");
        if (!channelId.equals(stream.path("user_id").asText())
                || !"live".equals(stream.path("type").asText())
                || viewers == null || !viewers.isIntegralNumber() || !viewers.canConvertToLong()
                || viewers.longValue() < 0) {
            throw unavailable();
        }
        return new StreamStatus(channelId, StreamState.LIVE, viewers.longValue(), clock.instant());
    }

    private JsonNode getData(String path, String parameter, String channelId) {
        if (channelId == null || !channelId.matches("[1-9][0-9]{0,19}")) {
            throw new IllegalArgumentException("Invalid Twitch channel ID");
        }
        JsonNode body = request(path, parameter, channelId);
        if (body == null || !body.path("data").isArray() || body.get("data").size() > 1) {
            throw unavailable();
        }
        return body.get("data");
    }

    private JsonNode request(String path, String parameter, String channelId) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = tokens.accessToken();
            try {
                return client.get().uri(uri -> uri.path(path).queryParam(parameter, channelId).build())
                        .header("Client-Id", properties.clientId())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve().body(JsonNode.class);
            } catch (HttpClientErrorException.Unauthorized exception) {
                if (attempt == 1) {
                    throw unavailable();
                }
                tokens.invalidate(token);
            } catch (RestClientException exception) {
                throw unavailable();
            }
        }
        throw unavailable();
    }
    private PlatformUnavailableException unavailable() {
        return new PlatformUnavailableException(platform(), new IllegalStateException("Helix request failed"));
    }
}
