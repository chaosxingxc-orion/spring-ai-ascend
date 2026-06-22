package com.bank.financial.research.engine;

import java.math.BigDecimal;

/**
 * Canonical number formatting shared across the report engines. {@link #fmt(double)}
 * guarantees one double always renders to one string, so a writer's prose and the
 * consistency checker agree on what "the number" is; {@link #pct(double)} renders a
 * fraction as a one-decimal percentage. (Each report type owns its own blackboard
 * key constants — {@code FundBb}, {@code BondBb}, {@code ThematicBb}.)
 */
public final class Bb {

    private Bb() {
    }

    /** Canonical, stable string form of a figure (so prose ↔ checker agree). */
    public static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "n/m";
        }
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    /** Format as a percentage with one decimal (value given as a fraction). */
    public static String pct(double fraction) {
        return fmt(Math.round(fraction * 1000.0) / 10.0) + "%";
    }
}
