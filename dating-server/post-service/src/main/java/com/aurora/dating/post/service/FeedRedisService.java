package com.aurora.dating.post.service;

import com.aurora.dating.post.client.UserClient;
import com.aurora.dating.post.config.PostCacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FeedRedisService {

    private static final Duration FEED_POOL_TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;
    private final PostCacheProperties cacheProperties;
    private final UserClient userClient;

    /**
     * 冷启动池
     *
     * 将新建的帖子加入冷启动池
     * 根据性别选择加入到male/female池
     * 根据当前时间为score
     * TTL=7day
     * @param postId
     * @param authorUserId
     */
    public void addToColdStartPool(Long postId, Long authorUserId) {
        boolean male = userClient.isMale(authorUserId);
        String key = coldStartPoolKey(male);

        double score = System.currentTimeMillis() / 1000.0;
        stringRedisTemplate.opsForZSet().add(key, String.valueOf(postId), score);
        stringRedisTemplate.expire(key, FEED_POOL_TTL);
    }

    private String coldStartPoolKey(boolean male) {
        return cacheProperties.getKeyPrefix()
                + ":feed:cold_start:pool:"
                + (male ? "male" : "female");
    }

    /**
     * 读取冷启动池
     *
     * 按score（发帖时间）从大到小取
     * 将冷启动池的postId读取到List<Long>
     * @param viewerUserId
     * @param limit
     * @return
     */
    public List<Long> listColdStartPostIds(Long viewerUserId, int limit) {
        boolean viewerMale = userClient.isMale(viewerUserId);
        String key = coldStartPoolKey(!viewerMale);

        Set<String> postIdTexts = stringRedisTemplate.opsForZSet()
                .reverseRange(key, 0, limit - 1);

        if (postIdTexts == null || postIdTexts.isEmpty()) {
            return List.of();
        }

        return postIdTexts.stream()
                .map(Long::parseLong)
                .toList();
    }

    /**
     * 写入推荐池
     *
     * 按分数来排序
     * @param male
     * @param candidates
     */
    public void rebuildRecommendPool(boolean male, List<FeedCandidate> candidates) {
        String key = recommendPoolKey(male);
        stringRedisTemplate.delete(key);

        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        for (FeedCandidate candidate : candidates) {
            stringRedisTemplate.opsForZSet().add(
                    key,
                    String.valueOf(candidate.postId()),
                    candidate.score()
            );
        }

        stringRedisTemplate.expire(key, FEED_POOL_TTL);
    }

    public record FeedCandidate(
            Long postId,
            double score
    ) {
    }

    /**
     * 读取推荐池
     *
     * 读取的是异性的推荐池数据
     * @param viewerUserId
     * @param limit
     * @return
     */
    public List<Long> listRecommendPostIds(Long viewerUserId, int limit) {
        boolean viewerMale = userClient.isMale(viewerUserId);
        String key = recommendPoolKey(!viewerMale);

        Set<String> postIdTexts = stringRedisTemplate.opsForZSet()
                .reverseRange(key, 0, limit - 1);

        if (postIdTexts == null || postIdTexts.isEmpty()) {
            return List.of();
        }

        return postIdTexts.stream()
                .map(Long::parseLong)
                .toList();
    }

    /**
     * 推荐池的key拼接
     * @param male
     * @return
     */
    private String recommendPoolKey(boolean male) {
        return cacheProperties.getKeyPrefix()
                + ":feed:pool:recommend:"
                + (male ? "male" : "female");
    }

    /**
     * 标记以推送过的帖子
     *
     * 将postIds存入Redis
     * @param userId
     * @param postIds
     */
    public void markSeen(Long userId, List<Long> postIds) {
        if (userId == null || userId <= 0 || postIds == null || postIds.isEmpty()) {
            return;
        }

        String key = seenKey(userId);

        String[] values = postIds.stream()
                .map(String::valueOf)
                .toArray(String[]::new);

        stringRedisTemplate.opsForSet().add(key, values);
        stringRedisTemplate.expire(key, FEED_POOL_TTL);
    }

    private String seenKey(Long userId) {
        return cacheProperties.getKeyPrefix()
                + ":feed:seen:"
                + userId;
    }

    public boolean hasSeen(Long userId, Long postId) {
        if (userId == null || userId <= 0 || postId == null || postId <= 0) {
            return false;
        }

        Boolean result = stringRedisTemplate.opsForSet()
                .isMember(seenKey(userId), String.valueOf(postId));

        return Boolean.TRUE.equals(result);
    }

    public void removePostFromFeedPools(Long postId) {
        if (postId == null || postId <= 0) {
            return;
        }

        String postIdText = String.valueOf(postId);

        stringRedisTemplate.opsForZSet().remove(coldStartPoolKey(true), postIdText);
        stringRedisTemplate.opsForZSet().remove(coldStartPoolKey(false), postIdText);
        stringRedisTemplate.opsForZSet().remove(recommendPoolKey(true), postIdText);
        stringRedisTemplate.opsForZSet().remove(recommendPoolKey(false), postIdText);
    }


}