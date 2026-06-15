package com.bank.financial.wealth;

import com.bank.financial.wealth.CustomerProfile.AssetTier;

/**
 * A wealth product on the bank's shelf.
 *
 * @param risk     product risk rating (中国监管:R1–R5);{@code null} for credit
 *                 products (短期贷款), which are not subject to investment suitability
 * @param minTier  minimum service tier eligible to be offered this product
 *                 (MASS = public shelf;PRIVATE/FAMILY_OFFICE = RM-only 私募/信托)
 */
public record Product(
        String id, String name, Category category, RiskLevel risk,
        Integer termDays, String indicativeYield, long minAmountCny, AssetTier minTier) {

    public boolean isCredit() {
        return category == Category.SHORT_LOAN;
    }

    /** Offered only through a relationship manager (not self-service mobile). */
    public boolean isPrivateOnly() {
        return minTier != AssetTier.MASS;
    }

    public enum Category {
        SHORT_TERM_WEALTH("短期理财"), MUTUAL_FUND("公募基金"), GOLD("黄金"),
        SHORT_LOAN("短期贷款"), PRIVATE_FUND("私募基金"), FAMILY_TRUST("家族信托");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    /** 产品风险等级 (China regulatory R1–R5). */
    public enum RiskLevel {
        R1(1), R2(2), R3(3), R4(4), R5(5);

        public final int level;

        RiskLevel(int level) {
            this.level = level;
        }
    }
}
