package com.aurora.dating.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aurora.dating.user.entity.UserDeviceRegistrationEntity;
import com.aurora.dating.user.mapper.UserDeviceRegistrationMapper;
import org.springframework.stereotype.Component;

@Component
public class UserDeviceRegistrationManager {

    private final UserDeviceRegistrationMapper userDeviceRegistrationMapper;

    public UserDeviceRegistrationManager(UserDeviceRegistrationMapper userDeviceRegistrationMapper) {
        this.userDeviceRegistrationMapper = userDeviceRegistrationMapper;
    }

    /**
     * 查询绑定关系
     *
     * 根据设备ID，平台，用户名
     * @param deviceId
     * @param platform
     * @param appName
     * @return
     */
    public UserDeviceRegistrationEntity findByDeviceAndApp(String deviceId, Integer platform, String appName) {
        return userDeviceRegistrationMapper.selectOne(
                new LambdaQueryWrapper<UserDeviceRegistrationEntity>()
                        .eq(UserDeviceRegistrationEntity::getDeviceId, deviceId)
                        .eq(UserDeviceRegistrationEntity::getPlatform, platform)
                        .eq(UserDeviceRegistrationEntity::getAppName, appName)
                        .eq(UserDeviceRegistrationEntity::getDeleted, false)
                        .last("limit 1"));
    }

    /**
     * 新增绑定关系
     * @param userId
     * @param deviceId
     * @param platform
     * @param appName
     * @return
     */
    public UserDeviceRegistrationEntity insertBinding(Long userId, String deviceId, Integer platform, String appName) {
        UserDeviceRegistrationEntity entity = new UserDeviceRegistrationEntity();
        entity.setUserId(userId);
        entity.setDeviceId(deviceId);
        entity.setPlatform(platform);
        entity.setAppName(appName);
        entity.setDeleted(false);

        userDeviceRegistrationMapper.insert(entity);
        return entity;
    }
}