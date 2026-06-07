package com.huawei.ascend.runtime.access.protocol.a2a.egress;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public final class A2aOutputRegistry {

    private final ConcurrentMap<A2aOutputHandle, CopyOnWriteArrayList<A2aOutput>> outputs = new ConcurrentHashMap<>();
    private final ConcurrentMap<A2aOutputHandle, CopyOnWriteArrayList<Consumer<A2aOutput>>> subscribers =
            new ConcurrentHashMap<>();

    public void append(A2aOutputHandle handle, A2aOutput output) {
        CopyOnWriteArrayList<A2aOutput> buffer = outputs.computeIfAbsent(handle, ignored -> new CopyOnWriteArrayList<>());
        // Synchronize append against subscribe on the per-handle buffer so a subscription can
        // never interleave between buffering an output and notifying current subscribers.
        synchronized (buffer) {
            buffer.add(output);
            subscribers.getOrDefault(handle, new CopyOnWriteArrayList<>())
                    .forEach(subscriber -> subscriber.accept(output));
            if (output.terminal()) {
                subscribers.remove(handle);
            }
        }
    }

    public List<A2aOutput> list(A2aOutputHandle handle) {
        return List.copyOf(outputs.getOrDefault(handle, new CopyOnWriteArrayList<>()));
    }

    public Runnable subscribe(A2aOutputHandle handle, Consumer<A2aOutput> subscriber) {
        CopyOnWriteArrayList<A2aOutput> buffer = outputs.computeIfAbsent(handle, ignored -> new CopyOnWriteArrayList<>());
        synchronized (buffer) {
            // Replay everything already buffered for THIS task, including any terminal output, so a
            // subscriber that arrives after the task already finished (fast task completing before the
            // SSE subscription) still receives the terminal and completes instead of waiting forever.
            // The handle is task-scoped, so a prior task's terminal cannot suppress this replay.
            List<A2aOutput> replay = List.copyOf(buffer);
            replay.forEach(subscriber);
            if (replay.stream().anyMatch(A2aOutput::terminal)) {
                return () -> { };
            }
            // Not yet terminal: register for future appends. append() holds the same monitor, so no
            // output can slip between this replay and registration.
            subscribers.computeIfAbsent(handle, ignored -> new CopyOnWriteArrayList<>()).add(subscriber);
        }
        return () -> subscribers.computeIfPresent(handle, (ignored, current) -> {
            current.remove(subscriber);
            return current.isEmpty() ? null : current;
        });
    }
}


