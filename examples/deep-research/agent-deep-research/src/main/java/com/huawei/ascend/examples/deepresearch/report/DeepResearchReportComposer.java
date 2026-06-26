/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure-Java report assembler used by tests and as a reference for expected root-agent output shape.
 */
public final class DeepResearchReportComposer {

    private DeepResearchReportComposer() {
    }

    public static DeepResearchReportOutput compose(ComposeRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, Map<String, String>> table = new LinkedHashMap<>();
        List<DeepResearchCitation> citations = new ArrayList<>();
        Map<String, Double> confidence = new LinkedHashMap<>();

        for (VendorRow row : request.vendorRows()) {
            table.put(row.vendor(), row.dimensions());
            citations.addAll(row.citations());
            confidence.putAll(row.confidencePerField());
        }

        String markdown = buildMarkdown(request.topic(), request.asOfDate(), table, citations, request.notes());
        return new DeepResearchReportOutput(markdown, table, List.copyOf(citations), Map.copyOf(confidence));
    }

    private static String buildMarkdown(
            String topic,
            String asOfDate,
            Map<String, Map<String, String>> table,
            List<DeepResearchCitation> citations,
            List<String> notes) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(topic).append("\n\n");
        builder.append("截止日期：").append(asOfDate).append("\n\n");
        builder.append("## 对比表\n\n");
        builder.append("| 厂商 | 定价 | 上下文 | 限速 | Function Calling |\n");
        builder.append("|------|------|--------|------|------------------|\n");
        for (Map.Entry<String, Map<String, String>> entry : table.entrySet()) {
            Map<String, String> dims = entry.getValue();
            builder.append("| ")
                    .append(entry.getKey())
                    .append(" | ")
                    .append(dims.getOrDefault("pricing", "未验证"))
                    .append(" | ")
                    .append(dims.getOrDefault("context_length", "未验证"))
                    .append(" | ")
                    .append(dims.getOrDefault("rate_limit", "未验证"))
                    .append(" | ")
                    .append(dims.getOrDefault("function_calling", "未验证"))
                    .append(" |\n");
        }
        if (notes != null && !notes.isEmpty()) {
            builder.append("\n## 备注\n\n");
            for (String note : notes) {
                builder.append("- ").append(note).append("\n");
            }
        }
        builder.append("\n## 引用\n\n");
        for (DeepResearchCitation citation : citations) {
            builder.append("- [")
                    .append(citation.title())
                    .append("](")
                    .append(citation.url())
                    .append(")\n");
        }
        return builder.toString();
    }

    public record VendorRow(
            String vendor,
            Map<String, String> dimensions,
            List<DeepResearchCitation> citations,
            Map<String, Double> confidencePerField) {
    }

    public record ComposeRequest(
            String topic,
            String asOfDate,
            List<VendorRow> vendorRows,
            List<String> notes) {
    }

    public static String nowIso() {
        return Instant.now().toString();
    }
}
