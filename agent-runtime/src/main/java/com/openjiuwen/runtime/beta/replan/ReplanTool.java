package com.openjiuwen.runtime.beta.replan;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * replan 虚拟工具——LLM 调用它表达"放弃当前路径换新策略"的 Replan 意图。
 *
 * <p>1.0 ReActAgent 只有 tool_call/answer 语义，无 2.0 的 LLMDecision.Replan。用此虚拟工具
 * 把 Replan 意图承载到 1.0 原生 tool_call 通道：LLM 调 {@code __replan__} = 表达 Replan。
 * {@link ReplanRail}（afterModelCall）拦截此工具调用，做计数/超限 escalate。
 *
 * <p>invoke 不真执行换路径（返固定确认），实际由 ReplanRail 拦截——范式同
 * {@code OpenJiuwenRemoteToolInstaller.PlaceholderRemoteAgentTool}（虚拟工具被 rail 拦截）。
 */
public class ReplanTool extends Tool {

    public static final String TOOL_NAME = "__replan__";
    public static final String ARG_REPLAN_REASON = "replan_reason";
    public static final String ARG_NEW_APPROACH = "new_approach";

    public ReplanTool() {
        super(ToolCard.builder()
                .id(TOOL_NAME)
                .name(TOOL_NAME)
                .description("重规划信号工具：放弃当前执行路径换新策略。调用此工具表达 Replan 意图，"
                        + "系统记录并在未超 maxReplanCount 时让你换策略重试。")
                .inputParams(replanInputSchema())
                .build());
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return Map.of(
                "status", "replan_recorded",
                "message", "Replan 意图已记录；系统将评估是否允许换策略（受 maxReplanCount 限制）。");
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return List.of(invoke(inputs, kwargs)).iterator();
    }

    private static Map<String, Object> replanInputSchema() {
        Map<String, Object> reasonField = new LinkedHashMap<>();
        reasonField.put("type", "string");
        reasonField.put("description", "当前路径走不通的原因");
        Map<String, Object> approachField = new LinkedHashMap<>();
        approachField.put("type", "string");
        approachField.put("description", "新策略描述");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(ARG_REPLAN_REASON, reasonField);
        properties.put(ARG_NEW_APPROACH, approachField);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(ARG_REPLAN_REASON, ARG_NEW_APPROACH));
        return schema;
    }
}
