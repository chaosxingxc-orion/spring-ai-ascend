package com.huawei.ascend.bus.forwarding.test;

import com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingLease;
import com.huawei.ascend.bus.forwarding.spi.ForwardingLeaseException;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxClaimPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingReceipt;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory test double for {@link ForwardingOutboxPort} and
 * {@link ForwardingOutboxClaimPort} — NON-PRODUCTION.
 *
 * <p>Backed by a {@link LinkedHashMap} keyed by {@code (tenantId, messageId)}
 * (the outbox unique key). Stores the full {@link ForwardingOutboxRecord}
 * projection including source / target service ids and the claim {@link ForwardingLease}.
 * Validates every transition through {@link ForwardingStateMachine} before
 * mutating, mirroring the contract a real JDBC implementation must honour: the
 * claim / lease semantics (atomic due-selection, single-owner lease, expired-lease
 * reclaim, terminal-unclaimable) are exercised directly. Tenant-scoped: a key
 * miss for a cross-tenant lookup surfaces as "not found" (explicit failure),
 * never a cross-tenant read.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md §4.1};
 * {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md §4}.
 */
// non-production — test fixture only; real persistence is Stage 8
public final class InMemoryForwardingOutbox
        implements ForwardingOutboxPort, ForwardingOutboxClaimPort {

    private record Key(String tenantId, String messageId) {}

    /** Mutable in-memory row; the public surface exposes immutable {@link ForwardingOutboxRecord}s. */
    private static final class Entry {
        private final ForwardingEnvelope envelope;
        private final String sourceServiceId;
        private final String targetServiceId;
        private final long createdAt;
        private ForwardingStatus.Outbox status;
        private int attemptCount;
        private long nextAttemptAt;
        private long updatedAt;
        private ForwardingFailureCode lastFailureCode;
        private ForwardingLease lease;

        Entry(ForwardingEnvelope envelope, String sourceServiceId, String targetServiceId,
              ForwardingStatus.Outbox status, long createdAt) {
            this.envelope = envelope;
            this.sourceServiceId = sourceServiceId;
            this.targetServiceId = targetServiceId;
            this.status = status;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
        }
    }

    private final Map<Key, Entry> store = new LinkedHashMap<>();
    private final ForwardingStateMachine stateMachine = new ForwardingStateMachine();

    // ===== ForwardingOutboxPort =====

    @Override
    public synchronized ForwardingReceipt enqueue(ForwardingEnvelope envelope,
                                                  String sourceServiceId, String targetServiceId,
                                                  long nowMillisEpoch) {
        Objects.requireNonNull(envelope, "envelope is required");
        requireNonBlank(sourceServiceId, "sourceServiceId");
        requireNonBlank(targetServiceId, "targetServiceId");
        Key key = new Key(envelope.tenantId(), envelope.messageId().value());
        if (store.containsKey(key)) {
            // idempotent re-enqueue: return already-accepted, do not mutate
            return ForwardingReceipt.accepted(envelope.messageId(), envelope.tenantId(), nowMillisEpoch);
        }
        ForwardingStatus.Outbox status =
                stateMachine.transitOutbox(null, ForwardingStateMachine.OutboxEvent.ENQUEUE);
        Entry entry = new Entry(envelope, sourceServiceId, targetServiceId, status, nowMillisEpoch);
        store.put(key, entry);
        return ForwardingReceipt.accepted(envelope.messageId(), envelope.tenantId(), nowMillisEpoch);
    }

    @Override
    public synchronized ForwardingStatus.Outbox markAcked(ForwardingMessageId id, String tenantId,
                                                           String leaseOwner) {
        return leaseGuardedMutate(id, tenantId, leaseOwner,
                ForwardingStateMachine.OutboxEvent.ACK, null, 0L);
    }

    @Override
    public synchronized ForwardingStatus.Outbox scheduleRetry(ForwardingMessageId id, String tenantId,
                                                              String leaseOwner, ForwardingFailureCode code,
                                                              long nextAttemptAtMillisEpoch) {
        Objects.requireNonNull(code, "code is required for scheduleRetry");
        if (!code.retryable()) {
            throw new IllegalArgumentException(
                    "scheduleRetry requires a retryable failureCode; " + code + " is not retryable (MI9-004)");
        }
        return leaseGuardedMutate(id, tenantId, leaseOwner,
                ForwardingStateMachine.OutboxEvent.RETRY, code, nextAttemptAtMillisEpoch);
    }

    @Override
    public synchronized ForwardingStatus.Outbox moveToDlq(ForwardingMessageId id, String tenantId,
                                                           String leaseOwner, ForwardingFailureCode code) {
        Objects.requireNonNull(code, "code is required for moveToDlq");
        if (code.dedup()) {
            throw new IllegalArgumentException(
                    "moveToDlq must not carry a dedup failureCode (MI9-004)");
        }
        return leaseGuardedMutate(id, tenantId, leaseOwner,
                ForwardingStateMachine.OutboxEvent.EXHAUST_RETRIES, code, 0L);
    }

    @Override
    public synchronized ForwardingStatus.Outbox markExpired(ForwardingMessageId id, String tenantId,
                                                            String leaseOwner) {
        return leaseGuardedMutate(id, tenantId, leaseOwner,
                ForwardingStateMachine.OutboxEvent.EXPIRE, ForwardingFailureCode.DELIVERY_TIMEOUT, 0L);
    }

    @Override
    public synchronized ForwardingStatus.Outbox statusOf(ForwardingMessageId id, String tenantId) {
        return requireEntry(id, tenantId).status;
    }

    // ===== ForwardingOutboxClaimPort =====

    @Override
    public synchronized List<ForwardingOutboxRecord> claimDue(String tenantId, long nowMillisEpoch,
                                                              int limit, String leaseOwner,
                                                              long leaseUntilMillisEpoch) {
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(leaseOwner, "leaseOwner");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        if (leaseUntilMillisEpoch <= nowMillisEpoch) {
            throw new IllegalArgumentException("leaseUntilMillisEpoch must be > nowMillisEpoch");
        }
        List<ForwardingOutboxRecord> claimed = new ArrayList<>();
        for (Map.Entry<Key, Entry> e : store.entrySet()) {
            Entry row = e.getValue();
            if (!e.getKey().tenantId.equals(tenantId)) {
                continue; // tenant scope (Rule R-C.c)
            }
            if (claimKindOf(row, nowMillisEpoch) == ClaimKind.NONE) {
                continue; // terminal, not-yet-due, or mid-dispatch under a live lease
            }
            // claim: transition fresh / retry → DISPATCHING (reclaim stays DISPATCHING), stamp lease
            ForwardingStatus.Outbox next = (row.status == ForwardingStatus.Outbox.DISPATCHING)
                    ? ForwardingStatus.Outbox.DISPATCHING
                    : stateMachine.transitOutbox(row.status, ForwardingStateMachine.OutboxEvent.BEGIN_DISPATCH);
            row.status = next;
            row.lease = new ForwardingLease(leaseOwner, leaseUntilMillisEpoch);
            row.updatedAt = nowMillisEpoch;
            claimed.add(snapshot(row));
            if (claimed.size() >= limit) {
                break;
            }
        }
        return claimed;
    }

    /** Why (or why not) a row is claimable at the given instant. */
    private enum ClaimKind { NONE, FRESH, RETRY, RECLAIM }

    private ClaimKind claimKindOf(Entry row, long nowMillisEpoch) {
        ForwardingLease existing = row.lease;
        if (existing != null && !existing.isExpiredAt(nowMillisEpoch)) {
            return ClaimKind.NONE; // held by an owner; not reclaimable yet
        }
        if (row.status == ForwardingStatus.Outbox.PENDING) {
            return ClaimKind.FRESH;
        }
        if (row.status == ForwardingStatus.Outbox.RETRY_SCHEDULED && row.nextAttemptAt <= nowMillisEpoch) {
            return ClaimKind.RETRY;
        }
        if (row.status == ForwardingStatus.Outbox.DISPATCHING
                && existing != null && existing.isExpiredAt(nowMillisEpoch)) {
            return ClaimKind.RECLAIM; // stuck holder reclaim
        }
        return ClaimKind.NONE;
    }

    @Override
    public synchronized boolean renewLease(ForwardingMessageId id, String tenantId, String leaseOwner,
                                           long leaseUntilMillisEpoch) {
        requireNonBlank(leaseOwner, "leaseOwner");
        Entry row = findEntryOrNull(id, tenantId);
        if (row == null || row.status != ForwardingStatus.Outbox.DISPATCHING
                || row.lease == null || !row.lease.isHeldBy(leaseOwner)) {
            return false;
        }
        row.lease = new ForwardingLease(leaseOwner, leaseUntilMillisEpoch);
        return true;
    }

    @Override
    public synchronized boolean releaseLease(ForwardingMessageId id, String tenantId, String leaseOwner) {
        requireNonBlank(leaseOwner, "leaseOwner");
        Entry row = findEntryOrNull(id, tenantId);
        if (row == null || row.lease == null || !row.lease.isHeldBy(leaseOwner)) {
            return false;
        }
        row.lease = null;
        return true;
    }

    // ===== test-only introspection =====

    /** Test-only introspection: current attempt count. */
    public synchronized int attemptCountOf(ForwardingMessageId id, String tenantId) {
        return requireEntry(id, tenantId).attemptCount;
    }

    /** Test-only introspection: the full record projection (tenant-scoped). */
    public synchronized ForwardingOutboxRecord recordOf(ForwardingMessageId id, String tenantId) {
        return snapshot(requireEntry(id, tenantId));
    }

    /** Test-only introspection: number of distinct outbox records. */
    public synchronized int entryCount() {
        return store.size();
    }

    // ===== internals =====

    /**
     * Lease-owner guarded mutation (Stage 9, MI9-001). A record reaches
     * DISPATCHING only via {@code claimDue}; every outbound transition is
     * rejected unless the caller is the current lease holder of a DISPATCHING
     * record. The lease is cleared on terminal + retry (MI9-002).
     */
    private ForwardingStatus.Outbox leaseGuardedMutate(ForwardingMessageId id, String tenantId,
                                                       String leaseOwner,
                                                       ForwardingStateMachine.OutboxEvent event,
                                                       ForwardingFailureCode failureCode,
                                                       long nextAttemptAt) {
        requireNonBlank(leaseOwner, "leaseOwner");
        Entry entry = store.get(new Key(tenantId, id.value()));
        if (entry == null) {
            throw new ForwardingLeaseException(ForwardingLeaseException.Reason.RECORD_NOT_FOUND,
                    "no outbox entry for tenantId=" + tenantId + " messageId=" + id.value());
        }
        if (entry.lease == null) {
            throw new ForwardingLeaseException(ForwardingLeaseException.Reason.NO_LEASE,
                    "no active lease on tenantId=" + tenantId + " messageId=" + id.value());
        }
        if (!entry.lease.isHeldBy(leaseOwner)) {
            throw new ForwardingLeaseException(ForwardingLeaseException.Reason.OWNER_MISMATCH,
                    "lease owner mismatch on tenantId=" + tenantId + " messageId=" + id.value()
                    + ": held by " + entry.lease.leaseOwner() + ", caller " + leaseOwner);
        }
        if (entry.status != ForwardingStatus.Outbox.DISPATCHING) {
            throw new ForwardingLeaseException(ForwardingLeaseException.Reason.NOT_DISPATCHING,
                    "record not DISPATCHING (status=" + entry.status + ") for tenantId=" + tenantId
                    + " messageId=" + id.value());
        }
        ForwardingStatus.Outbox next = stateMachine.transitOutbox(entry.status, event);
        entry.status = next;
        if (event == ForwardingStateMachine.OutboxEvent.RETRY) {
            entry.attemptCount = entry.attemptCount + 1;
            entry.nextAttemptAt = nextAttemptAt;
        }
        entry.lastFailureCode = (next == ForwardingStatus.Outbox.ACKED)
                ? null
                : (failureCode != null ? failureCode : entry.lastFailureCode);
        entry.lease = null; // MI9-002: clear lease on terminal + retry
        entry.updatedAt = System.currentTimeMillis();
        return next;
    }

    private ForwardingOutboxRecord snapshot(Entry e) {
        String payloadRef = e.envelope.carriesPayloadRef() ? e.envelope.payloadRef() : null;
        return new ForwardingOutboxRecord(
                e.envelope.tenantId(),
                e.envelope.messageId(),
                e.sourceServiceId,
                e.targetServiceId,
                e.envelope.routeHandle(),
                payloadRef,
                e.status,
                e.attemptCount,
                e.nextAttemptAt,
                e.createdAt,
                e.updatedAt,
                e.lastFailureCode,
                e.lease,
                e.envelope.correlationId(),     // FEAT-013 cross-hop correlation (mirrored from envelope, §2.3.1)
                e.envelope.eventType());         // FEAT-013/014 event-type (mirrored from envelope)
    }

    private Entry requireEntry(ForwardingMessageId id, String tenantId) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Entry entry = findEntryOrNull(id, tenantId);
        if (entry == null) {
            throw new IllegalStateException(
                    "no outbox entry for tenantId=" + tenantId + " messageId=" + id.value());
        }
        return entry;
    }

    private Entry findEntryOrNull(ForwardingMessageId id, String tenantId) {
        return store.get(new Key(tenantId, id.value()));
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
