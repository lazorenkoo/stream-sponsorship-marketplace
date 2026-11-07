package com.streambid.creative;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdCreativeRepository {
    private final DSLContext database;

    public AdCreativeRepository(DSLContext database) {
        this.database = database;
    }

    @Transactional
    public AdCreative insertPending(AdCreative creative) {
        return database.fetchSingle("""
                INSERT INTO ad_creatives(id, advertiser_id, title, object_key, format, width_px, height_px,
                    byte_size, sha256, status, version, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, CAST(? AS timestamptz)) RETURNING *
                """, creative.id(), creative.advertiserId(), creative.title(), creative.storageKey(), creative.format().name(),
                creative.widthPx(), creative.heightPx(), creative.byteSize(), creative.sha256(),
                OffsetDateTime.ofInstant(creative.createdAt(), ZoneOffset.UTC)).map(this::map);
    }

    public Optional<AdCreative> findOwned(UUID id, UUID advertiser) {
        return database.fetchOptional("SELECT * FROM ad_creatives WHERE id = ? AND advertiser_id = ?", id, advertiser)
                .map(this::map);
    }
    public Optional<AdCreative> find(UUID id) {
        return database.fetchOptional("SELECT * FROM ad_creatives WHERE id = ?", id).map(this::map);
    }

    public List<AdCreative> pending(UUID after, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid moderation queue limit");
        return database.fetch("""
                SELECT * FROM ad_creatives WHERE status = 'PENDING'
                  AND (CAST(? AS uuid) IS NULL OR id > ?) ORDER BY id LIMIT ?
                """, after, after, limit).map(this::map);
    }

    @Transactional
    public Optional<AdCreative> review(UUID id, UUID reviewer, CreativeStatus status, String reason, long expectedVersion,
            java.time.Instant now) {
        return database.fetchOptional("""
                WITH reviewed AS (
                    UPDATE ad_creatives SET status = ?, version = version + 1, reviewed_by = ?,
                        reviewed_at = GREATEST(created_at, CAST(? AS timestamptz)), rejection_reason = ?
                    WHERE id = ? AND status = 'PENDING' AND version = ? AND ? IN ('APPROVED', 'REJECTED')
                      AND EXISTS (SELECT 1 FROM user_roles WHERE user_id = ? AND role = 'ADMIN')
                    RETURNING *
                ), logged AS (
                    INSERT INTO creative_review_events(creative_id, version, reviewer_id, status, reason, occurred_at)
                    SELECT id, version, reviewed_by, status, rejection_reason, reviewed_at FROM reviewed
                ) SELECT * FROM reviewed
                """, status.name(), reviewer, OffsetDateTime.ofInstant(now, ZoneOffset.UTC), reason, id, expectedVersion,
                status.name(), reviewer).map(this::map);
    }

    public List<AdCreative> listOwned(UUID advertiser, UUID after, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Creative limit must be between 1 and 100");
        }
        return database.fetch("""
                SELECT * FROM ad_creatives WHERE advertiser_id = ?
                  AND (CAST(? AS uuid) IS NULL OR id > ?) ORDER BY id LIMIT ?
                """, advertiser, after, after, limit).map(this::map);
    }

    private AdCreative map(Record row) {
        return new AdCreative(row.get("id", UUID.class), row.get("advertiser_id", UUID.class), row.get("title", String.class),
                CreativeFormat.valueOf(row.get("format", String.class)), row.get("width_px", Integer.class),
                row.get("height_px", Integer.class), row.get("byte_size", Long.class), row.get("sha256", String.class),
                CreativeStatus.valueOf(row.get("status", String.class)), row.get("version", Long.class),
                row.get("created_at", OffsetDateTime.class).toInstant());
    }
}
