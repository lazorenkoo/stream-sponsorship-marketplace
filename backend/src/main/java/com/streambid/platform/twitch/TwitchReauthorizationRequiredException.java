package com.streambid.platform.twitch;

public class TwitchReauthorizationRequiredException extends RuntimeException {

    public TwitchReauthorizationRequiredException() {
        super("Twitch connection requires authorization");
    }
}
