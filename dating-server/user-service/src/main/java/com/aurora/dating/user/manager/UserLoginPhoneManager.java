package com.aurora.dating.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aurora.dating.user.entity.UserLoginPhoneEntity;
import com.aurora.dating.user.mapper.UserLoginPhoneMapper;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * 手机号与用户名绑定关系的数据操作层
 */
@Component
public class UserLoginPhoneManager {

    private final UserLoginPhoneMapper userLoginPhoneMapper;

    public UserLoginPhoneManager(UserLoginPhoneMapper userLoginPhoneMapper) {
        this.userLoginPhoneMapper = userLoginPhoneMapper;
    }

    /**
     * 查询手机号和用户名的绑定关系
     *
     * 有绑定关系，返回绑定实体
     * 没有绑定关系，返回null
     * @param phoneE164
     * @param appName
     * @return
     */
    public UserLoginPhoneEntity findByPhoneAndApp(String phoneE164, String appName) {
        return userLoginPhoneMapper.selectOne(
                new LambdaQueryWrapper<UserLoginPhoneEntity>()
                        .eq(UserLoginPhoneEntity::getPhoneE164, phoneE164)
                        .eq(UserLoginPhoneEntity::getAppName, appName)
                        .eq(UserLoginPhoneEntity::getDeleted, false)
                        .last("limit 1"));
    }

    /**
     * 插入一条用户名和手机号的绑定关系
     * @param userId
     * @param phoneE164
     * @param appName
     * @return
     */
    public UserLoginPhoneEntity insertBinding(Long userId, String phoneE164, String appName) {
        UserLoginPhoneEntity entity = new UserLoginPhoneEntity();
        entity.setUserId(userId);
        entity.setPhoneE164(phoneE164);
        entity.setAppName(appName);
        entity.setVerifiedAt(OffsetDateTime.now());
        entity.setDeleted(false);

        userLoginPhoneMapper.insert(entity);
        return entity;
    }
}
