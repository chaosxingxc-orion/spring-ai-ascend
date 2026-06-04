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
        outputs.computeIfAbsent(handle, ignored -> new CopyOnWriteArrayList<>()).add(output);
        subscribers.getOrDefault(handle, new CopyOnWriteArrayList<>())
                .forEach(subscriber -> subscriber.accept(output));
        if (output.terminal()) {
            subscribers.remove(handle);
        }
    }

    public List<A2aOutput> list(A2aOutputHandle handle) {
        return List.copyOf(outputs.getOrDefault(handle, new CopyOnWriteArrayList<>()));
    }

    public Runnable subscribe(A2aOutputHandle handle, Consumer<A2aOutput> subscriber) {
        subscribers.computeIfAbsent(handle, ignored -> new CopyOnWriteArrayList<>()).add(subscriber);
        List<A2aOutput> currentOutputs = list(handle);
        if (currentOutputs.stream().noneMatch(A2aOutput::terminal)) {
            currentOutputs.forEach(subscriber);
        }
        return () -> subscribers.computeIfPresent(handle, (ignored, current) -> {
            current.remove(subscriber);
            return current.isEmpty() ? null : current;
        });
    }
}


