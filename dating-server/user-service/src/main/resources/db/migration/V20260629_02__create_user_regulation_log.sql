CREATE TABLE IF NOT EXISTS user_regulation_log (
                                                   id BIGSERIAL PRIMARY KEY,
                                                   user_id BIGINT NOT NULL,
                                                   before_status SMALLINT NOT NULL,
                                                   after_status SMALLINT NOT NULL,
                                                   reason VARCHAR(512),
    operator_type SMALLINT NOT NULL DEFAULT 0,
    operator_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_user_regulation_log_user_id_created_at
    ON user_regulation_log (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_regulation_log_after_status_created_at
    ON user_regulation_log (after_status, created_at DESC);