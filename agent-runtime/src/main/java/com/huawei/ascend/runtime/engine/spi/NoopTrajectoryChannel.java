package com.huawei.ascend.runtime.engine.spi;

import java.util.stream.Stream;

/** No-op channel used at detail level OFF or when no consumer is attached. */
public final class NoopTrajectoryChannel implements TrajectoryChannel {

    @Override
    public void publish(TrajectoryEvent event) {
        // intentionally discarded
    }

    @Override
    public Stream<TrajectoryEvent> drain() {
        return Stream.empty();
    }

    @Override
    public void close() {
        // nothing to release
    }
}
