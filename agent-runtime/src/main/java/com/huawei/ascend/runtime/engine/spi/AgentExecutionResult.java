package com.huawei.ascend.runtime.engine.spi;

import java.util.Map;
import java.util.Objects;

public final class AgentExecutionResult {

    public enum Type { OUTPUT, COMPLETED, FAILED, INTERRUPTED }

    /**
     * Routing target for this result. Set by the producing handler to tell the
     * runtime engine where the content should be delivered. Default is
     * {@link Target#BOTH} (backward-compatible).
     */
    public enum Target { USER, LLM, BOTH }

    private final Type type;
    private final String outputContent;
    private final String errorCode;
    private final String errorMessage;
    private final String prompt;
    private final RemoteInvocation remoteInvocation;
    private final InterruptPayload interruptPayload;
    private final Target target;

    private AgentExecutionResult(Type type, String outputContent, String errorCode,
                                  String errorMessage, String prompt, RemoteInvocation remoteInvocation,
                                  InterruptPayload interruptPayload, Target target) {
        this.type = type;
        this.outputContent = outputContent;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.prompt = prompt;
        this.remoteInvocation = remoteInvocation;
        this.interruptPayload = interruptPayload;
        this.target = target != null ? target : Target.BOTH;
    }

    // ── Backward-compatible factories (default target = BOTH) ──

    public static AgentExecutionResult output(String content) {
        return output(content, Target.BOTH);
    }

    public static AgentExecutionResult completed(String content) {
        return completed(content, Target.BOTH);
    }

    public static AgentExecutionResult failed(String errorCode, String errorMessage) {
        return failed(errorCode, errorMessage, Target.BOTH);
    }

    public static AgentExecutionResult interrupted(String prompt) {
        return interrupted(prompt, Target.BOTH);
    }

    public static AgentExecutionResult interrupted(RemoteInvocation remoteInvocation) {
        RemoteInvocation required = Objects.requireNonNull(remoteInvocation, "remoteInvocation");
        return new AgentExecutionResult(
                Type.INTERRUPTED, null, null, null, null, required,
                new RemoteAgentInterrupt(required), Target.BOTH);
    }

    // ── Target-aware factories ──

    public static AgentExecutionResult output(String content, Target target) {
        return new AgentExecutionResult(Type.OUTPUT, content, null, null, null, null, null, target);
    }

    public static AgentExecutionResult completed(String content, Target target) {
        return new AgentExecutionResult(Type.COMPLETED, content, null, null, null, null, null, target);
    }

    public static AgentExecutionResult failed(String errorCode, String errorMessage, Target target) {
        return new AgentExecutionResult(Type.FAILED, null, errorCode, errorMessage, null, null, null, target);
    }

    public static AgentExecutionResult interrupted(String prompt, Target target) {
        return new AgentExecutionResult(
                Type.INTERRUPTED, null, null, null, prompt, null, new UserInputInterrupt(prompt), target);
    }

    public Type type() { return type; }
    public String outputContent() { return outputContent; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public String prompt() { return prompt; }
    public RemoteInvocation remoteInvocation() { return remoteInvocation; }
    public InterruptPayload interruptPayload() { return interruptPayload; }
    public Target target() { return target; }

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
