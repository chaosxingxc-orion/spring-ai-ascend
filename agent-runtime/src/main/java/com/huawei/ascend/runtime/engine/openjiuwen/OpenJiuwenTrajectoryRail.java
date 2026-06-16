package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.spi.TrajectoryDraft;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.ErrorCategory;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * openJiuwen-local rail that taps the framework's native model/tool callbacks and
 * normalizes them into framework-neutral {@link TrajectoryDraft}s pushed northbound
 * through a {@link TrajectoryEmitter}. The {@code RUN_START}/{@code RUN_END} lifecycle
 * is owned by {@link com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler},
 * so this rail only maps the inner steps. Every hook is defensive: a trajectory
 * failure must never break the agent run.
 *
 * <p>Other frameworks must use their own native callback mechanism — this rail
 * deliberately depends on openJiuwen's {@code AgentRail} API.
 */
final class OpenJiuwenTrajectoryRail extends AgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenJiuwenTrajectoryRail.class);

    private static final String MODEL_ERROR_CODE = "OPENJIUWEN_MODEL_ERROR";
    private static final String TOOL_ERROR_CODE = "OPENJIUWEN_TOOL_ERROR";

    private final TrajectoryEmitter trajectory;

    OpenJiuwenTrajectoryRail(TrajectoryEmitter trajectory) {
        this.trajectory = trajectory;
    }

    @Override
    public void beforeModelCall(AgentCallbackContext context) {
        safe(() -> {
            // Keep the payload scalar — counts, not the raw framework message/tool objects —
            // so northbound serialization never depends on framework internals.
            Map<String, Object> args = new LinkedHashMap<>();
            if (context.getInputs() instanceof ModelCallInputs inputs) {
                args.put("messages", inputs.getMessages() != null ? inputs.getMessages().size() : 0);
                args.put("tools", inputs.getTools() != null ? inputs.getTools().size() : 0);
            }
            trajectory.emit(TrajectoryDraft.modelCallStart(args));
        });
    }

    @Override
    public void afterModelCall(AgentCallbackContext context) {
        safe(() -> {
            TrajectoryEvent.Usage usage = null;
            String finishReason = null;
            String reasoning = null;
            if (context.getInputs() instanceof ModelCallInputs inputs
                    && inputs.getResponse() instanceof AssistantMessage message) {
                finishReason = message.getFinishReason();
                reasoning = message.getReasoningContent();
                usage = toUsage(message.getUsageMetadata());
            }
            trajectory.emit(TrajectoryDraft.modelCallEnd(usage, finishReason, reasoning));
        });
    }

    @Override
    public void onModelException(AgentCallbackContext context) {
        safe(() -> trajectory.emit(TrajectoryDraft.error(null, MODEL_ERROR_CODE,
                OpenJiuwenAgentRuntimeHandler.errorMessage(context.getException()),
                ErrorCategory.classify(context.getException()),
                context.getRetryAttempt(), true)));
    }

    @Override
    public void beforeToolCall(AgentCallbackContext context) {
        safe(() -> {
            if (context.getInputs() instanceof ToolCallInputs inputs) {
                trajectory.emit(TrajectoryDraft.toolCallStart(inputs.getToolName(), neutralize(inputs.getToolArgs())));
            }
        });
    }

    @Override
    public void afterToolCall(AgentCallbackContext context) {
        safe(() -> {
            if (context.getInputs() instanceof ToolCallInputs inputs) {
                trajectory.emit(TrajectoryDraft.toolCallEnd(inputs.getToolName(), neutralize(inputs.getToolResult())));
            }
        });
    }

    /**
     * Normalizes a native framework payload for northbound emission. Structured
     * {@code Map}/{@code List}/scalar payloads pass through unchanged so the runtime's key-based
     * masking can redact secrets inside them; only opaque framework objects are stringified,
     * keeping serialization framework-agnostic without defeating masking. Pre-flattening a Map to a
     * String here would hide secret-named keys from the masker.
     */
    private static Object neutralize(Object value) {
        if (value == null || value instanceof Map || value instanceof List
                || value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    @Override
    public void onToolException(AgentCallbackContext context) {
        safe(() -> {
            String toolName = context.getInputs() instanceof ToolCallInputs inputs ? inputs.getToolName() : null;
            trajectory.emit(TrajectoryDraft.error(toolName, TOOL_ERROR_CODE,
                    OpenJiuwenAgentRuntimeHandler.errorMessage(context.getException()),
                    ErrorCategory.classify(context.getException()),
                    context.getRetryAttempt(), true));
        });
    }

    private static TrajectoryEvent.Usage toUsage(UsageMetadata usage) {
        if (usage == null) {
            return null;
        }
        // openJiuwen reports totalLatency in seconds; normalize to milliseconds.
        Double latencyMs = usage.getTotalLatency() > 0 ? usage.getTotalLatency() * 1000.0 : null;
        // provider/costMicros are filled off the hot path (cost is a price-table lookup, not
        // framework data); the rail stays synchronous and cheap.
        return new TrajectoryEvent.Usage(
                usage.getInputTokens(), usage.getOutputTokens(), latencyMs, usage.getModelName(), null, null);
    }

    private static void safe(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOGGER.warn("openjiuwen trajectory rail hook failed errorClass={} message={}",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
