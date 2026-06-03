package com.huawei.ascend.service.queue;

import reactor.core.publisher.Flux;

/**
 * Internal Event Queue (IEQ) boundary.
 *
 * <p>The queue owns asynchronous handoff only. It is generic by design and must
 * not inspect task state, runtime signals, access frames, or agent semantics.
 * Implementations support multiple producer threads and one streaming consumer.
 */
public interface InternalEventQueue<T> extends AutoCloseable {

    String queueId();

    void offer(T value);

    Flux<T> stream();

    int size();

    @Override
    void close();
}
