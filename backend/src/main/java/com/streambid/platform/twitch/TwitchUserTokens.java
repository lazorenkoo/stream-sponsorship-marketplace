package com.streambid.platform.twitch;

import java.time.Instant;

public record TwitchUserTokens(String accessToken, String refreshToken, Instant expiresAt) {

    @Override
    public String toString() {
        return "TwitchUserTokens[redacted]";
    }
}
