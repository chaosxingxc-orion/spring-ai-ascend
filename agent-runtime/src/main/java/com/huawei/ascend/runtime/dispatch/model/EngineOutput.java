package com.huawei.ascend.service.engine.model;

/**
 * A unit of agent-produced output. {@code finalOutput} marks the terminal chunk
 * of a response. See engine model design §5.4.
 */
public class EngineOutput {
    private String content;
    private boolean finalOutput;

    public EngineOutput() {
    }

    public EngineOutput(String content, boolean finalOutput) {
        this.content = content;
        this.finalOutput = finalOutput;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isFinalOutput() {
        return finalOutput;
    }

    public void setFinalOutput(boolean finalOutput) {
        this.finalOutput = finalOutput;
    }
}
