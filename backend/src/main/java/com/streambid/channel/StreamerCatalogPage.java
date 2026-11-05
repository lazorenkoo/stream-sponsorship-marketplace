package com.streambid.channel;

import com.streambid.campaign.CampaignCandidate;
import java.util.List;
import java.util.UUID;

public record StreamerCatalogPage(List<CampaignCandidate> streamers, UUID nextCursor, boolean hasMore) {
    public StreamerCatalogPage {
        streamers = List.copyOf(streamers);
    }
}
