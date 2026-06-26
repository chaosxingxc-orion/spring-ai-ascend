/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.tool;

import com.huawei.ascend.examples.deepresearch.verify.client.TavilySearchClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Web search tool for verify-agent. Uses an independent Tavily API client —
 * does NOT call search-agent via A2A (avoids circular dependency).
 *
 * <p>Signature: {@code public static Map<String, Object> search(Map<String, Object> inputs)}.
 */
public final class WebSearchTool {

    private static volatile TavilySearchClient client;

    private WebSearchTool() {}

    /** Set the search provider. Called from A2A wrapper @Configuration. */
    public static void setClient(TavilySearchClient c) {
        client = c;
    }

    public static Map<String, Object> search(Map<String, Object> inputs) {
        String query = asString(inputs.get("query"));
        int topK = asInt(inputs.get("top_k"), 5);
        String timeRange = asString(inputs.get("time_range"));
        String language = asString(inputs.get("language"));

        if (query == null || query.isBlank()) {
            return errorResult("query is required");
        }
        if (timeRange == null || timeRange.isBlank()) timeRange = "month";
        if (language == null || language.isBlank()) language = "zh";

        if (client == null) {
            return errorResult("Search client not configured. Call WebSearchTool.setClient() first.");
        }

        try {
            List<Map<String, Object>> results = client.search(query, topK, timeRange, language);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("results", results);
            out.put("total", results.size());
            return out;
        } catch (Exception e) {
            return errorResult("Search failed: " + e.getMessage());
        }
    }

    private static Map<String, Object> errorResult(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("results", List.of());
        out.put("total", 0);
        out.put("error", message);
        return out;
    }

    private static String asString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static int asInt(Object v, int fallback) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
