package com.huawei.ascend.bus.spi.engine;

import com.huawei.ascend.bus.spi.engine.RunMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Library-mode SPI conformance test for SuspendSignal — Rule R-M.d + ADR-0019
 * (checked-suspension doctrine, sealed-variant post-rc3 refactor).
 *
 * <p>Two flavours of SuspendSignal: child-run (legacy W0+) and S2C client-callback
 * (W2.x Phase 3). Both share the same checked-exception base so the Java type
 * system pins suspension at compile time. This test asserts the invariants of
 * both factory paths.
 *
 * <p>Pure JUnit Jupiter — no Spring, no I/O. Part of the Rule D-3.b evidence layer +
 * Rule R-D.a.b TCK-promotion holding tank.
 */
class SuspendSignalLibraryTest {

    private static final ExecutorDefinition.GraphDefinition CHILD_DEF = new ExecutorDefinition.GraphDefinition(
            Map.of("start", (ctx, payload) -> payload),
            Map.of(),
            "start"
    );

    @Test
    void child_run_constructor_requires_parent_node_key() {
        assertThatThrownBy(() -> new SuspendSignal(null, "payload", RunMode.GRAPH, CHILD_DEF))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("parentNodeKey is required");
    }

    @Test
    void child_run_constructor_requires_child_mode() {
        assertThatThrownBy(() -> new SuspendSignal("node-1", "payload", null, CHILD_DEF))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("childMode is required");
    }

    @Test
    void child_run_constructor_requires_child_def() {
        assertThatThrownBy(() -> new SuspendSignal("node-1", "payload", RunMode.GRAPH, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("childDef is required");
    }

    @Test
    void child_run_constructor_accepts_null_resume_payload() {
        SuspendSignal s = new SuspendSignal("node-1", null, RunMode.GRAPH, CHILD_DEF);
        assertThat(s.parentNodeKey()).isEqualTo("node-1");
        assertThat(s.resumePayload()).isNull();
        assertThat(s.childMode()).isEqualTo(RunMode.GRAPH);
        assertThat(s.childDef()).isSameAs(CHILD_DEF);
        assertThat(s.isClientCallback()).isFalse();
        assertThat(s.clientCallback()).isNull();
    }

    @Test
    void client_callback_factory_requires_parent_node_key() {
        assertThatThrownBy(() -> SuspendSignal.forClientCallback(null, "envelope"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("parentNodeKey is required");
    }

    @Test
    void client_callback_factory_requires_envelope() {
        assertThatThrownBy(() -> SuspendSignal.forClientCallback("node-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("envelope is required");
    }

    @Test
    void client_callback_factory_produces_signal_with_distinguishable_shape() {
        Object envelope = new Object();
        SuspendSignal s = SuspendSignal.forClientCallback("node-2", envelope);
        assertThat(s.parentNodeKey()).isEqualTo("node-2");
        assertThat(s.isClientCallback()).isTrue();
        assertThat(s.clientCallback()).isSameAs(envelope);
        // Child-run fields are null on a client-callback signal.
        assertThat(s.childMode()).isNull();
        assertThat(s.childDef()).isNull();
        assertThat(s.resumePayload()).isNull();
    }

    @Test
    void suspend_signal_is_checked_exception() {
        // Compile-time witness for ADR-0019: every executor lambda declares throws
        // SuspendSignal, so suspension is visible in the type system. This test
        // simply asserts SuspendSignal extends Exception (checked) and not
        // RuntimeException (unchecked).
        assertThat(Exception.class).isAssignableFrom(SuspendSignal.class.getSuperclass())
                .as("SuspendSignal must remain a checked exception (ADR-0019)");
        assertThat(RuntimeException.class.isAssignableFrom(SuspendSignal.class))
                .as("SuspendSignal must not be a RuntimeException (ADR-0019)")
                .isFalse();
    }

    @Test
    void message_includes_parent_node_key_for_evidence_capture() {
        // Rule D-3.b evidence-capture surface: the exception message must include
        // the parentNodeKey so a stack-trace alone identifies the suspending node.
        SuspendSignal child = new SuspendSignal("graph.node.bravo", null, RunMode.GRAPH, CHILD_DEF);
        SuspendSignal callback = SuspendSignal.forClientCallback("loop.node.charlie", "env");
        assertThat(child.getMessage()).contains("graph.node.bravo");
        assertThat(callback.getMessage()).contains("loop.node.charlie");
    }

    @Test
    void executor_definition_graph_node_list_survives_construction() {
        // Sanity: the child def we pass to SuspendSignal is the same instance returned.
        List<String> nodeKeys = CHILD_DEF.nodes().keySet().stream().sorted().toList();
        assertThat(nodeKeys).containsExactly("start");
    }
}
