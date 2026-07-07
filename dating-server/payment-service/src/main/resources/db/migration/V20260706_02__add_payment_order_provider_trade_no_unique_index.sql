CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_orders_provider_trade_no
    ON payment_orders (provider_trade_no)
    WHERE provider_trade_no IS NOT NULL;
