CREATE TABLE campaign_target_platforms (
    campaign_id UUID NOT NULL REFERENCES campaigns(id),
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('TWITCH', 'YOUTUBE', 'KICK')),
    PRIMARY KEY (campaign_id, platform)
);
