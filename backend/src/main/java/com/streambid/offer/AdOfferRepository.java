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
        var rectangle = terms.rectangle();
        database.execute("""
                INSERT INTO ad_offer_revisions(offer_id, revision, proposed_by, creative_id, price, currency,
                    duration_seconds, mode, starts_at, advertising_category, created_at,
                    rectangle_x, rectangle_y, rectangle_width, rectangle_height)
                VALUES (?, 0, ?, ?, ?, ?, ?, ?, CAST(? AS timestamptz), ?, CAST(? AS timestamptz), ?, ?, ?, ?)
                """, id, advertiser, terms.creativeId(), terms.price(), terms.currency(), terms.durationSeconds(),
                terms.mode().name(), time(terms.startsAt()), terms.advertisingCategory(), time(now),
                rectangle.x(), rectangle.y(), rectangle.width(), rectangle.height());
        return Optional.of(id);
    }

    public boolean existsForParty(UUID id, UUID actor) {
        return database.fetchExists(database.selectOne().from("ad_offers")
                .where("id = ? AND (advertiser_id = ? OR streamer_id = ?)", id, actor, actor));
    }

    private OffsetDateTime time(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
