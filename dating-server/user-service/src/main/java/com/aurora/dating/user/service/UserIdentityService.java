package com.aurora.dating.user.service;

import com.aurora.dating.user.entity.UserInfoEntity;

public interface UserIdentityService {

    UserInfoEntity resolveOrCreateByPhone(String phoneE164, String appName);

    UserInfoEntity resolveOrCreateByDevice(String deviceId, Integer platform, String appName);

    boolean isBanned(Long userId);
}
