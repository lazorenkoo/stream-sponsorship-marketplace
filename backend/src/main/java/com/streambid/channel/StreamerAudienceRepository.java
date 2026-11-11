package com.streambid.channel;

import com.streambid.platform.StreamState;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class StreamerAudienceRepository {
    private final DSLContext database;

    public StreamerAudienceRepository(DSLContext database) {
        this.database = database;
    }

    public Map<UUID, StreamerCatalogPage.Audience> find(List<UUID> channelIds) {
        if (channelIds.isEmpty()) return Map.of();
        return database.fetch("""
                SELECT DISTINCT ON (m.channel_id) m.channel_id, m.measured_at, m.state, m.viewers,
                       AVG(m.viewers) FILTER (WHERE m.state = 'LIVE') OVER audience_window AS average,
                       MAX(m.viewers) FILTER (WHERE m.state = 'LIVE') OVER audience_window AS peak,
                       COUNT(m.viewers) FILTER (WHERE m.state = 'LIVE') OVER audience_window AS samples
                FROM channel_measurements m
                JOIN channel_listings l ON l.channel_id = m.channel_id AND l.listed
                JOIN channel_credentials c ON c.channel_id = m.channel_id
                WHERE m.channel_id = ANY(CAST(? AS uuid[]))
                  AND m.measured_at >= CURRENT_TIMESTAMP - INTERVAL '720 hours'
                  AND m.measured_at <= CURRENT_TIMESTAMP
                WINDOW audience_window AS (PARTITION BY m.channel_id)
                ORDER BY m.channel_id, m.measured_at DESC
                """, (Object) channelIds.toArray(UUID[]::new)).intoMap(
                row -> row.get("channel_id", UUID.class),
                row -> new StreamerCatalogPage.Audience(row.get("measured_at", OffsetDateTime.class).toInstant(),
                        StreamState.valueOf(row.get("state", String.class)), row.get("viewers", Long.class),
                        row.get("average", BigDecimal.class), row.get("peak", Long.class), row.get("samples", Long.class)));
    }
}
