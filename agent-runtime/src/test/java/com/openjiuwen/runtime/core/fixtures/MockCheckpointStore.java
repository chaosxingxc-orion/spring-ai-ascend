package com.openjiuwen.runtime.core.fixtures;

import com.openjiuwen.runtime.core.engine.DefaultAgentKernel;
import com.openjiuwen.core.kernel.model.Checkpoint;
import com.openjiuwen.core.kernel.model.TaskId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory CheckpointStore for testing.
 *
 * Thread-safe. Supports:
 * - save: stores checkpoint keyed by taskId
 * - loadLatest: returns the most recent checkpoint for a taskId
 * - list: returns all checkpoints for a taskId in chronological order
 */
public class MockCheckpointStore implements DefaultAgentKernel.CheckpointStore {

    private final ConcurrentHashMap<TaskId, List<Checkpoint>> store = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> save(Checkpoint checkpoint) {
        return Mono.fromRunnable(() ->
            store.computeIfAbsent(checkpoint.taskId(), k -> new CopyOnWriteArrayList<>())
                .add(checkpoint));
    }

    @Override
    public Mono<Checkpoint> loadLatest(TaskId taskId) {
        return Mono.fromCallable(() -> {
            List<Checkpoint> checkpoints = store.get(taskId);
            if (checkpoints == null || checkpoints.isEmpty()) {
                return null;
            }
            return checkpoints.stream()
                .max(Comparator.comparing(Checkpoint::timestamp))
                .orElse(null);
        });
    }

    @Override
    public Flux<Checkpoint> list(TaskId taskId) {
        return Flux.fromIterable(
            store.getOrDefault(taskId, List.of())
                .stream()
                .sorted(Comparator.comparing(Checkpoint::timestamp))
                .toList());
    }

    /** Get total number of saved checkpoints (for assertions) */
    public int count() {
        return store.values().stream().mapToInt(List::size).sum();
    }

    /** Get checkpoint count for a specific task */
    public int countForTask(TaskId taskId) {
        return store.getOrDefault(taskId, List.of()).size();
    }

    /** Clear all stored checkpoints */
    public void clear() {
        store.clear();
    }
}
