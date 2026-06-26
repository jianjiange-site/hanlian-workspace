package com.aurora.dating.user.service.impl;

import com.aurora.dating.user.entity.UserDeviceRegistrationEntity;
import com.aurora.dating.user.entity.UserInfoEntity;
import com.aurora.dating.user.entity.UserLoginPhoneEntity;
import com.aurora.dating.user.manager.UserDeviceRegistrationManager;
import com.aurora.dating.user.manager.UserInfoManager;
import com.aurora.dating.user.manager.UserLoginPhoneManager;
import com.aurora.dating.user.service.UserIdentityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserIdentityServiceImpl implements UserIdentityService {

    private final UserInfoManager userInfoManager;
    private final UserLoginPhoneManager userLoginPhoneManager;
    private final UserDeviceRegistrationManager userDeviceRegistrationManager;

    public UserIdentityServiceImpl(
            UserInfoManager userInfoManager,
            UserLoginPhoneManager userLoginPhoneManager,
            UserDeviceRegistrationManager userDeviceRegistrationManager) {
        this.userInfoManager = userInfoManager;
        this.userLoginPhoneManager = userLoginPhoneManager;
        this.userDeviceRegistrationManager = userDeviceRegistrationManager;
    }

    /**
     * 先查手机号是否绑定过用户；
     * 有就返回旧用户，
     * 没有就创建 pending 用户并绑定手机号
     * @param phoneE164
     * @param appName
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoEntity resolveOrCreateByPhone(String phoneE164, String appName) {
        validateText(phoneE164, "phoneE164");
        validateText(appName, "appName");

        UserLoginPhoneEntity binding = userLoginPhoneManager.findByPhoneAndApp(phoneE164, appName);
        if (binding != null) {
            userInfoManager.touchLastOpenAt(binding.getUserId());
            return userInfoManager.findByUserId(binding.getUserId());
        }

        UserInfoEntity user = userInfoManager.insertPlaceholder(appName);
        userLoginPhoneManager.insertBinding(user.getUserId(), phoneE164, appName);
        return user;
    }

    /**
     * 先查设备是否绑定过用户；
     * 有就返回旧用户，
     * 没有就创建 pending 用户并绑定设备
     * @param deviceId
     * @param platform
     * @param appName
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoEntity resolveOrCreateByDevice(String deviceId, Integer platform, String appName) {
        validateText(deviceId, "deviceId");
        validatePositive(platform, "platform");
        validateText(appName, "appName");

        UserDeviceRegistrationEntity binding =
                userDeviceRegistrationManager.findByDeviceAndApp(deviceId, platform, appName);
        if (binding != null) {
            userInfoManager.touchLastOpenAt(binding.getUserId());
            return userInfoManager.findByUserId(binding.getUserId());
        }

        UserInfoEntity user = userInfoManager.insertPlaceholder(appName);
        userDeviceRegistrationManager.insertBinding(user.getUserId(), deviceId, platform, appName);
        return user;
    }

    /**
     * 查询用户是否封禁
     * @param userId
     * @return
     */
    @Override
    public boolean isBanned(Long userId) {
        validatePositive(userId, "userId");

        UserInfoEntity user = userInfoManager.findByUserId(userId);
        if (user == null) {
            return false;
        }

        Integer regulationStatus = user.getRegulationStatus();
        return regulationStatus != null && (regulationStatus == 2 || regulationStatus == 5);
    }

    private void validateText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private void validatePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private void validatePositive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
