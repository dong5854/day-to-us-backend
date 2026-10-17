-- Apply before deploying the savings backend (production uses ddl-auto=validate).
BEGIN;
CREATE TABLE IF NOT EXISTS savings_plan (
    id uuid PRIMARY KEY, shared_space_id uuid NOT NULL REFERENCES shared_space(id),
    name varchar(255) NOT NULL, monthly_amount bigint, first_date date, auto_post_from date,
    maturity_date date, target_amount bigint, payer_email varchar(255) NOT NULL,
    payer_name varchar(255) NOT NULL, memo varchar(1000) NOT NULL DEFAULT '',
    archived boolean NOT NULL DEFAULT false, version bigint NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS savings_plan_space_idx ON savings_plan(shared_space_id);
CREATE TABLE IF NOT EXISTS savings_record (
    id uuid PRIMARY KEY, plan_id uuid NOT NULL REFERENCES savings_plan(id), date date NOT NULL,
    amount bigint NOT NULL CHECK (amount <> 0), payer_email varchar(255) NOT NULL,
    payer_name varchar(255) NOT NULL, memo varchar(1000) NOT NULL DEFAULT '', occurrence_month date,
    edited boolean NOT NULL DEFAULT false, deleted boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0, UNIQUE(plan_id, occurrence_month)
);
CREATE INDEX IF NOT EXISTS savings_record_plan_date_idx ON savings_record(plan_id, date);
COMMIT;
