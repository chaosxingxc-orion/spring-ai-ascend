package com.huawei.ascend.runtime.engine.agentscope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.a2a.Messages;
import org.a2aproject.sdk.spec.Message;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class AgentScopeRuntimeClient implements AutoCloseable {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AgentScopeRuntimeClientProperties properties;
    private final boolean ownsHttpClient;

    public AgentScopeRuntimeClient(AgentScopeRuntimeClientProperties properties) {
        this(HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build(),
                new ObjectMapper(), properties, true);
    }

    AgentScopeRuntimeClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            AgentScopeRuntimeClientProperties properties) {
        this(httpClient, objectMapper, properties, false);
    }

    AgentScopeRuntimeClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            AgentScopeRuntimeClientProperties properties,
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

    public Stream<Map<String, Object>> streamEvents(AgentScopeInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        HttpRequest request = HttpRequest.newBuilder(properties.endpoint())
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", invocation.tenantId())
                .header("X-Agent-Id", invocation.agentId())
                .header("X-Task-Id", invocation.taskId())
                // Bounds time-to-response-headers only; the SSE body streams unbounded
                // and stays cancellable through the raw stream's close().
                .timeout(properties.requestTimeout())
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
        Iterator<String> iterator = lines.iterator();
        Spliterator<Map<String, Object>> spliterator =
                new Spliterators.AbstractSpliterator<>(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL) {
                    private final StringBuilder data = new StringBuilder();
                    private final Deque<Map<String, Object>> pending = new ArrayDeque<>();
                    private boolean hasData;
                    private boolean terminated;

                    @Override
                    public boolean tryAdvance(Consumer<? super Map<String, Object>> action) {
                        while (true) {
                            Map<String, Object> event = pending.poll();
                            if (event != null) {
                                action.accept(event);
                                return true;
                            }
                            if (terminated) {
                                return false;
                            }
                            String line;
                            try {
                                if (!iterator.hasNext()) {
                                    terminated = true;
                                    flush();
                                    continue;
                                }
                                line = iterator.next();
                            } catch (RuntimeException ex) {
                                // A connection dropped mid-stream must surface as a structured
                                // failure event, matching the connect-time AGENTSCOPE_RUNTIME_IO path.
                                terminated = true;
                                action.accept(ioFailure(ex));
                                return true;
                            }
                            if (line.isBlank()) {
                                flush();
                            } else if (line.startsWith("data:")) {
                                appendData(line.substring("data:".length()));
                            }
                        }
                    }

                    private void appendData(String value) {
                        if (value.startsWith(" ")) {
                            value = value.substring(1);
                        }
                        if (hasData) {
                            data.append('\n');
                        }
                        data.append(value);
                        hasData = true;
                    }

                    private void flush() {
                        if (!hasData) {
                            return;
                        }
                        String eventData = data.toString();
                        data.setLength(0);
                        hasData = false;
                        String sentinel = eventData.trim();
                        if (sentinel.isEmpty() || "[DONE]".equals(sentinel) || "null".equals(sentinel)) {
                            return;
                        }
                        pending.addAll(readEventBlock(eventData));
                    }
                };
        return StreamSupport.stream(spliterator, false).onClose(lines::close);
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
        Throwable failure = ex;
        while ((failure instanceof CompletionException || failure instanceof UncheckedIOException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
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
                    "role", toAgentScopeRole(message.role()),
                    "content", List.of(Map.of("type", "text", "text", Messages.text(message)))));
        }
        return result;
    }

    private static String toAgentScopeRole(Message.Role role) {
        return role == Message.Role.ROLE_AGENT ? "assistant" : "user";
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize AgentScope request", ex);
        }
    }

    /**
     * Parses one SSE data block. A spec-compliant block holds a single JSON
     * document (possibly pretty-printed across lines), but upstreams that frame
     * events with bare newlines instead of blank lines arrive here merged, so
     * every JSON document in the block is decoded as its own event.
     */
    private List<Map<String, Object>> readEventBlock(String data) {
        List<Map<String, Object>> events = new ArrayList<>();
        try (MappingIterator<Map<String, Object>> values = objectMapper.readerFor(MAP_TYPE).readValues(data)) {
            while (values.hasNext()) {
                Map<String, Object> event = values.next();
                if (event != null) {
                    events.add(event);
                }
            }
        } catch (IOException | RuntimeException ex) {
            if (events.isEmpty()) {
                return List.of(Map.of("status", "output", "text", data));
            }
            // The corrupt remainder of a partially-parsed block may hold the terminal
            // event; surfacing a structured error keeps the failure visible instead of
            // letting the stream drain into a false COMPLETED.
            events.add(Map.of(
                    "status", "error",
                    "error_code", "AGENTSCOPE_RUNTIME_PARSE",
                    "message", "malformed SSE data block remainder dropped after "
                            + events.size() + " parsed event(s): "
                            + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())));
        }
        return events;
    }
}
