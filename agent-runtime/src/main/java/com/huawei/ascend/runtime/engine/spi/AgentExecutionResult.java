package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.model.EngineOutput;
import com.huawei.ascend.runtime.engine.model.InterruptType;

/**
 * Engine-neutral result produced by a framework result adapter. User handlers
 * return framework-native objects; the engine consumes this type after
 * adaptation to emit lifecycle and routing events.
 */
public final class AgentExecutionResult {

    public enum Type {
        OUTPUT,
        COMPLETED,
        FAILED,
        INTERRUPTED
    }

    private final Type type;
    private final EngineOutput output;
    private final String errorCode;
    private final String errorMessage;
    private final InterruptType interruptType;
    private final String prompt;

    private AgentExecutionResult(
            Type type,
            EngineOutput output,
            String errorCode,
            String errorMessage,
            InterruptType interruptType,
            String prompt) {
        this.type = type;
        this.output = output;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.interruptType = interruptType;
        this.prompt = prompt;
    }

    public static AgentExecutionResult output(String content) {
        return new AgentExecutionResult(Type.OUTPUT, new EngineOutput(content, false), null, null, null, null);
    }

    public static AgentExecutionResult completed(String content) {
        return new AgentExecutionResult(Type.COMPLETED, new EngineOutput(content, true), null, null, null, null);
    }

    public static AgentExecutionResult failed(String errorCode, String errorMessage) {
        return new AgentExecutionResult(Type.FAILED, null, errorCode, errorMessage, null, null);
    }

    public static AgentExecutionResult interrupted(InterruptType interruptType, String prompt) {
        return new AgentExecutionResult(Type.INTERRUPTED, null, null, null, interruptType, prompt);
    }

    public Type type() {
        return type;
    }

    public EngineOutput output() {
        return output;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public InterruptType interruptType() {
        return interruptType;
    }

    public String prompt() {
        return prompt;
    }
}
