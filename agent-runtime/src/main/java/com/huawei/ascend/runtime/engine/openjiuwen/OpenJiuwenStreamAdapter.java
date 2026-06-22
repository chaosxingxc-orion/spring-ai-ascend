package com.huawei.ascend.runtime.engine.openjiuwen;

import java.util.Map;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps openJiuwen streaming {@link OutputSchema} chunks to framework-neutral
 * agent results.
 */
public class OpenJiuwenStreamAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenJiuwenStreamAdapter.class);

    private static final String OPENJIUWEN_INTERACTION_TYPE = "__interaction__";

    static final String ERROR_CODE = "OPENJIUWEN_ERROR";

    private AgentExecutionResult mapPayload(Map<String, Object> result) {
        String type = result == null ? null : asString(result.get("result_type"));
        String output = result == null ? "" : asString(result.get("output"));
        LOGGER.info("openjiuwen result map resultType={} outputLength={} keys={}",
                type,
                output.length(),
                result == null ? "null" : result.keySet());
        if ("answer".equals(type)) {
            return AgentExecutionResult.completed(output);
        }
        return AgentExecutionResult.failed(ERROR_CODE, output);
    }

    public AgentExecutionResult map(OutputSchema chunk) {
        if (chunk == null) {
            return null;
        }
        Object payload = chunk.getPayload();
        String type = chunk.getType();
        if ("llm_output".equals(type)) {
            Object output = payload;
            if (payload instanceof Map<?, ?> map) {
                if (!map.containsKey("content")) {
                    return null;
                }
                output = map.get("content");
            }
            String text = asString(output);
            return text.isBlank() ? null : AgentExecutionResult.output(text);
        }
        if ("llm_usage".equals(type) || "llm_reasoning".equals(type) || "custom".equals(type)) {
            return null;
        }
        if (OPENJIUWEN_INTERACTION_TYPE.equals(type)) {
            return mapInteraction(payload);
        }
        if (payload instanceof Map<?, ?> map) {
            return mapPayload(normalizeMap(map));
        }
        if ("answer".equals(type)) {
            return AgentExecutionResult.completed(asString(payload));
        }
        return AgentExecutionResult.output(asString(payload));
    }

    private AgentExecutionResult mapInteraction(Object payload) {
        Object value = payload instanceof InteractionOutput interactionOutput
                ? interactionOutput.getValue()
                : payload;
        if (value instanceof InterruptRequest request && isRemoteInvocation(request.getContext())) {
            return AgentExecutionResult.interrupted(remoteInvocation(request.getContext()));
        }
        if (value instanceof InterruptRequest request) {
            return AgentExecutionResult.interrupted(promptFrom(request));
        }
        if (value instanceof String prompt) {
            return AgentExecutionResult.interrupted(prompt);
        }
        return AgentExecutionResult.failed(ERROR_CODE, "Unsupported openjiuwen interaction payload: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private static String promptFrom(InterruptRequest request) {
        String message = request.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        String interruptId = request.getInterruptId();
        if (interruptId != null && !interruptId.isBlank()) {
            return interruptId;
        }
        return "Input required";
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> map) {
        java.util.LinkedHashMap<String, Object> normalized = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static AgentExecutionResult.RemoteInvocation remoteInvocation(Map<String, Object> result) {
        Object args = result.get("runtime.remote.arguments");
        Map<String, Object> arguments = args instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        return new AgentExecutionResult.RemoteInvocation(
                asString(result.get("runtime.remote.agentId")),
                asString(result.get("runtime.remote.toolName")),
                asString(result.get("runtime.remote.toolCallId")),
                asString(result.get("runtime.remote.parentTaskId")),
                asString(result.get("runtime.remote.parentContextId")),
                asString(result.get("runtime.remote.localConversationId")),
                arguments);
    }

    private static boolean isRemoteInvocation(Map<String, Object> result) {
        return result != null && "REMOTE_AGENT_INVOCATION".equals(asString(result.get("runtime.remote.kind")));
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
