package com.jianjiange.dating.post.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

@TableName("post_images")
public class PostImageEntity {

    private Long postId;

    private Integer sortOrder;

    private String imageKey;

    private OffsetDateTime createdAt;

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
