package com.streambid.booking;

import com.streambid.offer.OfferRectangle;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record BookingWindow(Instant startsAt, Instant endsAt, OfferRectangle rectangle) {
    public BookingWindow {
        Objects.requireNonNull(startsAt, "Booking start is required");
        Objects.requireNonNull(endsAt, "Booking end is required");
        Objects.requireNonNull(rectangle, "Booking rectangle is required");
        startsAt = startsAt.truncatedTo(ChronoUnit.MICROS);
        endsAt = endsAt.truncatedTo(ChronoUnit.MICROS);
        if (!endsAt.isAfter(startsAt)) throw new IllegalArgumentException("Booking end must follow its start");
    }

    public boolean conflictsWith(BookingWindow other) {
        Objects.requireNonNull(other, "Other booking is required");
        return startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(endsAt) && rectangle.overlaps(other.rectangle);
    }
}
