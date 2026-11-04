CREATE TABLE channel_measurements (
    id UUID PRIMARY KEY,
    channel_id UUID NOT NULL REFERENCES connected_channels(id),
    measured_at TIMESTAMPTZ NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('LIVE', 'OFFLINE', 'UNKNOWN')),
    viewers BIGINT CHECK (viewers >= 0),
    CHECK (state = 'LIVE' OR viewers IS NULL),
    UNIQUE (channel_id, measured_at)
);

CREATE INDEX channel_measurements_history_idx
    ON channel_measurements (channel_id, measured_at DESC);
