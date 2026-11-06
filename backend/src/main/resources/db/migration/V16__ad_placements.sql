CREATE TABLE ad_placements (
    id UUID PRIMARY KEY,
    channel_id UUID NOT NULL REFERENCES connected_channels(id),
    title VARCHAR(120) NOT NULL CHECK (length(btrim(title)) > 0),
    position VARCHAR(120) NOT NULL CHECK (length(btrim(position)) > 0),
    width_px INTEGER NOT NULL CHECK (width_px BETWEEN 1 AND 7680),
    height_px INTEGER NOT NULL CHECK (height_px BETWEEN 1 AND 4320),
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds BETWEEN 1 AND 604800),
    minimum_price NUMERIC(12, 2) CHECK (minimum_price >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    negotiable BOOLEAN NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 100),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL CHECK (updated_at >= created_at),
    CHECK (minimum_price IS NOT NULL OR negotiable)
);
CREATE INDEX ad_placements_channel_index ON ad_placements(channel_id, id);
