package com.aurora.dating.user.service;

import com.aurora.dating.user.entity.UserInfoEntity;

public interface UserIdentityService {

    ResolveOrCreateResult resolveOrCreateByPhone(String phoneE164, String appName);

    ResolveOrCreateResult resolveOrCreateByDevice(String deviceId, Integer platform, String appName);

    boolean isBanned(Long userId);

    ResolveOrCreateResult resolveOrCreateByThirdParty(
            Integer platform,
            String thirdPartyUserId,
            String appName,
            String email);

    UserInfoEntity bindPhone(Long userId, String phoneE164, String appName);

    UserInfoEntity bindThirdParty(
            Long userId,
            Integer platform,
            String thirdPartyUserId,
            String appName,
            String email);

}
