package com.streambid.channel;

import com.streambid.campaign.CampaignCandidate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StreamerCatalogPage(List<CampaignCandidate> streamers, UUID nextCursor, boolean hasMore,
        Map<UUID, Audience> audience) {
    public StreamerCatalogPage {
        streamers = List.copyOf(streamers);
        audience = Map.copyOf(audience);
    }
    public record Audience(Instant measuredAt, com.streambid.platform.StreamState state, Long viewers,
            BigDecimal averageViewers, Long peakViewers, long viewerSamples) {}
}
