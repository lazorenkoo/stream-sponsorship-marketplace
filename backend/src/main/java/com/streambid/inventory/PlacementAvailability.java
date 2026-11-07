package com.streambid.inventory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record PlacementAvailability(UUID id, UUID placementId, Instant startsAt, Instant endsAt) {
    public PlacementAvailability {
        Objects.requireNonNull(id, "Availability ID is required");
        Objects.requireNonNull(placementId, "Placement ID is required");
        startsAt = Objects.requireNonNull(startsAt, "Start is required").truncatedTo(ChronoUnit.MICROS);
        endsAt = Objects.requireNonNull(endsAt, "End is required").truncatedTo(ChronoUnit.MICROS);
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Availability end must be after start");
        }
    }
}
