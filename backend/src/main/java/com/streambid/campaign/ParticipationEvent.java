package com.streambid.campaign;

import java.time.Instant;
import java.util.UUID;

public record ParticipationEvent(UUID participationId, long version, UUID actorId,
        ParticipationStatus status, Instant occurredAt) { }
