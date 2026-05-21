package com.huawei.ascend.evolve.online.spi;

import java.util.Map;
import java.util.Optional;

/**
 * Slow Track judge SPI.
 *
 * <p>Listens on AFTER_LLM_INVOCATION hook; produces an optional
 * ReflectionEnvelope when the trajectory critique yields a high-
 * confidence improvement signal.
 *
 * <p>System 2 / LLM-as-Judge: this is the cognitive coprocessor in the
 * Heaven-Earth Coordination cell of the Mode × Modality matrix
 * (Mode B + Online).
 *
 * <p>SPI surface only scaffold; reference impl (e.g.,
 * {@code OpenAiSlowTrackJudge} or {@code LocalLlmJudge}) lands as a
 * follow-up alongside ModelGateway routing decisions (separate ADR).
 *
 * <p>Wire contract for the produced ReflectionEnvelope:
 * {@code docs/contracts/reflection-envelope.v1.yaml}.
 */
public interface SlowTrackJudge {

    /**
     * Critique a completed trajectory and optionally produce a
     * reflection envelope for hot-patch delivery.
     *
     * @param trajectory the completed LLM invocation trajectory
     *                   (messages, tools called, tokens used, ...).
     * @return optional ReflectionEnvelope shape (as a Map mirroring
     *         the YAML contract); empty when confidence below
     *         threshold or no actionable insight found.
     */
    Optional<Map<String, Object>> critique(Map<String, Object> trajectory);
}
