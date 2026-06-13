package com.huawei.ascend.runtime.engine.spi;

import java.util.regex.Pattern;

/**
 * Resolved per-invocation trajectory settings handed to a {@link TrajectorySource}.
 * The runtime computes these from global configuration plus any per-request override
 * before opening the trajectory, so the adapter base never reads configuration itself.
 * When enabled, every supported kind is emitted with masked and truncated payloads.
 *
 * <p>{@code sampleRate} is a per-invocation Bernoulli probability in [0.0, 1.0].
 * {@code RUN_START}, {@code RUN_END}, and {@code ERROR} are always emitted regardless
 * of the sample decision; all other kinds are dropped on un-kept invocations.
 *
 * <p>{@code redactor} is optional. When {@code null} the emitter falls back to the
 * built-in key-name masking via {@link TrajectoryMasking#mask}, preserving byte-identical
 * default behaviour. A deployment supplies a {@link Redactor} bean to opt into
 * value-based recognition (card numbers, national IDs, GPS coordinate pairs, etc.).
 */
public record TrajectorySettings(boolean enabled, Pattern maskKeyPattern, int truncateChars,
        double sampleRate, Redactor redactor) {

    public static TrajectorySettings off() {
        return new TrajectorySettings(false, null, 0, 1.0, null);
    }

    /**
     * Convenience factory for fully-sampled settings ({@code sampleRate = 1.0}) with no
     * custom redactor. Use this in tests and any call site that does not need per-invocation
     * sampling or value-based recognition.
     */
    public static TrajectorySettings basic(boolean enabled, Pattern maskKeyPattern, int truncateChars) {
        return new TrajectorySettings(enabled, maskKeyPattern, truncateChars, 1.0, null);
    }
}
