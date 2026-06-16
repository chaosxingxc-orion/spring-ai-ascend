package com.bank.financial.research.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Freshness windows + latest-wins de-duplication. Borrowed from the SUE practice
 * of using only estimates within a recent window and the latest figure per
 * analyst, this prevents stale or double-counted inputs from contaminating the
 * report. A datum older than {@code maxAgeMs} is flagged stale (not silently
 * dropped — the report degrades transparently).
 */
public final class FreshnessPolicy {

    private final long maxAgeMs;

    public FreshnessPolicy(long maxAgeMs) {
        if (maxAgeMs <= 0) {
            throw new IllegalArgumentException("maxAgeMs must be > 0");
        }
        this.maxAgeMs = maxAgeMs;
    }

    /** Default policy: 90 days, mirroring the ~90-day estimate window in SUE. */
    public static FreshnessPolicy days(int days) {
        return new FreshnessPolicy((long) days * 24 * 60 * 60 * 1000);
    }

    public boolean isFresh(Provenance p, long nowMs) {
        return p.ageMs(nowMs) <= maxAgeMs;
    }

    public long maxAgeMs() {
        return maxAgeMs;
    }

    /**
     * Latest-wins dedup: given several provenanced values keyed by name, keep only
     * the one with the highest {@code asOf} per key. Returns insertion-ordered.
     */
    public static <V> Map<String, Provenanced<V>> latestWins(List<Provenanced<V>> items) {
        Map<String, Provenanced<V>> best = new LinkedHashMap<>();
        for (Provenanced<V> it : items) {
            Provenanced<V> prev = best.get(it.key());
            if (prev == null || it.provenance().asOfEpochMs() > prev.provenance().asOfEpochMs()) {
                best.put(it.key(), it);
            }
        }
        return best;
    }

    /** Collect human-readable stale warnings for a batch of provenanced values. */
    public List<String> staleWarnings(List<Provenanced<?>> items, long nowMs) {
        List<String> warnings = new ArrayList<>();
        for (Provenanced<?> it : items) {
            if (!isFresh(it.provenance(), nowMs)) {
                long ageDays = it.provenance().ageMs(nowMs) / (24L * 60 * 60 * 1000);
                warnings.add(it.key() + " is stale (" + ageDays + "d old, source=" + it.provenance().source() + ")");
            }
        }
        return warnings;
    }

    /** A value paired with its provenance and a dedup key. */
    public record Provenanced<V>(String key, V value, Provenance provenance) {
    }
}
