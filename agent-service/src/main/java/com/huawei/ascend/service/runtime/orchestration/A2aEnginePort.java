package com.huawei.ascend.service.runtime.orchestration;

import com.huawei.ascend.bus.spi.engine.AgentEvent;
import com.huawei.ascend.bus.spi.engine.EngineDescriptor;
import com.huawei.ascend.bus.spi.engine.EnginePort;
import com.huawei.ascend.bus.spi.engine.ExecuteRequest;
import com.huawei.ascend.bus.spi.engine.ExecutionContext;
import com.huawei.ascend.service.runtime.orchestration.transport.A2aEnvelopeMock;
import com.huawei.ascend.service.runtime.orchestration.transport.MockEngineChannel;
import com.huawei.ascend.service.runtime.orchestration.transport.ReserializingPublisher;

import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * A2A federation realization of {@link EnginePort} — drives an external / third-party agent as an
 * engine. MOCK-FUNCTIONAL: it serializes the {@link ExecuteRequest}, wraps the bytes in an
 * {@link A2aEnvelopeMock} (fake federation framing: from / to / agentCard) to mirror an A2A
 * message shape, round-trips the envelope through an in-JVM {@link MockEngineChannel}, unwraps
 * and deserializes the request on the server side, dispatches to a real co-located engine, and
 * re-serializes every event before delivery. No real federation broker is wired; the
 * {@link ExecutionContext} passes by reference on the server side (a real transport would rebind
 * a local context by runId). This proves the boundary is transport-agnostic: this port passes
 * the same conformance TCK as the in-process port.
 */
public final class A2aEnginePort implements EnginePort {

    private static final String FROM = "agent-service";
    private static final String TO = "federated-engine";
    private static final String AGENT_CARD = "mock-agent-card";

    private final EnginePort serverSide;
    private final MockEngineChannel channel;

    public A2aEnginePort(EnginePort serverSide, MockEngineChannel channel) {
        this.serverSide = Objects.requireNonNull(serverSide, "serverSide is required");
        this.channel = Objects.requireNonNull(channel, "channel is required");
    }

    @Override
    public Flow.Publisher<AgentEvent> execute(ExecutionContext ctx, ExecuteRequest request) {
        // Wrap the serialized request in the federation envelope, round-trip the whole envelope
        // through the mock wire, then unwrap + deserialize the payload on the server side.
        A2aEnvelopeMock envelope = new A2aEnvelopeMock(FROM, TO, AGENT_CARD, channel.serialize(request));
        A2aEnvelopeMock onWire = (A2aEnvelopeMock) deserialize(channel.serialize(envelope));
        ExecuteRequest serverRequest = channel.deserializeRequest(onWire.payload());
        Flow.Publisher<AgentEvent> events = serverSide.execute(ctx, serverRequest);
        return new ReserializingPublisher(events, channel);
    }

    @Override
    public EngineDescriptor describe() {
        return serverSide.describe();
    }

    private Object deserialize(byte[] wire) {
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(wire))) {
            return ois.readObject();
        } catch (java.io.IOException | ClassNotFoundException e) {
            throw new IllegalStateException("mock A2A envelope deserialize failed: " + e.getMessage(), e);
        }
    }
}
