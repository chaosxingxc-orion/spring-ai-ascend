package com.huawei.ascend.runtime.common;

import java.util.Map;

/**
 * Framework-neutral run input. The access layer converts a protocol request (A2A first) into
 * this; the engine and every framework driver consume only this type. Text-first for v1
 * ({@code input} is plain text); multimodal payloads extend it later without changing the seam.
 *
 * <p>Naming note: "invocation request" is the neutral, industry-common shape (cf. langchain4j
 * {@code ChatRequest}, Spring AI {@code Prompt}); kept deliberately distinct from any one
 * framework's request type.
 */
public record InvocationRequest(
        String requestId,
        String agentId,
        String sessionId,
        String input,
        Map<String, Object> metadata) {

    public InvocationRequest(String requestId, String agentId, String sessionId, String input) {
        this(requestId, agentId, sessionId, input, Map.of());
    }

    public InvocationRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
