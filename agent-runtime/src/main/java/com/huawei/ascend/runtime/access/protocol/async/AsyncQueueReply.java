package com.huawei.ascend.service.access.protocol.async;

import java.util.Map;
import java.util.Objects;

public record AsyncQueueReply(
        String body,
        Map<String, Object> headers) {

    public AsyncQueueReply {
        Objects.requireNonNull(body, "body");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
