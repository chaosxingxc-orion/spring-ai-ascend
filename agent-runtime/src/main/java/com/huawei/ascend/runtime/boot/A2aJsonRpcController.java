package com.huawei.ascend.runtime.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskQueryParams;
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
    private final ObjectMapper mapper = new ObjectMapper();

    public A2aJsonRpcController(RequestHandler handler) { this.handler = handler; }

    @PostMapping(value = {"/a2a", "/a2a/"})
    public Object handle(@RequestBody String body) {
        try {
            JsonNode r = mapper.readTree(body);
            String method = method(r);
            Object id = id(r);
            log.info("[A2A] {} id={}", method, id);
            if ("message/stream".equals(method) || "SendStreamingMessage".equals(method)
                    || "tasks/resubscribe".equals(method) || "SubscribeToTask".equals(method)) {
                return handleStream(r, method, id);
            }
            return handleBlocking(r, method, id);
        } catch (Exception e) {
            log.error("[A2A] error", e);
            return ResponseEntity.ok("{}");
        }
    }

    Flux<ServerSentEvent<String>> handleStream(JsonNode r, String method, Object id) throws IOException {
        var ctx = serverContext();
        Flow.Publisher<StreamingEventKind> publisher;
        if ("tasks/resubscribe".equals(method) || "SubscribeToTask".equals(method)) {
            JsonNode p = r.get("params");
            publisher = handler.onSubscribeToTask(new TaskIdParams(p.get("id").asText()), ctx);
        } else {
            publisher = handler.onMessageSendStream(
                    mapper.treeToValue(r.get("params"), MessageSendParams.class), ctx);
        }
        return Flux.from(FlowAdapters.toPublisher(publisher))
                .map(evt -> ServerSentEvent.<String>builder().event("jsonrpc")
                        .data(new SendStreamingMessageResponse(id, evt).toString()).build());
    }

    ResponseEntity<String> handleBlocking(JsonNode r, String method, Object id) throws IOException, A2AError {
        var ctx = serverContext();
        JsonNode p = r.get("params");
        Object result = switch (method) {
            case "message/send", "SendMessage" ->
                handler.onMessageSend(mapper.treeToValue(p, MessageSendParams.class), ctx);
            case "tasks/get", "GetTask" ->
                handler.onGetTask(new TaskQueryParams(p.get("id").asText()), ctx);
            case "tasks/cancel", "CancelTask" ->
                handler.onCancelTask(new CancelTaskParams(p.get("id").asText()), ctx);
            default -> throw new IllegalArgumentException("Unknown: " + method);
        };
        try {
            String json = mapper.writeValueAsString(Map.of("jsonrpc","2.0","id",id,"result",result));
            return ResponseEntity.ok(json);
        } catch (Exception e) {
            return ResponseEntity.ok("{}");
        }
    }

    private static String method(JsonNode r) { return r.has("method") ? r.get("method").asText() : null; }
    private static Object id(JsonNode r) { return r.has("id") && !r.get("id").isNull() ? r.get("id").asText() : null; }
    private static ServerCallContext serverContext() { return new ServerCallContext(null, Map.of(), Set.of()); }
}
