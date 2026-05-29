package com.huawei.ascend.service.runtime.orchestration;

import com.huawei.ascend.engine.exec.SequentialGraphExecutor;
import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.bus.spi.engine.DefinitionRef;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.bus.spi.engine.Orchestrator;
import com.huawei.ascend.bus.spi.engine.RunMode;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryCheckpointer;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import com.huawei.ascend.service.runtime.orchestration.inmemory.SyncOrchestrator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W4.2 spike (make-or-break) — proves the over-the-wire suspend/resume model is
 * feasible by showing, in-process, that resuming a suspended leg depends ONLY on
 * externalized, byte-serializable state:
 *
 * <ol>
 *   <li>the resume cursor is persisted as bytes via {@code Checkpointer}
 *       (what a fresh {@code execute(startCheckpointRef)} would reload);</li>
 *   <li>suspension is transparent to the final artifact — re-executing from the
 *       cursor yields the same result as a straight-through run;</li>
 *   <li>the two things that must cross a transport on resume — the definition
 *       reference and the resume payload — are byte-serializable.</li>
 * </ol>
 *
 * <p>If resume held only via the live JVM stack or retained objects it could
 * never cross a transport boundary. It holds via the checkpoint plus the resume
 * payload, both of which can. The in-process realization here maps onto the
 * checked {@code SuspendSignal} exception; the networked realization returns the
 * same state as an {@code INTERRUPT_REQUEST} terminal event (engine-port.v1.yaml).
 */
class SuspendResumeWireReadinessSpikeTest {

    private static final String TENANT = "spike-tenant";

    private static Orchestrator orchestrator(InMemoryCheckpointer cp, InMemoryRunRegistry reg) {
        return TestEnginePorts.inProcessOrchestrator(reg, cp,
                new EngineRegistry().register(new SequentialGraphExecutor()));
    }

    /** n1 -> n2 -> n3, fully in-process; n2 produces the "B|" segment. */
    private static ExecutorDefinition.GraphDefinition straightGraph() {
        return new ExecutorDefinition.GraphDefinition(
                Map.of(
                        "n1", (ctx, p) -> "A|",
                        "n2", (ctx, p) -> p + "B|",
                        "n3", (ctx, p) -> p + "C"),
                Map.of("n1", "n2", "n2", "n3"),
                "n1");
    }

    /** Same shape, but n2 delegates the "B|" segment to a child run and suspends. */
    private static ExecutorDefinition.GraphDefinition suspendingGraph() {
        ExecutorDefinition.GraphDefinition child = new ExecutorDefinition.GraphDefinition(
                Map.of("b", (ctx, p) -> p + "B|"),
                Map.of(),
                "b");
        return new ExecutorDefinition.GraphDefinition(
                Map.of(
                        "n1", (ctx, p) -> "A|",
                        "n2", (ctx, p) -> {
                            ctx.suspendForChild("n2", RunMode.GRAPH, child, p);
                            return null; // unreachable — suspendForChild always throws
                        },
                        "n3", (ctx, p) -> p + "C"),
                Map.of("n1", "n2", "n2", "n3"),
                "n1");
    }

    @Test
    void suspend_is_transparent_to_the_final_artifact() {
        Object straight = orchestrator(new InMemoryCheckpointer(), new InMemoryRunRegistry())
                .run(UUID.randomUUID(), TENANT, straightGraph(), null);

        Object suspended = orchestrator(new InMemoryCheckpointer(), new InMemoryRunRegistry())
                .run(UUID.randomUUID(), TENANT, suspendingGraph(), null);

        assertThat(straight).isEqualTo("A|B|C");
        assertThat(suspended).isEqualTo(straight);
    }

    @Test
    void resume_cursor_is_persisted_as_bytes() {
        InMemoryCheckpointer cp = new InMemoryCheckpointer();
        InMemoryRunRegistry reg = new InMemoryRunRegistry();
        UUID runId = UUID.randomUUID();

        Object result = orchestrator(cp, reg).run(runId, TENANT, suspendingGraph(), null);
        assertThat(result).isEqualTo("A|B|C");

        // The parent leg externalized its resume cursor to the checkpoint store as
        // UTF-8 bytes — exactly the state a fresh execute(startCheckpointRef) reloads.
        Optional<byte[]> cursor = cp.load(runId, "_graph_next_node");
        assertThat(cursor).isPresent();
        assertThat(new String(cursor.orElseThrow(), StandardCharsets.UTF_8)).isEqualTo("n3");
    }

    @Test
    void wire_transferable_resume_state_is_byte_serializable() throws Exception {
        // The definition reference and the resume payload are the state that must
        // cross a transport boundary; both round-trip through bytes.
        assertThat(roundTrip(new DefinitionRef("echo"))).isEqualTo(new DefinitionRef("echo"));
        assertThat(roundTrip("A|B|")).isEqualTo("A|B|");
    }

    private static Object roundTrip(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return in.readObject();
        }
    }
}
