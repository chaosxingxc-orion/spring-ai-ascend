package com.huawei.ascend.runtime.engine.openjiuwen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.RemoteAgentToolSpec;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.interrupt.BaseInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class OpenJiuwenRemoteAgentInterruptRail extends BaseInterruptRail {
    static final String REMOTE_KIND = "REMOTE_AGENT_INVOCATION";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentExecutionContext executionContext;
    private final Map<String, RemoteAgentToolSpec> specsByToolName;

    public OpenJiuwenRemoteAgentInterruptRail(AgentExecutionContext executionContext,
            List<RemoteAgentToolSpec> toolSpecs) {
        super(toolNames(toolSpecs));
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext");
        this.specsByToolName = (toolSpecs == null ? List.<RemoteAgentToolSpec>of() : toolSpecs)
                .stream()
                .collect(Collectors.toMap(
                        RemoteAgentToolSpec::toolName,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    @Override
    protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
        String toolName = toolCall == null ? "" : toolCall.getName();
        RemoteAgentToolSpec spec = specsByToolName.get(toolName);
        if (spec == null) {
            return approve();
        }
        String toolCallId = toolCall != null && toolCall.getId() != null ? toolCall.getId() : toolName;
        if (userInput != null) {
            return reject(resumeToolResult(toolCallId, userInput));
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("runtime.remote.kind", REMOTE_KIND);
        context.put("runtime.remote.agentId", spec.remoteAgentId());
        context.put("runtime.remote.toolName", spec.toolName());
        context.put("runtime.remote.toolCallId", toolCallId);
        context.put("runtime.remote.parentTaskId", executionContext.getScope().taskId());
        context.put("runtime.remote.parentContextId", executionContext.getScope().sessionId());
        context.put("runtime.remote.localConversationId", executionContext.getAgentStateKey());
        // Merge user metadata (from original A2A request) into tool call arguments
        // so the child agent receives biz fields (intent, wap_userName, etc.)
        // needed to reconstruct the target REST call. Tool call arguments
        // take priority over context variables on key collision.
        Map<String, Object> mergedArgs = mergeMetadata(executionContext, arguments(toolCall));
        // The LLM's tool call arguments are the canonical message for the
        // outbound A2A request — this works uniformly for both Versatile
        // ({"inputs":{...}}) and plain A2A agents ({"param":"val",...}).
        // RemoteAgentRequest.from() strips "message" from the metadata so
        // it only appears in the A2A message parts, not in child variables.
        String rawArgs = toolCall != null ? toolCall.getArguments() : null;
        if (rawArgs != null && !rawArgs.isBlank()) {
            mergedArgs.putIfAbsent("message", rawArgs);
        }
        context.put("runtime.remote.arguments", mergedArgs);
        return interrupt(InterruptRequest.builder()
                .message("Remote agent invocation requested: " + spec.toolName())
                .context(context)
                .build());
    }

    /**
     * Merge execution context variables (user metadata from the original A2A
     * request) with the LLM's tool call arguments. Context variables go first
     * (lower priority); tool call arguments override on key collision.
     *
     * <p>All context variables are forwarded. The child agent's adapter
     * (e.g. {@code VersatileMessageAdapter}) applies its own mapping rules
     * to place each field in the correct position (header, query param, or
     * body input). No filtering is done here — the child decides.
     *
     * <h3>Security rationale</h3>
     * The variables forwarded here originate from user-supplied A2A metadata
     * ({@code AgentExecutionContext.mergeVariables()}). Unlike internal state
     * which is never placed in the variables map, these are biz fields the
     * user explicitly chose to send. The child agent's adapter provides the
     * actual security boundary (e.g. {@code passthroughHeaders} allowlist for
     * HTTP headers, body input merging into a structured payload the remote
     * endpoint validates independently).
     *
     * <p>The merged map flows through {@code RemoteInvocation.arguments()} →
     * A2A message metadata → child's {@code AgentExecutionContext.variables}.
     */
    private static Map<String, Object> mergeMetadata(AgentExecutionContext executionContext,
            Map<String, Object> toolArgs) {
        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, Object> vars = executionContext.getVariables();
        if (vars != null) {
            merged.putAll(vars);
        }
        // Tool call arguments override context variables (higher priority)
        if (toolArgs != null) {
            merged.putAll(toolArgs);
        }
        return merged;
    }

    private static Object resumeToolResult(String toolCallId, Object userInput) {
        if (userInput instanceof InteractiveInput interactiveInput) {
            Object value = interactiveInput.getUserInputs().get(toolCallId);
            return value == null ? userInput : value;
        }
        return userInput;
    }

    private static List<String> toolNames(List<RemoteAgentToolSpec> toolSpecs) {
        return (toolSpecs == null ? List.<RemoteAgentToolSpec>of() : toolSpecs)
                .stream()
                .map(RemoteAgentToolSpec::toolName)
                .toList();
    }

    private static Map<String, Object> arguments(ToolCall toolCall) {
        String rawArguments = toolCall == null ? null : toolCall.getArguments();
        if (rawArguments == null || rawArguments.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(rawArguments, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of("message", rawArguments);
        }
    }
}
