package com.streambid.campaign;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record CampaignEvent(UUID campaignId, long campaignVersion, UUID actorId,
        CampaignEventType type, Instant occurredAt) {
    public CampaignEvent {
        Objects.requireNonNull(campaignId, "Campaign ID is required");
        Objects.requireNonNull(actorId, "Actor ID is required");
        Objects.requireNonNull(type, "Event type is required");
        Objects.requireNonNull(occurredAt, "Event time is required");
        if (campaignVersion < 0) {
            throw new IllegalArgumentException("Campaign version must not be negative");
        }
        occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
    }
}
