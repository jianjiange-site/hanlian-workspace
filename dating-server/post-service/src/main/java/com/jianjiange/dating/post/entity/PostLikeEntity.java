package com.jianjiange.dating.post.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("post_likes")
public class PostLikeEntity {

    private Long userId;

    private Long postId;

    private Integer status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
