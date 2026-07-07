CREATE TABLE IF NOT EXISTS payment_orders (
                                              id BIGSERIAL PRIMARY KEY,
                                              order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    product_type VARCHAR(32) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency VARCHAR(16) NOT NULL,
    coin_amount BIGINT NOT NULL DEFAULT 0,
    subscription_tier VARCHAR(32),
    status SMALLINT NOT NULL DEFAULT 10,
    provider_trade_no VARCHAR(128),
    paid_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_payment_orders_user_created_at
    ON payment_orders (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_orders_status
    ON payment_orders (status);

CREATE INDEX IF NOT EXISTS idx_payment_orders_provider_trade_no
    ON payment_orders (provider_trade_no);