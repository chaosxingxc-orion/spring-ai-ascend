package com.huawei.ascend.edp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;

import java.util.List;
import java.util.Map;

/**
 * EDPAgent 增强 ask_user 工具。
 */
public final class EnhancedAskUserTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EnhancedAskUserTool() {
    }

    public static Tool build() {
        return EdpaBusinessTools.localTool(EdpaBusinessTools.TOOL_ENHANCED_ASK_USER,
                "向用户追问缺失信息，并支持话术模板参数。",
                EdpaBusinessTools.objectSchema(Map.of(
                        "question", EdpaBusinessTools.stringProp("需要向用户追问的问题"),
                        "response_template_keys", EdpaBusinessTools.arrayProp("响应话术模板 key 列表"),
                        "response_template_status", EdpaBusinessTools.stringProp("响应话术状态"),
                        "response_template_vars", EdpaBusinessTools.objectProp("响应话术变量"),
                        "missing_fields", EdpaBusinessTools.arrayProp("缺失字段列表")),
                        List.of("question")),
                EnhancedAskUserTool::interruptForUserInput);
    }

    private static Object interruptForUserInput(Map<String, Object> inputs) {
        String question = String.valueOf(inputs.getOrDefault("question", "需要您确认以下信息"));
        InterruptRequest request = InterruptRequest.builder()
                .interruptId("ask_user_interrupt")
                .message(question)
                .context(Map.of("tool", EdpaBusinessTools.TOOL_ENHANCED_ASK_USER, "inputs", inputs))
                .payloadSchema(EdpaBusinessTools.objectSchema(
                        Map.of("answer", EdpaBusinessTools.stringProp("用户补充信息")), List.of("answer")))
                .build();
        ToolCall toolCall = ToolCall.builder()
                .id("ask_user_interrupt")
                .name(EdpaBusinessTools.TOOL_ENHANCED_ASK_USER)
                .arguments(toJson(inputs))
                .build();
        throw new ToolInterruptException(request, toolCall);
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ask_user arguments", e);
        }
    }
}
