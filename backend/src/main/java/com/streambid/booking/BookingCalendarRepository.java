package com.streambid.booking;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class BookingCalendarRepository {
    private final DSLContext database;
    public BookingCalendarRepository(DSLContext database) { this.database = database; }

    public boolean visible(UUID channel, UUID actor) {
        return database.fetchExists(database.selectOne().from("connected_channels c").where("""
                c.id = ? AND (c.user_id = ? OR (
                    EXISTS (SELECT 1 FROM channel_listings l WHERE l.channel_id = c.id AND l.listed)
                    AND EXISTS (SELECT 1 FROM channel_credentials t WHERE t.channel_id = c.id)))
                """, channel, actor));
    }

    record Entry(UUID cursor, BookingWindow window) { }
    List<Entry> list(UUID channel, Instant from, Instant to, UUID after, int limit) {
        if (limit < 1 || limit > 101) throw new IllegalArgumentException("Invalid calendar limit");
        return database.fetch("""
                SELECT id, starts_at, ends_at, rectangle_x, rectangle_y, rectangle_width, rectangle_height
                FROM ad_bookings WHERE channel_id = ? AND status = 'BOOKED'
                  AND starts_at < CAST(? AS timestamptz) AND ends_at > CAST(? AS timestamptz)
                  AND (CAST(? AS uuid) IS NULL OR id > ?) ORDER BY id LIMIT ?
                """, channel, OffsetDateTime.ofInstant(to, ZoneOffset.UTC), OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                after, after, limit).map(row -> new Entry(row.get("id", UUID.class),
                new BookingWindow(row.get("starts_at", OffsetDateTime.class).toInstant(), row.get("ends_at", OffsetDateTime.class).toInstant(),
                        new com.streambid.offer.OfferRectangle(row.get("rectangle_x", Integer.class), row.get("rectangle_y", Integer.class),
                                row.get("rectangle_width", Integer.class), row.get("rectangle_height", Integer.class)))));
    }
}
