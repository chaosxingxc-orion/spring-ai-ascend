package com.huawei.ascend.examples.workmate.prompt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserAttachmentItem(
        @NotBlank @Size(max = 512) String path,
        @Size(max = 256) String name,
        @Size(max = 128) String mime) {}
