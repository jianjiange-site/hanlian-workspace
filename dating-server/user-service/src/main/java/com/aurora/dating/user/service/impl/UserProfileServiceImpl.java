package com.aurora.dating.user.service.impl;

import com.aurora.dating.user.entity.UserInfoEntity;
import com.aurora.dating.user.entity.UserRegulationLogEntity;
import com.aurora.dating.user.manager.UserInfoManager;
import com.aurora.dating.user.service.UserProfileService;
import com.aurora.dating.user.storage.StorageProperties;
import org.springframework.stereotype.Service;
import com.aurora.dating.user.config.UserCacheProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.aurora.dating.user.entity.UserInterestEntity;
import com.aurora.dating.user.manager.UserInterestManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.aurora.dating.user.manager.UserRegulationLogManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserInfoManager userInfoManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final UserCacheProperties cacheProperties;
    private final UserInterestManager userInterestManager;
    private final UserRegulationLogManager userRegulationLogManager;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    private static final Set<String> AVATAR_ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long AVATAR_MAX_CONTENT_LENGTH = 10 * 1024 * 1024;
    private static final long AVATAR_UPLOAD_EXPIRE_SECONDS = 300L;

    private static final int MAX_INTEREST_COUNT = 50;
    private static final int MAX_PICTURE_INTEREST_COUNT = 9;
    private static final int INTEREST_TYPE_TEXT = 1;
    private static final int INTEREST_TYPE_PICTURE = 2;

    public UserProfileServiceImpl(
            UserInfoManager userInfoManager,
            UserInterestManager userInterestManager,
            UserRegulationLogManager userRegulationLogManager,
            S3Client s3Client,
            S3Presigner s3Presigner,
            StorageProperties storageProperties,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            UserCacheProperties cacheProperties) {
        this.userInfoManager = userInfoManager;
        this.userInterestManager = userInterestManager;
        this.userRegulationLogManager = userRegulationLogManager;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.storageProperties = storageProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    /**
     * 根据ID查询用户
     * @param userId
     * @return
     */
    @Override
    public UserInfoEntity getProfile(Long userId) {
        validatePositive(userId, "userId");

        UserInfoEntity cached = getCachedProfile(userId);
        if (cached != null) {
            return cached;
        }

        UserInfoEntity user = userInfoManager.findByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }

        cacheProfile(user);
        return user;
    }

    /**
     * 批量查询用户资料,优先走redis缓存
     * @param userIds
     * @return
     */
    @Override
    public List<UserInfoEntity> batchGetProfile(List<Long> userIds) {
        List<Long> normalizedUserIds = normalizeBatchUserIds(userIds);

        Map<Long, UserInfoEntity> userMap = new HashMap<>();
        List<Long> missedUserIds = new ArrayList<>();

        for (Long userId : normalizedUserIds) {
            UserInfoEntity cached = getCachedProfile(userId);
            if (cached == null) {
                missedUserIds.add(userId);
            } else {
                userMap.put(userId, cached);
            }
        }

        if (!missedUserIds.isEmpty()) {
            List<UserInfoEntity> dbUsers = userInfoManager.findByUserIds(missedUserIds);
            for (UserInfoEntity user : dbUsers) {
                cacheProfile(user);
                userMap.put(user.getUserId(), user);
            }
        }

        List<UserInfoEntity> result = new ArrayList<>();
        for (Long userId : normalizedUserIds) {
            UserInfoEntity user = userMap.get(userId);
            if (user != null) {
                result.add(user);
            }
        }

        return result;
    }

    /**
     * 校验并规范化批量 userId
     * @param userIds
     * @return
     */
    private List<Long> normalizeBatchUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("userIds is required");
        }
        if (userIds.size() > 100) {
            throw new IllegalArgumentException("userIds size must be <= 100");
        }

        LinkedHashSet<Long> uniqueUserIds = new LinkedHashSet<>();
        for (Long userId : userIds) {
            validatePositive(userId, "userId");
            uniqueUserIds.add(userId);
        }

        return new ArrayList<>(uniqueUserIds);
    }

    /**
     * 取redis
     * @param userId
     * @return
     */
    private UserInfoEntity getCachedProfile(Long userId) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(profileCacheKey(userId));
            if (cached == null) {
                return null;
            }
            return objectMapper.readValue(cached, UserInfoEntity.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 存redis
     * @param user
     */
    private void cacheProfile(UserInfoEntity user) {
        try {
            String json = objectMapper.writeValueAsString(user);
            stringRedisTemplate.opsForValue()
                    .set(profileCacheKey(user.getUserId()), json, cacheProperties.getProfileTtl());
        } catch (JsonProcessingException e) {
        } catch (RuntimeException e) {
        }
    }

    /**
     * 删redis
     * @param userId
     */
    private void evictProfileCache(Long userId) {
        try {
            stringRedisTemplate.delete(profileCacheKey(userId));
        } catch (RuntimeException e) {
        }
    }

    private String profileCacheKey(Long userId) {
        return cacheProperties.getKeyPrefix() + ":user:profile:" + userId;
    }

    private void validatePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    /**
     * 更新用户数据
     * @param userId
     * @param nickname
     * @param age
     * @param bio
     * @param location
     * @param occupation
     * @param education
     * @param height
     * @return
     */
    @Override
    public UserInfoEntity updateProfile(
            Long userId,
            String nickname,
            Integer age,
            String bio,
            String location,
            String occupation,
            String education,
            Integer height) {
        validatePositive(userId, "userId");
        validateNickname(nickname);
        validateAge(age);
        validateTextLength(bio, "bio", 500);
        validateTextLength(location, "location", 128);
        validateTextLength(occupation, "occupation", 128);
        validateTextLength(education, "education", 128);
        validateHeight(height);

        UserInfoEntity user = userInfoManager.findByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }

        userInfoManager.updateProfile(userId, nickname, age, bio, location, occupation, education, height);
        evictProfileCache(userId);
        return userInfoManager.findByUserId(userId);
    }

    private void validateNickname(String nickname) {
        if (nickname != null && nickname.length() > 64) {
            throw new IllegalArgumentException("nickname length must be <= 64");
        }
    }

    private void validateAge(Integer age) {
        if (age != null && (age < 0 || age > 120)) {
            throw new IllegalArgumentException("age must be between 0 and 120");
        }
    }

    private void validateHeight(Integer height) {
        if (height != null && (height < 0 || height > 260)) {
            throw new IllegalArgumentException("height must be between 0 and 260");
        }
    }

    private void validateTextLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must be <= " + maxLength);
        }
    }

    private LocalDate parseBirthday(String birthday) {
        if (birthday == null || birthday.isBlank()) {
            return null;
        }

        LocalDate birthdayDate;
        try {
            birthdayDate = LocalDate.parse(birthday.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("birthday must be yyyy-MM-dd");
        }

        if (birthdayDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("birthday must not be future");
        }
        return birthdayDate;
    }

    /**
     * 更新 onboarding 资料，并把用户标记为已完成资料补全pending = false
     * @param userId
     * @param nickname
     * @param gender
     * @param age
     * @param bio
     * @param location
     * @param occupation
     * @param education
     * @param height
     * @return
     */
    @Override
    public UserInfoEntity upsertOnboarding(
            Long userId,
            String nickname,
            Integer gender,
            Integer age,
            String bio,
            String location,
            String occupation,
            String education,
            Integer height,
            String birthday) {
        validatePositive(userId, "userId");
        validateNickname(nickname);
        validateGender(gender);
        validateAge(age);
        validateTextLength(bio, "bio", 500);
        validateTextLength(location, "location", 128);
        validateTextLength(occupation, "occupation", 128);
        validateTextLength(education, "education", 128);
        validateHeight(height);
        LocalDate birthdayDate = parseBirthday(birthday);

        UserInfoEntity user = userInfoManager.findByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }

        userInfoManager.upsertOnboarding(userId, nickname, gender, age, bio, location, occupation, education, height, birthdayDate);
        evictProfileCache(userId);
        return userInfoManager.findByUserId(userId);
    }

    private void validateGender(Integer gender) {
        if (gender != null && (gender < 0 || gender > 2)) {
            throw new IllegalArgumentException("gender must be between 0 and 2");
        }
    }

    /**
     * 生成头像上传凭证
     * @param userId
     * @param fileExt
     * @param contentType
     * @param contentLength
     * @return
     */
    @Override
    public AvatarUploadSignature presignAvatarUpload(
            Long userId,
            String fileExt,
            String contentType,
            Long contentLength) {
        validatePositive(userId, "userId");
        validateAvatarFileExt(fileExt);
        validateAvatarContentLength(contentLength);

        UserInfoEntity user = userInfoManager.findByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }

        String normalizedExt = normalizeAvatarFileExt(fileExt);
        String objectKey = "avatar/" + userId + "/" + UUID.randomUUID() + "." + normalizedExt;
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(storageProperties.getBucket())
                .key(objectKey)
                .contentType(normalizeAvatarContentType(contentType))
                .contentLength(contentLength)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(AVATAR_UPLOAD_EXPIRE_SECONDS))
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String uploadUrl = presignedRequest.url().toString();

        return new AvatarUploadSignature(uploadUrl, objectKey, AVATAR_UPLOAD_EXPIRE_SECONDS);
    }


    /**
     * 确认头像上传完成，并把头像 objectKey 写入用户资料
     * @param userId
     * @param objectKey
     * @return
     */
    @Override
    public UserInfoEntity confirmAvatarUpload(Long userId, String objectKey) {
        validatePositive(userId, "userId");
        validateAvatarObjectKey(userId, objectKey);

        UserInfoEntity user = userInfoManager.findByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }

        ensureAvatarObjectExists(objectKey);
        String customAvatar = buildAvatarJson(objectKey);
        userInfoManager.updateCustomAvatar(userId, customAvatar);
        evictProfileCache(userId);

        return userInfoManager.findByUserId(userId);
    }

    private String normalizeAvatarContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.trim();
    }

    private void ensureAvatarObjectExists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(storageProperties.getBucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IllegalArgumentException("avatar object not found");
            }
            throw e;
        }
    }

    private String buildAvatarJson(String objectKey) {
        return "{\"originalKey\":\"" + objectKey + "\",\"minKey\":\"" + objectKey + "\",\"midKey\":\"" + objectKey + "\",\"status\":\"READY\"}";
    }

    private void validateAvatarFileExt(String fileExt) {
        String normalizedExt = normalizeAvatarFileExt(fileExt);
        if (!AVATAR_ALLOWED_EXTENSIONS.contains(normalizedExt)) {
            throw new IllegalArgumentException("avatar fileExt must be jpg, jpeg, png or webp");
        }
    }

    private String normalizeAvatarFileExt(String fileExt) {
        if (fileExt == null || fileExt.isBlank()) {
            throw new IllegalArgumentException("fileExt is required");
        }
        String normalizedExt = fileExt.trim().toLowerCase(Locale.ROOT);
        if (normalizedExt.startsWith(".")) {
            normalizedExt = normalizedExt.substring(1);
        }
        return normalizedExt;
    }

    private void validateAvatarContentLength(Long contentLength) {
        if (contentLength == null || contentLength <= 0) {
            throw new IllegalArgumentException("contentLength must be positive");
        }
        if (contentLength > AVATAR_MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("avatar contentLength must be <= 10485760");
        }
    }

    private void validateAvatarObjectKey(Long userId, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey is required");
        }
        String expectedPrefix = "avatar/" + userId + "/";
        if (!objectKey.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("objectKey invalid");
        }
        String normalizedObjectKey = objectKey.toLowerCase(Locale.ROOT);
        boolean allowed = AVATAR_ALLOWED_EXTENSIONS.stream()
                .anyMatch(ext -> normalizedObjectKey.endsWith("." + ext));
        if (!allowed) {
            throw new IllegalArgumentException("objectKey fileExt invalid");
        }
    }

    @Override
    public List<UserInterestItem> getUserInterests(Long userId) {
        validatePositive(userId, "userId");

        List<UserInterestItem> cached = getCachedInterests(userId);
        if (cached != null) {
            return cached;
        }

        ensureUserExists(userId);

        List<UserInterestItem> interests = userInterestManager.findByUserId(userId).stream()
                .map(this::toUserInterestItem)
                .toList();
        cacheInterests(userId, interests);
        return interests;
    }

    @Override
    public List<UserInterestItem> replaceUserInterests(Long userId, List<UserInterestItem> interests) {
        validatePositive(userId, "userId");
        ensureUserExists(userId);

        List<UserInterestItem> normalizedInterests = normalizeInterests(interests);
        List<UserInterestEntity> entities = normalizedInterests.stream()
                .map(interest -> toUserInterestEntity(userId, interest))
                .toList();

        userInterestManager.replaceByUserId(userId, entities);
        evictInterestCache(userId);

        List<UserInterestItem> result = userInterestManager.findByUserId(userId).stream()
                .map(this::toUserInterestItem)
                .toList();
        cacheInterests(userId, result);
        return result;
    }

    @Override
    public UserInfoEntity updateRegulationStatus(Long userId, Integer regulationStatus, String reason) {
        validatePositive(userId, "userId");
        validateRegulationStatus(regulationStatus);
        validateTextLength(reason, "reason", 512);

        UserInfoEntity user = userInfoManager.findByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }

        Integer beforeStatus = user.getRegulationStatus() == null ? 0 : user.getRegulationStatus();

        userInfoManager.updateRegulationStatus(userId, regulationStatus);
        userRegulationLogManager.insertLog(
                userId,
                beforeStatus,
                regulationStatus,
                normalizeNullableText(reason),
                0,
                null);

        evictProfileCache(userId);
        evictBanStatusCache(userId);

        return userInfoManager.findByUserId(userId);
    }

    @Override
    public List<RegulationLogItem> listRegulationLogs(Long userId, Integer limit) {
        validatePositive(userId, "userId");
        ensureUserExists(userId);

        return userRegulationLogManager.findByUserId(userId, limit).stream()
                .map(this::toRegulationLogItem)
                .toList();
    }

    private RegulationLogItem toRegulationLogItem(UserRegulationLogEntity entity) {
        return new RegulationLogItem(
                entity.getId(),
                entity.getUserId(),
                entity.getBeforeStatus(),
                entity.getAfterStatus(),
                entity.getReason(),
                entity.getOperatorType(),
                entity.getOperatorId(),
                entity.getCreatedAt() == null ? 0L : entity.getCreatedAt().toInstant().toEpochMilli());
    }

    private void ensureUserExists(Long userId) {
        UserInfoEntity user = userInfoManager.findByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
    }

    private List<UserInterestItem> normalizeInterests(List<UserInterestItem> interests) {
        if (interests == null) {
            throw new IllegalArgumentException("interests is required");
        }
        if (interests.size() > MAX_INTEREST_COUNT) {
            throw new IllegalArgumentException("interests size must be <= 50");
        }

        List<UserInterestItem> result = new ArrayList<>();
        Set<String> interestCodes = new HashSet<>();
        int pictureCount = 0;

        for (int i = 0; i < interests.size(); i++) {
            UserInterestItem interest = interests.get(i);
            validateInterest(interest);

            String interestCode = interest.interestCode().trim();
            if (!interestCodes.add(interestCode)) {
                throw new IllegalArgumentException("interestCode duplicated");
            }

            Integer type = interest.type();
            if (type == INTEREST_TYPE_PICTURE) {
                pictureCount++;
            }
            if (pictureCount > MAX_PICTURE_INTEREST_COUNT) {
                throw new IllegalArgumentException("picture interests size must be <= 9");
            }

            result.add(new UserInterestItem(
                    interestCode,
                    interest.displayName().trim(),
                    type,
                    normalizeNullableText(interest.picKey()),
                    interest.sortOrder() == null ? i : interest.sortOrder()));
        }

        return result;
    }

    private void validateInterest(UserInterestItem interest) {
        if (interest == null) {
            throw new IllegalArgumentException("interest is required");
        }
        validateRequiredText(interest.interestCode(), "interestCode", 64);
        validateRequiredText(interest.displayName(), "displayName", 64);
        if (interest.type() == null
                || (interest.type() != INTEREST_TYPE_TEXT && interest.type() != INTEREST_TYPE_PICTURE)) {
            throw new IllegalArgumentException("interest type invalid");
        }
        validateTextLength(interest.picKey(), "picKey", 256);
        if (interest.type() == INTEREST_TYPE_PICTURE
                && (interest.picKey() == null || interest.picKey().isBlank())) {
            throw new IllegalArgumentException("picKey is required for picture interest");
        }
    }

    private void validateRequiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must be <= " + maxLength);
        }
    }

    private String normalizeNullableText(String value) {
        return value == null ? null : value.trim();
    }

    private UserInterestEntity toUserInterestEntity(Long userId, UserInterestItem interest) {
        UserInterestEntity entity = new UserInterestEntity();
        entity.setUserId(userId);
        entity.setInterestCode(interest.interestCode());
        entity.setDisplayName(interest.displayName());
        entity.setType(interest.type());
        entity.setPicKey(interest.picKey());
        entity.setSortOrder(interest.sortOrder());
        return entity;
    }

    private UserInterestItem toUserInterestItem(UserInterestEntity entity) {
        return new UserInterestItem(
                entity.getInterestCode(),
                entity.getDisplayName(),
                entity.getType(),
                entity.getPicKey(),
                entity.getSortOrder());
    }

    private List<UserInterestItem> getCachedInterests(Long userId) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(interestCacheKey(userId));
            if (cached == null) {
                return null;
            }
            return objectMapper.readValue(cached, new TypeReference<List<UserInterestItem>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    private void cacheInterests(Long userId, List<UserInterestItem> interests) {
        try {
            String json = objectMapper.writeValueAsString(interests);
            stringRedisTemplate.opsForValue()
                    .set(interestCacheKey(userId), json, cacheProperties.getProfileTtl());
        } catch (JsonProcessingException e) {
        } catch (RuntimeException e) {
        }
    }

    private void evictInterestCache(Long userId) {
        try {
            stringRedisTemplate.delete(interestCacheKey(userId));
        } catch (RuntimeException e) {
        }
    }

    private String interestCacheKey(Long userId) {
        return cacheProperties.getKeyPrefix() + ":user:interest:" + userId;
    }

    private void validateRegulationStatus(Integer regulationStatus) {
        if (regulationStatus == null || regulationStatus < 0 || regulationStatus > 5) {
            throw new IllegalArgumentException("regulationStatus must be between 0 and 5");
        }
    }

    private void evictBanStatusCache(Long userId) {
        try {
            stringRedisTemplate.delete(banStatusCacheKey(userId));
        } catch (RuntimeException e) {
        }
    }

    private String banStatusCacheKey(Long userId) {
        return cacheProperties.getKeyPrefix() + ":user:ban:status:" + userId;
    }
}
