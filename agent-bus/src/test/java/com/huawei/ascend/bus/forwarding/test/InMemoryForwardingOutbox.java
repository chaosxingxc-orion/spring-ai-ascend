package com.huawei.ascend.bus.forwarding.test;

import com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingReceipt;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory test double for {@link ForwardingOutboxPort} — NON-PRODUCTION.
 *
 * <p>Backed by a {@link HashMap} keyed by {@code (tenantId, messageId)} (the
 * outbox unique key). Validates every transition through
 * {@link ForwardingStateMachine} before mutating, mirroring the contract a real
 * JDBC implementation must honour in Stage 8. Tenant-scoped: a key miss for a
 * cross-tenant lookup surfaces as "not found" (explicit failure), never a
 * cross-tenant read.
 *
 * <p>Authority: {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §4.1}.
 */
// non-production — test fixture only; real persistence is Stage 8
public final class InMemoryForwardingOutbox implements ForwardingOutboxPort {

    private record Key(String tenantId, String messageId) {}

    private record Entry(ForwardingEnvelope envelope, ForwardingStatus.Outbox status,
                         int attemptCount, long nextAttemptAt, long createdAt,
                         long updatedAt, ForwardingFailureCode lastFailureCode) {}

    private final Map<Key, Entry> store = new HashMap<>();
    private final ForwardingStateMachine stateMachine = new ForwardingStateMachine();

    @Override
    public ForwardingReceipt enqueue(ForwardingEnvelope envelope, long nowMillisEpoch) {
        Objects.requireNonNull(envelope, "envelope is required");
        Key key = new Key(envelope.tenantId(), envelope.messageId().value());
        if (store.containsKey(key)) {
            // idempotent re-enqueue: return already-accepted, do not mutate
            return ForwardingReceipt.accepted(envelope.messageId(), envelope.tenantId(), nowMillisEpoch);
        }
        ForwardingStatus.Outbox status =
                stateMachine.transitOutbox(null, ForwardingStateMachine.OutboxEvent.ENQUEUE);
        store.put(key, new Entry(envelope, status, 0, 0L, nowMillisEpoch, nowMillisEpoch, null));
        return ForwardingReceipt.accepted(envelope.messageId(), envelope.tenantId(), nowMillisEpoch);
    }

    @Override
    public ForwardingStatus.Outbox markDispatching(ForwardingMessageId id, String tenantId) {
        return mutate(id, tenantId, ForwardingStateMachine.OutboxEvent.BEGIN_DISPATCH, null, 0L);
    }

    @Override
    public ForwardingStatus.Outbox markAcked(ForwardingMessageId id, String tenantId) {
        return mutate(id, tenantId, ForwardingStateMachine.OutboxEvent.ACK, null, 0L);
    }

    @Override
    public ForwardingStatus.Outbox scheduleRetry(ForwardingMessageId id, String tenantId,
                                                 ForwardingFailureCode code, long nextAttemptAtMillisEpoch) {
        Objects.requireNonNull(code, "code is required for scheduleRetry");
        return mutate(id, tenantId, ForwardingStateMachine.OutboxEvent.RETRY, code, nextAttemptAtMillisEpoch);
    }

    @Override
    public ForwardingStatus.Outbox moveToDlq(ForwardingMessageId id, String tenantId,
                                             ForwardingFailureCode code) {
        Objects.requireNonNull(code, "code is required for moveToDlq");
        return mutate(id, tenantId, ForwardingStateMachine.OutboxEvent.EXHAUST_RETRIES, code, 0L);
    }

    @Override
    public ForwardingStatus.Outbox markExpired(ForwardingMessageId id, String tenantId) {
        return mutate(id, tenantId, ForwardingStateMachine.OutboxEvent.EXPIRE,
                ForwardingFailureCode.DELIVERY_TIMEOUT, 0L);
    }

    @Override
    public ForwardingStatus.Outbox statusOf(ForwardingMessageId id, String tenantId) {
        Entry entry = requireEntry(id, tenantId);
        return entry.status();
    }

    /** Test-only introspection: current attempt count. */
    public int attemptCountOf(ForwardingMessageId id, String tenantId) {
        return requireEntry(id, tenantId).attemptCount();
    }

    /** Test-only introspection: number of distinct outbox records. */
    public int entryCount() {
        return store.size();
    }

    private ForwardingStatus.Outbox mutate(ForwardingMessageId id, String tenantId,
                                           ForwardingStateMachine.OutboxEvent event,
                                           ForwardingFailureCode failureCode, long nextAttemptAt) {
        Entry entry = requireEntry(id, tenantId);
        ForwardingStatus.Outbox next = stateMachine.transitOutbox(entry.status(), event);
        int nextAttempts = (event == ForwardingStateMachine.OutboxEvent.RETRY)
                ? entry.attemptCount() + 1 : entry.attemptCount();
        ForwardingFailureCode nextCode = (next == ForwardingStatus.Outbox.ACKED)
                ? null
                : (failureCode != null ? failureCode : entry.lastFailureCode());
        store.put(new Key(tenantId, id.value()),
                new Entry(entry.envelope(), next, nextAttempts, nextAttemptAt,
                        entry.createdAt(), System.currentTimeMillis(), nextCode));
        return next;
    }

    private Entry requireEntry(ForwardingMessageId id, String tenantId) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Entry entry = store.get(new Key(tenantId, id.value()));
        if (entry == null) {
            throw new IllegalStateException(
                    "no outbox entry for tenantId=" + tenantId + " messageId=" + id.value());
        }
        return entry;
    }
}
