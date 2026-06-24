package com.huawei.ascend.examples.a2a.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link MemoryProvider} implementation for external Memory Engine (FastAPI).
 * Delegates to an HTTP service exposing REST endpoints for message ingestion
 * ({@code POST /add_messages/}) and semantic search ({@code POST /search_memory/}).
 *
 * <p>Mapping between runtime concepts and backend parameters:
 * <ul>
 *   <li>{@code user_id} ← {@code context.getScope().userId()}</li>
 *   <li>{@code scope_id} ← {@code context.getScope().userId()} (user-level,
 *       so different sessions of the same user share long-term memory)</li>
 * </ul>
 *
 * <p>All failures are caught and logged at WARN level so the agent execution
 * is never interrupted by memory backend issues.
 */
public class JiuwenMemoryProvider implements MemoryProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JiuwenMemoryProvider.class);

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String HEALTH_PATH = "/health";
    private static final String ADD_MESSAGES_PATH = "/add_messages/";
    private static final String SEARCH_MEMORY_PATH = "/search_memory/";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final double threshold;

    public JiuwenMemoryProvider(String baseUrl) {
        this(baseUrl, 0.3);
    }

    public JiuwenMemoryProvider(String baseUrl, double threshold) {
        this(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
             new ObjectMapper(), baseUrl, threshold);
    }

    JiuwenMemoryProvider(HttpClient httpClient, ObjectMapper objectMapper,
                         String baseUrl, double threshold) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.threshold = threshold;
    }

    @Override
    public void init(AgentExecutionContext context) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + HEALTH_PATH))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                LOGGER.info("jiuwen memory health ok tenantId={} sessionId={}",
                        context.getScope().tenantId(), context.getScope().sessionId());
            } else {
                LOGGER.warn("jiuwen memory health check returned status={} tenantId={} sessionId={}",
                        response.statusCode(), context.getScope().tenantId(), context.getScope().sessionId());
            }
        } catch (Exception error) {
            LOGGER.warn("jiuwen memory health check failed tenantId={} sessionId={} errorClass={} message={}",
                    context.getScope().tenantId(), context.getScope().sessionId(),
                    error.getClass().getSimpleName(), error.getMessage());
        }
    }

    @Override
    public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("num", limit);
        body.put("user_id", context.getScope().userId());
        body.put("scope_id", context.getScope().userId());
        body.put("threshold", threshold);
        LOGGER.info("jiuwen memory search request user_id={} scope_id={} query={} tenantId={} sessionId={}",
                context.getScope().userId(), context.getScope().userId(), query,
                context.getScope().tenantId(), context.getScope().sessionId());

        try {
            String responseBody = postJson(SEARCH_MEMORY_PATH, body);
            Map<String, Object> parsed = objectMapper.readValue(responseBody, MAP_TYPE);
            return parseSearchResults(parsed);
        } catch (Exception error) {
            LOGGER.warn("jiuwen memory search failed tenantId={} sessionId={} query={} errorClass={} message={}",
                    context.getScope().tenantId(), context.getScope().sessionId(),
                    query, error.getClass().getSimpleName(), error.getMessage());
            if (error instanceof MemoryEngineException mee) {
                LOGGER.warn("jiuwen memory search error body: {}", mee.responseBody());
            }
            return List.of();
        }
    }

    @Override
    public void save(AgentExecutionContext context, List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Map<String, String>> messages = new ArrayList<>(records.size());
        for (MemoryRecord record : records) {
            Map<String, String> msg = new LinkedHashMap<>();
            msg.put("role", record.role());
            msg.put("content", record.content());
            messages.add(msg);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", messages);
        body.put("user_id", context.getScope().userId());
        body.put("scope_id", context.getScope().userId());
        LOGGER.info("jiuwen memory save request user_id={} scope_id={} recordCount={} tenantId={} sessionId={}",
                context.getScope().userId(), context.getScope().userId(), records.size(),
                context.getScope().tenantId(), context.getScope().sessionId());

        try {
            postJson(ADD_MESSAGES_PATH, body);
            LOGGER.info("jiuwen memory save ok tenantId={} sessionId={} recordCount={}",
                    context.getScope().tenantId(), context.getScope().sessionId(), records.size());
        } catch (Exception error) {
            LOGGER.warn("jiuwen memory save failed tenantId={} sessionId={} recordCount={} errorClass={} message={}",
                    context.getScope().tenantId(), context.getScope().sessionId(), records.size(),
                    error.getClass().getSimpleName(), error.getMessage());
            if (error instanceof MemoryEngineException mee) {
                LOGGER.warn("jiuwen memory save error body: {}", mee.responseBody());
            }
        }
    }

    private String postJson(String path, Map<String, Object> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new MemoryEngineException(response.statusCode(), response.body());
        }
        return response.body();
    }

    @SuppressWarnings("unchecked")
    private static List<MemoryHit> parseSearchResults(Map<String, Object> parsed) {
        Object raw = parsed.get("results");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<MemoryHit> hits = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String memId = stringField(map, "mem_id");
                String content = stringField(map, "content");
                double score = numberField(map, "score");
                Map<String, Object> metadata = new LinkedHashMap<>();
                String type = stringField(map, "type");
                if (type != null && !type.isBlank()) {
                    metadata.put("type", type);
                }
                hits.add(new MemoryHit(memId, content, score, metadata));
            }
        }
        return hits;
    }

    private static String stringField(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof String text ? text : (value != null ? String.valueOf(value) : null);
    }

    private static double numberField(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    static final class MemoryEngineException extends Exception {
        private final int statusCode;
        private final String responseBody;

        MemoryEngineException(int statusCode, String responseBody) {
            super("jiuwen memory engine returned HTTP " + statusCode);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        int statusCode() { return statusCode; }
        String responseBody() { return responseBody; }
    }
}
