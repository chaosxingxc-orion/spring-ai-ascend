package com.huawei.ascend.runtime.engine.spi;

/**
 * SPI for field-level payload redaction before a trajectory event leaves the process.
 *
 * <p>A deployment registers a bean that implements this interface to opt into value-based
 * recognition (e.g. Luhn-valid card numbers, national-id patterns, GPS coordinate pairs)
 * in addition to the built-in key-name redaction. When no bean is registered the runtime
 * falls back to {@link TrajectoryMasking#mask}, preserving byte-identical default behaviour.
 *
 * <p>Implementations must be side-effect-free and cheap — {@code redact} is called once
 * per masked slot per event on the emit thread.
 *
 * @see ValueRecognizingRedactor
 */
public interface Redactor {

    /**
     * Returns a redacted form of {@code value}.
     *
     * @param eventKind  the draft kind name (e.g. {@code "MODEL_CALL_END"})
     * @param fieldPath  the slot being redacted ({@code "args"}, {@code "result"},
     *                   {@code "reasoning"}, or {@code "error.message"})
     * @param value      the whole payload for that slot — a {@code Map}, {@code List}, or scalar
     * @return the redacted payload; returning {@code value} unchanged is valid
     */
    Object redact(String eventKind, String fieldPath, Object value);
}
