package com.huawei.ascend.service.access.protocol.a2a;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidRequestError;
import org.a2aproject.sdk.spec.JSONParseError;
import org.a2aproject.sdk.spec.Message;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/a2a")
public final class A2aJsonRpcController {

    private static final String METHOD_SEND_MESSAGE = "SendMessage";
    private static final String METHOD_SEND_STREAMING_MESSAGE = "SendStreamingMessage";
    private static final String METHOD_GET_TASK = "GetTask";
    private static final String METHOD_CANCEL_TASK = "CancelTask";

    private final A2aAccessService accessService;
    private final A2aOutputRegistry outputRegistry;
    private final ObjectMapper objectMapper;

    public A2aJsonRpcController(
            A2aAccessService accessService,
            A2aOutputRegistry outputRegistry,
            ObjectMapper objectMapper) {
        this.accessService = Objects.requireNonNull(accessService, "accessService");
        this.outputRegistry = Objects.requireNonNull(outputRegistry, "outputRegistry");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @PostMapping(
            value = "/",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object handle(@RequestBody String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            return ResponseEntity.ok(new A2AErrorResponse(null, new JSONParseError("Invalid JSON-RPC body")));
        }
        Object id = jsonRpcId(root);
        String methodName = text(root.get("method"));
        if (methodName == null || methodName.isBlank()) {
            return ResponseEntity.ok(new A2AErrorResponse(id, new InvalidRequestError("Missing JSON-RPC method")));
        }
        if (!"2.0".equals(text(root.get("jsonrpc")))) {
            return ResponseEntity.ok(new A2AErrorResponse(id, new InvalidRequestError("JSON-RPC version must be 2.0")));
        }
        try {
            if (METHOD_SEND_MESSAGE.equals(methodName)) {
                JsonUtil.fromJson(body, SendMessageRequest.class);
                return ResponseEntity.ok(handleSend(id, root.get("params")));
            }
            if (METHOD_SEND_STREAMING_MESSAGE.equals(methodName)) {
                JsonUtil.fromJson(body, SendStreamingMessageRequest.class);
                return handleStream(id, root.get("params"));
            }
            if (METHOD_GET_TASK.equals(methodName)) {
                JsonUtil.fromJson(body, GetTaskRequest.class);
                return ResponseEntity.ok(handleGetTask(id, root.get("params")));
            }
            if (METHOD_CANCEL_TASK.equals(methodName)) {
                JsonUtil.fromJson(body, CancelTaskRequest.class);
                return ResponseEntity.ok(handleCancel(id, root.get("params")));
            }
            return ResponseEntity.ok(new A2AErrorResponse(
                    id, new InvalidRequestError("Unsupported A2A JSON-RPC method: " + methodName)));
        } catch (org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException ex) {
            return ResponseEntity.ok(new A2AErrorResponse(id, new InvalidRequestError(ex.getMessage())));
        }
    }

    private SendMessageResponse handleSend(Object id, JsonNode params) {
        try {
            A2aAcceptedResponse accepted = accessService.send(toEnvelope(params));
            return new SendMessageResponse(id, toAcceptedMessage(accepted));
        } catch (IllegalArgumentException ex) {
            return new SendMessageResponse(id, new InvalidRequestError(ex.getMessage()));
        } catch (RuntimeException ex) {
            return new SendMessageResponse(id, new InternalError(ex.getMessage()));
        }
    }

    private SseEmitter handleStream(Object id, JsonNode params) {
        SseEmitter emitter = new SseEmitter(0L);
        A2aAcceptedResponse accepted;
        try {
            accepted = accessService.stream(toEnvelope(params));
            send(emitter, new SendStreamingMessageResponse(id, toAcceptedMessage(accepted)), "jsonrpc");
        } catch (IllegalArgumentException ex) {
            send(emitter, new SendStreamingMessageResponse(id, new InvalidRequestError(ex.getMessage())), "error");
            emitter.complete();
            return emitter;
        } catch (RuntimeException ex) {
            send(emitter, new SendStreamingMessageResponse(id, new InternalError(ex.getMessage())), "error");
            emitter.complete();
            return emitter;
        }

        A2aOutputHandle handle = new A2aOutputHandle(
                accepted.tenantId(),
                accepted.sessionId(),
                accepted.taskId());
        Runnable unsubscribe = outputRegistry.subscribe(handle, output -> {
            send(emitter, new SendStreamingMessageResponse(id, output.event()), "jsonrpc");
            if (output.terminal()) {
                emitter.complete();
            }
        });
        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(unsubscribe);
        emitter.onError(ignored -> unsubscribe.run());
        return emitter;
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
            A2aAcceptedResponse accepted = accessService.cancel(toCancelEnvelope(params));
            return new CancelTaskResponse(id, A2aTaskMapper.canceledTask(accepted));
        } catch (IllegalArgumentException ex) {
            return new CancelTaskResponse(id, new InvalidRequestError(ex.getMessage()));
        } catch (RuntimeException ex) {
            return new CancelTaskResponse(id, new InternalError(ex.getMessage()));
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

    private A2aEnvelope toEnvelope(JsonNode params) {
        if (params == null || params.isNull()) {
            throw new IllegalArgumentException("Missing JSON-RPC params");
        }
        JsonNode message = required(params, "message");
        JsonNode metadata = object(message.get("metadata"));
        String contextId = text(message.get("contextId"));
        String sessionId = firstText(metadata.get("sessionId"), message.get("contextId"));
        return new A2aEnvelope(
                new A2aEnvelope.A2aContext(
                        requiredText(params, metadata, "tenant", "tenantId"),
                        requiredText(metadata, "userId"),
                        requiredText(metadata, "agentId"),
                        sessionId,
                        contextId,
                        text(metadata.get("idempotencyKey")),
                        text(metadata.get("correlationId"))),
                new A2aEnvelope.A2aMessage(
                        messageText(message),
                        parts(message.get("parts")),
                        metadataMap(metadata)),
                pushNotificationConfig(params));
    }

    private A2aEnvelope toCancelEnvelope(JsonNode params) {
        if (params == null || params.isNull()) {
            throw new IllegalArgumentException("Missing JSON-RPC params");
        }
        JsonNode metadata = object(params.get("metadata"));
        String taskId = firstText(params.get("id"), params.get("taskId"));
        return new A2aEnvelope(
                new A2aEnvelope.A2aContext(
                        requiredText(metadata, "tenantId"),
                        requiredText(metadata, "userId"),
                        requiredText(metadata, "agentId"),
                        firstText(metadata.get("sessionId"), metadata.get("contextId")),
                        text(metadata.get("contextId")),
                        text(metadata.get("idempotencyKey")),
                        text(metadata.get("correlationId"))),
                new A2aEnvelope.A2aMessage(
                        null,
                        List.of(),
                        Map.of("taskId", taskId == null ? "" : taskId)),
                null);
    }

    private A2aEnvelope.A2aPushNotificationConfig pushNotificationConfig(JsonNode params) {
        JsonNode config = params.path("configuration").path("taskPushNotificationConfig");
        if (config == null || config.isMissingNode() || config.isNull()) {
            return null;
        }
        String url = text(config.get("url"));
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Missing A2A taskPushNotificationConfig.url");
        }
        JsonNode authentication = object(config.get("authentication"));
        return new A2aEnvelope.A2aPushNotificationConfig(
                text(config.get("id")),
                text(config.get("taskId")),
                url,
                text(config.get("token")),
                text(authentication.get("scheme")),
                text(authentication.get("credentials")),
                text(config.get("tenant")));
    }

    private A2aTaskQueryParams toTaskQuery(JsonNode params) {
        if (params == null || params.isNull()) {
            throw new IllegalArgumentException("Missing JSON-RPC params");
        }
        JsonNode metadata = object(params.get("metadata"));
        return new A2aTaskQueryParams(
                requiredText(metadata, "tenantId"),
                requiredText(metadata, "sessionId"),
                firstText(params.get("id"), params.get("taskId")));
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

    private String requiredText(JsonNode firstNode, JsonNode secondNode, String firstField, String secondField) {
        String value = firstText(firstNode.get(firstField), secondNode.get(secondField));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing A2A params." + firstField);
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

    private void send(SseEmitter emitter, Object response, String eventName) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(objectMapper.writeValueAsString(response)));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}
