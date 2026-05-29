package com.huawei.ascend.service.runtime.orchestration.inmemory;

import com.huawei.ascend.engine.exec.IterativeAgentLoopExecutor;
import com.huawei.ascend.engine.exec.SequentialGraphExecutor;
import com.huawei.ascend.engine.runtime.EngineOutcomeChannel;
import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.bus.spi.engine.AgentEvent;
import com.huawei.ascend.bus.spi.engine.DefinitionRef;
import com.huawei.ascend.bus.spi.engine.DefinitionResolver;
import com.huawei.ascend.bus.spi.engine.EnginePort;
import com.huawei.ascend.bus.spi.engine.ExecuteRequest;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.bus.spi.engine.RunContext;
import com.huawei.ascend.bus.spi.engine.RunMode;
import com.huawei.ascend.service.runtime.capability.CapabilityRegistry;
import com.huawei.ascend.service.runtime.orchestration.CompositeDefinitionResolver;
import com.huawei.ascend.service.runtime.orchestration.EngineEventStreams;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transport-conformance kit for {@link EnginePort}. Every transport realization —
 * in-process, internal-RPC mock, and A2A mock — MUST pass this identical battery so the
 * boundary is semantically transport-agnostic: same dispatch results across engine types,
 * same {@code engine_mismatch} FAILED event on an unknown definition, same suspension
 * surfaced as an INTERRUPT_REQUEST terminal event. A subclass supplies the port under test
 * for exactly one transport; that is the single seam a new transport plugs into.
 *
 * <p>The port returns a stream now: every leg emits exactly one TERMINAL {@link AgentEvent}
 * then completes. The driver collects it via {@link EngineEventStreams#awaitTerminal}.
 */
abstract class EnginePortConformanceTck {

    private static final String TRACEPARENT =
            "00-" + "0".repeat(32) + "-" + "0".repeat(16) + "-01";

    /** The port under test, wired to dispatch the given registry's engines. */
    protected abstract EnginePort portUnderTest(EngineRegistry registry, DefinitionResolver resolver,
                                                EngineOutcomeChannel outcomes);

    private static EngineRegistry enginesRegistered() {
        return new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor());
    }

    private static DefinitionResolver resolver() {
        return new CompositeDefinitionResolver(new CapabilityRegistry(List.of()));
    }

    private static RunContext ctx() {
        return new RunContextImpl("tck-tenant", UUID.randomUUID(), new InMemoryCheckpointer());
    }

    private static ExecuteRequest request(DefinitionResolver resolver, String engineType,
                                          ExecutorDefinition def) {
        DefinitionRef ref = resolver.referenceFor(def);
        return new ExecuteRequest(UUID.randomUUID().toString(), engineType, ref, null, null,
                TRACEPARENT, null);
    }

    @Test
    void dispatches_a_graph_definition() {
        EngineOutcomeChannel outcomes = new EngineOutcomeChannel();
        DefinitionResolver resolver = resolver();
        EnginePort port = portUnderTest(enginesRegistered(), resolver, outcomes);
        ExecutorDefinition def = new ExecutorDefinition.GraphDefinition(
                Map.of("only", (c, p) -> "GRAPH-DONE"), Map.of(), "only");

        AgentEvent ev = EngineEventStreams.awaitTerminal(
                port.execute(ctx(), request(resolver, "graph", def)));

        assertThat(ev).isInstanceOf(AgentEvent.Finished.class);
        assertThat(((AgentEvent.Finished) ev).result()).isEqualTo("GRAPH-DONE");
    }

    @Test
    void dispatches_an_agent_loop_definition() {
        EngineOutcomeChannel outcomes = new EngineOutcomeChannel();
        DefinitionResolver resolver = resolver();
        EnginePort port = portUnderTest(enginesRegistered(), resolver, outcomes);
        ExecutorDefinition def = new ExecutorDefinition.AgentLoopDefinition(
                (c, p, i) -> ExecutorDefinition.ReasoningResult.done("LOOP-DONE"), 5, Map.of());

        AgentEvent ev = EngineEventStreams.awaitTerminal(
                port.execute(ctx(), request(resolver, "agent-loop", def)));

        assertThat(ev).isInstanceOf(AgentEvent.Finished.class);
        assertThat(((AgentEvent.Finished) ev).result()).isEqualTo("LOOP-DONE");
    }

    @Test
    void unknown_definition_fails_with_engine_mismatch() {
        EngineOutcomeChannel outcomes = new EngineOutcomeChannel();
        DefinitionResolver resolver = resolver();
        EnginePort port = portUnderTest(new EngineRegistry(), resolver, outcomes); // no executors registered
        ExecutorDefinition def = new ExecutorDefinition.GraphDefinition(
                Map.of("only", (c, p) -> "X"), Map.of(), "only");

        AgentEvent ev = EngineEventStreams.awaitTerminal(
                port.execute(ctx(), request(resolver, "graph", def)));

        assertThat(ev).isInstanceOf(AgentEvent.Failed.class);
        assertThat(((AgentEvent.Failed) ev).errorClass()).isEqualTo("engine_mismatch");
    }

    @Test
    void suspension_is_surfaced_to_the_driver() {
        EngineOutcomeChannel outcomes = new EngineOutcomeChannel();
        DefinitionResolver resolver = resolver();
        EnginePort port = portUnderTest(enginesRegistered(), resolver, outcomes);
        ExecutorDefinition child = new ExecutorDefinition.GraphDefinition(
                Map.of("c", (c, p) -> "child"), Map.of(), "c");
        ExecutorDefinition def = new ExecutorDefinition.GraphDefinition(
                Map.of("s", (c, p) -> {
                    c.suspendForChild("s", RunMode.GRAPH, child, p);
                    return null; // unreachable — suspendForChild always throws
                }), Map.of(), "s");

        // In-process surfaces suspension as an INTERRUPT_REQUEST terminal event carrying the
        // in-JVM outcome handle (the driver retrieves the SuspendSignal by handle and rethrows).
        AgentEvent ev = EngineEventStreams.awaitTerminal(
                port.execute(ctx(), request(resolver, "graph", def)));

        assertThat(ev).isInstanceOf(AgentEvent.InterruptRequest.class);
        AgentEvent.InterruptRequest ir = (AgentEvent.InterruptRequest) ev;
        assertThat(ir.checkpointRef()).isNotNull();
        assertThat(ir.correlationHandle()).isNotNull();
    }

    @Test
    void exactly_one_terminal_event() {
        EngineOutcomeChannel outcomes = new EngineOutcomeChannel();
        DefinitionResolver resolver = resolver();
        EnginePort port = portUnderTest(enginesRegistered(), resolver, outcomes);
        ExecutorDefinition def = new ExecutorDefinition.GraphDefinition(
                Map.of("only", (c, p) -> "GRAPH-DONE"), Map.of(), "only");

        AgentEvent ev = EngineEventStreams.awaitTerminal(
                port.execute(ctx(), request(resolver, "graph", def)));

        assertThat(ev.terminal()).isTrue();
        assertThat(ev.kind()).isEqualTo(AgentEvent.Kind.FINISHED);
    }
}
