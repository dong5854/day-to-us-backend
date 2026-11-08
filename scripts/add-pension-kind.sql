-- Additive migration; existing savings and records retain their behavior.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE savings_plan ADD COLUMN IF NOT EXISTS kind varchar(255) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE savings_record ADD COLUMN IF NOT EXISTS confirmed boolean NOT NULL DEFAULT false;
COMMIT;
