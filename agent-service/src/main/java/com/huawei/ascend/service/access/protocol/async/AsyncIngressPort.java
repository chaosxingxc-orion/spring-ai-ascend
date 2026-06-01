package com.huawei.ascend.service.access.protocol.async;

public interface AsyncIngressPort {
    void enqueue(AsyncEnvelope envelope);
}
