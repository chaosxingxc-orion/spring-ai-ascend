package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;

/**
 * Marks a handler that can surface a northbound trajectory for one invocation. The
 * runtime calls {@link #openTrajectory} (before {@code execute}) to obtain the
 * channel it will drain; the same handler then pushes events through the emitter it
 * stashed on the context. Handlers that do not implement this interface run exactly
 * as before, with no trajectory.
 */
public interface TrajectorySource {

    TrajectoryChannel openTrajectory(AgentExecutionContext context, TrajectorySettings settings);
}
