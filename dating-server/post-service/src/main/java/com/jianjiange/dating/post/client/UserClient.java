package com.jianjiange.dating.post.client;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UserClient {

    /**
     * 获取用户好友 ID 列表。
     * 第一版先返回空，表示没有好友时间线数据。
     */
    public List<Long> getFriendUserIds(Long userId) {
        return List.of();
    }

    /**
     * 判断用户是否为男性。
     * 第一版临时规则：偶数 userId 当作男性，奇数当作女性。
     */
    public boolean isMale(Long userId) {
        if (userId == null) {
            return false;
        }
        return userId % 2 == 0;
    }

    /**
     * 批量获取性别。
     * FeedScoreJob 后面会用到。
     */
    public Map<Long, Boolean> getGenders(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        return userIds.stream()
                .collect(Collectors.toMap(
                        userId -> userId,
                        this::isMale,
                        (left, right) -> left
                ));
    }
}