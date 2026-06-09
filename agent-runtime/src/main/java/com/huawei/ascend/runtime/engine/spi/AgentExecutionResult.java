package com.huawei.ascend.runtime.engine.spi;

public final class AgentExecutionResult {

    public enum Type { OUTPUT, COMPLETED, FAILED, INTERRUPTED }

    private final Type type;
    private final String outputContent;
    private final String errorCode;
    private final String errorMessage;
    private final String prompt;

    private AgentExecutionResult(Type type, String outputContent, String errorCode,
                                  String errorMessage, String prompt) {
        this.type = type;
        this.outputContent = outputContent;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.prompt = prompt;
    }

    public static AgentExecutionResult output(String content) {
        return new AgentExecutionResult(Type.OUTPUT, content, null, null, null);
    }

    public static AgentExecutionResult completed(String content) {
        return new AgentExecutionResult(Type.COMPLETED, content, null, null, null);
    }

    public static AgentExecutionResult failed(String errorCode, String errorMessage) {
        return new AgentExecutionResult(Type.FAILED, null, errorCode, errorMessage, null);
    }

    public static AgentExecutionResult interrupted(String prompt) {
        return new AgentExecutionResult(Type.INTERRUPTED, null, null, null, prompt);
    }

    public Type type() { return type; }
    public String outputContent() { return outputContent; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public String prompt() { return prompt; }
}
