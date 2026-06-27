package com.huawei.ascend.examples.financial.expensereview.tool;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stub company expense policy lookup tool.
 *
 * <p>Returns fixed policy data for common expense categories.
 * No external dependencies — SIT teams can replace with a real policy
 * service by swapping the tool implementation.
 *
 * <p>Built-in policies:
 * <ul>
 *   <li>交通: flight ≤ 5000, train ≤ 1000</li>
 *   <li>住宿: per night ≤ 600, receipt required</li>
 *   <li>餐饮: per meal ≤ 300, daily ≤ 500</li>
 *   <li>其他: per item ≤ 1000</li>
 * </ul>
 */
public final class CompanyPolicyTool extends LocalFunction {

    public static final String TOOL_ID = "company_policy";

    private static final Map<String, Map<String, Object>> POLICIES = buildPolicies();

    public CompanyPolicyTool() {
        super(buildCard(), CompanyPolicyTool::execute);
    }

    private static ToolCard buildCard() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("items", Map.of(
                "type", "array",
                "description", "报销条目列表，每项含 name/amount/category",
                "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "name", Map.of("type", "string", "description", "条目名称"),
                                "amount", Map.of("type", "number", "description", "金额"),
                                "category", Map.of("type", "string", "description", "类别")
                        )
                )
        ));

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", props);
        inputParams.put("required", List.of("items"));

        return ToolCard.builder()
                .id(TOOL_ID)
                .name(TOOL_ID)
                .description("查询公司费用政策：根据报销条目返回对应的费用上限和审批要求")
                .inputParams(inputParams)
                .build();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> execute(Map<String, Object> inputs) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) inputs.get("items");

        List<Map<String, Object>> policyRules = new ArrayList<>();
        if (items != null) {
            for (Map<String, Object> item : items) {
                String category = (String) item.getOrDefault("category", "其他");
                Map<String, Object> rule = POLICIES.getOrDefault(category, POLICIES.get("其他"));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("item_name", item.getOrDefault("name", "未知"));
                // item_amount 是用于比对限额的单价(住宿=每晚)，与 limit 口径对齐；
                // 老 analyze 只有 amount(总价)时回退到 amount，保持向后兼容。
                Object unit = item.get("unit_price");
                entry.put("item_amount", unit != null ? unit : item.getOrDefault("amount", 0));
                entry.put("quantity", item.getOrDefault("quantity", 1));
                entry.put("category", category);
                entry.put("limit", rule.get("limit"));
                entry.put("requires_approval", rule.get("requires_approval"));
                entry.put("notes", rule.get("notes"));
                policyRules.add(entry);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policy_rules", policyRules);
        out.put("limits", Map.of(
                "flight", 5000,
                "train", 1000,
                "hotel_per_night", 600,
                "meal_per_meal", 300,
                "meal_daily", 500,
                "other_per_item", 1000
        ));
        out.put("checked_at", java.time.Instant.now().toString());
        return out;
    }

    private static Map<String, Map<String, Object>> buildPolicies() {
        Map<String, Map<String, Object>> p = new LinkedHashMap<>();

        Map<String, Object> transport = new LinkedHashMap<>();
        transport.put("limit", 5000);
        transport.put("requires_approval", false);
        transport.put("notes", "机票≤5000元，火车票≤1000元");
        p.put("交通", transport);

        Map<String, Object> hotel = new LinkedHashMap<>();
        hotel.put("limit", 600);
        hotel.put("requires_approval", true);
        hotel.put("notes", "每晚≤600元，需提供住宿发票");
        p.put("住宿", hotel);

        Map<String, Object> meal = new LinkedHashMap<>();
        meal.put("limit", 300);
        meal.put("requires_approval", false);
        meal.put("notes", "每餐≤300元，每日≤500元");
        p.put("餐饮", meal);

        Map<String, Object> other = new LinkedHashMap<>();
        other.put("limit", 1000);
        other.put("requires_approval", false);
        other.put("notes", "其他单项≤1000元");
        p.put("其他", other);

        return p;
    }
}
