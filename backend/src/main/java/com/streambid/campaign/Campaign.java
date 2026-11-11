package com.streambid.campaign;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record Campaign(UUID id, UUID advertiserId, String title, String description,
        CampaignStatus status, Instant createdAt, Instant updatedAt, long version) {
    public Campaign(UUID id, UUID advertiserId, String title, String description,
            CampaignStatus status, Instant createdAt, Instant updatedAt) {
        this(id, advertiserId, title, description, status, createdAt, updatedAt, 0);
    }
    public Campaign {
        Objects.requireNonNull(id, "Campaign ID is required");
        Objects.requireNonNull(advertiserId, "Advertiser ID is required");
        Objects.requireNonNull(title, "Title is required");
        Objects.requireNonNull(description, "Description is required");
        Objects.requireNonNull(status, "Campaign status is required");
        Objects.requireNonNull(createdAt, "Creation time is required");
        Objects.requireNonNull(updatedAt, "Update time is required");
        if (version < 0) {
            throw new IllegalArgumentException("Campaign version must not be negative");
        }
        title = title.strip();
        if (title.isBlank() || title.length() > 120 || title.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Title must contain 1 to 120 characters without control characters");
        }
        if (description.length() > 5000 || description.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Description must contain at most 5000 characters without null characters");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Update time must not precede creation time");
        }
        createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        updatedAt = updatedAt.truncatedTo(ChronoUnit.MICROS);
    }
}
