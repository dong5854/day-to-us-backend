-- Apply before deploying the fixed-income API. Existing records remain expenses.
ALTER TABLE fixed_expense ADD COLUMN IF NOT EXISTS type varchar(16) NOT NULL DEFAULT 'EXPENSE';
