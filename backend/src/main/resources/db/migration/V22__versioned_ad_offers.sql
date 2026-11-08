CREATE TABLE ad_offers (
    id UUID PRIMARY KEY,
    placement_id UUID NOT NULL REFERENCES ad_placements(id),
    advertiser_id UUID NOT NULL REFERENCES users(id),
    streamer_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PENDING', 'COUNTERED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'WITHDRAWN')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    current_revision BIGINT NOT NULL DEFAULT 0 CHECK (current_revision >= 0),
    accepted_revision BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL CHECK (updated_at >= created_at),
    CHECK (advertiser_id <> streamer_id),
    CHECK ((status = 'ACCEPTED' AND accepted_revision IS NOT NULL AND accepted_revision = current_revision) OR
           (status <> 'ACCEPTED' AND accepted_revision IS NULL))
);
CREATE TABLE ad_offer_revisions (
    offer_id UUID NOT NULL REFERENCES ad_offers(id),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    proposed_by UUID NOT NULL REFERENCES users(id),
    creative_id UUID NOT NULL REFERENCES ad_creatives(id),
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds BETWEEN 1 AND 604800),
    mode VARCHAR(12) NOT NULL CHECK (mode IN ('SCHEDULED', 'FLEXIBLE')),
    starts_at TIMESTAMPTZ,
    advertising_category VARCHAR(40) NOT NULL CHECK (advertising_category ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (offer_id, revision),
    CHECK ((mode = 'SCHEDULED' AND starts_at IS NOT NULL) OR (mode = 'FLEXIBLE' AND starts_at IS NULL))
);
ALTER TABLE ad_offers ADD CONSTRAINT ad_offers_current_revision_fk
    FOREIGN KEY (id, current_revision) REFERENCES ad_offer_revisions(offer_id, revision)
    DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE ad_offers ADD CONSTRAINT ad_offers_accepted_revision_fk
    FOREIGN KEY (id, accepted_revision) REFERENCES ad_offer_revisions(offer_id, revision)
    DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX ad_offers_advertiser_index ON ad_offers(advertiser_id, id);
CREATE INDEX ad_offers_streamer_index ON ad_offers(streamer_id, id);
