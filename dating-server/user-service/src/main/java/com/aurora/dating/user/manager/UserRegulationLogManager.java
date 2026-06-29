package com.aurora.dating.user.manager;

import com.aurora.dating.user.entity.UserRegulationLogEntity;
import com.aurora.dating.user.mapper.UserRegulationLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class UserRegulationLogManager {

    private final UserRegulationLogMapper userRegulationLogMapper;

    public UserRegulationLogManager(UserRegulationLogMapper userRegulationLogMapper) {
        this.userRegulationLogMapper = userRegulationLogMapper;
    }

    public void insertLog(
            Long userId,
            Integer beforeStatus,
            Integer afterStatus,
            String reason,
            Integer operatorType,
            String operatorId) {
        UserRegulationLogEntity entity = new UserRegulationLogEntity();
        entity.setUserId(userId);
        entity.setBeforeStatus(beforeStatus);
        entity.setAfterStatus(afterStatus);
        entity.setReason(reason);
        entity.setOperatorType(operatorType);
        entity.setOperatorId(operatorId);
        entity.setCreatedAt(OffsetDateTime.now());

        userRegulationLogMapper.insert(entity);
    }

    public List<UserRegulationLogEntity> findByUserId(Long userId, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);

        return userRegulationLogMapper.selectList(new LambdaQueryWrapper<UserRegulationLogEntity>()
                .eq(UserRegulationLogEntity::getUserId, userId)
                .orderByDesc(UserRegulationLogEntity::getCreatedAt)
                .orderByDesc(UserRegulationLogEntity::getId)
                .last("limit " + safeLimit));
    }
}
