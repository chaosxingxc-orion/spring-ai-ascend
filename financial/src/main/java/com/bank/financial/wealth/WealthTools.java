package com.bank.financial.wealth;

import com.bank.financial.kit.tool.LocalTool;
import com.bank.financial.kit.tool.Schemas;
import com.bank.financial.wealth.RecommendationService.Recommendation;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent tools over the retail-wealth domain. The recommendation tool enforces
 * suitability/tier/channel in {@link RecommendationService} — so the model can
 * only ever recommend products the tool returns, never something above the
 * customer's risk level. (Numbers come from here, not the model.)
 */
public final class WealthTools {

    private WealthTools() {
    }

    public static LocalFunction customerProfileTool() {
        return LocalTool.of("get_customer_profile",
                "查询客户画像:风险承受等级(C1-C5)、资产规模、服务分层、专属客户经理",
                Schemas.object().required("customerId", "string", "客户号").build(),
                inputs -> {
                    CustomerProfile c = WealthData.customer(str(inputs, "customerId"));
                    if (c == null) {
                        return Map.of("error", "未找到该客户");
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("customerId", c.id());
                    m.put("name", c.name());
                    m.put("riskLevel", c.risk().name() + "/" + c.risk().label);
                    m.put("aumCny", c.aumCny());
                    m.put("tier", c.tier().label);
                    m.put("privateEligible", c.isPrivateEligible());
                    m.put("relationshipManager", c.rmName());
                    m.put("tags", c.tags());
                    return m;
                });
    }

    /**
     * @param rmChannel true for the RM copilot (may surface eligible private products);
     *                  false for self-service mobile (public shelf only)
     */
    public static LocalFunction recommendTool(boolean rmChannel) {
        return LocalTool.of("recommend_products",
                "按投资者适当性(产品风险≤客户承受等级)+资产分层+渠道,一次性返回该客户【全部可推荐】产品(不传 category 即返回所有品类);只在需要单一品类时才传 category。只能推荐本工具返回的产品。",
                Schemas.object()
                        .required("customerId", "string", "客户号")
                        .optional("category", "string", "可选品类:短期理财/公募基金/黄金/短期贷款/私募基金/家族信托")
                        .build(),
                inputs -> {
                    CustomerProfile c = WealthData.customer(str(inputs, "customerId"));
                    if (c == null) {
                        return Map.of("error", "未找到该客户");
                    }
                    Product.Category filter = parseCategory(str(inputs, "category"));
                    Recommendation rec = RecommendationService.recommend(c, filter, rmChannel);

                    List<Map<String, Object>> items = new ArrayList<>();
                    for (Product p : rec.products()) {
                        Map<String, Object> pm = new LinkedHashMap<>();
                        pm.put("id", p.id());
                        pm.put("name", p.name());
                        pm.put("category", p.category().label);
                        pm.put("risk", p.risk() == null ? "N/A(信贷)" : p.risk().name());
                        if (p.termDays() != null) {
                            pm.put("termDays", p.termDays());
                        }
                        pm.put("indicativeYield", p.indicativeYield());
                        pm.put("minAmountCny", p.minAmountCny());
                        items.add(pm);
                    }

                    // Compact output to save tokens: no repeated disclaimer (the agent
                    // states it once from its prompt), only fields the model needs.
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("customerRiskLevel", c.risk().name() + "/" + c.risk().label);
                    out.put("tier", c.tier().label);
                    out.put("recommendations", items);
                    if (rec.rmNote() != null) {
                        out.put("rmNote", rec.rmNote());
                    }
                    return out;
                });
    }

    private static Product.Category parseCategory(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        for (Product.Category c : Product.Category.values()) {
            if (c.label.equals(s) || c.name().equalsIgnoreCase(s)) {
                return c;
            }
        }
        return null;
    }

    private static String str(Map<String, Object> inputs, String key) {
        Object v = inputs == null ? null : inputs.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
