package com.streambid.campaign;

import java.util.List;
import java.util.UUID;

public record ParticipationPage(List<CampaignParticipation> requests, boolean hasMore, UUID nextCursor) {
    public ParticipationPage {
        requests = List.copyOf(requests);
    }
}
