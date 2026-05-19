package ascend.springai.engine.orchestration.spi;

import java.util.Optional;
import java.util.UUID;

/**
 * Layer-3 (tier-internal) SPI for suspend-point persistence. Pure Java — no Spring imports.
 *
 * <p>W0 dev: in-memory {@code ConcurrentHashMap} ({@code InMemoryCheckpointer}).
 * Single-threaded; checkpoint write and RunRepository save are sequentially atomic on the same
 * call stack — see {@code SyncOrchestrator.executeLoop} javadoc.
 *
 * <p>W2 Postgres: checkpoint bytes in {@code run_checkpoints} table (same DataSource as the
 * {@code runs} table). MUST be called inside the same {@code @Transactional} block as
 * {@code RunRepository.save(suspended)} to satisfy the suspension write atomicity contract
 * (ADR-0024). If the Checkpointer backend is non-DB (e.g. Redis), the transactional-outbox
 * pattern (ADR-0007) provides equivalent atomicity.
 *
 * <p>W4 Temporal: this SPI is bypassed entirely. {@code TemporalOrchestrator} does not call
 * {@code Checkpointer} — Temporal's workflow state machine is the durable record (ADR-0024).
 */
public interface Checkpointer {

    /**
     * Persist a serialised checkpoint for the given run and node.
     * Overwrites any previously stored value for the same (runId, nodeKey) pair.
     */
    void save(UUID runId, String nodeKey, byte[] payload);

    /**
     * Load the most recent checkpoint for the given run and node.
     * Returns empty if no checkpoint has been saved for this pair.
     */
    Optional<byte[]> load(UUID runId, String nodeKey);
}
