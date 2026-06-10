package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.idempotency.IdempotencyStore;
import java.util.concurrent.Flow;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.A2AErrorCodes;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.EventKind;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Idempotent {@code message/send} (W1 contract, ADR-0027 adapted to the A2A
 * surface): the messageId is the idempotency key, scoped by tenant. A retry of
 * a completed send replays the current state of the task it created instead of
 * executing the agent twice; a concurrent duplicate while the original is
 * still executing is rejected. Streaming sends are not deduplicated — A2A
 * already provides SubscribeToTask for reattaching to a running task.
 */
public final class IdempotentRequestHandler implements RequestHandler {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotentRequestHandler.class);

    private final RequestHandler delegate;
    private final IdempotencyStore store;
    private final TaskStore taskStore;

    public IdempotentRequestHandler(RequestHandler delegate, IdempotencyStore store, TaskStore taskStore) {
        this.delegate = delegate;
        this.store = store;
        this.taskStore = taskStore;
    }

    @Override
    public EventKind onMessageSend(MessageSendParams params, ServerCallContext context) throws A2AError {
        String messageId = params.message() == null ? null : params.message().messageId();
        if (messageId == null || messageId.isBlank()) {
            return delegate.onMessageSend(params, context);
        }
        String tenantId = tenant(context);
        switch (store.claim(tenantId, messageId)) {
            case REPLAY -> {
                Task replayed = replayTask(tenantId, messageId);
                if (replayed != null) {
                    LOG.info("[A2A] idempotent replay tenantId={} messageId={} taskId={}",
                            tenantId, messageId, replayed.id());
                    return replayed;
                }
                // The recorded task vanished from the store — fall through to re-execution
                // after re-claiming, rather than answering with nothing.
                store.release(tenantId, messageId);
                return onMessageSend(params, context);
            }
            case IN_FLIGHT -> throw new A2AError(A2AErrorCodes.INVALID_REQUEST.code(),
                    "duplicate message/send is still executing: messageId=" + messageId, null);
            case ACQUIRED -> {
                // proceed below
            }
        }
        try {
            EventKind result = delegate.onMessageSend(params, context);
            if (result instanceof Task task) {
                store.complete(tenantId, messageId, task.id());
            } else {
                // Nothing replayable was produced — release so a retry can execute.
                store.release(tenantId, messageId);
            }
            return result;
        } catch (RuntimeException e) {
            // A failed send must stay retryable (ADR-0027 claim/replay semantics).
            store.release(tenantId, messageId);
            throw e;
        }
    }

    private Task replayTask(String tenantId, String messageId) {
        return store.completedReference(tenantId, messageId)
                .map(taskStore::get)
                .orElse(null);
    }

    private static String tenant(ServerCallContext context) {
        Object tenant = context == null ? null : context.getState().get(A2aAgentExecutor.TENANT_STATE_KEY);
        return tenant instanceof String value && !value.isBlank() ? value : "default";
    }

    // ── pure delegation below ──

    @Override
    public Flow.Publisher<StreamingEventKind> onMessageSendStream(
            MessageSendParams params, ServerCallContext context) throws A2AError {
        return delegate.onMessageSendStream(params, context);
    }

    @Override
    public Flow.Publisher<StreamingEventKind> onSubscribeToTask(TaskIdParams params, ServerCallContext context)
            throws A2AError {
        return delegate.onSubscribeToTask(params, context);
    }

    @Override
    public Task onGetTask(TaskQueryParams params, ServerCallContext context) throws A2AError {
        return delegate.onGetTask(params, context);
    }

    @Override
    public ListTasksResult onListTasks(ListTasksParams params, ServerCallContext context) throws A2AError {
        return delegate.onListTasks(params, context);
    }

    @Override
    public Task onCancelTask(CancelTaskParams params, ServerCallContext context) throws A2AError {
        return delegate.onCancelTask(params, context);
    }

    @Override
    public TaskPushNotificationConfig onCreateTaskPushNotificationConfig(
            TaskPushNotificationConfig params, ServerCallContext context) throws A2AError {
        return delegate.onCreateTaskPushNotificationConfig(params, context);
    }

    @Override
    public TaskPushNotificationConfig onGetTaskPushNotificationConfig(
            GetTaskPushNotificationConfigParams params, ServerCallContext context) throws A2AError {
        return delegate.onGetTaskPushNotificationConfig(params, context);
    }

    @Override
    public ListTaskPushNotificationConfigsResult onListTaskPushNotificationConfigs(
            ListTaskPushNotificationConfigsParams params, ServerCallContext context) throws A2AError {
        return delegate.onListTaskPushNotificationConfigs(params, context);
    }

    @Override
    public void onDeleteTaskPushNotificationConfig(
            DeleteTaskPushNotificationConfigParams params, ServerCallContext context) throws A2AError {
        delegate.onDeleteTaskPushNotificationConfig(params, context);
    }

    @Override
    public void validateRequestedTask(String requestedTaskId) throws A2AError {
        delegate.validateRequestedTask(requestedTaskId);
    }
}
