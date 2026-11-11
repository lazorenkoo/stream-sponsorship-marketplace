CREATE TABLE ad_offer_events (
    offer_id UUID NOT NULL REFERENCES ad_offers(id),
    version BIGINT NOT NULL CHECK (version >= 0),
    revision BIGINT NOT NULL,
    actor_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PENDING', 'COUNTERED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'WITHDRAWN')),
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (offer_id, version),
    FOREIGN KEY (offer_id, revision) REFERENCES ad_offer_revisions(offer_id, revision)
);
