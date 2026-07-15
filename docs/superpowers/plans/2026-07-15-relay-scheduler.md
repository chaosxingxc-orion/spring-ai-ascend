# Relay Scheduler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire a production scheduler that periodically fires `EventBusRelayWorker.runOnce` for both relays (forward + response) on the event-bus process, so the two-hop governance path actually runs in production (today `runOnce` has no production caller).

**Architecture:** Approach B (mirror `ForwardingDispatchLoop`) / lifetime B-i (single-tick-per-fire). A pure `RelayDispatchLoop` (no clock/scheduler/thread — §6.1) holds a `RelayTick` 1-method seam (needed because `EventBusRelayWorker` is `final`/un-mockable). A `RelayScheduler` Spring driver on a **dedicated** `ThreadPoolTaskScheduler` fires `loop.run()` at fixed delay via programmatic `scheduleAtFixedDelay` (not `@Scheduled`, which would land on the registry-probe pool). A one-shot `TickSource` yields `clock.now()` once per fire ⇒ one tick per fire. Per-tick `try/catch` swallow is **load-bearing**: `scheduleAtFixedDelay` cancels its future if the task throws — the swallow prevents that.

**Tech Stack:** Java 17+, Spring Boot 3.2+ (Spring 6 — `RestClient`/`TaskScheduler.scheduleAtFixedDelay(Runnable, Duration)` available), JUnit 5 + AssertJ (both via `spring-boot-starter-test`).

**Spec:** `docs/superpowers/specs/2026-07-15-relay-scheduler-design.md` (commit `1c025b01`, approved).

## Global Constraints

- **Module:** `agent-bus`. Run tests from the repo root with `./mvnw -f agent-bus/pom.xml test ...`.
- **No `@SpringBootTest`** — agent-bus hard convention ("agent-bus has no `@SpringBootTest` anywhere — every test is plain JUnit 5"). Every new test is plain JUnit 5; construct Spring-managed beans manually + call lifecycle methods directly.
- **Purity:** pure Java only in `com.huawei.ascend.bus.forwarding.runtime.relay` (no Spring/JDBC/`org.apache.rocketmq` imports — `AgentBusForwardingSpiPurityTest`). Spring wiring confined to `com.huawei.ascend.bus.gateway.runtime` (licensed — `EventBusRelayConfiguration`/`GatewayRuntimeController` precedent).
- **Profile scope:** new wiring is `@Profile("eventbus")`; gateway `ForwardingDispatcherWorker` same-gap is **out of scope**.
- **TDD red→green per task; frequent commits on `experimental`; push deferred to user** (session convention, same as `60f5dd6a`).
- `@FunctionalInterface` needs no import (it is in `java.lang`, auto-imported — see `ForwardingDispatchLoop`).

### Spec deviations taken at plan-time (intent preserved, mechanism changed to match repo conventions)

1. **Spec §7.2 said `@SpringBootTest` slice → plan uses plain JUnit 5** (agent-bus has no `@SpringBootTest` anywhere). The test manually constructs `ThreadPoolTaskScheduler` + `RelayScheduler` + fakes and calls `start()`. Same intent (boot-verify the scheduler mechanism, no real broker/JDBC), convention-aligned.
2. **Spec §7.2 said Awaitility → plan uses `CountDownLatch`** (repo's concurrency tests use plain-JDK latches; Awaitility availability from the grep was ambiguous). Same intent (await first fire with timeout).
3. **Spec §4.4 left the one-shot `TickSource` mechanism to TDD → plan pins it**: a `SingleShotTickSource` (yields `clock.now()` once then empty) that the driver `reset()`s before each `loop.run()` ⇒ robust across a thrown tick.

---

## Task 1: `RelayTick` seam + `RelayDispatchLoop` (pure) + unit test

**Files:**
- Create: `agent-bus/src/main/java/com/huawei/ascend/bus/forwarding/runtime/relay/RelayTick.java`
- Create: `agent-bus/src/main/java/com/huawei/ascend/bus/forwarding/runtime/relay/RelayDispatchLoop.java`
- Test: `agent-bus/src/test/java/com/huawei/ascend/bus/forwarding/runtime/relay/RelayDispatchLoopTest.java`

**Interfaces:**
- Consumes: `EventBusRelayWorker` (existing, same package — `final` class with `public RelayTickResult runOnce(String, long, int)` + nested `public record RelayTickResult(int relayed, int dedupSuppressed, int governanceRejected, int skipped)`).
- Produces: `RelayTick` (interface, `RelayTickResult runOnce(String, long, int)`); `RelayDispatchLoop` (class, nested `TickSource` + `RelayIdleStrategy` + `NO_BACKOFF` + `RelayTickResult run(String tenantId, int limit)`). Used by Task 2 (`RelayScheduler`).

- [ ] **Step 1: Write the failing test**

Create `agent-bus/src/test/java/com/huawei/ascend/bus/forwarding/runtime/relay/RelayDispatchLoopTest.java`:

```java
package com.huawei.ascend.bus.forwarding.runtime.relay;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deterministic unit tests for {@link RelayDispatchLoop} — the pure relay-tick
 * loop. No Spring / threads / sleep: the {@link RelayTick} worker, the
 * {@link RelayDispatchLoop.TickSource}, and the {@link RelayDispatchLoop.RelayIdleStrategy}
 * are inline fakes. Mirrors the testability story of {@code ForwardingDispatchLoop}.
 */
class RelayDispatchLoopTest {

    /** Fake worker: records (tenant, now, limit) per tick + returns scripted results. */
    static final class RecordingRelayTick implements RelayTick {
        final List<String> tenants = new ArrayList<>();
        final List<Long> nows = new ArrayList<>();
        final List<Integer> limits = new ArrayList<>();
        final Deque<EventBusRelayWorker.RelayTickResult> scripted = new ArrayDeque<>();

        @Override
        public EventBusRelayWorker.RelayTickResult runOnce(String tenantId, long nowMillisEpoch, int limit) {
            tenants.add(tenantId);
            nows.add(nowMillisEpoch);
            limits.add(limit);
            return scripted.isEmpty() ? new EventBusRelayWorker.RelayTickResult(0, 0, 0, 0) : scripted.poll();
        }
    }

    /** Capturing idle strategy: records every idle tick. */
    static final class CapturingIdle implements RelayDispatchLoop.RelayIdleStrategy {
        final List<EventBusRelayWorker.RelayTickResult> idleTicks = new ArrayList<>();
        @Override
        public void onIdle(EventBusRelayWorker.RelayTickResult lastTick) { idleTicks.add(lastTick); }
    }

    /** Queue-based TickSource: yields the given instants in order, then empty (stop). */
    static RelayDispatchLoop.TickSource source(long... instants) {
        Deque<Long> q = new ArrayDeque<>();
        for (long t : instants) q.add(t);
        return () -> q.isEmpty() ? OptionalLong.empty() : OptionalLong.of(q.poll());
    }

    @Test
    void runs_one_tick_per_yielded_instant_and_aggregates() {
        RecordingRelayTick worker = new RecordingRelayTick();
        worker.scripted.add(new EventBusRelayWorker.RelayTickResult(2, 1, 0, 1)); // tick @100
        worker.scripted.add(new EventBusRelayWorker.RelayTickResult(3, 0, 1, 2)); // tick @200
        CapturingIdle idle = new CapturingIdle();
        RelayDispatchLoop loop = new RelayDispatchLoop(worker, source(100L, 200L), idle);

        EventBusRelayWorker.RelayTickResult agg = loop.run("t1", 5);

        assertThat(worker.tenants).containsExactly("t1", "t1");
        assertThat(worker.nows).containsExactly(100L, 200L);
        assertThat(worker.limits).containsExactly(5, 5);
        assertThat(agg.relayed()).isEqualTo(5);            // 2 + 3
        assertThat(agg.dedupSuppressed()).isEqualTo(1);    // 1 + 0
        assertThat(agg.governanceRejected()).isEqualTo(1); // 0 + 1
        assertThat(agg.skipped()).isEqualTo(3);           // 1 + 2
        assertThat(idle.idleTicks).isEmpty();              // neither tick was idle (all counts > 0)
    }

    @Test
    void fires_onIdle_only_for_all_zero_ticks() {
        RecordingRelayTick worker = new RecordingRelayTick();
        worker.scripted.add(new EventBusRelayWorker.RelayTickResult(1, 0, 0, 0)); // busy (relayed)
        worker.scripted.add(new EventBusRelayWorker.RelayTickResult(0, 0, 0, 0)); // idle
        worker.scripted.add(new EventBusRelayWorker.RelayTickResult(0, 0, 0, 1)); // busy (skipped > 0)
        CapturingIdle idle = new CapturingIdle();
        RelayDispatchLoop loop = new RelayDispatchLoop(worker, source(10L, 20L, 30L), idle);

        loop.run("t1", 5);

        assertThat(idle.idleTicks).hasSize(1); // only the all-zero tick (@20) was idle
    }

    @Test
    void stops_when_source_returns_empty() {
        RecordingRelayTick worker = new RecordingRelayTick();
        RelayDispatchLoop loop = new RelayDispatchLoop(worker, source(), new CapturingIdle());

        EventBusRelayWorker.RelayTickResult agg = loop.run("t1", 5);

        assertThat(worker.tenants).isEmpty(); // zero ticks run
        assertThat(agg.relayed()).isZero();
    }

    @Test
    void rejects_blank_tenant_and_non_positive_limit() {
        RelayDispatchLoop loop = new RelayDispatchLoop(new RecordingRelayTick(), source(1L), new CapturingIdle());
        assertThatThrownBy(() -> loop.run("", 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loop.run("t1", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loop.run("t1", -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails (compile-red)**

Run: `./mvnw -f agent-bus/pom.xml test -Dtest=RelayDispatchLoopTest`
Expected: **FAILURE** — compile error: `RelayTick` / `RelayDispatchLoop` cannot be found.

- [ ] **Step 3: Create `RelayTick`**

Create `agent-bus/src/main/java/com/huawei/ascend/bus/forwarding/runtime/relay/RelayTick.java`:

```java
package com.huawei.ascend.bus.forwarding.runtime.relay;

/**
 * One relay tick — the consume→govern→re-produce step a driver periodically fires.
 * Functional seam over the {@code final} {@link EventBusRelayWorker} so the loop
 * ({@link RelayDispatchLoop}) and the driver ({@code RelayScheduler}) can be driven
 * with a fake, and so the worker stays the single concrete relay-tick implementation.
 *
 * <p>Authority: {@code docs/superpowers/specs/2026-07-15-relay-scheduler-design.md} §4.1.
 */
@FunctionalInterface
public interface RelayTick {
    EventBusRelayWorker.RelayTickResult runOnce(String tenantId, long nowMillisEpoch, int limit);
}
```

- [ ] **Step 4: Create `RelayDispatchLoop` (mirror `ForwardingDispatchLoop`)**

Create `agent-bus/src/main/java/com/huawei/ascend/bus/forwarding/runtime/relay/RelayDispatchLoop.java`:

```java
package com.huawei.ascend.bus.forwarding.runtime.relay;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Pure dispatch loop that drives {@link RelayTick#runOnce} ticks from an injected
 * {@link TickSource} — the relay-scheduler primitive mirroring
 * {@code ForwardingDispatchLoop}. The loop owns no clock, no scheduler, no thread
 * pool, no transport (decision §6.1: no scheduler/timer in the bus). The caller
 * injects (a) when to run the next tick ({@link TickSource}) and (b) what to do
 * when a tick polled nothing ({@link RelayIdleStrategy}). Real cadence, threading,
 * idle backoff and fault isolation are the caller's concern.
 *
 * <p>Because the clock + worker are injected, the loop is fully deterministic and
 * unit-testable without any thread or sleep.
 *
 * <p>Authority: {@code docs/superpowers/specs/2026-07-15-relay-scheduler-design.md} §4.2;
 * mirrors {@code ForwardingDispatchLoop} (forwarding/runtime/ForwardingDispatchLoop.java).
 */
public final class RelayDispatchLoop {

    /** Supplies the instant of the next tick, or empty to stop the loop. Injected (no clock in the loop). */
    @FunctionalInterface
    public interface TickSource {
        OptionalLong nextTickMillisEpoch();
    }

    /** Reacts to an idle tick (one that polled nothing — all counts zero). The loop itself never sleeps. */
    @FunctionalInterface
    public interface RelayIdleStrategy {
        void onIdle(EventBusRelayWorker.RelayTickResult lastTick);
    }

    /** No-op idle strategy: never backs off. For fixed-delay drivers / tests. */
    public static final RelayIdleStrategy NO_BACKOFF = tick -> { };

    private final RelayTick worker;
    private final TickSource tickSource;
    private final RelayIdleStrategy idleStrategy;

    public RelayDispatchLoop(RelayTick worker, TickSource tickSource, RelayIdleStrategy idleStrategy) {
        this.worker = Objects.requireNonNull(worker, "worker is required");
        this.tickSource = Objects.requireNonNull(tickSource, "tickSource is required");
        this.idleStrategy = Objects.requireNonNull(idleStrategy, "idleStrategy is required");
    }

    /**
     * Run ticks until {@link TickSource} stops, aggregating the results.
     *
     * @param tenantId tenant scope applied to every tick
     * @param limit    max messages to relay per tick ({@code > 0})
     * @return the aggregate of all ticks run
     * @throws IllegalArgumentException if {@code tenantId} is blank or {@code limit <= 0}
     */
    public EventBusRelayWorker.RelayTickResult run(String tenantId, int limit) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        int relayed = 0;
        int dedupSuppressed = 0;
        int governanceRejected = 0;
        int skipped = 0;
        while (true) {
            OptionalLong next = tickSource.nextTickMillisEpoch();
            if (next.isEmpty()) {
                break;
            }
            long now = next.getAsLong();
            EventBusRelayWorker.RelayTickResult tick = worker.runOnce(tenantId, now, limit);
            relayed += tick.relayed();
            dedupSuppressed += tick.dedupSuppressed();
            governanceRejected += tick.governanceRejected();
            skipped += tick.skipped();
            if (isIdle(tick)) {
                idleStrategy.onIdle(tick);
            }
        }
        return new EventBusRelayWorker.RelayTickResult(relayed, dedupSuppressed, governanceRejected, skipped);
    }

    /** A tick that polled nothing — all four counts zero. */
    private static boolean isIdle(EventBusRelayWorker.RelayTickResult tick) {
        return tick.relayed() == 0 && tick.dedupSuppressed() == 0
                && tick.governanceRejected() == 0 && tick.skipped() == 0;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -f agent-bus/pom.xml test -Dtest=RelayDispatchLoopTest`
Expected: **BUILD SUCCESS**, `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 6: Commit**

```bash
git add agent-bus/src/main/java/com/huawei/ascend/bus/forwarding/runtime/relay/RelayTick.java \
        agent-bus/src/main/java/com/huawei/ascend/bus/forwarding/runtime/relay/RelayDispatchLoop.java \
        agent-bus/src/test/java/com/huawei/ascend/bus/forwarding/runtime/relay/RelayDispatchLoopTest.java
git commit -m "feat(agent-bus): relay-tick seam + RelayDispatchLoop (mirror ForwardingDispatchLoop) + unit test"
```

---

## Task 2: `AgentBusBrokerProperties` fields + `RelayScheduler` driver + `EventBusRelaySchedulingConfig` + scheduler test

**Files:**
- Modify: `agent-bus/src/main/java/com/huawei/ascend/bus/gateway/runtime/AgentBusBrokerProperties.java`
- Create: `agent-bus/src/main/java/com/huawei/ascend/bus/gateway/runtime/RelayScheduler.java`
- Create: `agent-bus/src/main/java/com/huawei/ascend/bus/gateway/runtime/EventBusRelaySchedulingConfig.java`
- Test: `agent-bus/src/test/java/com/huawei/ascend/bus/gateway/runtime/EventBusRelaySchedulerTest.java`

**Interfaces:**
- Consumes: `RelayTick` + `RelayDispatchLoop` (Task 1); `AgentBusBrokerProperties` (existing record, edited here).
- Produces: `RelayScheduler` (`(RelayTick forward, RelayTick response, String tenantId, int limit, long fixedDelayMs, ThreadPoolTaskScheduler)` ctor; `void start()`); `EventBusRelaySchedulingConfig` (`@Bean relayTaskScheduler` + `@Bean relayScheduler`); `AgentBusBrokerProperties.relayTickLimit()` / `.relayFixedDelayMs()`. Used by Task 3 (the `RelayTick` beans) only transitively (Task 3 provides the real `RelayTick` beans this config injects).

- [ ] **Step 1: Write the failing test**

Create `agent-bus/src/test/java/com/huawei/ascend/bus/gateway/runtime/EventBusRelaySchedulerTest.java`:

```java
package com.huawei.ascend.bus.gateway.runtime;

import com.huawei.ascend.bus.forwarding.runtime.relay.RelayTick;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scheduler-mechanism test for {@link RelayScheduler} — plain JUnit 5 (agent-bus has
 * no {@code @SpringBootTest}). Constructs a real {@link ThreadPoolTaskScheduler} +
 * {@link RelayScheduler} + counting-fake {@link RelayTick}, calls {@link RelayScheduler#start()},
 * and awaits the first fire. Does NOT boot RocketMQ/JDBC — the fake RelayTicks replace the
 * real workers. Mirrors the plain-JDK concurrency-test idiom of
 * {@code C3ForwardingMultiWorkerConcurrencyIntegrationTest}.
 */
class EventBusRelaySchedulerTest {

    private ThreadPoolTaskScheduler scheduler;

    private ThreadPoolTaskScheduler newScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setThreadNamePrefix("relay-test-");
        s.initialize(); // required when not Spring-managed
        return s;
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown(); // cancels the scheduled relay tasks
        }
    }

    /** Counting fake: records each call's (tenant, now, limit); can throw on the first N calls. */
    static final class CountingRelayTick implements RelayTick {
        final AtomicInteger calls = new AtomicInteger();
        final List<String> tenants = new ArrayList<>();
        final List<Long> nows = new ArrayList<>();
        final List<Integer> limits = new ArrayList<>();
        int throwOnFirst;
        CountDownLatch latch;

        @Override
        public EventBusRelayWorker.RelayTickResult runOnce(String tenantId, long nowMillisEpoch, int limit) {
            int n = calls.incrementAndGet();
            tenants.add(tenantId);
            nows.add(nowMillisEpoch);
            limits.add(limit);
            if (latch != null) {
                latch.countDown();
            }
            if (throwOnFirst > 0 && n <= throwOnFirst) {
                throw new RuntimeException("simulated tick failure #" + n);
            }
            return new EventBusRelayWorker.RelayTickResult(0, 0, 0, 0); // idle tick
        }
    }

    private static AgentBusBrokerProperties props(int relayTickLimit, long relayFixedDelayMs) {
        // null/0 for the unused fields — the compact constructor applies their defaults.
        return new AgentBusBrokerProperties(
                null, null, null, 0, null, null, 0, 0, 0, "default", relayTickLimit, relayFixedDelayMs);
    }

    @Test
    void scheduler_fires_runOnce_with_correct_args() throws InterruptedException {
        CountingRelayTick fwd = new CountingRelayTick();
        CountingRelayTick resp = new CountingRelayTick();
        CountDownLatch firstFire = new CountDownLatch(1);
        fwd.latch = firstFire;
        scheduler = newScheduler();
        RelayScheduler rs = new RelayScheduler(fwd, resp, "default", 10, 50L, scheduler);

        rs.start();

        assertThat(firstFire.await(2, TimeUnit.SECONDS))
                .as("forward relay fired at least once").isTrue();
        assertThat(fwd.calls.get()).as("forward fired >= 1").isGreaterThanOrEqualTo(1);
        assertThat(resp.calls.get()).as("response fired >= 1").isGreaterThanOrEqualTo(1);
        assertThat(fwd.tenants.get(0)).isEqualTo("default");
        assertThat(fwd.nows.get(0)).as("clock.now() > 0").isGreaterThan(0L);
        assertThat(fwd.limits.get(0)).isEqualTo(10);
    }

    @Test
    void thrown_tick_does_not_kill_scheduler() throws InterruptedException {
        // scheduleAtFixedDelay cancels its future if the task THROWS — the per-tick try/catch
        // in driveForward swallows, so the schedule survives. Await the 2nd call as proof.
        CountingRelayTick fwd = new CountingRelayTick();
        fwd.throwOnFirst = 1; // first call throws
        CountDownLatch secondCall = new CountDownLatch(2);
        fwd.latch = secondCall;
        scheduler = newScheduler();
        RelayScheduler rs = new RelayScheduler(fwd, new CountingRelayTick(), "default", 10, 50L, scheduler);

        rs.start();

        assertThat(secondCall.await(3, TimeUnit.SECONDS))
                .as("scheduler kept firing after the first tick threw — the per-tick try/catch must "
                        + "swallow (else scheduleAtFixedDelay cancels the future)").isTrue();
        assertThat(fwd.calls.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void relay_props_default_when_non_positive() {
        AgentBusBrokerProperties defaulted = props(0, 0L);
        assertThat(defaulted.relayTickLimit()).as("relayTickLimit default 100").isEqualTo(100);
        assertThat(defaulted.relayFixedDelayMs()).as("relayFixedDelayMs default 1000").isEqualTo(1_000L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails (compile-red)**

Run: `./mvnw -f agent-bus/pom.xml test -Dtest=EventBusRelaySchedulerTest`
Expected: **FAILURE** — compile errors: `RelayScheduler` not found; `AgentBusBrokerProperties` has no `relayTickLimit`/`relayFixedDelayMs`.

- [ ] **Step 3: Add the two fields + defaults to `AgentBusBrokerProperties`**

Modify `agent-bus/src/main/java/com/huawei/ascend/bus/gateway/runtime/AgentBusBrokerProperties.java`. Add `relayTickLimit` + `relayFixedDelayMs` to the record header (after `tenant`):

```java
        long leaseDurationMs,
        String tenant,
        int relayTickLimit,
        long relayFixedDelayMs
) {
```

Add two defaults to the compact constructor (after the `tenant` block, before the closing `}`):

```java
        if (tenant == null || tenant.isBlank()) {
            tenant = "default";
        }
        if (relayTickLimit <= 0) {
            relayTickLimit = 100;
        }
        if (relayFixedDelayMs <= 0) {
            relayFixedDelayMs = 1_000L;
        }
    }
}
```

- [ ] **Step 4: Create `RelayScheduler` (the driver)**

Create `agent-bus/src/main/java/com/huawei/ascend/bus/gateway/runtime/RelayScheduler.java`:

```java
package com.huawei.ascend.bus.gateway.runtime;

import com.huawei.ascend.bus.forwarding.runtime.relay.RelayDispatchLoop;
import com.huawei.ascend.bus.forwarding.runtime.relay.RelayTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.PostConstruct;
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
 * <p><b>Fault isolation is load-bearing:</b> {@code scheduleAtFixedDelay} cancels
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
        scheduler.scheduleAtFixedDelay(this::driveForward, Duration.ofMillis(fixedDelayMs));
        scheduler.scheduleAtFixedDelay(this::driveResponse, Duration.ofMillis(fixedDelayMs));
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
```

- [ ] **Step 5: Create `EventBusRelaySchedulingConfig`**

Create `agent-bus/src/main/java/com/huawei/ascend/bus/gateway/runtime/EventBusRelaySchedulingConfig.java`:

```java
package com.huawei.ascend.bus.gateway.runtime;

import com.huawei.ascend.bus.forwarding.runtime.relay.RelayTick;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Schedules the two-hop relay ticks on a dedicated {@link ThreadPoolTaskScheduler}
 * (relay-scheduler slice; spec {@code docs/superpowers/specs/2026-07-15-relay-scheduler-design.md}).
 *
 * <p><b>Dedicated pool, not the global @Scheduled pool:</b> {@code RegistrySchedulingConfig}
 * binds the global {@code @Scheduled} {@code TaskScheduler} to the registry probes via
 * {@code setTaskScheduler}; a {@code @Scheduled} relay method would land there (shared with
 * probes). The relay is instead driven <b>programmatically</b> ({@code scheduleAtFixedDelay})
 * on its own pool — a hung relay tick blocks only its own thread.
 */
@Configuration
@Profile("eventbus")
public class EventBusRelaySchedulingConfig {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler relayTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("relay-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean
    public RelayScheduler relayScheduler(
            @Qualifier("forwardRelayTick") RelayTick forward,
            @Qualifier("responseRelayTick") RelayTick response,
            AgentBusBrokerProperties props,
            ThreadPoolTaskScheduler relayTaskScheduler) {
        return new RelayScheduler(forward, response, props.tenant(),
                props.relayTickLimit(), props.relayFixedDelayMs(), relayTaskScheduler);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -f agent-bus/pom.xml test -Dtest=EventBusRelaySchedulerTest`
Expected: **BUILD SUCCESS**, `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`. The `thrown_tick_does_not_kill_scheduler` test is the load-bearing one — if the try/catch were missing it would time out.

- [ ] **Step 7: Commit**

```bash
git add agent-bus/src/main/java/com/huawei/ascend/bus/gateway/runtime/AgentBusBrokerProperties.java \
        agent-bus/src/main/java/com/huawei/ascend/bus/gateway/runtime/RelayScheduler.java \
        agent-bus/src/main/java/com/huawei/ascend/bus/gateway/runtime/EventBusRelaySchedulingConfig.java \
        agent-bus/src/test/java/com/huawei/ascend/bus/gateway/runtime/EventBusRelaySchedulerTest.java
git commit -m "feat(agent-bus): RelayScheduler driver + EventBusRelaySchedulingConfig (dedicated ThreadPoolTaskScheduler, B-i) + scheduler test"
```

---

## Task 3: Wire the real `RelayTick` beans + fix the boot-correctness javadoc over-claim

**Files:**
- Modify: `agent-bus/src/main/java/com/huawei/ascend/bus/gateway/runtime/EventBusRelayConfiguration.java`

**Interfaces:**
- Consumes: `EventBusRelayWorker` beans `forwardRelayWorker` / `responseRelayWorker` (existing, same file); `RelayTick` (Task 1).
- Produces: `RelayTick` beans `forwardRelayTick` / `responseRelayTick` — consumed by `EventBusRelaySchedulingConfig.relayScheduler` (Task 2).

- [ ] **Step 1: Add the `RelayTick` import**

In `EventBusRelayConfiguration.java`, after the existing `import com.huawei.ascend.bus.forwarding.runtime.relay.EventBusRelayWorker;` line, add:

```java
import com.huawei.ascend.bus.forwarding.runtime.relay.RelayTick;
```

- [ ] **Step 2: Add the two `RelayTick` beans**

Insert after the `responseRelayWorker` `@Bean` method (before the `relaySubscriptions` `SmartLifecycle`):

```java
    /** {@link RelayTick} seam bound to the forward relay worker (hop1 req -> hop2 deliver). */
    @Bean(name = "forwardRelayTick")
    RelayTick forwardRelayTick(@Qualifier("forwardRelayWorker") EventBusRelayWorker forwardRelayWorker) {
        return forwardRelayWorker::runOnce;
    }

    /** {@link RelayTick} seam bound to the response relay worker (resp_in -> resp_out). */
    @Bean(name = "responseRelayTick")
    RelayTick responseRelayTick(@Qualifier("responseRelayWorker") EventBusRelayWorker responseRelayWorker) {
        return responseRelayWorker::runOnce;
    }
```

- [ ] **Step 3: Fix the boot-correctness over-claim in the class javadoc**

In the class javadoc `<p><b>Verification:</b>` block (the four lines starting `* <p><b>Verification:</b> compile-verified (full suite green); producer {@code start()} +`), replace:

```java
 * <p><b>Verification:</b> compile-verified (full suite green); producer {@code start()} +
 * subscribe-at-startup + the relay-consume→re-produce loop boot-correctness is verified by
 * the two-hop IT (G5-E, env-guarded against the real broker). See
 * {@code docs/4plus1/delta/event-bus-relay/deviations.md}.
```

with:

```java
 * <p><b>Verification:</b> compile-verified (full suite green); producer {@code start()} +
 * subscribe-at-startup boot-correctness is verified by the two-hop IT (G5-E, env-guarded
 * against the real broker). The scheduler that periodically drives {@code runOnce} is
 * mechanism-verified by the {@code EventBusRelaySchedulerTest} slice (plain-JUnit, dedicated
 * ThreadPoolTaskScheduler + fake RelayTick); the full event-bus context boot vs real
 * broker+Postgres remains env-deferred. See
 * {@code docs/4plus1/delta/event-bus-relay/deviations.md}.
```

- [ ] **Step 4: Run the full agent-bus suite (no regression; new beans are @Profile-gated so no existing test loads them)**

Run: `./mvnw -f agent-bus/pom.xml test`
Expected: **BUILD SUCCESS**, `Tests run: 441` (434 existing + 4 `RelayDispatchLoopTest` + 3 `EventBusRelaySchedulerTest`)`, ... Skipped: 14, Failures: 0, Errors: 0`. ArchUnit purity rules (`AgentBusForwardingSpiPurityTest`) still green — pure `RelayTick`/`RelayDispatchLoop` in `forwarding.runtime.relay`, Spring wiring in `gateway.runtime`.

- [ ] **Step 5: Commit**

```bash
git add agent-bus/src/main/java/com/huawei/ascend/bus/gateway/runtime/EventBusRelayConfiguration.java
git commit -m "feat(agent-bus): wire RelayTick beans into EventBusRelayConfiguration + fix boot-correctness javadoc over-claim"
```

---

## Task 4: Record the gap resolution in `deviations.md`

**Files:**
- Modify: `docs/4plus1/delta/event-bus-relay/deviations.md`

**Interfaces:** none (docs only).

- [ ] **Step 1: Append the "Scheduler gap → resolved" section + a G5-E footnote**

At the end of `deviations.md` (after the `### #3 D13 ...` section), append:

```markdown

---

## Scheduler gap → resolved (2026-07-15)

**Gap.** `EventBusRelayWorker.runOnce` (the two-hop relay tick) had **no production caller**. `EventBusRelayConfiguration` wired the worker beans + subscribe-at-startup, but nothing drove `runOnce`, so the two-hop path did not run in production — hop1 messages piled up on `ascend_bus_*_req` / `ascend_bus_*_resp_in` and were never relayed to hop2. Only tests called `runOnce` (`EventBusRelayWorkerTest` unit; `RealBrokerTwoHopRelayIntegrationTest` IT — direct construction + manual drive). **This is why G5-E did not surface the gap:** G5-E validates loop *logic* + substrate (direct construction against the real broker + Postgres), not boot or scheduling.

**Resolution (Approach B/B-i, spec `docs/superpowers/specs/2026-07-15-relay-scheduler-design.md`).** A production driver now periodically fires `runOnce` for both relays on a dedicated `ThreadPoolTaskScheduler`. The shape mirrors `ForwardingDispatchLoop`: a pure `RelayDispatchLoop` (no clock/scheduler/thread — §6.1) holds a `RelayTick` 1-method seam (bound to `forwardRelayWorker::runOnce` / `responseRelayWorker::runOnce`; the seam exists because `EventBusRelayWorker` is `final`/un-mockable). Lifetime B-i = single-tick-per-fire: `scheduleAtFixedDelay` fires `loop.run()` at fixed delay; a one-shot `TickSource` yields `clock.now()` once then empty ⇒ one tick per fire; `NO_BACKOFF` idle (drain/backoff dormant; value = shape-consistency + swap-readiness). **Fault isolation is load-bearing:** `scheduleAtFixedDelay` cancels its future if the task throws, so each drive wraps `loop.run` in `try/catch` (RuntimeException) — logged + swallowed (mirrors `MvpHealthProbeScheduler`). Dedicated pool (not the global `@Scheduled` pool, which `RegistrySchedulingConfig` binds to registry probes) — a hung relay tick blocks only its own thread.

**Verification.** `RelayDispatchLoopTest` (unit, deterministic — fake `RelayTick` + queue `TickSource` + capturing idle; no threads) + `EventBusRelaySchedulerTest` (plain JUnit — manual `ThreadPoolTaskScheduler` + counting-fake `RelayTick` + the swallow-is-load-bearing test). The scheduler *mechanism* is unit/slice-verified; the full event-bus context boot vs real broker+Postgres remains env-deferred (as for G5-B). The real `RelayTick` @Bean wiring (`forwardRelayWorker::runOnce`) is compile + suite verified. **NOT a REQ-2026-001 release blocker.**

**Scope-honesty correction.** The earlier G5-B/G5-E framing ("boot-correctness verified by the two-hop IT (G5-E)") over-claimed: G5-E verifies the *substrate* (producer/consumer/inbox/outbox + subscribe, via direct construction + real broker/Postgres) and a *manually-driven* tick — NOT the periodic scheduler that drives `runOnce` in production. That scheduler did not exist until this slice; it is now wired + mechanism-verified. Committed on `experimental` 2026-07-15 (push deferred to user per session convention).
```

Then add a one-line cross-reference footnote at the end of the `## G5-E ...` section (after its "Test isolation." paragraph, before the `---` separator):

```markdown

**Note (2026-07-15):** G5-E verifies the *substrate* + a manually-driven tick, not the production scheduler that periodically drives `runOnce` — that gap is resolved in the "Scheduler gap → resolved" section below.
```

- [ ] **Step 2: Commit**

```bash
git add docs/4plus1/delta/event-bus-relay/deviations.md
git commit -m "docs(agent-bus): deviations — scheduler gap resolved + correct G5-B/G5-E boot framing"
```

---

## Task 5: Final DoD verification

**Files:** none (verification only).

- [ ] **Step 1: Full agent-bus suite green, no regression**

Run: `./mvnw -f agent-bus/pom.xml test`
Expected: **BUILD SUCCESS**, `Tests run: 441, Skipped: 14, Failures: 0, Errors: 0` (14 skip = env-guarded real-broker ITs, no `ROCKETMQ_NAMESERVER`; the scheduler is mock-booted so no broker needed).

- [ ] **Step 2: Working tree clean (all committed; push deferred to user)**

Run: `git status --short && git log --oneline -6`
Expected: empty `git status` output; `git log` shows this plan commit plus the four implementation commits (Tasks 1–4) on top of `1c025b01` (spec) on `experimental`. Do NOT push — push is deferred to the user per session convention.

- [ ] **Step 3: Out-of-repo housekeeping (assistant, not a repo commit)**

Update the `event-bus-relay-arch-driven` memory file (`C:\Users\d00650599\.claude\projects\D--code-spring-ai-ascend-d00650599-2-spring-ai-ascend\memory\event-bus-relay-arch-driven.md`): append that the scheduler driver is now wired (no longer a gap), Approach B/B-i, the 4 commits' hashes, the 2 new tests, and link to `docs/superpowers/specs/2026-07-15-relay-scheduler-design.md` + `docs/superpowers/plans/2026-07-15-relay-scheduler.md`. Update the `MEMORY.md` index line if needed.

---

## Self-review (plan-author, done)

**Spec coverage:** §4.1 `RelayTick` → Task 1 ✓; §4.2 `RelayDispatchLoop` → Task 1 ✓; §4.3 `EventBusRelaySchedulingConfig` → Task 2 ✓; §4.4 `RelayScheduler` → Task 2 ✓; §4.5 `EventBusRelayConfiguration` edits → Task 3 ✓; §4.6 `AgentBusBrokerProperties` → Task 2 ✓; §5 data flow → Task 3 wiring ✓; §6 error handling (try/catch swallow) → Task 2 (load-bearing test) ✓; §7.1 `RelayDispatchLoopTest` → Task 1 ✓; §7.2 scheduler test → Task 2 (mechanism refined to plain JUnit per repo convention) ✓; §9 deviations → Task 4 ✓; §11 verification → Task 5 ✓.
**Placeholder scan:** none — every step has complete code + exact commands + expected output.
**Type consistency:** `RelayTick.runOnce(String,long,int)→RelayTickResult` consistent across Task 1 (interface + loop) and Task 2 (driver + fake). `RelayDispatchLoop.run(String,int)→RelayTickResult` consistent. `RelayScheduler(RelayTick,RelayTick,String,int,long,ThreadPoolTaskScheduler)` consistent between Task 2 impl + test. `AgentBusBrokerProperties` 12-arg ctor consistent between Task 2 edit + test. `SingleShotTickSource.reset()` defined + called in `driveForward`/`driveResponse`. ✓.
