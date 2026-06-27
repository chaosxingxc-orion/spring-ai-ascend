package com.huawei.ascend.examples.workmate.session.dto;

import com.huawei.ascend.examples.workmate.session.ExpertTransitionMode;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ExpertTransitionRequest(
        @Size(max = 128) String expertId,
        ExpertTransitionMode mode,
        List<@Size(max = 128) String> enabledConnectorIds) {}
