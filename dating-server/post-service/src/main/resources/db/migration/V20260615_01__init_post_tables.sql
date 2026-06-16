CREATE TABLE IF NOT EXISTS posts (
                                     id BIGSERIAL PRIMARY KEY,
                                     post_id BIGINT NOT NULL UNIQUE,
                                     user_id BIGINT NOT NULL,
                                     content VARCHAR(1024) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    deleted SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_posts_user_created_at
    ON posts (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS post_images (
                                           post_id BIGINT NOT NULL,
                                           sort_order SMALLINT NOT NULL,
                                           image_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, sort_order)
    );

CREATE INDEX IF NOT EXISTS idx_post_images_post_id
    ON post_images (post_id);

CREATE TABLE IF NOT EXISTS post_stats (
                                          post_id BIGINT PRIMARY KEY,
                                          like_count INT NOT NULL DEFAULT 0,
                                          comment_count INT NOT NULL DEFAULT 0,
                                          updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_post_stats_like_count
    ON post_stats (like_count DESC);

CREATE INDEX IF NOT EXISTS idx_post_stats_comment_count
    ON post_stats (comment_count DESC);

CREATE TABLE IF NOT EXISTS post_likes (
                                          user_id BIGINT NOT NULL,
                                          post_id BIGINT NOT NULL,
                                          status SMALLINT NOT NULL DEFAULT 1,
                                          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          PRIMARY KEY (user_id, post_id)
    );

CREATE INDEX IF NOT EXISTS idx_post_likes_post_id_active
    ON post_likes (post_id)
    WHERE status = 1;

CREATE TABLE IF NOT EXISTS post_comments (
                                             id BIGSERIAL PRIMARY KEY,
                                             comment_id BIGINT NOT NULL UNIQUE,
                                             post_id BIGINT NOT NULL,
                                             user_id BIGINT NOT NULL,
                                             root_id BIGINT NOT NULL DEFAULT 0,
                                             parent_id BIGINT NOT NULL DEFAULT 0,
                                             reply_to_user_id BIGINT NOT NULL DEFAULT 0,
                                             content VARCHAR(512) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    deleted SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_post_comments_post_root_created_at
    ON post_comments (post_id, root_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_post_comments_root_created_at
    ON post_comments (root_id, created_at ASC);

CREATE TABLE IF NOT EXISTS shedlock (
                                        name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
    );