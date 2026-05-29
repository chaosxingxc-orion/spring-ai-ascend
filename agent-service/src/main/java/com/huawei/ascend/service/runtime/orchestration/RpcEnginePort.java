package com.huawei.ascend.service.runtime.orchestration;

import com.huawei.ascend.bus.spi.engine.AgentEvent;
import com.huawei.ascend.bus.spi.engine.EngineDescriptor;
import com.huawei.ascend.bus.spi.engine.EnginePort;
import com.huawei.ascend.bus.spi.engine.ExecuteRequest;
import com.huawei.ascend.bus.spi.engine.ExecutionContext;
import com.huawei.ascend.service.runtime.orchestration.transport.MockEngineChannel;
import com.huawei.ascend.service.runtime.orchestration.transport.ReserializingPublisher;

import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Internal-RPC realization of {@link EnginePort} — deployment Form 1 (separate microservices you
 * own). MOCK-FUNCTIONAL: it serializes the {@link ExecuteRequest} (proving the DefinitionRef +
 * input are wire-safe), round-trips it through an in-JVM {@link MockEngineChannel}, dispatches to
 * a real co-located engine, and re-serializes every event before delivery. No real RPC server is
 * wired; the {@link ExecutionContext} passes by reference on the server side (a real transport
 * would rebind a local context by runId). This proves the boundary is transport-agnostic: this
 * port passes the same conformance TCK as the in-process port.
 */
public final class RpcEnginePort implements EnginePort {

    private final EnginePort serverSide;
    private final MockEngineChannel channel;

    public RpcEnginePort(EnginePort serverSide, MockEngineChannel channel) {
        this.serverSide = Objects.requireNonNull(serverSide, "serverSide is required");
        this.channel = Objects.requireNonNull(channel, "channel is required");
    }

    @Override
    public Flow.Publisher<AgentEvent> execute(ExecutionContext ctx, ExecuteRequest request) {
        ExecuteRequest onWire = channel.deserializeRequest(channel.serialize(request));
        Flow.Publisher<AgentEvent> events = serverSide.execute(ctx, onWire);
        return new ReserializingPublisher(events, channel);
    }

    @Override
    public EngineDescriptor describe() {
        return serverSide.describe();
    }
}
