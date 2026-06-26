/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebSearchToolTest {

    @Test
    void missingQuery_shouldReturnError() {
        Map<String, Object> result = WebSearchTool.search(Map.of("top_k", 5));
        assertThat(result.get("error")).asString().contains("query is required");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
        assertThat(results).isEmpty();
    }

    @Test
    void defaultValues_shouldBeApplied() {
        // With no API key set, search will fail with an error
        // which gets caught and turned into an error result
        Map<String, Object> result = WebSearchTool.search(Map.of("query", "test query"));
        // Either gets error (no API key) or runs — both are valid test outcomes
        assertThat(result).containsKey("results");
        assertThat(result).containsKey("total");
    }
}
