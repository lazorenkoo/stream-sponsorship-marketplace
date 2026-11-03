package com.streambid.platform.twitch;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchAuthorizationService {

    private final TwitchProperties properties;
    private final TwitchOAuthProperties oauth;
    private final TwitchOAuthState states;

    public TwitchAuthorizationService(TwitchProperties properties, TwitchOAuthProperties oauth, TwitchOAuthState states) {
        this.properties = properties;
        this.oauth = oauth;
        this.states = states;
    }

    public String authorizationUrl(HttpSession session, UUID owner) {
        if (!StringUtils.hasText(properties.clientId())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Twitch is not configured");
        }
        return UriComponentsBuilder.fromUriString("https://id.twitch.tv/oauth2/authorize")
                .queryParam("response_type", "code").queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", oauth.redirectUri()).queryParam("scope", "")
                .queryParam("force_verify", "true").queryParam("state", states.begin(session, owner))
                .build().encode().toUriString();
    }
}
