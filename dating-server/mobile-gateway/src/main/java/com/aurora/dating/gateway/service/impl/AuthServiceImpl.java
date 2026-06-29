package com.aurora.dating.gateway.service.impl;

import com.aurora.dating.gateway.client.UserIdentityClient;
import com.aurora.dating.gateway.dto.BindPhoneRequest;
import com.aurora.dating.gateway.dto.BindThirdPartyRequest;
import com.aurora.dating.gateway.dto.LoginDeviceRequest;
import com.aurora.dating.gateway.dto.LoginPhoneRequest;
import com.aurora.dating.gateway.dto.LoginThirdPartyRequest;
import com.aurora.dating.gateway.dto.RefreshTokenRequest;
import com.aurora.dating.gateway.entity.AuthRefreshTokenEntity;
import com.aurora.dating.gateway.exception.BizException;
import com.aurora.dating.gateway.exception.ErrorCodes;
import com.aurora.dating.gateway.manager.AuthRefreshTokenManager;
import com.aurora.dating.gateway.security.JwtIssuer;
import com.aurora.dating.gateway.security.JwtVerifier;
import com.aurora.dating.gateway.security.TokenHashUtil;
import com.aurora.dating.gateway.security.TokenPair;
import com.aurora.dating.gateway.service.AuthService;
import com.aurora.dating.gateway.vo.LoginResponse;
import com.aurora.dating.gateway.vo.BindAccountResponse;
import com.dating.hanlian.proto.user.v1.CheckBanResponse;
import com.dating.hanlian.proto.user.v1.ResolveOrCreateResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_APP_NAME = "hanlian";

    private final UserIdentityClient userIdentityClient;
    private final JwtIssuer jwtIssuer;
    private final JwtVerifier jwtVerifier;
    private final AuthRefreshTokenManager authRefreshTokenManager;

    public AuthServiceImpl(
            UserIdentityClient userIdentityClient,
            JwtIssuer jwtIssuer,
            JwtVerifier jwtVerifier,
            AuthRefreshTokenManager authRefreshTokenManager) {
        this.userIdentityClient = userIdentityClient;
        this.jwtIssuer = jwtIssuer;
        this.jwtVerifier = jwtVerifier;
        this.authRefreshTokenManager = authRefreshTokenManager;
    }

    @Override
    public LoginResponse loginDevice(LoginDeviceRequest request) {
        validateLoginDeviceRequest(request);

        String appName = StringUtils.hasText(request.appName()) ? request.appName() : DEFAULT_APP_NAME;
        ResolveOrCreateResponse resolveResponse = userIdentityClient.resolveOrCreateByDevice(
                request.deviceId(),
                request.platform(),
                appName);
        return buildLoginResponse(resolveResponse, request.deviceId());
    }

    @Override
    public LoginResponse loginPhone(LoginPhoneRequest request) {
        validateLoginPhoneRequest(request);

        String appName = StringUtils.hasText(request.appName()) ? request.appName() : DEFAULT_APP_NAME;
        ResolveOrCreateResponse resolveResponse = userIdentityClient.resolveOrCreateByPhone(
                request.phoneE164(),
                appName);
        return buildLoginResponse(resolveResponse, null);
    }

    @Override
    public LoginResponse loginThirdParty(LoginThirdPartyRequest request) {
        validateLoginThirdPartyRequest(request);

        String appName = StringUtils.hasText(request.appName()) ? request.appName() : DEFAULT_APP_NAME;
        ResolveOrCreateResponse resolveResponse = userIdentityClient.resolveOrCreateByThirdParty(
                request.platform(),
                request.thirdPartyUserId(),
                appName,
                request.email());

        return buildLoginResponse(resolveResponse, null);
    }

    @Override
    public BindAccountResponse bindPhone(Long userId, BindPhoneRequest request) {
        validatePositiveUserId(userId);
        validateBindPhoneRequest(request);

        String appName = StringUtils.hasText(request.appName()) ? request.appName() : DEFAULT_APP_NAME;
        com.dating.hanlian.proto.user.v1.BindAccountResponse response =
                userIdentityClient.bindPhone(userId, request.phoneE164(), appName);
        return new BindAccountResponse(response.getUserId());
    }

    @Override
    public BindAccountResponse bindThirdParty(Long userId, BindThirdPartyRequest request) {
        validatePositiveUserId(userId);
        validateBindThirdPartyRequest(request);

        String appName = StringUtils.hasText(request.appName()) ? request.appName() : DEFAULT_APP_NAME;
        com.dating.hanlian.proto.user.v1.BindAccountResponse response =
                userIdentityClient.bindThirdParty(
                        userId,
                        request.platform(),
                        request.thirdPartyUserId(),
                        appName,
                        request.email());
        return new BindAccountResponse(response.getUserId());
    }

    @Override
    public LoginResponse refresh(RefreshTokenRequest request) {
        validateRefreshTokenRequest(request);

        long userId = jwtVerifier.verifyRefreshToken(request.refreshToken());
        String oldTokenHash = TokenHashUtil.sha256(request.refreshToken());
        AuthRefreshTokenEntity oldToken = authRefreshTokenManager.findByTokenHash(oldTokenHash);
        if (oldToken == null || Boolean.TRUE.equals(oldToken.getRevoked())) {
            throw new BizException(ErrorCodes.TOKEN_INVALID, "refresh token invalid");
        }
        if (oldToken.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCodes.TOKEN_INVALID, "refresh token expired");
        }

        authRefreshTokenManager.revokeByTokenHash(oldTokenHash);

        TokenPair tokenPair = jwtIssuer.issue(userId);
        saveRefreshToken(userId, tokenPair, oldToken.getDeviceId());

        return new LoginResponse(
                userId,
                false,
                false,
                false,
                "",
                "",
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.expiresIn());
    }

    private LoginResponse buildLoginResponse(ResolveOrCreateResponse resolveResponse, String deviceId) {
        CheckBanResponse banResponse = userIdentityClient.checkBan(resolveResponse.getUserId());
        if (banResponse.getBanned()) {
            throw new BizException(ErrorCodes.USER_BANNED, banResponse.getMessage());
        }

        TokenPair tokenPair = jwtIssuer.issue(resolveResponse.getUserId());
        saveRefreshToken(resolveResponse.getUserId(), tokenPair, deviceId);

        return new LoginResponse(
                resolveResponse.getUserId(),
                resolveResponse.getPending(),
                resolveResponse.getCreated(),
                false,
                "",
                "",
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.expiresIn());
    }

    private void saveRefreshToken(long userId, TokenPair tokenPair, String deviceId) {
        LocalDateTime now = LocalDateTime.now();
        String tokenHash = TokenHashUtil.sha256(tokenPair.refreshToken());

        authRefreshTokenManager.saveToken(
                userId,
                tokenHash,
                deviceId,
                now,
                now.plusSeconds(tokenPair.refreshExpiresIn()));
    }

    private void validateRefreshTokenRequest(RefreshTokenRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!StringUtils.hasText(request.refreshToken())) {
            throw new IllegalArgumentException("refreshToken is required");
        }
    }

    private void validateLoginDeviceRequest(LoginDeviceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!StringUtils.hasText(request.deviceId())) {
            throw new IllegalArgumentException("deviceId is required");
        }
        if (request.platform() == null || request.platform() <= 0) {
            throw new IllegalArgumentException("platform must be positive");
        }
    }

    private void validateLoginPhoneRequest(LoginPhoneRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!StringUtils.hasText(request.phoneE164())) {
            throw new IllegalArgumentException("phoneE164 is required");
        }
        if (!StringUtils.hasText(request.smsCode())) {
            throw new IllegalArgumentException("smsCode is required");
        }
    }

    private void validateLoginThirdPartyRequest(LoginThirdPartyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.platform() == null || request.platform() <= 0) {
            throw new IllegalArgumentException("platform must be positive");
        }
        if (!StringUtils.hasText(request.thirdPartyUserId())) {
            throw new IllegalArgumentException("thirdPartyUserId is required");
        }
        if (!StringUtils.hasText(request.thirdPartyToken())) {
            throw new IllegalArgumentException("thirdPartyToken is required");
        }
    }

    private void validateBindPhoneRequest(BindPhoneRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!StringUtils.hasText(request.phoneE164())) {
            throw new IllegalArgumentException("phoneE164 is required");
        }
        if (!StringUtils.hasText(request.smsCode())) {
            throw new IllegalArgumentException("smsCode is required");
        }
    }

    private void validateBindThirdPartyRequest(BindThirdPartyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.platform() == null || request.platform() <= 0) {
            throw new IllegalArgumentException("platform must be positive");
        }
        if (!StringUtils.hasText(request.thirdPartyUserId())) {
            throw new IllegalArgumentException("thirdPartyUserId is required");
        }
        if (!StringUtils.hasText(request.thirdPartyToken())) {
            throw new IllegalArgumentException("thirdPartyToken is required");
        }
    }

    private void validatePositiveUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }
}
