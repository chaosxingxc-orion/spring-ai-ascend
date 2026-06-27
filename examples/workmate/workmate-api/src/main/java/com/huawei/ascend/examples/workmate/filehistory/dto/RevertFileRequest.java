package com.huawei.ascend.examples.workmate.filehistory.dto;

import jakarta.validation.constraints.NotBlank;

public record RevertFileRequest(@NotBlank String path, @NotBlank String versionId) {
}
