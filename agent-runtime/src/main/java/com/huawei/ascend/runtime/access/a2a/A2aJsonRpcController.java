package com.huawei.ascend.runtime.access.a2a;

import java.io.IOException;
import java.util.Objects;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.spec.InternalError;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping({"/a2a", "/a2a/"})
public final class A2aJsonRpcController {

    private final A2aJsonRpcHandler handler;
    private final A2aOutputRegistry outputRegistry;

    public A2aJsonRpcController(
            A2aJsonRpcHandler handler,
            A2aOutputRegistry outputRegistry) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.outputRegistry = Objects.requireNonNull(outputRegistry, "outputRegistry");
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object handle(@RequestBody String body) {
        try {
            return handleStream(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(handler.handle(body));
        }
    }

    private SseEmitter handleStream(String body) {
        SseEmitter emitter = new SseEmitter(0L);
        A2aJsonRpcStreamExchange exchange;
        try {
            exchange = handler.openStream(body);
            send(emitter, exchange.acceptedResponse(), "jsonrpc");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            send(emitter, new SendStreamingMessageResponse(null, new InternalError(ex.getMessage())), "error");
            emitter.complete();
            return emitter;
        }

        Runnable unsubscribe = outputRegistry.subscribe(exchange.outputHandle(), output -> {
            send(emitter, new SendStreamingMessageResponse(exchange.id(), output.event()), "jsonrpc");
            if (output.terminal()) {
                emitter.complete();
            }
        });
        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(unsubscribe);
        emitter.onError(ignored -> unsubscribe.run());
        return emitter;
    }

    private void send(SseEmitter emitter, Object response, String eventName) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(handler.toJson(response)));
        } catch (IOException ex) {
            emitter.complete();
        }
    }
}
