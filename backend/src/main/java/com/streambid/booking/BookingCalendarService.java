package com.streambid.booking;

import com.streambid.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingCalendarService {
    private final BookingCalendarRepository calendar;
    private final UserRepository users;
    public BookingCalendarService(BookingCalendarRepository calendar, UserRepository users) {
        this.calendar = calendar;
        this.users = users;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReservationPage list(String email, UUID channel, Instant from, Instant to, String cursor, int limit) {
        UUID actor = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).id();
        if (!calendar.visible(channel, actor)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (from == null || to == null || !to.isAfter(from) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0
                || limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid calendar range or limit");
        UUID after = null;
        try {
            if (cursor != null) {
                if (cursor.length() > 64) throw new IllegalArgumentException();
                after = UUID.fromString(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII));
            }
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid calendar cursor");
        }
        var entries = calendar.list(channel, from, to, after, limit + 1);
        boolean more = entries.size() > limit;
        var page = entries.subList(0, Math.min(limit, entries.size()));
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString(
                page.getLast().cursor().toString().getBytes(StandardCharsets.US_ASCII)) : null;
        return new ReservationPage(page.stream().map(BookingCalendarRepository.Entry::window).toList(), more, next);
    }
}
