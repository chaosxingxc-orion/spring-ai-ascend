/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector.mcp;

import com.openjiuwen.core.foundation.tool.Tool;
import java.util.List;

public final class McpToolExecutor implements McpToolAdapter {

    @Override
    public List<Tool> tools() {
        return McpToolRegistry.mockTools();
    }
}
