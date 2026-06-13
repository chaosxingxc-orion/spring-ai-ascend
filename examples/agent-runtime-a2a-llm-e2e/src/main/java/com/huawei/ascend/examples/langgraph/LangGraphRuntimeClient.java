package com.huawei.ascend.examples.langgraph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.SseEventDecoder;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Streams a run against a remote LangGraph runtime over SSE. Unlike the
 * AgentScope dialect, LangGraph frames carry a meaningful {@code event:} name
 * (metadata / values / messages partial / updates / error / end), so each
 * emitted element is a map of {@code event} (name, possibly empty) and
 * {@code data} (the decoded JSON payload).
 *
 * <p>Sample code: this adapter lives in the example module and is NOT part of
 * the shipped agent-runtime adapter surface (which is openJiuwen + AgentScope);
 * promoting it requires an authorizing ADR plus the L1/contract-catalog
 * lockstep.
 */
public final class LangGraphRuntimeClient implements AutoCloseable {

    static final String EVENT_KEY = "event";
    static final String DATA_KEY = "data";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LangGraphRuntimeClientProperties properties;
    private final boolean ownsHttpClient;

    public LangGraphRuntimeClient(LangGraphRuntimeClientProperties properties) {
        this(HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build(),
                new ObjectMapper(), properties, true);
    }

    LangGraphRuntimeClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            LangGraphRuntimeClientProperties properties) {
        this(httpClient, objectMapper, properties, false);
    }

    LangGraphRuntimeClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            LangGraphRuntimeClientProperties properties,
            boolean ownsHttpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.ownsHttpClient = ownsHttpClient;
    }

    /**
     * Releases the HTTP transport if this client created it; an injected
     * transport belongs to its injector and is left open.
     */
    @Override
    public void close() {
        if (ownsHttpClient) {
            httpClient.close();
        }
    }

    public Stream<Map<String, Object>> streamEvents(AgentExecutionContext context) {
        Objects.requireNonNull(context, "context");
        RuntimeIdentity scope = Objects.requireNonNull(context.getScope(), "scope");
        HttpRequest.Builder builder = HttpRequest.newBuilder(properties.endpoint())
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", scope.tenantId())
                .header("X-Agent-Id", scope.agentId())
                .header("X-Task-Id", scope.taskId());
        properties.headers().forEach(builder::header);
        HttpRequest request = builder
                // Bounds time-to-response-headers only; the SSE body streams unbounded
                // and stays cancellable through the raw stream's close().
                .timeout(properties.requestTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody(context, scope))))
                .build();
        HttpResponse<Stream<String>> response;
        try {
            response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).join();
        } catch (RuntimeException ex) {
            return Stream.of(ioFailure(ex));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // The error body is not an SSE stream; close it so the HTTP connection is released.
            response.body().close();
            return Stream.of(errorEvent(
                    "LANGGRAPH_RUNTIME_HTTP_" + response.statusCode(),
                    "LangGraph runtime returned HTTP " + response.statusCode()));
        }
        return readEvents(response.body());
    }

    private Stream<Map<String, Object>> readEvents(Stream<String> lines) {
        // LangGraph frames carry meaningful event: names, and an end frame may carry
        // no data line at all — still a real event, so data-less named frames are kept.
        return SseEventDecoder.frames(lines, true, true)
                .flatMap(frame -> {
                    if (frame.failure() != null) {
                        return Stream.of(ioFailure(frame.failure()));
                    }
                    String name = frame.name();
                    String sentinel = frame.data() == null ? "" : frame.data().trim();
                    if (sentinel.isEmpty() || "[DONE]".equals(sentinel) || "null".equals(sentinel)) {
                        return name.isBlank() ? Stream.empty() : Stream.of(event(name, null));
                    }
                    return readEventBlock(frame.data()).stream().map(payload -> event(name, payload));
                });
    }

    private static Map<String, Object> event(String name, Object payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put(EVENT_KEY, name == null ? "" : name);
        event.put(DATA_KEY, payload);
        return event;
    }

    private static Map<String, Object> errorEvent(String code, String message) {
        return event("error", Map.of("error", code, "message", message));
    }

    private static Map<String, Object> ioFailure(RuntimeException ex) {
        return errorEvent("LANGGRAPH_RUNTIME_IO", SseEventDecoder.failureMessage(ex));
    }

    private Map<String, Object> requestBody(AgentExecutionContext context, RuntimeIdentity scope) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("assistant_id", properties.assistantId());
        body.put("input", Map.of("messages", input(context.getMessages())));
        body.put("stream_mode", List.of("values"));
        // thread_id keys LangGraph's checkpointer; the A2A session is the
        // conversation, so state restores across tasks of the same context.
        body.put("config", Map.of("configurable", Map.of("thread_id", scope.sessionId())));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", scope.tenantId());
        metadata.put("agentId", scope.agentId());
        metadata.put("taskId", scope.taskId());
        body.put("metadata", metadata);
        return body;
    }

    private List<Map<String, Object>> input(List<RuntimeMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RuntimeMessage message : messages) {
            result.add(Map.of(
                    "role", message.role() == RuntimeMessage.Role.AGENT ? "assistant" : "user",
                    "content", message.text()));
        }
        return result;
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize LangGraph request", ex);
        }
    }

    /** Decodes every JSON document in one SSE data block (bare-newline framing tolerated). */
    private List<Object> readEventBlock(String data) {
        List<Object> events = new ArrayList<>();
        try (MappingIterator<Object> values = objectMapper.readerFor(Object.class).readValues(data)) {
            while (values.hasNext()) {
                Object event = values.next();
                if (event != null) {
                    events.add(event);
                }
            }
        } catch (IOException | RuntimeException ex) {
            if (events.isEmpty()) {
                return List.of(data);
            }
            // The corrupt remainder of a partially-parsed block may hold the terminal
            // event; surfacing a structured error keeps the failure visible instead of
            // letting the stream drain into a false COMPLETED.
            events.add(Map.of(
                    "error", "LANGGRAPH_RUNTIME_PARSE",
                    "message", "malformed SSE data block remainder dropped after "
                            + events.size() + " parsed event(s): "
                            + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())));
        }
        return events;
    }
}
