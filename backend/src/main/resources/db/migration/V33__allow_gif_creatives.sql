ALTER TABLE ad_creatives
    DROP CONSTRAINT ad_creatives_format_check,
    ADD CONSTRAINT ad_creatives_format_check CHECK (format IN ('PNG', 'JPEG', 'WEBP', 'GIF'));

ALTER TABLE creative_upload_intents
    DROP CONSTRAINT creative_upload_intents_format_check,
    ADD CONSTRAINT creative_upload_intents_format_check CHECK (format IN ('PNG', 'JPEG', 'WEBP', 'GIF'));
