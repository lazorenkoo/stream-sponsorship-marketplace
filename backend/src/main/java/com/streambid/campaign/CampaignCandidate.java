package com.streambid.campaign;

import com.streambid.platform.StreamingPlatform;
import java.util.Objects;
import java.util.UUID;

public record CampaignCandidate(UUID channelId, StreamingPlatform platform, String displayName) {
    public CampaignCandidate {
        Objects.requireNonNull(channelId, "Channel ID is required");
        Objects.requireNonNull(platform, "Platform is required");
        Objects.requireNonNull(displayName, "Display name is required");
        if (displayName.isBlank() || displayName.length() > 255) {
            throw new IllegalArgumentException("Display name must contain 1 to 255 characters");
        }
    }
}
