CREATE TABLE creative_review_events (
    creative_id UUID NOT NULL REFERENCES ad_creatives(id),
    version BIGINT NOT NULL CHECK (version > 0),
    reviewer_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(12) NOT NULL CHECK (status IN ('APPROVED', 'REJECTED')),
    reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (creative_id, version),
    CHECK (status <> 'REJECTED' OR (reason IS NOT NULL AND length(btrim(reason)) > 0))
);
