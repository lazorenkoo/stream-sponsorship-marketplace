CREATE TABLE creative_upload_intents (
    creative_id UUID PRIMARY KEY,
    format VARCHAR(10) NOT NULL CHECK (format IN ('PNG', 'JPEG', 'WEBP')),
    created_at TIMESTAMPTZ NOT NULL,
    cleanup_after TIMESTAMPTZ NOT NULL CHECK (cleanup_after >= created_at),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0)
);
CREATE INDEX creative_upload_cleanup_index ON creative_upload_intents(cleanup_after, creative_id);
