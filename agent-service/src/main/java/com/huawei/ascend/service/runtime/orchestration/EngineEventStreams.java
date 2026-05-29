package com.huawei.ascend.service.runtime.orchestration;

import com.huawei.ascend.bus.spi.engine.AgentEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

/** Blocking collector that drains an {@link EnginePort} stream to its single terminal event. */
public final class EngineEventStreams {

    private EngineEventStreams() {}

    public static AgentEvent awaitTerminal(Flow.Publisher<AgentEvent> publisher) {
        AtomicReference<AgentEvent> terminal = new AtomicReference<>();
        AtomicReference<Throwable> streamError = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(AgentEvent event) { if (event.terminal()) { terminal.set(event); } }
            @Override public void onError(Throwable t) { streamError.set(t); done.countDown(); }
            @Override public void onComplete() { done.countDown(); }
        });
        try {
            done.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted awaiting engine terminal event", ie);
        }
        if (streamError.get() != null) {
            throw new IllegalStateException("engine stream error", streamError.get());
        }
        AgentEvent t = terminal.get();
        if (t == null) {
            throw new IllegalStateException("engine stream completed without a terminal event");
        }
        return t;
    }
}
