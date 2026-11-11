ALTER TABLE ad_placements ADD CONSTRAINT ad_placement_channel_pair UNIQUE (id, channel_id);
ALTER TABLE ad_offers ADD CONSTRAINT ad_offer_placement_pair UNIQUE (id, placement_id);
CREATE TABLE ad_bookings (
    id UUID PRIMARY KEY,
    offer_id UUID NOT NULL UNIQUE,
    revision BIGINT NOT NULL,
    placement_id UUID NOT NULL,
    channel_id UUID NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL CHECK (ends_at > starts_at),
    rectangle_x INTEGER NOT NULL CHECK (rectangle_x BETWEEN 0 AND 9999),
    rectangle_y INTEGER NOT NULL CHECK (rectangle_y BETWEEN 0 AND 9999),
    rectangle_width INTEGER NOT NULL CHECK (rectangle_width BETWEEN 1 AND 10000),
    rectangle_height INTEGER NOT NULL CHECK (rectangle_height BETWEEN 1 AND 10000),
    status VARCHAR(12) NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED', 'CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL CHECK (updated_at >= created_at),
    FOREIGN KEY (offer_id, revision) REFERENCES ad_offer_revisions(offer_id, revision),
    FOREIGN KEY (offer_id, placement_id) REFERENCES ad_offers(id, placement_id),
    FOREIGN KEY (placement_id, channel_id) REFERENCES ad_placements(id, channel_id),
    CHECK (rectangle_x + rectangle_width <= 10000 AND rectangle_y + rectangle_height <= 10000),
    EXCLUDE USING gist (
        channel_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&,
        int4range(rectangle_x, rectangle_x + rectangle_width, '[)') WITH &&,
        int4range(rectangle_y, rectangle_y + rectangle_height, '[)') WITH &&
    ) WHERE (status = 'BOOKED')
);
CREATE INDEX ad_bookings_placement_time ON ad_bookings(placement_id, starts_at, id);
