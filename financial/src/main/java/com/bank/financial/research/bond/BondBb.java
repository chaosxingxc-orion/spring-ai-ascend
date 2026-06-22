package com.bank.financial.research.bond;

/** Blackboard keys for the bond / fixed-income pipeline. Metrics are computed, then narrated. */
public final class BondBb {

    private BondBb() {
    }

    public static final String CODE = "bond.code";
    public static final String NAME = "bond.name";
    public static final String ISSUER = "bond.issuer";
    public static final String RATING = "bond.rating";
    public static final String FACE_VALUE = "bond.faceValue";
    public static final String COUPON_RATE = "bond.couponRate";
    public static final String PERIODS = "bond.periodsRemaining";
    public static final String MARKET_PRICE = "bond.marketPrice";
    public static final String BENCHMARK_YIELD = "bond.benchmarkYield";

    public static final String YTM = "rates.ytm";
    public static final String CURRENT_YIELD = "rates.currentYield";
    public static final String MACAULAY = "rates.macaulay";
    public static final String MODIFIED = "rates.modified";
    public static final String CONVEXITY = "rates.convexity";

    public static final String CREDIT_SPREAD = "credit.spread";
    public static final String CREDIT_LEVEL = "credit.level";

    public static final String STANCE = "overall.stance";
    public static final String THESIS = "thesis";
    public static final String OUTLINE = "outline";
    public static final String SECTION_PREFIX = "section.";

    public static final String OUTLINE_DEFAULT = "summary,yield,credit,allocation";

    public static String stanceLabel(String name) {
        return switch (name == null ? "" : name) {
            case "EXTEND" -> "增持/拉久期 (Extend)";
            case "SHORTEN" -> "回避/缩久期 (Shorten)";
            default -> "标配 (Neutral)";
        };
    }

    public static String titleOf(String id) {
        return switch (id) {
            case "summary" -> "摘要与配置评级";
            case "yield" -> "收益率与久期";
            case "credit" -> "信用与利差";
            case "allocation" -> "配置建议";
            default -> id;
        };
    }
}
