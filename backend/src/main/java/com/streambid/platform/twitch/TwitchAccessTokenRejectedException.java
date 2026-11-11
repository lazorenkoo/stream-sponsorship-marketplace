package com.streambid.platform.twitch;

public class TwitchAccessTokenRejectedException extends RuntimeException {

    public TwitchAccessTokenRejectedException() {
        super("Twitch access token was rejected");
    }
}
