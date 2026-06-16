package com.huawei.ascend.runtime.engine.spi;

/**
 * Value-level trajectory redaction, orthogonal to the key-name {@link TrajectoryMasking}: it
 * inspects the <i>content</i> of a payload (already key-masked and truncated) and redacts
 * sensitive values a key name could not catch — a credit-card number in free text, GPS
 * coordinates, a national id. Applied by the runtime to {@code args}/{@code result}/
 * {@code reasoning} just before an event is published, so every rail sees redacted content.
 *
 * <p>Implementations must be pure and side-effect-free, recurse into {@code Map}/{@code List}
 * structures, and never throw — but the runtime still guards each call fail-closed (a thrown
 * redactor degrades the payload to a redaction marker rather than leaking it).
 */
@FunctionalInterface
public interface Redactor {

    /** The marker substituted for redacted content. */
    String REDACTED = "***";

    /** A no-op redactor: returns the value unchanged. The default when value-level redaction is off. */
    Redactor NONE = value -> value;

    Object redact(Object value);
}
