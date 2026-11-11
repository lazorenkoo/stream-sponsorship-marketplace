package com.streambid.platform.twitch;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("twitch.oauth")
public record TwitchOAuthProperties(URI redirectUri) {

    public TwitchOAuthProperties {
        if (redirectUri == null || redirectUri.getHost() == null || redirectUri.getFragment() != null
                || redirectUri.getUserInfo() != null) {
            throw new IllegalArgumentException("Invalid Twitch OAuth redirect URI");
        }
        boolean local = "localhost".equals(redirectUri.getHost()) || "127.0.0.1".equals(redirectUri.getHost());
        if (!"https".equals(redirectUri.getScheme()) && !(local && "http".equals(redirectUri.getScheme()))) {
            throw new IllegalArgumentException("Twitch OAuth redirect must use HTTPS or local HTTP");
        }
    }
}
