package com.huawei.ascend.runtime.access.protocol.async;

public interface AsyncQueueReplySink {

    String REPLY_QUEUE_ID_HEADER = "replyQueueId";

    void send(AsyncQueueReply reply);
}
