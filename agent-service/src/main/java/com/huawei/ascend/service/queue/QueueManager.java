package com.huawei.ascend.service.queue;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class QueueManager {

    private final ConcurrentMap<String, QueueEntry<?>> queuesById = new ConcurrentHashMap<>();

    public <T> InternalEventQueue<T> getOrCreate(String queueId, Class<T> payloadType) {
        Objects.requireNonNull(payloadType, "payloadType");
        QueueEntry<?> entry = queuesById.compute(requireNonBlank(queueId, "queueId"), (id, existing) -> {
            if (existing == null) {
                return new QueueEntry<>(payloadType, new InMemoryInternalEventQueue<>(id));
            }
            if (!existing.payloadType().equals(payloadType)) {
                throw new IllegalStateException("payload type mismatch for queue: " + id);
            }
            return existing;
        });
        return typed(entry, payloadType);
    }

    public Optional<InternalEventQueue<?>> find(String queueId) {
        Objects.requireNonNull(queueId, "queueId");
        QueueEntry<?> entry = queuesById.get(queueId);
        return entry == null ? Optional.empty() : Optional.of(entry.queue());
    }

    public List<String> queueIds() {
        return queuesById.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public void close(String queueId) {
        Objects.requireNonNull(queueId, "queueId");
        QueueEntry<?> entry = queuesById.remove(queueId);
        if (entry != null) {
            entry.queue().close();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> InternalEventQueue<T> typed(QueueEntry<?> entry, Class<T> payloadType) {
        if (!entry.payloadType().equals(payloadType)) {
            throw new IllegalStateException("payload type mismatch for queue: " + entry.queue().queueId());
        }
        return (InternalEventQueue<T>) entry.queue();
    }

    private record QueueEntry<T>(Class<T> payloadType, InternalEventQueue<T> queue) {
    };

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
