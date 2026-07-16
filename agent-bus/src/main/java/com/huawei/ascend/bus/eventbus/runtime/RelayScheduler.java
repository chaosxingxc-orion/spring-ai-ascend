package com.huawei.ascend.bus.eventbus.runtime;

import com.huawei.ascend.bus.forwarding.runtime.relay.RelayDispatchLoop;
import com.huawei.ascend.bus.forwarding.runtime.relay.RelayTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

/**
 * Production driver that periodically fires the two-hop relay tick
 * ({@link RelayTick#runOnce}) for the forward + response relays on a dedicated
 * {@link ThreadPoolTaskScheduler}, mirroring the isolation rationale of
 * {@code registryProbeTaskScheduler} (a hung relay tick blocks only its own
 * thread, not the other relay or the registry probes).
 *
 * <p>Lifetime = B-i (single-tick-per-fire): {@link #start()} schedules
 * {@link #driveForward()} / {@link #driveResponse()} at fixed delay; each drive
 * resets its one-shot {@link RelayDispatchLoop.TickSource} (yields
 * {@code clock.now()} once then empty) and runs the loop, which executes exactly
 * one worker tick. {@link RelayDispatchLoop#NO_BACKOFF} idle — the cadence is the
 * scheduler's fixed delay. The loop's drain/backoff power is dormant in B-i; the
 * value is shape-consistency with {@code ForwardingDispatchLoop} + swap-readiness
 * for a future drain/backoff variant.
 *
 * <p><b>Fault isolation is load-bearing:</b> {@code scheduleWithFixedDelay} cancels
 * its future if the task throws, so each drive wraps {@code loop.run} in
 * try/catch (RuntimeException) — a thrown tick is logged + swallowed, so the
 * schedule keeps firing (mirrors {@code MvpHealthProbeScheduler}'s per-probe isolation).
 *
 * <p>Authority: {@code docs/superpowers/specs/2026-07-15-relay-scheduler-design.md} §4.4.
 */
public class RelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(RelayScheduler.class);

    private final RelayDispatchLoop forwardLoop;
    private final RelayDispatchLoop responseLoop;
    private final SingleShotTickSource forwardSource;
    private final SingleShotTickSource responseSource;
    private final String tenantId;
    private final int limit;
    private final long fixedDelayMs;
    private final ThreadPoolTaskScheduler scheduler;

    public RelayScheduler(RelayTick forward, RelayTick response,
                          String tenantId, int limit, long fixedDelayMs,
                          ThreadPoolTaskScheduler scheduler) {
        this.forwardSource = new SingleShotTickSource(System::currentTimeMillis);
        this.responseSource = new SingleShotTickSource(System::currentTimeMillis);
        this.forwardLoop = new RelayDispatchLoop(forward, this.forwardSource, RelayDispatchLoop.NO_BACKOFF);
        this.responseLoop = new RelayDispatchLoop(response, this.responseSource, RelayDispatchLoop.NO_BACKOFF);
        this.tenantId = requireNonBlank(tenantId, "tenantId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        this.limit = limit;
        if (fixedDelayMs <= 0) {
            throw new IllegalArgumentException("fixedDelayMs must be > 0");
        }
        this.fixedDelayMs = fixedDelayMs;
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @PostConstruct
    public void start() {
        scheduler.scheduleWithFixedDelay(this::driveForward, Duration.ofMillis(fixedDelayMs));
        scheduler.scheduleWithFixedDelay(this::driveResponse, Duration.ofMillis(fixedDelayMs));
    }

    /** One forward-relay tick (hop1 req -> hop2 deliver). Package-private for tests. */
    void driveForward() {
        forwardSource.reset();
        try {
            forwardLoop.run(tenantId, limit);
        } catch (RuntimeException e) {
            log.warn("forward relay tick failed; will retry next fire (tenant={})", tenantId, e);
        }
    }

    /** One response-relay tick (resp_in -> resp_out). Package-private for tests. */
    void driveResponse() {
        responseSource.reset();
        try {
            responseLoop.run(tenantId, limit);
        } catch (RuntimeException e) {
            log.warn("response relay tick failed; will retry next fire (tenant={})", tenantId, e);
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /**
     * One-shot TickSource: yields {@code clock.now()} once per {@link RelayDispatchLoop#run},
     * then empty (loop stops => one tick per fire). The driver {@link #driveForward} /
     * {@link #driveResponse} {@link #reset()} it before each run, so each fire yields a
     * fresh tick even if the previous fire's tick threw.
     */
    static final class SingleShotTickSource implements RelayDispatchLoop.TickSource {
        private final LongSupplier clock;
        private boolean armed;

        SingleShotTickSource(LongSupplier clock) {
            this.clock = clock;
        }

        @Override
        public OptionalLong nextTickMillisEpoch() {
            if (!armed) {
                return OptionalLong.empty();
            }
            armed = false;
            return OptionalLong.of(clock.getAsLong());
        }

        void reset() {
            armed = true;
        }
    }
}
