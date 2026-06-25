/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector.mcp;

import com.openjiuwen.core.foundation.tool.Tool;
import java.util.List;

public final class McpToolRegistry {

    private McpToolRegistry() {
    }

    public static List<Tool> mockTools() {
        return List.of(
                new TravelCalendarSearchTool(),
                new TravelTodoSearchTool(),
                new TravelPolicyLookupTool());
    }
}
