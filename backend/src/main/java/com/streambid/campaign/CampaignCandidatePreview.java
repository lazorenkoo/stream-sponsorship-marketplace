package com.streambid.campaign;

import java.util.List;
import java.util.UUID;

public record CampaignCandidatePreview(UUID campaignId, long campaignVersion, List<CampaignCandidate> candidates) {
    public CampaignCandidatePreview {
        candidates = List.copyOf(candidates);
    }
}
