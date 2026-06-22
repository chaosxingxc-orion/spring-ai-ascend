package com.bank.financial.research.macro;

/**
 * Blackboard keys for the macro & policy pipeline. The indicator readings and the
 * computed stance (growth/inflation/activity/liquidity scores + composite + asset
 * tilt) are written once by their owning agent and narrated by the writer.
 */
public final class MacroBb {

    private MacroBb() {
    }

    public static final String REGION = "region";
    public static final String OUTLINE = "outline";
    public static final String SECTION_PREFIX = "section.";
    public static final String INDICATOR_PREFIX = "ind.";

    public static final String THESIS = "thesis";
    public static final String COMPOSITE = "macro.composite";
    public static final String SCORE_GROWTH = "score.growth";
    public static final String SCORE_INFLATION = "score.inflation";
    public static final String SCORE_ACTIVITY = "score.activity";
    public static final String SCORE_LIQUIDITY = "score.liquidity";
    public static final String ASSET_TILT = "asset.tilt";

    /** Default section order (top-down macro note). */
    public static final String OUTLINE_DEFAULT =
            "summary,growth,inflation,monetary,activity,allocation,risks";

    public static String indicatorKey(String key) {
        return INDICATOR_PREFIX + key;
    }

    public static String tiltLabel(String name) {
        return switch (name == null ? "" : name) {
            case "EQUITY_FAVOURED" -> "权益占优 (Equity-favoured)";
            case "BONDS_FAVOURED" -> "债券占优 (Bonds-favoured)";
            default -> "中性 (Neutral)";
        };
    }

    public static String titleOf(String id) {
        return switch (id) {
            case "summary" -> "摘要与宏观判断";
            case "growth" -> "经济增长";
            case "inflation" -> "通胀与物价";
            case "monetary" -> "货币与流动性";
            case "activity" -> "景气与高频(PMI)";
            case "allocation" -> "大类资产配置含义";
            case "risks" -> "风险提示";
            default -> id;
        };
    }
}
