CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE placement_availability (
    id UUID PRIMARY KEY,
    placement_id UUID NOT NULL REFERENCES ad_placements(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL CHECK (ends_at > starts_at),
    EXCLUDE USING gist (placement_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
);
