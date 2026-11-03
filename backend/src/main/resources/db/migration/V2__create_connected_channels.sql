CREATE TABLE connected_channels (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL,
    external_channel_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (platform, external_channel_id),
    CONSTRAINT connected_channels_platform CHECK (platform IN ('TWITCH', 'YOUTUBE', 'KICK')),
    CONSTRAINT connected_channels_external_id CHECK (length(trim(external_channel_id)) > 0),
    CONSTRAINT connected_channels_name CHECK (length(trim(display_name)) > 0)
);

CREATE INDEX connected_channels_user_id ON connected_channels(user_id);
