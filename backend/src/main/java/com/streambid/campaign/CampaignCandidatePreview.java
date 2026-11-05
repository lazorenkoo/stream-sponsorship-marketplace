package com.streambid.campaign;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CampaignCandidatePreview(UUID campaignId, long campaignVersion, List<CampaignCandidate> candidates,
                                       UUID nextCursor, boolean hasMore) {
    public CampaignCandidatePreview {
        Objects.requireNonNull(campaignId, "Campaign ID is required");
        if (campaignVersion < 0) {
            throw new IllegalArgumentException("Campaign version cannot be negative");
        }
        candidates = List.copyOf(candidates);
        if (hasMore != (nextCursor != null)) {
            throw new IllegalArgumentException("A next cursor is required when more candidates exist");
        }
    }
}
