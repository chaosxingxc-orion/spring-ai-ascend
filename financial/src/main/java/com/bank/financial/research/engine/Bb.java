package com.bank.financial.research.engine;

import java.math.BigDecimal;

/**
 * Blackboard key constants and canonical number formatting. The blackboard is the
 * single source of truth for a report run: every figure is written under one of
 * these keys by exactly one owning sub-agent, and every other agent reads it from
 * here instead of re-deriving it. {@link #fmt(double)} guarantees one double
 * always renders to one string, so the writer's prose and the consistency checker
 * agree on what "the number" is.
 */
public final class Bb {

    private Bb() {
    }

    // ── House view (owned by lead-manager) ────────────────────────────────────
    public static final String COMPANY = "company";
    public static final String CURRENCY = "currency";
    public static final String THESIS = "thesis";
    public static final String RATING = "rating";
    public static final String PRICE_TARGET = "priceTarget";
    public static final String CURRENT_PRICE = "currentPrice";
    public static final String UPSIDE_PCT = "upsidePct";

    // ── Estimates / model (owned by quant-model) ──────────────────────────────
    public static final String REVENUE_FY1 = "revenue.FY1";
    public static final String EPS_FY1 = "eps.FY1";
    public static final String FCF_FY1 = "fcf.FY1";
    public static final String TREND_BLENDED = "trend.blended";
    public static final String TREND_CONVERGENT = "trend.convergent";
    public static final String GROWTH = "growth.assumption";

    // ── Valuation (owned by valuation) ────────────────────────────────────────
    public static final String DCF_PER_SHARE = "dcf.perShare";
    public static final String DCF_TERMINAL_WEIGHT = "dcf.terminalWeight";
    public static final String COMPS_MEDIAN = "comps.median";
    public static final String COMPS_LOW = "comps.low";
    public static final String COMPS_HIGH = "comps.high";
    public static final String CONVERGENCE_VERDICT = "convergence.verdict";
    public static final String CONVERGENCE_BLENDED = "convergence.blended";
    public static final String CONVERGENCE_DISPERSION = "convergence.dispersion";
    public static final String SCENARIO_BULL = "scenario.bull";
    public static final String SCENARIO_BASE = "scenario.base";
    public static final String SCENARIO_BEAR = "scenario.bear";
    public static final String SCENARIO_EXPECTED = "scenario.expected";

    // ── Surprise / external impact (owned by quant-model / sector-macro) ───────
    public static final String SUE = "sue";
    public static final String SUE_CLASS = "sue.class";
    public static final String REVENUE_IMPACT_PCT = "impact.revenuePct";
    public static final String EPS_IMPACT = "impact.eps";

    // ── Narrative / structure ─────────────────────────────────────────────────
    public static final String OUTLINE = "outline";
    public static final String SECTION_PREFIX = "section.";

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
