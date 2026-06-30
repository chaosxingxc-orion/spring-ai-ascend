package com.huawei.ascend.examples.deepresearch.search;

import com.huawei.ascend.examples.deepresearch.search.WebSearchProvider.Language;
import com.huawei.ascend.examples.deepresearch.search.WebSearchProvider.Result;
import com.huawei.ascend.examples.deepresearch.search.WebSearchProvider.SearchRequest;
import com.huawei.ascend.examples.deepresearch.search.WebSearchProvider.SearchResponse;
import com.huawei.ascend.examples.deepresearch.search.WebSearchProvider.TimeRange;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prod {@code web_search} tool bridge. Wired from {@code agent.prod.yaml} via
 * {@code tools[].ref.{class,method}}. Holds a lazily-initialised
 * {@link TavilyWebSearchProvider} keyed by {@code TAVILY_API_KEY} env var.
 *
 * <p>YAML constraint: must expose
 * {@code public static Object search(Map<String,Object>)}. The same static
 * method is invoked by every ReAct iteration.
 *
 * <p>Stub counterpart: {@link StubWebSearchTool}.
 */
public final class WebSearchTool {

    private static final String API_KEY_ENV = "TAVILY_API_KEY";
    private static final String API_URL_ENV = "TAVILY_API_URL";

    private static volatile WebSearchProvider provider;

    private WebSearchTool() {
    }

    public static Object search(Map<String, Object> inputs) {
        String query = stringInput(inputs, "query", "");
        if (query.isBlank()) {
            return error("query is required");
        }
        int topK = intInput(inputs, "top_k", 10);
        TimeRange timeRange = parseTimeRange(stringInput(inputs, "time_range", "all"));
        Language language = parseLanguage(stringInput(inputs, "language", "any"));

        SearchResponse response;
        try {
            response = provider().search(new SearchRequest(query, topK, timeRange, language));
        } catch (RuntimeException ex) {
            return error("tavily_failed: " + ex.getMessage());
        }
        return WebSearchResultSerializer.serialize(response);
    }

    /** Visible for tests — override provider with an in-memory fake. */
    static void useProvider(WebSearchProvider override) {
        provider = override;
    }

    private static WebSearchProvider provider() {
        WebSearchProvider local = provider;
        if (local != null) {
            return local;
        }
        synchronized (WebSearchTool.class) {
            if (provider == null) {
                String key = System.getenv(API_KEY_ENV);
                if (key == null || key.isBlank()) {
                    throw new IllegalStateException(API_KEY_ENV + " env var is required for prod search-agent");
                }
                String endpoint = System.getenv(API_URL_ENV);
                if (endpoint == null || endpoint.isBlank()) {
                    endpoint = TavilyWebSearchProvider.DEFAULT_ENDPOINT;
                }
                provider = new TavilyWebSearchProvider(key, endpoint);
            }
            return provider;
        }
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
        out.put("results", List.<Result>of());
        out.put("error", message);
        return out;
    }
}