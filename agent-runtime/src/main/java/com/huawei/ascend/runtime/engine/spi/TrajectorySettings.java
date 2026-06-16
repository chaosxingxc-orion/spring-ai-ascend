package com.huawei.ascend.runtime.engine.spi;

import java.util.regex.Pattern;

/**
 * Resolved per-invocation trajectory settings handed to a {@link TrajectorySource}.
 * The runtime computes these from global configuration plus any per-request override
 * before opening the trajectory, so the adapter base never reads configuration itself.
 * When enabled, every supported kind is emitted with masked and truncated payloads,
 * unless {@code sampleRate} drops the whole invocation (head sampling). {@code redactor}
 * applies value-level content redaction on top of key-name masking; {@code costCalculator}
 * fills model-call {@code provider}/{@code costMicros}. Both default to no-ops.
 * {@code payloadRefStore} externalizes oversized payloads (exceeding {@code truncateChars})
 * to a write-only store and records an opaque reference instead of a truncated excerpt;
 * null means normal truncation applies.
 */
public record TrajectorySettings(boolean enabled, Pattern maskKeyPattern, int truncateChars, double sampleRate,
        Redactor redactor, CostCalculator costCalculator, PayloadRefStore payloadRefStore) {

    public TrajectorySettings {
        if (redactor == null) {
            redactor = Redactor.NONE;
        }
        if (costCalculator == null) {
            costCalculator = CostCalculator.NONE;
        }
        // payloadRefStore: null is the intentional "disabled" sentinel — no default no-op needed.
    }

    /** Full-sampling, no value-redaction/cost/payload-ref convenience — keeps pre-sampling call sites unchanged. */
    public TrajectorySettings(boolean enabled, Pattern maskKeyPattern, int truncateChars) {
        this(enabled, maskKeyPattern, truncateChars, 1.0, Redactor.NONE, CostCalculator.NONE, null);
    }

    /** No value-redaction/cost/payload-ref convenience — keeps pre-redaction call sites unchanged. */
    public TrajectorySettings(boolean enabled, Pattern maskKeyPattern, int truncateChars, double sampleRate) {
        this(enabled, maskKeyPattern, truncateChars, sampleRate, Redactor.NONE, CostCalculator.NONE, null);
    }

    /** Full-settings convenience without payload-ref — keeps pre-Wave8 call sites unchanged. */
    public TrajectorySettings(boolean enabled, Pattern maskKeyPattern, int truncateChars, double sampleRate,
            Redactor redactor, CostCalculator costCalculator) {
        this(enabled, maskKeyPattern, truncateChars, sampleRate, redactor, costCalculator, null);
    }

    public static TrajectorySettings off() {
        return new TrajectorySettings(false, null, 0, 1.0);
    }
}
