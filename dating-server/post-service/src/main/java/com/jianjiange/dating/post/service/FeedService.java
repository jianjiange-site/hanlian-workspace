package com.jianjiange.dating.post.service;

import com.dating.hanlian.proto.post.v1.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FeedService {

    private final FeedRedisService feedRedisService;
    private final PostReadService postReadService;

    private static final double RECOMMEND_RATIO = 0.7;

    /**
     * 获取推荐 Feed
     *
     * 逻辑：
     * 1. 先从推荐池取帖子
     * 2. 再从冷启动池补充
     * 3. 过滤掉用户已经看过的帖子
     * 4. 返回分页结果
     */
    public RecommendFeedResult getRecommendFeed(Long userId, int pageSize) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }

        int size = pageSize <= 0 ? 10 : Math.min(pageSize, 20);
        int queryLimit = size + 1;

        List<Long> recommendIds = feedRedisService.listRecommendPostIds(userId, queryLimit);
        List<Long> coldStartIds = feedRedisService.listColdStartPostIds(userId, queryLimit);

        List<Long> mergedIds = mixFeedPostIds(userId, recommendIds, coldStartIds, queryLimit);

        boolean hasMore = mergedIds.size() > size;
        if (hasMore) {
            mergedIds = mergedIds.subList(0, size);
        }

        long nextCursorPostId = mergedIds.isEmpty()
                ? 0L
                : mergedIds.get(mergedIds.size() - 1);

        feedRedisService.markSeen(userId, mergedIds);

        List<Post> posts = mergedIds.stream()
                .map(postId -> getPostDetailOrNull(userId, postId))
                .filter(Objects::nonNull)
                .toList();

        return new RecommendFeedResult(posts, nextCursorPostId, hasMore);
    }

    /**
     * 推荐池比例：70%推荐池+30%冷启动池
     * @param userId
     * @param recommendIds
     * @param coldStartIds
     * @param limit
     * @return
     */
    private List<Long> mixFeedPostIds(Long userId,
                                      List<Long> recommendIds,
                                      List<Long> coldStartIds,
                                      int limit) {
        List<Long> result = new ArrayList<>();

        int recommendQuota = (int) Math.ceil(limit * RECOMMEND_RATIO);
        int coldStartQuota = limit - recommendQuota;

        addUnseenPostIds(result, recommendIds, userId, recommendQuota);
        addUnseenPostIds(result, coldStartIds, userId, recommendQuota + coldStartQuota);

        if (result.size() < limit) {
            addUnseenPostIds(result, recommendIds, userId, limit);
        }

        if (result.size() < limit) {
            addUnseenPostIds(result, coldStartIds, userId, limit);
        }

        return result;
    }


    /**
     * 从 source 里挑出没看过、没重复、并且不超过 size 的帖子
     */
    private void addUnseenPostIds(List<Long> target, List<Long> source, Long userId, int size) {
        for (Long postId : source) {
            if (target.size() >= size) {
                break;
            }
            if (target.contains(postId)) {
                continue;
            }
            if (feedRedisService.hasSeen(userId, postId)) {
                continue;
            }

            target.add(postId);
        }
    }

    private Post getPostDetailOrNull(Long userId, Long postId) {
        try {
            return postReadService.getPostDetail(userId, postId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 推荐 Feed 结果
     */
    public record RecommendFeedResult(
            List<Post> posts,
            long nextCursorPostId,
            boolean hasMore
    ) {
    }
}
