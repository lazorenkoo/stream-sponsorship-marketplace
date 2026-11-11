ALTER TABLE ad_placements ADD COLUMN unrestricted_categories BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TABLE placement_advertising_categories (
    placement_id UUID NOT NULL REFERENCES ad_placements(id),
    category VARCHAR(40) NOT NULL CHECK (category ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    PRIMARY KEY (placement_id, category)
);
