package com.huawei.ascend.service.access.protocol.a2a.jsonrpc;

import com.huawei.ascend.service.access.protocol.a2a.egress.A2aOutputHandle;

public record A2aJsonRpcStreamExchange(
        Object id,
        Object acceptedResponse,
        A2aOutputHandle outputHandle) {
}
