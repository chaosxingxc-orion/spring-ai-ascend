package com.huawei.ascend.edp.tools;

import com.openjiuwen.core.foundation.tool.Tool;

import java.util.List;
import java.util.Map;

/**
 * EDPAgent MCP 调用工具。
 */
public final class CallMcpTool {

    private CallMcpTool() {
    }

    public static Tool build() {
        return EdpaBusinessTools.localTool(EdpaBusinessTools.TOOL_CALL_MCP,
                "声明 MCP 沙箱脚本调用意图，执行和数据通道写入由 McpInterruptRail 负责。",
                EdpaBusinessTools.objectSchema(Map.of(
                        "script_command", EdpaBusinessTools.stringProp("待执行的 MCP 脚本或命令"),
                        "script_params", EdpaBusinessTools.objectProp("脚本入参")),
                        List.of("script_command")),
                inputs -> Map.of("tool", EdpaBusinessTools.TOOL_CALL_MCP, "status", "mcp_intent", "input", inputs));
    }
}
