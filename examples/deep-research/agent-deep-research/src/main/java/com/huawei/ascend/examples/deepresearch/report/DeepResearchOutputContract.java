/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and parses the structured JSON tail block from a deep-research response.
 */
public final class DeepResearchOutputContract {

    public static final String REPORT_MARKDOWN = "report_markdown";
    public static final String COMPARISON_TABLE = "comparison_table";
    public static final String CITATIONS = "citations";
    public static final String CONFIDENCE_PER_FIELD = "confidence_per_field";

    public static final List<String> CORE_DIMENSIONS =
            List.of("pricing", "context_length", "rate_limit", "function_calling");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern JSON_FENCE =
            Pattern.compile("```json\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private DeepResearchOutputContract() {
    }

    public static Map<String, Object> parseJsonTail(String markdown) {
        Matcher matcher = JSON_FENCE.matcher(markdown == null ? "" : markdown);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing ```json``` tail block in deep-research output");
        }
        try {
            return MAPPER.readValue(matcher.group(1).trim(), MAP_TYPE);
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid JSON tail block in deep-research output", error);
        }
    }

    public static DeepResearchReportOutput parseReport(String markdown) {
        Map<String, Object> root = parseJsonTail(markdown);
        return new DeepResearchReportOutput(
                string(root.get(REPORT_MARKDOWN)),
                comparisonTable(root.get(COMPARISON_TABLE)),
                citations(root.get(CITATIONS)),
                confidence(root.get(CONFIDENCE_PER_FIELD)));
    }

    public static int countFilledComparisonCells(Map<String, Map<String, String>> table, List<String> dimensions) {
        if (table == null || table.isEmpty()) {
            return 0;
        }
        int filled = 0;
        for (Map<String, String> row : table.values()) {
            if (row == null) {
                continue;
            }
            for (String dimension : dimensions) {
                String value = row.get(dimension);
                if (value != null && !value.isBlank() && !"未验证".equals(value.trim())) {
                    filled++;
                }
            }
        }
        return filled;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, String>> comparisonTable(Object raw) {
        if (!(raw instanceof Map<?, ?> outer)) {
            throw new IllegalArgumentException("comparison_table must be an object");
        }
        Map<String, Map<String, String>> table = new java.util.LinkedHashMap<>();
        outer.forEach((vendor, dimensions) -> {
            if (dimensions instanceof Map<?, ?> row) {
                Map<String, String> typed = new java.util.LinkedHashMap<>();
                row.forEach((key, value) -> typed.put(String.valueOf(key), String.valueOf(value)));
                table.put(String.valueOf(vendor), typed);
            }
        });
        return table;
    }

    @SuppressWarnings("unchecked")
    private static List<DeepResearchCitation> citations(Object raw) {
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("citations must be an array");
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .map(item -> new DeepResearchCitation(
                        string(item.get("url")),
                        string(item.get("title")),
                        string(item.get("fetched_at"))))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> confidence(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("confidence_per_field must be an object");
        }
        Map<String, Double> result = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (value instanceof Number number) {
                result.put(String.valueOf(key), number.doubleValue());
            }
        });
        return result;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
