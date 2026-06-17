package com.jianjiange.dating.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jianjiange.dating.post.entity.PostLikeEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

@Mapper
public interface PostLikeMapper extends BaseMapper<PostLikeEntity> {

    @Insert("""
            INSERT INTO post_likes (user_id, post_id, status, created_at, updated_at)
            VALUES (#{userId}, #{postId}, #{status}, #{now}, #{now})
            ON CONFLICT (user_id, post_id)
            DO UPDATE SET status = EXCLUDED.status,
                          updated_at = EXCLUDED.updated_at
            """)
    int upsertLike(@Param("userId") Long userId,
                   @Param("postId") Long postId,
                   @Param("status") int status,
                   @Param("now") OffsetDateTime now);
}
