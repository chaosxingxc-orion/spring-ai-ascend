package com.bank.financial.wealth;

import java.util.List;

/**
 * A retail customer's wealth profile — the basis for 千人千面 personalization and
 * for investor-suitability matching. In production this comes from the bank's
 * customer/CRM systems; here it is supplied by an in-memory directory for demo.
 *
 * @param risk    the customer's assessed risk-tolerance level (中国监管:C1–C5)
 * @param aumCny  assets under management (CNY) — drives the service tier
 * @param tags    profile signals for personalization (life stage, goals, preferences)
 * @param rmName  assigned relationship manager (private-banking clients), else null
 */
public record CustomerProfile(
        String id, String name, CustomerRiskLevel risk, long aumCny, List<String> tags, String rmName) {

    /** Service tier by AUM: ≥1000万 家族办公室;≥500万 私人银行;否则 大众客户. */
    public AssetTier tier() {
        if (aumCny >= 10_000_000L) {
            return AssetTier.FAMILY_OFFICE;
        }
        if (aumCny >= 5_000_000L) {
            return AssetTier.PRIVATE;
        }
        return AssetTier.MASS;
    }

    /** Private-banking eligible (has / should have a relationship manager). */
    public boolean isPrivateEligible() {
        return tier() != AssetTier.MASS;
    }

    /** 投资者风险承受能力等级 (China regulatory C1–C5). */
    public enum CustomerRiskLevel {
        C1("保守型", 1), C2("谨慎型", 2), C3("稳健型", 3), C4("积极型", 4), C5("进取型", 5);

        public final String label;
        public final int level;

        CustomerRiskLevel(String label, int level) {
            this.label = label;
            this.level = level;
        }
    }

    /** Service tier by asset scale. */
    public enum AssetTier {
        MASS("大众客户", 0), PRIVATE("私人银行", 1), FAMILY_OFFICE("家族办公室", 2);

        public final String label;
        public final int rank;

        AssetTier(String label, int rank) {
            this.label = label;
            this.rank = rank;
        }
    }
}
