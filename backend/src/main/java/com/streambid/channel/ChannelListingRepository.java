package com.streambid.channel;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class ChannelListingRepository {
    private final DSLContext database;

    public ChannelListingRepository(DSLContext database) {
        this.database = database;
    }

    public Optional<ChannelListing> readOwned(UUID channelId, UUID owner) {
        return database.fetchOptional("""
                SELECT c.id AS channel_id, COALESCE(l.listed, FALSE) AS listed, l.updated_at
                FROM connected_channels c LEFT JOIN channel_listings l ON l.channel_id = c.id
                WHERE c.id = ? AND c.user_id = ?
                """, channelId, owner).map(this::map);
    }

    public Optional<ChannelListing> setOwned(UUID channelId, UUID owner, boolean listed) {
        return database.fetchOptional("""
                INSERT INTO channel_listings (channel_id, listed, updated_at)
                SELECT c.id, ?, CURRENT_TIMESTAMP FROM connected_channels c
                WHERE c.id = ? AND c.user_id = ?
                  AND (? = FALSE OR EXISTS (SELECT 1 FROM channel_credentials t WHERE t.channel_id = c.id))
                ON CONFLICT (channel_id) DO UPDATE SET listed = EXCLUDED.listed, updated_at = EXCLUDED.updated_at
                RETURNING channel_id, listed, updated_at
                """, listed, channelId, owner, listed).map(this::map);
    }

    private ChannelListing map(Record row) {
        var updatedAt = row.get("updated_at", OffsetDateTime.class);
        return new ChannelListing(row.get("channel_id", UUID.class), row.get("listed", Boolean.class),
                updatedAt == null ? null : updatedAt.toInstant());
    }
}
