package com.huawei.ascend.runtime.boot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Flow;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.reactivestreams.FlowAdapters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * Maps A2A JSON-RPC calls onto the SDK {@link RequestHandler}.
 * Follows the same pattern as agentscope-runtime-java's
 * {@code A2aController}: deserialize → dispatch → serialize.
 * Kept minimal — all A2A logic lives in the SDK's DefaultRequestHandler.
 */
@RestController
public class A2aController {

    private static final Logger log = LoggerFactory.getLogger(A2aController.class);
    private final ObjectProvider<RequestHandler> handlerProvider;
    private final ObjectMapper mapper;

    public A2aController(ObjectProvider<RequestHandler> handlerProvider, ObjectMapper mapper) {
        this.handlerProvider = handlerProvider;
        this.mapper = mapper;
    }

    @PostMapping(value = {"/a2a", "/a2a/"})
    public Object handle(@RequestBody String body) {
        RequestHandler h = handlerProvider.getIfAvailable();
        if (h == null) return ResponseEntity.status(503).body(error(null, "No handler"));

        try {
            JsonNode r = mapper.readTree(body);
            String m = r.has("method") ? r.get("method").asText() : null;
            Object id = r.has("id") && !r.get("id").isNull() ? (r.get("id").isTextual() ? r.get("id").asText() : r.get("id").asInt()) : null;
            log.info("[A2A] {} id={}", m, id);
            if (m == null) return error(id, "Missing method");

            boolean streaming = "message/stream".equals(m) || "SendStreamingMessage".equals(m)
                    || "tasks/resubscribe".equals(m) || "SubscribeToTask".equals(m);
            if (streaming) return stream(h, r, id, m);
            return result(h, r, id, m);

        } catch (Exception e) { log.error("[A2A] dispatch error", e); return error(null, e.getMessage()); }
    }

    // ── streaming SSE ──

    private SseEmitter stream(RequestHandler h, JsonNode r, Object id, String method) throws Exception {
        SseEmitter emitter = new SseEmitter(0L);
        JsonNode params = r.get("params");
        ServerCallContext ctx = new ServerCallContext(null, new HashMap<>(), java.util.Set.of());

        Flow.Publisher<StreamingEventKind> publisher;
        if ("message/stream".equals(method) || "SendStreamingMessage".equals(method))
            publisher = h.onMessageSendStream(mapper.treeToValue(params, MessageSendParams.class), ctx);
        else
            publisher = h.onSubscribeToTask(new TaskIdParams(params.get("id").asText(),
                    params.has("tenant") ? params.get("tenant").asText() : null), ctx);

        Flux.from(FlowAdapters.toPublisher(publisher)).subscribe(
                evt -> { try { emitter.send(sse("jsonrpc", sseFrame(id, evt))); } catch (IOException ex) { emitter.completeWithError(ex); } },
                err -> { try { emitter.send(sse("error", errFrame(id, err))); } catch (IOException ignored) {} emitter.complete(); },
                emitter::complete);
        return emitter;
    }

    // ── non-streaming ──

    private ResponseEntity<String> result(RequestHandler h, JsonNode r, Object id, String method) throws Exception {
        ServerCallContext ctx = new ServerCallContext(null, new HashMap<>(), java.util.Set.of());
        JsonNode params = r.get("params");
        Object result;
        try {
            result = switch (method) {
                case "message/send", "SendMessage" -> h.onMessageSend(mapper.treeToValue(params, MessageSendParams.class), ctx);
                case "tasks/get", "GetTask" -> h.onGetTask(new org.a2aproject.sdk.spec.TaskQueryParams(params.get("id").asText()), ctx);
                case "tasks/cancel", "CancelTask" -> h.onCancelTask(new org.a2aproject.sdk.spec.CancelTaskParams(params.get("id").asText()), ctx);
                default -> throw new IllegalArgumentException("Unknown: " + method);
            };
        } catch (Exception e) { return ResponseEntity.ok(errFrame(id, e)); }
        return ResponseEntity.ok(json(id, result));
    }

    // ── helpers ──

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    private String sseFrame(Object id, Object result) throws JsonProcessingException {
        Map<String, Object> f = new HashMap<>(); f.put("jsonrpc", "2.0"); f.put("id", id); f.put("result", result);
        return mapper.writeValueAsString(f);
    }

    private String json(Object id, Object result) throws JsonProcessingException {
        return sseFrame(id, result);
    }

    private String error(Object id, String message) {
        try { return errFrame(id, new RuntimeException(message)); } catch (Exception e) { return "{}"; }
    }

    private String errFrame(Object id, Throwable err) throws JsonProcessingException {
        Map<String, Object> e = new HashMap<>(); e.put("code", -32603); e.put("message", err.getMessage());
        Map<String, Object> f = new HashMap<>(); f.put("jsonrpc", "2.0"); f.put("id", id); f.put("error", e);
        return mapper.writeValueAsString(f);
    }
}
