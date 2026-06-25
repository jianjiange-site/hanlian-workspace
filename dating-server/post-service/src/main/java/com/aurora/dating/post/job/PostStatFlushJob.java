package com.aurora.dating.post.job;

import com.aurora.dating.post.manager.PostManager;
import com.aurora.dating.post.service.PostStatRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor

public class PostStatFlushJob {

    private final PostStatRedisService postStatRedisService;
    private final PostManager postManager;

    /**
     * 点赞，评论刷盘任务
     */
    @Scheduled(fixedDelay = 300000)
    public void flushPostStats() {
        var postIds = postStatRedisService.listUpdatedPosts(100);
        if (postIds.isEmpty()) {
            return;
        }
        for (String postIdText : postIds) {
            Long postId = Long.parseLong(postIdText);
            Long likeDelta = postStatRedisService.popLikeDelta(postId);
            Long commentDelta = postStatRedisService.popCommentDelta(postId);

            if (likeDelta != 0) {
                postManager.increaseLikeCount(postId, likeDelta.intValue());
            }
            if (commentDelta != 0) {
                postManager.increaseCommentCount(postId, commentDelta.intValue());
            }

            postStatRedisService.removeUpdatedPost(postId);
        }
    }
}