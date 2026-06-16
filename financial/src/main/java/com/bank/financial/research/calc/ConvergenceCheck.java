package com.bank.financial.research.calc;

import java.util.List;

/**
 * Triangulation convergence across independent valuation methods. Sell-side
 * practice is that no single method is definitive: the highest-conviction value
 * is the zone where DCF and comparables agree, and a sharp divergence is a
 * trigger to reconcile (investigate the growth/risk/sentiment gap) rather than
 * to silently average.
 *
 * <p>This produces a machine verdict the orchestrator acts on: CONVERGENT ranges
 * yield a blended target; DIVERGENT ranges are routed back to the lead/manager
 * agent for an explicit reconciliation step before a target is set.
 */
public final class ConvergenceCheck {

    private ConvergenceCheck() {
    }

    /** Default: midpoints within 15% are convergent; beyond 30% is divergent. */
    public static ConvergenceResult of(List<ValueRange> methodRanges) {
        return of(methodRanges, 0.15, 0.30);
    }

    /**
     * @param methodRanges per-method per-share ranges (e.g. DCF range, comps range)
     * @param convergentMaxSpread midpoint dispersion at/below which methods are CONVERGENT
     * @param divergentMinSpread  midpoint dispersion at/above which methods are DIVERGENT
     */
    public static ConvergenceResult of(List<ValueRange> methodRanges,
            double convergentMaxSpread, double divergentMinSpread) {
        if (methodRanges == null || methodRanges.size() < 2) {
            throw new IllegalArgumentException("convergence needs at least two method ranges");
        }

        double minMid = methodRanges.stream().mapToDouble(ValueRange::mid).min().orElseThrow();
        double maxMid = methodRanges.stream().mapToDouble(ValueRange::mid).max().orElseThrow();
        double meanMid = methodRanges.stream().mapToDouble(ValueRange::mid).average().orElseThrow();

        // Dispersion of method midpoints, normalised by the mean midpoint.
        double dispersion = meanMid == 0 ? 0 : (maxMid - minMid) / Math.abs(meanMid);

        // Overlap zone: intersection of all ranges, if any.
        double overlapLow = methodRanges.stream().mapToDouble(ValueRange::low).max().orElseThrow();
        double overlapHigh = methodRanges.stream().mapToDouble(ValueRange::high).min().orElseThrow();
        boolean hasOverlap = overlapHigh >= overlapLow;

        Verdict verdict;
        if (dispersion <= convergentMaxSpread && hasOverlap) {
            verdict = Verdict.CONVERGENT;
        } else if (dispersion >= divergentMinSpread || !hasOverlap) {
            verdict = Verdict.DIVERGENT;
        } else {
            verdict = Verdict.PARTIAL;
        }

        // Blended point estimate: midpoint of the overlap zone when it exists,
        // else the mean of method midpoints (the honest "no clean overlap" fallback).
        double blended = hasOverlap ? (overlapLow + overlapHigh) / 2.0 : meanMid;

        ValueRange overlap = hasOverlap
                ? new ValueRange(Calc.money(overlapLow), Calc.money(blended), Calc.money(overlapHigh))
                : null;

        return new ConvergenceResult(
                verdict, Calc.rate(dispersion), Calc.money(blended), overlap,
                Calc.money(minMid), Calc.money(maxMid));
    }

    public enum Verdict {
        /** Methods agree; blended target is high-conviction. */
        CONVERGENT,
        /** Methods partly disagree; usable but flag the spread. */
        PARTIAL,
        /** Methods disagree sharply; require manager reconciliation before a target. */
        DIVERGENT
    }

    /**
     * @param overlapZone the intersection range, or {@code null} when methods do not overlap
     */
    public record ConvergenceResult(
            Verdict verdict,
            double dispersion,
            double blendedPerShare,
            ValueRange overlapZone,
            double lowMid,
            double highMid) {

        public boolean requiresReconciliation() {
            return verdict == Verdict.DIVERGENT;
        }
    }
}
