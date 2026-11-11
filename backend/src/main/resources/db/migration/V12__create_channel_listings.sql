CREATE TABLE channel_listings (
    channel_id UUID PRIMARY KEY REFERENCES connected_channels(id),
    listed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
