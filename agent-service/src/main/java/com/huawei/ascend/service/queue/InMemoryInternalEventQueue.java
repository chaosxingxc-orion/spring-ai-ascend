package com.huawei.ascend.service.queue;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Current in-memory InternalEventQueue implementation backed by JDK primitives.
 */
public final class InMemoryInternalEventQueue<T> implements InternalEventQueue<T> {

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
        if (closed.get()) {
            throw new IllegalStateException("queue is closed: " + queueId);
        }
        size.incrementAndGet();
        Sinks.EmitResult result = sink.tryEmitNext(value);
        if (result == Sinks.EmitResult.OK) {
            return;
        }
        if (result == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
            sink.emitNext(value, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
            return;
        }
        size.decrementAndGet();
        if (result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
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
                .doOnNext(value -> size.decrementAndGet());
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
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
