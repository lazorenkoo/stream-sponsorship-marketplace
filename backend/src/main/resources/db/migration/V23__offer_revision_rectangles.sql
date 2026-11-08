ALTER TABLE ad_offer_revisions
    ADD COLUMN rectangle_x INTEGER NOT NULL,
    ADD COLUMN rectangle_y INTEGER NOT NULL,
    ADD COLUMN rectangle_width INTEGER NOT NULL,
    ADD COLUMN rectangle_height INTEGER NOT NULL,
    ADD CONSTRAINT ad_offer_rectangle_bounds CHECK (
        rectangle_x BETWEEN 0 AND 9999 AND rectangle_y BETWEEN 0 AND 9999
        AND rectangle_width BETWEEN 1 AND 10000 AND rectangle_height BETWEEN 1 AND 10000
        AND rectangle_x + rectangle_width <= 10000
        AND rectangle_y + rectangle_height <= 10000
    );
