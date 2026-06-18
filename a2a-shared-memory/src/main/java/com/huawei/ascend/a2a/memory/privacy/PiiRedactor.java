package com.huawei.ascend.a2a.memory.privacy;

/**
 * Strips user PII from text before it is written to the tenant-shared, cross-run
 * experience layer (the a2a-shared-memory design decision: experience is PII-stripped). The kit ships a
 * best-effort {@link DefaultPiiRedactor}; the closed engine may substitute a
 * stronger detector. This is a hard requirement, not advisory — experience is
 * shared across a tenant's collaborations and must never carry a customer's
 * private data.
 */
public interface PiiRedactor {

    /** Return {@code text} with detected PII replaced by a redaction marker. */
    String redact(String text);

    /** A no-op redactor — ONLY for tests that assert raw passthrough; never in production. */
    PiiRedactor NONE = text -> text;
}
