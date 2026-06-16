package com.huawei.ascend.collab.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.huawei.ascend.collab.core.CollaborationResult;
import com.huawei.ascend.collab.core.Coordinator;
import com.huawei.ascend.collab.core.SubTask;
import com.huawei.ascend.collab.core.TaskToken;
import com.huawei.ascend.collab.core.WorkResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
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
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.junit.jupiter.api.Test;

/**
 * Token-echo verification on the real A2A bridge: the coordinator must only accept a
 * remote whose response echoes the issued token. Before the fix, {@code A2aWorker} re-presented
 * the locally-issued token, so any real agent passed validation even without echoing — the check
 * held only for in-memory workers. These tests drive a fake transport that either echoes the
 * token (compliant agent) or not (non-compliant), and assert COMPLETED vs REJECTED.
 */
class A2aWorkerTokenEchoTest {

    @Test
    void echoingAgentIsAccepted() {
        A2aWorker worker = new A2aWorker("echo", Set.of("cap"), new FakeTransport(true), 1_000);
        Coordinator coordinator = new Coordinator(List.of(worker));

        CollaborationResult r = coordinator.run(List.of(SubTask.of("t1", "cap", "p")));
        assertEquals(WorkResult.Status.COMPLETED, r.outcomes().get("t1"),
                "agent echoed the token -> accepted");
    }

    @Test
    void nonEchoingAgentIsRejected() {
        A2aWorker worker = new A2aWorker("silent", Set.of("cap"), new FakeTransport(false), 1_000);
        Coordinator coordinator = new Coordinator(List.of(worker));

        CollaborationResult r = coordinator.run(List.of(SubTask.of("t1", "cap", "p")));
        assertEquals(WorkResult.Status.REJECTED, r.outcomes().get("t1"),
                "agent did NOT echo the token -> rejected (no longer auto-trusted)");
    }

    @Test
    void workerSurfacesTheEchoedTokenItParsed() {
        TaskToken token = TaskToken.issue("t1", "cap", "echo", "tenant",
                UUID.randomUUID(), 30_000, System.currentTimeMillis());

        WorkResult echoed = new A2aWorker("echo", Set.of("cap"), new FakeTransport(true), 1_000)
                .execute(SubTask.of("t1", "cap", "p"), token);
        assertNotNull(echoed.echoedToken(), "parsed the echoed token from response metadata");
        assertEquals(token.tokenId(), echoed.echoedToken().tokenId(), "echoed tokenId matches issued");

        WorkResult silent = new A2aWorker("silent", Set.of("cap"), new FakeTransport(false), 1_000)
                .execute(SubTask.of("t1", "cap", "p"), token);
        assertNull(silent.echoedToken(), "no echo in response -> null echoed token");
    }

    /** Fake transport: completes the task, optionally echoing the inbound token metadata. */
    private static final class FakeTransport implements ClientTransport {
        private final boolean echoToken;

        FakeTransport(boolean echoToken) {
            this.echoToken = echoToken;
        }

        @Override
        public EventKind sendMessage(MessageSendParams params, ClientCallContext ctx) {
            Map<String, Object> inbound = params.metadata();
            Map<String, Object> echo = new HashMap<>();
            if (echoToken && inbound != null) {
                for (String k : List.of(A2aWorker.MK_TOKEN, A2aWorker.MK_TASK,
                        A2aWorker.MK_IDEM, A2aWorker.MK_DEADLINE)) {
                    Object v = inbound.get(k);
                    if (v != null) {
                        echo.put(k, v);
                    }
                }
            }
            return Task.builder()
                    .id("remote-task-1")
                    .contextId(params.message() != null ? params.message().contextId() : "ctx")
                    .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                    .metadata(echo)
                    .build();
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
