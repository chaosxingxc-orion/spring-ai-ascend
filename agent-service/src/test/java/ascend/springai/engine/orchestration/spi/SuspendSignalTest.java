package ascend.springai.engine.orchestration.spi;

import ascend.springai.engine.orchestration.spi.RunMode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SuspendSignalTest {

    // n1 is terminal: no entry in edges means no successor
    private static final ExecutorDefinition.GraphDefinition SIMPLE_GRAPH =
            new ExecutorDefinition.GraphDefinition(
                    Map.of("n1", (ctx, p) -> p),
                    Map.of(),
                    "n1");

    @Test
    void suspend_signal_carries_all_fields() {
        var signal = new SuspendSignal("node-2", "resume-data", RunMode.GRAPH, SIMPLE_GRAPH);
        assertThat(signal.parentNodeKey()).isEqualTo("node-2");
        assertThat(signal.resumePayload()).isEqualTo("resume-data");
        assertThat(signal.childMode()).isEqualTo(RunMode.GRAPH);
        assertThat(signal.childDef()).isSameAs(SIMPLE_GRAPH);
    }

    @Test
    void suspend_signal_message_contains_node_key() {
        var signal = new SuspendSignal("my-node", null, RunMode.AGENT_LOOP,
                new ExecutorDefinition.AgentLoopDefinition(
                        (ctx, p, i) -> ExecutorDefinition.ReasoningResult.done(p), 5, Map.of()));
        assertThat(signal.getMessage()).contains("my-node");
    }

    @Test
    void suspend_signal_requires_parent_node_key() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SuspendSignal(null, null, RunMode.GRAPH, SIMPLE_GRAPH))
                .withMessageContaining("parentNodeKey");
    }

    @Test
    void suspend_signal_requires_child_mode() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SuspendSignal("node", null, null, SIMPLE_GRAPH))
                .withMessageContaining("childMode");
    }

    @Test
    void suspend_signal_requires_child_def() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SuspendSignal("node", null, RunMode.GRAPH, null))
                .withMessageContaining("childDef");
    }
}
