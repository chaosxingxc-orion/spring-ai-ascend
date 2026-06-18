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
        // The LLM's remoteInput tool argument is the canonical outbound A2A
        // message text. The A2A layer forwards request metadata separately.
        context.put("runtime.remote.arguments", arguments(toolCall));
        return interrupt(InterruptRequest.builder()
                .message("Remote agent invocation requested: " + spec.toolName())
                .context(context)
                .build());
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
            return Map.of("remoteInput", rawArguments);
        }
    }
}
