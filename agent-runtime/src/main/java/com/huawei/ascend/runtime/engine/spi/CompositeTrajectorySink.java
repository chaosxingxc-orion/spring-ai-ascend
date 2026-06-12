package com.huawei.ascend.runtime.engine.spi;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fans the trajectory stream to several {@link TrajectorySink}s. Each call is isolated:
 * one sink throwing is logged and never starves the others or breaks the run, so a
 * misbehaving exporter cannot take down the operator log or the agent.
 */
public final class CompositeTrajectorySink implements TrajectorySink {

    private static final Logger LOG = LoggerFactory.getLogger(CompositeTrajectorySink.class);

    private final List<TrajectorySink> sinks;

    public CompositeTrajectorySink(List<TrajectorySink> sinks) {
        this.sinks = List.copyOf(sinks);
    }

    @Override
    public void onOpen(String contextId, String taskId) {
        for (TrajectorySink sink : sinks) {
            safe(sink, () -> sink.onOpen(contextId, taskId));
        }
    }

    @Override
    public void accept(TrajectoryEvent event) {
        for (TrajectorySink sink : sinks) {
            safe(sink, () -> sink.accept(event));
        }
    }

    @Override
    public void onClose() {
        for (TrajectorySink sink : sinks) {
            safe(sink, sink::onClose);
        }
    }

    private static void safe(TrajectorySink sink, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOG.warn("trajectory sink failed sink={} errorClass={} message={}",
                    sink.getClass().getSimpleName(), e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
