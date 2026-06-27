package com.huawei.ascend.examples.workmate.automation.dto;

import java.util.UUID;

public record AutomationJobResponse(
        UUID id,
        String name,
        boolean enabled,
        String expertId,
        String promptText,
        String cronExpression,
        String nextRunAt,
        String lastRunAt,
        UUID lastSessionId,
        String lastStatus,
        String lastError,
        String createdAt,
        String updatedAt) {}
