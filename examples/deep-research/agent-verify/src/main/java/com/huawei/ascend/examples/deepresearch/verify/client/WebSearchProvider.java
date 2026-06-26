/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.client;

import java.util.List;
import java.util.Map;

/**
 * Abstraction for web search backends (Tavily, Serper, etc.).
 */
public interface WebSearchProvider {

    /**
     * Search the web.
     *
     * @param query    search query string
     * @param topK     max results to return
     * @param timeRange time filter: year, month, week, all
     * @param language language filter: zh, en, any
     * @return list of search result maps with keys: url, title, snippet, source_kind, score
     */
    List<Map<String, Object>> search(String query, int topK, String timeRange, String language);
}
