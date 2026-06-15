package com.huawei.ascend.runtime.engine.spi;

/**
 * The push side of the extraction contract: a framework adapter calls
 * {@link #emit(TrajectoryDraft)} from its native callback (e.g. an openJiuwen
 * {@code AgentRail} hook). The implementation stamps correlation, applies the
 * detail level + masking, and publishes to the per-invocation channel.
 */
@FunctionalInterface
public interface TrajectoryEmitter {

    /** A sink that drops everything — used when trajectory is OFF or not wired. */
    TrajectoryEmitter NOOP = draft -> { };

    void emit(TrajectoryDraft draft);
}
