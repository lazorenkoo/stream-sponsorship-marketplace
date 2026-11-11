package com.streambid.inventory;

import com.streambid.offer.OfferRectangle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record AdPlacement(UUID id, UUID channelId, String title, String position, int widthPx, int heightPx,
        int durationSeconds, BigDecimal minimumPrice, String currency, boolean negotiable, int capacity,
        OfferRectangle zone, boolean active, long version, Instant createdAt, Instant updatedAt) {
    public AdPlacement {
        Objects.requireNonNull(id, "Placement ID is required");
        Objects.requireNonNull(channelId, "Channel ID is required");
        title = requiredText(title, "Title");
        position = requiredText(position, "Position");
        if (widthPx < 1 || widthPx > 7680 || heightPx < 1 || heightPx > 4320
                || durationSeconds < 1 || durationSeconds > 604800 || capacity < 1 || capacity > 100) {
            throw new IllegalArgumentException("Invalid placement dimensions, duration or capacity");
        }
        if (minimumPrice == null && !negotiable) {
            throw new IllegalArgumentException("A price or negotiation option is required");
        }
        if (minimumPrice != null) {
            minimumPrice = minimumPrice.setScale(2, RoundingMode.UNNECESSARY);
            if (minimumPrice.signum() < 0 || minimumPrice.precision() > 12) {
                throw new IllegalArgumentException("Invalid minimum price");
            }
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must contain three uppercase letters");
        }
        Objects.requireNonNull(createdAt, "Creation time is required");
        Objects.requireNonNull(updatedAt, "Update time is required");
        if (version < 0 || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Invalid placement version or timestamps");
        }
        createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        updatedAt = updatedAt.truncatedTo(ChronoUnit.MICROS);
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank() || value.strip().length() > 120
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must contain 1 to 120 printable characters");
        }
        return value.strip();
    }
}
