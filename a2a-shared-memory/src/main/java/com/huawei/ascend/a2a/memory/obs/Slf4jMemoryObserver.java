package com.huawei.ascend.a2a.memory.obs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Structured, dual-mode ops trail on the {@code a2amem} logger (the a2a-shared-memory design decision /
 * personal observability bar). One instrumentation surface, intensity switched by
 * the {@code verbose} flag + logger level — not two code paths:
 *
 * <ul>
 *   <li>routine successful operations &rarr; DEBUG (runtime-lean: at fleet scale
 *       there are many per second), or INFO when {@code verbose} (dev). Guarded by
 *       {@code isEnabled} so a disabled level costs nothing (no MDC, no formatting).</li>
 *   <li>degraded outcomes (fail-open / circuit-open / ownership-rejected) &rarr;
 *       WARN always (ops must see these in prod).</li>
 * </ul>
 *
 * MDC ({@code op}/{@code scope}/{@code latencyMs}) is always cleared in finally —
 * never leak context onto the pooled thread.
 */
public final class Slf4jMemoryObserver implements MemoryObserver {

    private static final Logger LOG = LoggerFactory.getLogger("a2amem");

    private final boolean verbose;

    public Slf4jMemoryObserver() {
        this(false);
    }

    public Slf4jMemoryObserver(boolean verbose) {
        this.verbose = verbose;
    }

    /** Dev mode: routine operations promoted to INFO. */
    public static Slf4jMemoryObserver verbose() {
        return new Slf4jMemoryObserver(true);
    }

    @Override
    public void onOperation(String op, String scope, boolean ok, long latencyMs) {
        if (verbose ? !LOG.isInfoEnabled() : !LOG.isDebugEnabled()) {
            return;
        }
        MDC.put("op", op);
        MDC.put("scope", scope);
        MDC.put("latencyMs", Long.toString(latencyMs));
        try {
            if (verbose) {
                LOG.info("a2amem op={} scope={} ok={} latencyMs={}", op, scope, ok, latencyMs);
            } else {
                LOG.debug("a2amem op={} scope={} ok={} latencyMs={}", op, scope, ok, latencyMs);
            }
        } finally {
            MDC.remove("op");
            MDC.remove("scope");
            MDC.remove("latencyMs");
        }
    }

    @Override
    public void onDegraded(String op, String scope, String reason) {
        if (!LOG.isWarnEnabled()) {
            return;
        }
        MDC.put("op", op);
        MDC.put("scope", scope);
        try {
            LOG.warn("a2amem degraded op={} scope={} reason={}", op, scope, reason);
        } finally {
            MDC.remove("op");
            MDC.remove("scope");
        }
    }
}
