package com.streambid.campaign;

import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CampaignTargetingService {
    private final CampaignDraftService drafts;
    private final CampaignRepository campaigns;
    private final CampaignTargetingRepository targeting;
    private final CampaignEventRepository events;
    private final Clock clock;

    @Autowired
    public CampaignTargetingService(CampaignDraftService drafts, CampaignRepository campaigns,
            CampaignTargetingRepository targeting, CampaignEventRepository events) {
        this(drafts, campaigns, targeting, events, Clock.systemUTC());
    }

    CampaignTargetingService(CampaignDraftService drafts, CampaignRepository campaigns,
            CampaignTargetingRepository targeting, CampaignEventRepository events, Clock clock) {
        this.drafts = drafts;
        this.campaigns = campaigns;
        this.targeting = targeting;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CampaignTargetingView replace(String email, UUID id, CampaignTargetingRequest request) {
        var current = drafts.findOwned(email, id);
        var selection = new CampaignTargeting(id, request.platforms());
        var now = clock.instant();
        var updatedAt = now.isBefore(current.updatedAt()) ? current.updatedAt() : now;
        var changed = new Campaign(id, current.advertiserId(), current.title(), current.description(),
                current.status(), current.createdAt(), updatedAt, current.version());
        var saved = campaigns.updateDraft(changed, request.expectedVersion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Campaign has changed or is archived"));
        targeting.replaceInTransaction(selection);
        events.append(new CampaignEvent(id, saved.version(), saved.advertiserId(),
                CampaignEventType.TARGETING_CHANGED, saved.updatedAt()));
        return new CampaignTargetingView(id, saved.version(), selection.platforms().stream().sorted().toList());
    }
}
