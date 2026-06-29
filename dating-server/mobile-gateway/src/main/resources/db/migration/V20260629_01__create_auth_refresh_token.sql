CREATE TABLE IF NOT EXISTS auth_refresh_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    device_id VARCHAR(128),
    issued_at TIMESTAMP NOT NULL,
    expired_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_auth_refresh_token_hash
    ON auth_refresh_token (token_hash);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_user_id
    ON auth_refresh_token (user_id);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_expired_at
    ON auth_refresh_token (expired_at);
