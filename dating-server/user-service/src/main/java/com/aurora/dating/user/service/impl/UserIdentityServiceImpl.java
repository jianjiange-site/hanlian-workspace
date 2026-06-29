package com.aurora.dating.user.service.impl;

import com.aurora.dating.user.config.UserCacheProperties;
import com.aurora.dating.user.entity.UserDeviceRegistrationEntity;
import com.aurora.dating.user.entity.UserInfoEntity;
import com.aurora.dating.user.entity.UserLoginPhoneEntity;
import com.aurora.dating.user.manager.UserDeviceRegistrationManager;
import com.aurora.dating.user.manager.UserInfoManager;
import com.aurora.dating.user.manager.UserLoginPhoneManager;
import com.aurora.dating.user.service.ResolveOrCreateResult;
import com.aurora.dating.user.service.UserIdentityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import com.aurora.dating.user.entity.UserThirdPartyRegistrationEntity;
import com.aurora.dating.user.manager.UserThirdPartyRegistrationManager;

@Service
public class UserIdentityServiceImpl implements UserIdentityService {

    private static final Logger log = LoggerFactory.getLogger(UserIdentityServiceImpl.class);

    private final UserInfoManager userInfoManager;
    private final UserLoginPhoneManager userLoginPhoneManager;
    private final UserDeviceRegistrationManager userDeviceRegistrationManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserCacheProperties cacheProperties;
    private final RedissonClient redissonClient;
    private final UserThirdPartyRegistrationManager userThirdPartyRegistrationManager;

    public UserIdentityServiceImpl(
            UserInfoManager userInfoManager,
            UserLoginPhoneManager userLoginPhoneManager,
            UserDeviceRegistrationManager userDeviceRegistrationManager,
            StringRedisTemplate stringRedisTemplate,
            UserCacheProperties cacheProperties,
            RedissonClient redissonClient,
            UserThirdPartyRegistrationManager userThirdPartyRegistrationManager) {
        this.userInfoManager = userInfoManager;
        this.userLoginPhoneManager = userLoginPhoneManager;
        this.userDeviceRegistrationManager = userDeviceRegistrationManager;
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheProperties = cacheProperties;
        this.redissonClient = redissonClient;
        this.userThirdPartyRegistrationManager = userThirdPartyRegistrationManager;
    }


    /**
     * 手机号登录时，找到已有用户，或者创建 pending 用户
     * @param phoneE164
     * @param appName
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResolveOrCreateResult resolveOrCreateByPhone(String phoneE164, String appName) {
        validateText(phoneE164, "phoneE164");
        validateText(appName, "appName");

        String lockKey = registerPhoneLockKey(phoneE164, appName);
        return executeWithRegisterLock(lockKey, () -> doResolveOrCreateByPhone(phoneE164, appName));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoEntity bindPhone(Long userId, String phoneE164, String appName) {
        validatePositive(userId, "userId");
        validateText(phoneE164, "phoneE164");
        validateText(appName, "appName");

        String lockKey = registerPhoneLockKey(phoneE164, appName);
        return executeWithRegisterLock(lockKey, () -> doBindPhone(userId, phoneE164, appName));
    }

    private UserInfoEntity doBindPhone(Long userId, String phoneE164, String appName) {
        UserInfoEntity user = userInfoManager.findByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }

        UserLoginPhoneEntity binding = userLoginPhoneManager.findByPhoneAndApp(phoneE164, appName);
        if (binding != null && !userId.equals(binding.getUserId())) {
            throw new IllegalArgumentException("phone already bound");
        }
        if (binding == null) {
            userLoginPhoneManager.insertBinding(userId, phoneE164, appName);
        }

        userInfoManager.touchLastOpenAt(userId);
        return userInfoManager.findByUserId(userId);
    }

    /**
     * 执行手机号查找/创建逻辑
     *
     * 先查手机号是否绑定过用户；
     * 有就返回旧用户，
     * 没有就创建 pending 用户并绑定手机号
     * @param phoneE164
     * @param appName
     * @return
     */
    private ResolveOrCreateResult doResolveOrCreateByPhone(String phoneE164, String appName) {
        UserLoginPhoneEntity binding = userLoginPhoneManager.findByPhoneAndApp(phoneE164, appName);
        if (binding != null) {
            userInfoManager.touchLastOpenAt(binding.getUserId());
            UserInfoEntity user = userInfoManager.findByUserId(binding.getUserId());
            return new ResolveOrCreateResult(user, false);
        }

        UserInfoEntity user = userInfoManager.insertPlaceholder(appName);
        userLoginPhoneManager.insertBinding(user.getUserId(), phoneE164, appName);
        return new ResolveOrCreateResult(user, true);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResolveOrCreateResult resolveOrCreateByDevice(String deviceId, Integer platform, String appName) {
        validateText(deviceId, "deviceId");
        validatePositive(platform, "platform");
        validateText(appName, "appName");

        String lockKey = registerDeviceLockKey(deviceId, platform, appName);
        return executeWithRegisterLock(lockKey, () -> doResolveOrCreateByDevice(deviceId, platform, appName));
    }

    /**
     * 执行设备查找/创建逻辑
     *
     * 先查设备是否绑定过用户；
     * 有就返回旧用户，
     * 没有就创建 pending 用户并绑定设备
     * @param deviceId
     * @param platform
     * @param appName
     * @return
     */
    private ResolveOrCreateResult doResolveOrCreateByDevice(String deviceId, Integer platform, String appName) {
        UserDeviceRegistrationEntity binding =
                userDeviceRegistrationManager.findByDeviceAndApp(deviceId, platform, appName);
        if (binding != null) {
            userInfoManager.touchLastOpenAt(binding.getUserId());
            UserInfoEntity user = userInfoManager.findByUserId(binding.getUserId());
            return new ResolveOrCreateResult(user, false);
        }

        UserInfoEntity user = userInfoManager.insertPlaceholder(appName);
        userDeviceRegistrationManager.insertBinding(user.getUserId(), deviceId, platform, appName);
        return new ResolveOrCreateResult(user, true);
    }

    /**
     * 统一执行“加锁 -> 执行业务 -> 释放锁”
     *
     * 输入锁key，业务逻辑action
     * 输出调用逻辑action.get()
     * @param lockKey
     * @param action
     * @return
     * @param <T>
     */
    private <T> T executeWithRegisterLock(String lockKey, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("register lock acquire failed");
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("register lock interrupted", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String registerPhoneLockKey(String phoneE164, String appName) {
        return cacheProperties.getKeyPrefix() + ":lock:user:register:phone:" + phoneE164 + ":" + appName;
    }

    private String registerDeviceLockKey(String deviceId, Integer platform, String appName) {
        return cacheProperties.getKeyPrefix() + ":lock:user:register:device:" + platform + ":" + deviceId + ":" + appName;
    }

    /**
     * 查询用户是否封禁
     * @param userId
     * @return
     */
    @Override
    public boolean isBanned(Long userId) {
        validatePositive(userId, "userId");

        Boolean cached = getCachedBanStatus(userId);
        if (cached != null) {
            return cached;
        }

        boolean banned = queryBanStatusFromDb(userId);
        cacheBanStatus(userId, banned);
        return banned;
    }

    /**
     * 从redis里面取出来该用户的状态（true or false）
     * @param userId
     * @return
     */
    private Boolean getCachedBanStatus(Long userId) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(banStatusCacheKey(userId));
            if ("true".equals(cached)) {
                return true;
            }
            if ("false".equals(cached)) {
                return false;
            }
            return null;
        } catch (RuntimeException e) {
            log.warn("Read ban status cache failed, userId={}", userId, e);
            return null;
        }
    }

    /**
     * DB查询用户是否封禁（true or false）
     * @param userId
     * @return
     */
    private boolean queryBanStatusFromDb(Long userId) {
        UserInfoEntity user = userInfoManager.findByUserId(userId);
        if (user == null) {
            return false;
        }

        Integer regulationStatus = user.getRegulationStatus();
        return regulationStatus != null && (regulationStatus == 2 || regulationStatus == 5);
    }

    /**
     * 将用户封禁情况存入redis（true or false）
     * @param userId
     * @param banned
     */
    private void cacheBanStatus(Long userId, boolean banned) {
        try {
            stringRedisTemplate.opsForValue()
                    .set(banStatusCacheKey(userId), String.valueOf(banned), cacheProperties.getBanStatusTtl());
        } catch (RuntimeException e) {
            log.warn("Write ban status cache failed, userId={}", userId, e);
        }
    }

    /**
     * 拼接isBanned的key
     * @param userId
     * @return
     */
    private String banStatusCacheKey(Long userId) {
        return cacheProperties.getKeyPrefix() + ":user:ban:status:" + userId;
    }

    private void validateText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    /**
     * 查看userid是否合法
     * @param value
     * @param fieldName
     */
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

    /**
     * 第三方登录入口，负责校验参数、生成锁 key、加锁执行查找 / 创建逻辑
     * @param platform
     * @param thirdPartyUserId
     * @param appName
     * @param email
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResolveOrCreateResult resolveOrCreateByThirdParty(
            Integer platform,
            String thirdPartyUserId,
            String appName,
            String email) {
        validatePositive(platform, "platform");
        validateText(thirdPartyUserId, "thirdPartyUserId");
        validateText(appName, "appName");

        String lockKey = registerThirdPartyLockKey(platform, thirdPartyUserId, appName);
        return executeWithRegisterLock(
                lockKey,
                () -> doResolveOrCreateByThirdParty(platform, thirdPartyUserId, appName, email));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoEntity bindThirdParty(
            Long userId,
            Integer platform,
            String thirdPartyUserId,
            String appName,
            String email) {
        validatePositive(userId, "userId");
        validatePositive(platform, "platform");
        validateText(thirdPartyUserId, "thirdPartyUserId");
        validateText(appName, "appName");

        String lockKey = registerThirdPartyLockKey(platform, thirdPartyUserId, appName);
        return executeWithRegisterLock(
                lockKey,
                () -> doBindThirdParty(userId, platform, thirdPartyUserId, appName, email));
    }

    private UserInfoEntity doBindThirdParty(
            Long userId,
            Integer platform,
            String thirdPartyUserId,
            String appName,
            String email) {
        UserInfoEntity user = userInfoManager.findByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }

        UserThirdPartyRegistrationEntity binding =
                userThirdPartyRegistrationManager.findByPlatformAndThirdPartyUserId(platform, thirdPartyUserId, appName);
        if (binding != null && !userId.equals(binding.getUserId())) {
            throw new IllegalArgumentException("third party account already bound");
        }
        if (binding == null) {
            userThirdPartyRegistrationManager.insertBinding(userId, platform, thirdPartyUserId, appName, email);
        }

        userInfoManager.touchLastOpenAt(userId);
        return userInfoManager.findByUserId(userId);
    }

    /**
     * 执行第三方账号查找 / 创建逻辑
     * @param platform
     * @param thirdPartyUserId
     * @param appName
     * @param email
     * @return
     */
    private ResolveOrCreateResult doResolveOrCreateByThirdParty(
            Integer platform,
            String thirdPartyUserId,
            String appName,
            String email) {
        UserThirdPartyRegistrationEntity binding =
                userThirdPartyRegistrationManager.findByPlatformAndThirdPartyUserId(platform, thirdPartyUserId, appName);
        if (binding != null) {
            userInfoManager.touchLastOpenAt(binding.getUserId());
            UserInfoEntity user = userInfoManager.findByUserId(binding.getUserId());
            return new ResolveOrCreateResult(user, false);
        }

        UserInfoEntity user = userInfoManager.insertPlaceholder(appName);
        userThirdPartyRegistrationManager.insertBinding(user.getUserId(), platform, thirdPartyUserId, appName, email);
        return new ResolveOrCreateResult(user, true);
    }

    private String registerThirdPartyLockKey(Integer platform, String thirdPartyUserId, String appName) {
        return cacheProperties.getKeyPrefix()
                + ":lock:user:register:third-party:"
                + platform
                + ":"
                + thirdPartyUserId
                + ":"
                + appName;
    }
}
