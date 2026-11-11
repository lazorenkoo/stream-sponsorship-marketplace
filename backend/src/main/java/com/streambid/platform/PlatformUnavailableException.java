package com.streambid.platform;

public class PlatformUnavailableException extends RuntimeException {

    public PlatformUnavailableException(StreamingPlatform platform, Throwable cause) {
        super(platform + " is temporarily unavailable", cause);
    }
}
