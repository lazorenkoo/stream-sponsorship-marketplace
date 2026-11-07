package com.streambid.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class PlacementCatalogRepository {
    private final DSLContext database;

    public PlacementCatalogRepository(DSLContext database) {
        this.database = database;
    }

    public List<AvailablePlacement> list(UUID channelId, UUID after, int limit) {
        return list(channelId, after, limit, null);
    }

    public List<AvailablePlacement> list(UUID channelId, UUID after, int limit, String category) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Placement catalog limit must be between 1 and 100");
        }
        return query(null, channelId, after, limit, category);
    }

    public Optional<AvailablePlacement> find(UUID id) {
        return query(id, null, null, 1, null).stream().findFirst();
    }

    private List<AvailablePlacement> query(UUID id, UUID channelId, UUID after, int limit, String category) {
        return database.fetch("""
                SELECT p.id, p.channel_id, p.title, p.position, p.width_px, p.height_px, p.duration_seconds,
                    p.minimum_price, p.currency, p.negotiable, p.capacity, p.unrestricted_categories,
                    ARRAY(SELECT category FROM placement_advertising_categories WHERE placement_id = p.id ORDER BY category) AS categories
                FROM ad_placements p JOIN channel_listings l ON l.channel_id = p.channel_id AND l.listed
                JOIN channel_credentials t ON t.channel_id = p.channel_id
                WHERE p.active AND (CAST(? AS uuid) IS NULL OR p.id = ?)
                  AND (CAST(? AS uuid) IS NULL OR p.channel_id = ?)
                  AND (CAST(? AS uuid) IS NULL OR p.id > ?)
                  AND (CAST(? AS varchar) IS NULL OR p.unrestricted_categories OR EXISTS
                    (SELECT 1 FROM placement_advertising_categories WHERE placement_id = p.id AND category = ?))
                  AND EXISTS (SELECT 1 FROM placement_availability a WHERE a.placement_id = p.id
                    AND a.ends_at >= GREATEST(a.starts_at, CURRENT_TIMESTAMP) + p.duration_seconds * INTERVAL '1 second')
                ORDER BY p.id LIMIT ?
                """, id, id, channelId, channelId, after, after, category, category, limit)
                .map(row -> new AvailablePlacement(row.get("id", UUID.class), row.get("channel_id", UUID.class),
                        row.get("title", String.class), row.get("position", String.class), row.get("width_px", Integer.class),
                        row.get("height_px", Integer.class), row.get("duration_seconds", Integer.class),
                        row.get("minimum_price", java.math.BigDecimal.class), row.get("currency", String.class),
                        row.get("negotiable", Boolean.class), row.get("capacity", Integer.class),
                        row.get("unrestricted_categories", Boolean.class), List.of(row.get("categories", String[].class))));
    }
}
