package com.bank.financial.research.fund;

/** Blackboard keys for the fund / FOF pipeline. Metrics are computed, then narrated. */
public final class FundBb {

    private FundBb() {
    }

    public static final String CODE = "fund.code";
    public static final String NAME = "fund.name";
    public static final String TYPE = "fund.type";
    public static final String NAV_POINTS = "nav.points";
    public static final String PERIODS_PER_YEAR = "nav.periodsPerYear";

    public static final String CUM_RETURN = "perf.cumReturn";
    public static final String ANN_RETURN = "perf.annReturn";
    public static final String ANN_VOL = "perf.annVol";
    public static final String SHARPE = "perf.sharpe";
    public static final String MAX_DD = "perf.maxDrawdown";
    public static final String CALMAR = "perf.calmar";
    public static final String BETA = "perf.beta";
    public static final String ALPHA = "perf.alpha";
    public static final String RISK_LEVEL = "risk.level";

    public static final String OVERALL_RATING = "overall.rating";
    public static final String THESIS = "thesis";
    public static final String OUTLINE = "outline";
    public static final String SECTION_PREFIX = "section.";

    public static final String OUTLINE_DEFAULT = "summary,performance,risk,suitability";

    public static String ratingLabel(String name) {
        return switch (name == null ? "" : name) {
            case "PREFERRED" -> "优选 (Preferred)";
            case "AVOID" -> "回避 (Avoid)";
            default -> "中性 (Neutral)";
        };
    }

    public static String titleOf(String id) {
        return switch (id) {
            case "summary" -> "摘要与评级";
            case "performance" -> "业绩表现";
            case "risk" -> "风险特征";
            case "suitability" -> "适配与配置建议";
            default -> id;
        };
    }
}
