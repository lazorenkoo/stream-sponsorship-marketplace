package com.streambid.platform;

import java.time.Instant;

public class PlatformRateLimitException extends PlatformUnavailableException {

    private final Instant retryAt;

    public PlatformRateLimitException(StreamingPlatform platform, Instant retryAt) {
        super(platform, new IllegalStateException("API rate limit exceeded"));
        this.retryAt = retryAt;
    }

    public Instant retryAt() {
        return retryAt;
    }
}
