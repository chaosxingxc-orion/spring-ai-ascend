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
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.reactivestreams.FlowAdapters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController
public class A2aController {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2aController.class);

    private final RequestHandler requestHandler;
    private final AgentCard agentCard;
    private final ObjectMapper objectMapper;

    public A2aController(RequestHandler requestHandler, AgentCard agentCard, ObjectMapper objectMapper) {
        this.requestHandler = requestHandler;
        this.agentCard = agentCard;
        this.objectMapper = objectMapper;
    }

    // ── Agent Card ──

    @GetMapping(value = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard agentCard() {
        return agentCard;
    }

    @GetMapping(value = "/.well-known/agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard agentCardLegacy() {
        return agentCard;
    }

    // ── A2A JSON-RPC ──

    @PostMapping(value = {"/a2a", "/a2a/"})
    public Object handle(@RequestBody String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String method = method(root);
            Object id = id(root);

            if (method == null) {
                return error(id, "Missing method");
            }

            return switch (method) {
                case "message/stream" -> handleStream(id, root);
                case "tasks/resubscribe" -> handleResubscribe(id, root);
                default -> handleNonStream(id, root, method);
            };
        } catch (Exception e) {
            LOGGER.error("A2A dispatch error", e);
            return ResponseEntity.ok(error(null, e.getMessage()));
        }
    }

    // ── Streaming ──

    private SseEmitter handleStream(Object id, JsonNode root) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            MessageSendParams params = objectMapper.treeToValue(
                    root.get("params"), MessageSendParams.class);
            Flow.Publisher<StreamingEventKind> publisher =
                    requestHandler.onMessageSendStream(params, serverCallContext());
            Flux<StreamingEventKind> flux = Flux.from(FlowAdapters.toPublisher(publisher));
            flux.subscribe(
                    event -> sendSse(emitter, id, event),
                    error -> { sendSseError(emitter, id, error); emitter.complete(); },
                    () -> emitter.complete());
        } catch (Exception e) {
            sendSseError(emitter, id, e);
            emitter.complete();
        }
        return emitter;
    }

    private SseEmitter handleResubscribe(Object id, JsonNode root) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            JsonNode params = root.get("params");
            TaskIdParams taskParams = new TaskIdParams(
                    params.get("id").asText(),
                    params.has("tenant") ? params.get("tenant").asText() : null);
            Flow.Publisher<StreamingEventKind> publisher =
                    requestHandler.onSubscribeToTask(taskParams, serverCallContext());
            Flux<StreamingEventKind> flux = Flux.from(FlowAdapters.toPublisher(publisher));
            flux.subscribe(
                    event -> sendSse(emitter, id, event),
                    error -> { sendSseError(emitter, id, error); emitter.complete(); },
                    () -> emitter.complete());
        } catch (Exception e) {
            sendSseError(emitter, id, e);
            emitter.complete();
        }
        return emitter;
    }

    // ── Non-streaming ──

    private ResponseEntity<String> handleNonStream(Object id, JsonNode root, String method) {
        try {
            Object result = switch (method) {
                case "message/send" -> {
                    MessageSendParams params = objectMapper.treeToValue(
                            root.get("params"), MessageSendParams.class);
                    yield requestHandler.onMessageSend(params, serverCallContext());
                }
                case "tasks/get" -> {
                    String taskId = root.get("params").get("id").asText();
                    yield requestHandler.onGetTask(
                            new org.a2aproject.sdk.spec.TaskQueryParams(taskId),
                            serverCallContext());
                }
                case "tasks/cancel" -> {
                    String taskId = root.get("params").get("id").asText();
                    yield requestHandler.onCancelTask(
                            new org.a2aproject.sdk.spec.CancelTaskParams(taskId),
                            serverCallContext());
                }
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };
            return ResponseEntity.ok(toJsonRpcResponse(id, result));
        } catch (Exception e) {
            return ResponseEntity.ok(toJsonRpcError(id, e.getMessage()));
        }
    }

    // ── Helpers ──

    private void sendSse(SseEmitter emitter, Object id, StreamingEventKind event) {
        try {
            String data = objectMapper.writeValueAsString(
                    new SendStreamingMessageResponse(id, event));
            emitter.send(ServerSentEvent.builder().event("jsonrpc").data(data).build());
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendSseError(SseEmitter emitter, Object id, Throwable error) {
        try {
            String data = objectMapper.writeValueAsString(
                    new SendStreamingMessageResponse(id, new InternalError(error.getMessage())));
            emitter.send(ServerSentEvent.builder().event("error").data(data).build());
        } catch (IOException ignored) {
        }
    }

    private String toJsonRpcResponse(Object id, Object result) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return objectMapper.writeValueAsString(response);
    }

    private String toJsonRpcError(Object id, String message) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("code", -32603);
            error.put("message", message);
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.put("error", error);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"internal\"}}";
        }
    }

    private String method(JsonNode root) {
        JsonNode method = root.get("method");
        return method == null ? null : method.asText();
    }

    private Object id(JsonNode root) {
        JsonNode id = root.get("id");
        return id == null || id.isNull() ? null :
                id.isTextual() ? id.asText() : id.asInt();
    }

    private String error(Object id, String message) {
        return toJsonRpcError(id, message);
    }

    private static ServerCallContext serverCallContext() {
        return new ServerCallContext(null, new HashMap<>(), java.util.Set.of());
    }
}
