CREATE TABLE campaign_events (
    campaign_id UUID NOT NULL REFERENCES campaigns(id),
    campaign_version BIGINT NOT NULL CHECK (campaign_version >= 0),
    actor_id UUID NOT NULL REFERENCES users(id),
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('CREATED', 'EDITED', 'ARCHIVED')),
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (campaign_id, campaign_version)
);
