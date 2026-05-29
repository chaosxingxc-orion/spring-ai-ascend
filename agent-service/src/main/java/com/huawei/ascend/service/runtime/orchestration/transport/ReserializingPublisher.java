package com.huawei.ascend.service.runtime.orchestration.transport;

import com.huawei.ascend.bus.spi.engine.AgentEvent;

import java.util.concurrent.Flow;

/**
 * Decorates an EnginePort event stream, round-tripping every {@link AgentEvent} through
 * {@link MockEngineChannel} (serialize -> deserialize) before delivery — proving each event is
 * wire-safe. Forwards subscription semantics 1:1 (upstream is synchronous single-event).
 */
public final class ReserializingPublisher implements Flow.Publisher<AgentEvent> {

    private final Flow.Publisher<AgentEvent> upstream;
    private final MockEngineChannel channel;

    public ReserializingPublisher(Flow.Publisher<AgentEvent> upstream, MockEngineChannel channel) {
        this.upstream = upstream;
        this.channel = channel;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super AgentEvent> subscriber) {
        upstream.subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription s) { subscriber.onSubscribe(s); }
            @Override public void onNext(AgentEvent event) {
                subscriber.onNext(channel.deserializeEvent(channel.serialize(event)));
            }
            @Override public void onError(Throwable t) { subscriber.onError(t); }
            @Override public void onComplete() { subscriber.onComplete(); }
        });
    }
}
