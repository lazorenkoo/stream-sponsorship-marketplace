package com.streambid.campaign;

import com.streambid.platform.StreamingPlatform;
import java.util.List;
import java.util.UUID;

public record CampaignTargetingView(UUID campaignId, long version, List<StreamingPlatform> platforms) {
    public CampaignTargetingView {
        platforms = List.copyOf(platforms);
    }
}
