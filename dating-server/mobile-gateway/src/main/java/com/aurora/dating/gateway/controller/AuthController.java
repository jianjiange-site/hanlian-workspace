package com.aurora.dating.gateway.controller;

import com.aurora.dating.gateway.dto.LoginDeviceRequest;
import com.aurora.dating.gateway.dto.LoginPhoneRequest;
import com.aurora.dating.gateway.dto.BindPhoneRequest;
import com.aurora.dating.gateway.dto.BindThirdPartyRequest;
import com.aurora.dating.gateway.service.AuthService;
import com.aurora.dating.gateway.vo.BindAccountResponse;
import com.aurora.dating.gateway.vo.LoginResponse;
import com.aurora.dating.gateway.vo.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.aurora.dating.gateway.dto.LoginThirdPartyRequest;
import com.aurora.dating.gateway.security.JwtUserContext;
import com.aurora.dating.gateway.vo.MeResponse;
import com.aurora.dating.gateway.dto.RefreshTokenRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login-device")
    public Result<LoginResponse> loginDevice(@RequestBody LoginDeviceRequest request) {
        return Result.success(authService.loginDevice(request));
    }

    @PostMapping("/login-phone")
    public Result<LoginResponse> loginPhone(@RequestBody LoginPhoneRequest request) {
        return Result.success(authService.loginPhone(request));
    }

    @PostMapping("/login-third-party")
    public Result<LoginResponse> loginThirdParty(@RequestBody LoginThirdPartyRequest request) {
        return Result.success(authService.loginThirdParty(request));
    }

    @PostMapping("/bind-phone")
    public Result<BindAccountResponse> bindPhone(@RequestBody BindPhoneRequest request) {
        Long userId = JwtUserContext.getUserId();
        return Result.success(authService.bindPhone(userId, request));
    }

    @PostMapping("/bind-third-party")
    public Result<BindAccountResponse> bindThirdParty(@RequestBody BindThirdPartyRequest request) {
        Long userId = JwtUserContext.getUserId();
        return Result.success(authService.bindThirdParty(userId, request));
    }

    @PostMapping("/me")
    public Result<MeResponse> me() {
        Long userId = JwtUserContext.getUserId();
        return Result.success(new MeResponse(userId));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refresh(request));
    }
}
