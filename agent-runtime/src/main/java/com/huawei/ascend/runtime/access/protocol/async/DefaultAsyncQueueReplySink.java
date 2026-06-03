package com.huawei.ascend.service.access.protocol.async;

import com.huawei.ascend.service.queue.InternalEventQueue;
import com.huawei.ascend.service.queue.QueueManager;
import java.util.Objects;
import java.util.Optional;

public final class DefaultAsyncQueueReplySink implements AsyncQueueReplySink {

    private final QueueManager queueManager;

    public DefaultAsyncQueueReplySink(QueueManager queueManager) {
        this.queueManager = Objects.requireNonNull(queueManager, "queueManager");
    }

    @Override
    public void send(AsyncQueueReply reply) {
        Objects.requireNonNull(reply, "reply");
        replyQueueId(reply).ifPresent(queueId -> {
            InternalEventQueue<AsyncQueueReply> queue = queueManager.getOrCreate(queueId, AsyncQueueReply.class);
            queue.offer(reply);
        });
    }

    private Optional<String> replyQueueId(AsyncQueueReply reply) {
        Object value = reply.headers().get(REPLY_QUEUE_ID_HEADER);
        if (value == null) {
            return Optional.empty();
        }
        String queueId = String.valueOf(value).trim();
        return queueId.isEmpty() ? Optional.empty() : Optional.of(queueId);
    }
}
