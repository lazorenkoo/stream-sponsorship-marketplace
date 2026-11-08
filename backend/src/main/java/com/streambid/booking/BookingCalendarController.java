package com.streambid.booking;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingCalendarController {
    private final BookingCalendarService calendar;
    public BookingCalendarController(BookingCalendarService calendar) { this.calendar = calendar; }

    @GetMapping("/api/catalog/channels/{id}/reservations")
    public ResponseEntity<ReservationPage> list(Principal principal, @PathVariable UUID id,
            @RequestParam Instant from, @RequestParam Instant to, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(calendar.list(principal.getName(), id, from, to, cursor, limit));
    }
}
