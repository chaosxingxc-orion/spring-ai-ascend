package com.huawei.ascend.service.queue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

/**
 * Current in-memory TaskQueue implementation backed by JDK primitives.
 *
 * <p>Future Redis/JDBC/Kafka backends should add another TaskQueue
 * implementation instead of teaching this queue about Task state.
 */
public final class InMemoryTaskQueue<T> implements TaskQueue<T> {

    private final String queueId;
    private final LinkedBlockingQueue<T> delegate;

    InMemoryTaskQueue(String queueId) {
        this.queueId = requireNonBlank(queueId, "queueId");
        this.delegate = new LinkedBlockingQueue<>();
    }

    @Override
    public String queueId() {
        return queueId;
    }

    @Override
    public boolean offer(T value) {
        return delegate.offer(Objects.requireNonNull(value, "value"));
    }

    @Override
    public Optional<T> poll() {
        return Optional.ofNullable(delegate.poll());
    }

    @Override
    public Optional<T> peek() {
        return Optional.ofNullable(delegate.peek());
    }

    @Override
    public Optional<T> find(Predicate<? super T> matcher) {
        Objects.requireNonNull(matcher, "matcher");
        return delegate.stream().filter(matcher).findFirst();
    }

    @Override
    public List<T> snapshot() {
        return List.copyOf(delegate);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
