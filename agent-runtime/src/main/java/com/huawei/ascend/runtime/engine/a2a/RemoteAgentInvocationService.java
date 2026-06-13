package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class RemoteAgentInvocationService {
    private final OutboundPort outboundPort;

    public RemoteAgentInvocationService(OutboundPort outboundPort) {
        this.outboundPort = Objects.requireNonNull(outboundPort, "outboundPort");
    }

    public List<RemoteAgentResult> invoke(AgentExecutionResult.RemoteInvocation invocation,
            Consumer<RemoteAgentResult> eventConsumer) {
        return outboundPort.invoke(RemoteAgentRequest.from(invocation), eventConsumer);
    }

    public List<RemoteAgentResult> resumeRemoteInput(RemoteRoute route, String userInput,
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
                Map.of()), eventConsumer);
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

        static RemoteAgentRequest from(AgentExecutionResult.RemoteInvocation invocation) {
            Map<String, Object> args = new java.util.LinkedHashMap<>(invocation.arguments());
            // "message" is an internal convention: the interrupt rail injects
            // the user's original text so the outbound A2A request has a
            // non-empty TextPart. Strip it from the metadata before it reaches
            // the child agent — it is not an A2A standard field.
            Object message = args.remove("message");
            return new RemoteAgentRequest(
                    invocation.remoteAgentId(),
                    null,
                    null,
                    invocation.toolCallId(),
                    invocation.parentTaskId(),
                    invocation.parentContextId(),
                    invocation.localConversationId(),
                    message == null ? "" : String.valueOf(message),
                    args);
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
