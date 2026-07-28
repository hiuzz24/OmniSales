ALTER TABLE channels
ADD COLUMN IF NOT EXISTS last_synced_application_at TIMESTAMPTZ;
