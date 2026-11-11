package com.streambid.inventory;

import com.streambid.offer.OfferRectangle;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class AdPlacementRepository {
    private final DSLContext database;

    public AdPlacementRepository(DSLContext database) {
        this.database = database;
    }

    public Optional<AdPlacement> insertOwned(AdPlacement placement, UUID owner) {
        var zone = placement.zone();
        return database.fetchOptional("""
                INSERT INTO ad_placements (id, channel_id, title, position, width_px, height_px, duration_seconds,
                    minimum_price, currency, negotiable, capacity, zone_x, zone_y, zone_width, zone_height, active, version, created_at, updated_at)
                SELECT ?, c.id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, 0, CAST(? AS timestamptz), CAST(? AS timestamptz)
                FROM connected_channels c WHERE c.id = ? AND c.user_id = ? RETURNING *
                """, placement.id(), placement.title(), placement.position(), placement.widthPx(), placement.heightPx(),
                placement.durationSeconds(), placement.minimumPrice(), placement.currency(), placement.negotiable(), placement.capacity(),
                zone == null ? null : zone.x(), zone == null ? null : zone.y(),
                zone == null ? null : zone.width(), zone == null ? null : zone.height(), time(placement.createdAt()), time(placement.createdAt()), placement.channelId(), owner).map(this::map);
    }

    public Optional<AdPlacement> findOwned(UUID id, UUID owner) {
        return database.fetchOptional("""
                SELECT p.* FROM ad_placements p JOIN connected_channels c ON c.id = p.channel_id
                WHERE p.id = ? AND c.user_id = ?
                """, id, owner).map(this::map);
    }
    public Optional<AdPlacement> lockOwned(UUID id, UUID owner) {
        return database.fetchOptional("""
                SELECT p.* FROM ad_placements p JOIN connected_channels c ON c.id = p.channel_id
                WHERE p.id = ? AND c.user_id = ? FOR UPDATE OF p
                """, id, owner).map(this::map);
    }

    public boolean touchInactive(UUID id, UUID owner, long expectedVersion, java.time.Instant now) {
        return database.execute("""
                UPDATE ad_placements p SET version = p.version + 1,
                    updated_at = GREATEST(p.updated_at, CAST(? AS timestamptz))
                FROM connected_channels c WHERE p.channel_id = c.id AND c.user_id = ?
                    AND p.id = ? AND p.version = ? AND NOT p.active
                """, time(now), owner, id, expectedVersion) == 1;
    }

    public List<AdPlacement> listOwned(UUID owner, UUID after, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Placement limit must be between 1 and 100");
        }
        return database.fetch("""
                SELECT p.* FROM ad_placements p JOIN connected_channels c ON c.id = p.channel_id
                WHERE c.user_id = ? AND (CAST(? AS uuid) IS NULL OR p.id > CAST(? AS uuid))
                ORDER BY p.id LIMIT ?
                """, owner, after, after, limit).map(this::map);
    }

    public Optional<AdPlacement> updateOwned(AdPlacement placement, UUID owner, long expectedVersion) {
        var zone = placement.zone();
        return database.fetchOptional("""
                UPDATE ad_placements p SET title = ?, position = ?, width_px = ?, height_px = ?, duration_seconds = ?,
                    minimum_price = ?, currency = ?, negotiable = ?, capacity = ?, zone_x = ?, zone_y = ?, zone_width = ?, zone_height = ?,
                    updated_at = GREATEST(p.updated_at, CAST(? AS timestamptz)), version = p.version + 1
                FROM connected_channels c WHERE p.channel_id = c.id AND c.user_id = ? AND p.id = ? AND p.version = ? AND NOT p.active
                RETURNING p.*
                """, placement.title(), placement.position(), placement.widthPx(), placement.heightPx(), placement.durationSeconds(),
                placement.minimumPrice(), placement.currency(), placement.negotiable(), placement.capacity(),
                zone == null ? null : zone.x(), zone == null ? null : zone.y(),
                zone == null ? null : zone.width(), zone == null ? null : zone.height(), time(placement.updatedAt()), owner, placement.id(), expectedVersion).map(this::map);
    }

    private OffsetDateTime time(java.time.Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
    public Optional<AdPlacement> setActiveOwned(UUID id, UUID owner, boolean active, long expectedVersion, java.time.Instant now) {
        return database.fetchOptional("""
                UPDATE ad_placements p SET active = ?, version = p.version + 1,
                    updated_at = GREATEST(p.updated_at, CAST(? AS timestamptz))
                FROM connected_channels c WHERE p.channel_id = c.id AND c.user_id = ? AND p.id = ? AND p.version = ?
                  AND (? = FALSE OR (
                    EXISTS (SELECT 1 FROM channel_credentials t WHERE t.channel_id = c.id FOR SHARE OF t)
                    AND EXISTS (SELECT 1 FROM channel_listings l WHERE l.channel_id = c.id AND l.listed)
                    AND EXISTS (SELECT 1 FROM placement_availability a WHERE a.placement_id = p.id
                        AND a.ends_at >= GREATEST(a.starts_at, CAST(? AS timestamptz)) + p.duration_seconds * INTERVAL '1 second')))
                RETURNING p.*
                """, active, time(now), owner, id, expectedVersion, active, time(now)).map(this::map);
    }

    static OfferRectangle mapZone(Record row) {
        Integer x = row.get("zone_x", Integer.class);
        return x == null ? null : new OfferRectangle(x, row.get("zone_y", Integer.class),
                row.get("zone_width", Integer.class), row.get("zone_height", Integer.class));
    }

    private AdPlacement map(Record row) {
        return new AdPlacement(row.get("id", UUID.class), row.get("channel_id", UUID.class), row.get("title", String.class),
                row.get("position", String.class), row.get("width_px", Integer.class), row.get("height_px", Integer.class),
                row.get("duration_seconds", Integer.class), row.get("minimum_price", java.math.BigDecimal.class),
                row.get("currency", String.class), row.get("negotiable", Boolean.class), row.get("capacity", Integer.class),
                mapZone(row), row.get("active", Boolean.class), row.get("version", Long.class),
                row.get("created_at", OffsetDateTime.class).toInstant(), row.get("updated_at", OffsetDateTime.class).toInstant());
    }
}
