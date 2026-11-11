package com.streambid.booking;

import com.streambid.offer.OfferVersionRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdBookingController {
    private final AdBookingService bookings;

    public AdBookingController(AdBookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/api/offers/{id}/accept")
    public ResponseEntity<BookingView> accept(Principal principal, @PathVariable UUID id,
            @Valid @RequestBody OfferVersionRequest request) {
        var saved = bookings.accept(principal.getName(), id, request);
        return ResponseEntity.created(URI.create("/api/bookings/" + saved.id())).cacheControl(CacheControl.noStore()).body(saved);
    }

    @PostMapping("/api/bookings/{id}/cancel")
    public ResponseEntity<BookingView> cancel(Principal principal, @PathVariable UUID id,
            @Valid @RequestBody CancelBookingRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bookings.cancel(principal.getName(), id, request));
    }

    @GetMapping("/api/bookings/{id}")
    public ResponseEntity<BookingView> find(Principal principal, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bookings.find(principal.getName(), id));
    }

    @GetMapping("/api/bookings/{id}/cancellation")
    public ResponseEntity<BookingCancellationView> cancellation(Principal principal, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bookings.cancellation(principal.getName(), id));
    }

    @GetMapping("/api/bookings")
    public ResponseEntity<BookingPage> list(Principal principal, @RequestParam(required = false) UUID after,
            @RequestParam(required = false) BookingStatus status, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bookings.list(principal.getName(), after, status, limit));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && "23P01".equals(sql.getSQLState())) {
                return ResponseEntity.status(409).cacheControl(CacheControl.noStore())
                        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Reserved area overlaps an existing booking"));
            }
        }
        throw exception;
    }
}
