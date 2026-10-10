-- Backfill recurring entries from June 1, 2026, as requested.
BEGIN;
SET LOCAL lock_timeout = '5s';
ALTER TABLE fixed_expense ADD COLUMN IF NOT EXISTS auto_post_from date;
UPDATE fixed_expense SET auto_post_from = DATE '2026-06-01' WHERE auto_post_from IS NULL;
ALTER TABLE fixed_expense ALTER COLUMN auto_post_from SET NOT NULL;
ALTER TABLE fixed_expense ALTER COLUMN auto_post_from SET DEFAULT DATE '2026-06-01';
ALTER TABLE fixed_expense ADD COLUMN IF NOT EXISTS posted_through date;
COMMIT;
