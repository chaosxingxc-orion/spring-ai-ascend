package com.huawei.ascend.examples.workmate.cloud.dto;

public record CloudSessionHealthResponse(
        String cloudSessionId,
        String status,
        String runtimeBaseUrl,
        boolean healthy,
        String message) {}
