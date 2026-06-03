package com.huawei.ascend.service.access.protocol.async;

public interface AsyncQueueIngressPort {

    AsyncQueueReply enqueue(AsyncQueueMessage message);
}
