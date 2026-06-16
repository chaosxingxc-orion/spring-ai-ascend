package com.bank.financial.research.calc;

/**
 * Standardized Unexpected Earnings (SUE): how far an actual result lands from the
 * consensus estimate, normalised by the dispersion of analyst estimates. Stocks
 * move on beats/misses relative to consensus, so SUE is the canonical surprise
 * signal a report cites.
 *
 * <p>SUE = (actual − consensusMean) / consensusStdev.
 */
public final class EarningsSurprise {

    private EarningsSurprise() {
    }

    /** Classify a surprise; |SUE| below {@code inlineBand} (default 0.5σ) is in-line. */
    public static SurpriseResult sue(double actual, double consensusMean, double consensusStdev) {
        return sue(actual, consensusMean, consensusStdev, 0.5);
    }

    public static SurpriseResult sue(double actual, double consensusMean, double consensusStdev,
            double inlineBand) {
        double rawSurprisePct = Calc.pctChange(consensusMean, actual);
        double sue;
        if (consensusStdev <= 0) {
            // No dispersion data — fall back to a degenerate SUE of 0 but keep the raw %.
            sue = 0.0;
        } else {
            sue = (actual - consensusMean) / consensusStdev;
        }
        Surprise klass;
        if (sue >= inlineBand) {
            klass = Surprise.BEAT;
        } else if (sue <= -inlineBand) {
            klass = Surprise.MISS;
        } else {
            klass = Surprise.INLINE;
        }
        return new SurpriseResult(Calc.rate(sue), Calc.rate(rawSurprisePct), klass);
    }

    public enum Surprise {
        BEAT, INLINE, MISS
    }

    public record SurpriseResult(double sue, double rawSurprisePct, Surprise classification) {
    }
}
