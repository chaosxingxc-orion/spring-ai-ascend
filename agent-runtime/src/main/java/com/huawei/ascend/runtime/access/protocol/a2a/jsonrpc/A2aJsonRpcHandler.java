package com.huawei.ascend.runtime.access.protocol.a2a.jsonrpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.InvalidProtocolBufferException;
import com.huawei.ascend.runtime.access.core.AccessSubmissionService;
import com.huawei.ascend.runtime.access.model.AccessAcceptedResponse;
import com.huawei.ascend.runtime.access.model.AccessCancelCommand;
import com.huawei.ascend.runtime.access.protocol.a2a.A2aAccessProperties;
import com.huawei.ascend.runtime.access.protocol.a2a.egress.A2aOutput;
import com.huawei.ascend.runtime.access.protocol.a2a.egress.A2aOutputHandle;
import com.huawei.ascend.runtime.access.protocol.a2a.egress.A2aOutputRegistry;
import com.huawei.ascend.runtime.access.protocol.a2a.egress.A2aTaskMapper;
import com.huawei.ascend.runtime.access.protocol.a2a.model.A2aAcceptedResponse;
import com.huawei.ascend.runtime.access.protocol.a2a.model.A2aTaskQueryParams;
import com.huawei.ascend.runtime.schema.AgentRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidRequestError;
import org.a2aproject.sdk.spec.JSONParseError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;

public final class A2aJsonRpcHandler {

    private static final String METHOD_SEND_MESSAGE = "SendMessage";
    private static final String METHOD_SEND_STREAMING_MESSAGE = "SendStreamingMessage";
    private static final String METHOD_GET_TASK = "GetTask";
    private static final String METHOD_CANCEL_TASK = "CancelTask";
    private static final String METHOD_MESSAGE_SEND = "message/send";
    private static final String METHOD_MESSAGE_STREAM = "message/stream";
    private static final String METHOD_TASKS_GET = "tasks/get";
    private static final String METHOD_TASKS_CANCEL = "tasks/cancel";

    private final AccessSubmissionService submissionService;
    private final A2aOutputRegistry outputRegistry;
    private final ObjectMapper objectMapper;
    private final A2aAccessProperties properties;

    public A2aJsonRpcHandler(
            AccessSubmissionService submissionService,
            A2aOutputRegistry outputRegistry,
            ObjectMapper objectMapper,
            A2aAccessProperties properties) {
        this.submissionService = Objects.requireNonNull(submissionService, "submissionService");
        this.outputRegistry = Objects.requireNonNull(outputRegistry, "outputRegistry");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public Object handle(String body) {
        JsonNode root = readRoot(body);
        if (root == null) {
            return new A2AErrorResponse(null, new JSONParseError("Invalid JSON-RPC body"));
        }
        Object id = jsonRpcId(root);
        String methodName = text(root.get("method"));
        if (methodName == null || methodName.isBlank()) {
            return new A2AErrorResponse(id, new InvalidRequestError("Missing JSON-RPC method"));
        }
        if (!"2.0".equals(text(root.get("jsonrpc")))) {
            return new A2AErrorResponse(id, new InvalidRequestError("JSON-RPC version must be 2.0"));
        }
        try {
            if (METHOD_SEND_MESSAGE.equals(methodName) || METHOD_MESSAGE_SEND.equals(methodName)) {
                validateCanonicalMethod(body, methodName, METHOD_SEND_MESSAGE, SendMessageRequest.class);
                return handleSend(id, root.get("params"));
            }
            if (METHOD_GET_TASK.equals(methodName) || METHOD_TASKS_GET.equals(methodName)) {
                validateCanonicalMethod(body, methodName, METHOD_GET_TASK, GetTaskRequest.class);
                return handleGetTask(id, root.get("params"));
            }
            if (METHOD_CANCEL_TASK.equals(methodName) || METHOD_TASKS_CANCEL.equals(methodName)) {
                validateCanonicalMethod(body, methodName, METHOD_CANCEL_TASK, CancelTaskRequest.class);
                return handleCancel(id, root.get("params"));
            }
            if (METHOD_SEND_STREAMING_MESSAGE.equals(methodName) || METHOD_MESSAGE_STREAM.equals(methodName)) {
                return new A2AErrorResponse(
                        id, new InvalidRequestError("SendStreamingMessage is only supported by HTTP/SSE transport"));
            }
            return new A2AErrorResponse(id, new InvalidRequestError("Unsupported A2A JSON-RPC method: " + methodName));
        } catch (org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException ex) {
            return new A2AErrorResponse(id, new InvalidRequestError(ex.getMessage()));
        }
    }

    public String handleToJson(String body) {
        return toJson(handle(body));
    }

    public A2aJsonRpcStreamExchange openStream(String body) {
        JsonNode root = readRoot(body);
        if (root == null) {
            throw new IllegalArgumentException("Invalid JSON-RPC body");
        }
        Object id = jsonRpcId(root);
        String methodName = text(root.get("method"));
        if (!METHOD_SEND_STREAMING_MESSAGE.equals(methodName) && !METHOD_MESSAGE_STREAM.equals(methodName)) {
            throw new IllegalArgumentException("JSON-RPC method must be SendStreamingMessage");
        }
        if (!"2.0".equals(text(root.get("jsonrpc")))) {
            throw new IllegalArgumentException("JSON-RPC version must be 2.0");
        }
        A2aAcceptedResponse accepted = submit(toAgentRequest(root.get("params")));
        return new A2aJsonRpcStreamExchange(
                id,
                new SendStreamingMessageResponse(id, toAcceptedMessage(accepted)),
                outputHandle(accepted));
    }

    public String toJson(Object response) {
        try {
            JsonNode json = objectMapper.valueToTree(response);
            if (normalizeStreamingResponseResult(response, json)) {
                removeNullFields(json);
            } else {
                normalizeA2aWireValues(json);
            }
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException | InvalidProtocolBufferException ex) {
            throw new IllegalStateException("Failed to serialize A2A JSON-RPC response", ex);
        }
    }

    private JsonNode readRoot(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Object jsonRpcId(JsonNode root) {
        JsonNode id = root == null ? null : root.get("id");
        if (id == null || id.isNull()) {
            return null;
        }
        if (id.isNumber()) {
            return id.numberValue();
        }
        if (id.isBoolean()) {
            return id.booleanValue();
        }
        return id.asText();
    }

    private void validateCanonicalMethod(String body, String methodName, String canonicalMethod, Class<?> requestType)
            throws org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException {
        if (canonicalMethod.equals(methodName)) {
            JsonUtil.fromJson(body, requestType);
        }
    }

    private Message toAcceptedMessage(A2aAcceptedResponse accepted) {
        return A2aTaskMapper.agentMessage(
                accepted.sessionId(),
                accepted.taskId(),
                accepted.message() == null ? "accepted" : accepted.message(),
                Map.of(
                        "tenantId", accepted.tenantId(),
                        "userId", accepted.userId(),
                        "agentId", accepted.agentId(),
                        "accepted", accepted.accepted()));
    }

    private A2aOutputHandle outputHandle(A2aAcceptedResponse accepted) {
        return new A2aOutputHandle(accepted.tenantId(), accepted.sessionId(), accepted.taskId());
    }

    private SendMessageResponse handleSend(Object id, JsonNode params) {
        try {
            A2aAcceptedResponse accepted = submit(toAgentRequest(params));
            return new SendMessageResponse(id, toAcceptedMessage(accepted));
        } catch (IllegalArgumentException ex) {
            return new SendMessageResponse(id, new InvalidRequestError(ex.getMessage()));
        } catch (RuntimeException ex) {
            return new SendMessageResponse(id, new InternalError(ex.getMessage()));
        }
    }

    private GetTaskResponse handleGetTask(Object id, JsonNode params) {
        try {
            A2aTaskQueryParams query = toTaskQuery(params);
            A2aOutputHandle handle = new A2aOutputHandle(query.tenantId(), query.sessionId(), query.taskId());
            List<A2aOutput> outputs = outputRegistry.list(handle);
            return new GetTaskResponse(id, A2aTaskMapper.toTask(query, outputs));
        } catch (IllegalArgumentException ex) {
            return new GetTaskResponse(id, new InvalidRequestError(ex.getMessage()));
        } catch (RuntimeException ex) {
            return new GetTaskResponse(id, new InternalError(ex.getMessage()));
        }
    }

    private CancelTaskResponse handleCancel(Object id, JsonNode params) {
        try {
            A2aAcceptedResponse accepted = cancel(toCancelCommand(params));
            return new CancelTaskResponse(id, A2aTaskMapper.canceledTask(accepted));
        } catch (IllegalArgumentException ex) {
            return new CancelTaskResponse(id, new InvalidRequestError(ex.getMessage()));
        } catch (RuntimeException ex) {
            return new CancelTaskResponse(id, new InternalError(ex.getMessage()));
        }
    }

    private A2aAcceptedResponse submit(AgentRequest request) {
        return toA2aAcceptedResponse(submissionService.run(request).toCompletableFuture().join());
    }

    private A2aAcceptedResponse cancel(AccessCancelCommand command) {
        return toA2aAcceptedResponse(submissionService.cancel(command).toCompletableFuture().join());
    }

    private static A2aAcceptedResponse toA2aAcceptedResponse(AccessAcceptedResponse response) {
        return new A2aAcceptedResponse(
                response.tenantId(),
                response.userId(),
                response.agentId(),
                response.sessionId(),
                response.taskId(),
                response.accepted(),
                response.message());
    }

    private AgentRequest toAgentRequest(JsonNode params) {
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
                List.of(com.huawei.ascend.runtime.schema.Message.user(messageText(message))),
                text(metadata.get("idempotencyKey")),
                requestMetadata);
    }

    private AccessCancelCommand toCancelCommand(JsonNode params) {
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

    private A2aTaskQueryParams toTaskQuery(JsonNode params) {
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

    private boolean normalizeStreamingResponseResult(Object response, JsonNode json)
            throws JsonProcessingException, InvalidProtocolBufferException {
        if (!(response instanceof SendStreamingMessageResponse streamingResponse)
                || !(json instanceof ObjectNode object)
                || !(streamingResponse.getResult() instanceof StreamingEventKind streamingEvent)) {
            return false;
        }
        String streamResponseJson = com.google.protobuf.util.JsonFormat.printer()
                .omittingInsignificantWhitespace()
                .print(ProtoUtils.ToProto.streamResponse(streamingEvent));
        object.set("result", objectMapper.readTree(streamResponseJson));
        return true;
    }

    private void normalizeA2aWireValues(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node instanceof ArrayNode array) {
            array.forEach(this::normalizeA2aWireValues);
            return;
        }
        if (!(node instanceof ObjectNode object)) {
            return;
        }
        normalizeTaskState(object);
        normalizeMessageRole(object);
        normalizePartKind(object);
        object.elements().forEachRemaining(this::normalizeA2aWireValues);
        removeNullFields(object);
    }

    private void removeNullFields(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node instanceof ArrayNode array) {
            array.forEach(this::removeNullFields);
            return;
        }
        if (!(node instanceof ObjectNode object)) {
            return;
        }
        object.elements().forEachRemaining(this::removeNullFields);
        List<String> nullFields = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue() == null || field.getValue().isNull()) {
                nullFields.add(field.getKey());
            }
        }
        nullFields.forEach(object::remove);
    }

    private void normalizeTaskState(ObjectNode object) {
        JsonNode state = object.get("state");
        if (state == null || !state.isTextual()) {
            return;
        }
        String value = switch (state.asText()) {
            case "TASK_STATE_SUBMITTED" -> "submitted";
            case "TASK_STATE_WORKING" -> "working";
            case "TASK_STATE_INPUT_REQUIRED" -> "input-required";
            case "TASK_STATE_AUTH_REQUIRED" -> "auth-required";
            case "TASK_STATE_COMPLETED" -> "completed";
            case "TASK_STATE_CANCELED" -> "canceled";
            case "TASK_STATE_FAILED" -> "failed";
            case "TASK_STATE_REJECTED" -> "rejected";
            default -> null;
        };
        if (value != null) {
            object.put("state", value);
        }
    }

    private void normalizeMessageRole(ObjectNode object) {
        JsonNode role = object.get("role");
        if (role == null || !role.isTextual()) {
            return;
        }
        String value = switch (role.asText()) {
            case "ROLE_AGENT" -> "agent";
            case "ROLE_USER" -> "user";
            default -> null;
        };
        if (value != null) {
            object.put("role", value);
        }
    }

    private void normalizePartKind(ObjectNode object) {
        if (object.has("kind")) {
            return;
        }
        if (object.has("text")) {
            object.put("kind", "text");
        } else if (object.has("file")) {
            object.put("kind", "file");
        } else if (object.has("data")) {
            object.put("kind", "data");
        }
    }
}
