BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
CREATE TABLE IF NOT EXISTS kis_connection (
    plan_id uuid PRIMARY KEY REFERENCES savings_plan(id) ON DELETE CASCADE,
    owner_email varchar(255) NOT NULL,
    product_code varchar(255) NOT NULL,
    account_encrypted varchar(2048) NOT NULL,
    app_key_encrypted varchar(2048) NOT NULL,
    app_secret_encrypted varchar(2048) NOT NULL,
    account_last_four varchar(255) NOT NULL,
    token_encrypted varchar(4096),
    token_expires_at timestamptz,
    last_attempt_at timestamptz,
    last_synced_at timestamptz,
    valuation bigint,
    cash bigint,
    error varchar(255)
);
CREATE INDEX IF NOT EXISTS kis_connection_owner_idx ON kis_connection(owner_email);
COMMIT;
