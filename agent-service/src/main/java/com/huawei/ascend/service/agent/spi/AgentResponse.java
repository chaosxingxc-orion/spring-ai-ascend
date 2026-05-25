package com.huawei.ascend.service.agent.spi;

import com.huawei.ascend.middleware.model.spi.ModelResponse;
import com.huawei.ascend.middleware.model.spi.ModelUsage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Synchronous {@link Agent#invoke(AgentInvocation)} response.
 *
 * <p>Authority: ADR-0128.
 *
 * @param assistantMessage agent's final message; never null.
 * @param toolCalls        tool calls emitted during the run (may
 *                         be empty if the agent answered directly).
 * @param usage            aggregate token usage from the underlying
 *                         model invocations; nullable when
 *                         unreported.
 * @param traceId          correlation id for the invocation span.
 * @param runId            optional reference to a long-running
 *                         {@code Run} when the agent escalated to
 *                         the orchestrator.
 */
public record AgentResponse(
        String assistantMessage,
        List<ModelResponse.ToolCall> toolCalls,
        ModelUsage usage,
        String traceId,
        Optional<String> runId) {

    public AgentResponse {
        Objects.requireNonNull(assistantMessage, "assistantMessage");
        Objects.requireNonNull(toolCalls, "toolCalls");
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(runId, "runId");
        toolCalls = List.copyOf(toolCalls);
    }
}
