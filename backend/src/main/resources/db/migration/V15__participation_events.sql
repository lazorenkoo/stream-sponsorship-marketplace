CREATE TABLE participation_events (
    participation_id UUID NOT NULL REFERENCES campaign_participations(id),
    version BIGINT NOT NULL CHECK (version >= 0),
    actor_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (participation_id, version)
);
