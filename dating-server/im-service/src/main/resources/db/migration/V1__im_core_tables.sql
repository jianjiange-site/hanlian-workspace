CREATE TABLE IF NOT EXISTS im_registered_user (
    user_id BIGINT PRIMARY KEY,
    im_user_id VARCHAR(64) NOT NULL UNIQUE,
    nickname VARCHAR(128),
    avatar VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS im_conversation (
    conversation_id VARCHAR(128) PRIMARY KEY,
    user_id_a BIGINT NOT NULL,
    user_id_b BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_im_conversation_pair UNIQUE (user_id_a, user_id_b)
);

CREATE TABLE IF NOT EXISTS im_system_message (
    message_id VARCHAR(128) PRIMARY KEY,
    to_user_id BIGINT NOT NULL,
    biz_type VARCHAR(64) NOT NULL,
    title VARCHAR(128),
    content TEXT,
    payload_json TEXT,
    provider VARCHAR(32) NOT NULL DEFAULT 'mock',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_online_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    platform VARCHAR(64),
    online_at TIMESTAMPTZ NOT NULL,
    offline_at TIMESTAMPTZ,
    duration_seconds BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_user_online_session_open
    ON user_online_session (user_id, online_at)
    WHERE offline_at IS NULL AND deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_user_online_session_offline_at
    ON user_online_session (offline_at)
    WHERE offline_at IS NOT NULL AND deleted = FALSE;
