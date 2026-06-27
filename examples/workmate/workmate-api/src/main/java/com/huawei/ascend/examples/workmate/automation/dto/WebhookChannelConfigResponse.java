package com.huawei.ascend.examples.workmate.automation.dto;

public record WebhookChannelConfigResponse(
        String id, boolean enabled, String path, boolean secretConfigured) {}
