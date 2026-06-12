package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;

/**
 * Marks a handler that can surface a northbound trajectory for one invocation. The
 * runtime calls {@link #openTrajectory} (before {@code execute}) with the sink it
 * wants fed; the handler wires a stamping emitter for its supported kinds onto the
 * context, and the adapter then pushes events through that emitter synchronously.
 * Handlers that do not implement this interface run exactly as before, with no
 * trajectory.
 */
public interface TrajectorySource {

    void openTrajectory(AgentExecutionContext context, TrajectorySettings settings, TrajectorySink sink);
}
