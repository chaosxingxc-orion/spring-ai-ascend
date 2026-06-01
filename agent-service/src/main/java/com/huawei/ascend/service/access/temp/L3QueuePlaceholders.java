package com.huawei.ascend.service.access.temp;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Temporary L3 queue placeholders used only to keep the L1 access-layer skeleton compilable.
 *
 * <p>When the real L3 queue module is available, delete this file and replace all usages of
 * these nested types with the official L3 queue interfaces.
 */
public final class L3QueuePlaceholders {

    private L3QueuePlaceholders() {
    }

    public interface Queue extends QueuePublisher, QueueConsumer {
        QueueId id();
    }

    public interface QueuePublisher {
        void push(Object value);
    }

    public interface QueueConsumer {
        Optional<Object> poll(QueuePollRequest request);
    }

    public interface QueueFactory {
        Queue createQueue(QueueSpec spec);
    }

    public record QueueId(String value) {
        public QueueId {
            Objects.requireNonNull(value, "value");
            if (value.isBlank()) {
                throw new IllegalArgumentException("value must not be blank");
            }
        }
    }

    public record QueueSpec(
            QueueId queueId,
            String tenantId,
            String sessionId,
            String taskId) {

        public QueueSpec {
            Objects.requireNonNull(queueId, "queueId");
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(taskId, "taskId");
        }
    }

    public record QueuePollRequest(Duration timeout) {
        public static QueuePollRequest immediate() {
            return new QueuePollRequest(Duration.ZERO);
        }
    }

    public static final class InMemoryQueueFactory implements QueueFactory {
        @Override
        public Queue createQueue(QueueSpec spec) {
            Objects.requireNonNull(spec, "spec");
            return new InMemoryQueue(spec.queueId());
        }
    }

    public static final class InMemoryQueue implements Queue {

        private final QueueId id;
        private final BlockingQueue<Object> values = new LinkedBlockingQueue<>();

        public InMemoryQueue(QueueId id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        @Override
        public QueueId id() {
            return id;
        }

        @Override
        public void push(Object value) {
            values.add(Objects.requireNonNull(value, "value"));
        }

        @Override
        public Optional<Object> poll(QueuePollRequest request) {
            Duration timeout = request == null ? Duration.ZERO : request.timeout();
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                return Optional.ofNullable(values.poll());
            }
            try {
                return Optional.ofNullable(values.poll(timeout.toMillis(), TimeUnit.MILLISECONDS));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }
}


