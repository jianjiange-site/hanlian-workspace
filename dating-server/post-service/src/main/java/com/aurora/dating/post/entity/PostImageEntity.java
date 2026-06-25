package com.aurora.dating.post.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("post_images")
public class PostImageEntity {

    private Long postId;

    private Integer sortOrder;

    private String imageKey;

    private OffsetDateTime createdAt;
}
