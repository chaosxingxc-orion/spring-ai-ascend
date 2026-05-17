package ascend.springai.service.runtime.orchestration.inmemory;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCheckpointerTest {

    private final InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();

    @Test
    void load_returns_empty_when_nothing_saved() {
        assertThat(checkpointer.load(UUID.randomUUID(), "node-x")).isEmpty();
    }

    @Test
    void save_then_load_returns_same_bytes() {
        UUID runId = UUID.randomUUID();
        byte[] payload = "checkpoint-data".getBytes(StandardCharsets.UTF_8);
        checkpointer.save(runId, "node-1", payload);
        byte[] loaded = checkpointer.load(runId, "node-1").orElseThrow();
        assertThat(loaded).isEqualTo(payload);
    }

    @Test
    void save_returns_copy_not_reference() {
        UUID runId = UUID.randomUUID();
        byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
        checkpointer.save(runId, "n", payload);
        payload[0] = 0; // mutate original
        byte[] loaded = checkpointer.load(runId, "n").orElseThrow();
        assertThat(loaded[0]).isNotEqualTo((byte) 0);
    }

    @Test
    void different_run_ids_are_independent() {
        UUID run1 = UUID.randomUUID();
        UUID run2 = UUID.randomUUID();
        checkpointer.save(run1, "node", "r1".getBytes(StandardCharsets.UTF_8));
        assertThat(checkpointer.load(run2, "node")).isEmpty();
    }

    @Test
    void overwrite_replaces_previous_checkpoint() {
        UUID runId = UUID.randomUUID();
        checkpointer.save(runId, "node", "v1".getBytes(StandardCharsets.UTF_8));
        checkpointer.save(runId, "node", "v2".getBytes(StandardCharsets.UTF_8));
        String loaded = new String(checkpointer.load(runId, "node").orElseThrow(), StandardCharsets.UTF_8);
        assertThat(loaded).isEqualTo("v2");
    }
}
