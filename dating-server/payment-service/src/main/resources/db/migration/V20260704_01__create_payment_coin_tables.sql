CREATE TABLE IF NOT EXISTS payment_coin_accounts (
                                                     user_id BIGINT PRIMARY KEY,
                                                     balance BIGINT NOT NULL DEFAULT 0,
                                                     total_recharge BIGINT NOT NULL DEFAULT 0,
                                                     total_consume BIGINT NOT NULL DEFAULT 0,
                                                     status SMALLINT NOT NULL DEFAULT 1,
                                                     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                     updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS payment_coin_ledger (
                                                   id BIGSERIAL PRIMARY KEY,
                                                   ledger_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    change_amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    direction SMALLINT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    remark VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_payment_coin_ledger_user_created_at
    ON payment_coin_ledger (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_coin_ledger_reason
    ON payment_coin_ledger (reason);