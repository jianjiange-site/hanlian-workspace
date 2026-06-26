ALTER TABLE user_info
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

UPDATE user_info
SET user_id = id
WHERE user_id IS NULL;

ALTER TABLE user_info
    ALTER COLUMN user_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_info_user_id
    ON user_info(user_id);

COMMENT ON COLUMN user_info.user_id IS '业务用户 ID';
COMMENT ON COLUMN user_login_phone.user_id IS '关联 user_info.user_id';
COMMENT ON COLUMN user_device_registration.user_id IS '关联 user_info.user_id';
