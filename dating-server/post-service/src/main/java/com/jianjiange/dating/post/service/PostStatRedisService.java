package com.jianjiange.dating.post.service;

import com.jianjiange.dating.post.config.PostCacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostStatRedisService {

    private final StringRedisTemplate stringRedisTemplate;
    private final PostCacheProperties cacheProperties;

    public void increaseLikeDelta(Long postId, int delta) {
        increaseDelta(likeDeltaKey(postId), postId, delta);
    }

    public void increaseCommentDelta(Long postId, int delta) {
        increaseDelta(commentDeltaKey(postId), postId, delta);
    }

    public int getLikeDelta(Long postId) {
        return getInt(likeDeltaKey(postId));
    }

    public int getCommentDelta(Long postId) {
        return getInt(commentDeltaKey(postId));
    }

    /**
     * 记录帖子计数的 Redis 增量
     *
     * 1. 将本次点赞/评论变化量 delta 累加到指定 Redis key
     * 2. 将发生变化的帖子 ID 放入 updated_set，供后续刷盘任务处理
     * @param key
     * @param postId
     * @param delta
     */
    private void increaseDelta(String key, Long postId, int delta) {
        stringRedisTemplate.opsForValue().increment(key, delta);
        stringRedisTemplate.expire(key, cacheProperties.getStatDeltaTtl());

        stringRedisTemplate.opsForSet().add(updatedSetKey(), String.valueOf(postId));
        stringRedisTemplate.expire(updatedSetKey(), cacheProperties.getStatDeltaTtl());
    }

    /**
     * 获取对应key里面的value
     * @param key
     * @return
     */
    private int getInt(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    /**
     * 拼接key
     * @param postId
     * @return
     */
    private String likeDeltaKey(Long postId) {
        return cacheProperties.getKeyPrefix() + ":post:stat:incr:" + postId + ":likes";
    }
    private String commentDeltaKey(Long postId) {
        return cacheProperties.getKeyPrefix() + ":post:stat:incr:" + postId + ":comments";
    }
    private String updatedSetKey() {
        return cacheProperties.getKeyPrefix() + ":post:updated_set";
    }

    /**
     * 取出点赞，评论的增量，并将该key设置为0
     * @param postId
     * @return
     */
    public Long popLikeDelta(Long postId) {
        return popDelta(likeDeltaKey(postId));
    }
    public Long popCommentDelta(Long postId) {
        return popDelta(commentDeltaKey(postId));
    }

    /**
     * 刷盘结束后，将set里面的postId删除
     * @param postId
     */
    public void removeUpdatedPost(Long postId) {
        stringRedisTemplate.opsForSet().remove(updatedSetKey(), String.valueOf(postId));
    }

    /**
     * 取出count个有变化的postId
     * @param count
     * @return
     */
    public List<String> listUpdatedPosts(int count) {
        Set<String> postIds = stringRedisTemplate.opsForSet().distinctRandomMembers(updatedSetKey(), count);
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(postIds);
    }

    /**
     * 读取key值，并将该key值设置为0
     * @param key
     * @return
     */
    private Long popDelta(String key) {
        String value = stringRedisTemplate.opsForValue().getAndSet(key, "0");
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(value);
    }
}
