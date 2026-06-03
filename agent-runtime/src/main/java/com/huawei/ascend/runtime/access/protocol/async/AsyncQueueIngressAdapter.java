package com.huawei.ascend.service.access.protocol.async;

import com.huawei.ascend.service.access.protocol.a2a.jsonrpc.A2aJsonRpcHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

public final class AsyncQueueIngressAdapter implements AsyncQueueIngressPort {

    private final A2aJsonRpcHandler handler;
    private final Optional<AsyncQueueReplySink> replySink;

    public AsyncQueueIngressAdapter(A2aJsonRpcHandler handler) {
        this(handler, Optional.empty());
    }

    public AsyncQueueIngressAdapter(A2aJsonRpcHandler handler, Optional<AsyncQueueReplySink> replySink) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.replySink = Objects.requireNonNull(replySink, "replySink");
    }

    @Override
    public AsyncQueueReply enqueue(AsyncQueueMessage message) {
        Objects.requireNonNull(message, "message");
        String response = handler.handleToJson(message.body());
        AsyncQueueReply reply = new AsyncQueueReply(response, replyHeaders(message));
        replySink.ifPresent(sink -> sink.send(reply));
        return reply;
    }

    private Map<String, Object> replyHeaders(AsyncQueueMessage message) {
        LinkedHashMap<String, Object> headers = new LinkedHashMap<>(message.headers());
        headers.putIfAbsent("responseFormat", "A2A_JSON_RPC");
        return headers;
    }
}
