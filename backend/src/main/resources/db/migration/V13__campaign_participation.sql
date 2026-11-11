ALTER TABLE campaigns DROP CONSTRAINT campaigns_status_check;
ALTER TABLE campaigns ADD CONSTRAINT campaigns_status_check
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'));
ALTER TABLE campaigns ADD COLUMN published_at TIMESTAMPTZ;
ALTER TABLE campaigns ADD CONSTRAINT campaigns_publication_check
    CHECK ((status <> 'PUBLISHED' OR published_at IS NOT NULL)
        AND (published_at IS NULL OR published_at >= created_at));
ALTER TABLE campaign_events DROP CONSTRAINT campaign_events_event_type_check;
ALTER TABLE campaign_events ADD CONSTRAINT campaign_events_event_type_check
    CHECK (event_type IN ('CREATED', 'EDITED', 'ARCHIVED', 'TARGETING_CHANGED', 'PUBLISHED'));
CREATE TABLE campaign_participations (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES campaigns(id),
    channel_id UUID NOT NULL REFERENCES connected_channels(id),
    initiated_by UUID NOT NULL REFERENCES users(id),
    direction VARCHAR(20) NOT NULL CHECK (direction IN ('APPLICATION', 'INVITATION')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL CHECK (updated_at >= created_at),
    UNIQUE (campaign_id, channel_id)
);
CREATE INDEX campaign_participations_channel_index ON campaign_participations(channel_id, id);
