package com.streambid.platform.twitch;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("twitch")
public record TwitchProperties(String clientId, String clientSecret, String apiUrl, String tokenUrl) {

    @Override
    public String toString() {
        return "TwitchProperties[redacted]";
    }
}
