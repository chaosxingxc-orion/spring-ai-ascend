package com.huawei.ascend.examples.workmate.myfiles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MyFilePathRequest(@NotNull UUID sessionId, @NotBlank String path) {}
