package com.huawei.ascend.runtime.engine.spi;

/**
 * Per-invocation trajectory wiring shared between the runtime (which drains the
 * {@link TrajectoryChannel}) and the adapter base (which pushes through the
 * {@link TrajectoryEmitter}). Created by {@link TrajectorySource#openTrajectory}
 * and stashed on the {@code AgentExecutionContext} for that one call.
 */
public record TrajectoryRuntime(TrajectoryChannel channel, TrajectoryEmitter emitter) {
}
