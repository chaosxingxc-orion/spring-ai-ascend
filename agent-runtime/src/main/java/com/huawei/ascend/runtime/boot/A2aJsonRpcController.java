package com.huawei.ascend.runtime.boot;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.jsonrpc.common.json.JsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.json.MethodNotFoundJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2ARequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CreateTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CreateTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.DeleteTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.DeleteTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTaskPushNotificationConfigsRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTaskPushNotificationConfigsResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SubscribeToTaskRequest;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.A2AErrorCodes;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.reactivestreams.FlowAdapters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class A2aJsonRpcController {
    private static final Logger log = LoggerFactory.getLogger(A2aJsonRpcController.class);
    private final RequestHandler handler;

    public A2aJsonRpcController(RequestHandler handler) { this.handler = handler; }

    @PostMapping(value = {"/a2a", "/a2a/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public Object handle(@RequestBody String body) {
        Object id = null;
        try {
            A2ARequest<?> request = JSONRPCUtils.parseRequestBody(body, null);
            id = request.getId();
            log.info("[A2A] {} id={}", request.getMethod(), id);
            if (request instanceof SendStreamingMessageRequest || request instanceof SubscribeToTaskRequest) {
                return handleStream(request);
            }
            return handleBlocking(request);
        } catch (A2AError e) {
            // Protocol error raised by the SDK or the request handler — surface it with its own code.
            return errorResponse(id, ensureCode(e, A2AErrorCodes.INTERNAL));
        } catch (MethodNotFoundJsonMappingException e) {
            return errorResponse(e.getId(), error(A2AErrorCodes.METHOD_NOT_FOUND, e.getMessage()));
        } catch (JsonMappingException e) {
            // Well-formed JSON whose shape does not match any A2A request.
            return errorResponse(id, error(A2AErrorCodes.INVALID_REQUEST, e.getMessage()));
        } catch (JsonProcessingException | com.google.gson.JsonParseException e) {
            // Body is not parseable JSON (the SDK parses with Gson, whose JsonSyntaxException is unchecked).
            return errorResponse(id, error(A2AErrorCodes.JSON_PARSE, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return errorResponse(id, error(A2AErrorCodes.METHOD_NOT_FOUND, e.getMessage()));
        } catch (Exception e) {
            log.error("[A2A] unexpected error id={}", id, e);
            return errorResponse(id, error(A2AErrorCodes.INTERNAL, e.getMessage()));
        }
    }

    @PostMapping(value = {"/a2a", "/a2a/"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> handleSse(@RequestBody String body) throws Exception {
        return handleStream(JSONRPCUtils.parseRequestBody(body, null));
    }

    Flux<ServerSentEvent<String>> handleStream(A2ARequest<?> request) throws A2AError {
        var ctx = serverContext();
        Flow.Publisher<StreamingEventKind> publisher;
        if (request instanceof SubscribeToTaskRequest subscribe) {
            publisher = handler.onSubscribeToTask(subscribe.getParams(), ctx);
        } else if (request instanceof SendStreamingMessageRequest send) {
            publisher = handler.onMessageSendStream(send.getParams(), ctx);
        } else {
            throw new IllegalArgumentException("Unknown streaming request: " + request.getMethod());
        }
        return Flux.from(FlowAdapters.toPublisher(publisher))
                .map(evt -> ServerSentEvent.<String>builder().event("jsonrpc")
                        .data(streamingResponseJson(request.getId(), evt)).build());
    }

    ResponseEntity<String> handleBlocking(A2ARequest<?> request) throws A2AError {
        var ctx = serverContext();
        A2AResponse<?> response = switch (request) {
            case SendMessageRequest send ->
                    new SendMessageResponse(request.getId(), handler.onMessageSend(send.getParams(), ctx));
            case GetTaskRequest get ->
                    new GetTaskResponse(request.getId(), handler.onGetTask(get.getParams(), ctx));
            case CancelTaskRequest cancel ->
                    new CancelTaskResponse(request.getId(), handler.onCancelTask(cancel.getParams(), ctx));
            case CreateTaskPushNotificationConfigRequest create -> new CreateTaskPushNotificationConfigResponse(
                    request.getId(), handler.onCreateTaskPushNotificationConfig(create.getParams(), ctx));
            case GetTaskPushNotificationConfigRequest get -> new GetTaskPushNotificationConfigResponse(
                    request.getId(), handler.onGetTaskPushNotificationConfig(get.getParams(), ctx));
            case ListTaskPushNotificationConfigsRequest list -> new ListTaskPushNotificationConfigsResponse(
                    request.getId(), handler.onListTaskPushNotificationConfigs(list.getParams(), ctx));
            case DeleteTaskPushNotificationConfigRequest delete -> {
                handler.onDeleteTaskPushNotificationConfig(delete.getParams(), ctx);
                yield new DeleteTaskPushNotificationConfigResponse(request.getId());
            }
            default -> throw new IllegalArgumentException("Unknown: " + request.getMethod());
        };
        try {
            return ResponseEntity.ok(JsonUtil.toJson(response));
        } catch (Exception e) {
            return errorResponse(request.getId(),
                    error(A2AErrorCodes.INTERNAL, "failed to serialize A2A response: " + e.getMessage()));
        }
    }

    private static String streamingResponseJson(Object id, StreamingEventKind event) {
        try {
            return JsonUtil.toJson(new SendStreamingMessageResponse(id, event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize A2A stream event", e);
        }
    }

    private static ResponseEntity<String> errorResponse(Object id, A2AError error) {
        return ResponseEntity.ok(JSONRPCUtils.toJsonRPCErrorResponse(id, error));
    }

    private static A2AError error(A2AErrorCodes code, String message) {
        return new A2AError(code.code(), message, null);
    }

    /** Guarantees a non-null JSON-RPC code so {@code toJsonRPCErrorResponse} never dereferences null. */
    private static A2AError ensureCode(A2AError error, A2AErrorCodes fallback) {
        return error.getCode() != null
                ? error
                : new A2AError(fallback.code(), error.getMessage(), error.getDetails());
    }

    private static ServerCallContext serverContext() { return new ServerCallContext(null, Map.of(), Set.of()); }
}
