package com.huawei.ascend.edp.rail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.edp.config.EdpConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ask_user 工具话术增强 Rail。
 *
 * <p>参考 Python EDPA AskUserRail：等待模型真实发起 ask_user tool_call 后拦截工具调用，
 * 首次调用转为 OpenJiuwen tool interrupt；恢复调用时把用户输入作为 tool_result 返回给模型。</p>
 */
public class AskUserTemplateRail extends AgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(AskUserTemplateRail.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TOOL_ASK_USER = "ask_user";
    private static final String DEFAULT_INTERRUPT_ID = "ask_user_interrupt";

    /**
     * EDP 专有配置，当前用于读取 utterances.config_path。
     */
    private final EdpConfig edpConfig;

    /**
     * 构造 ask_user 话术增强 Rail。
     *
     * @param edpConfig EDP 专有配置，提供话术配置路径
     */
    public AskUserTemplateRail(EdpConfig edpConfig) {
        this.edpConfig = edpConfig;
        setPriority(50);
    }

    /**
     * 工具调用前回调。
     *
     * <p>作用：仅处理 ask_user 工具；其他工具直接放行。</p>
     *
     * @param ctx OpenJiuwen 回调上下文，包含工具名、工具入参、会话等信息
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        if (!TOOL_ASK_USER.equals(inputs.getToolName())) {
            return;
        }

        String toolCallId = inputs.getToolCall() != null && inputs.getToolCall().getId() != null
                && !inputs.getToolCall().getId().isBlank()
                ? inputs.getToolCall().getId() : DEFAULT_INTERRUPT_ID;
        Object resumeInput = resolveResumeInput(ctx, toolCallId);
        if (resumeInput != null) {
            LOGGER.info("AskUserTemplateRail: resuming ask_user with user input");
            ctx.getExtra().put("_skip_tool", Boolean.TRUE);
            Map<String, Object> toolResult = new LinkedHashMap<>();
            toolResult.put("tool", TOOL_ASK_USER);
            toolResult.put("status", "user_responded");
            toolResult.put("user_response", resumeInput);
            inputs.setToolResult(toolResult);
            inputs.setToolMsg(ToolMessage.builder()
                    .content(toJson(toolResult))
                    .toolCallId(toolCallId)
                    .build());
            return;
        }

        Map<String, Object> args = normalizeArgs(inputs.getToolArgs());
        String question = question(args);
        args.putIfAbsent("question", question);
        inputs.setToolArgs(args);
        LOGGER.info("AskUserTemplateRail: interrupting ask_user tool call, toolCallId={}, question='{}'",
                toolCallId, question);

        InterruptRequest request = InterruptRequest.builder()
                .interruptId(toolCallId)
                .message(question)
                .context(Map.of("tool", TOOL_ASK_USER, "inputs", args))
                .payloadSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("answer", Map.of("type", "string", "description", "用户补充信息")),
                        "required", List.of("answer")))
                .build();
        throw new ToolInterruptException(request, inputs.getToolCall());
    }

    private Object resolveResumeInput(AgentCallbackContext ctx, String toolCallId) {
        Object rawInput = ctx.getExtra().get(ToolInterruptionState.RESUME_USER_INPUT_KEY);
        if (rawInput instanceof InteractiveInput interactiveInput) {
            Map<String, Object> userInputs = interactiveInput.getUserInputs();
            if (toolCallId != null && !toolCallId.isBlank() && userInputs.containsKey(toolCallId)) {
                return userInputs.get(toolCallId);
            }
            return interactiveInput.getRawInputs();
        }
        if (rawInput instanceof Map<?, ?> map && toolCallId != null && !toolCallId.isBlank() && map.containsKey(toolCallId)) {
            return map.get(toolCallId);
        }
        return rawInput;
    }

    private Map<String, Object> normalizeArgs(Object rawArgs) {
        if (rawArgs instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (rawArgs instanceof String text && !text.isBlank()) {
            try {
                return OBJECT_MAPPER.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() { });
            } catch (Exception e) {
                LOGGER.warn("AskUserTemplateRail: failed to parse ask_user args as JSON, args={}", text);
            }
        }
        return new LinkedHashMap<>();
    }

    private String question(Map<String, Object> args) {
        Object question = args.get("question");
        if (question != null && !String.valueOf(question).isBlank()) {
            return String.valueOf(question);
        }
        return resolveUtterance();
    }

    /**
     * 解析 ask_user 默认追问话术。
     *
     * <p>当前 spike 阶段只记录 ScriptsConfig.md 路径，并返回固定话术；后续可扩展为真实模板解析。</p>
     *
     * @return ask_user 默认追问话术
     */
    private String resolveUtterance() {
        if (edpConfig != null && edpConfig.getUtterances() != null) {
            String configPath = edpConfig.getUtterances().getConfigPath();
            if (configPath != null) {
                LOGGER.info("AskUserTemplateRail: loading utterances from {}", configPath);
            }
        }
        return "需要您确认以下信息";
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ask_user tool result", e);
        }
    }
}
