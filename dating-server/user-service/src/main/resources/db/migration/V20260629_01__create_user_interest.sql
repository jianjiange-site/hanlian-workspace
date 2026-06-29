CREATE TABLE IF NOT EXISTS user_interest (
                                             id BIGSERIAL PRIMARY KEY,
                                             user_id BIGINT NOT NULL,
                                             interest_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    type SMALLINT NOT NULL,
    pic_key VARCHAR(256),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_user_interest_user_id_sort
    ON user_interest (user_id, sort_order, id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_interest_user_code
    ON user_interest (user_id, interest_code);