package com.huawei.ascend.runtime.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Flow;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.reactivestreams.FlowAdapters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController
public class A2aController {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2aController.class);

    private final ObjectProvider<RequestHandler> requestHandlerProvider;
    private final ObjectMapper objectMapper;

    public A2aController(ObjectProvider<RequestHandler> requestHandlerProvider, ObjectMapper objectMapper) {
        this.requestHandlerProvider = requestHandlerProvider;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = {"/a2a", "/a2a/"})
    public Object handle(@RequestBody String body) {
        RequestHandler handler = requestHandlerProvider.getIfAvailable();
        if (handler == null) {
            LOGGER.warn("[A2A-ctrl] no RequestHandler available");
            return ResponseEntity.status(503).body(
                    error(null, "A2A request handler not available"));
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String method = method(root);
            Object id = id(root);
            LOGGER.info("[A2A-ctrl] request method={} id={}", method, id);
            if (method == null) {
                return error(id, "Missing method");
            }
            return switch (method) {
                case "message/stream" -> handleStream(handler, id, root);
                case "tasks/resubscribe" -> handleResubscribe(handler, id, root);
                default -> handleNonStream(handler, id, root, method);
            };
        } catch (Exception e) {
            LOGGER.error("[A2A-ctrl] dispatch error", e);
            return ResponseEntity.ok(error(null, e.getMessage()));
        }
    }

    private SseEmitter handleStream(RequestHandler handler, Object id, JsonNode root) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            MessageSendParams params = objectMapper.treeToValue(root.get("params"), MessageSendParams.class);
            LOGGER.info("[A2A-ctrl] stream params msgRole={} taskId={} contextId={}",
                    params.message() != null ? params.message().role() : "null",
                    params.message() != null ? params.message().taskId() : "null",
                    params.message() != null ? params.message().contextId() : "null");
            Flow.Publisher<StreamingEventKind> publisher = handler.onMessageSendStream(params, serverCallContext());
            Flux.from(FlowAdapters.toPublisher(publisher))
                    .doOnSubscribe(s -> LOGGER.info("[A2A-ctrl] sse subscribed id={}", id))
                    .doOnNext(event -> LOGGER.info("[A2A-ctrl] sse event id={} kind={}", id,
                            event != null ? event.getClass().getSimpleName() : "null"))
                    .doOnComplete(() -> LOGGER.info("[A2A-ctrl] sse complete id={}", id))
                    .doOnError(e -> LOGGER.error("[A2A-ctrl] sse error id={}", id, e))
                    .subscribe(
                            event -> sendSse(emitter, id, event),
                            error -> { sendSseError(emitter, id, error); emitter.complete(); },
                            () -> emitter.complete());
        } catch (Exception e) {
            sendSseError(emitter, id, e);
            emitter.complete();
        }
        return emitter;
    }

    private SseEmitter handleResubscribe(RequestHandler handler, Object id, JsonNode root) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            JsonNode params = root.get("params");
            TaskIdParams taskParams = new TaskIdParams(params.get("id").asText(),
                    params.has("tenant") ? params.get("tenant").asText() : null);
            Flow.Publisher<StreamingEventKind> publisher = handler.onSubscribeToTask(taskParams, serverCallContext());
            Flux.from(FlowAdapters.toPublisher(publisher))
                    .doOnSubscribe(s -> LOGGER.info("[A2A-ctrl] sse subscribed id={}", id))
                    .doOnNext(event -> LOGGER.info("[A2A-ctrl] sse event id={} kind={}", id,
                            event != null ? event.getClass().getSimpleName() : "null"))
                    .doOnComplete(() -> LOGGER.info("[A2A-ctrl] sse complete id={}", id))
                    .doOnError(e -> LOGGER.error("[A2A-ctrl] sse error id={}", id, e))
                    .subscribe(
                            event -> sendSse(emitter, id, event),
                            error -> { sendSseError(emitter, id, error); emitter.complete(); },
                            () -> emitter.complete());
        } catch (Exception e) {
            sendSseError(emitter, id, e);
            emitter.complete();
        }
        return emitter;
    }

    private ResponseEntity<String> handleNonStream(RequestHandler handler, Object id, JsonNode root, String method) {
        try {
            Object result = switch (method) {
                case "message/send" -> handler.onMessageSend(
                        objectMapper.treeToValue(root.get("params"), MessageSendParams.class),
                        serverCallContext());
                case "tasks/get" -> handler.onGetTask(
                        new org.a2aproject.sdk.spec.TaskQueryParams(root.get("params").get("id").asText()),
                        serverCallContext());
                case "tasks/cancel" -> handler.onCancelTask(
                        new org.a2aproject.sdk.spec.CancelTaskParams(root.get("params").get("id").asText()),
                        serverCallContext());
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };
            return ResponseEntity.ok(toJsonRpcResponse(id, result));
        } catch (Exception e) {
            return ResponseEntity.ok(toJsonRpcError(id, e.getMessage()));
        }
    }

    private void sendSse(SseEmitter emitter, Object id, StreamingEventKind event) {
        try {
            emitter.send(ServerSentEvent.builder().event("jsonrpc")
                    .data(objectMapper.writeValueAsString(new SendStreamingMessageResponse(id, event))).build());
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendSseError(SseEmitter emitter, Object id, Throwable error) {
        try {
            emitter.send(ServerSentEvent.builder().event("error")
                    .data(objectMapper.writeValueAsString(
                            new SendStreamingMessageResponse(id, new InternalError(error.getMessage())))).build());
        } catch (IOException ignored) {}
    }

    private String toJsonRpcResponse(Object id, Object result) throws Exception {
        Map<String, Object> r = new HashMap<>();
        r.put("jsonrpc", "2.0"); r.put("id", id); r.put("result", result);
        return objectMapper.writeValueAsString(r);
    }

    private String error(Object id, String message) {
        return toJsonRpcError(id, message);
    }

    private String toJsonRpcError(Object id, String message) {
        try {
            Map<String, Object> err = new HashMap<>();
            err.put("code", -32603);
            err.put("message", message);
            Map<String, Object> r = new HashMap<>();
            r.put("jsonrpc", "2.0"); r.put("id", id); r.put("error", err);
            return objectMapper.writeValueAsString(r);
        } catch (Exception e) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"internal\"}}";
        }
    }

    private String method(JsonNode root) {
        JsonNode m = root.get("method");
        return m == null ? null : m.asText();
    }

    private Object id(JsonNode root) {
        JsonNode i = root.get("id");
        return i == null || i.isNull() ? null : i.isTextual() ? i.asText() : i.asInt();
    }

    private static ServerCallContext serverCallContext() {
        return new ServerCallContext(null, new HashMap<>(), java.util.Set.of());
    }
}
