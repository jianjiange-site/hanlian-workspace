package com.aurora.dating.gateway.service;

import com.aurora.dating.gateway.dto.LoginDeviceRequest;
import com.aurora.dating.gateway.dto.LoginPhoneRequest;
import com.aurora.dating.gateway.dto.BindPhoneRequest;
import com.aurora.dating.gateway.dto.BindThirdPartyRequest;
import com.aurora.dating.gateway.vo.LoginResponse;
import com.aurora.dating.gateway.dto.LoginThirdPartyRequest;
import com.aurora.dating.gateway.dto.RefreshTokenRequest;
import com.aurora.dating.gateway.vo.BindAccountResponse;

public interface AuthService {

    LoginResponse loginDevice(LoginDeviceRequest request);

    LoginResponse loginPhone(LoginPhoneRequest request);

    LoginResponse loginThirdParty(LoginThirdPartyRequest request);

    BindAccountResponse bindPhone(Long userId, BindPhoneRequest request);

    BindAccountResponse bindThirdParty(Long userId, BindThirdPartyRequest request);

    LoginResponse refresh(RefreshTokenRequest request);
}
