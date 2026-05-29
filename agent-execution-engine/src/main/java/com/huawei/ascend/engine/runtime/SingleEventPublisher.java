package com.huawei.ascend.engine.runtime;

import com.huawei.ascend.bus.spi.engine.AgentEvent;

import java.util.concurrent.Flow;

/**
 * Cold, synchronous, single-subscriber publisher emitting exactly one {@link AgentEvent}
 * then completing — the in-process realization of the EnginePort stream. Synchronous so the
 * orchestrator's blocking collect preserves the single-threaded call-stack atomicity
 * (ADR-0024).
 */
final class SingleEventPublisher implements Flow.Publisher<AgentEvent> {

    private final AgentEvent event;

    SingleEventPublisher(AgentEvent event) {
        this.event = event;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super AgentEvent> subscriber) {
        subscriber.onSubscribe(new Flow.Subscription() {
            private boolean done;
            @Override public void request(long n) {
                if (done || n <= 0) {
                    return;
                }
                done = true;
                subscriber.onNext(event);
                subscriber.onComplete();
            }
            @Override public void cancel() {
                done = true;
            }
        });
    }
}
