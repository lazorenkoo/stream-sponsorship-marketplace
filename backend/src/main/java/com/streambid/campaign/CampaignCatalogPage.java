package com.streambid.campaign;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CampaignCatalogPage(List<PublishedCampaign> campaigns, boolean hasMore,
        Instant nextBefore, UUID nextBeforeId) {
    public CampaignCatalogPage {
        campaigns = List.copyOf(campaigns);
    }
}
