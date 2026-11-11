package com.streambid.platform;

public class ChannelNotFoundException extends RuntimeException {

    public ChannelNotFoundException(StreamingPlatform platform) {
        super("Channel not found on " + platform);
    }
}
