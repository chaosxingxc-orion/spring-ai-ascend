package com.huawei.ascend.runtime.engine.spi;

/**
 * Convenience base for {@link AgentDriver} implementations: provides the standard
 * started/stopped lifecycle ({@code start}/{@code stop}/{@code isRunning}) and a default
 * description. Subclasses supply {@code name}, {@code frameworkId}, {@code invoke} and
 * {@code outputConverter}.
 */
public abstract class AbstractAgentDriver implements AgentDriver {

    private volatile boolean running;

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String description() {
        return name();
    }
}
