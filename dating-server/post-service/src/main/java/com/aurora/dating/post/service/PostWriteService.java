package com.aurora.dating.post.service;

import com.aurora.dating.common.id.SnowflakeIdGenerator;
import com.aurora.dating.post.exception.BusinessException;
import com.aurora.dating.post.manager.PostManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PostWriteService {

    private final PostManager postManager;
    private final SnowflakeIdGenerator idGenerator;
    private final FeedRedisService feedRedisService;

    public PostWriteService(PostManager postManager,
                            SnowflakeIdGenerator idGenerator,
                            FeedRedisService feedRedisService) {
        this.postManager = postManager;
        this.idGenerator = idGenerator;
        this.feedRedisService = feedRedisService;
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
        feedRedisService.addToColdStartPool(postId, userId);
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
            throw new BusinessException(HttpStatus.FORBIDDEN, "帖子不存在或没有操作权限");
        }

        feedRedisService.removePostFromFeedPools(postId);

        return true;
    }
}
