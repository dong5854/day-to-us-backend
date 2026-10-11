BEGIN;
SET LOCAL lock_timeout = '5s';
CREATE TABLE IF NOT EXISTS recurring_occurrence (
    id uuid PRIMARY KEY,
    fixed_expense_id uuid NOT NULL,
    scheduled_date date NOT NULL,
    budget_entry_id uuid,
    protected_entry boolean NOT NULL DEFAULT false,
    UNIQUE (fixed_expense_id, scheduled_date)
);
CREATE INDEX IF NOT EXISTS recurring_occurrence_budget_idx ON recurring_occurrence(budget_entry_id);
-- Existing rows have no edit history; the application imports them as protected.
ALTER TABLE fixed_expense ADD COLUMN IF NOT EXISTS occurrences_tracked boolean NOT NULL DEFAULT false;
COMMIT;
