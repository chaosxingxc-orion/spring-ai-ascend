package com.huawei.ascend.runtime.engine;

import com.huawei.ascend.runtime.common.InvocationRequest;
import com.huawei.ascend.runtime.common.RunEvent;
import com.huawei.ascend.runtime.engine.spi.AgentDriver;
import java.util.concurrent.Flow;

/**
 * Coordinates one agent run: wraps exactly one {@link AgentDriver} and produces the neutral
 * reactive {@code RunEvent} stream. Hosting (SDK / microservice) and the access protocol (A2A)
 * sit above the coordinator; the framework driver sits below it. The coordinator itself is
 * framework-neutral.
 *
 * <p>Naming: "run coordinator" + {@code stream} are neutral, industry-common terms; kept
 * distinct from any single framework's run/runner type.
 */
public final class RunCoordinator {

    private final AgentDriver driver;

    public RunCoordinator(AgentDriver driver) {
        this.driver = driver;
    }

    public void start() {
        driver.start();
    }

    public void stop() {
        driver.stop();
    }

    public boolean isRunning() {
        return driver.isRunning();
    }

    public String frameworkId() {
        return driver.frameworkId();
    }

    /** Execute the agent and return the neutral {@code RunEvent} stream. */
    public Flow.Publisher<RunEvent> stream(InvocationRequest request) {
        Object nativeStream = driver.invoke(request);
        return driver.outputConverter().convert(nativeStream);
    }
}
