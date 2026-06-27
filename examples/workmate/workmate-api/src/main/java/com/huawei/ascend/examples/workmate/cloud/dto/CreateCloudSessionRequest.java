package com.huawei.ascend.examples.workmate.cloud.dto;

import jakarta.validation.constraints.Size;

public record CreateCloudSessionRequest(
        @Size(max = 128) String expertId,
        @Size(max = 512) String title,
        @Size(max = 128) String permissionMode) {}
