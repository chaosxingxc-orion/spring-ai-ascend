package com.huawei.ascend.examples.deepresearch.search;

import com.huawei.ascend.examples.deepresearch.search.WebSearchProvider.Language;
import com.huawei.ascend.examples.deepresearch.search.WebSearchProvider.SearchRequest;
import com.huawei.ascend.examples.deepresearch.search.WebSearchProvider.SearchResponse;
import com.huawei.ascend.examples.deepresearch.search.WebSearchProvider.TimeRange;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stub {@code web_search} tool bridge. Wired from {@code agent.stub.yaml}.
 * Backed by {@link StubWebSearchProvider}; reads canned results from
 * {@code /fixtures/search-results.json} on the classpath.
 *
 * <p>Same I/O contract as {@link WebSearchTool} — only the YAML
 * {@code tools[].ref.class} differs between stub and prod, so the assembly
 * path is identical and there is no behavioural drift between profiles.
 */
public final class StubWebSearchTool {

    private static final WebSearchProvider PROVIDER = new StubWebSearchProvider();

    private StubWebSearchTool() {
    }

    public static Object search(Map<String, Object> inputs) {
        String query = stringInput(inputs, "query", "");
        if (query.isBlank()) {
            return error("query is required");
        }
        int topK = intInput(inputs, "top_k", 10);
        TimeRange timeRange = parseTimeRange(stringInput(inputs, "time_range", "all"));
        Language language = parseLanguage(stringInput(inputs, "language", "any"));

        SearchResponse response = PROVIDER.search(new SearchRequest(query, topK, timeRange, language));
        return WebSearchResultSerializer.serialize(response);
    }

    private static String stringInput(Map<String, Object> inputs, String key, String fallback) {
        Object v = inputs == null ? null : inputs.get(key);
        return v == null ? fallback : v.toString();
    }

    private static int intInput(Map<String, Object> inputs, String key, int fallback) {
        Object v = inputs == null ? null : inputs.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
        return fallback;
    }

    private static TimeRange parseTimeRange(String raw) {
        try {
            return TimeRange.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TimeRange.ALL;
        }
    }

    private static Language parseLanguage(String raw) {
        try {
            return Language.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Language.ANY;
        }
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("results", List.of());
        out.put("error", message);
        return out;
    }
}