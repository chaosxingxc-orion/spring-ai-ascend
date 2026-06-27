package com.huawei.ascend.examples.workmate.automation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAutomationJobRequest(
        @NotBlank @Size(max = 256) String name,
        @Size(max = 128) String expertId,
        @NotBlank String promptText,
        @Size(max = 128) String cronExpression,
        Boolean enabled) {}
