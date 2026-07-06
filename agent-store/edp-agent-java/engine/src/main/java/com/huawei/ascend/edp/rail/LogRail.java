package com.huawei.ascend.edp.rail;

import com.huawei.ascend.edp.config.EdpConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EDPAgent 观测日志 Rail。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>记录模型调用前的消息数量和最后一条消息。</li>
 *     <li>记录模型调用后的响应对象。</li>
 *     <li>记录模型 token 使用量，支撑端到端穿刺证据收集。</li>
 *     <li>记录工具调用完成事件。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #LogRail(EdpConfig)}：创建日志 Rail。</li>
 *     <li>{@link #beforeModelCall(AgentCallbackContext)}：模型调用前回调。</li>
 *     <li>{@link #afterModelCall(AgentCallbackContext)}：模型调用后回调。</li>
 *     <li>{@link #afterToolCall(AgentCallbackContext)}：工具调用后回调。</li>
 * </ul>
 */
public class LogRail extends AgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogRail.class);

    /**
     * EDP 专有配置，当前预留给后续日志脱敏、采样和开关控制使用。
     */
    private final EdpConfig edpConfig;

    /**
     * 构造日志 Rail。
     *
     * @param edpConfig EDP 专有配置
     */
    public LogRail(EdpConfig edpConfig) {
        this.edpConfig = edpConfig;
        // 日志 Rail 使用较低优先级，尽量在其他业务 Rail 完成处理后记录最终上下文。
        setPriority(10);
    }

    /**
     * 模型调用前回调。
     *
     * @param ctx OpenJiuwen 回调上下文，包含模型入参
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        // 关键判断：只有模型调用上下文才记录模型输入。
        if (ctx.getInputs() instanceof ModelCallInputs inputs) {
            Object lastMessage = inputs.getMessages().isEmpty() ? null : inputs.getMessages().get(inputs.getMessages().size() - 1);
            LOGGER.info("E2E_MODEL_INPUT messageCount={}, lastMessage={}", inputs.getMessages().size(), lastMessage);
        }
    }

    /**
     * 模型调用后回调。
     *
     * @param ctx OpenJiuwen 回调上下文，包含模型响应
     */
    @Override
    public void afterModelCall(AgentCallbackContext ctx) {
        // 关键判断：非模型调用上下文只记录完成事件，不读取模型响应。
        if (!(ctx.getInputs() instanceof ModelCallInputs inputs)) {
            LOGGER.info("LogRail: model call completed, model response received");
            return;
        }
        Object response = inputs.getResponse();
        LOGGER.info("E2E_MODEL_OUTPUT response={}", response);

        // 关键判断：只有 AssistantMessage 响应才可能携带 usage 元数据。
        if (response instanceof AssistantMessage assistantMessage) {
            UsageMetadata usage = assistantMessage.getUsageMetadata();
            if (usage != null) {
                LOGGER.info("E2E_MODEL_USAGE inputTokens={}, outputTokens={}, totalTokens={}, model={}",
                        usage.getInputTokens(), usage.getOutputTokens(), usage.getTotalTokens(), usage.getModelName());
            } else {
                LOGGER.info("E2E_MODEL_USAGE null");
            }
        }
    }

    /**
     * 工具调用后回调。
     *
     * @param ctx OpenJiuwen 回调上下文，包含工具调用信息
     */
    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        // 关键判断：只有工具调用上下文才记录工具名。
        if (ctx.getInputs() instanceof ToolCallInputs inputs) {
            LOGGER.info("LogRail: tool call completed, toolName={}", inputs.getToolName());
        }
    }
}
