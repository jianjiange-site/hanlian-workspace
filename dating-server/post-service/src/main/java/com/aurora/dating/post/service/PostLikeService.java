package com.aurora.dating.post.service;

import com.aurora.dating.post.entity.PostLikeEntity;
import com.aurora.dating.post.entity.PostStatEntity;
import com.aurora.dating.post.exception.BusinessException;
import com.aurora.dating.post.manager.PostManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostLikeService {

    private static final int STATUS_LIKED = 1;
    private static final int STATUS_UNLIKED = 0;

    private final PostManager postManager;
    private final PostStatRedisService postStatRedisService;

    /**
     * 点赞或取消点赞帖子。
     *
     * @param userId 执行点赞动作的用户业务 ID，必须大于 0
     * @param postId 被操作的帖子业务 ID，必须存在且未删除
     * @param liked true 表示点赞，false 表示取消点赞
     * @return 点赞动作执行后的状态和当前点赞数
     */
    @Transactional
    public LikePostResult likePost(Long userId, Long postId, boolean liked) {
        validate(userId, postId);
        if (!postManager.existsNormalPost(postId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "帖子不存在");
        }

        // 1. 读取用户当前点赞状态，避免重复点赞或重复取消导致计数不准。
        Optional<PostLikeEntity> oldLike = postManager.findLikeByUserIdAndPostId(userId, postId);
        int newStatus = liked ? STATUS_LIKED : STATUS_UNLIKED;
        int oldStatus = oldLike.map(PostLikeEntity::getStatus).orElse(STATUS_UNLIKED);

        if (oldLike.isEmpty()) {
            postManager.upsertLike(userId, postId, newStatus);
        } else if (oldStatus != newStatus) {
            postManager.updateLikeStatus(userId, postId, newStatus);
        }

        // 2. 只有状态发生变化才更新计数，重复请求保持幂等。
        int delta = calculateDelta(oldStatus, newStatus);
        if (delta != 0) {
            //postManager.increaseLikeCount(postId, delta);
            postStatRedisService.increaseLikeDelta(postId, delta);
        }

        int dbLikeCount = postManager.findStatByPostId(postId)
                .map(PostStatEntity::getLikeCount)
                .orElse(0);
        int likeCount = dbLikeCount + postStatRedisService.getLikeDelta(postId);
        likeCount = Math.max(likeCount, 0);
        return new LikePostResult(true, liked, likeCount);
    }

    /**
     * 判断点赞人和帖子的有效性
     * @param userId
     * @param postId
     */
    private void validate(Long userId, Long postId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId is required");
        }
    }

    private int calculateDelta(int oldStatus, int newStatus) {
        if (oldStatus == newStatus) {
            return 0;
        }
        return newStatus == STATUS_LIKED ? 1 : -1;
    }

    public record LikePostResult(
            boolean success,
            boolean liked,
            int likeCount
    ) {
    }
}
