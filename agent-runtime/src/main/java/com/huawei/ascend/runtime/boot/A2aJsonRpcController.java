package com.huawei.ascend.runtime.boot;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import com.huawei.ascend.runtime.engine.a2a.A2aAgentExecutor;
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
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class A2aJsonRpcController {
    private static final Logger log = LoggerFactory.getLogger(A2aJsonRpcController.class);
    private final RequestHandler handler;
    private final RuntimeAccessProperties access;

    public A2aJsonRpcController(RequestHandler handler, RuntimeAccessProperties access) {
        this.handler = handler;
        this.access = access;
    }

    @PostMapping(value = {"/a2a", "/a2a/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public Object handle(@RequestBody String body,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantHeader) {
        Object id = null;
        try {
            A2ARequest<?> request = JSONRPCUtils.parseRequestBody(body, null);
            id = request.getId();
            log.info("[A2A] {} id={}", request.getMethod(), id);
            if (request instanceof SendStreamingMessageRequest || request instanceof SubscribeToTaskRequest) {
                return handleStream(request, tenantHeader);
            }
            return handleBlocking(request, tenantHeader);
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
    public Flux<ServerSentEvent<String>> handleSse(@RequestBody String body,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantHeader) {
        A2ARequest<?> request;
        try {
            request = JSONRPCUtils.parseRequestBody(body, null);
        } catch (Exception e) {
            log.warn("[A2A] stream request parse failed: {}", e.getMessage());
            return Flux.just(errorEvent(null, error(A2AErrorCodes.JSON_PARSE, e.getMessage())));
        }
        try {
            return handleStream(request, tenantHeader);
        } catch (A2AError e) {
            log.warn("[A2A] {} id={} failed code={} message={}",
                    request.getMethod(), request.getId(), e.getCode(), e.getMessage());
            return Flux.just(errorEvent(request.getId(), ensureCode(e, A2AErrorCodes.INTERNAL)));
        } catch (Exception e) {
            log.error("[A2A] {} id={} failed", request.getMethod(), request.getId(), e);
            return Flux.just(errorEvent(request.getId(), error(A2AErrorCodes.INTERNAL, e.getMessage())));
        }
    }

    Flux<ServerSentEvent<String>> handleStream(A2ARequest<?> request, String tenantHeader) {
        var ctx = serverContext(tenantHeader);
        Object id = request.getId();
        Flow.Publisher<StreamingEventKind> publisher;
        if (request instanceof SubscribeToTaskRequest subscribe) {
            publisher = handler.onSubscribeToTask(subscribe.getParams(), ctx);
        } else if (request instanceof SendStreamingMessageRequest send) {
            publisher = handler.onMessageSendStream(send.getParams(), ctx);
        } else {
            throw error(A2AErrorCodes.METHOD_NOT_FOUND, "Unknown streaming request: " + request.getMethod());
        }
        return Flux.from(FlowAdapters.toPublisher(publisher))
                .map(evt -> ServerSentEvent.<String>builder().event("jsonrpc")
                        .data(streamingResponseJson(id, evt)).build())
                // A mid-stream failure must end with a JSON-RPC error frame, not a bare
                // transport drop — clients cannot otherwise tell agent failure from network loss.
                .onErrorResume(e -> {
                    log.error("[A2A] stream failed id={}", id, e);
                    A2AError fault = e instanceof A2AError a2aError
                            ? ensureCode(a2aError, A2AErrorCodes.INTERNAL)
                            : error(A2AErrorCodes.INTERNAL, e.getMessage());
                    return Flux.just(errorEvent(id, fault));
                });
    }

    ResponseEntity<String> handleBlocking(A2ARequest<?> request, String tenantHeader) throws A2AError {
        var ctx = serverContext(tenantHeader);
        A2AResponse<?> response = switch (request) {
            case SendMessageRequest send ->
                    new SendMessageResponse(request.getId(), handler.onMessageSend(send.getParams(), ctx));
            case GetTaskRequest get ->
                    new GetTaskResponse(request.getId(), handler.onGetTask(get.getParams(), ctx));
            case ListTasksRequest list ->
                    new ListTasksResponse(request.getId(), handler.onListTasks(list.getParams(), ctx));
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
            default -> throw error(A2AErrorCodes.METHOD_NOT_FOUND, "Unknown: " + request.getMethod());
        };
        try {
            return ResponseEntity.ok(JsonUtil.toJson(response));
        } catch (Exception e) {
            log.error("[A2A] response serialization failed id={}", request.getId(), e);
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

    private static ServerSentEvent<String> errorEvent(Object id, A2AError error) {
        return ServerSentEvent.<String>builder().event("jsonrpc")
                .data(JSONRPCUtils.toJsonRPCErrorResponse(id, error)).build();
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

    /**
     * The raw {@code X-Tenant-Id} header travels through the call-context state
     * and takes precedence downstream over the client-self-declared
     * params.tenant. The runtime performs no authentication of its own: in any
     * multi-tenant deployment a fronting gateway must strip the header from
     * client traffic and re-inject it after authenticating the caller, otherwise
     * the header is as client-controlled as params.tenant.
     */
    private ServerCallContext serverContext(String tenantHeader) {
        String tenant = tenantHeader == null || tenantHeader.isBlank()
                ? access.getDefaultTenantId() : tenantHeader.trim();
        return new ServerCallContext(null, Map.of(A2aAgentExecutor.TENANT_STATE_KEY, tenant), Set.of());
    }
}
