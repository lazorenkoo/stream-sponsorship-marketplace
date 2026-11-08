package com.streambid.booking;

import com.streambid.offer.AdOfferRepository;
import com.streambid.offer.OfferVersionRequest;
import com.streambid.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdBookingService {
    private final AdBookingRepository bookings;
    private final AdOfferRepository offers;
    private final UserRepository users;
    private final Clock clock = Clock.systemUTC();

    public AdBookingService(AdBookingRepository bookings, AdOfferRepository offers, UserRepository users) {
        this.bookings = bookings;
        this.offers = offers;
        this.users = users;
    }

    @Transactional
    public BookingView accept(String email, UUID offer, OfferVersionRequest request) {
        UUID actor = actor(email);
        offers.findForParty(offer, actor).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        UUID id = bookings.accept(offer, actor, request.expectedVersion(), clock.instant())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Offer changed or reservation is unavailable"));
        return bookings.findForParty(id, actor).orElseThrow();
    }

    public BookingView find(String email, UUID id) {
        return bookings.findForParty(id, actor(email)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private UUID actor(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).id();
    }
}
