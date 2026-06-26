/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub web_search tool: returns empty results for all queries.
 * No API key needed. Logs a warning so the developer can verify
 * search integration without real Tavily calls.
 */
public final class StubWebSearchTool {

    private static final Logger LOG = LoggerFactory.getLogger(StubWebSearchTool.class);

    private StubWebSearchTool() {}

    public static Map<String, Object> search(Map<String, Object> inputs) {
        String query = asString(inputs.get("query"));
        LOG.warn("[stub] web_search called with query='{}' — returning empty results (stub mode)", query);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("results", List.of());
        out.put("total", 0);
        out.put("stub_note", "StubWebSearchTool: no real search in stub mode. Query was: " + query);
        return out;
    }

    private static String asString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
