package com.huawei.ascend.examples.workmate.connector.dto;

public record OAuthRedirectStartResponse(String authorizeUrl, String state, int expiresIn) {}
