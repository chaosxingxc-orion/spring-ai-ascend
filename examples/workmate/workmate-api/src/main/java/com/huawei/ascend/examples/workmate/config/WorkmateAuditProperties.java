package com.huawei.ascend.examples.workmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.audit")
public record WorkmateAuditProperties(int previewMaxChars, int maxPayloadBytes, boolean failCloseEnabled) {}
