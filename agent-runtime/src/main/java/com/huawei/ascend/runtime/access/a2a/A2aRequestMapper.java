package com.huawei.ascend.runtime.access.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.access.AccessCancelCommand;
import com.huawei.ascend.runtime.common.AgentRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses inbound A2A JSON-RPC {@code params} into the runtime's neutral request
 * types ({@link AgentRequest}, {@link AccessCancelCommand}, {@link A2aTaskQueryParams}).
 *
 * <p>Split out of {@code A2aJsonRpcHandler} so the protocol-to-domain mapping —
 * field extraction, metadata merging, tenant/agent defaulting and session-id
 * resolution — lives in one place separate from JSON-RPC envelope dispatch.
 * A2A-specific wire fields stay confined here; callers downstream only ever see
 * the neutral request types.
 */
final class A2aRequestMapper {

    private final ObjectMapper objectMapper;
    private final A2aAccessProperties properties;

    A2aRequestMapper(ObjectMapper objectMapper, A2aAccessProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    AgentRequest toAgentRequest(JsonNode params) {
        if (params == null || params.isNull()) {
            throw new IllegalArgumentException("Missing JSON-RPC params");
        }
        JsonNode message = required(params, "message");
        JsonNode paramsMetadata = object(params.get("metadata"));
        JsonNode messageMetadata = object(message.get("metadata"));
        Map<String, Object> paramsMetadataMap = metadataMap(paramsMetadata);
        Map<String, Object> messageMetadataMap = metadataMap(messageMetadata);
        HashMap<String, Object> mergedMetadata = new HashMap<>(paramsMetadataMap);
        mergedMetadata.putAll(messageMetadataMap);
        JsonNode metadata = objectMapper.valueToTree(mergedMetadata);
        String contextId = text(message.get("contextId"));
        String sessionId = firstText(metadata.get("sessionId"), message.get("contextId"));
        validatePushNotificationConfig(params);

        HashMap<String, Object> requestMetadata = new HashMap<>();
        requestMetadata.put("parts", parts(message.get("parts")));
        requestMetadata.put("paramsMetadata", paramsMetadataMap);
        requestMetadata.put("messageMetadata", messageMetadataMap);
        requestMetadata.put("metadata", mergedMetadata);
        putIfPresent(requestMetadata, "contextId", contextId);
        putIfPresent(requestMetadata, "correlationId", text(metadata.get("correlationId")));
        requestMetadata.putAll(mergedMetadata);
        return new AgentRequest(
                requiredTextOrDefault(
                        firstText(params.get("tenant"), metadata.get("tenantId")),
                        properties.getDefaultTenantId(),
                        "A2A params.tenant"),
                requiredText(metadata, "userId"),
                requiredTextOrDefault(text(metadata.get("agentId")),
                        properties.getDefaultAgentId(),
                        "A2A metadata.agentId"),
                optionalSessionId(sessionId),
                List.of(com.huawei.ascend.runtime.common.Message.user(messageText(message))),
                text(metadata.get("idempotencyKey")),
                requestMetadata);
    }

    AccessCancelCommand toCancelCommand(JsonNode params) {
        if (params == null || params.isNull()) {
            throw new IllegalArgumentException("Missing JSON-RPC params");
        }
        JsonNode metadata = object(params.get("metadata"));
        String taskId = firstText(params.get("id"), params.get("taskId"));
        return new AccessCancelCommand(
                requiredTextOrDefault(
                        text(metadata.get("tenantId")),
                        properties.getDefaultTenantId(),
                        "A2A metadata.tenantId"),
                requiredText(metadata, "userId"),
                requiredTextOrDefault(
                        text(metadata.get("agentId")),
                        properties.getDefaultAgentId(),
                        "A2A metadata.agentId"),
                normalizeSessionId(firstText(metadata.get("sessionId"), metadata.get("contextId")),
                        text(metadata.get("contextId"))),
                taskId,
                null,
                Map.of("taskId", taskId == null ? "" : taskId));
    }

    A2aTaskQueryParams toTaskQuery(JsonNode params) {
        if (params == null || params.isNull()) {
            throw new IllegalArgumentException("Missing JSON-RPC params");
        }
        JsonNode metadata = object(params.get("metadata"));
        return new A2aTaskQueryParams(
                requiredTextOrDefault(
                        text(metadata.get("tenantId")),
                        properties.getDefaultTenantId(),
                        "A2A metadata.tenantId"),
                requiredText(metadata, "sessionId"),
                firstText(params.get("id"), params.get("taskId")));
    }

    private void validatePushNotificationConfig(JsonNode params) {
        JsonNode config = params.path("configuration").path("taskPushNotificationConfig");
        if (config == null || config.isMissingNode() || config.isNull()) {
            return;
        }
        String url = text(config.get("url"));
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Missing A2A taskPushNotificationConfig.url");
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing A2A params." + field);
        }
        return value;
    }

    private static JsonNode object(JsonNode node) {
        return node == null || node.isNull() || !node.isObject()
                ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                : node;
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node.get(field));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing A2A metadata." + field);
        }
        return value;
    }

    private String requiredTextOrDefault(String value, String defaultValue, String field) {
        if (value == null || value.isBlank()) {
            value = defaultValue;
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return value;
    }

    private String firstText(JsonNode first, JsonNode second) {
        String value = text(first);
        return value == null || value.isBlank() ? text(second) : value;
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String optionalSessionId(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? null : sessionId;
    }

    private static String normalizeSessionId(String sessionId, String fallback) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return java.util.UUID.randomUUID().toString();
    }

    private String messageText(JsonNode message) {
        JsonNode parts = message.get("parts");
        if (parts == null || !parts.isArray()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : parts) {
            String text = text(part.get("text"));
            if (text != null && !text.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(text);
            }
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private List<Object> parts(JsonNode parts) {
        if (parts == null || !parts.isArray()) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (JsonNode part : parts) {
            result.add(objectMapper.convertValue(part, Object.class));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadataMap(JsonNode metadata) {
        return objectMapper.convertValue(metadata, Map.class);
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
