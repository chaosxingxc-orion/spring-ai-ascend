package com.huawei.ascend.examples.workmate.prompt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnhancePromptRequest(
        @NotBlank @Size(max = 8000) String text,
        String expertId) {}
