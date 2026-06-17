package com.jianjiange.dating.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;


@Getter
@Setter
@TableName("post_comments")
public class PostCommentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long commentId;

    private Long postId;

    private Long userId;

    private Long rootId;

    private Long parentId;

    private Long replyToUserId;

    private String content;

    private Integer status;

    private Integer deleted;

    private OffsetDateTime createdAt;
}
