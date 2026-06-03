package com.huawei.ascend.service.access.protocol.async;

public interface AsyncQueueReplySink {

    String REPLY_QUEUE_ID_HEADER = "replyQueueId";

    void send(AsyncQueueReply reply);
}
