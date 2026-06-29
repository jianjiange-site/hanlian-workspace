package com.aurora.dating.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.aurora.dating.common.id.SnowflakeIdGenerator;
import com.aurora.dating.user.entity.UserInfoEntity;
import com.aurora.dating.user.mapper.UserInfoMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.List;
import java.time.LocalDateTime;

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
     * 按业务 user_id 批量查询用户资料
     * @param userIds
     * @return
     */
    public List<UserInfoEntity> findByUserIds(List<Long> userIds) {
        return userInfoMapper.selectList(
                new LambdaQueryWrapper<UserInfoEntity>()
                        .in(UserInfoEntity::getUserId, userIds)
                        .eq(UserInfoEntity::getDeleted, false));
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

    /**
     * 更新用户资料
     * @param userId
     * @param nickname
     * @param age
     * @param bio
     * @param location
     * @param occupation
     * @param education
     * @param height
     */
    public void updateProfile(
            Long userId,
            String nickname,
            Integer age,
            String bio,
            String location,
            String occupation,
            String education,
            Integer height) {
        LambdaUpdateWrapper<UserInfoEntity> wrapper = new LambdaUpdateWrapper<UserInfoEntity>()
                .eq(UserInfoEntity::getUserId, userId)
                .eq(UserInfoEntity::getDeleted, false)
                .set(StringUtils.hasText(nickname), UserInfoEntity::getNickname, nickname)
                .set(age != null && age > 0, UserInfoEntity::getAge, age)
                .set(bio != null, UserInfoEntity::getBio, bio)
                .set(location != null, UserInfoEntity::getPreferredLocation, location)
                .set(occupation != null, UserInfoEntity::getProfession, occupation)
                .set(education != null, UserInfoEntity::getEducation, education)
                .set(height != null && height > 0, UserInfoEntity::getHeight, height)
                .set(UserInfoEntity::getUpdatedAt, OffsetDateTime.now());

        userInfoMapper.update(null, wrapper);
    }

    /**
     *写入 onboarding 资料，并把用户标记为已完成资料补全pending = false
     * @param userId
     * @param nickname
     * @param gender
     * @param age
     * @param bio
     * @param location
     * @param occupation
     * @param education
     * @param height
     */
    public void upsertOnboarding(
            Long userId,
            String nickname,
            Integer gender,
            Integer age,
            String bio,
            String location,
            String occupation,
            String education,
            Integer height,
            LocalDate birthday) {
        LambdaUpdateWrapper<UserInfoEntity> wrapper = new LambdaUpdateWrapper<UserInfoEntity>()
                .eq(UserInfoEntity::getUserId, userId)
                .eq(UserInfoEntity::getDeleted, false)
                .set(UserInfoEntity::getPending, false)
                .set(StringUtils.hasText(nickname), UserInfoEntity::getNickname, nickname)
                .set(gender != null && gender >= 0, UserInfoEntity::getGender, gender)
                .set(age != null && age > 0, UserInfoEntity::getAge, age)
                .set(birthday != null, UserInfoEntity::getBirthday, birthday)
                .set(bio != null, UserInfoEntity::getBio, bio)
                .set(location != null, UserInfoEntity::getPreferredLocation, location)
                .set(occupation != null, UserInfoEntity::getProfession, occupation)
                .set(education != null, UserInfoEntity::getEducation, education)
                .set(height != null && height > 0, UserInfoEntity::getHeight, height)
                .set(UserInfoEntity::getUpdatedAt, OffsetDateTime.now());

        userInfoMapper.update(null, wrapper);
    }

    public void updateCustomAvatar(Long userId, String customAvatar) {
        userInfoMapper.updateCustomAvatar(userId, customAvatar);
    }

    public void updateRegulationStatus(Long userId, Integer regulationStatus) {
        UserInfoEntity entity = new UserInfoEntity();
        entity.setRegulationStatus(regulationStatus);
        entity.setUpdatedAt(OffsetDateTime.now());

        userInfoMapper.update(entity, new LambdaUpdateWrapper<UserInfoEntity>()
                .eq(UserInfoEntity::getUserId, userId));
    }


}
