package com.streambid.campaign;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class CampaignRepository {
    private final DSLContext database;

    public CampaignRepository(DSLContext database) {
        this.database = database;
    }

    public void insert(Campaign campaign) {
        database.execute("""
                INSERT INTO campaigns (id, advertiser_id, title, description, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz), ?)
                """, campaign.id(), campaign.advertiserId(), campaign.title(), campaign.description(), campaign.status().name(),
                OffsetDateTime.ofInstant(campaign.createdAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(campaign.updatedAt(), ZoneOffset.UTC), campaign.version());
    }

    public Optional<Campaign> updateDraft(Campaign draft, long expectedVersion) {
        return database.fetchOptional("""
                UPDATE campaigns SET title = ?, description = ?,
                    updated_at = GREATEST(updated_at, CAST(? AS timestamptz)), version = version + 1
                WHERE id = ? AND advertiser_id = ? AND status = 'DRAFT' AND version = ?
                RETURNING *
                """, draft.title(), draft.description(), OffsetDateTime.ofInstant(draft.updatedAt(), ZoneOffset.UTC),
                draft.id(), draft.advertiserId(), expectedVersion).map(this::map);
    }
    public Optional<Campaign> findOwned(UUID id, UUID advertiser) {
        return database.fetchOptional("SELECT * FROM campaigns WHERE id = ? AND advertiser_id = ?", id, advertiser)
                .map(this::map);
    }

    public List<Campaign> listOwned(UUID advertiser, Instant before, UUID beforeId, int limit) {
        if (limit < 1 || limit > 100 || (before == null) != (beforeId == null)) {
            throw new IllegalArgumentException("Limit must be 1 to 100 and cursor requires both time and ID");
        }
        var time = before == null ? null : OffsetDateTime.ofInstant(
                before.truncatedTo(java.time.temporal.ChronoUnit.MICROS), ZoneOffset.UTC);
        return database.fetch("""
                SELECT * FROM campaigns WHERE advertiser_id = ?
                  AND (CAST(? AS timestamptz) IS NULL OR (created_at, id) < (CAST(? AS timestamptz), CAST(? AS uuid)))
                ORDER BY created_at DESC, id DESC LIMIT ?
                """, advertiser, time, time, beforeId, limit).map(this::map);
    }

    private Campaign map(Record row) {
        return new Campaign(row.get("id", UUID.class), row.get("advertiser_id", UUID.class),
                row.get("title", String.class), row.get("description", String.class),
                CampaignStatus.valueOf(row.get("status", String.class)),
                row.get("created_at", OffsetDateTime.class).toInstant(), row.get("updated_at", OffsetDateTime.class).toInstant(), row.get("version", Long.class));
    }
}
