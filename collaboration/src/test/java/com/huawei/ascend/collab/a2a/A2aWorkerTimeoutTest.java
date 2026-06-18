package com.huawei.ascend.collab.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.collab.core.SubTask;
import com.huawei.ascend.collab.core.TaskToken;
import com.huawei.ascend.collab.core.WorkResult;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.GetExtendedAgentCardParams;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.junit.jupiter.api.Test;

/**
 * The blocking A2A call must be wall-clock bounded: a hung remote yields TIMEOUT
 * (which the coordinator reclaims) instead of blocking the coordinator forever.
 */
class A2aWorkerTimeoutTest {

    @Test
    void hungRemoteYieldsTimeoutNotAHang() {
        ClientTransport hung = new HungTransport(2_000); // sleeps 2s on sendMessage
        A2aWorker worker = new A2aWorker("w", Set.of("cap"), hung, 200); // 200ms budget

        TaskToken token = TaskToken.issue("t1", "cap", "w", "tenant",
                UUID.randomUUID(), 30_000, 0L);

        long start = System.nanoTime();
        WorkResult r = worker.execute(SubTask.of("t1", "cap", "p"), token);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(WorkResult.Status.TIMEOUT, r.status(), "hung remote → TIMEOUT");
        assertTrue(elapsedMs < 1_500, "returned on the timeout budget, not the 2s hang (was " + elapsedMs + "ms)");
    }

    /** A transport whose sendMessage blocks; everything else is unused. */
    private static final class HungTransport implements ClientTransport {
        private final long sleepMs;

        HungTransport(long sleepMs) {
            this.sleepMs = sleepMs;
        }

        @Override
        public EventKind sendMessage(MessageSendParams params, ClientCallContext ctx) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }

        @Override
        public void sendMessageStreaming(MessageSendParams p, Consumer<StreamingEventKind> e,
                Consumer<Throwable> err, ClientCallContext c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Task getTask(TaskQueryParams p, ClientCallContext c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Task cancelTask(CancelTaskParams p, ClientCallContext c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ListTasksResult listTasks(ListTasksParams p, ClientCallContext c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskPushNotificationConfig createTaskPushNotificationConfiguration(
                TaskPushNotificationConfig p, ClientCallContext c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskPushNotificationConfig getTaskPushNotificationConfiguration(
                GetTaskPushNotificationConfigParams p, ClientCallContext c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ListTaskPushNotificationConfigsResult listTaskPushNotificationConfigurations(
                ListTaskPushNotificationConfigsParams p, ClientCallContext c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteTaskPushNotificationConfigurations(
                DeleteTaskPushNotificationConfigParams p, ClientCallContext c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void subscribeToTask(TaskIdParams p, Consumer<StreamingEventKind> e,
                Consumer<Throwable> err, ClientCallContext c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentCard getExtendedAgentCard(GetExtendedAgentCardParams p, ClientCallContext c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }
}
