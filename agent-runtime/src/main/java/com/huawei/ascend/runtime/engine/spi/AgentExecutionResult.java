package com.huawei.ascend.runtime.engine.spi;

import java.util.Map;
import java.util.Objects;

public final class AgentExecutionResult {

    public enum Type { OUTPUT, COMPLETED, FAILED, INTERRUPTED }

    private final Type type;
    private final String outputContent;
    private final String errorCode;
    private final String errorMessage;
    private final String prompt;
    private final RemoteInvocation remoteInvocation;
    private final InterruptPayload interruptPayload;

    private AgentExecutionResult(Type type, String outputContent, String errorCode,
                                  String errorMessage, String prompt, RemoteInvocation remoteInvocation,
                                  InterruptPayload interruptPayload) {
        this.type = type;
        this.outputContent = outputContent;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.prompt = prompt;
        this.remoteInvocation = remoteInvocation;
        this.interruptPayload = interruptPayload;
    }

    public static AgentExecutionResult output(String content) {
        return new AgentExecutionResult(Type.OUTPUT, content, null, null, null, null, null);
    }

    public static AgentExecutionResult completed(String content) {
        return new AgentExecutionResult(Type.COMPLETED, content, null, null, null, null, null);
    }

    public static AgentExecutionResult failed(String errorCode, String errorMessage) {
        return new AgentExecutionResult(Type.FAILED, null, errorCode, errorMessage, null, null, null);
    }

    public static AgentExecutionResult interrupted(String prompt) {
        return new AgentExecutionResult(
                Type.INTERRUPTED, null, null, null, prompt, null, new UserInputInterrupt(prompt));
    }

    public static AgentExecutionResult interrupted(RemoteInvocation remoteInvocation) {
        RemoteInvocation required = Objects.requireNonNull(remoteInvocation, "remoteInvocation");
        return new AgentExecutionResult(
                Type.INTERRUPTED, null, null, null, null, required, new RemoteAgentInterrupt(required));
    }

    public Type type() { return type; }
    public String outputContent() { return outputContent; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public String prompt() { return prompt; }
    public RemoteInvocation remoteInvocation() { return remoteInvocation; }
    public InterruptPayload interruptPayload() { return interruptPayload; }

    public sealed interface InterruptPayload permits UserInputInterrupt, RemoteAgentInterrupt {
    }

    public record UserInputInterrupt(String prompt) implements InterruptPayload {
    }

    public record RemoteAgentInterrupt(RemoteInvocation remoteInvocation) implements InterruptPayload {
        public RemoteAgentInterrupt {
            Objects.requireNonNull(remoteInvocation, "remoteInvocation");
        }
    }

    public record RemoteInvocation(
            String remoteAgentId,
            String toolName,
            String toolCallId,
            String parentTaskId,
            String parentContextId,
            String localConversationId,
            Map<String, Object> arguments) {
        public RemoteInvocation {
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        }
    }
}
