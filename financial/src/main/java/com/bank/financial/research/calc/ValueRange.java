package com.bank.financial.research.calc;

/**
 * A closed numeric range [low, high] with a representative midpoint. Used to
 * express a per-share value estimate from a single method, so the convergence
 * check can reason about overlap between methods (DCF vs comparables).
 */
public record ValueRange(double low, double mid, double high) {

    public ValueRange {
        if (low > high) {
            throw new IllegalArgumentException("low (" + low + ") must be <= high (" + high + ")");
        }
    }

    /** A symmetric range of ±{@code spreadPct} around a point estimate. */
    public static ValueRange around(double point, double spreadPct) {
        double lo = point * (1.0 - spreadPct);
        double hi = point * (1.0 + spreadPct);
        return new ValueRange(Calc.money(Math.min(lo, hi)), Calc.money(point), Calc.money(Math.max(lo, hi)));
    }

    /** A degenerate range collapsed to a single point. */
    public static ValueRange point(double point) {
        double p = Calc.money(point);
        return new ValueRange(p, p, p);
    }

    public double width() {
        return high - low;
    }

    /** True if {@code v} falls within [low, high]. */
    public boolean contains(double v) {
        return v >= low && v <= high;
    }

    /** Overlapping width with another range (0 when disjoint). */
    public double overlapWidth(ValueRange other) {
        double lo = Math.max(this.low, other.low);
        double hi = Math.min(this.high, other.high);
        return Math.max(0.0, hi - lo);
    }
}
