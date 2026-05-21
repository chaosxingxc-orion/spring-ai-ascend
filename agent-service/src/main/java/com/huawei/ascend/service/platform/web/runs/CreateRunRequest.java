package com.huawei.ascend.service.platform.web.runs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /v1/runs} (plan §6.5). Only {@code capabilityName} is
 * required at L1; future waves add tool config, budget envelopes, etc.
 */
public record CreateRunRequest(
        @NotBlank @Size(max = 128) String capabilityName
) {
}
