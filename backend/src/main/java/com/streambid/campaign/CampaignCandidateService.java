package com.streambid.campaign;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CampaignCandidateService {
    private final CampaignDraftService drafts;
    private final CampaignTargetingRepository targeting;
    private final CampaignCandidateRepository candidates;

    public CampaignCandidateService(CampaignDraftService drafts, CampaignTargetingRepository targeting,
            CampaignCandidateRepository candidates) {
        this.drafts = drafts;
        this.targeting = targeting;
        this.candidates = candidates;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CampaignCandidatePreview preview(String email, UUID id, UUID after, Long expectedVersion, int limit) {
        var campaign = drafts.findOwned(email, id);
        if (campaign.status() != CampaignStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidate preview requires a draft campaign");
        }
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Candidate limit must be between 1 and 100");
        }
        if (expectedVersion != null && expectedVersion < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected campaign version cannot be negative");
        }
        if (expectedVersion != null && expectedVersion != campaign.version()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Campaign changed; restart candidate preview");
        }
        var selected = targeting.readOwned(id, campaign.advertiserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var rows = candidates.find(selected.platforms(), after, limit + 1);
        boolean hasMore = rows.size() > limit;
        var page = List.copyOf(rows.subList(0, Math.min(rows.size(), limit)));
        UUID nextCursor = hasMore ? page.getLast().channelId() : null;
        return new CampaignCandidatePreview(id, campaign.version(), page, nextCursor, hasMore);
    }
}
