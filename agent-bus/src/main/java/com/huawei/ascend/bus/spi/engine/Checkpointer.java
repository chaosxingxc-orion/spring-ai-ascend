package com.huawei.ascend.bus.spi.engine;

import java.util.Optional;
import java.util.UUID;

/**
 * Layer-3 (tier-internal) SPI for suspend-point persistence. Pure Java — no Spring imports.
 *
 * <p>This SPI is currently design-only: no implementation ships in this
 * repository yet (consult {@code docs/contracts/engine-port.v1.yaml} for the
 * contract status). The durability-tier plan it is designed against:
 *
 * <p>Dev tier: an in-memory map; checkpoint write and run save are sequentially
 * atomic on the same call stack.
 *
 * <p>Postgres tier: checkpoint bytes in a {@code run_checkpoints} table (same
 * DataSource as the {@code runs} table). Save MUST happen inside the same
 * {@code @Transactional} block as the suspended-run save to satisfy the
 * suspension write atomicity contract (ADR-0024). If the Checkpointer backend
 * is non-DB (e.g. Redis), the transactional-outbox pattern (ADR-0007) provides
 * equivalent atomicity.
 *
 * <p>Temporal tier: this SPI is bypassed entirely — the workflow state machine
 * is the durable record (ADR-0024).
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
