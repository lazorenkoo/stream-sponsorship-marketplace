package com.streambid.inventory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class PlacementAvailabilityRepository {
    private final DSLContext database;

    public PlacementAvailabilityRepository(DSLContext database) {
        this.database = database;
    }

    public Optional<PlacementAvailability> insertOwned(PlacementAvailability window, UUID owner) {
        return database.fetchOptional("""
                INSERT INTO placement_availability(id, placement_id, starts_at, ends_at)
                SELECT ?, p.id, CAST(? AS timestamptz), CAST(? AS timestamptz)
                FROM ad_placements p JOIN connected_channels c ON c.id = p.channel_id
                WHERE p.id = ? AND c.user_id = ? RETURNING *
                """, window.id(), OffsetDateTime.ofInstant(window.startsAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(window.endsAt(), ZoneOffset.UTC), window.placementId(), owner)
                .map(row -> new PlacementAvailability(row.get("id", UUID.class), row.get("placement_id", UUID.class),
                        row.get("starts_at", OffsetDateTime.class).toInstant(), row.get("ends_at", OffsetDateTime.class).toInstant()));
    }

    public List<PlacementAvailability> listOwned(UUID placementId, UUID owner, java.time.Instant after, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Availability limit must be between 1 and 100");
        }
        var time = after == null ? null : OffsetDateTime.ofInstant(after, ZoneOffset.UTC);
        return database.fetch("""
                SELECT a.* FROM placement_availability a JOIN ad_placements p ON p.id = a.placement_id
                JOIN connected_channels c ON c.id = p.channel_id
                WHERE p.id = ? AND c.user_id = ? AND (CAST(? AS timestamptz) IS NULL OR a.starts_at > CAST(? AS timestamptz))
                ORDER BY a.starts_at LIMIT ?
                """, placementId, owner, time, time, limit)
                .map(row -> new PlacementAvailability(row.get("id", UUID.class), row.get("placement_id", UUID.class),
                        row.get("starts_at", OffsetDateTime.class).toInstant(), row.get("ends_at", OffsetDateTime.class).toInstant()));
    }
    public boolean deleteOwned(UUID id, UUID placementId, UUID owner) {
        return database.execute("""
                DELETE FROM placement_availability a USING ad_placements p, connected_channels c
                WHERE a.placement_id = p.id AND p.channel_id = c.id
                  AND a.id = ? AND p.id = ? AND c.user_id = ?
                """, id, placementId, owner) == 1;
    }
}
