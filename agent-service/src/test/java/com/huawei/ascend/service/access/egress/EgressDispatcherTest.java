package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.access.model.NotificationType;
import com.huawei.ascend.service.access.model.ReplyChannel;
import com.huawei.ascend.service.queue.QueueManager;
import com.huawei.ascend.service.schema.RunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class EgressDispatcherTest {

    @Test
    void startIsIdempotentForSingleConsumerQueue() {
        QueueManager queueManager = new QueueManager();
        EgressQueueRegistry registry = new DefaultEgressQueueRegistry(queueManager);
        RecordingEgressAdapter adapter = new RecordingEgressAdapter();
        Executor directExecutor = Runnable::run;
        EgressDispatcher dispatcher = new EgressDispatcher(registry, List.of(adapter), directExecutor);
        EgressBinding binding = new EgressBinding("tenant", "session", "task", ReplyChannel.A2A,
                "stream", "target", "correlation", Map.of());

        assertThatCode(() -> {
            dispatcher.start(binding);
            dispatcher.start(binding);
        }).doesNotThrowAnyException();

        registry.getOrCreate(binding).offer(new NotificationFrame("tenant", "session", "task",
                NotificationType.ACK, RunStatus.COMPLETED, List.of(), null, Map.of(), true));

        assertThat(adapter.frames()).hasSize(1);
        assertThat(queueManager.find("tenant:session:task:egress")).isEmpty();
    }

    private static final class RecordingEgressAdapter implements EgressAdapter {

        private final List<NotificationFrame> frames = new CopyOnWriteArrayList<>();

        @Override
        public ReplyChannel channel() {
            return ReplyChannel.A2A;
        }

        @Override
        public void deliver(EgressBinding binding, NotificationFrame frame) {
            frames.add(frame);
        }

        List<NotificationFrame> frames() {
            return frames;
        }
    }
}
