package com.huawei.ascend.edp.rail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.edp.config.EdpConfig;
import com.huawei.ascend.edp.config.ScriptConstants;
import com.huawei.ascend.edp.config.ScriptResolver;
import com.huawei.ascend.edp.config.SysScriptsConfig;
import com.huawei.ascend.edp.config.ToolConstants;
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
    private static final String TOOL_ASK_USER = ToolConstants.ASK_USER;
    private static final String DEFAULT_INTERRUPT_ID = "ask_user_interrupt";

    /**
     * EDP 专有配置，当前用于读取 utterances.config_path。
     */
    private final EdpConfig edpConfig;

    /**
     * 话术配置，用于在 beforeToolCall 中预解析 ask_user 话术，
     * 使 InterruptRequest.message 与 interrupt_start.content 一致。
     */
    private final SysScriptsConfig scripts;

    /**
     * 构造 ask_user 话术增强 Rail。
     *
     * @param edpConfig EDP 专有配置，提供话术配置路径
     * @param scripts  话术配置，用于预解析 ask_user 话术
     */
    public AskUserTemplateRail(EdpConfig edpConfig, SysScriptsConfig scripts) {
        this.edpConfig = edpConfig;
        this.scripts = scripts;
        setPriority(85);
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
            ctx.getExtra().put(ScriptConstants.KEY_SKIP_TOOL, Boolean.TRUE);
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

        // 预解析话术：使 InterruptRequest.message 与 interrupt_start.content 一致，
        // 避免框架 statusUpdate 携带 LLM 原始 question（与 conversation_end 时序冲突）。
        String interruptMessage = question;
        if (scripts != null) {
            ScriptResolver.resolveAskUser(scripts, args, ctx.getExtra());
            Object rt = ctx.getExtra().get(ScriptConstants.KEY_RESPONSE_TEMPLATE);
            if (rt != null && !String.valueOf(rt).isBlank()) {
                interruptMessage = String.valueOf(rt);
            } else {
                // 兜底：无业务话术模板时，保留 LLM 原始 question，
                // 用 interrupt_start 前缀提示用户"这是系统需要你补充信息"
                interruptMessage = ScriptResolver.interruptStart(scripts) + "：" + question;
            }
        }
        LOGGER.info("AskUserTemplateRail: interrupting ask_user tool call, toolCallId={}, message='{}'",
                toolCallId, interruptMessage);

        InterruptRequest request = InterruptRequest.builder()
                .interruptId(toolCallId)
                .message(interruptMessage)
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
        // 话术由 ScriptsRail（B 面）解析 response_template_* → _edp_response_template，
        // EdpaEventRail.onToolException 读之填 interrupt_start.content；此处仅返回占位。
        return "";
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ask_user tool result", e);
        }
    }
}
