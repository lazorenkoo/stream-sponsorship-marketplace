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
public class CampaignCatalogRepository {
    private final DSLContext database;

    public CampaignCatalogRepository(DSLContext database) {
        this.database = database;
    }

    public List<PublishedCampaign> list(Instant before, UUID beforeId, int limit) {
        return list(before, beforeId, limit, null);
    }

    public List<PublishedCampaign> list(Instant before, UUID beforeId, int limit,
            com.streambid.platform.StreamingPlatform platform) {
        if (limit < 1 || limit > 101 || (before == null) != (beforeId == null)) {
            throw new IllegalArgumentException("Query limit must be 1 to 101 and cursor requires time and ID");
        }
        var time = before == null ? null : OffsetDateTime.ofInstant(before, ZoneOffset.UTC);
        return database.fetch("""
                SELECT id, title, description, published_at,
                    ARRAY(SELECT platform FROM campaign_target_platforms WHERE campaign_id = campaigns.id ORDER BY platform) AS platforms
                FROM campaigns WHERE status = 'PUBLISHED'
                  AND (CAST(? AS varchar) IS NULL OR EXISTS
                      (SELECT 1 FROM campaign_target_platforms WHERE campaign_id = campaigns.id AND platform = ?))
                  AND (CAST(? AS timestamptz) IS NULL OR (published_at, id) < (CAST(? AS timestamptz), CAST(? AS uuid)))
                ORDER BY published_at DESC, id DESC LIMIT ?
                """, platform == null ? null : platform.name(), platform == null ? null : platform.name(),
                time, time, beforeId, limit).map(this::map);
    }

    public Optional<PublishedCampaign> find(UUID id) {
        return database.fetchOptional("""
                SELECT id, title, description, published_at,
                    ARRAY(SELECT platform FROM campaign_target_platforms WHERE campaign_id = campaigns.id ORDER BY platform) AS platforms
                FROM campaigns WHERE id = ? AND status = 'PUBLISHED'
                """, id).map(this::map);
    }

    private PublishedCampaign map(Record row) {
        return new PublishedCampaign(row.get("id", UUID.class), row.get("title", String.class),
                row.get("description", String.class), row.get("published_at", OffsetDateTime.class).toInstant(),
                java.util.Arrays.stream(row.get("platforms", String[].class))
                        .map(com.streambid.platform.StreamingPlatform::valueOf).toList());
    }
}
