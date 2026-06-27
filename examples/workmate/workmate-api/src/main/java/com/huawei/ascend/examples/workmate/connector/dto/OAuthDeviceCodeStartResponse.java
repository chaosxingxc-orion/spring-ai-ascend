package com.huawei.ascend.examples.workmate.connector.dto;

public record OAuthDeviceCodeStartResponse(
        String sessionId,
        String userCode,
        String deviceCode,
        String verificationUri,
        String method,
        int expiresIn) {}
