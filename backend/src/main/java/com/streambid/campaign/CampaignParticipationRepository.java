package com.streambid.campaign;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class CampaignParticipationRepository {
    private final DSLContext database;

    public CampaignParticipationRepository(DSLContext database) {
        this.database = database;
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<CampaignParticipation> create(CampaignParticipation request) {
        return database.fetchOptional("""
                INSERT INTO campaign_participations
                    (id, campaign_id, channel_id, initiated_by, direction, status, version, created_at, updated_at)
                SELECT ?, c.id, ch.id, ?, ?, 'PENDING', 0, CAST(? AS timestamptz), CAST(? AS timestamptz)
                FROM campaigns c JOIN connected_channels ch ON ch.id = ?
                JOIN channel_credentials t ON t.channel_id = ch.id
                JOIN campaign_target_platforms p ON p.campaign_id = c.id AND p.platform = ch.platform
                WHERE c.id = ? AND c.status = 'PUBLISHED'
                  AND c.advertiser_id <> ch.user_id
                  AND ((? = 'APPLICATION' AND ch.user_id = ?)
                    OR (? = 'INVITATION' AND c.advertiser_id = ? AND EXISTS
                        (SELECT 1 FROM channel_listings l WHERE l.channel_id = ch.id AND l.listed)))
                FOR UPDATE OF c, ch
                ON CONFLICT (campaign_id, channel_id) DO NOTHING
                RETURNING *
                """, request.id(), request.initiatedBy(), request.direction().name(),
                OffsetDateTime.ofInstant(request.createdAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(request.createdAt(), ZoneOffset.UTC), request.channelId(), request.campaignId(),
                request.direction().name(), request.initiatedBy(), request.direction().name(), request.initiatedBy()).map(this::map);
    }

    public Optional<CampaignParticipation> findForActor(UUID id, UUID actor) {
        return database.fetchOptional("""
                SELECT p.* FROM campaign_participations p JOIN campaigns c ON c.id = p.campaign_id
                JOIN connected_channels ch ON ch.id = p.channel_id
                WHERE p.id = ? AND (c.advertiser_id = ? OR ch.user_id = ?)
                """, id, actor, actor).map(this::map);
    }
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockCampaign(UUID campaignId) {
        database.fetch("SELECT id FROM campaigns WHERE id = ? FOR UPDATE", campaignId);
    }
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<CampaignParticipation> decide(CampaignParticipation request, ParticipationStatus next,
            long expectedVersion, java.time.Instant now) {
        return database.fetchOptional("""
                WITH locked_campaign AS (
                    SELECT id, status FROM campaigns WHERE id = ? FOR UPDATE
                )
                UPDATE campaign_participations p SET status = ?, version = p.version + 1,
                    updated_at = GREATEST(p.updated_at, CAST(? AS timestamptz))
                FROM locked_campaign c
                WHERE p.id = ? AND p.campaign_id = c.id AND p.status = 'PENDING' AND p.version = ?
                  AND (? = 'WITHDRAWN' OR c.status = 'PUBLISHED')
                  AND (? <> 'ACCEPTED' OR EXISTS (
                      SELECT 1 FROM connected_channels ch JOIN channel_credentials t ON t.channel_id = ch.id
                      JOIN campaign_target_platforms target ON target.campaign_id = c.id AND target.platform = ch.platform
                      WHERE ch.id = p.channel_id FOR SHARE OF t))
                RETURNING p.*
                """, request.campaignId(), next.name(), OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                request.id(), expectedVersion, next.name(), next.name()).map(this::map);
    }

    public List<CampaignParticipation> listForActor(UUID actor, UUID after, int limit) {
        return listForActor(actor, after, limit, null, null, null);
    }

    public List<CampaignParticipation> listForActor(UUID actor, UUID after, int limit, UUID campaignId,
            ParticipationStatus status, ParticipationDirection direction) {
        if (limit < 1 || limit > 101) {
            throw new IllegalArgumentException("Participation query limit must be between 1 and 101");
        }
        return database.fetch("""
                SELECT p.* FROM campaign_participations p JOIN campaigns c ON c.id = p.campaign_id
                JOIN connected_channels ch ON ch.id = p.channel_id
                WHERE (c.advertiser_id = ? OR ch.user_id = ?)
                  AND (CAST(? AS uuid) IS NULL OR p.campaign_id = ?)
                  AND (CAST(? AS varchar) IS NULL OR p.status = ?)
                  AND (CAST(? AS varchar) IS NULL OR p.direction = ?)
                  AND (CAST(? AS uuid) IS NULL OR p.id > CAST(? AS uuid)) ORDER BY p.id LIMIT ?
                """, actor, actor, campaignId, campaignId, status == null ? null : status.name(),
                status == null ? null : status.name(), direction == null ? null : direction.name(),
                direction == null ? null : direction.name(), after, after, limit).map(this::map);
    }

    private CampaignParticipation map(Record row) {
        return new CampaignParticipation(row.get("id", UUID.class), row.get("campaign_id", UUID.class),
                row.get("channel_id", UUID.class), row.get("initiated_by", UUID.class),
                ParticipationDirection.valueOf(row.get("direction", String.class)),
                ParticipationStatus.valueOf(row.get("status", String.class)), row.get("version", Long.class),
                row.get("created_at", OffsetDateTime.class).toInstant(), row.get("updated_at", OffsetDateTime.class).toInstant());
    }
}
