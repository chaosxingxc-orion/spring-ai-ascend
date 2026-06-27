package com.huawei.ascend.examples.workmate.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Request bulk auto-archive: specify {@code count} or {@code targetActiveCount}, not both. */
public record AutoArchiveRequest(
        @Min(1) @Max(100) Integer count, @Min(0) Integer targetActiveCount) {}
