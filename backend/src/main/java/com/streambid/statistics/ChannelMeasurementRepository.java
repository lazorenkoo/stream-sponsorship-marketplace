package com.streambid.statistics;

import com.streambid.platform.StreamState;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ChannelMeasurementRepository {
    private final DSLContext database;

    public ChannelMeasurementRepository(DSLContext database) {
        this.database = database;
    }

    public boolean saveIfConnected(ChannelMeasurement measurement) {
        return database.execute("""
                INSERT INTO channel_measurements (id, channel_id, measured_at, state, viewers)
                SELECT ?, channel_id, CAST(? AS timestamptz), ?, CAST(? AS bigint) FROM channel_credentials WHERE channel_id = ?
                ON CONFLICT (channel_id, measured_at) DO NOTHING
                """, measurement.id(), OffsetDateTime.ofInstant(measurement.measuredAt(), ZoneOffset.UTC),
                measurement.state().name(), measurement.viewers(), measurement.channelId()) == 1;
    }

    public List<ChannelMeasurement> history(UUID channelId, UUID owner, Instant before, int limit) {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("History limit must be between 1 and 200");
        }
        OffsetDateTime cursor = before == null ? null : OffsetDateTime.ofInstant(before, ZoneOffset.UTC);
        return database.fetch("""
                SELECT m.id, m.channel_id, m.measured_at, m.state, m.viewers
                FROM channel_measurements m JOIN connected_channels c ON c.id = m.channel_id
                WHERE c.id = ? AND c.user_id = ?
                  AND (CAST(? AS timestamptz) IS NULL OR m.measured_at < CAST(? AS timestamptz))
                ORDER BY m.measured_at DESC LIMIT ?
                """, channelId, owner, cursor, cursor, limit).map(row -> new ChannelMeasurement(
                        row.get("id", UUID.class), row.get("channel_id", UUID.class),
                        row.get("measured_at", OffsetDateTime.class).toInstant(),
                        StreamState.valueOf(row.get("state", String.class)), row.get("viewers", Long.class)));
    }
}
