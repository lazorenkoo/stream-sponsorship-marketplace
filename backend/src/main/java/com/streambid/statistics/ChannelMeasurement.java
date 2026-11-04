package com.streambid.statistics;

import com.streambid.platform.StreamState;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ChannelMeasurement(UUID id, UUID channelId, Instant measuredAt, StreamState state, Long viewers) {
    public ChannelMeasurement {
        Objects.requireNonNull(id, "Measurement ID is required");
        Objects.requireNonNull(channelId, "Channel ID is required");
        Objects.requireNonNull(measuredAt, "Measurement time is required");
        Objects.requireNonNull(state, "Stream state is required");
        if (viewers != null && viewers < 0) {
            throw new IllegalArgumentException("Viewer count must not be negative");
        }
        if (state != StreamState.LIVE && viewers != null) {
            throw new IllegalArgumentException("Viewer count requires a live stream");
        }
    }
}
