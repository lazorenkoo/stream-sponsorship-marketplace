package com.streambid.offer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record OfferTerms(UUID creativeId, BigDecimal price, String currency, int durationSeconds,
        OfferMode mode, Instant startsAt, String advertisingCategory, OfferRectangle rectangle) {
    public OfferTerms {
        Objects.requireNonNull(creativeId, "Creative is required");
        Objects.requireNonNull(mode, "Offer mode is required");
        Objects.requireNonNull(rectangle, "Advertising rectangle is required");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("Invalid offer price");
        try {
            price = price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException("Offer price must have at most two decimal places");
        }
        if (price.precision() > 12) throw new IllegalArgumentException("Offer price is too large");
        if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Invalid currency");
        if (durationSeconds < 1 || durationSeconds > 604800) throw new IllegalArgumentException("Invalid duration");
        if ((mode == OfferMode.SCHEDULED) != (startsAt != null)) throw new IllegalArgumentException("Invalid offer start");
        if (startsAt != null) startsAt = startsAt.truncatedTo(ChronoUnit.MICROS);
        if (advertisingCategory == null || advertisingCategory.length() > 40
                || !advertisingCategory.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Invalid advertising category");
        }
    }
}
