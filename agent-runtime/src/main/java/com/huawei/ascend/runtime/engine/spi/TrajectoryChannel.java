package com.huawei.ascend.runtime.engine.spi;

import java.util.stream.Stream;

/**
 * A per-invocation, ordered conduit between the producer (the framework adapter
 * thread, which {@link #publish(TrajectoryEvent)}es) and a single consumer (the
 * runtime drain thread, which {@link #drain()}s and fans events to logs / A2A /
 * OTel). {@link #close()} signals end-of-stream so {@code drain()} terminates.
 */
public interface TrajectoryChannel extends AutoCloseable {

    /** A channel that swallows publishes and drains empty — used when trajectory is OFF. */
    TrajectoryChannel NOOP = new NoopTrajectoryChannel();

    void publish(TrajectoryEvent event);

    /** Blocks until each event is available; completes when {@link #close()} is called. */
    Stream<TrajectoryEvent> drain();

    @Override
    void close();
}
