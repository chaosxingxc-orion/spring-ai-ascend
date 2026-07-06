package com.huawei.ascend.edp.tools;

import com.openjiuwen.core.foundation.tool.Tool;

import java.util.List;
import java.util.Map;

/**
 * EDPAgent Versatile Agent 委托工具。
 */
public final class CallVersatileTool {

    private CallVersatileTool() {
    }

    public static Tool build() {
        return EdpaBusinessTools.localTool(EdpaBusinessTools.TOOL_CALL_VERSATILE,
                "声明 Versatile Agent 委托意图，级联恢复和结果归一化由 VersatileInterruptRail 负责。",
                EdpaBusinessTools.objectSchema(Map.of(
                        "query_description", EdpaBusinessTools.stringProp("委托查询描述"),
                        "query_intent", EdpaBusinessTools.stringProp("委托查询意图"),
                        "query_response_analysis_scripts", EdpaBusinessTools.arrayProp("响应归一化脚本列表"),
                        "response_template_keys", EdpaBusinessTools.arrayProp("响应话术模板 key 列表"),
                        "notice_context", EdpaBusinessTools.objectProp("非中断话术上下文"),
                        "input_key", EdpaBusinessTools.stringProp("从 ToolDataChannel 读取前序数据的 key")),
                        List.of("query_description", "query_intent")),
                inputs -> Map.of("tool", EdpaBusinessTools.TOOL_CALL_VERSATILE,
                        "status", "delegate_intent", "input", inputs));
    }
}
