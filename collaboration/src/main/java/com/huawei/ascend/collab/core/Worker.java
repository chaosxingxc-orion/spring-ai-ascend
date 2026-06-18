package com.huawei.ascend.collab.core;

import java.util.Set;

/**
 * An agent that can execute sub-tasks for one or more capabilities. The
 * transport is abstracted: {@code A2aWorker} bridges to a remote A2A agent,
 * while the eval harness uses an in-memory scripted worker. Implementations MUST
 * echo the received {@link TaskToken} back on the {@link WorkResult}.
 */
public interface Worker {

    String id();

    Set<String> capabilities();

    default boolean handles(String capability) {
        return capabilities().contains(capability);
    }

    /**
     * Whether this worker is currently fit to receive work. The coordinator skips
     * unhealthy workers when a healthy one is available. Default: always healthy
     * (a self-reporting worker, e.g. one tracking its own remote endpoint, may override).
     */
    default boolean healthy() {
        return true;
    }

    /** Execute the task under the issued token; echo the token back on the result. */
    WorkResult execute(SubTask task, TaskToken token);
}
