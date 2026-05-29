package com.huawei.ascend.bus.spi.engine;

import java.util.UUID;

/**
 * Neutral engine-facing execution context: the correlation + capability surface an engine
 * sees across ANY transport. Carries only opaque correlation (runId / traceId / spanId) and
 * the {@link Checkpointer}. It exposes NO tenant or session semantics — those stay
 * Service-owned (ADR-0158). The Service-side {@link RunContext} subtype adds tenant/session
 * and the child-run suspend capability for node functions.
 *
 * <p>Pure Java — no Spring imports.
 */
public interface ExecutionContext {

    UUID runId();

    String traceId();

    String spanId();

    Checkpointer checkpointer();
}
