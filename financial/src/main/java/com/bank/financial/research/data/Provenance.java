package com.bank.financial.research.data;

/**
 * Where a datum came from and how fresh it is. Provenance travels with every
 * value into the blackboard so the report can disclose sources (FINRA-style) and
 * the critic/compliance agents can reason about staleness and reliability.
 *
 * @param source        human-readable source label (e.g. "I/B/E/S", "10-K FY24", "exchange feed")
 * @param type          which tier of the data stack this came from
 * @param asOfEpochMs   the timestamp the datum is valid as-of (for freshness windows)
 * @param reference     a citation handle (URL, filing id, transcript id) — may be empty
 * @param confidence    [0,1] source confidence (analyst-assigned or source-default)
 */
public record Provenance(String source, SourceType type, long asOfEpochMs, String reference, double confidence) {

    public Provenance {
        source = source == null || source.isBlank() ? "unknown" : source;
        reference = reference == null ? "" : reference;
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be in [0,1] (was " + confidence + ")");
        }
    }

    public static Provenance of(String source, SourceType type, long asOfEpochMs) {
        return new Provenance(source, type, asOfEpochMs, "", 0.8);
    }

    /** Age in milliseconds relative to {@code nowMs} (0 if the datum is from the future). */
    public long ageMs(long nowMs) {
        return Math.max(0L, nowMs - asOfEpochMs);
    }

    /** A compact disclosure string for the report's sources section. */
    public String cite() {
        return reference.isEmpty() ? source : source + " (" + reference + ")";
    }
}
