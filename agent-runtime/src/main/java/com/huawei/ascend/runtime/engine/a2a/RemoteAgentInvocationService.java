package com.huawei.ascend.runtime.engine.a2a;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class RemoteAgentInvocationService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OutboundPort outboundPort;

    public RemoteAgentInvocationService(OutboundPort outboundPort) {
        this.outboundPort = Objects.requireNonNull(outboundPort, "outboundPort");
    }

    public List<RemoteAgentResult> invoke(AgentExecutionResult.RemoteInvocation invocation,
            Map<String, Object> requestMetadata,
            Consumer<RemoteAgentResult> eventConsumer) {
        return outboundPort.invoke(RemoteAgentRequest.from(invocation, requestMetadata), eventConsumer);
    }

    public List<RemoteAgentResult> resumeRemoteInput(RemoteRoute route, String userInput,
            Map<String, Object> requestMetadata,
            Consumer<RemoteAgentResult> eventConsumer) {
        return outboundPort.invoke(new RemoteAgentRequest(
                route.remoteAgentId(),
                route.remoteTaskId(),
                route.remoteContextId(),
                route.toolCallId(),
                route.parentTaskId(),
                route.parentContextId(),
                route.localConversationId(),
                userInput == null ? "" : userInput,
                requestMetadata != null ? requestMetadata : Map.of()), eventConsumer);
    }

    public void cancel(RemoteTaskReference reference) {
        if (reference != null) {
            outboundPort.cancel(reference);
        }
    }

    public interface OutboundPort {
        List<RemoteAgentResult> invoke(RemoteAgentRequest request, Consumer<RemoteAgentResult> eventConsumer);

        void cancel(RemoteTaskReference reference);
    }

    public record RemoteAgentRequest(
            String remoteAgentId,
            String remoteTaskId,
            String remoteContextId,
            String toolCallId,
            String parentTaskId,
            String parentContextId,
            String localConversationId,
            String message,
            Map<String, Object> arguments) {
        public RemoteAgentRequest {
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        }

        /**
         * Builds the outbound request from the interrupt rail's remote invocation.
         * The LLM's tool call arguments (as stored by the interrupt rail) are
         * serialized to JSON as the A2A message text — the child adapter
         * (e.g. {@code VersatileMessageAdapter}) parses this text to reconstruct
         * the body. The {@code requestMetadata} is the original A2A request
         * metadata (headers, query params, etc.) forwarded transparently by the
         * A2A layer.
         */
        static RemoteAgentRequest from(AgentExecutionResult.RemoteInvocation invocation,
                Map<String, Object> requestMetadata) {
            String message = toJson(invocation.arguments());
            return new RemoteAgentRequest(
                    invocation.remoteAgentId(),
                    null,
                    null,
                    invocation.toolCallId(),
                    invocation.parentTaskId(),
                    invocation.parentContextId(),
                    invocation.localConversationId(),
                    message,
                    requestMetadata != null ? requestMetadata : Map.of());
        }

        private static String toJson(Map<String, Object> map) {
            if (map == null || map.isEmpty()) {
                return "";
            }
            try {
                return OBJECT_MAPPER.writeValueAsString(map);
            } catch (JsonProcessingException e) {
                return "";
            }
        }
    }

    public record RemoteTaskReference(String remoteAgentId, String remoteTaskId, String remoteContextId) {
    }

    public record RemoteRoute(
            String remoteAgentId,
            String remoteTaskId,
            String remoteContextId,
            String toolCallId,
            String parentTaskId,
            String parentContextId,
            String localConversationId) {
    }

    public record RemoteAgentResult(
            Type type,
            String text,
            String remoteTaskId,
            String remoteContextId,
            Map<String, Object> metadata,
            AgentExecutionResult.Target target) {
        public enum Type { MESSAGE, ARTIFACT, INPUT_REQUIRED, COMPLETED, FAILED }

        public RemoteAgentResult {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            target = target == null ? AgentExecutionResult.Target.BOTH : target;
        }

        public RemoteAgentResult(Type type, String text, String remoteTaskId,
                String remoteContextId, Map<String, Object> metadata) {
            this(type, text, remoteTaskId, remoteContextId, metadata, AgentExecutionResult.Target.BOTH);
        }

        public static RemoteAgentResult failed(String text) {
            return new RemoteAgentResult(Type.FAILED, text, null, null, Map.of());
        }
    }
}
