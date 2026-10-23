-- Apply before backend rollout. Additive and safe for the previous application.
BEGIN;
ALTER TABLE expense_category ADD COLUMN IF NOT EXISTS sort_order integer;
-- Preserve the database's existing scan order on the one-time migration.
WITH ordered AS (SELECT id, row_number() OVER (ORDER BY ctid) - 1 AS position FROM expense_category)
UPDATE expense_category c SET sort_order = o.position FROM ordered o WHERE c.id = o.id AND c.sort_order IS NULL;
ALTER TABLE expense_category ALTER COLUMN sort_order SET DEFAULT 0;
ALTER TABLE expense_category ALTER COLUMN sort_order SET NOT NULL;
ALTER TABLE expense_category ADD COLUMN IF NOT EXISTS archived boolean NOT NULL DEFAULT false;
CREATE TABLE IF NOT EXISTS category_budget (
    id uuid PRIMARY KEY,
    category_id uuid NOT NULL REFERENCES expense_category(id),
    budget_month date NOT NULL CHECK (EXTRACT(DAY FROM budget_month) = 1),
    recurring boolean NOT NULL,
    amount bigint CHECK (amount >= 0 AND amount <= 999999999999),
    UNIQUE (category_id, budget_month, recurring)
);
CREATE INDEX IF NOT EXISTS budget_entry_category_idx ON budget_entry(category_id);
CREATE INDEX IF NOT EXISTS fixed_expense_category_idx ON fixed_expense(category_id);
COMMIT;
