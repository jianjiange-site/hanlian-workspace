package com.aurora.dating.gateway.manager;

import com.aurora.dating.gateway.entity.AuthRefreshTokenEntity;
import com.aurora.dating.gateway.mapper.AuthRefreshTokenMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuthRefreshTokenManager {

    private final AuthRefreshTokenMapper authRefreshTokenMapper;

    public AuthRefreshTokenManager(AuthRefreshTokenMapper authRefreshTokenMapper) {
        this.authRefreshTokenMapper = authRefreshTokenMapper;
    }

    public void saveToken(long userId, String tokenHash, String deviceId, LocalDateTime issuedAt, LocalDateTime expiredAt) {
        AuthRefreshTokenEntity entity = new AuthRefreshTokenEntity();
        entity.setUserId(userId);
        entity.setTokenHash(tokenHash);
        entity.setDeviceId(deviceId);
        entity.setIssuedAt(issuedAt);
        entity.setExpiredAt(expiredAt);
        entity.setRevoked(false);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        authRefreshTokenMapper.insert(entity);
    }

    public AuthRefreshTokenEntity findByTokenHash(String tokenHash) {
        return authRefreshTokenMapper.selectOne(
                new LambdaQueryWrapper<AuthRefreshTokenEntity>()
                        .eq(AuthRefreshTokenEntity::getTokenHash, tokenHash)
                        .last("LIMIT 1"));
    }

    public void revokeByTokenHash(String tokenHash) {
        AuthRefreshTokenEntity entity = findByTokenHash(tokenHash);
        if (entity == null) {
            return;
        }
        entity.setRevoked(true);
        entity.setRevokedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        authRefreshTokenMapper.updateById(entity);
    }
}
