package com.streambid.booking;

import static org.assertj.core.api.Assertions.*;
import com.streambid.offer.OfferRectangle;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookingWindowTest {
    @Test
    void conflictsRequireBothSharedTimeAndSharedArea() {
        var start = Instant.parse("2026-11-09T12:00:00Z");
        var area = new OfferRectangle(0, 0, 2000, 1000);
        var booked = new BookingWindow(start, start.plusSeconds(60), area);
        assertThat(booked.conflictsWith(new BookingWindow(start.plusSeconds(59), start.plusSeconds(120), area))).isTrue();
        assertThat(booked.conflictsWith(new BookingWindow(start.plusSeconds(60), start.plusSeconds(120), area))).isFalse();
        assertThat(booked.conflictsWith(new BookingWindow(start, start.plusSeconds(60),
                new OfferRectangle(2000, 0, 2000, 1000)))).isFalse();
        assertThat(booked.conflictsWith(new BookingWindow(start, start.plusSeconds(60),
                new OfferRectangle(1999, 999, 2000, 1000)))).isTrue();
    }

    @Test
    void rejectsEmptyIntervalsAfterDatabasePrecisionNormalization() {
        var start = Instant.parse("2026-11-09T12:00:00Z");
        var area = new OfferRectangle(0, 0, 100, 100);
        assertThatThrownBy(() -> new BookingWindow(start, start, area)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookingWindow(start, start.plusNanos(999), area)).isInstanceOf(IllegalArgumentException.class);
    }
}
