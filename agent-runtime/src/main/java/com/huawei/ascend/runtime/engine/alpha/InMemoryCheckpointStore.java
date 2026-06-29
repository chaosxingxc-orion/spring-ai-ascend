package com.huawei.ascend.runtime.engine.alpha;

import com.openjiuwen.core.kernel.model.Checkpoint;
import com.openjiuwen.core.kernel.model.TaskId;
import com.openjiuwen.runtime.core.engine.DefaultAgentKernel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link DefaultAgentKernel.CheckpointStore} — the process-local default for the
 * Alpha engine. Checkpoints live in a per-task {@link CopyOnWriteArrayList} keyed by task id;
 * {@code loadLatest} resolves the highest-timestamp entry. Sufficient for single-process
 * execution and tests; durable backends (redis/jdbc) are out of scope for this round.
 */
public final class InMemoryCheckpointStore implements DefaultAgentKernel.CheckpointStore {

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
                store.getOrDefault(taskId, List.of()).stream()
                        .sorted(Comparator.comparing(Checkpoint::timestamp))
                        .toList());
    }
}
