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

    public CampaignDraftService(CampaignRepository campaigns, UserRepository users) {
        this.campaigns = campaigns;
        this.users = users;
    }

    @Transactional
    public Campaign create(String email, CampaignDraftRequest request) {
        var user = requireAdvertiser(email);
        var now = Instant.now();
        Campaign draft;
        try {
            draft = new Campaign(UUID.randomUUID(), user.id(), request.title(), request.description(),
                    CampaignStatus.DRAFT, now, now);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        campaigns.insert(draft);
        return draft;
    }
    @Transactional(readOnly = true)
    public Campaign findOwned(String email, UUID id) {
        var user = requireAdvertiser(email);
        return campaigns.findOwned(id, user.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private com.streambid.user.UserAccount requireAdvertiser(String email) {
        var user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!users.findRoles(user.id()).contains("ADVERTISER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return user;
    }
}
