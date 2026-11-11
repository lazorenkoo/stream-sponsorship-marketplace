CREATE TABLE campaigns (
    id UUID PRIMARY KEY,
    advertiser_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(120) NOT NULL CHECK (length(trim(title)) > 0),
    description VARCHAR(5000) NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL CHECK (updated_at >= created_at)
);

CREATE INDEX campaigns_advertiser_created_idx ON campaigns (advertiser_id, created_at DESC, id DESC);
