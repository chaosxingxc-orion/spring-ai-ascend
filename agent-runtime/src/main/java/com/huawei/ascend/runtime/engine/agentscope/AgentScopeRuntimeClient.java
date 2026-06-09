package com.huawei.ascend.runtime.engine.agentscope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.Message;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
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
        HttpResponse<Stream<String>> response;
        try {
            response = send(request);
        } catch (RuntimeException ex) {
            return Stream.of(ioFailure(ex));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // The error body is not an SSE stream; close it so the HTTP connection is released.
            response.body().close();
            return Stream.of(Map.of(
                    "status", "error",
                    "error_code", "AGENTSCOPE_RUNTIME_HTTP_" + response.statusCode(),
                    "message", "AgentScope runtime returned HTTP " + response.statusCode()));
        }
        return readEvents(response.body());
    }

    private Stream<Map<String, Object>> readEvents(Stream<String> lines) {
        return lines
                .map(String::trim)
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .filter(data -> !data.isBlank() && !"[DONE]".equals(data))
                .map(this::readEvent);
    }

    private HttpResponse<Stream<String>> send(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).join();
    }

    private static Map<String, Object> ioFailure(RuntimeException ex) {
        return Map.of(
                "status", "error",
                "error_code", "AGENTSCOPE_RUNTIME_IO",
                "message", failureMessage(ex));
    }

    private static String failureMessage(RuntimeException ex) {
        Throwable failure = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
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
}
