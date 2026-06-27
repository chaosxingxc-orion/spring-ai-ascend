package com.huawei.ascend.examples.workmate.session.dto;

import com.huawei.ascend.examples.workmate.session.PermissionMode;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateSessionRequest(
        @Size(max = 512) String title,
        @Size(max = 256) String workspacePath,
        @Size(max = 128) String expertId,
        PermissionMode permissionMode,
        @Size(max = 128) String modelId,
        @Size(max = 32) String effort,
        Boolean autoArchive,
        @Size(max = 128) String gitBranch,
        List<@Size(max = 128) String> enabledConnectorIds,
        List<@Size(max = 128) String> enabledSkillIds) {
}
