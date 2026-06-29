package com.aurora.dating.gateway;

import com.aurora.dating.gateway.client.UserIdentityClient;
import com.dating.hanlian.proto.user.v1.CheckBanResponse;
import com.dating.hanlian.proto.user.v1.ResolveOrCreateResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserIdentityDebugController {

    private static final String DEFAULT_APP_NAME = "hanlian";

    private final UserIdentityClient userIdentityClient;

    public UserIdentityDebugController(UserIdentityClient userIdentityClient) {
        this.userIdentityClient = userIdentityClient;
    }

    @GetMapping("/internal/debug/user-identity/phone")
    public Map<String, Object> resolvePhone(
            @RequestParam String phoneE164,
            @RequestParam(defaultValue = DEFAULT_APP_NAME) String appName) {
        ResolveOrCreateResponse response = userIdentityClient.resolveOrCreateByPhone(phoneE164, appName);
        return resolveResponse(response);
    }

    @GetMapping("/internal/debug/user-identity/device")
    public Map<String, Object> resolveDevice(
            @RequestParam String deviceId,
            @RequestParam int platform,
            @RequestParam(defaultValue = DEFAULT_APP_NAME) String appName) {
        ResolveOrCreateResponse response = userIdentityClient.resolveOrCreateByDevice(deviceId, platform, appName);
        return resolveResponse(response);
    }

    @GetMapping("/internal/debug/user-identity/third-party")
    public Map<String, Object> resolveThirdParty(
            @RequestParam int platform,
            @RequestParam String thirdPartyUserId,
            @RequestParam(defaultValue = DEFAULT_APP_NAME) String appName,
            @RequestParam(defaultValue = "") String email) {
        ResolveOrCreateResponse response =
                userIdentityClient.resolveOrCreateByThirdParty(platform, thirdPartyUserId, appName, email);
        return resolveResponse(response);
    }

    @GetMapping("/internal/debug/user-identity/check-ban")
    public Map<String, Object> checkBan(@RequestParam long userId) {
        CheckBanResponse response = userIdentityClient.checkBan(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("banned", response.getBanned());
        result.put("reason", response.getReason());
        result.put("bannedAtMs", response.getBannedAtMs());
        result.put("message", response.getMessage());
        return result;
    }

    private Map<String, Object> resolveResponse(ResolveOrCreateResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", response.getUserId());
        result.put("pending", response.getPending());
        result.put("created", response.getCreated());
        return result;
    }
}
