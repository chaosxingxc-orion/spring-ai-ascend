package com.huawei.ascend.service.queue;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Internal Event Queue (IEQ) boundary.
 *
 * <p>The queue owns ordering and storage only. It is generic by design and must
 * not inspect Task state, runtime signals, or agent semantics; Task-Centric
 * Control (TCC) code interprets the payload.
 */
public interface TaskQueue<T> {

    String queueId();

    boolean offer(T value);

    Optional<T> poll();

    Optional<T> peek();

    Optional<T> find(Predicate<? super T> matcher);

    List<T> snapshot();

    int size();
}
