package com.huawei.ascend.a2a.memory.obs;

import java.util.List;

/**
 * Fans observability out to several observers (e.g. {@link MicrometerMemoryObserver}
 * for metrics + {@link Slf4jMemoryObserver} for the structured trail). A failing
 * delegate is swallowed so observability never breaks the memory path.
 */
public final class CompositeMemoryObserver implements MemoryObserver {

    private final List<MemoryObserver> delegates;

    public CompositeMemoryObserver(MemoryObserver... delegates) {
        this.delegates = List.of(delegates);
    }

    public static CompositeMemoryObserver of(MemoryObserver... delegates) {
        return new CompositeMemoryObserver(delegates);
    }

    @Override
    public void onOperation(String op, String scope, boolean ok, long latencyMs) {
        for (MemoryObserver d : delegates) {
            try {
                d.onOperation(op, scope, ok, latencyMs);
            } catch (RuntimeException ignored) {
                // observability is best-effort; never break the memory path
            }
        }
    }

    @Override
    public void onDegraded(String op, String scope, String reason) {
        for (MemoryObserver d : delegates) {
            try {
                d.onDegraded(op, scope, reason);
            } catch (RuntimeException ignored) {
                // best-effort
            }
        }
    }
}
