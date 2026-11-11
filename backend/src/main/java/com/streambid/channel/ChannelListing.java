package com.streambid.channel;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ChannelListing(UUID channelId, boolean listed, Instant updatedAt) {
    public ChannelListing {
        Objects.requireNonNull(channelId, "Channel ID is required");
    }
}
