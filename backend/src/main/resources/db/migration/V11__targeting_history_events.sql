ALTER TABLE campaign_events DROP CONSTRAINT campaign_events_event_type_check;
ALTER TABLE campaign_events ADD CONSTRAINT campaign_events_event_type_check
    CHECK (event_type IN ('CREATED', 'EDITED', 'ARCHIVED', 'TARGETING_CHANGED'));
