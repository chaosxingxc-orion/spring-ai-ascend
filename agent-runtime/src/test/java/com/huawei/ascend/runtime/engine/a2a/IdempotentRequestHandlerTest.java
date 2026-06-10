package com.huawei.ascend.runtime.engine.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.ascend.runtime.idempotency.InMemoryIdempotencyStore;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class IdempotentRequestHandlerTest {

    private final RequestHandler delegate = mock(RequestHandler.class);
    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    private final InMemoryTaskStore taskStore = new InMemoryTaskStore();
    private final IdempotentRequestHandler handler =
            new IdempotentRequestHandler(delegate, store, taskStore);

    /** A retried message/send replays the created task instead of executing twice. */
    @Test
    void retriedSendReplaysTheCreatedTask() throws A2AError {
        Task created = task("task-1", TaskState.TASK_STATE_COMPLETED);
        taskStore.save(created, true);
        when(delegate.onMessageSend(any(), any())).thenReturn(created);

        EventKind first = handler.onMessageSend(params("msg-1"), context("bank-7"));
        EventKind second = handler.onMessageSend(params("msg-1"), context("bank-7"));

        assertThat(first).isEqualTo(created);
        assertThat(((Task) second).id()).isEqualTo("task-1");
        verify(delegate, times(1)).onMessageSend(any(), any());
    }

    /** The replay answers the CURRENT task state, not a snapshot from send time. */
    @Test
    void replayReflectsLatestTaskState() throws A2AError {
        Task created = task("task-1", TaskState.TASK_STATE_WORKING);
        taskStore.save(created, true);
        when(delegate.onMessageSend(any(), any())).thenReturn(created);
        handler.onMessageSend(params("msg-1"), context("bank-7"));

        taskStore.save(task("task-1", TaskState.TASK_STATE_COMPLETED), true);
        Task replayed = (Task) handler.onMessageSend(params("msg-1"), context("bank-7"));

        assertThat(replayed.status().state()).isEqualTo(TaskState.TASK_STATE_COMPLETED);
    }

    /** Identical messageIds from different tenants are independent sends. */
    @Test
    void dedupKeyIsTenantScoped() throws A2AError {
        when(delegate.onMessageSend(any(), any()))
                .thenReturn(task("task-1", TaskState.TASK_STATE_COMPLETED))
                .thenReturn(task("task-2", TaskState.TASK_STATE_COMPLETED));

        handler.onMessageSend(params("msg-1"), context("bank-7"));
        handler.onMessageSend(params("msg-1"), context("bank-8"));

        verify(delegate, times(2)).onMessageSend(any(), any());
    }

    /** A failed send releases the claim so the client can retry. */
    @Test
    void failedSendStaysRetryable() throws A2AError {
        when(delegate.onMessageSend(any(), any()))
                .thenThrow(new RuntimeException("transient"))
                .thenReturn(task("task-1", TaskState.TASK_STATE_COMPLETED));

        assertThatThrownBy(() -> handler.onMessageSend(params("msg-1"), context("bank-7")))
                .isInstanceOf(RuntimeException.class);
        EventKind retried = handler.onMessageSend(params("msg-1"), context("bank-7"));

        assertThat(((Task) retried).id()).isEqualTo("task-1");
        verify(delegate, times(2)).onMessageSend(any(), any());
    }

    /** A send without a messageId cannot be deduplicated and passes straight through. */
    @Test
    void sendWithoutMessageIdIsNotDeduplicated() throws A2AError {
        when(delegate.onMessageSend(any(), any())).thenReturn(task("task-1", TaskState.TASK_STATE_COMPLETED));

        handler.onMessageSend(params(null), context("bank-7"));
        handler.onMessageSend(params(null), context("bank-7"));

        verify(delegate, times(2)).onMessageSend(any(), any());
    }

    private static MessageSendParams params(String messageId) {
        Message.Builder message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.<Part<?>>of(new TextPart("ping")));
        if (messageId != null) {
            message.messageId(messageId);
        }
        return new MessageSendParams(message.build(), null, null);
    }

    private static ServerCallContext context(String tenantId) {
        return new ServerCallContext(null,
                Map.of(A2aAgentExecutor.TENANT_STATE_KEY, tenantId), Set.of());
    }

    private static Task task(String id, TaskState state) {
        return new Task(id, "ctx-1", new TaskStatus(state), List.of(), List.of(), Map.of());
    }
}
