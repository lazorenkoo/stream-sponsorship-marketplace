ALTER TABLE ad_placements
    ADD COLUMN zone_x integer,
    ADD COLUMN zone_y integer,
    ADD COLUMN zone_width integer,
    ADD COLUMN zone_height integer,
    ADD CONSTRAINT valid_placement_zone CHECK (
        num_nonnulls(zone_x, zone_y, zone_width, zone_height) = 0 OR (
            num_nonnulls(zone_x, zone_y, zone_width, zone_height) = 4
            AND zone_x BETWEEN 0 AND 9999 AND zone_y BETWEEN 0 AND 9999
            AND zone_width > 0 AND zone_height > 0
            AND zone_x::bigint + zone_width <= 10000 AND zone_y::bigint + zone_height <= 10000
        )
    );

CREATE FUNCTION placement_allows_rectangle(placement ad_placements, x integer, y integer, width integer, height integer)
RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT placement.zone_x IS NULL OR (
        x >= placement.zone_x AND y >= placement.zone_y
        AND x + width <= placement.zone_x + placement.zone_width
        AND y + height <= placement.zone_y + placement.zone_height
    )
$$;
