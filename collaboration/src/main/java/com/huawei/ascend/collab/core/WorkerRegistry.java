package com.huawei.ascend.collab.core;

import java.util.List;

/**
 * The set of workers the coordinator can route to — resolved per dispatch, so membership
 * can change at runtime (workers join/leave) instead of being frozen at construction.
 *
 * <p>This is the seam a fleet-scale deployment plugs service discovery into: an
 * implementation backed by a registry/bus would return the live, capability-matching
 * workers for a tenant. The in-process {@link InMemoryWorkerRegistry} is the default;
 * cross-process discovery is deliberately out of scope here (it needs the agent-bus
 * direction to be settled).
 */
public interface WorkerRegistry {

    /** A snapshot of the workers that currently handle the capability. */
    List<Worker> workersFor(String capability);

    static WorkerRegistry of(List<Worker> workers) {
        return new InMemoryWorkerRegistry(workers);
    }
}
