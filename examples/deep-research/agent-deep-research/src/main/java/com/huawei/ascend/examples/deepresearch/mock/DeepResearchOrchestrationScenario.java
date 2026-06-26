/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.mock;

import com.huawei.ascend.examples.deepresearch.report.DeepResearchCitation;
import com.huawei.ascend.examples.deepresearch.report.DeepResearchReportComposer;
import com.huawei.ascend.examples.deepresearch.report.DeepResearchReportOutput;
import java.util.List;
import java.util.Map;

/**
 * Reference orchestration that mirrors expected root-agent behavior when sub-agents return mock fixtures.
 *
 * <p>Simulates: search → read (spa_blocked recovery) → verify (contradict) → report assembly.
 */
public final class DeepResearchOrchestrationScenario {

    private static final List<String> VENDORS =
            List.of("火山方舟", "阿里百炼", "智谱AI", "Kimi", "DeepSeek");

    private DeepResearchOrchestrationScenario() {
    }

    public static DeepResearchReportOutput runIntegrationScenario() {
        Map<String, Object> search = MockSubAgentFixtures.searchResults();
        Map<String, Object> spaBlocked = MockSubAgentFixtures.readSpaBlocked();
        Map<String, Object> officialRead = MockSubAgentFixtures.readOfficialPricing();
        Map<String, Object> cloudflare = MockSubAgentFixtures.readCloudflare403();
        Map<String, Object> contradict = MockSubAgentFixtures.verifyContradict();
        Map<String, Object> insufficient = MockSubAgentFixtures.verifyInsufficient();

        if (!"spa_blocked".equals(nested(spaBlocked, "metadata", "doc_type"))) {
            throw new IllegalStateException("fixture must include spa_blocked path");
        }
        if (!"contradict".equals(contradict.get("verdict"))) {
            throw new IllegalStateException("fixture must include contradict verdict");
        }
        if (!"insufficient".equals(insufficient.get("verdict"))) {
            throw new IllegalStateException("fixture must include insufficient verdict");
        }
        if (!"cloudflare_403".equals(nested(cloudflare, "metadata", "doc_type"))) {
            throw new IllegalStateException("fixture must include cloudflare_403 path");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) search.get("results");
        if (results == null || results.isEmpty()) {
            throw new IllegalStateException("search fixture must return results");
        }

        String recoveredUrl = String.valueOf(officialRead.get("title"));
        List<DeepResearchReportComposer.VendorRow> rows = VENDORS.stream()
                .map(vendor -> vendorRow(vendor, officialRead, contradict))
                .toList();

        return DeepResearchReportComposer.compose(new DeepResearchReportComposer.ComposeRequest(
                "国内主流大模型 API 对比 (2026 Q2)",
                "2026-06-22",
                rows,
                List.of(
                        "plan_read 对 SPA 页面返回 spa_blocked 后，已换源读取: " + recoveredUrl,
                        "verify-agent 对火山方舟定价返回 verdict=contradict，表中保留争议说明",
                        "verify-agent 对限速字段返回 verdict=insufficient，已标注未验证",
                        "plan_read 对 cloudflare_403 URL 已降权并排除 citations")));
    }

    private static DeepResearchReportComposer.VendorRow vendorRow(
            String vendor,
            Map<String, Object> officialRead,
            Map<String, Object> contradict) {
        String pricing = vendor.equals("火山方舟")
                ? "争议：0.0008 vs 0.001 元/千 token（contradict）"
                : "输入 0.001 / 输出 0.002 元/千 token";
        double pricingConfidence = vendor.equals("火山方舟") ? 0.55 : 0.9;

        Map<String, String> dimensions = Map.of(
                "pricing", pricing,
                "context_length", "128K",
                "rate_limit", vendor.equals("DeepSeek") ? "未验证" : "60 QPS",
                "function_calling", "支持");

        DeepResearchCitation citation = new DeepResearchCitation(
                "https://www.volcengine.com/pricing",
                String.valueOf(officialRead.get("title")),
                DeepResearchReportComposer.nowIso());

        Map<String, Double> confidence = Map.of(
                vendor + ".pricing.input", pricingConfidence,
                vendor + ".context_length", 0.88,
                vendor + ".function_calling", 0.95);

        return new DeepResearchReportComposer.VendorRow(vendor, dimensions, List.of(citation), confidence);
    }

    @SuppressWarnings("unchecked")
    private static String nested(Map<String, Object> map, String key1, String key2) {
        Object nested = map.get(key1);
        if (nested instanceof Map<?, ?> inner) {
            Object value = inner.get(key2);
            return value == null ? "" : String.valueOf(value);
        }
        return "";
    }
}
