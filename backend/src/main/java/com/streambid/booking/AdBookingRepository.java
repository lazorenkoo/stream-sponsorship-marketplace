package com.streambid.booking;

import com.streambid.offer.AdOfferRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdBookingRepository {
    private final DSLContext database;
    private final AdOfferRepository offers;

    public AdBookingRepository(DSLContext database, AdOfferRepository offers) {
        this.database = database;
        this.offers = offers;
    }

    @Transactional
    public Optional<UUID> accept(UUID offer, UUID actor, long expectedVersion, Instant now) {
        database.fetch("""
                SELECT c.id FROM connected_channels c JOIN ad_placements p ON p.channel_id = c.id
                JOIN ad_offers o ON o.placement_id = p.id
                WHERE o.id = ? AND (o.advertiser_id = ? OR o.streamer_id = ?) FOR UPDATE OF c
                """, offer, actor, actor);
        database.fetch("""
                SELECT p.id FROM ad_placements p JOIN ad_offers o ON o.placement_id = p.id
                WHERE o.id = ? FOR UPDATE OF p
                """, offer);
        database.fetch("""
                SELECT a.id FROM ad_creatives a JOIN ad_offer_revisions r ON r.creative_id = a.id
                JOIN ad_offers o ON o.id = r.offer_id AND o.current_revision = r.revision
                WHERE o.id = ? FOR SHARE OF a
                """, offer);
        database.fetch("""
                SELECT t.channel_id FROM channel_credentials t JOIN ad_placements p ON p.channel_id = t.channel_id
                JOIN ad_offers o ON o.placement_id = p.id WHERE o.id = ? FOR SHARE OF t
                """, offer);
        var accepted = database.fetchOptional("""
                UPDATE ad_offers o SET status = 'ACCEPTED', accepted_revision = o.current_revision,
                    version = o.version + 1, updated_at = GREATEST(o.updated_at, CAST(? AS timestamptz))
                FROM ad_placements p, ad_offer_revisions r, ad_creatives a
                WHERE o.id = ? AND o.version = ? AND o.status IN ('PENDING', 'COUNTERED')
                  AND (o.advertiser_id = ? OR o.streamer_id = ?) AND r.proposed_by <> ?
                  AND r.offer_id = o.id AND r.revision = o.current_revision AND p.id = o.placement_id
                  AND a.id = r.creative_id AND a.advertiser_id = o.advertiser_id AND a.status = 'APPROVED'
                  AND EXISTS (SELECT 1 FROM user_roles WHERE user_id = ?
                      AND role = CASE WHEN o.advertiser_id = ? THEN 'ADVERTISER' ELSE 'STREAMER' END)
                  AND p.active AND p.currency = r.currency
                  AND EXISTS (SELECT 1 FROM channel_credentials WHERE channel_id = p.channel_id)
                  AND EXISTS (SELECT 1 FROM channel_listings WHERE channel_id = p.channel_id AND listed)
                  AND (p.negotiable OR (p.minimum_price = r.price AND p.duration_seconds = r.duration_seconds))
                  AND (p.unrestricted_categories OR EXISTS (SELECT 1 FROM placement_advertising_categories
                      WHERE placement_id = p.id AND category = r.advertising_category))
                  AND r.mode = 'SCHEDULED' AND r.starts_at >= CAST(? AS timestamptz)
                  AND EXISTS (SELECT 1 FROM placement_availability w WHERE w.placement_id = p.id
                      AND w.starts_at <= r.starts_at AND w.ends_at >= r.starts_at + r.duration_seconds * INTERVAL '1 second')
                  AND NOT EXISTS (
                    SELECT 1 FROM (
                      SELECT r.starts_at AS at
                      UNION SELECT b.starts_at FROM ad_bookings b WHERE b.placement_id = p.id AND b.status = 'BOOKED'
                        AND b.starts_at >= r.starts_at AND b.starts_at < r.starts_at + r.duration_seconds * INTERVAL '1 second'
                    ) times WHERE (SELECT count(*) FROM ad_bookings b WHERE b.placement_id = p.id AND b.status = 'BOOKED'
                        AND b.starts_at <= times.at AND b.ends_at > times.at) >= p.capacity)
                RETURNING o.current_revision
                """, time(now), offer, expectedVersion, actor, actor, actor, actor, actor, time(now));
        if (accepted.isEmpty()) return Optional.empty();
        UUID id = UUID.randomUUID();
        database.execute("""
                INSERT INTO ad_bookings(id, offer_id, revision, placement_id, channel_id, starts_at, ends_at,
                    rectangle_x, rectangle_y, rectangle_width, rectangle_height, created_at, updated_at)
                SELECT ?, o.id, r.revision, p.id, p.channel_id, r.starts_at,
                    r.starts_at + r.duration_seconds * INTERVAL '1 second', r.rectangle_x, r.rectangle_y,
                    r.rectangle_width, r.rectangle_height, CAST(? AS timestamptz), CAST(? AS timestamptz)
                FROM ad_offers o JOIN ad_offer_revisions r ON r.offer_id = o.id AND r.revision = o.accepted_revision
                JOIN ad_placements p ON p.id = o.placement_id WHERE o.id = ?
                """, id, time(now), time(now), offer);
        offers.recordEvent(offer, actor);
        return Optional.of(id);
    }

    @Transactional
    public boolean cancel(UUID id, UUID actor, long expectedVersion, String reason, Instant now) {
        database.fetch("""
                SELECT c.id FROM connected_channels c JOIN ad_bookings b ON b.channel_id = c.id
                JOIN ad_offers o ON o.id = b.offer_id
                WHERE b.id = ? AND (o.advertiser_id = ? OR o.streamer_id = ?) FOR UPDATE OF c
                """, id, actor, actor);
        return database.fetchOptional("""
                WITH cancelled AS (
                    UPDATE ad_bookings b SET status = 'CANCELLED', version = b.version + 1,
                        updated_at = GREATEST(b.updated_at, CAST(? AS timestamptz))
                    FROM ad_offers o WHERE b.offer_id = o.id AND b.id = ? AND b.version = ?
                      AND b.status = 'BOOKED' AND b.starts_at > GREATEST(CAST(? AS timestamptz), clock_timestamp())
                      AND (o.advertiser_id = ? OR o.streamer_id = ?) RETURNING b.id, b.version, b.updated_at
                ), logged AS (
                    INSERT INTO ad_booking_cancellations(booking_id, version, actor_id, reason, cancelled_at)
                    SELECT id, version, ?, ?, updated_at FROM cancelled
                ) SELECT id FROM cancelled
                """, time(now), id, expectedVersion, time(now), actor, actor, actor, reason).isPresent();
    }

    public Optional<BookingView> findForParty(UUID id, UUID actor) {
        return database.fetchOptional("""
                SELECT b.* FROM ad_bookings b JOIN ad_offers o ON o.id = b.offer_id
                WHERE b.id = ? AND (o.advertiser_id = ? OR o.streamer_id = ?)
                """, id, actor, actor).map(this::map);
    }

    public java.util.List<BookingView> listForParty(UUID actor, UUID after, BookingStatus status, int limit) {
        if (limit < 1 || limit > 101) throw new IllegalArgumentException("Invalid booking page limit");
        return database.fetch("""
                SELECT b.* FROM ad_bookings b JOIN ad_offers o ON o.id = b.offer_id
                WHERE (o.advertiser_id = ? OR o.streamer_id = ?)
                  AND (CAST(? AS uuid) IS NULL OR b.id > ?)
                  AND (CAST(? AS varchar) IS NULL OR b.status = ?) ORDER BY b.id LIMIT ?
                """, actor, actor, after, after, status == null ? null : status.name(),
                status == null ? null : status.name(), limit).map(this::map);
    }

    private BookingView map(org.jooq.Record row) {
        return new BookingView(row.get("id", UUID.class), row.get("offer_id", UUID.class),
                row.get("revision", Long.class), row.get("placement_id", UUID.class), row.get("channel_id", UUID.class),
                new BookingWindow(row.get("starts_at", OffsetDateTime.class).toInstant(), row.get("ends_at", OffsetDateTime.class).toInstant(),
                        new com.streambid.offer.OfferRectangle(row.get("rectangle_x", Integer.class), row.get("rectangle_y", Integer.class),
                                row.get("rectangle_width", Integer.class), row.get("rectangle_height", Integer.class))),
                row.get("status", String.class), row.get("version", Long.class));
    }

    private OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
