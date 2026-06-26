/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tavily Search API client.
 *
 * <p>API key is held in a static field; set via {@link #setApiKey(String)}
 * before first use (typically from the A2A wrapper's @Configuration).
 */
public final class TavilySearchClient implements WebSearchProvider {

    private static final String TAVILY_URL = "https://api.tavily.com/search";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Domain authority weighting for source_kind classification
    private static final Set<String> OFFICIAL_DOMAINS = Set.of(
            "volcengine.com", "bailian.aliyun.com", "bigmodel.cn",
            "moonshot.cn", "deepseek.com", "baidu.com",
            "cloud.tencent.com", "qwenlm.ai", "lingyiwanwu.com"
    );
    private static final Set<String> LOW_QUALITY_DOMAINS = Set.of(
            "csdn.net", "juejin.cn", "zhihu.com"
    );

    private static volatile String apiKey;

    TavilySearchClient() {}

    /** Set the Tavily API key. Must be called before any search. */
    public static void setApiKey(String key) {
        apiKey = key;
    }

    @Override
    public List<Map<String, Object>> search(String query, int topK, String timeRange, String language) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Tavily API key not set. Call TavilySearchClient.setApiKey() first.");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("api_key", apiKey);
            body.put("query", query);
            body.put("max_results", Math.min(topK, 10));
            body.put("search_depth", "basic");
            body.put("include_answer", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TAVILY_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Tavily API returned status " + response.statusCode() + ": " + response.body());
            }

            Map<String, Object> raw = MAPPER.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawResults = (List<Map<String, Object>>) raw.getOrDefault("results", List.of());

            return processResults(rawResults);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Tavily search failed: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> processResults(List<Map<String, Object>> rawResults) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rawResults) {
            String url = asString(r.get("url"));
            double score = asDouble(r.get("score"), 0.5);

            // Reranker: official domains get boost, low-quality get penalty
            String domain = extractDomain(url);
            if (OFFICIAL_DOMAINS.contains(domain)) {
                score *= 2.0;
            } else if (LOW_QUALITY_DOMAINS.contains(domain)) {
                score *= 0.7;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("url", url);
            item.put("title", r.getOrDefault("title", ""));
            item.put("snippet", r.getOrDefault("content", ""));
            item.put("source_kind", classifySource(url, domain));
            item.put("score", score);
            out.add(item);
        }
        // Sort by score descending
        out.sort((a, b) -> Double.compare(asDouble(b.get("score"), 0), asDouble(a.get("score"), 0)));
        return out;
    }

    private static String classifySource(String url, String domain) {
        if (OFFICIAL_DOMAINS.contains(domain)) return "official";
        if (url.contains("blog") || domain.contains("blog")) return "blog";
        if (LOW_QUALITY_DOMAINS.contains(domain)) return "forum";
        return "news";
    }

    static String extractDomain(String url) {
        if (url == null || url.isBlank()) return "";
        String host = url.replaceFirst("^https?://", "");
        int slash = host.indexOf('/');
        if (slash > 0) host = host.substring(0, slash);
        // Strip "www." prefix
        if (host.startsWith("www.")) host = host.substring(4);
        return host.toLowerCase();
    }

    private static String asString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static double asDouble(Object v, double fallback) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
