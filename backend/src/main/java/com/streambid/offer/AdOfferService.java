package com.streambid.offer;

import com.streambid.user.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdOfferService {
    private final AdOfferRepository offers;
    private final UserRepository users;
    private final Clock clock = Clock.systemUTC();

    public AdOfferService(AdOfferRepository offers, UserRepository users) {
        this.offers = offers;
        this.users = users;
    }

    @Transactional
    public OfferView create(String email, CreateOfferRequest request) {
        UUID actor = actor(email);
        if (!users.findRoles(actor).contains("ADVERTISER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        UUID id = offers.createDraft(UUID.randomUUID(), actor, request.placementId(), request.terms(), clock.instant())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Placement or creative is unavailable"));
        return offers.findForParty(id, actor).orElseThrow();
    }

    @Transactional
    public OfferView send(String email, UUID id, OfferVersionRequest request) {
        UUID actor = actor(email);
        if (!users.findRoles(actor).contains("ADVERTISER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        offers.findForParty(id, actor).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!offers.sendDraft(id, actor, request.expectedVersion(), clock.instant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Offer changed or placement conditions are unavailable");
        }
        return offers.findForParty(id, actor).orElseThrow();
    }

    public OfferView find(String email, UUID id) {
        return offers.findForParty(id, actor(email)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<OfferView> list(String email, UUID after, int limit) {
        UUID actor = actor(email);
        if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid offer limit");
        return offers.listForParty(actor, after, limit);
    }

    private UUID actor(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).id();
    }
}
