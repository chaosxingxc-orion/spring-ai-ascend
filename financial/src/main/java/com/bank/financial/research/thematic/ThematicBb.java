package com.bank.financial.research.thematic;

/**
 * Blackboard keys for the thematic / sector-strategy pipeline. As in the equity
 * engine, each figure is written once by an owning agent and read by the others;
 * sub-sector scores/ratings are computed (by the sector-impact model) and the
 * writer only narrates them.
 */
public final class ThematicBb {

    private ThematicBb() {
    }

    public static final String THEME = "theme";
    public static final String OVERALL_RATING = "overall.rating";
    public static final String OVERALL_SCORE = "overall.score";
    public static final String THESIS = "thesis";
    public static final String OUTLINE = "outline";
    public static final String SECTION_PREFIX = "section.";
    public static final String FACTORS_SUMMARY = "factors.summary";

    public static final String ALLOC_OVERWEIGHT = "alloc.overweight";
    public static final String ALLOC_NEUTRAL = "alloc.neutral";
    public static final String ALLOC_UNDERWEIGHT = "alloc.underweight";

    /** Canonical equity-report outline for a sector-strategy note. */
    public static final String OUTLINE_DEFAULT =
            "summary,macro_global,macro_china,markets,transmission,allocation,risks";

    public static String sectorScoreKey(String sector) {
        return "sector.score::" + sector;
    }

    public static String sectorRatingKey(String sector) {
        return "sector.rating::" + sector;
    }

    /** Map a machine rating (enum name) to its Chinese display label. */
    public static String ratingLabel(String name) {
        return switch (name == null ? "" : name) {
            case "OVERWEIGHT" -> "超配 (Overweight)";
            case "UNDERWEIGHT" -> "低配 (Underweight)";
            default -> "标配 (Neutral)";
        };
    }

    public static String titleOf(String id) {
        return switch (id) {
            case "summary" -> "摘要与核心观点";
            case "macro_global" -> "全球宏观:地缘与美联储";
            case "macro_china" -> "中国宏观与政策";
            case "markets" -> "中美股市与流动性";
            case "transmission" -> "传导分析:宏观因子向子板块映射";
            case "allocation" -> "配置建议";
            case "risks" -> "风险提示";
            default -> id;
        };
    }
}
