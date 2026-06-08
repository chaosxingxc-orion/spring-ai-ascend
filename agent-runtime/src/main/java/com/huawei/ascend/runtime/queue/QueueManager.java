package com.huawei.ascend.runtime.queue;

import com.huawei.ascend.runtime.common.Guards;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueManager.class);

    private final ConcurrentMap<String, QueueEntry<?>> queuesById = new ConcurrentHashMap<>();

    public <T> InternalEventQueue<T> getOrCreate(String queueId, Class<T> payloadType) {
        Objects.requireNonNull(payloadType, "payloadType");
        QueueEntry<?> entry = queuesById.compute(Guards.requireNonBlank(queueId, "queueId"), (id, existing) -> {
            if (existing == null) {
                LOGGER.info("queue create queueId={} payloadType={}", id, payloadType.getSimpleName());
                return new QueueEntry<>(payloadType, new InMemoryInternalEventQueue<>(id));
            }
            if (!existing.payloadType().equals(payloadType)) {
                LOGGER.warn("queue type mismatch queueId={} expectedPayloadType={} actualPayloadType={}",
                        id, payloadType.getSimpleName(), existing.payloadType().getSimpleName());
                throw new IllegalStateException("payload type mismatch for queue: " + id);
            }
            LOGGER.info("queue reuse queueId={} payloadType={}", id, payloadType.getSimpleName());
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
            LOGGER.info("queue unregister queueId={} payloadType={}",
                    queueId, entry.payloadType().getSimpleName());
        } else {
            LOGGER.info("queue unregister skipped queueId={} reason=notFound", queueId);
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
}
