package com.huawei.ascend.runtime.engine.spi;

/**
 * A terminal consumer of the drained trajectory: the runtime fans every
 * {@link TrajectoryEvent} of one invocation to each registered sink (operator JSONL
 * log, OpenTelemetry export, opt-in A2A delivery to the caller). One instance per
 * invocation. {@link #onOpen}/{@link #onClose} bracket the stream so a sink can set up
 * and flush/close per-invocation resources (e.g. open spans, a northbound artifact).
 * A sink must treat trajectory as best-effort: its failures are isolated by the
 * runtime and never break the agent run.
 */
public interface TrajectorySink {

    /** Called once on the drain thread before the first event. */
    default void onOpen(String contextId, String taskId) { }

    /** Called for each drained event, in {@code seq} order, on the drain thread. */
    void accept(TrajectoryEvent event);

    /** Called once after the last event (or on drain failure) to flush/close resources. */
    default void onClose() { }
}
