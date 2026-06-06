package com.huawei.ascend.runtime.engine.adapters.dify;

import com.huawei.ascend.runtime.common.InvocationRequest;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentDriver;
import com.huawei.ascend.runtime.engine.spi.OutputConverter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Remote-protocol {@link com.huawei.ascend.runtime.engine.spi.AgentDriver} for Dify. Calls a Dify
 * application's {@code /chat-messages} endpoint with {@code response_mode=streaming} and returns the
 * raw SSE body as the opaque native stream; {@link DifyOutputConverter} turns Dify's SSE events into
 * the neutral {@code RunEvent} stream.
 *
 * <p>This is the second adapter shape: where the in-process adapters embed a Java framework, this
 * one fronts an existing remote Dify deployment over REST + SSE — existing Dify workflows are reused
 * as-is, with all tools / memory / nodes staying inside Dify. The runtime core is unchanged.
 */
public final class DifyAgentDriver extends AbstractAgentDriver {

    private final String agentId;
    private final String apiBase;
    private final String apiKey;
    private final DifyOutputConverter outputConverter = new DifyOutputConverter();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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
        return "Dify application driven remotely over REST + SSE through the agent-runtime neutral SPI.";
    }

    @Override
    public String frameworkId() {
        return "dify";
    }

    @Override
    public Object invoke(InvocationRequest request) {
        String conversationId = request.sessionId() == null ? "" : request.sessionId();
        String body = "{"
                + "\"inputs\":{},"
                + "\"query\":" + jsonString(request.input()) + ","
                + "\"response_mode\":\"streaming\","
                + "\"conversation_id\":" + jsonString(conversationId) + ","
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
            return response.body();
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
