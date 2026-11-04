package com.streambid.campaign;

import com.streambid.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CampaignDraftService {
    private final CampaignRepository campaigns;
    private final UserRepository users;
    private final CampaignEventRepository events;
    private final java.time.Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CampaignDraftService(CampaignRepository campaigns, UserRepository users, CampaignEventRepository events) {
        this(campaigns, users, events, java.time.Clock.systemUTC());
    }

    CampaignDraftService(CampaignRepository campaigns, UserRepository users, CampaignEventRepository events, java.time.Clock clock) {
        this.campaigns = campaigns;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Campaign create(String email, CampaignDraftRequest request) {
        var user = requireAdvertiser(email);
        var now = clock.instant();
        Campaign draft;
        try {
            draft = new Campaign(UUID.randomUUID(), user.id(), request.title(), request.description(),
                    CampaignStatus.DRAFT, now, now);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        campaigns.insert(draft);
        recordEvent(draft, CampaignEventType.CREATED);
        return draft;
    }
    @Transactional(readOnly = true)
    public Campaign findOwned(String email, UUID id) {
        var user = requireAdvertiser(email);
        return campaigns.findOwned(id, user.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public java.util.List<Campaign> listOwned(String email, Instant before, UUID beforeId, int limit, CampaignStatus status) {
        var user = requireAdvertiser(email);
        if (limit < 1 || limit > 100 || (before == null) != (beforeId == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be 1 to 100 and cursor requires time and ID");
        }
        return campaigns.listOwned(user.id(), before, beforeId, limit, status);
    }
    @Transactional
    public Campaign update(String email, UUID id, CampaignDraftUpdateRequest request) {
        var user = requireAdvertiser(email);
        var current = campaigns.findOwned(id, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var now = clock.instant();
        var updatedAt = now.isBefore(current.updatedAt()) ? current.updatedAt() : now;
        Campaign changed;
        try {
            changed = new Campaign(current.id(), current.advertiserId(), request.title(), request.description(),
                    current.status(), current.createdAt(), updatedAt, current.version());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        var saved = campaigns.updateDraft(changed, request.expectedVersion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Campaign has changed; reload before editing"));
        recordEvent(saved, CampaignEventType.EDITED);
        return saved;
    }
    @Transactional
    public Campaign archive(String email, UUID id, CampaignArchiveRequest request) {
        var user = requireAdvertiser(email);
        campaigns.findOwned(id, user.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var saved = campaigns.archiveOwned(id, user.id(), request.expectedVersion(), clock.instant())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Campaign has changed or is already archived"));
        recordEvent(saved, CampaignEventType.ARCHIVED);
        return saved;
    }
    @Transactional(readOnly = true)
    public java.util.List<CampaignEvent> history(String email, UUID id, Long beforeVersion, int limit) {
        var user = requireAdvertiser(email);
        campaigns.findOwned(id, user.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (limit < 1 || limit > 100 || (beforeVersion != null && beforeVersion < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "History limit must be 1 to 100 and version nonnegative");
        }
        return events.findOwned(id, user.id(), beforeVersion, limit);
    }
    private void recordEvent(Campaign campaign, CampaignEventType type) {
        events.append(new CampaignEvent(campaign.id(), campaign.version(), campaign.advertiserId(), type, campaign.updatedAt()));
    }

    private com.streambid.user.UserAccount requireAdvertiser(String email) {
        var user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!users.findRoles(user.id()).contains("ADVERTISER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return user;
    }
}
