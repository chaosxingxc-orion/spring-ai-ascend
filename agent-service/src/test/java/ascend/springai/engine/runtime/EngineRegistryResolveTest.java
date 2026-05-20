package ascend.springai.engine.runtime;

import ascend.springai.engine.spi.AgentLoopExecutor;
import ascend.springai.engine.spi.EngineMatchingException;
import ascend.springai.engine.spi.ExecutorAdapter;
import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import ascend.springai.engine.spi.GraphExecutor;
import ascend.springai.engine.orchestration.spi.RunContext;
import ascend.springai.engine.orchestration.spi.SuspendSignal;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies EngineRegistry strict-matching behaviour (ADR-0072, Rules 43+44).
 *
 * <p>Strongest reading of Rule R-M.b: a mismatch raises EngineMatchingException —
 * never silent reinterpretation, never fallback.
 */
class EngineRegistryResolveTest {

    private static final ExecutorDefinition.GraphDefinition GRAPH_DEF =
            new ExecutorDefinition.GraphDefinition(
                    Map.of("only", (ctx, p) -> "done"),
                    Map.of(),
                    "only");

    private static final ExecutorDefinition.AgentLoopDefinition LOOP_DEF =
            new ExecutorDefinition.AgentLoopDefinition(
                    (ctx, payload, iter) -> ExecutorDefinition.ReasoningResult.done("done"),
                    1,
                    Map.of());

    private static final GraphExecutor STUB_GRAPH =
            (RunContext ctx, ExecutorDefinition.GraphDefinition def, Object payload) -> "graph-result";

    private static final AgentLoopExecutor STUB_LOOP =
            (RunContext ctx, ExecutorDefinition.AgentLoopDefinition def, Object payload) -> "loop-result";

    @Test
    void resolve_by_envelope_engineType_returns_registered_adapter() {
        EngineRegistry registry = new EngineRegistry().register(STUB_GRAPH).register(STUB_LOOP);
        EngineEnvelope env = EngineEnvelope.of("my-agent", GraphExecutor.ENGINE_TYPE, GRAPH_DEF);

        ExecutorAdapter adapter = registry.resolve(env);

        assertThat(adapter).isSameAs(STUB_GRAPH);
        assertThat(adapter.engineType()).isEqualTo("graph");
    }

    @Test
    void resolve_unknown_engineType_raises_engine_mismatch() {
        EngineRegistry registry = new EngineRegistry().register(STUB_GRAPH);
        EngineEnvelope env = EngineEnvelope.of("my-agent", "unknown-engine", GRAPH_DEF);

        assertThatThrownBy(() -> registry.resolve(env))
                .isInstanceOf(EngineMatchingException.class)
                .hasMessageContaining("engine_mismatch")
                .hasMessageContaining("unknown-engine");
    }

    @Test
    void resolveByPayload_routes_by_concrete_class() {
        EngineRegistry registry = new EngineRegistry().register(STUB_GRAPH).register(STUB_LOOP);

        assertThat(registry.resolveByPayload(GRAPH_DEF)).isSameAs(STUB_GRAPH);
        assertThat(registry.resolveByPayload(LOOP_DEF)).isSameAs(STUB_LOOP);
    }

    @Test
    void duplicate_registration_is_rejected() {
        EngineRegistry registry = new EngineRegistry().register(STUB_GRAPH);
        GraphExecutor anotherGraph =
                (RunContext ctx, ExecutorDefinition.GraphDefinition def, Object payload) -> "different";

        assertThatThrownBy(() -> registry.register(anotherGraph))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate adapter registration");
    }

    @Test
    void registered_engine_types_preserves_registration_order() {
        EngineRegistry registry = new EngineRegistry().register(STUB_GRAPH).register(STUB_LOOP);

        assertThat(registry.registeredEngineTypes())
                .containsExactly("graph", "agent-loop");
    }

    @Test
    void empty_registry_resolveByPayload_raises_engine_mismatch_with_helpful_message() {
        EngineRegistry registry = new EngineRegistry();

        assertThatThrownBy(() -> registry.resolveByPayload(GRAPH_DEF))
                .isInstanceOf(EngineMatchingException.class)
                .hasMessageContaining("engine_mismatch")
                .hasMessageContaining("GraphDefinition");
    }
}
