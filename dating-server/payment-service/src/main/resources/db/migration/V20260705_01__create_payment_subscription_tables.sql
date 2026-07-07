CREATE TABLE IF NOT EXISTS payment_user_subscriptions (
    user_id BIGINT PRIMARY KEY,
    tier VARCHAR(32) NOT NULL,
    expire_at TIMESTAMPTZ,
    status SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_payment_user_subscriptions_tier
    ON payment_user_subscriptions (tier);

CREATE INDEX IF NOT EXISTS idx_payment_user_subscriptions_expire_at
    ON payment_user_subscriptions (expire_at);