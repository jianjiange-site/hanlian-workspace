CREATE TABLE IF NOT EXISTS user_third_party_registration (
                                                             id BIGSERIAL PRIMARY KEY,
                                                             user_id BIGINT NOT NULL,
                                                             platform SMALLINT NOT NULL,
                                                             third_party_user_id VARCHAR(128) NOT NULL,
    app_name VARCHAR(32) NOT NULL,
    email VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
    );

COMMENT ON TABLE user_third_party_registration IS '第三方账号绑定表';
COMMENT ON COLUMN user_third_party_registration.id IS '内部自增主键';
COMMENT ON COLUMN user_third_party_registration.user_id IS '关联 user_info.user_id';
COMMENT ON COLUMN user_third_party_registration.platform IS '第三方平台';
COMMENT ON COLUMN user_third_party_registration.third_party_user_id IS '第三方平台用户唯一 ID';
COMMENT ON COLUMN user_third_party_registration.app_name IS '所属 App';
COMMENT ON COLUMN user_third_party_registration.email IS '第三方平台返回的邮箱';
COMMENT ON COLUMN user_third_party_registration.created_at IS '创建时间';
COMMENT ON COLUMN user_third_party_registration.updated_at IS '更新时间';
COMMENT ON COLUMN user_third_party_registration.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_third_party_platform_uid_app
    ON user_third_party_registration(platform, third_party_user_id, app_name);

CREATE INDEX IF NOT EXISTS idx_user_third_party_user_id
    ON user_third_party_registration(user_id);