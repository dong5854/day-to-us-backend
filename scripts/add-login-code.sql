-- Ephemeral, hashed login grants. No JWTs or raw codes are stored here.
CREATE TABLE IF NOT EXISTS login_code (
    code_hash varchar(255) PRIMARY KEY,
    binding_hash varchar(255) NOT NULL,
    user_id bigint NOT NULL REFERENCES users(id),
    expires_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS login_code_expires_at_idx ON login_code(expires_at);
