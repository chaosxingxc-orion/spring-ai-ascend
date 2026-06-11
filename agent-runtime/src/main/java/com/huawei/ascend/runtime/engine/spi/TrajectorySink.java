package com.huawei.ascend.runtime.engine.spi;

/**
 * A terminal consumer of the trajectory: the stamping emitter hands every
 * {@link TrajectoryEvent} of one invocation to each registered sink synchronously
 * (OpenTelemetry export, opt-in A2A delivery to the caller). One instance per
 * invocation. {@link #onOpen}/{@link #onClose} bracket the stream so a sink can set up
 * and flush/close per-invocation resources (e.g. open spans, a northbound artifact).
 * Events normally arrive on the execute thread, but a framework may emit from its own
 * worker; a sink with mutable state must be thread-safe or rely on the emitter's
 * serialization. A sink must treat trajectory as best-effort: its failures are
 * isolated by the runtime and never break the agent run.
 */
public interface TrajectorySink {

    /** Called once on the execute thread before the first event. */
    default void onOpen(String contextId, String taskId) { }

    /** Called for each event, in {@code seq} order. */
    void accept(TrajectoryEvent event);

    /** Called once after the last event (on the execute thread) to flush/close resources. */
    default void onClose() { }
}
