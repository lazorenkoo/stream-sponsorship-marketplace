package com.streambid.campaign;

import com.streambid.channel.ChannelRepository;
import com.streambid.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CampaignParticipationService {
    private final CampaignParticipationRepository requests;
    private final UserRepository users;
    private final ChannelRepository channels;
    private final CampaignDraftService campaigns;
    private final CampaignCatalogRepository catalog;
    private final ParticipationEventRepository events;
    private final Clock clock = Clock.systemUTC();

    public CampaignParticipationService(CampaignParticipationRepository requests, UserRepository users,
            ChannelRepository channels, CampaignDraftService campaigns, CampaignCatalogRepository catalog,
            ParticipationEventRepository events) {
        this.requests = requests;
        this.users = users;
        this.channels = channels;
        this.campaigns = campaigns;
        this.catalog = catalog;
        this.events = events;
    }

    @Transactional
    public CampaignParticipation create(String email, UUID campaignId, UUID channelId, ParticipationDirection direction) {
        var user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String role = direction == ParticipationDirection.APPLICATION ? "STREAMER" : "ADVERTISER";
        if (!users.findRoles(user.id()).contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (direction == ParticipationDirection.APPLICATION) {
            channels.find(channelId).filter(channel -> channel.userId().equals(user.id()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            catalog.find(campaignId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } else {
            campaigns.findOwned(email, campaignId);
        }
        var now = clock.instant();
        var request = new CampaignParticipation(UUID.randomUUID(), campaignId, channelId, user.id(), direction,
                ParticipationStatus.PENDING, 0, now, now);
        var saved = requests.create(request).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "Channel is ineligible or already has a request for this campaign"));
        events.append(saved, user.id());
        return saved;
    }
    @Transactional(readOnly = true)
    public CampaignParticipation find(String email, UUID id) {
        return requests.findForActor(id, actorId(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ParticipationPage list(String email, UUID after, int limit, UUID campaignId,
            ParticipationStatus status, ParticipationDirection direction) {
        UUID actor = actorId(email);
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participation limit must be between 1 and 100");
        }
        var rows = requests.listForActor(actor, after, limit + 1, campaignId, status, direction);
        boolean hasMore = rows.size() > limit;
        var page = rows.subList(0, Math.min(rows.size(), limit));
        return new ParticipationPage(page, hasMore, hasMore ? page.getLast().id() : null);
    }

    private UUID actorId(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).id();
    }
    @Transactional(readOnly = true)
    public List<ParticipationEvent> history(String email, UUID id, Long beforeVersion, int limit) {
        UUID actor = actorId(email);
        requests.findForActor(id, actor).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (limit < 1 || limit > 100 || (beforeVersion != null && beforeVersion < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid participation history limit or version");
        }
        return events.findForActor(id, actor, beforeVersion, limit);
    }
    @Transactional
    public CampaignParticipation decide(String email, UUID id, ParticipationDecisionRequest decision) {
        UUID actor = actorId(email);
        var request = requests.findForActor(id, actor)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean initiator = request.initiatedBy().equals(actor);
        boolean withdraw = decision.action() == ParticipationAction.WITHDRAW;
        if (withdraw != initiator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient can accept or reject a request");
        }
        var next = switch (decision.action()) {
            case ACCEPT -> ParticipationStatus.ACCEPTED;
            case REJECT -> ParticipationStatus.REJECTED;
            case WITHDRAW -> ParticipationStatus.WITHDRAWN;
        };
        requests.lockCampaign(request.campaignId());
        if (next == ParticipationStatus.ACCEPTED) {
            var channel = channels.find(request.channelId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Channel is unavailable"));
            channels.lockOwned(channel.id(), channel.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Channel is unavailable"));
        }
        var saved = requests.decide(request, next, decision.expectedVersion(), clock.instant())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Request has changed or is no longer eligible for this action"));
        events.append(saved, actor);
        return saved;
    }
}
