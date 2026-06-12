package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Drives {@link AgentRuntimeHandler#start()} / {@link AgentRuntimeHandler#stop()}
 * around the serving window and owns the {@link RuntimeReadiness} gate.
 *
 * <p>Phase 0 keeps this bean below Spring Boot's web-server lifecycle phases,
 * so handlers start before the server accepts traffic and stop only after the
 * server has stopped accepting new requests (stop order is the reverse of
 * start order).
 *
 * <p>A handler start failure aborts context refresh after rolling back the
 * handlers already started — a runtime that cannot open its handlers must not
 * come up looking healthy. Stop failures are logged and swallowed so every
 * handler gets its release attempt.
 */
public final class AgentRuntimeLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeLifecycle.class);

    private final List<AgentRuntimeHandler> handlers;
    private final RuntimeReadiness readiness;
    private volatile boolean running;

    public AgentRuntimeLifecycle(List<AgentRuntimeHandler> handlers, RuntimeReadiness readiness) {
        this.handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers"));
        this.readiness = Objects.requireNonNull(readiness, "readiness");
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        int started = 0;
        try {
            for (AgentRuntimeHandler handler : handlers) {
                handler.start();
                started++;
            }
        } catch (RuntimeException startFailure) {
            stopQuietly(started);
            throw startFailure;
        }
        readiness.markReady();
        running = true;
        log.info("agent runtime lifecycle started handlers={}", handlers.size());
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        // Close the gate before touching handlers so no new execution races a
        // handler that is releasing its resources.
        readiness.markNotReady();
        running = false;
        stopQuietly(handlers.size());
        log.info("agent runtime lifecycle stopped handlers={}", handlers.size());
    }

    /** Stops the first {@code count} handlers in reverse start order, swallowing failures. */
    private void stopQuietly(int count) {
        for (int i = count - 1; i >= 0; i--) {
            AgentRuntimeHandler handler = handlers.get(i);
            try {
                handler.stop();
            } catch (RuntimeException stopFailure) {
                log.warn("agent runtime handler stop failed agentId={} errorClass={} message={}",
                        handler.agentId(), stopFailure.getClass().getSimpleName(), stopFailure.getMessage());
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Below the web-server lifecycle phases: ready before traffic, released after it.
        return 0;
    }
}
