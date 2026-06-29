package com.aurora.dating.user.manager;

import com.aurora.dating.user.entity.UserInterestEntity;
import com.aurora.dating.user.mapper.UserInterestMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class UserInterestManager {

    private final UserInterestMapper userInterestMapper;

    public UserInterestManager(UserInterestMapper userInterestMapper) {
        this.userInterestMapper = userInterestMapper;
    }

    public List<UserInterestEntity> findByUserId(Long userId) {
        return userInterestMapper.selectList(new LambdaQueryWrapper<UserInterestEntity>()
                .eq(UserInterestEntity::getUserId, userId)
                .orderByAsc(UserInterestEntity::getSortOrder)
                .orderByAsc(UserInterestEntity::getId));
    }

    @Transactional
    public void replaceByUserId(Long userId, List<UserInterestEntity> interests) {
        userInterestMapper.delete(new LambdaQueryWrapper<UserInterestEntity>()
                .eq(UserInterestEntity::getUserId, userId));

        for (UserInterestEntity interest : interests) {
            userInterestMapper.insert(interest);
        }
    }
}