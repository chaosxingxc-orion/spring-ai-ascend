package com.huawei.ascend.service.queue;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Current in-memory InternalEventQueue implementation backed by JDK primitives.
 */
public final class InMemoryInternalEventQueue<T> implements InternalEventQueue<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryInternalEventQueue.class);

    private final String queueId;
    private final Sinks.Many<T> sink;
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicBoolean subscribed = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    InMemoryInternalEventQueue(String queueId) {
        this.queueId = requireNonBlank(queueId, "queueId");
        this.sink = Sinks.many().unicast().onBackpressureBuffer();
    }

    @Override
    public String queueId() {
        return queueId;
    }

    @Override
    public void offer(T value) {
        Objects.requireNonNull(value, "value");
        long startedNanos = System.nanoTime();
        if (closed.get()) {
            LOGGER.warn("queue offer rejected queueId={} payloadType={} reason=closed",
                    queueId, value.getClass().getSimpleName());
            throw new IllegalStateException("queue is closed: " + queueId);
        }
        size.incrementAndGet();
        Sinks.EmitResult result = sink.tryEmitNext(value);
        if (result == Sinks.EmitResult.OK) {
            LOGGER.debug("trace stage=queue-offer queueId={} payloadType={} size={} durationMs={}",
                    queueId, value.getClass().getSimpleName(), size.get(), elapsedMs(startedNanos));
            return;
        }
        if (result == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
            sink.emitNext(value, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
            LOGGER.debug("trace stage=queue-offer queueId={} payloadType={} size={} retry=nonSerialized durationMs={}",
                    queueId, value.getClass().getSimpleName(), size.get(), elapsedMs(startedNanos));
            return;
        }
        size.decrementAndGet();
        if (result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            LOGGER.warn("queue offer dropped queueId={} payloadType={} reason=zeroSubscriber",
                    queueId, value.getClass().getSimpleName());
            return;
        }
        throw new IllegalStateException("queue offer failed for " + queueId + ": " + result);
    }

    @Override
    public Flux<T> stream() {
        if (!subscribed.compareAndSet(false, true)) {
            throw new IllegalStateException("queue supports a single consumer: " + queueId);
        }
        return sink.asFlux()
                .doOnSubscribe(subscription -> LOGGER.info("queue subscribed queueId={}", queueId))
                .doOnNext(value -> {
                    int remaining = size.decrementAndGet();
                    LOGGER.debug("queue consume queueId={} payloadType={} remaining={}",
                            queueId, value.getClass().getSimpleName(), remaining);
                });
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            size.set(0);
            sink.tryEmitComplete();
            LOGGER.info("queue closed queueId={}", queueId);
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
