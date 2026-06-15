/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.a2a;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mem0 OSS REST-backed {@link MemoryProvider} for the hotel A2A demo.
 *
 * <p>The scope key is intentionally narrower than the upstream example: only
 * {@code user_id} + {@code agent_id}, no {@code run_id}. Each A2A request gets a
 * fresh {@code sessionId}, so including it would partition memory per request and
 * defeat the "save a booking, recall it later" scenario.
 *
 * <p>Activated when {@code hotel-agent.memory.provider=mem0}. Only the OSS REST
 * contract is implemented — {@code POST /search} and {@code POST /memories}, auth
 * via the {@code X-API-Key} header. Mem0 Platform deployments must supply their
 * own MemoryProvider implementation.
 */
final class HotelMem0MemoryProvider implements MemoryProvider {

    private static final Logger LOG = LoggerFactory.getLogger(HotelMem0MemoryProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String SEARCH_PATH = "/search";
    private static final String ADD_PATH = "/memories";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final boolean inferOnSave;

    HotelMem0MemoryProvider(String baseUrl, String apiKey, boolean inferOnSave) {
        // Pin HTTP/1.1: JDK HttpClient defaults to HTTP/2 and emits an "Upgrade: h2c"
        // preface on cleartext POSTs. uvicorn (mem0-wrapper) only speaks HTTP/1.1 and
        // mis-parses the upgraded request as a body-less POST, yielding a 422 with
        // {"loc":["body"],"input":null}. HTTP/1.1 round-trips cleanly with uvicorn.
        this(HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                baseUrl, apiKey, inferOnSave);
    }

    HotelMem0MemoryProvider(HttpClient httpClient, String baseUrl, String apiKey, boolean inferOnSave) {
        this.httpClient = httpClient;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
        this.inferOnSave = inferOnSave;
    }

    @Override
    public void init(AgentExecutionContext context) {
        RuntimeIdentity scope = context.getScope();
        String lastUserText = context.getMessages() == null ? "<null>" : context.getMessages().stream()
                .filter(m -> m.role() == RuntimeMessage.Role.USER)
                .map(RuntimeMessage::text)
                .reduce((first, second) -> second)
                .orElse("<no-user-msg>");
        LOG.info("[HOTEL-MEM0] init tenantId={} userId={} agentId={} baseUrl={} inferOnSave={} lastUserText={}",
                scope.tenantId(), scope.userId(), scope.agentId(), baseUrl, inferOnSave, lastUserText);
    }

    @Override
    public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
        if (limit <= 0 || query == null || query.isBlank()) {
            return List.of();
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", query);
        request.put("top_k", limit);
        request.putAll(scope(context));
        LOG.info("[HOTEL-MEM0] search userId={} agentId={} limit={} query={}",
                context.getScope().userId(), context.getScope().agentId(), limit, query);
        Map<String, Object> response = post(SEARCH_PATH, request);
        List<MemoryHit> hits = memoryItems(response).stream()
                .map(this::toHit)
                .filter(hit -> !hit.content().isBlank())
                .limit(limit)
                .toList();
        LOG.info("[HOTEL-MEM0] search returned {} hits", hits.size());
        return hits;
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
        LOG.info("[HOTEL-MEM0] save userId={} agentId={} messages={} infer={}",
                context.getScope().userId(), context.getScope().agentId(), messages.size(), inferOnSave);
        post(ADD_PATH, request);
    }

    private MemoryHit toHit(Map<String, Object> item) {
        String content = firstText(item, "memory", "content", "text");
        Object rawScore = item.get("score");
        Double score = rawScore instanceof Number number ? number.doubleValue() : null;
        String id = firstText(item, "id", "memory_id");
        return new MemoryHit(id, content, score, item);
    }

    private Map<String, Object> scope(AgentExecutionContext context) {
        RuntimeIdentity identity = context.getScope();
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("user_id", identity.userId());
        scope.put("agent_id", identity.agentId());
        scope.put("metadata", Map.of(
                "tenantId", identity.tenantId(),
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
                builder.header("X-API-Key", apiKey);
            }
            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
}
