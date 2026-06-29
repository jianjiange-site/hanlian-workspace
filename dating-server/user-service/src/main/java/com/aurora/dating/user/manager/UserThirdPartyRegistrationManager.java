package com.aurora.dating.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aurora.dating.user.entity.UserThirdPartyRegistrationEntity;
import com.aurora.dating.user.mapper.UserThirdPartyRegistrationMapper;
import org.springframework.stereotype.Component;

@Component
public class UserThirdPartyRegistrationManager {

    private final UserThirdPartyRegistrationMapper userThirdPartyRegistrationMapper;

    public UserThirdPartyRegistrationManager(UserThirdPartyRegistrationMapper userThirdPartyRegistrationMapper) {
        this.userThirdPartyRegistrationMapper = userThirdPartyRegistrationMapper;
    }

    /**
     * 根据第三方平台、第三方用户 ID、App 名查绑定关系
     * @param platform
     * @param thirdPartyUserId
     * @param appName
     * @return
     */
    public UserThirdPartyRegistrationEntity findByPlatformAndThirdPartyUserId(
            Integer platform,
            String thirdPartyUserId,
            String appName) {
        return userThirdPartyRegistrationMapper.selectOne(
                new LambdaQueryWrapper<UserThirdPartyRegistrationEntity>()
                        .eq(UserThirdPartyRegistrationEntity::getPlatform, platform)
                        .eq(UserThirdPartyRegistrationEntity::getThirdPartyUserId, thirdPartyUserId)
                        .eq(UserThirdPartyRegistrationEntity::getAppName, appName)
                        .eq(UserThirdPartyRegistrationEntity::getDeleted, false)
                        .last("limit 1"));
    }

    /**
     * 新增第三方账号绑定关系
     * @param userId
     * @param platform
     * @param thirdPartyUserId
     * @param appName
     * @param email
     * @return
     */
    public UserThirdPartyRegistrationEntity insertBinding(
            Long userId,
            Integer platform,
            String thirdPartyUserId,
            String appName,
            String email) {
        UserThirdPartyRegistrationEntity entity = new UserThirdPartyRegistrationEntity();
        entity.setUserId(userId);
        entity.setPlatform(platform);
        entity.setThirdPartyUserId(thirdPartyUserId);
        entity.setAppName(appName);
        entity.setEmail(email);
        entity.setDeleted(false);

        userThirdPartyRegistrationMapper.insert(entity);
        return entity;
    }
}
