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

    @Transactional
    public BookingView cancel(String email, UUID id, CancelBookingRequest request) {
        UUID actor = actor(email);
        bookings.findForParty(id, actor).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String reason = request.reason().strip();
        if (reason.isBlank() || reason.length() > 500 || reason.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A printable cancellation reason is required");
        }
        if (!bookings.cancel(id, actor, request.expectedVersion(), reason, clock.instant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking changed, was cancelled or has already started");
        }
        return bookings.findForParty(id, actor).orElseThrow();
    }

    public BookingView find(String email, UUID id) {
        return bookings.findForParty(id, actor(email)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public BookingCancellationView cancellation(String email, UUID id) {
        UUID actor = actor(email);
        bookings.findForParty(id, actor).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return bookings.cancellationForParty(id, actor).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public BookingPage list(String email, UUID after, BookingStatus status, int limit) {
        UUID actor = actor(email);
        if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid booking limit");
        var entries = bookings.listForParty(actor, after, status, limit + 1);
        boolean more = entries.size() > limit;
        var page = entries.subList(0, Math.min(limit, entries.size()));
        return new BookingPage(page, more, more ? page.getLast().id() : null);
    }

    private UUID actor(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).id();
    }
}
