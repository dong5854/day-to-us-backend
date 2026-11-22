BEGIN;
CREATE TABLE personal_token (
    id uuid PRIMARY KEY,
    digest varchar(64) NOT NULL UNIQUE,
    owner_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    space_id uuid NOT NULL REFERENCES shared_space(id) ON DELETE CASCADE,
    name varchar(80) NOT NULL,
    access varchar(8) NOT NULL CHECK (access IN ('READ', 'EDIT')),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    last_used_at timestamptz,
    revoked_at timestamptz,
    window_start timestamptz NOT NULL,
    request_count integer NOT NULL DEFAULT 0
);
CREATE INDEX personal_token_owner_created ON personal_token(owner_id, created_at);
CREATE FUNCTION revoke_tokens_on_space_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE personal_token SET revoked_at = now()
    WHERE owner_id = OLD.id AND space_id = OLD.shared_space_id AND revoked_at IS NULL;
    RETURN NEW;
END;
$$;
CREATE TRIGGER revoke_tokens_on_space_change AFTER UPDATE OF shared_space_id ON users
FOR EACH ROW WHEN (OLD.shared_space_id IS DISTINCT FROM NEW.shared_space_id)
EXECUTE FUNCTION revoke_tokens_on_space_change();
COMMIT;
