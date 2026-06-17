package com.jianjiange.dating.post.service;

import com.jianjiange.dating.post.config.SnowflakeIdGenerator;
import com.jianjiange.dating.post.manager.PostManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PostWriteService {

    private final PostManager postManager;
    private final SnowflakeIdGenerator idGenerator;

    public PostWriteService(PostManager postManager, SnowflakeIdGenerator idGenerator) {
        this.postManager = postManager;
        this.idGenerator = idGenerator;
    }

    /**
     * 创建帖子
     * @param userId
     * @param content
     * @param imageKeys
     * @return
     */
    @Transactional
    public Long createPost(Long userId, String content, List<String> imageKeys) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("content is required");
        }
        if (content.length() > 1024) {
            throw new IllegalArgumentException("content length must be <= 1024");
        }
        if (imageKeys == null) {
            imageKeys = List.of();
        }
        if (imageKeys.size() > 9) {
            throw new IllegalArgumentException("image count must be <= 9");
        }

        Long postId = idGenerator.nextId();
        postManager.createPost(postId, userId, content, imageKeys);
        return postId;
    }

    /**
     * 删除帖子
     * @param userId
     * @param postId
     * @return
     */
    @Transactional
    public boolean deletePost(Long userId, Long postId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId is required");
        }

        int updated = postManager.markPostDeleted(postId, userId);
        if (updated == 0) {
            throw new IllegalArgumentException("post not found or no permission");
        }

        return true;
    }
}
