CREATE TABLE channel_credentials (
    channel_id UUID PRIMARY KEY REFERENCES connected_channels(id) ON DELETE CASCADE,
    access_token_ciphertext TEXT NOT NULL,
    refresh_token_ciphertext TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_validated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT credentials_access_encrypted CHECK (access_token_ciphertext LIKE 'v1.%'),
    CONSTRAINT credentials_refresh_encrypted CHECK (refresh_token_ciphertext LIKE 'v1.%')
);
