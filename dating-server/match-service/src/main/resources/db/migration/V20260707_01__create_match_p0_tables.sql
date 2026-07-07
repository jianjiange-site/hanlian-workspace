CREATE TABLE IF NOT EXISTS user_swipe_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    target_user_type SMALLINT NOT NULL,
    action SMALLINT NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    match_id BIGINT,
    swiped_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE user_swipe_history IS '用户划卡历史表';
COMMENT ON COLUMN user_swipe_history.user_id IS '发起划卡的用户 ID';
COMMENT ON COLUMN user_swipe_history.target_user_id IS '被划卡的目标用户 ID';
COMMENT ON COLUMN user_swipe_history.target_user_type IS '目标用户类型：1 BH 真人，2 DH 数字人';
COMMENT ON COLUMN user_swipe_history.action IS '划卡动作：1 左划，2 右划，3 Super Hi';
COMMENT ON COLUMN user_swipe_history.status IS '状态：1 有效';
COMMENT ON COLUMN user_swipe_history.match_id IS '本次划卡产生的配对 ID，没有配对则为空';
COMMENT ON COLUMN user_swipe_history.swiped_at IS '划卡发生时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_swipe_history_user_target
    ON user_swipe_history (user_id, target_user_id);

CREATE INDEX IF NOT EXISTS idx_user_swipe_history_user_swiped_at
    ON user_swipe_history (user_id, swiped_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_swipe_history_target_action
    ON user_swipe_history (target_user_id, action);

CREATE TABLE IF NOT EXISTS match_pairs (
    match_id BIGSERIAL PRIMARY KEY,
    user_id_low BIGINT NOT NULL,
    user_id_high BIGINT NOT NULL,
    source SMALLINT NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    matched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE match_pairs IS '配对关系表';
COMMENT ON COLUMN match_pairs.match_id IS '配对 ID';
COMMENT ON COLUMN match_pairs.user_id_low IS '较小的用户 ID，用于保证双人唯一';
COMMENT ON COLUMN match_pairs.user_id_high IS '较大的用户 ID，用于保证双人唯一';
COMMENT ON COLUMN match_pairs.source IS '配对来源：1 普通互划，2 Super Hi';
COMMENT ON COLUMN match_pairs.status IS '状态：1 有效';
COMMENT ON COLUMN match_pairs.matched_at IS '配对成功时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_match_pairs_user_pair
    ON match_pairs (user_id_low, user_id_high);

CREATE INDEX IF NOT EXISTS idx_match_pairs_low_matched_at
    ON match_pairs (user_id_low, matched_at DESC);

CREATE INDEX IF NOT EXISTS idx_match_pairs_high_matched_at
    ON match_pairs (user_id_high, matched_at DESC);

CREATE TABLE IF NOT EXISTS like_record (
    id BIGSERIAL PRIMARY KEY,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    source SMALLINT NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    liked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE like_record IS '单向喜欢记录表';
COMMENT ON COLUMN like_record.from_user_id IS '喜欢发起人';
COMMENT ON COLUMN like_record.to_user_id IS '被喜欢的人';
COMMENT ON COLUMN like_record.source IS '来源：1 右划，2 DH 计划';
COMMENT ON COLUMN like_record.status IS '状态：1 有效';
COMMENT ON COLUMN like_record.liked_at IS '喜欢发生时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_like_record_from_to
    ON like_record (from_user_id, to_user_id);

CREATE INDEX IF NOT EXISTS idx_like_record_to_liked_at
    ON like_record (to_user_id, liked_at DESC)
    WHERE status = 1;

CREATE TABLE IF NOT EXISTS visit_record (
    id BIGSERIAL PRIMARY KEY,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    visit_count INT NOT NULL DEFAULT 1,
    status SMALLINT NOT NULL DEFAULT 1,
    visited_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE visit_record IS '访问记录表';
COMMENT ON COLUMN visit_record.from_user_id IS '访问发起人';
COMMENT ON COLUMN visit_record.to_user_id IS '被访问的人';
COMMENT ON COLUMN visit_record.visit_count IS '同一访问关系累计访问次数';
COMMENT ON COLUMN visit_record.status IS '状态：1 有效';
COMMENT ON COLUMN visit_record.visited_at IS '最近访问时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_visit_record_from_to
    ON visit_record (from_user_id, to_user_id);

CREATE INDEX IF NOT EXISTS idx_visit_record_to_visited_at
    ON visit_record (to_user_id, visited_at DESC)
    WHERE status = 1;
