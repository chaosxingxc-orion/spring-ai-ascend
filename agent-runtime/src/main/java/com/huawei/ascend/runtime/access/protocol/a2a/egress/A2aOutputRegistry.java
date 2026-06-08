package com.huawei.ascend.runtime.access.protocol.a2a.egress;

import com.huawei.ascend.runtime.access.output.OutputChannelRegistry;
import com.huawei.ascend.runtime.access.output.RuntimeOutput;
import com.huawei.ascend.runtime.access.output.RuntimeOutputHandle;
import com.huawei.ascend.runtime.common.AgentResponseEvent;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import reactor.core.Disposable;

public final class A2aOutputRegistry {

    private final OutputChannelRegistry channels = new OutputChannelRegistry();
    private final ConcurrentMap<A2aOutputHandle, CopyOnWriteArrayList<A2aOutput>> outputs = new ConcurrentHashMap<>();

    public void append(A2aOutputHandle handle, A2aOutput output) {
        append(handle, output, toResponseEvent(handle, output, nextSequence(handle)));
    }

    public void append(A2aOutputHandle handle, A2aOutput output, AgentResponseEvent event) {
        Objects.requireNonNull(event, "event");
        CopyOnWriteArrayList<A2aOutput> buffer = outputs.computeIfAbsent(handle, ignored -> new CopyOnWriteArrayList<>());
        synchronized (buffer) {
            if (buffer.stream().anyMatch(A2aOutput::terminal)) {
                return;
            }
            buffer.add(output);
            channels.getOrCreate(toRuntimeHandle(handle)).write(RuntimeOutput.from(event));
        }
    }

    public List<A2aOutput> list(A2aOutputHandle handle) {
        return List.copyOf(outputs.getOrDefault(handle, new CopyOnWriteArrayList<>()));
    }

    public Runnable subscribe(A2aOutputHandle handle, Consumer<A2aOutput> subscriber) {
        AtomicInteger delivered = new AtomicInteger();
        Disposable subscription = channels.getOrCreate(toRuntimeHandle(handle)).stream().subscribe(ignored -> {
            int index = delivered.getAndIncrement();
            List<A2aOutput> current = list(handle);
            if (index < current.size()) {
                subscriber.accept(current.get(index));
            }
        });
        return subscription::dispose;
    }

    private RuntimeOutputHandle toRuntimeHandle(A2aOutputHandle handle) {
        return new RuntimeOutputHandle(handle.tenantId(), handle.sessionId(), handle.taskId());
    }

    private int nextSequence(A2aOutputHandle handle) {
        return outputs.getOrDefault(handle, new CopyOnWriteArrayList<>()).size() + 1;
    }

    private AgentResponseEvent toResponseEvent(A2aOutputHandle handle, A2aOutput output, int sequence) {
        String requestId = metadataValue(output, "requestId", handle.taskId());
        String userId = metadataValue(output, "userId", "");
        String agentId = metadataValue(output, "agentId", "a2a");
        String text = output.body() == null ? "" : String.valueOf(output.body());
        if (output.terminal()) {
            return AgentResponseEvent.finalText(
                    requestId,
                    handle.tenantId(),
                    userId,
                    agentId,
                    handle.sessionId(),
                    handle.taskId(),
                    text,
                    output.metadata());
        }
        return AgentResponseEvent.deltaText(
                requestId,
                Math.max(1L, sequence),
                handle.tenantId(),
                userId,
                agentId,
                handle.sessionId(),
                handle.taskId(),
                text);
    }

    private static String metadataValue(A2aOutput output, String key, String fallback) {
        Map<String, Object> metadata = output.metadata();
        Object value = metadata == null ? null : metadata.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }
}
