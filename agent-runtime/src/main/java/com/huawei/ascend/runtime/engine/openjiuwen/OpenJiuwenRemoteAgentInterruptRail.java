package com.huawei.ascend.runtime.engine.openjiuwen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.RemoteAgentCardCache;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
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
    private final Map<String, RemoteAgentCardCache.RemoteAgentToolSpec> specsByToolName;

    public OpenJiuwenRemoteAgentInterruptRail(AgentExecutionContext executionContext,
            List<RemoteAgentCardCache.RemoteAgentToolSpec> toolSpecs) {
        super(toolNames(toolSpecs));
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext");
        this.specsByToolName = (toolSpecs == null ? List.<RemoteAgentCardCache.RemoteAgentToolSpec>of() : toolSpecs)
                .stream()
                .collect(Collectors.toMap(
                        RemoteAgentCardCache.RemoteAgentToolSpec::toolName,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    @Override
    protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
        String toolName = toolCall == null ? "" : toolCall.getName();
        RemoteAgentCardCache.RemoteAgentToolSpec spec = specsByToolName.get(toolName);
        if (spec == null) {
            return approve();
        }
        if (userInput != null) {
            return reject(userInput);
        }
        String toolCallId = toolCall != null && toolCall.getId() != null ? toolCall.getId() : toolName;
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("runtime.remote.kind", REMOTE_KIND);
        context.put("runtime.remote.agentId", spec.remoteAgentId());
        context.put("runtime.remote.toolName", spec.toolName());
        context.put("runtime.remote.toolCallId", toolCallId);
        context.put("runtime.remote.parentTaskId", executionContext.getScope().taskId());
        context.put("runtime.remote.parentContextId", executionContext.getScope().sessionId());
        context.put("runtime.remote.localConversationId", executionContext.getAgentStateKey());
        context.put("runtime.remote.arguments", arguments(toolCall));
        return interrupt(InterruptRequest.builder()
                .interruptId(toolCallId)
                .message("Remote agent invocation requested: " + spec.toolName())
                .context(context)
                .build());
    }

    private static List<String> toolNames(List<RemoteAgentCardCache.RemoteAgentToolSpec> toolSpecs) {
        return (toolSpecs == null ? List.<RemoteAgentCardCache.RemoteAgentToolSpec>of() : toolSpecs)
                .stream()
                .map(RemoteAgentCardCache.RemoteAgentToolSpec::toolName)
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
