package com.huawei.ascend.examples.workmate.session.dto;

import com.huawei.ascend.examples.workmate.session.PermissionMode;
import java.util.List;

/** F4 pin/archive + W34 G6 model/effort + session-scoped MCP connectors + permission mode + expert switch. */
public record SessionMetadataRequest(
        Boolean pinned,
        Boolean archived,
        String modelId,
        String effort,
        List<String> enabledConnectorIds,
        List<String> enabledSkillIds,
        PermissionMode permissionMode,
        String expertId) {}
