package com.huawei.ascend.bus.forwarding.test;

import com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingInboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory test double for {@link ForwardingInboxPort} — NON-PRODUCTION.
 *
 * <p>Backed by a {@link HashMap} keyed by {@code (tenantId, messageId,
 * consumerServiceId)} (the inbox dedup key). Validates every transition through
 * {@link ForwardingStateMachine}. Distinct consumers dedup independently;
 * duplicate arrival returns {@code DUPLICATE_SUPPRESSED} without mutating the
 * stored entry.
 *
 * <p>Authority: {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §4.2}.
 */
// non-production — test fixture only; real persistence is Stage 8
public final class InMemoryForwardingInbox implements ForwardingInboxPort {

    private record Key(String tenantId, String messageId, String consumerServiceId) {}

    private record Entry(ForwardingStatus.Inbox status, long receivedAt,
                         long consumedAt, ForwardingFailureCode failureCode) {}

    private final Map<Key, Entry> store = new HashMap<>();
    private final ForwardingStateMachine stateMachine = new ForwardingStateMachine();

    @Override
    public ForwardingStatus.Inbox receive(ForwardingEnvelope envelope,
                                          String consumerServiceId, long nowMillisEpoch) {
        Objects.requireNonNull(envelope, "envelope is required");
        Objects.requireNonNull(consumerServiceId, "consumerServiceId is required");
        if (consumerServiceId.isBlank()) {
            throw new IllegalArgumentException("consumerServiceId must not be blank");
        }
        Key key = new Key(envelope.tenantId(), envelope.messageId().value(), consumerServiceId);
        if (store.containsKey(key)) {
            // duplicate arrival — dedup outcome, do not mutate the stored entry
            return stateMachine.transitInbox(null, ForwardingStateMachine.InboxEvent.ARRIVE_DUPLICATE);
        }
        ForwardingStatus.Inbox status =
                stateMachine.transitInbox(null, ForwardingStateMachine.InboxEvent.ARRIVE_NEW);
        store.put(key, new Entry(status, nowMillisEpoch, 0L, null));
        return status;
    }

    @Override
    public ForwardingStatus.Inbox markConsumed(ForwardingMessageId id, String tenantId,
                                               String consumerServiceId) {
        return mutate(id, tenantId, consumerServiceId,
                ForwardingStateMachine.InboxEvent.CONSUME, null);
    }

    @Override
    public ForwardingStatus.Inbox markRejected(ForwardingMessageId id, String tenantId,
                                               String consumerServiceId, ForwardingFailureCode code) {
        Objects.requireNonNull(code, "code is required for markRejected");
        return mutate(id, tenantId, consumerServiceId,
                ForwardingStateMachine.InboxEvent.REJECT, code);
    }

    @Override
    public ForwardingStatus.Inbox statusOf(ForwardingMessageId id, String tenantId,
                                           String consumerServiceId) {
        Entry entry = requireEntry(id, tenantId, consumerServiceId);
        return entry.status();
    }

    private ForwardingStatus.Inbox mutate(ForwardingMessageId id, String tenantId,
                                          String consumerServiceId,
                                          ForwardingStateMachine.InboxEvent event,
                                          ForwardingFailureCode code) {
        Entry entry = requireEntry(id, tenantId, consumerServiceId);
        ForwardingStatus.Inbox next = stateMachine.transitInbox(entry.status(), event);
        long consumedAt = (next == ForwardingStatus.Inbox.CONSUMED)
                ? System.currentTimeMillis() : entry.consumedAt();
        store.put(new Key(tenantId, id.value(), consumerServiceId),
                new Entry(next, entry.receivedAt(), consumedAt, code));
        return next;
    }

    private Entry requireEntry(ForwardingMessageId id, String tenantId, String consumerServiceId) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(consumerServiceId, "consumerServiceId is required");
        Entry entry = store.get(new Key(tenantId, id.value(), consumerServiceId));
        if (entry == null) {
            throw new IllegalStateException(
                    "no inbox entry for tenantId=" + tenantId
                    + " messageId=" + id.value() + " consumerServiceId=" + consumerServiceId);
        }
        return entry;
    }
}
