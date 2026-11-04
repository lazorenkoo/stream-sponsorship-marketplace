package com.streambid.campaign;

import com.streambid.platform.StreamingPlatform;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CampaignTargeting(UUID campaignId, Set<StreamingPlatform> platforms) {
    public CampaignTargeting {
        Objects.requireNonNull(campaignId, "Campaign ID is required");
        platforms = Set.copyOf(platforms);
    }
}
