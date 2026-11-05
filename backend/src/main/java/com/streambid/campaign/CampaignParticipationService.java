package com.streambid.campaign;

import com.streambid.channel.ChannelRepository;
import com.streambid.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
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
    private final Clock clock = Clock.systemUTC();

    public CampaignParticipationService(CampaignParticipationRepository requests, UserRepository users,
            ChannelRepository channels, CampaignDraftService campaigns, CampaignCatalogRepository catalog) {
        this.requests = requests;
        this.users = users;
        this.channels = channels;
        this.campaigns = campaigns;
        this.catalog = catalog;
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
        return requests.create(request).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "Channel is ineligible or already has a request for this campaign"));
    }
}
