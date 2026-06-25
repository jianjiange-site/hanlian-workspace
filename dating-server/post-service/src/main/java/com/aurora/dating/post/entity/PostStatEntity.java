package com.aurora.dating.post.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("post_stats")
public class PostStatEntity {

    @TableId
    private Long postId;

    private Integer likeCount;

    private Integer commentCount;

    private OffsetDateTime updatedAt;
}
