-- user-service V1: identity schema
-- 目标：先跑通手机号 / 设备登录闭环，后续再补第三方、兴趣、头像等能力。

CREATE TABLE IF NOT EXISTS user_info (
                                         id BIGSERIAL PRIMARY KEY,
                                         app_name VARCHAR(32) NOT NULL,
    pending BOOLEAN NOT NULL DEFAULT TRUE,
    nickname VARCHAR(64) NOT NULL,
    gender SMALLINT NOT NULL DEFAULT 0,
    age SMALLINT,
    birthday DATE,
    bio VARCHAR(500),
    preferred_location VARCHAR(128),
    profession VARCHAR(128),
    education VARCHAR(128),
    height SMALLINT,
    custom_avatar JSONB,
    regulation_status SMALLINT NOT NULL DEFAULT 0,
    last_open_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
    );

COMMENT ON TABLE user_info IS '用户主表';
COMMENT ON COLUMN user_info.id IS '内部自增主键';
COMMENT ON COLUMN user_info.app_name IS '所属 App';
COMMENT ON COLUMN user_info.pending IS '是否资料未补全';
COMMENT ON COLUMN user_info.nickname IS '昵称';
COMMENT ON COLUMN user_info.gender IS '性别：0未知 1男 2女';
COMMENT ON COLUMN user_info.age IS '年龄';
COMMENT ON COLUMN user_info.birthday IS '生日';
COMMENT ON COLUMN user_info.bio IS '个人简介';
COMMENT ON COLUMN user_info.preferred_location IS '所在城市';
COMMENT ON COLUMN user_info.profession IS '职业';
COMMENT ON COLUMN user_info.education IS '学历';
COMMENT ON COLUMN user_info.height IS '身高，cm';
COMMENT ON COLUMN user_info.custom_avatar IS '头像对象信息 JSON';
COMMENT ON COLUMN user_info.regulation_status IS '监管状态';
COMMENT ON COLUMN user_info.last_open_at IS '最近打开时间';
COMMENT ON COLUMN user_info.created_at IS '创建时间';
COMMENT ON COLUMN user_info.updated_at IS '更新时间';
COMMENT ON COLUMN user_info.deleted IS '逻辑删除标记';

CREATE INDEX IF NOT EXISTS idx_user_info_app_name ON user_info(app_name);
CREATE INDEX IF NOT EXISTS idx_user_info_regulation_status ON user_info(regulation_status);
CREATE INDEX IF NOT EXISTS idx_user_info_last_open_at ON user_info(last_open_at);

CREATE TABLE IF NOT EXISTS user_login_phone (
                                                id BIGSERIAL PRIMARY KEY,
                                                user_id BIGINT NOT NULL,
                                                phone_e164 VARCHAR(32) NOT NULL,
    app_name VARCHAR(32) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
    );

COMMENT ON TABLE user_login_phone IS '手机号绑定表';
COMMENT ON COLUMN user_login_phone.id IS '内部自增主键';
COMMENT ON COLUMN user_login_phone.user_id IS '关联 user_info.id';
COMMENT ON COLUMN user_login_phone.phone_e164 IS 'E.164 手机号';
COMMENT ON COLUMN user_login_phone.app_name IS '所属 App';
COMMENT ON COLUMN user_login_phone.verified_at IS '验证时间';
COMMENT ON COLUMN user_login_phone.created_at IS '创建时间';
COMMENT ON COLUMN user_login_phone.updated_at IS '更新时间';
COMMENT ON COLUMN user_login_phone.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_login_phone_phone_app
    ON user_login_phone(phone_e164, app_name);

CREATE INDEX IF NOT EXISTS idx_user_login_phone_user_id
    ON user_login_phone(user_id);

CREATE TABLE IF NOT EXISTS user_device_registration (
                                                        id BIGSERIAL PRIMARY KEY,
                                                        user_id BIGINT NOT NULL,
                                                        device_id VARCHAR(128) NOT NULL,
    platform SMALLINT NOT NULL,
    app_name VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
    );

COMMENT ON TABLE user_device_registration IS '设备绑定表';
COMMENT ON COLUMN user_device_registration.id IS '内部自增主键';
COMMENT ON COLUMN user_device_registration.user_id IS '关联 user_info.id';
COMMENT ON COLUMN user_device_registration.device_id IS '设备 ID';
COMMENT ON COLUMN user_device_registration.platform IS '平台';
COMMENT ON COLUMN user_device_registration.app_name IS '所属 App';
COMMENT ON COLUMN user_device_registration.created_at IS '创建时间';
COMMENT ON COLUMN user_device_registration.updated_at IS '更新时间';
COMMENT ON COLUMN user_device_registration.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_device_reg_device_platform_app
    ON user_device_registration(device_id, platform, app_name);

CREATE INDEX IF NOT EXISTS idx_user_device_reg_user_id
    ON user_device_registration(user_id);