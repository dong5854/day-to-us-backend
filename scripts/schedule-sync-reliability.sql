-- Run before deploying the schedule sync changes. Existing records remain visible.
ALTER TABLE schedule ADD COLUMN IF NOT EXISTS deleted_at timestamp;
ALTER TABLE schedule ADD COLUMN IF NOT EXISTS google_user_id bigint;
ALTER TABLE schedule ADD COLUMN IF NOT EXISTS google_calendar_id varchar(255);
ALTER TABLE schedule ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
-- Legacy records have no reliable calendar history. Their origin is captured on
-- a successful write or an actual pull; a 404 on an unknown calendar is not
-- treated as confirmed deletion. Do not invent historical IDs.
