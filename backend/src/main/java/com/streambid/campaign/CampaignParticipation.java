package com.streambid.campaign;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record CampaignParticipation(UUID id, UUID campaignId, UUID channelId, UUID initiatedBy,
        ParticipationDirection direction, ParticipationStatus status, long version,
        Instant createdAt, Instant updatedAt) {
    public CampaignParticipation {
        Objects.requireNonNull(id, "Participation ID is required");
        Objects.requireNonNull(campaignId, "Campaign ID is required");
        Objects.requireNonNull(channelId, "Channel ID is required");
        Objects.requireNonNull(initiatedBy, "Initiator is required");
        Objects.requireNonNull(direction, "Direction is required");
        Objects.requireNonNull(status, "Status is required");
        Objects.requireNonNull(createdAt, "Creation time is required");
        Objects.requireNonNull(updatedAt, "Update time is required");
        if (version < 0 || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Invalid participation version or timestamps");
        }
        createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        updatedAt = updatedAt.truncatedTo(ChronoUnit.MICROS);
    }
}
