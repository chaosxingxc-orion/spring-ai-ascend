package com.huawei.ascend.runtime.engine.openjiuwen;


import java.util.Map;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps openJiuwen's {@code Runner.runAgent} result map to a framework-neutral
 * agent result, per the execution contract in design §10.4:
 * {@code result_type ∈ {answer, error, interrupt}} →
 * completed / failed / interrupted.
 */
public class OpenJiuwenStreamAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenJiuwenStreamAdapter.class);

    static final String ERROR_CODE = "OPENJIUWEN_ERROR";

    public AgentExecutionResult map(Map<String, Object> result) {
        String type = result == null ? null : asString(result.get("result_type"));
        String output = result == null ? "" : asString(result.get("output"));
        LOGGER.info("openjiuwen result map resultType={} outputLength={} keys={}",
                type,
                output.length(),
                result == null ? "null" : result.keySet());
        if ("answer".equals(type)) {
            return AgentExecutionResult.completed(output);
        }
        if ("interrupt".equals(type)) {
            Map<String, Object> remoteContext = remoteContext(result);
            if (isRemoteInvocation(remoteContext)) {
                return AgentExecutionResult.interrupted(remoteInvocation(remoteContext));
            }
            return AgentExecutionResult.interrupted(output);
        }
        return AgentExecutionResult.failed(ERROR_CODE, output);
    }

    private static Map<String, Object> remoteContext(Map<String, Object> result) {
        if (isRemoteInvocation(result)) {
            return result;
        }
        Object state = result == null ? null : result.get("state");
        if (!(state instanceof List<?> states)) {
            return Map.of();
        }
        for (Object item : states) {
            Object payload = item instanceof OutputSchema outputSchema ? outputSchema.getPayload() : item;
            Object value = payload instanceof InteractionOutput interactionOutput ? interactionOutput.getValue() : payload;
            if (value instanceof InterruptRequest request && isRemoteInvocation(request.getContext())) {
                return request.getContext();
            }
        }
        return Map.of();
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
