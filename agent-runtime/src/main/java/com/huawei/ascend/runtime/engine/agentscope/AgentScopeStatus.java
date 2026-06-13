package com.huawei.ascend.runtime.engine.agentscope;

import java.util.Locale;
import java.util.Map;

/**
 * Canonical classification of AgentScope wire-status strings.
 * <p>
 * A single {@link #fromWire(String)} lookup replaces the three boolean
 * {@code isXxxStatus} predicates and makes previously-silent unknown
 * statuses visible via a caller-side WARN log.
 */
enum AgentScopeStatus {

    FAILURE,
    INTERRUPT,
    COMPLETED,
    /** Any non-blank status string not in the recognized set. */
    UNKNOWN;

    private static final Map<String, AgentScopeStatus> WIRE_MAP = Map.ofEntries(
            // failure
            Map.entry("error",     FAILURE),
            Map.entry("errored",   FAILURE),
            Map.entry("failed",    FAILURE),
            Map.entry("failure",   FAILURE),
            Map.entry("exception", FAILURE),
            // interrupt
            Map.entry("interrupt",      INTERRUPT),
            Map.entry("interrupted",    INTERRUPT),
            Map.entry("input_required", INTERRUPT),
            Map.entry("requires_input", INTERRUPT),
            Map.entry("human",          INTERRUPT),
            Map.entry("human_input",    INTERRUPT),
            // completed
            Map.entry("completed",  COMPLETED),
            Map.entry("complete",   COMPLETED),
            Map.entry("final",      COMPLETED),
            Map.entry("finished",   COMPLETED),
            Map.entry("done",       COMPLETED),
            Map.entry("success",    COMPLETED),
            Map.entry("succeeded",  COMPLETED)
    );

    /**
     * Maps a raw wire status string to its {@link AgentScopeStatus} category.
     * <p>
     * Null or blank inputs return {@link #UNKNOWN} without consuming a lookup.
     * An unrecognized non-blank string also returns {@link #UNKNOWN}; the
     * caller is responsible for logging a warning.
     *
     * @param raw the raw status value from the AgentScope event payload
     * @return the matching category, never {@code null}
     */
    static AgentScopeStatus fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return WIRE_MAP.getOrDefault(normalized, UNKNOWN);
    }
}
