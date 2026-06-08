package com.huawei.ascend.runtime.engine;

/**
 * The kind of outcome an {@link EngineEvent} reports as a handler runs: the
 * lifecycle start, an incremental output chunk, or one of the terminal/blocking
 * outcomes (completed, failed, interrupted-waiting, cancelled).
 */
public enum EngineEventKind {
    STARTED,
    OUTPUT,
    COMPLETED,
    FAILED,
    INTERRUPTED,
    CANCELLED
}
