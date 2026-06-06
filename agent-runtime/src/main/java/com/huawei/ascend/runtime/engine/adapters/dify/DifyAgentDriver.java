package com.huawei.ascend.runtime.engine.adapters.dify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.InvocationRequest;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentDriver;
import com.huawei.ascend.runtime.engine.spi.OutputConverter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remote-protocol {@link com.huawei.ascend.runtime.engine.spi.AgentDriver} for Dify. Calls a Dify
 * application's {@code /chat-messages} endpoint with {@code response_mode=streaming} and returns the
 * SSE body; {@link DifyOutputConverter} turns Dify's SSE events into the neutral {@code RunEvent}
 * stream.
 *
 * <p>This is the second adapter shape: where the in-process adapters embed a Java framework, this
 * one fronts an existing remote Dify deployment over REST + SSE — existing Dify workflows are reused
 * as-is, with all tools / memory / nodes staying inside Dify. The runtime core is unchanged.
 *
 * <p><b>Conversation state.</b> Dify expects an empty {@code conversation_id} to open a new
 * conversation and the id it returns for every later turn. A2A callers only know their local
 * session id, so this driver keeps a {@code sessionId -> difyConversationId} map: the first turn of
 * a session sends an empty id, and the id Dify returns is stored and replayed on later turns of the
 * same session. (The map is unbounded for v1; eviction / multi-instance sharing is a follow-up.)
 *
 * <p><b>Buffering.</b> v1 reads the whole SSE response with {@code BodyHandlers.ofString()} before
 * converting it, so neutral {@code RunEvent} chunks are produced after Dify closes the response, not
 * incrementally. The chunk structure is preserved (one CHUNK per Dify message event); true
 * end-to-end incremental streaming additionally requires the engine dispatcher to route each
 * {@code RunEvent} as it arrives, which is a separate change.
 */
public final class DifyAgentDriver extends AbstractAgentDriver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String agentId;
    private final String apiBase;
    private final String apiKey;
    private final DifyOutputConverter outputConverter = new DifyOutputConverter();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ConcurrentHashMap<String, String> conversationBySession = new ConcurrentHashMap<>();

    /**
     * @param agentId logical agent id this driver answers for
     * @param apiBase Dify API base, e.g. {@code https://api.dify.ai/v1} (no trailing slash)
     * @param apiKey  Dify application API key (sent as {@code Authorization: Bearer ...})
     */
    public DifyAgentDriver(String agentId, String apiBase, String apiKey) {
        this.agentId = agentId;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return agentId;
    }

    @Override
    public String description() {
        return "Dify application driven remotely over REST + SSE (buffered) through the agent-runtime neutral SPI.";
    }

    @Override
    public String frameworkId() {
        return "dify";
    }

    @Override
    public Object invoke(InvocationRequest request) {
        String sessionId = request.sessionId();
        boolean sessionScoped = sessionId != null && !sessionId.isBlank();
        // Empty conversation_id starts a new Dify conversation; reuse the returned id for later turns.
        String difyConversationId = sessionScoped
                ? conversationBySession.getOrDefault(sessionId, "")
                : "";
        String body = "{"
                + "\"inputs\":{},"
                + "\"query\":" + jsonString(request.input()) + ","
                + "\"response_mode\":\"streaming\","
                + "\"conversation_id\":" + jsonString(difyConversationId) + ","
                + "\"user\":" + jsonString(request.requestId())
                + "}";
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/chat-messages"))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "Dify returned HTTP " + response.statusCode() + ": " + response.body());
            }
            String responseBody = response.body();
            if (sessionScoped) {
                String returnedConversationId = parseConversationId(responseBody);
                if (!returnedConversationId.isBlank()) {
                    conversationBySession.put(sessionId, returnedConversationId);
                }
            }
            return responseBody;
        } catch (IOException ex) {
            throw new IllegalStateException("Dify request failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dify request interrupted", ex);
        }
    }

    @Override
    public OutputConverter outputConverter() {
        return outputConverter;
    }

    /** Returns the first non-blank {@code conversation_id} carried by any SSE event, or "". */
    private static String parseConversationId(String sse) {
        for (String rawLine : sse.split("\\r?\\n")) {
            String line = rawLine.strip();
            if (!line.startsWith("data:")) {
                continue;
            }
            String json = line.substring("data:".length()).strip();
            if (json.isEmpty() || "[DONE]".equals(json)) {
                continue;
            }
            try {
                JsonNode node = MAPPER.readTree(json);
                String conversationId = node.path("conversation_id").asText("");
                if (!conversationId.isBlank()) {
                    return conversationId;
                }
            } catch (Exception ignored) {
                // skip malformed event lines
            }
        }
        return "";
    }

    private static String jsonString(String value) {
        String safe = value == null ? "" : value;
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
