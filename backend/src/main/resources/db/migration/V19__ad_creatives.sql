CREATE TABLE ad_creatives (
    id UUID PRIMARY KEY,
    advertiser_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(120) NOT NULL CHECK (length(btrim(title)) > 0),
    object_key VARCHAR(100) NOT NULL UNIQUE,
    format VARCHAR(10) NOT NULL CHECK (format IN ('PNG', 'JPEG', 'WEBP')),
    width_px INTEGER NOT NULL CHECK (width_px BETWEEN 1 AND 8192),
    height_px INTEGER NOT NULL CHECK (height_px BETWEEN 1 AND 8192),
    byte_size BIGINT NOT NULL CHECK (byte_size BETWEEN 1 AND 10485760),
    sha256 VARCHAR(64) NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    rejection_reason VARCHAR(500),
    CHECK (width_px::bigint * height_px <= 40000000),
    CHECK ((status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND rejection_reason IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND reviewed_at >= created_at)),
    CHECK (status <> 'REJECTED' OR (rejection_reason IS NOT NULL AND length(btrim(rejection_reason)) > 0))
);
CREATE INDEX ad_creatives_owner_index ON ad_creatives(advertiser_id, id);
