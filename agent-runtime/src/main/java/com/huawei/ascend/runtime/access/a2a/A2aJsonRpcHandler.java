package com.huawei.ascend.runtime.access.a2a;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.huawei.ascend.runtime.access.AccessAcceptedResponse;
import com.huawei.ascend.runtime.access.AccessCancelCommand;
import com.huawei.ascend.runtime.access.AccessSubmissionService;
import com.huawei.ascend.runtime.common.AgentRequest;
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
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidRequestError;
import org.a2aproject.sdk.spec.JSONParseError;
import org.a2aproject.sdk.spec.Message;

/**
 * Routes A2A JSON-RPC calls onto the access layer.
 *
 * <p>Responsible only for the JSON-RPC envelope: parse the root, validate
 * version/method, dispatch to the matching access call, and serialize the
 * response. Inbound {@code params} parsing is delegated to {@link
 * A2aRequestMapper} and outbound wire normalization to {@link A2aResponseMapper},
 * so this class stays focused on dispatch.
 */
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
    private final A2aRequestMapper requestMapper;
    private final A2aResponseMapper responseMapper;

    public A2aJsonRpcHandler(
            AccessSubmissionService submissionService,
            A2aOutputRegistry outputRegistry,
            ObjectMapper objectMapper,
            A2aAccessProperties properties) {
        this.submissionService = Objects.requireNonNull(submissionService, "submissionService");
        this.outputRegistry = Objects.requireNonNull(outputRegistry, "outputRegistry");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(properties, "properties");
        this.requestMapper = new A2aRequestMapper(objectMapper, properties);
        this.responseMapper = new A2aResponseMapper(objectMapper);
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
        A2aAcceptedResponse accepted = submit(requestMapper.toAgentRequest(root.get("params")));
        return new A2aJsonRpcStreamExchange(
                id,
                new SendStreamingMessageResponse(id, toAcceptedMessage(accepted)),
                outputHandle(accepted));
    }

    public String toJson(Object response) {
        try {
            JsonNode json = objectMapper.valueToTree(response);
            responseMapper.normalizeWire(response, json);
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
            A2aAcceptedResponse accepted = submit(requestMapper.toAgentRequest(params));
            return new SendMessageResponse(id, toAcceptedMessage(accepted));
        } catch (IllegalArgumentException ex) {
            return new SendMessageResponse(id, new InvalidRequestError(ex.getMessage()));
        } catch (RuntimeException ex) {
            return new SendMessageResponse(id, new InternalError(ex.getMessage()));
        }
    }

    private GetTaskResponse handleGetTask(Object id, JsonNode params) {
        try {
            A2aTaskQueryParams query = requestMapper.toTaskQuery(params);
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
            A2aAcceptedResponse accepted = cancel(requestMapper.toCancelCommand(params));
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

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
