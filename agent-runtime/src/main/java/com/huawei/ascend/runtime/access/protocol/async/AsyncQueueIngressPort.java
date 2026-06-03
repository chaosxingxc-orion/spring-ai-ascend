package com.huawei.ascend.runtime.access.protocol.async;

public interface AsyncQueueIngressPort {

    AsyncQueueReply enqueue(AsyncQueueMessage message);
}
