package com.huawei.ascend.bus.forwarding.runtime.relay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.ScheduledFuture;
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
 * <p><b>Lifecycle:</b> implements {@link SmartLifecycle} so the scheduler only
 * starts ticking after the {@code relaySubscriptions} SmartLifecycle (phase
 * {@code Integer.MIN_VALUE + 100}) has subscribed the relay consumers — the
 * scheduler's phase is {@code Integer.MIN_VALUE + 101}, guaranteeing
 * subscribe-before-poll ordering. Previously {@link jakarta.annotation.PostConstruct}
 * started the scheduler during bean init, which races ahead of the
 * subscription lifecycle and throws {@code "polled before subscribe"} on the
 * first few ticks (PR #389 E2E startup issue).
 *
 * <p>Authority: {@code docs/superpowers/specs/2026-07-15-relay-scheduler-design.md} §4.4.
 */
public class RelayScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RelayScheduler.class);

    private final RelayDispatchLoop forwardLoop;
    private final RelayDispatchLoop responseLoop;
    private final SingleShotTickSource forwardSource;
    private final SingleShotTickSource responseSource;
    private final String tenantId;
    private final int limit;
    private final long fixedDelayMs;
    private final ThreadPoolTaskScheduler scheduler;

    private volatile boolean running;
    private volatile ScheduledFuture<?> forwardFuture;
    private volatile ScheduledFuture<?> responseFuture;

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

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        forwardFuture = scheduler.scheduleWithFixedDelay(this::driveForward, Duration.ofMillis(fixedDelayMs));
        responseFuture = scheduler.scheduleWithFixedDelay(this::driveResponse, Duration.ofMillis(fixedDelayMs));
        running = true;
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (forwardFuture != null) {
            forwardFuture.cancel(false);
            forwardFuture = null;
        }
        if (responseFuture != null) {
            responseFuture.cancel(false);
            responseFuture = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Phase after relaySubscriptions (Integer.MIN_VALUE + 100): subscribe-before-poll.
        return Integer.MIN_VALUE + 101;
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
