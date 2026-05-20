package ascend.springai.engine.runtime;

import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the EngineEnvelope record validates required fields on construction
 * and applies safe defaults for optional fields. Schema authority:
 * docs/contracts/engine-envelope.v1.yaml. ADR-0072 + CLAUDE.md Rule R-M.a.
 */
class EngineEnvelopeValidationTest {

    private static final ExecutorDefinition.GraphDefinition SAMPLE_GRAPH =
            new ExecutorDefinition.GraphDefinition(
                    Map.of("only", (ctx, p) -> "done"),
                    Map.of(),
                    "only");

    @Test
    void rejects_null_name() {
        assertThatThrownBy(() -> new EngineEnvelope(
                null, null, null, null, "graph", null, null, Map.of(), Map.of(), SAMPLE_GRAPH))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void rejects_blank_name() {
        assertThatThrownBy(() -> new EngineEnvelope(
                "  ", null, null, null, "graph", null, null, Map.of(), Map.of(), SAMPLE_GRAPH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejects_null_engineType() {
        assertThatThrownBy(() -> new EngineEnvelope(
                "x", null, null, null, null, null, null, Map.of(), Map.of(), SAMPLE_GRAPH))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("engineType is required");
    }

    @Test
    void rejects_null_payload() {
        assertThatThrownBy(() -> new EngineEnvelope(
                "x", null, null, null, "graph", null, null, Map.of(), Map.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payload is required");
    }

    @Test
    void optional_fields_default_to_safe_values() {
        EngineEnvelope env = EngineEnvelope.of("my-agent", "graph", SAMPLE_GRAPH);
        assertThat(env.identifier()).isNull();
        assertThat(env.version()).isNull();
        assertThat(env.owner()).isNull();
        assertThat(env.engineVersion()).isNull();
        assertThat(env.compatibility()).isNull();
        assertThat(env.runtimeHints()).isEmpty();
        assertThat(env.observabilityHints()).isEmpty();
    }

    @Test
    void hint_maps_are_defensively_copied() {
        java.util.HashMap<String, Object> mutable = new java.util.HashMap<>();
        mutable.put("k", "v");
        EngineEnvelope env = new EngineEnvelope(
                "x", null, null, null, "graph", null, null, mutable, Map.of(), SAMPLE_GRAPH);
        mutable.put("after", "construction");
        assertThat(env.runtimeHints()).containsOnlyKeys("k");
    }
}
