CREATE TABLE IF NOT EXISTS payment_callback_records (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    order_no VARCHAR(64),
    provider_trade_no VARCHAR(128),
    paid_amount_cents BIGINT,
    currency VARCHAR(16),
    raw_payload TEXT NOT NULL,
    process_status SMALLINT NOT NULL,
    error_message VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_callback_records_order_no
    ON payment_callback_records (order_no);

CREATE INDEX IF NOT EXISTS idx_payment_callback_records_provider_trade_no
    ON payment_callback_records (provider_trade_no);

CREATE INDEX IF NOT EXISTS idx_payment_callback_records_created_at
    ON payment_callback_records (created_at DESC);
