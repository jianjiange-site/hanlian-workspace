package com.aurora.dating.gateway.dto;

public record LoginDeviceRequest(
        String deviceId,
        Integer platform,
        String appName) {
}
