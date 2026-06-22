package com.bank.financial.research.calc;

/**
 * Small numeric helpers shared by the valuation models. Kept deliberately tiny
 * and dependency-free so the financial math stays auditable.
 *
 * <p>The engine uses {@code double} for analytical modelling (valuation, not
 * ledger accounting); every public result is rounded explicitly at the boundary
 * so two runs on the same inputs produce byte-identical numbers — a hard
 * requirement for the report's internal consistency.
 */
public final class Calc {

    private Calc() {
    }

    /** Round to {@code places} decimals, half-up, NaN/Inf → 0. */
    public static double round(double v, int places) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0.0;
        }
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }

    /** Round to 2 decimals (money / per-share). */
    public static double money(double v) {
        return round(v, 2);
    }

    /** Round to 4 decimals (rates / ratios). */
    public static double rate(double v) {
        return round(v, 4);
    }

    /** Percent change a→b as a fraction (b/a − 1); 0 when a is 0. */
    public static double pctChange(double from, double to) {
        if (from == 0.0) {
            return 0.0;
        }
        return (to - from) / Math.abs(from);
    }

    /** Compound a base value by {@code rate} for {@code periods} periods. */
    public static double compound(double base, double rate, int periods) {
        return base * Math.pow(1.0 + rate, periods);
    }

    /** Require a strictly-positive argument (guards divide-by-zero in models). */
    public static double requirePositive(String name, double v) {
        if (!(v > 0.0)) {
            throw new IllegalArgumentException(name + " must be > 0 (was " + v + ")");
        }
        return v;
    }

    /** Require a finite argument. */
    public static double requireFinite(String name, double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException(name + " must be finite (was " + v + ")");
        }
        return v;
    }
}
