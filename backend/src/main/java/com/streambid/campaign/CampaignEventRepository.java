package com.streambid.campaign;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class CampaignEventRepository {
    private final DSLContext database;

    public CampaignEventRepository(DSLContext database) {
        this.database = database;
    }

    public void append(CampaignEvent event) {
        database.execute("""
                INSERT INTO campaign_events (campaign_id, campaign_version, actor_id, event_type, occurred_at)
                VALUES (?, ?, ?, ?, CAST(? AS timestamptz))
                """, event.campaignId(), event.campaignVersion(), event.actorId(), event.type().name(),
                OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC));
    }

    public List<CampaignEvent> findOwned(UUID campaignId, UUID owner, Long beforeVersion, int limit) {
        if (limit < 1 || limit > 100 || (beforeVersion != null && beforeVersion < 0)) {
            throw new IllegalArgumentException("History limit must be 1 to 100 and version must not be negative");
        }
        return database.fetch("""
                SELECT e.* FROM campaign_events e JOIN campaigns c ON c.id = e.campaign_id
                WHERE e.campaign_id = ? AND c.advertiser_id = ?
                  AND (CAST(? AS bigint) IS NULL OR e.campaign_version < ?)
                ORDER BY e.campaign_version DESC LIMIT ?
                """, campaignId, owner, beforeVersion, beforeVersion, limit).map(row -> new CampaignEvent(
                        row.get("campaign_id", UUID.class), row.get("campaign_version", Long.class),
                        row.get("actor_id", UUID.class), CampaignEventType.valueOf(row.get("event_type", String.class)),
                        row.get("occurred_at", OffsetDateTime.class).toInstant()));
    }
}
