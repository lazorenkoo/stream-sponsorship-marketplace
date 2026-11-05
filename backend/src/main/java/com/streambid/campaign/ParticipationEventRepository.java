package com.streambid.campaign;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ParticipationEventRepository {
    private final DSLContext database;

    public ParticipationEventRepository(DSLContext database) {
        this.database = database;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(CampaignParticipation request, UUID actor) {
        database.execute("""
                INSERT INTO participation_events(participation_id, version, actor_id, status, occurred_at)
                VALUES (?, ?, ?, ?, CAST(? AS timestamptz))
                """, request.id(), request.version(), actor, request.status().name(),
                OffsetDateTime.ofInstant(request.updatedAt(), ZoneOffset.UTC));
    }

    public List<ParticipationEvent> findForActor(UUID participationId, UUID actor, Long beforeVersion, int limit) {
        if (limit < 1 || limit > 100 || (beforeVersion != null && beforeVersion < 0)) {
            throw new IllegalArgumentException("Invalid participation history limit or version");
        }
        return database.fetch("""
                SELECT e.* FROM participation_events e
                JOIN campaign_participations p ON p.id = e.participation_id
                JOIN campaigns c ON c.id = p.campaign_id JOIN connected_channels ch ON ch.id = p.channel_id
                WHERE e.participation_id = ? AND (c.advertiser_id = ? OR ch.user_id = ?)
                  AND (CAST(? AS bigint) IS NULL OR e.version < ?) ORDER BY e.version DESC LIMIT ?
                """, participationId, actor, actor, beforeVersion, beforeVersion, limit)
                .map(row -> new ParticipationEvent(row.get("participation_id", UUID.class), row.get("version", Long.class),
                        row.get("actor_id", UUID.class), ParticipationStatus.valueOf(row.get("status", String.class)),
                        row.get("occurred_at", OffsetDateTime.class).toInstant()));
    }
}
