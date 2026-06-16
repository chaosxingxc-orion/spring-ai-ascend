package com.huawei.ascend.collab.core;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process {@link WorkerRegistry} with runtime {@link #register}/{@link #deregister} —
 * workers can join or leave between dispatches. Insertion order is preserved so routing
 * (round-robin) stays deterministic; the backing list is copy-on-write for safe concurrent
 * reads under {@code runConcurrent}.
 */
public final class InMemoryWorkerRegistry implements WorkerRegistry {

    private final CopyOnWriteArrayList<Worker> workers = new CopyOnWriteArrayList<>();

    public InMemoryWorkerRegistry() {
    }

    public InMemoryWorkerRegistry(Collection<Worker> initial) {
        if (initial != null) {
            initial.forEach(this::register);
        }
    }

    /** Add (or replace, by id) a worker. */
    public void register(Worker worker) {
        deregister(worker.id());
        workers.add(worker);
    }

    public void deregister(String workerId) {
        workers.removeIf(w -> w.id().equals(workerId));
    }

    @Override
    public List<Worker> workersFor(String capability) {
        return workers.stream().filter(w -> w.handles(capability)).toList();
    }

    public List<Worker> all() {
        return List.copyOf(workers);
    }
}
