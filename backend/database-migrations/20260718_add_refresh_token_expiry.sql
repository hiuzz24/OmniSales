ALTER TABLE channel_credentials
    ADD COLUMN IF NOT EXISTS refresh_token_expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_channel_credentials_token_refresh
    ON channel_credentials (connection_state, token_expires_at);
