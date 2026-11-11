package com.streambid.platform;

import java.time.Instant;
import java.util.Objects;

public record StreamStatus(String channelId, StreamState state, Long viewers, Instant measuredAt) {

    public StreamStatus {
        Objects.requireNonNull(channelId, "Channel ID is required");
        Objects.requireNonNull(state, "Stream state is required");
        Objects.requireNonNull(measuredAt, "Measurement time is required");
        if (viewers != null && viewers < 0) {
            throw new IllegalArgumentException("Viewer count must not be negative");
        }
        if (state != StreamState.LIVE && viewers != null) {
            throw new IllegalArgumentException("Viewer count requires a live stream");
        }
    }
}
