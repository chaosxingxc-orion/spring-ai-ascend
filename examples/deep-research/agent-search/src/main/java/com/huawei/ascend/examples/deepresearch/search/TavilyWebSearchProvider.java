package com.huawei.ascend.examples.deepresearch.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tavily Search API backend.
 * <p>
 * Calls {@code POST https://api.tavily.com/search} with {@code search_depth=basic}
 * + {@code include_answer=false} (the deep-research root agent synthesises the
 * final answer; Tavily's LLM summary is not consumed). The API key is read
 * from the {@code TAVILY_API_KEY} env var by the caller and passed in.
 */
public final class TavilyWebSearchProvider implements WebSearchProvider {

    static final String DEFAULT_ENDPOINT = "https://api.tavily.com/search";

    private final String apiKey;
    private final URI endpoint;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public TavilyWebSearchProvider(String apiKey) {
        this(apiKey, DEFAULT_ENDPOINT, defaultHttpClient());
    }

    TavilyWebSearchProvider(String apiKey, String endpoint, HttpClient httpClient) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.endpoint = URI.create(endpoint);
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    private static HttpClient defaultHttpClient() {
        // HTTP/1.1: corporate proxies and CDNs occasionally break HTTP/2 ALPN.
        // proxy(ProxySelector.getDefault()): JDK HttpClient defaults to NO_PROXY
        // and IGNORES the http_proxy / https_proxy env vars (curl reads those,
        // HttpClient does not). Honouring the default ProxySelector picks up
        // -Dhttp.proxyHost / -Dhttps.proxyHost / -Dhttp.nonProxyHosts at jar
        // launch, so the same jar works direct-to-internet locally and via the
        // corporate proxy on locked-down hosts.
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(ProxySelector.getDefault())
                .build();
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        try {
            String body = buildRequestBody(request);
            HttpRequest http = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(http, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "tavily http " + response.statusCode() + ": " + truncate(response.body(), 400));
            }
            return parseResponse(response.body(), request.topK());
        } catch (IOException ex) {
            throw new IllegalStateException("tavily call failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("tavily call interrupted", ex);
        }
    }

    private String buildRequestBody(SearchRequest request) throws IOException {
        // No include_domains push-down: domain bias is applied AFTER fetch by
        // DomainReranker (official x2, blog x0.7). Narrowing at Tavily here
        // would suppress generic queries that don't mention a vendor brand.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_key", apiKey);
        body.put("query", request.query());
        body.put("search_depth", "basic");
        body.put("max_results", Math.max(1, request.topK()));
        body.put("include_answer", false);
        Integer days = mapTimeRangeToDays(request.timeRange());
        if (days != null) {
            body.put("days", days);
        }
        return mapper.writeValueAsString(body);
    }

    private SearchResponse parseResponse(String json, int topK) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode results = root.path("results");
        List<Result> raw = new ArrayList<>();
        Iterator<JsonNode> it = results.elements();
        while (it.hasNext()) {
            JsonNode node = it.next();
            String url = node.path("url").asText("");
            if (url.isBlank()) {
                continue;
            }
            String title = node.path("title").asText("");
            String snippet = node.path("content").asText("");
            double score = node.path("score").asDouble(0.0);
            WebSearchProvider.SourceKind kind = SourceKindClassifier.classify(url);
            raw.add(new Result(url, title, snippet, kind, score));
        }
        List<Result> reranked = DomainReranker.rerank(raw);
        if (reranked.size() > topK) {
            reranked = reranked.subList(0, topK);
        }
        return new SearchResponse(reranked);
    }

    private static Integer mapTimeRangeToDays(TimeRange range) {
        if (range == null || range == TimeRange.ALL) {
            return null;
        }
        return switch (range) {
            case WEEK -> 7;
            case MONTH -> 30;
            case YEAR -> 365;
            default -> null;
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}