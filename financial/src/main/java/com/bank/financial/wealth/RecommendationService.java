package com.bank.financial.wealth;

import com.bank.financial.wealth.CustomerProfile.AssetTier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The core retail-wealth asset: turns a customer profile into a recommendation
 * list under three hard rules, enforced in CODE (the LLM never sees products it
 * may not offer):
 *
 * <ol>
 *   <li><b>投资者适当性</b> — a product's risk Rn may be offered only if
 *       {@code n ≤ } the customer's risk-tolerance Cn (credit products excluded).</li>
 *   <li><b>资产分层</b> — a product's {@code minTier} must be ≤ the customer's tier;
 *       私募/家族信托 are PRIVATE/FAMILY_OFFICE only.</li>
 *   <li><b>渠道</b> — the self-service mobile channel offers only the public shelf;
 *       private products are surfaced only on the relationship-manager channel.</li>
 * </ol>
 *
 * 千人千面 personalization then ranks the eligible set by the customer's profile.
 */
public final class RecommendationService {

    private RecommendationService() {
    }

    public record Recommendation(List<Product> products, boolean privateEligible, String rmNote) {
    }

    /**
     * @param rmChannel true on the relationship-manager copilot (may include private
     *                  products the client is eligible for);false on self-service mobile
     */
    public static Recommendation recommend(CustomerProfile c, Product.Category filter, boolean rmChannel) {
        List<Product> eligible = new ArrayList<>();
        for (Product p : WealthData.SHELF) {
            if (filter != null && p.category() != filter) {
                continue;
            }
            if (!suitable(c, p)) {            // rule 1: 适当性
                continue;
            }
            if (!tierAllows(c, p)) {          // rule 2: 资产分层
                continue;
            }
            if (!rmChannel && p.isPrivateOnly()) { // rule 3: 渠道(自助不直售私募/信托)
                continue;
            }
            eligible.add(p);
        }
        eligible.sort(rankFor(c));

        boolean privateEligible = c.isPrivateEligible();
        String rmNote = null;
        if (privateEligible && !rmChannel) {
            rmNote = "客户符合" + c.tier().label + "资格,私募/家族信托等专属产品请通过专属客户经理("
                    + (c.rmName() == null ? "待分配" : c.rmName()) + ")办理,本渠道不直接销售。";
        }
        return new Recommendation(List.copyOf(eligible), privateEligible, rmNote);
    }

    /** 适当性:产品风险等级不得高于客户风险承受能力;信贷类不参与该匹配. */
    public static boolean suitable(CustomerProfile c, Product p) {
        return p.isCredit() || p.risk() == null || p.risk().level <= c.risk().level;
    }

    private static boolean tierAllows(CustomerProfile c, Product p) {
        AssetTier need = p.minTier();
        return need == null || need.rank <= c.tier().rank;
    }

    /** Personalization: conservative tags push lower risk first; growth tags allow higher within the suitable set. */
    private static Comparator<Product> rankFor(CustomerProfile c) {
        boolean growth = c.tags() != null
                && c.tags().stream().anyMatch(t -> t.contains("成长") || t.contains("波动") || t.contains("增值"));
        Comparator<Product> byRisk = Comparator.comparingInt(p -> p.risk() == null ? 0 : p.risk().level);
        return growth ? byRisk.reversed() : byRisk;
    }
}
