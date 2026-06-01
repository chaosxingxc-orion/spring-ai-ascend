package com.huawei.ascend.service.access.core;

import com.huawei.ascend.service.access.protocol.a2a.A2aEnvelope;
import com.huawei.ascend.service.access.egress.EgressBindingFactory;
import com.huawei.ascend.service.access.egress.EgressDispatcher;
import com.huawei.ascend.service.access.egress.EgressQueueRegistry;
import com.huawei.ascend.service.access.model.AccessAcceptedResponse;
import com.huawei.ascend.service.access.model.AccessIntent;
import com.huawei.ascend.service.access.model.AccessOperation;
import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.protocol.async.AsyncEnvelope;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class AccessGateway {

    private final TaskHandler taskHandler;
    private final EgressQueueRegistry egressQueueRegistry;
    private final EgressDispatcher egressDispatcher;

    public AccessGateway(
            TaskHandler taskHandler,
            EgressQueueRegistry egressQueueRegistry,
            EgressDispatcher egressDispatcher) {
        this.taskHandler = Objects.requireNonNull(taskHandler, "taskHandler");
        this.egressQueueRegistry = Objects.requireNonNull(egressQueueRegistry, "egressQueueRegistry");
        this.egressDispatcher = Objects.requireNonNull(egressDispatcher, "egressDispatcher");
    }

    public AccessIntent acceptA2a(A2aEnvelope envelope) {
        return acceptA2a(envelope, false);
    }

    public AccessIntent acceptA2a(A2aEnvelope envelope, boolean streaming) {
        Objects.requireNonNull(envelope, "envelope");
        A2aEnvelope.A2aContext context = envelope.context();
        A2aEnvelope.A2aMessage message = envelope.message();
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("parts", message == null ? java.util.List.of() : message.parts());
        payload.put("metadata", message == null ? Map.of() : message.metadata());
        payload.put("contextId", context.contextId());
        payload.put("correlationId", context.correlationId());
        payload.put("a2aStreaming", streaming);
        if (envelope.pushNotificationConfig() != null) {
            payload.put("a2aPushNotificationConfig", envelope.pushNotificationConfig());
        }
        return new AccessIntent(
                AccessOperation.SUBMIT,
                context.tenantId(),
                context.userId(),
                context.agentId(),
                context.sessionId(),
                message == null ? null : message.text(),
                context.idempotencyKey(),
                Collections.unmodifiableMap(payload));
    }

    /**
     * Single inbound entry: allocate the task id, normalise the session id,
     * bind the reply (egress) channel, then hand off to the task handler.
     *
     * <p>Binding before the handler runs is what lets a synchronous runtime
     * deliver output during {@link TaskHandler#runTask}. The reply channel is
     * keyed by (tenant, session, task), so the session id is normalised here —
     * defaulting to the task id when absent — and the same resolved intent is
     * passed to the handler so task control sees the identical session id.
     */
    public CompletionStage<AccessAcceptedResponse> dispatch(AccessIntent intent) {
        Objects.requireNonNull(intent, "intent");
        String taskId = java.util.UUID.randomUUID().toString();
        AccessIntent resolved = withSessionId(intent, taskId);
        bindEgress(resolved, taskId);
        return taskHandler.runTask(resolved, taskId);
    }

    private static AccessIntent withSessionId(AccessIntent intent, String fallback) {
        if (intent.sessionId() != null && !intent.sessionId().isBlank()) {
            return intent;
        }
        return new AccessIntent(intent.operation(), intent.tenantId(), intent.userId(),
                intent.agentId(), fallback, intent.query(), intent.idempotencyKey(), intent.payload());
    }

    public AccessIntent acceptAsync(AsyncEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("payload", envelope.body().payload());
        payload.put("replyTopic", envelope.headers().replyTopic());
        payload.put("correlationId", envelope.headers().correlationId());
        return new AccessIntent(
                envelope.headers().operation(),
                envelope.headers().tenantId(),
                envelope.headers().userId(),
                envelope.headers().agentId(),
                envelope.headers().sessionId(),
                envelope.body().query(),
                envelope.headers().idempotencyKey(),
                Collections.unmodifiableMap(payload));
    }

    /**
     * Creates (idempotently) the reply queue for {@code taskId} and starts its
     * egress dispatcher. Safe to call more than once for the same task.
     */
    private void bindEgress(AccessIntent intent, String taskId) {
        EgressBinding binding = EgressBindingFactory.from(intent, taskId);
        egressQueueRegistry.getOrCreate(binding);
        egressDispatcher.start(binding);
    }
}



