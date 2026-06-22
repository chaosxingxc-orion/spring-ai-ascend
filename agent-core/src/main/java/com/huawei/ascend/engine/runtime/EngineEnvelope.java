package com.huawei.ascend.engine.runtime;

import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;

import java.util.Map;
import java.util.Objects;

/**
 * Lightweight metadata + payload envelope for a heterogeneous execution engine.
 *
 * <p>The schema authority is {@code docs/contracts/engine-envelope.v1.yaml}.
 * This Java record mirrors the schema and validates required fields on
 * construction. Optional fields default to safe empty values.
 *
 * <p>The envelope is intentionally SHALLOW (proposal §3.2 of 2026-05-15):
 * it carries metadata for routing / governance / observability, NOT a
 * universal DSL. The {@code payload} is opaque to the envelope and validated
 * by the matching {@link com.huawei.ascend.engine.spi.ExecutorAdapter}
 * resolved by {@link EngineRegistry}.
 *
 * <p>W2.x Phase 1: only {@code name}, {@code engineType}, and {@code payload}
 * are required. {@code identifier} / {@code version} / {@code owner} /
 * {@code engineVersion} / {@code compatibility} are optional pending W2
 * persistence backfill. {@code runtimeHints} and {@code observabilityHints}
 * default to empty maps.
 *
 * <p>Authority: ADR-0072; CLAUDE.md Rule 43 (Engine Envelope Single Authority).
 */
public record EngineEnvelope(
        String name,                          // kebab-case stable name; required
        String identifier,                    // optional uri or stable id
        String version,                       // optional semver of THIS envelope instance
        String owner,                         // optional owner
        String engineType,                    // required; MUST match a known_engines[].id
        String engineVersion,                 // optional semver of the targeted engine
        String compatibility,                 // optional semver-range
        Map<String, Object> runtimeHints,     // empty map if absent
        Map<String, Object> observabilityHints, // empty map if absent
        ExecutorDefinition payload            // required; opaque to the envelope
) {
    public EngineEnvelope {
        Objects.requireNonNull(name, "name is required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(engineType, "engineType is required");
        if (engineType.isBlank()) {
            throw new IllegalArgumentException("engineType must not be blank");
        }
        Objects.requireNonNull(payload, "payload is required");
        runtimeHints = runtimeHints == null ? Map.of() : Map.copyOf(runtimeHints);
        observabilityHints = observabilityHints == null ? Map.of() : Map.copyOf(observabilityHints);
    }

    /**
     * Convenience constructor for the common case: only name, engineType, payload.
     * All other fields default to null / empty.
     */
    public static EngineEnvelope of(String name, String engineType, ExecutorDefinition payload) {
        return new EngineEnvelope(name, null, null, null, engineType, null, null, Map.of(), Map.of(), payload);
    }
}
