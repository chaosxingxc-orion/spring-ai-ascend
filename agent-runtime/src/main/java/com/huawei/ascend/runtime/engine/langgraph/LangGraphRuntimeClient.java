package com.huawei.ascend.runtime.engine.langgraph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.Messages;
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
import org.a2aproject.sdk.spec.Message;

/**
 * Streams a run against a remote LangGraph runtime over SSE. Unlike the
 * AgentScope dialect, LangGraph frames carry a meaningful {@code event:} name
 * (metadata / values / messages partial / updates / error / end), so each
 * emitted element is a map of {@code event} (name, possibly empty) and
 * {@code data} (the decoded JSON payload).
 */
public final class LangGraphRuntimeClient implements AutoCloseable {

    static final String EVENT_KEY = "event";
    static final String DATA_KEY = "data";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LangGraphRuntimeClientProperties properties;
    private final boolean ownsHttpClient;

    public LangGraphRuntimeClient(LangGraphRuntimeClientProperties properties) {
        this(HttpClient.newHttpClient(), new ObjectMapper(), properties, true);
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
        Iterator<String> iterator = lines.iterator();
        Spliterator<Map<String, Object>> spliterator =
                new Spliterators.AbstractSpliterator<>(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL) {
                    private final StringBuilder data = new StringBuilder();
                    private final Deque<Map<String, Object>> pending = new ArrayDeque<>();
                    private String eventName = "";
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
                                // A mid-stream disconnect surfaces as a structured failure event,
                                // matching the connect-time LANGGRAPH_RUNTIME_IO path.
                                terminated = true;
                                action.accept(ioFailure(ex));
                                return true;
                            }
                            if (line.isBlank()) {
                                flush();
                            } else if (line.startsWith("event:")) {
                                eventName = line.substring("event:".length()).trim();
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
                        String name = eventName;
                        eventName = "";
                        if (!hasData) {
                            // An end frame may carry no data line at all — still a real event.
                            if (!name.isBlank()) {
                                pending.add(event(name, null));
                            }
                            return;
                        }
                        String eventData = data.toString();
                        data.setLength(0);
                        hasData = false;
                        String sentinel = eventData.trim();
                        if (sentinel.isEmpty() || "[DONE]".equals(sentinel) || "null".equals(sentinel)) {
                            if (!name.isBlank()) {
                                pending.add(event(name, null));
                            }
                            return;
                        }
                        for (Object payload : readEventBlock(eventData)) {
                            pending.add(event(name, payload));
                        }
                    }
                };
        return StreamSupport.stream(spliterator, false).onClose(lines::close);
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
        return errorEvent("LANGGRAPH_RUNTIME_IO", failureMessage(ex));
    }

    private static String failureMessage(RuntimeException ex) {
        Throwable failure = ex;
        while ((failure instanceof CompletionException || failure instanceof UncheckedIOException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
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

    private List<Map<String, Object>> input(List<Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message message : messages) {
            result.add(Map.of(
                    "role", message.role() == Message.Role.ROLE_AGENT ? "assistant" : "user",
                    "content", Messages.text(message)));
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
        }
        return events;
    }
}
