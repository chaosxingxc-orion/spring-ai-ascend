# Relay Scheduler Design — agent-bus event-bus two-hop relay driver

**Date:** 2026-07-15
**Status:** Approved (brainstorm sign-off 2026-07-15) — ready for implementation plan
**Approach:** B (mirror `ForwardingDispatchLoop`) / lifetime B-i (single-tick-per-fire)
**Scope:** `agent-bus` eventbus process (`@Profile("eventbus")`)

---

## 1. Background & problem

The event-bus two-hop governance relay (FEAT-013/014) was delivered via the arch-driven loop (CLOSED; commits `96901bd0` / `a305dde7` PUSHED to `origin/experimental`; 2 deferred review findings ACCEPT-as-is, commit `60f5dd6a` local unpushed). The relay worker `EventBusRelayWorker.runOnce(tenantId, nowMillisEpoch, limit)` is the consume→govern→re-produce tick: poll a hop1 message → inbox dedup / correlation-match / audit → re-publish as hop2 → commit (model B ack-after-consume).

**Gap (surfaced 2026-07-15):** `runOnce` has **no production caller.** Verified by reading `EventBusRelayConfiguration` (`@Profile("eventbus")`) end-to-end (all 181 lines): it wires the two `EventBusRelayWorker` beans (`forwardRelayWorker`, `responseRelayWorker`) + a subscribe-at-startup `SmartLifecycle` (`relaySubscriptions`), but contains **no `@Scheduled` / `TaskScheduler` / run-loop / `@PostConstruct` driver**. Only tests call `runOnce` — `EventBusRelayWorkerTest` (unit) and `RealBrokerTwoHopRelayIntegrationTest` (IT, which directly constructs beans and manually drives `runOnce`, acting as the scheduler).

**Consequence:** when the event-bus process boots (`--spring.profiles.active=eventbus`), consumers subscribe at startup but nothing polls → hop1 messages pile up on `ascend_bus_*_req` / `ascend_bus_*_resp_in` and are never relayed to hop2. **The two-hop path does not run in production.**

**Why G5-E (4/4 green) didn't catch it:** the IT does not boot the Spring context — it directly constructs beans and manually drives `runOnce`. It validates loop *logic* + substrate, not boot. Hence `deviations.md`'s claim *"relay-consume→re-produce loop boot-correctness verified by the two-hop IT (G5-E)"* is an **over-claim** and is corrected as part of this slice (§9).

---

## 2. Goal & success criteria

Wire a production driver that periodically fires `runOnce` for both relays on the event-bus process, so the two-hop path runs in production.

- **DoD (build):** `./mvnw -f agent-bus/pom.xml test` green (434 + N-new green / 14 skip / 0 fail, no regression); `git status --short` clean; new commit on `experimental` (push deferred to user per session convention, same as `60f5dd6a`).
- **DoD (scope honesty):** the scheduler *mechanism* is boot-verified by a `@SpringBootTest` slice; full event-bus context boot vs real broker+Postgres remains env-deferred (G5-E covers substrate logic via direct construction).
- **Non-blocker:** NOT a REQ-2026-001 release blocker (release bar = InMemoryBroker CI gate + real RocketMQ env-guarded + TestAgentRuntime E2E; none require an independent relay process to boot).

---

## 3. Approach — B / B-i

**B (mirror `ForwardingDispatchLoop`):** drive the relay through a loop primitive that shares the forwarding substrate's shape — `RelayTick` seam + `RelayDispatchLoop` (pure, holds no clock/scheduler/thread per decision §6.1) + Spring driver. This makes the relay the **first production driver for the loop shape** that `ForwardingDispatchLoop` (today test-only) can later reuse, and keeps one canonical loop abstraction in the bus.

> **B does not avoid the `RelayTick` seam.** `ForwardingDispatchLoop` is unit-testable precisely because the worker + clock are *injected*; it holds `ForwardingDispatcherWorker` concretely. `EventBusRelayWorker` is `final` (un-mockable), so the relay loop must hold an injectable abstraction — the `RelayTick` seam. The loop layer is the only net-new surface over a direct (A-style) driver; the seam is shared.

**B-i lifetime (single-tick-per-fire):** the dedicated scheduler fires `loop.run(tenant, limit)` at fixed delay; a one-shot `TickSource` yields `clock.now()` once per `run()` then empty ⇒ exactly one tick per fire; `NO_BACKOFF` idle. The loop's drain/backoff power is *dormant* in B-i. Value: **shape-consistency** (relay driven via the same loop primitive forwarding is designed around) + **swap-readiness** (a future B-iii drain-until-idle or B-ii run-forever-backoff variant is a wiring-only change to `TickSource` / `IdleStrategy`; loop + driver contract untouched).

**Rejected lifetime alternatives** (deferred unless a need surfaces):
- **B-iii** (drain-until-idle bursts per fire) — exercises adaptive drain but adds a capped-burst `TickSource` + reschedule logic + unbounded-burst concerns.
- **B-ii** (run-forever with `IdleStrategy` sleep-backoff on a forever-blocking thread) — bespoke forever-blocking threading + shutdown signaling; the bespoke-threading downside.

---

## 4. Components

### 4.1 `RelayTick` (NEW — pure Java, `forwarding.runtime.relay`)
1-method functional seam:
```java
@FunctionalInterface
public interface RelayTick {
    EventBusRelayWorker.RelayTickResult runOnce(String tenantId, long nowMillisEpoch, int limit);
}
```
Wired (in `EventBusRelayConfiguration`, §4.5) to `forwardRelayWorker::runOnce` / `responseRelayWorker::runOnce`. Reuses the existing public record `EventBusRelayWorker.RelayTickResult` (`relayed, dedupSuppressed, governanceRejected, skipped`).

### 4.2 `RelayDispatchLoop` (NEW — pure Java, `final`, `forwarding.runtime.relay`)
Mirrors `ForwardingDispatchLoop`:
```java
public final class RelayDispatchLoop {
    @FunctionalInterface
    public interface TickSource {              // empty = stop the loop
        OptionalLong nextTickMillisEpoch();
    }
    @FunctionalInterface
    public interface RelayIdleStrategy {
        void onIdle(EventBusRelayWorker.RelayTickResult lastTick);
    }
    public static final RelayIdleStrategy NO_BACKOFF = tick -> { };

    private final RelayTick worker;
    private final TickSource tickSource;
    private final RelayIdleStrategy idleStrategy;
    // ctor: requireNonNull(worker, tickSource, idleStrategy)

    public EventBusRelayWorker.RelayTickResult run(String tenantId, int limit) {
        // throw IllegalArgumentException on blank tenantId / limit<=0  (mirror ForwardingDispatchLoop.run)
        // while (true):
        //     next = tickSource.nextTickMillisEpoch();  if empty break;
        //     tick = worker.runOnce(tenantId, next.getAsLong(), limit);
        //     aggregate(relayed, dedupSuppressed, governanceRejected, skipped) += tick;
        //     if (allZero(tick)) idleStrategy.onIdle(tick);
        // return aggregate RelayTickResult;
    }
}
```
- `now` comes from the `TickSource` (mirrors `ForwardingDispatchLoop` where `now = next.getAsLong()`); the relay's `runOnce` is 3-arg `(tenantId, now, limit)` with no per-call lease params (lease is a worker constructor field), so `run` is 2-arg `(tenantId, limit)`.
- **idle predicate** (for `onIdle`): `tick.relayed()==0 && tick.dedupSuppressed()==0 && tick.governanceRejected()==0 && tick.skipped()==0` — all-zero ⟺ polled nothing ⟺ idle (`runOnce` breaks on empty poll; every polled message increments exactly one count).
- **No try/catch, no sleep** inside the loop — fault isolation + cadence are the driver's job (the `ForwardingDispatchLoop` contract).

### 4.3 `EventBusRelaySchedulingConfig` (NEW — `@Configuration`, `gateway.runtime`, `@Profile("eventbus")`)
```java
@Configuration
@Profile("eventbus")
public class EventBusRelaySchedulingConfig {
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler relayTaskScheduler() {
        // pool 2, "relay-" prefix, waitForTasksToCompleteOnShutdown=false  (mirrors registryProbeTaskScheduler)
    }
    @Bean
    public RelayScheduler relayScheduler(
            @Qualifier("forwardRelayTick") RelayTick forward,
            @Qualifier("responseRelayTick") RelayTick response,
            AgentBusBrokerProperties props,
            ThreadPoolTaskScheduler relayTaskScheduler) { ... }
}
```
Diverges from `RegistrySchedulingConfig` deliberately: it does **not** `implements SchedulingConfigurer` / `setTaskScheduler` (that would re-bind the global `@Scheduled` pool). It drives the relay **programmatically** via `relayTaskScheduler.scheduleAtFixedDelay(...)` so the relay runs on its own isolated pool and never touches the registry-probe pool or other `@Scheduled` beans.

### 4.4 `RelayScheduler` (NEW — driver, `gateway.runtime`)
- Fields: two `RelayDispatchLoop` (`forwardLoop`, `responseLoop` — each built from its `RelayTick` + a one-shot `TickSource`(`System::currentTimeMillis`) + `NO_BACKOFF`), `String tenant`, `int limit`, `long fixedDelayMs`, `ThreadPoolTaskScheduler scheduler`.
- `@PostConstruct`: `scheduler.scheduleAtFixedDelay(this::driveForward, fixedDelayMs)` + `scheduler.scheduleAtFixedDelay(this::driveResponse, fixedDelayMs)`.
- `driveForward()` / `driveResponse()`:
  ```java
  try {
      forwardLoop.run(tenant, limit);   // one-shot source ⇒ one tick @ clock.now()
  } catch (RuntimeException e) {
      log.warn("forward relay tick failed; will retry next fire", e);   // swallow
  }
  ```
- **Startup ordering:** `@PostConstruct` runs during refresh (before `SmartLifecycle.start()`); the first fire is ~`fixedDelayMs` (default 1s) after `@PostConstruct`. `relaySubscriptions.start()` (the subscribe-at-startup `SmartLifecycle`) completes synchronously at end of refresh (milliseconds), so by the first fire the consumers are subscribed + started — **no startup race in practice**. Even if a future tuning sets `fixedDelayMs` very low, the driver's try/catch swallows any pre-subscribe poll error and the path self-corrects once subscribe lands.
- **One-shot `TickSource` semantics:** yields `clock.getAsLong()` exactly once per `loop.run()` invocation, then `empty` (loop stops ⇒ one tick per fire). The implementation must yield exactly one **fresh** tick per fire and remain correct across a thrown tick (the driver's try/catch swallows; the next fire must still yield a fresh tick). Exact mechanism (resettable source reset before each `run()` vs a self-rearming toggle source) is an implementation choice pinned by the boot test.
- **Shutdown:** `relayTaskScheduler` is a `@Bean(destroyMethod="shutdown")` with `waitForTasksToCompleteOnShutdown=false` ⇒ on context shutdown the scheduler shuts down promptly and its scheduled relay tasks are cancelled. `RelayScheduler` needs no destroy logic of its own.

### 4.5 `EventBusRelayConfiguration` (EDIT — `gateway.runtime`)
- Add 2 `RelayTick` @Beans:
  ```java
  @Bean(name = "forwardRelayTick")
  RelayTick forwardRelayTick(@Qualifier("forwardRelayWorker") EventBusRelayWorker w) { return w::runOnce; }

  @Bean(name = "responseRelayTick")
  RelayTick responseRelayTick(@Qualifier("responseRelayWorker") EventBusRelayWorker w) { return w::runOnce; }
  ```
- **Fix the over-claim** in the class javadoc (lines 50-53): *"producer `start()` + subscribe-at-startup + the relay-consume→re-produce loop boot-correctness is verified by the two-hop IT (G5-E)"* → *"producer `start()` + subscribe-at-startup boot-correctness is verified by the two-hop IT (G5-E, env-guarded); the scheduler that drives `runOnce` is boot-verified by the `@SpringBootTest` scheduler slice (this slice). Full event-bus context boot vs real broker+Postgres remains env-deferred."*

### 4.6 `AgentBusBrokerProperties` (EDIT — record, `gateway.runtime`)
Add 2 fields with compact-constructor defaults (mirrors the 6 existing `if (x<=0) x=default` fields; **not** `@Value`):
```java
int relayTickLimit,         // default 100
long relayFixedDelayMs      // default 1000
```
```java
if (relayTickLimit <= 0)     relayTickLimit = 100;
if (relayFixedDelayMs <= 0)  relayFixedDelayMs = 1000;
```

---

## 5. Data flow / wiring

- **Boot (eventbus profile):** `EventBusRelayConfiguration` wires workers / consumers / producers / inbox / outbox + `relaySubscriptions` (subscribe at startup) + 2 `RelayTick` beans. `EventBusRelaySchedulingConfig` wires `relayTaskScheduler` + `RelayScheduler` (`@PostConstruct` schedules 2 fixed-delay tasks on the dedicated pool).
- **Per forward fire:** `driveForward()` → `forwardLoop.run(tenant, limit)` → `forwardRelayTick.runOnce(tenant, now, limit)` = poll hop1 req → govern (dedup / correlation / audit) → re-publish hop2 deliver → commit.
- **Per response fire:** same, `resp_in` → `resp_out`.
- **Isolation:** forward + response run on separate `relayTaskScheduler` threads (pool 2) — neither stalls the other; neither shares the registry-probe pool (`registryProbeTaskScheduler`) or other Spring `@Scheduled` beans (which land on the registry pool via `RegistrySchedulingConfig.setTaskScheduler`).

---

## 6. Error handling

- **Per-fire try/catch (RuntimeException)** at the driver, `log.warn` + swallow → a thrown tick/loop doesn't kill the scheduler; the next fixed-delay fire runs clean. Mirrors `MvpHealthProbeScheduler`'s per-probe fault isolation.
- `runOnce` throws `IllegalArgumentException` only on blank tenant / `limit<=0` (a config bug). `tenant` comes from props (default `"default"`, non-blank); `limit` defaults 100 — so this won't occur in practice. Per-record failures inside `runOnce` are reject+commit (no throw) — they never reach the driver's catch.
- **Scheduler isolation:** a hung relay tick (slow broker poll) blocks ONE relay's thread only, not the other relay, not the registry probes, not other `@Scheduled` beans. `destroyMethod=shutdown` + `waitForTasksToCompleteOnShutdown=false` ⇒ prompt stop on context shutdown.

---

## 7. Testing (TDD)

### 7.1 `RelayDispatchLoopTest` (unit — pure/deterministic; the B upside)
Fake `RelayTick` + a queue-based `TickSource` (fixed instants, then empty) + capturing `RelayIdleStrategy`. Asserts:
- `runOnce` called with the correct `(tenant, now, limit)` per yielded instant;
- aggregates `RelayTickResult` correctly (sum of relayed/dedup/rejected/skipped across ticks);
- fires `onIdle` iff a tick was all-zero; does **not** fire when any count > 0;
- stops when `TickSource` returns empty;
- throws `IllegalArgumentException` on blank tenant / `limit<=0` (mirror `ForwardingDispatchLoop.run`).
**No Spring / threads / sleep. Red→green TDD anchor** — write first, watch red, implement, watch green.

### 7.2 `EventBusRelaySchedulerBootTest` (`@SpringBootTest` slice)
`@Import(EventBusRelaySchedulingConfig.class)` + counting-fake `RelayTick` @Beans (forward+response, via a `@TestConfiguration` that `@Bean`-overrides both) + `@TestPropertySource` short `agent-bus.relay-fixed-delay-ms=50` (and `agent-bus.relay-tick-limit=10`, plus minimal `agent-bus.nameserver` / `namespace` / `producer-group` props to satisfy the record) + Awaitility await first fire (~2s timeout). Asserts the fake's `runOnce` was called ≥1× with `(tenant, now>0, limit)`. Does **not** boot RocketMQ/JDBC — the fake `RelayTick` beans replace the real workers; `EventBusRelaySchedulingConfig` depends only on `RelayTick` + `AgentBusBrokerProperties` + `relayTaskScheduler`.
**Honest scope:** scheduler *mechanism* boot-verified (slice); full context boot vs real broker+Postgres still env-deferred (G5-E covers substrate via direct construction).

### 7.3 Unchanged
`EventBusRelayWorkerTest` (unit) + `RealBrokerTwoHopRelayIntegrationTest` (IT) unchanged (no scheduling/filter change → the IT runs as an env-guarded skip without `ROCKETMQ_NAMESERVER`).

---

## 8. Configuration

| Property | Type | Default | Notes |
|---|---|---|---|
| `agent-bus.relay-tick-limit` | int | 100 | per-tick poll cap; `runOnce` drains ≤100/tick; backlog clears across ticks |
| `agent-bus.relay-fixed-delay-ms` | long | 1000 | 1s cadence (hotter than the registry probe's 5s); configurable |

---

## 9. Recording (part of this slice)

- **`docs/4plus1/delta/event-bus-relay/deviations.md`:**
  - Correct the over-claim "boot-correctness verified by the IT (G5-E)" (in the G5-B / G5-E sections) → "scheduler mechanism boot-verified via the `@SpringBootTest` scheduler slice; full event-bus context boot vs real broker+Postgres still env-deferred (G5-E covers substrate logic via direct construction)."
  - Add a "Scheduler gap → resolved (2026-07-15)" section: the gap, the B/B-i approach, the new wiring (`RelayTick` + `RelayDispatchLoop` + `EventBusRelaySchedulingConfig` + `RelayScheduler` + 2 `RelayTick` beans + 2 props), the slice tests, the commit hash once landed.
- **Memory:** update `event-bus-relay-arch-driven` to note the scheduler driver is now wired (no longer a gap) and link to this spec.

---

## 10. Out of scope

- Gateway `ForwardingDispatcherWorker` has the same no-driver gap (claim outbox → deliver, `@Profile("gateway")`); **noted, not fixed here** — separate slice.
- Generalizing `ForwardingDispatchLoop` / `RelayDispatchLoop` into one generic loop primitive (`DispatchLoop<W, R>`) — would touch the forwarding loop + its existing tests; explicitly deferred (this slice *mirrors*, does not unify).
- B-iii (drain-until-idle) / B-ii (run-forever backoff) lifetime variants — deferred unless a need surfaces.
- Multi-tenant relay subscribe (deferred finding #3, ACCEPT-as-is) — unchanged.

---

## 11. Verification (DoD)

- `./mvnw -f agent-bus/pom.xml test` → 434 + N-new green / 14 skip / 0 fail (no regression). New tests: `RelayDispatchLoopTest` (unit) + `EventBusRelaySchedulerBootTest` (slice).
- ArchUnit purity suite (`AgentBusForwardingSpiPurityTest`) green — confirm the pure `RelayTick` / `RelayDispatchLoop` in `forwarding.runtime.relay` and the Spring wiring (`EventBusRelaySchedulingConfig` / `RelayScheduler`) in `gateway.runtime` satisfy the existing rules (no new transport.broker / SPI violations).
- `git status --short` clean; new commit on `experimental` (push deferred to user per session convention, same as `60f5dd6a`).

---

## 12. References

- **This builds on (CLOSED):** arch-driven event-bus-relay — `docs/4plus1/delta/event-bus-relay/` (scope, decision-tree, overview, as-built, ADR-0161, CLOSED.md); commits `96901bd0` / `a305dde7` PUSHED; `60f5dd6a` local (findings #1/#3 ACCEPT).
- **Impl touchpoints:** `EventBusRelayWorker.java` (`final`, `runOnce` 3-arg, `RelayTickResult` record) · `EventBusRelayConfiguration.java` (edit: 2 `RelayTick` beans + javadoc fix) · `AgentBusBrokerProperties.java` (edit: 2 fields) · `RegistrySchedulingConfig.java` + `MvpHealthProbeScheduler.java` (patterns mirrored) · `ForwardingDispatchLoop.java` (shape mirrored).
- **L2 spec:** `architecture/L2-Low-Level-Design/agent-bus/feat-013-client-invocation-event-forwarding.md` (§1.2 治理中继 / §4.1 两跳时序 / §5.1 部署).
- **Memory:** `event-bus-relay-arch-driven` (CLOSED) · `broker-filtering-spi-impl` (findings resolved) · `feat-013-014-broker-decisions` (4 L2 constraints).
