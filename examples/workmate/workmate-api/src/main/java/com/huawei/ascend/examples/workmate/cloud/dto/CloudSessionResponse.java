package com.huawei.ascend.examples.workmate.cloud.dto;

import java.util.UUID;

public record CloudSessionResponse(
        UUID id,
        String expertId,
        String title,
        String status,
        String runtimeBaseUrl,
        String sandboxId,
        UUID linkedSessionId,
        String lastError,
        String createdAt,
        String updatedAt,
        String destroyedAt) {}
