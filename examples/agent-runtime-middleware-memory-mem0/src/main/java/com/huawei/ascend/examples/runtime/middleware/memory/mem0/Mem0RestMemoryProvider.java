package com.huawei.ascend.examples.runtime.middleware.memory.mem0;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Example-only Mem0 REST {@link MemoryProvider}.
 *
 * <p>The runtime SPI stays small and vendor-neutral. This adapter demonstrates
 * how a concrete application can bridge that SPI to a Mem0-compatible REST
 * service without putting Mem0 types in {@code agent-runtime}.
 */
public final class Mem0RestMemoryProvider implements MemoryProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final boolean inferOnSave;
    private final ApiMode apiMode;

    public Mem0RestMemoryProvider(String baseUrl, String apiKey, boolean inferOnSave) {
        this(baseUrl, apiKey, inferOnSave, "oss");
    }

    public Mem0RestMemoryProvider(String baseUrl, String apiKey, boolean inferOnSave, String apiMode) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), baseUrl, apiKey, inferOnSave,
                apiMode);
    }

    Mem0RestMemoryProvider(HttpClient httpClient, String baseUrl, String apiKey, boolean inferOnSave, String apiMode) {
        this.httpClient = httpClient;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
        this.inferOnSave = inferOnSave;
        this.apiMode = ApiMode.from(apiMode);
    }

    @Override
    public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
        if (limit <= 0 || query == null || query.isBlank()) {
            return List.of();
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", query);
        request.put("top_k", limit);
        request.putAll(searchScope(context));
        Map<String, Object> response = post(apiMode.searchPath(), request);
        return memoryItems(response).stream()
                .map(this::toHit)
                .filter(hit -> !hit.content().isBlank())
                .limit(limit)
                .toList();
    }

    @Override
    public void save(AgentExecutionContext context, List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Map<String, Object>> messages = records.stream()
                .filter(record -> record != null && !record.content().isBlank())
                .map(record -> Map.<String, Object>of("role", record.role(), "content", record.content()))
                .toList();
        if (messages.isEmpty()) {
            return;
        }
        Map<String, Object> request = new LinkedHashMap<>(scope(context));
        request.put("messages", messages);
        request.put("infer", inferOnSave);
        post(apiMode.addPath(), request);
    }

    private MemoryHit toHit(Map<String, Object> item) {
        String content = firstText(item, "memory", "content", "text");
        Object rawScore = item.get("score");
        Double score = rawScore instanceof Number number ? number.doubleValue() : null;
        String id = firstText(item, "id", "memory_id");
        return new MemoryHit(id, content, score, item);
    }

    private Map<String, Object> searchScope(AgentExecutionContext context) {
        Map<String, Object> scope = scope(context);
        if (apiMode == ApiMode.PLATFORM) {
            return Map.of("filters", scope);
        }
        return scope;
    }

    private Map<String, Object> scope(AgentExecutionContext context) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("user_id", context.getScope().userId());
        scope.put("agent_id", context.getScope().agentId());
        scope.put("run_id", context.getScope().sessionId());
        scope.put("metadata", Map.of(
                "tenantId", context.getScope().tenantId(),
                "sessionId", context.getScope().sessionId(),
                "taskId", context.getScope().taskId(),
                "agentStateKey", context.getAgentStateKey()));
        return scope;
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json");
            if (!apiKey.isBlank()) {
                apiMode.addAuth(builder, apiKey);
            }
            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Mem0 request failed with status " + response.statusCode()
                        + ": " + response.body());
            }
            if (response.body() == null || response.body().isBlank()) {
                return Map.of();
            }
            return MAPPER.readValue(response.body(), MAP_TYPE);
        } catch (IOException error) {
            throw new IllegalStateException("Mem0 request failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mem0 request interrupted", error);
        }
    }

    private static List<Map<String, Object>> memoryItems(Map<String, Object> response) {
        Object results = response.get("results");
        if (results instanceof List<?> list) {
            return castList(list);
        }
        Object memories = response.get("memories");
        if (memories instanceof List<?> list) {
            return castList(list);
        }
        if (response.get("memory") != null || response.get("content") != null || response.get("text") != null) {
            return List.of(response);
        }
        return List.of();
    }

    private static List<Map<String, Object>> castList(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> typed = new LinkedHashMap<>();
                map.forEach((key, value) -> typed.put(String.valueOf(key), value));
                result.add(typed);
            }
        }
        return result;
    }

    private static String firstText(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8000" : baseUrl.trim();
        return value.replaceAll("/+$", "");
    }

    private enum ApiMode {
        OSS("/search", "/memories"),
        PLATFORM("/v2/memories/search/", "/v1/memories/");

        private final String searchPath;
        private final String addPath;

        ApiMode(String searchPath, String addPath) {
            this.searchPath = searchPath;
            this.addPath = addPath;
        }

        private String searchPath() {
            return searchPath;
        }

        private String addPath() {
            return addPath;
        }

        private void addAuth(HttpRequest.Builder builder, String apiKey) {
            if (this == PLATFORM) {
                builder.header("Authorization", "Token " + apiKey);
                return;
            }
            builder.header("X-API-Key", apiKey);
        }

        private static ApiMode from(String value) {
            if (value != null && "platform".equalsIgnoreCase(value.trim())) {
                return PLATFORM;
            }
            return OSS;
        }
    }
}
