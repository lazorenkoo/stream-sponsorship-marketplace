package com.streambid.offer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdOfferRepository {
    private final DSLContext database;

    public AdOfferRepository(DSLContext database) {
        this.database = database;
    }

    @Transactional
    public Optional<UUID> createDraft(UUID id, UUID advertiser, UUID placement, OfferTerms terms, Instant now) {
        var inserted = database.fetchOptional("""
                INSERT INTO ad_offers(id, placement_id, advertiser_id, streamer_id, created_at, updated_at)
                SELECT ?, p.id, ?, c.user_id, CAST(? AS timestamptz), CAST(? AS timestamptz)
                FROM ad_placements p JOIN connected_channels c ON c.id = p.channel_id
                JOIN channel_listings l ON l.channel_id = c.id AND l.listed
                JOIN channel_credentials t ON t.channel_id = c.id
                JOIN ad_creatives a ON a.id = ? AND a.advertiser_id = ? AND a.status = 'APPROVED'
                WHERE p.id = ? AND p.active AND c.user_id <> ? AND p.currency = ?
                  AND EXISTS (SELECT 1 FROM user_roles WHERE user_id = ? AND role = 'ADVERTISER')
                  AND (p.unrestricted_categories OR EXISTS (SELECT 1 FROM placement_advertising_categories
                      WHERE placement_id = p.id AND category = ?))
                RETURNING id
                """, id, advertiser, time(now), time(now), terms.creativeId(), advertiser, placement, advertiser,
                terms.currency(), advertiser, terms.advertisingCategory());
        if (inserted.isEmpty()) return Optional.empty();
        appendRevision(id, 0, advertiser, terms, now);
        return Optional.of(id);
    }

    @Transactional
    public boolean sendDraft(UUID id, UUID actor, long expectedVersion, Instant now) {
        database.fetch("""
                SELECT c.id FROM connected_channels c JOIN ad_placements p ON p.channel_id = c.id
                JOIN ad_offers o ON o.placement_id = p.id WHERE o.id = ? AND o.advertiser_id = ? FOR UPDATE OF c
                """, id, actor);
        database.fetch("""
                SELECT p.id FROM ad_placements p JOIN ad_offers o ON o.placement_id = p.id
                WHERE o.id = ? AND o.advertiser_id = ? FOR UPDATE OF p
                """, id, actor);
        database.fetch("""
                SELECT a.id FROM ad_creatives a JOIN ad_offer_revisions r ON r.creative_id = a.id
                JOIN ad_offers o ON o.id = r.offer_id AND o.current_revision = r.revision
                WHERE o.id = ? AND o.advertiser_id = ? FOR SHARE OF a
                """, id, actor);
        database.fetch("""
                SELECT t.channel_id FROM channel_credentials t JOIN ad_placements p ON p.channel_id = t.channel_id
                JOIN ad_offers o ON o.placement_id = p.id WHERE o.id = ? AND o.advertiser_id = ? FOR SHARE OF t
                """, id, actor);
        return database.execute("""
                UPDATE ad_offers o SET status = 'PENDING', version = o.version + 1,
                    sent_at = GREATEST(o.created_at, CAST(? AS timestamptz)),
                    updated_at = GREATEST(o.updated_at, CAST(? AS timestamptz))
                FROM ad_placements p, ad_offer_revisions r, ad_creatives a
                WHERE o.id = ? AND o.advertiser_id = ? AND o.status = 'DRAFT' AND o.version = ?
                  AND p.id = o.placement_id AND p.active AND r.offer_id = o.id AND r.revision = o.current_revision
                  AND a.id = r.creative_id AND a.advertiser_id = o.advertiser_id AND a.status = 'APPROVED'
                  AND EXISTS (SELECT 1 FROM user_roles WHERE user_id = ? AND role = 'ADVERTISER')
                  AND EXISTS (SELECT 1 FROM channel_credentials WHERE channel_id = p.channel_id)
                  AND EXISTS (SELECT 1 FROM channel_listings WHERE channel_id = p.channel_id AND listed)
                  AND p.currency = r.currency
                  AND (p.negotiable OR (p.minimum_price = r.price AND p.duration_seconds = r.duration_seconds))
                  AND (p.unrestricted_categories OR EXISTS (SELECT 1 FROM placement_advertising_categories
                      WHERE placement_id = p.id AND category = r.advertising_category))
                  AND EXISTS (SELECT 1 FROM placement_availability w WHERE w.placement_id = p.id
                    AND ((r.mode = 'SCHEDULED' AND r.starts_at >= CAST(? AS timestamptz) AND w.starts_at <= r.starts_at
                          AND w.ends_at >= r.starts_at + r.duration_seconds * INTERVAL '1 second')
                      OR (r.mode = 'FLEXIBLE' AND w.ends_at >= GREATEST(w.starts_at, CAST(? AS timestamptz))
                          + r.duration_seconds * INTERVAL '1 second')))
                """, time(now), time(now), id, actor, expectedVersion, actor, time(now), time(now)) == 1;
    }

    @Transactional
    public boolean counter(UUID id, UUID actor, long expectedVersion, OfferTerms terms, Instant now) {
        database.fetch("""
                SELECT c.id FROM connected_channels c JOIN ad_placements p ON p.channel_id = c.id
                JOIN ad_offers o ON o.placement_id = p.id
                WHERE o.id = ? AND (o.advertiser_id = ? OR o.streamer_id = ?) FOR UPDATE OF c
                """, id, actor, actor);
        database.fetch("""
                SELECT p.id FROM ad_placements p JOIN ad_offers o ON o.placement_id = p.id
                WHERE o.id = ? AND (o.advertiser_id = ? OR o.streamer_id = ?) FOR UPDATE OF p
                """, id, actor, actor);
        database.fetch("SELECT id FROM ad_creatives WHERE id = ? FOR SHARE", terms.creativeId());
        database.fetch("""
                SELECT t.channel_id FROM channel_credentials t JOIN ad_placements p ON p.channel_id = t.channel_id
                JOIN ad_offers o ON o.placement_id = p.id WHERE o.id = ? FOR SHARE OF t
                """, id);
        var current = database.fetchOptional("""
                SELECT o.current_revision FROM ad_offers o
                JOIN ad_offer_revisions r ON r.offer_id = o.id AND r.revision = o.current_revision
                JOIN ad_placements p ON p.id = o.placement_id
                JOIN ad_creatives a ON a.id = ? AND a.advertiser_id = o.advertiser_id AND a.status = 'APPROVED'
                WHERE o.id = ? AND o.version = ? AND o.status IN ('PENDING', 'COUNTERED')
                  AND r.proposed_by <> ? AND (o.advertiser_id = ? OR o.streamer_id = ?)
                  AND EXISTS (SELECT 1 FROM user_roles WHERE user_id = ?
                      AND role = CASE WHEN o.advertiser_id = ? THEN 'ADVERTISER' ELSE 'STREAMER' END)
                  AND p.active AND p.currency = ?
                  AND EXISTS (SELECT 1 FROM channel_listings WHERE channel_id = p.channel_id AND listed)
                  AND EXISTS (SELECT 1 FROM channel_credentials WHERE channel_id = p.channel_id)
                  AND (p.negotiable OR (p.minimum_price = ? AND p.duration_seconds = ?))
                  AND (p.unrestricted_categories OR EXISTS (SELECT 1 FROM placement_advertising_categories
                      WHERE placement_id = p.id AND category = ?))
                  AND EXISTS (SELECT 1 FROM placement_availability w WHERE w.placement_id = p.id
                    AND ((? = 'SCHEDULED' AND CAST(? AS timestamptz) >= CAST(? AS timestamptz)
                          AND w.starts_at <= CAST(? AS timestamptz)
                          AND w.ends_at >= CAST(? AS timestamptz) + ? * INTERVAL '1 second')
                      OR (? = 'FLEXIBLE' AND w.ends_at >= GREATEST(w.starts_at, CAST(? AS timestamptz))
                          + ? * INTERVAL '1 second')))
                FOR UPDATE OF o
                """, terms.creativeId(), id, expectedVersion, actor, actor, actor, actor, actor, terms.currency(),
                terms.price(), terms.durationSeconds(), terms.advertisingCategory(), terms.mode().name(), time(terms.startsAt()),
                time(now), time(terms.startsAt()), time(terms.startsAt()), terms.durationSeconds(),
                terms.mode().name(), time(now), terms.durationSeconds());
        if (current.isEmpty()) return false;
        long revision = current.get().get("current_revision", Long.class) + 1;
        appendRevision(id, revision, actor, terms, now);
        database.execute("""
                UPDATE ad_offers SET current_revision = ?, version = version + 1,
                    status = CASE WHEN advertiser_id = ? THEN 'PENDING' ELSE 'COUNTERED' END,
                    updated_at = GREATEST(updated_at, CAST(? AS timestamptz)) WHERE id = ?
                """, revision, actor, time(now), id);
        return true;
    }

    private void appendRevision(UUID id, long revision, UUID actor, OfferTerms terms, Instant now) {
        var rectangle = terms.rectangle();
        database.execute("""
                INSERT INTO ad_offer_revisions(offer_id, revision, proposed_by, creative_id, price, currency,
                    duration_seconds, mode, starts_at, advertising_category, created_at,
                    rectangle_x, rectangle_y, rectangle_width, rectangle_height)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS timestamptz), ?, CAST(? AS timestamptz), ?, ?, ?, ?)
                """, id, revision, actor, terms.creativeId(), terms.price(), terms.currency(), terms.durationSeconds(),
                terms.mode().name(), time(terms.startsAt()), terms.advertisingCategory(), time(now),
                rectangle.x(), rectangle.y(), rectangle.width(), rectangle.height());
    }

    @Transactional
    public boolean close(UUID id, UUID actor, OfferCloseAction action, long expectedVersion, Instant now) {
        return database.execute("""
                UPDATE ad_offers o SET status = CASE WHEN ? = 'REJECT' THEN 'REJECTED' ELSE 'WITHDRAWN' END,
                    version = o.version + 1, updated_at = GREATEST(o.updated_at, CAST(? AS timestamptz))
                FROM ad_offer_revisions r WHERE o.id = ? AND o.version = ?
                  AND r.offer_id = o.id AND r.revision = o.current_revision
                  AND ((? = 'WITHDRAW' AND o.advertiser_id = ? AND o.status IN ('DRAFT', 'PENDING', 'COUNTERED')
                      AND EXISTS (SELECT 1 FROM user_roles WHERE user_id = ? AND role = 'ADVERTISER'))
                    OR (? = 'REJECT' AND o.status IN ('PENDING', 'COUNTERED') AND r.proposed_by <> ?
                      AND (o.advertiser_id = ? OR o.streamer_id = ?)
                      AND EXISTS (SELECT 1 FROM user_roles WHERE user_id = ?
                          AND role = CASE WHEN o.advertiser_id = ? THEN 'ADVERTISER' ELSE 'STREAMER' END)))
                """, action.name(), time(now), id, expectedVersion, action.name(), actor, actor,
                action.name(), actor, actor, actor, actor, actor) == 1;
    }

    public boolean existsForParty(UUID id, UUID actor) {
        return findForParty(id, actor).isPresent();
    }

    public Optional<OfferView> findForParty(UUID id, UUID actor) {
        return queryForParty(id, actor, null, 1).stream().findFirst();
    }

    public java.util.List<OfferView> listForParty(UUID actor, UUID after, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Offer limit must be between 1 and 100");
        return queryForParty(null, actor, after, limit);
    }

    private java.util.List<OfferView> queryForParty(UUID id, UUID actor, UUID after, int limit) {
        return database.fetch("""
                SELECT o.id, o.placement_id, o.status, o.version, o.current_revision,
                    o.created_at AS offer_created_at, o.updated_at, r.*
                FROM ad_offers o JOIN ad_offer_revisions r ON r.offer_id = o.id AND r.revision = o.current_revision
                WHERE (o.advertiser_id = ? OR (o.streamer_id = ? AND o.sent_at IS NOT NULL))
                  AND (CAST(? AS uuid) IS NULL OR o.id = ?)
                  AND (CAST(? AS uuid) IS NULL OR o.id > ?)
                ORDER BY o.id LIMIT ?
                """, actor, actor, id, id, after, after, limit).map(row -> {
            return new OfferView(row.get("id", UUID.class), row.get("placement_id", UUID.class),
                    OfferStatus.valueOf(row.get("status", String.class)), row.get("version", Long.class),
                    row.get("current_revision", Long.class), terms(row), row.get("offer_created_at", OffsetDateTime.class).toInstant(),
                    row.get("updated_at", OffsetDateTime.class).toInstant());
        });
    }

    public java.util.List<OfferRevisionView> revisionsForParty(UUID id, UUID actor, Long before, int limit) {
        if (limit < 1 || limit > 100 || (before != null && before < 0)) throw new IllegalArgumentException("Invalid revision page");
        return database.fetch("""
                SELECT r.*, CASE WHEN r.proposed_by = o.advertiser_id THEN 'ADVERTISER' ELSE 'STREAMER' END AS proposer_role
                FROM ad_offer_revisions r JOIN ad_offers o ON o.id = r.offer_id
                WHERE o.id = ? AND (o.advertiser_id = ? OR (o.streamer_id = ? AND o.sent_at IS NOT NULL))
                  AND (CAST(? AS bigint) IS NULL OR r.revision < ?) ORDER BY r.revision DESC LIMIT ?
                """, id, actor, actor, before, before, limit).map(row -> new OfferRevisionView(row.get("revision", Long.class),
                        row.get("proposer_role", String.class), terms(row), row.get("created_at", OffsetDateTime.class).toInstant()));
    }

    private OfferTerms terms(org.jooq.Record row) {
        var start = row.get("starts_at", OffsetDateTime.class);
        return new OfferTerms(row.get("creative_id", UUID.class), row.get("price", java.math.BigDecimal.class),
                row.get("currency", String.class), row.get("duration_seconds", Integer.class),
                OfferMode.valueOf(row.get("mode", String.class)), start == null ? null : start.toInstant(),
                row.get("advertising_category", String.class), new OfferRectangle(row.get("rectangle_x", Integer.class),
                row.get("rectangle_y", Integer.class), row.get("rectangle_width", Integer.class), row.get("rectangle_height", Integer.class)));
    }

    private OffsetDateTime time(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
