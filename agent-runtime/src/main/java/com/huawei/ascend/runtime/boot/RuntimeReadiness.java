package com.huawei.ascend.runtime.boot;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The runtime's serving gate: open only between handler start and handler
 * stop. The A2A executor consults it before accepting an execution and the
 * health surface reports OUT_OF_SERVICE while it is closed, so boot and drain
 * windows reject work retryable instead of executing against half-open
 * handlers.
 */
public final class RuntimeReadiness {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    public boolean isReady() {
        return ready.get();
    }

    void markReady() {
        ready.set(true);
    }

    void markNotReady() {
        ready.set(false);
    }
}
