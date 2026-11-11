package com.streambid.campaign;

import com.streambid.platform.StreamingPlatform;
import java.util.Objects;

public final class CampaignPlatformMatcher {
    private CampaignPlatformMatcher() {
    }

    public static CampaignPlatformMatch evaluate(CampaignTargeting targeting, StreamingPlatform channelPlatform,
            boolean channelAuthorized) {
        Objects.requireNonNull(targeting, "Campaign targeting is required");
        Objects.requireNonNull(channelPlatform, "Channel platform is required");
        if (!channelAuthorized) {
            return CampaignPlatformMatch.AUTHORIZATION_REQUIRED;
        }
        if (targeting.platforms().isEmpty()) {
            return CampaignPlatformMatch.NO_PLATFORMS_SELECTED;
        }
        return targeting.platforms().contains(channelPlatform)
                ? CampaignPlatformMatch.MATCHED : CampaignPlatformMatch.PLATFORM_NOT_SELECTED;
    }
}
