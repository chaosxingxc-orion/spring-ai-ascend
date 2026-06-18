package com.bank.financial.wealth;

import com.bank.financial.wealth.CustomerProfile.AssetTier;
import com.bank.financial.wealth.CustomerProfile.CustomerRiskLevel;
import com.bank.financial.wealth.Product.Category;
import com.bank.financial.wealth.Product.RiskLevel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sample, in-memory product shelf and customer directory so the retail-wealth
 * agents run end to end with no backend. A bank replaces these with real
 * product-catalog and customer/CRM systems (swap the lookups for HTTP tools).
 */
public final class WealthData {

    private WealthData() {
    }

    /** The shelf: short-term wealth, mutual funds, gold, a credit product, plus RM-only private products. */
    public static final List<Product> SHELF = List.of(
            new Product("ST7", "7天通知理财", Category.SHORT_TERM_WEALTH, RiskLevel.R1, 7, "约1.8%", 10_000, AssetTier.MASS),
            new Product("ST30", "30天稳健理财", Category.SHORT_TERM_WEALTH, RiskLevel.R2, 30, "约2.6%", 10_000, AssetTier.MASS),
            new Product("MMF", "货币基金", Category.MUTUAL_FUND, RiskLevel.R1, null, "约1.9%", 1, AssetTier.MASS),
            new Product("BOND", "债券基金", Category.MUTUAL_FUND, RiskLevel.R2, null, "约3.5%", 100, AssetTier.MASS),
            new Product("MIX", "混合型基金", Category.MUTUAL_FUND, RiskLevel.R3, null, "波动,历史年化中枢约5%", 100, AssetTier.MASS),
            new Product("EQUITY", "股票型基金", Category.MUTUAL_FUND, RiskLevel.R4, null, "高波动", 100, AssetTier.MASS),
            new Product("GOLD", "积存金", Category.GOLD, RiskLevel.R3, null, "挂钩金价", 700, AssetTier.MASS),
            new Product("LOAN30", "30天消费贷", Category.SHORT_LOAN, null, 30, "日息约0.02%", 1_000, AssetTier.MASS),
            new Product("PEFOF", "稳健私募FOF", Category.PRIVATE_FUND, RiskLevel.R4, null, "中高波动", 1_000_000, AssetTier.PRIVATE),
            new Product("PEGROWTH", "成长私募基金", Category.PRIVATE_FUND, RiskLevel.R5, null, "高波动", 1_000_000, AssetTier.PRIVATE),
            new Product("TRUST", "家族信托服务", Category.FAMILY_TRUST, null, null, "定制化传承方案", 10_000_000, AssetTier.FAMILY_OFFICE));

    /** Sample customers across risk levels and asset tiers. */
    public static final Map<String, CustomerProfile> CUSTOMERS = buildCustomers();

    public static Product product(String id) {
        return SHELF.stream().filter(p -> p.id().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    public static CustomerProfile customer(String id) {
        return id == null ? null : CUSTOMERS.get(id);
    }

    /** One line per sample customer — printed by the playground so you know what to type. */
    public static String customerRoster() {
        StringBuilder sb = new StringBuilder();
        for (CustomerProfile c : CUSTOMERS.values()) {
            sb.append(String.format("    %-5s %s  %s/%s  %,d元  %s%s%n",
                    c.id(), c.name(), c.risk().name(), c.risk().label, c.aumCny(),
                    c.tier().label, c.rmName() == null ? "" : "  (" + c.rmName() + ")"));
        }
        return sb.toString();
    }

    private static Map<String, CustomerProfile> buildCustomers() {
        Map<String, CustomerProfile> m = new LinkedHashMap<>();
        put(m, new CustomerProfile("1001", "李雷", CustomerRiskLevel.C2, 80_000,
                List.of("年轻", "流动性偏好", "保本倾向"), null));
        put(m, new CustomerProfile("1002", "韩梅梅", CustomerRiskLevel.C4, 800_000,
                List.of("成长", "可承受波动", "定投"), null));
        put(m, new CustomerProfile("2001", "王总", CustomerRiskLevel.C4, 6_000_000,
                List.of("企业主", "资产配置", "稳健增值"), "私行客户经理-王经理"));
        put(m, new CustomerProfile("3001", "赵董", CustomerRiskLevel.C5, 12_000_000,
                List.of("超高净值", "财富传承", "税务规划"), "私行客户经理-李经理"));
        return m;
    }

    private static void put(Map<String, CustomerProfile> m, CustomerProfile c) {
        m.put(c.id(), c);
    }
}
