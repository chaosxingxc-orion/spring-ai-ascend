package com.huawei.ascend.runtime.engine.agentscope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.Message;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class AgentScopeRuntimeClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AgentScopeRuntimeClientProperties properties;

    public AgentScopeRuntimeClient(AgentScopeRuntimeClientProperties properties) {
        this(HttpClient.newHttpClient(), new ObjectMapper(), properties);
    }

    AgentScopeRuntimeClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            AgentScopeRuntimeClientProperties properties) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public Stream<Map<String, Object>> streamEvents(AgentScopeInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        HttpRequest request = HttpRequest.newBuilder(properties.endpoint())
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", invocation.tenantId())
                .header("X-Agent-Id", invocation.agentId())
                .header("X-Task-Id", invocation.taskId())
                .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody(invocation))))
                .build();
        HttpResponse<Stream<String>> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Stream.of(Map.of(
                    "status", "error",
                    "error_code", "AGENTSCOPE_RUNTIME_HTTP_" + response.statusCode(),
                    "message", "AgentScope runtime returned HTTP " + response.statusCode()));
        }
        return response.body()
                .map(String::trim)
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .filter(data -> !data.isBlank() && !"[DONE]".equals(data))
                .map(this::readEvent);
    }

    private HttpResponse<Stream<String>> send(HttpRequest request) {
        try {
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).join();
        } catch (RuntimeException ex) {
            return syntheticFailure(ex);
        }
    }

    private HttpResponse<Stream<String>> syntheticFailure(RuntimeException ex) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return 599;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public java.util.Optional<HttpResponse<Stream<String>>> previousResponse() {
                return java.util.Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (ignoredName, ignoredValue) -> true);
            }

            @Override
            public Stream<String> body() {
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                return Stream.of("data: {\"status\":\"error\",\"error_code\":\"AGENTSCOPE_RUNTIME_IO\",\"message\":\""
                        + escapeJson(message) + "\"}");
            }

            @Override
            public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
                return java.util.Optional.empty();
            }

            @Override
            public java.net.URI uri() {
                return properties.endpoint();
            }

            @Override
            public java.net.http.HttpClient.Version version() {
                return java.net.http.HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private Map<String, Object> requestBody(AgentScopeInvocation invocation) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", input(invocation.messages()));
        body.put("stream", true);
        body.put("id", invocation.taskId());
        body.put("session_id", invocation.sessionId());
        body.put("user_id", invocation.userId());
        Map<String, Object> metadata = new LinkedHashMap<>(invocation.metadata());
        metadata.put("tenantId", invocation.tenantId());
        metadata.put("agentId", invocation.agentId());
        metadata.put("taskId", invocation.taskId());
        metadata.put("inputType", invocation.inputType());
        body.put("metadata", metadata);
        if (!invocation.variables().isEmpty()) {
            body.put("variables", invocation.variables());
        }
        return body;
    }

    private List<Map<String, Object>> input(List<Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message message : messages) {
            result.add(Map.of(
                    "role", message.role().wire(),
                    "content", List.of(Map.of("type", "text", "text", message.text()))));
        }
        return result;
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize AgentScope request", ex);
        }
    }

    private Map<String, Object> readEvent(String data) {
        try {
            return objectMapper.readValue(data, MAP_TYPE);
        } catch (IOException ex) {
            return Map.of("status", "output", "text", data);
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
