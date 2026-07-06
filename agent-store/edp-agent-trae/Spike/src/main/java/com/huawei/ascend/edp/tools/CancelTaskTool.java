package com.huawei.ascend.edp.tools;

import com.openjiuwen.core.foundation.tool.Tool;

import java.util.List;
import java.util.Map;

/**
 * EDPAgent 任务取消工具。
 */
public final class CancelTaskTool {

    private CancelTaskTool() {
    }

    public static Tool build() {
        return EdpaBusinessTools.localTool(EdpaBusinessTools.TOOL_CANCEL_TASK,
                "取消当前任务并触发取消清理话术。使用前必须先通过 ask_user 向用户确认取消意图，"
                        + "获得用户明确确认后再调用此工具。",
                EdpaBusinessTools.objectSchema(Map.of("reason", EdpaBusinessTools.stringProp("取消原因")), List.of()),
                inputs -> Map.of("tool", EdpaBusinessTools.TOOL_CANCEL_TASK,
                        "status", "cancelled", "input", inputs));
    }
}
