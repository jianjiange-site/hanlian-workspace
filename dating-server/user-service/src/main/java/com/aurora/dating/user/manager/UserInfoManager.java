package com.aurora.dating.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.aurora.dating.common.id.SnowflakeIdGenerator;
import com.aurora.dating.user.entity.UserInfoEntity;
import com.aurora.dating.user.mapper.UserInfoMapper;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class UserInfoManager {

    private final UserInfoMapper userInfoMapper;
    private final SnowflakeIdGenerator idGenerator;

    public UserInfoManager(UserInfoMapper userInfoMapper, SnowflakeIdGenerator idGenerator) {
        this.userInfoMapper = userInfoMapper;
        this.idGenerator = idGenerator;
    }

    /**
     * 根据用户id查找一条状态正常的用户数据
     * @param userId
     * @return
     */
    public UserInfoEntity findByUserId(Long userId) {
        return userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfoEntity>()
                        .eq(UserInfoEntity::getUserId, userId)
                        .eq(UserInfoEntity::getDeleted, false)
                        .last("limit 1"));
    }

    /**
     * 插入一条用户数据做快捷登录
     *
     * 用户只输入用户名，给用户自动填充默认信息
     * @param appName
     * @return
     */
    public UserInfoEntity insertPlaceholder(String appName) {
        UserInfoEntity entity = new UserInfoEntity();
        entity.setUserId(idGenerator.nextId());
        entity.setAppName(appName);
        entity.setPending(true);
        entity.setNickname("User");
        entity.setGender(0);
        entity.setRegulationStatus(0);
        entity.setLastOpenAt(OffsetDateTime.now());

        userInfoMapper.insert(entity);

        return entity;
    }

    /**
     * 更新最近在线时间
     * @param userId
     */
    public void touchLastOpenAt(Long userId) {
        userInfoMapper.update(
                null,
                new LambdaUpdateWrapper<UserInfoEntity>()
                        .eq(UserInfoEntity::getUserId, userId)
                        .eq(UserInfoEntity::getDeleted, false)
                        .set(UserInfoEntity::getLastOpenAt, OffsetDateTime.now()));
    }
}
