package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult;
import com.huawei.ascend.bus.forwarding.spi.ForwardingLeaseException;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxClaimPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;

import java.util.List;
import java.util.Objects;

/**
 * Minimal dispatcher worker that drives claimed outbox records to a terminal
 * state through the abstract delivery port (Stage 8 plan §3 slice 5).
 *
 * <p>This is the claim / deliver / ack / retry half of the forwarding lifecycle,
 * kept separate from {@code ForwardingDispatcher} (the accept / enqueue gateway
 * role) per MI8-003. A single synchronous {@link #runOnce} tick:
 * <ol>
 *   <li>claims due records via {@link ForwardingOutboxClaimPort#claimDue} (each
 *       already atomically transitioned to DISPATCHING and leased to the worker);</li>
 *   <li>delivers each via {@link ForwardingDeliveryPort#deliver}, consuming only
 *       the opaque {@code routeHandle} (never a physical endpoint);</li>
 *   <li>maps the {@link ForwardingDeliveryResult} to the matching outbox state
 *       transition — ACKED / RETRY_SCHEDULED / DLQ / EXPIRED — carrying the
 *       caller's {@code leaseOwner} so the mutation is lease-owner guarded
 *       (Stage 9, MI9-001): a record reclaimed by another worker between claim
 *       and ack is rejected, not mutated. A rejected mutation raises
 *       {@link ForwardingLeaseException}; the worker treats it as "skip this
 *       record" (Stage 10, MI10-001) — counted as {@code skipped} and the tick
 *       continues, so one reclaimed record never aborts the whole tick.</li>
 * </ol>
 *
 * <p>The worker holds no threads, no scheduler, no registry, no transport. Real
 * polling cadence, threading, backpressure and a concrete delivery binding are
 * deferred to a later stage; Stage 8 ships this skeleton so the ACK / RETRY /
 * DLQ / EXPIRED paths can be exercised with a fake delivery port. The worker
 * never writes Task execution state.
 *
 * <p>Lease renewal (Stage 10, MI10-002; Stage 11, MI11-001): a {@code deliver}
 * that outlives the lease TTL would lose the lease and fail the ack. The worker
 * carries a {@link DispatchLeasePolicy} and an {@link EpochClock}; before each
 * delivery it reads the live clock and, if the remaining lease TTL
 * ({@code leaseUntil - clockNow}) is below the policy threshold, calls
 * {@link ForwardingOutboxClaimPort#renewLease} to extend the lease. (Before Stage
 * 11 the check used the tick-start instant, so the remainder never shrank inside
 * a tick and renewal could not fire under a natural dispatch loop.) A renew that
 * returns {@code false} (reclaimed / not DISPATCHING) is treated exactly like a
 * {@link ForwardingLeaseException}: the record is skipped, not delivered. The
 * in-memory harness checks owner, not lease expiry, so "renew-or-lose-the-ack"
 * is encoded as a SQL contract in {@code forwarding-persistence.md §7.2}, not
 * asserted in-memory.
 *
 * <p>Exception contract (Stage 11, MI11-002 / MI11-003): within a tick the worker
 * never propagates a per-record failure. A {@link ForwardingLeaseException} from
 * the lease guard, or a non-lease {@link RuntimeException} from {@code deliver}
 * (a real transport binding MUST map transport errors to a
 * {@link ForwardingDeliveryResult} and not throw — see the ICD), is swallowed as
 * a {@code skipped} record (left DISPATCHING, reclaimed on lease expiry) and the
 * tick continues. {@code runOnce} throws {@link IllegalArgumentException} only
 * for illegal arguments (blank tenant / lease owner, non-positive limit) — a
 * caller bug the dispatch loop fails fast on rather than masking.
 *
 * <p>Circuit breaker (Stage 16): before each delivery the worker asks the injected
 * {@link ForwardingCircuitBreaker} {@link ForwardingCircuitBreaker#allowsDelivery};
 * an OPEN route is short-circuited along the existing skip path (skipped, left
 * DISPATCHING, reclaimed on lease expiry, consuming no attemptCount). After each
 * delivery the outcome is fed back via
 * {@link ForwardingCircuitBreaker#recordOutcome} — once from the deliver-exception
 * catch (as a {@code RECEIVER_UNAVAILABLE} failure) and once with the real result
 * before the state mutation — so the breaker's {@code probeInFlight} marker can
 * never strand a HALF_OPEN probe regardless of which path a record takes. The
 * default {@link ForwardingCircuitBreaker#ALWAYS_CLOSED} no-op keeps the pre-Stage-16
 * behaviour when no breaker is injected.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md §3/§4.1};
 * {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md §5}.
 */
public final class ForwardingDispatcherWorker {

    private final ForwardingOutboxClaimPort claimPort;
    private final ForwardingOutboxPort outboxPort;
    private final ForwardingDeliveryPort deliveryPort;
    private final DispatchLeasePolicy leasePolicy;
    private final EpochClock clock;
    private final ForwardingRetryPolicy retryPolicy;
    private final ForwardingCircuitBreaker circuitBreaker;

    public ForwardingDispatcherWorker(ForwardingOutboxClaimPort claimPort,
                                      ForwardingOutboxPort outboxPort,
                                      ForwardingDeliveryPort deliveryPort) {
        this(claimPort, outboxPort, deliveryPort, DispatchLeasePolicy.DISABLED,
                EpochClock.SYSTEM, ForwardingRetryPolicy.DEFAULT);
    }

    public ForwardingDispatcherWorker(ForwardingOutboxClaimPort claimPort,
                                      ForwardingOutboxPort outboxPort,
                                      ForwardingDeliveryPort deliveryPort,
                                      DispatchLeasePolicy leasePolicy) {
        this(claimPort, outboxPort, deliveryPort, leasePolicy, EpochClock.SYSTEM,
                ForwardingRetryPolicy.DEFAULT);
    }

    /** Stage 11 (MI11-001): inject the wall clock the renewal check reads. */
    public ForwardingDispatcherWorker(ForwardingOutboxClaimPort claimPort,
                                      ForwardingOutboxPort outboxPort,
                                      ForwardingDeliveryPort deliveryPort,
                                      DispatchLeasePolicy leasePolicy,
                                      EpochClock clock) {
        this(claimPort, outboxPort, deliveryPort, leasePolicy, clock, ForwardingRetryPolicy.DEFAULT);
    }

    /**
     * Stage 14: inject the retry / backoff policy alongside the lease policy
     * and clock. On a {@code RETRY_SCHEDULED} result the policy decides whether
     * the record retries (and when) or is exhausted to DLQ. Delegates to the
     * full constructor with the {@link ForwardingCircuitBreaker#ALWAYS_CLOSED}
     * no-op breaker — Stage 16 wired a per-route breaker in, but this overload
     * keeps the Stage 14 signature source-compatible.
     */
    public ForwardingDispatcherWorker(ForwardingOutboxClaimPort claimPort,
                                      ForwardingOutboxPort outboxPort,
                                      ForwardingDeliveryPort deliveryPort,
                                      DispatchLeasePolicy leasePolicy,
                                      EpochClock clock,
                                      ForwardingRetryPolicy retryPolicy) {
        this(claimPort, outboxPort, deliveryPort, leasePolicy, clock, retryPolicy,
                ForwardingCircuitBreaker.ALWAYS_CLOSED);
    }

    /**
     * Stage 16: full constructor — inject the per-route
     * {@link ForwardingCircuitBreaker} alongside the retry policy, lease policy
     * and clock. Before each delivery the worker asks the breaker
     * {@link ForwardingCircuitBreaker#allowsDelivery}; an OPEN route is
     * short-circuited (skipped, left DISPATCHING, reclaimed on lease expiry —
     * like the existing lease / deliver-exception skip paths, consuming no
     * attemptCount). After each delivery the worker feeds the result back via
     * {@link ForwardingCircuitBreaker#recordOutcome}; the breaker classifies it
     * internally (ACKED → success, a retryable failure → failure, DLQ / EXPIRED
     * → ignored).
     */
    public ForwardingDispatcherWorker(ForwardingOutboxClaimPort claimPort,
                                      ForwardingOutboxPort outboxPort,
                                      ForwardingDeliveryPort deliveryPort,
                                      DispatchLeasePolicy leasePolicy,
                                      EpochClock clock,
                                      ForwardingRetryPolicy retryPolicy,
                                      ForwardingCircuitBreaker circuitBreaker) {
        this.claimPort = Objects.requireNonNull(claimPort, "claimPort is required");
        this.outboxPort = Objects.requireNonNull(outboxPort, "outboxPort is required");
        this.deliveryPort = Objects.requireNonNull(deliveryPort, "deliveryPort is required");
        this.leasePolicy = Objects.requireNonNull(leasePolicy, "leasePolicy is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy is required");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker is required");
    }

    /**
     * Run one dispatch tick for a single tenant.
     *
     * <p>The worker's {@link DispatchLeasePolicy} (set at construction) decides
     * whether a short remaining lease is renewed before delivery (Stage 10,
     * MI10-002; Stage 11, MI11-001 — the renewal check reads the injected
     * {@link EpochClock}, not {@code nowMillisEpoch}); a record whose renew or
     * ack is rejected by the lease guard is skipped, not delivered, and the tick
     * continues. A {@code deliver} that throws a non-lease
     * {@link RuntimeException} is likewise skipped (Stage 11, MI11-002).
     *
     * <p>{@code nowMillisEpoch} is the claim instant (passed to {@code claimDue}
     * as the claim moment); the renewal check and the delivery instant both read
     * the injected {@link EpochClock} so they reflect real elapsed time.
     *
     * @param tenantId             tenant scope of the tick (Rule R-C.c)
     * @param nowMillisEpoch       the claim instant of this tick
     * @param limit                max records to claim this tick ({@code > 0})
     * @param leaseOwner           identity of this worker instance
     * @param leaseUntilMillisEpoch instant until which claimed leases are exclusive
     * @return a summary of how many records were claimed and how each resolved
     * @throws IllegalArgumentException if {@code tenantId} / {@code leaseOwner}
     *         is blank or {@code limit <= 0} (caller bug — fail fast; the tick
     *         body never propagates a per-record failure, MI11-003)
     */
    public DispatchTickResult runOnce(String tenantId, long nowMillisEpoch, int limit,
                                      String leaseOwner, long leaseUntilMillisEpoch) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(leaseOwner, "leaseOwner is required");
        if (leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        List<ForwardingOutboxRecord> claimed =
                claimPort.claimDue(tenantId, nowMillisEpoch, limit, leaseOwner, leaseUntilMillisEpoch);

        int acked = 0;
        int retried = 0;
        int dlqd = 0;
        int expired = 0;
        int skipped = 0;
        for (ForwardingOutboxRecord record : claimed) {
            try {
                // Stage 11 (MI11-001): read the live clock once per record — the
                // renewal check and the delivery instant both use real elapsed time,
                // not the tick-start instant, so renewal fires when a long tick
                // approaches the lease TTL. claimDue still uses nowMillisEpoch as
                // the claim moment.
                long clockNow = clock.epochMillis();
                // Stage 10 (MI10-002): if the remaining lease TTL is below the policy
                // threshold, renew before delivering so a long deliver does not outlive
                // the lease. A failed renew (reclaimed / not DISPATCHING) is treated
                // like a lease exception — skip, do not deliver.
                if (leasePolicy.renewBeforeExpiryMillis() > 0) {
                    long remaining = leaseUntilMillisEpoch - clockNow;
                    if (remaining < leasePolicy.renewBeforeExpiryMillis()) {
                        long extendedUntil = leaseUntilMillisEpoch + leasePolicy.leaseExtensionMillis();
                        if (!claimPort.renewLease(record.messageId(), tenantId, leaseOwner, extendedUntil)) {
                            skipped++;
                            continue;
                        }
                    }
                }
                // Stage 16: per-route circuit breaker. Placed AFTER the lease-renewal
                // check (a renew failure skips before the breaker is touched, so it
                // cannot leak a HALF_OPEN probe marker) and BEFORE delivery. An OPEN
                // route short-circuits exactly like the existing skip paths: the record
                // is skipped, left DISPATCHING, reclaimed on lease expiry, and consumes
                // no attemptCount.
                if (!circuitBreaker.allowsDelivery(record.routeHandle())) {
                    skipped++;
                    continue;
                }
                // Stage 11 (MI11-002): a real transport binding must map transport
                // errors to a ForwardingDeliveryResult and MUST NOT throw (ICD /
                // delivery port contract). If it still throws a non-lease
                // RuntimeException, the worker skips the record (left DISPATCHING,
                // reclaimed on lease expiry) rather than aborting the tick.
                ForwardingDeliveryResult result;
                try {
                    result = deliveryPort.deliver(record, clockNow);
                } catch (RuntimeException e) {
                    // Stage 16: feed the failure back so a HALF_OPEN probe that threw
                    // (and thus never returned a result) cannot strand its in-flight
                    // marker — a thrown deliver is a transport fault, mapped to a
                    // retryable RECEIVER_UNAVAILABLE failure.
                    circuitBreaker.recordOutcome(record.routeHandle(),
                            ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE));
                    skipped++;
                    continue;
                }
                // Stage 16: feed the delivery outcome back BEFORE the state mutation
                // (markAcked / scheduleRetry / moveToDlq / markExpired), so a lease-guard
                // exception in the mutation cannot leave a HALF_OPEN probe stranded.
                circuitBreaker.recordOutcome(record.routeHandle(), result);
                switch (result.outcome()) {
                    case ACKED -> {
                        outboxPort.markAcked(record.messageId(), tenantId, leaseOwner);
                        acked++;
                    }
                    case RETRY_SCHEDULED -> {
                        // Stage 14: retry governance is the policy's job, not the
                        // delivery result's. A retryable failure whose retries are
                        // exhausted goes to DLQ (a retryable code is a legal DLQ
                        // code); otherwise the policy picks the next-attempt instant
                        // (record.attemptCount() is the retries already recorded —
                        // scheduleRetry increments it).
                        ForwardingFailureCode retryCode = result.failureCode();
                        if (retryPolicy.exhausted(record.attemptCount())) {
                            outboxPort.moveToDlq(record.messageId(), tenantId, leaseOwner, retryCode);
                            dlqd++;
                        } else {
                            long nextAttemptAt =
                                    retryPolicy.nextAttemptAt(retryCode, record.attemptCount(), clockNow);
                            outboxPort.scheduleRetry(record.messageId(), tenantId, leaseOwner,
                                    retryCode, nextAttemptAt);
                            retried++;
                        }
                    }
                    case DLQ -> {
                        outboxPort.moveToDlq(record.messageId(), tenantId, leaseOwner,
                                result.failureCode());
                        dlqd++;
                    }
                    case EXPIRED -> {
                        outboxPort.markExpired(record.messageId(), tenantId, leaseOwner);
                        expired++;
                    }
                }
            } catch (ForwardingLeaseException e) {
                // Stage 10 (MI10-001): the lease guard rejected this record — it was
                // reclaimed by another worker, or the lease is no longer held / not
                // DISPATCHING. Skip it: the record's true owner (or the next reclaim)
                // drives it forward. The tick continues with the remaining records.
                skipped++;
            }
        }
        return new DispatchTickResult(claimed.size(), acked, retried, dlqd, expired, skipped);
    }

    /** Immutable summary of one dispatch tick. */
    public record DispatchTickResult(int claimed, int acked, int retried, int dlqd, int expired,
                                     int skipped) {
        public DispatchTickResult {
            if (claimed < 0 || acked < 0 || retried < 0 || dlqd < 0 || expired < 0 || skipped < 0) {
                throw new IllegalArgumentException("tick counts must be non-negative");
            }
            if (claimed != acked + retried + dlqd + expired + skipped) {
                throw new IllegalArgumentException(
                        "tick counts must be self-consistent: claimed must equal "
                        + "acked + retried + dlqd + expired + skipped");
            }
        }
    }

    /**
     * Lease-renewal policy for a dispatch tick (Stage 10, MI10-002). Governs when
     * {@link ForwardingDispatcherWorker} refreshes a claimed record's lease before
     * delivery, so a long {@code deliver} does not outlive the lease TTL.
     *
     * <p>Before each delivery, if the remaining lease TTL (leaseUntil - now) is
     * below {@code renewBeforeExpiryMillis}, the worker calls
     * {@link ForwardingOutboxClaimPort#renewLease} to extend the lease by
     * {@code leaseExtensionMillis}. A renew returning {@code false} (the worker
     * no longer holds the lease — reclaimed / not DISPATCHING) is treated like a
     * {@link ForwardingLeaseException}: the record is skipped, not delivered.
     *
     * <p>Note: the in-memory harness checks owner, not lease expiry, so it cannot
     * reproduce the JDBC {@code WHERE lease_until > now()} guard — the
     * "renew-or-lose-the-ack" semantics are a SQL contract
     * ({@code forwarding-persistence.md §7.2}), not an in-memory assertion.
     */
    public record DispatchLeasePolicy(long renewBeforeExpiryMillis, long leaseExtensionMillis) {
        public DispatchLeasePolicy {
            if (renewBeforeExpiryMillis < 0) {
                throw new IllegalArgumentException("renewBeforeExpiryMillis must be >= 0");
            }
            if (leaseExtensionMillis <= 0) {
                throw new IllegalArgumentException("leaseExtensionMillis must be > 0");
            }
        }

        /** No renewal: the worker relies on the caller's leaseUntil for the whole tick. */
        public static final DispatchLeasePolicy DISABLED = new DispatchLeasePolicy(0, 1);
    }
}
