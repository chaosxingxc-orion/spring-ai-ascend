# 0031. Three-Track Channel Isolation for Northbound Streaming

**Status:** accepted
**Deciders:** architecture
**Date:** 2026-05-12
**Technical story:** Fifth architecture reviewer raised Finding 4: the workflow intermediary risks
becoming a "rigid Temporal-as-God orchestrator." Investigation confirmed this is factually wrong at
W0 (SyncOrchestrator is 102 lines; Orchestrator SPI is one method; Temporal is a W4 durability tier
behind the same SPI, not committed). However, the reviewer's constructive proposals surface a real
gap: §4 #11's streamed northbound surface (deferred W2) mixes control-flow signals, data-plane
progress events, and heartbeats on a single `Flux<RunEvent>` channel. Systematic Category D audit
confirmed this (HD-D.1) and surfaced five additional gaps (HD-D.2 through HD-D.6). This ADR
addresses all six through three-track channel isolation. All implementation deferred to W2.

## Context

§4 #11 (Northbound handoff contract) names three modes:
1. Synchronous `Object` return — shipped at W0.
2. Streamed `Flux<RunEvent>` — deferred W2 (Rule 15).
3. Yield-via-`SuspendSignal` — shipped at W0.

When the W2 streamed surface is introduced, it currently plans to emit: typed progress events,
heartbeats (≤30s), and terminal frames — all on the same `Flux<RunEvent>`. This design has three
failure modes:

**HD-D.1 — Channel mixing:** A heavy-data progress event (e.g., RAG document blob in a
`NodeCompleted` event) occupies the channel and delays heartbeats. Downstream consumers may
incorrectly infer a dead connection and cancel. In financial-services contexts, a false cancellation
on a 6-hour approval-flow run is a production incident.

**HD-D.2 — Control channel gap:** The only cancel mechanism available to the caller at W2 is
cancelling the `Flux` subscription. This is a reactive-streams cancel, not a platform-level
`RunStatus.CANCELLED` transition. There is no out-of-band control plane for injecting cancel /
priority-suspend commands into an in-flight Run without subscribing to the full data stream first.

**HD-D.3 — Heartbeat-on-data contention:** When the data stream is paused (e.g., waiting for a
child run to complete), heartbeats must still fire at ≤30s cadence. On a single `Flux`, heartbeats
compete with data events for emission priority.

**HD-D.4 — Registry tenant-scope gap:** `capability_registry_spi` is designed (ADR-0021) but
silent on per-tenant authorisation — which tenant may invoke which capability. Without this, any
tenant with a `RunContext` can request any capability.

**HD-D.6 — Dispatch SPI gap:** `SyncOrchestrator` dispatches child runs via synchronous recursion
(SyncOrchestrator.java:72). When W2 `PostgresOrchestrator` swaps in, child dispatch becomes async
(Run row inserted + Executor invoked asynchronously). No SPI surface today exposes the queue/buffer
between Orchestrator and Executor — the W2 impl would embed it inline.

(HD-D.5 — "Bidding" / capability-selection-by-intent: W4+ topology concern. Documented in §Future
below; no yaml row added at W0.)

## Decision Drivers

- §4 #11(c) requires heartbeat cadence ≤30s; this is unenforceable on a mixed channel at load.
- Financial-services operators require explicit, auditable cancel signals — not a reactive-streams
  subscription cancel that may race with in-flight events.
- The `capability_registry_spi` (ADR-0021) must be tenant-scoped before W2 to prevent cross-tenant
  capability access on a shared Orchestrator.
- A `RunDispatcher` SPI at W2 enables the push-pull buffer pattern the reviewer names — callers
  enqueue intent; the dispatcher drains under backpressure.

## Considered Options

1. **Three-track physical channel separation + RunDispatcher SPI** (this decision).
2. **Single `Flux<RunEvent>` with priority-tagged events** — simpler; heartbeats marked HIGH
   priority; consumer applies priority-based drain. Still cannot support out-of-band cancel.
3. **No change to §4 #11** — defer channel design to W2 implementors. Risks W2 designs diverging
   from §4 #11 intent.

## Decision Outcome

**Chosen option:** Option 1 — three-track isolation.

### Three-track channel contract (§4 #28)

```
Track 1 — Control Channel (RunControlSink)
  Direction: Caller → Orchestrator (push only)
  Messages: Cancel(runId), PrioritySuspend(runId, reason), HeartbeatAck
  SPI: RunControlSink.push(RunControlCommand)
  Guarantee: commands delivered before the next executor iteration boundary
  W2 impl: in-process Sinks.many().unicast() with bounded buffer

Track 2 — Data Channel (Flux<RunEvent>)
  Direction: Orchestrator → Caller (reactive push, caller-driven demand)
  Messages: NodeStarted, NodeCompleted(EncodedPayload output), Suspended, Resumed, Failed, Terminal
  Backpressure: caller controls demand; Orchestrator buffers up to N events (configurable, default 64)
  Overflow policy: Non-terminal events use DROP_OLDEST under overflow with a counter metric;
  Terminal events bypass the drop policy via a reserved terminal slot and are never dropped.
  W2 impl: Sinks.many().multicast().onBackpressureBuffer(N)

Track 3 — Heartbeat Channel (Flux<Instant>)
  Direction: Orchestrator → Caller (time-driven, independent of data channel load)
  Cadence: every max(10s, min(30s, executorIterationBudget / 2))
  Messages: Instant (timestamp of heartbeat emission)
  Guarantee: emitted even when data channel is blocked; caller distinguishes liveness from progress
  W2 impl: Flux.interval(Duration) on a dedicated scheduler, independent of the data-channel Sink
```

### RunDispatcher SPI (HD-D.6 + push-pull buffer)

```java
// com.huawei.ascend.runtime.orchestration.spi — pure java.*

/**
 * Mediates between the Orchestrator (intent producer) and the Executor (intent consumer).
 *
 * <p>At W0: SyncOrchestrator calls execute() synchronously — RunDispatcher is implicit.
 * <p>At W2: PostgresOrchestrator enqueues a RunDispatchRequest to a durable queue;
 *            a pool of Executor workers drain the queue under backpressure.
 */
public interface RunDispatcher {
    /** Enqueue a Run for execution. Returns immediately; execution is async. */
    void dispatch(RunDispatchRequest request);

    /** Cancel a pending or in-flight Run dispatch. Idempotent. */
    void cancel(UUID runId);
}

public record RunDispatchRequest(
        UUID runId,
        String tenantId,
        ExecutorDefinition definition,
        Object initialPayload
) {}
```

`SyncOrchestrator` does not implement `RunDispatcher` explicitly — it delegates synchronously.
W2 `PostgresOrchestrator` implements both `Orchestrator` and wires a `RunDispatcher` internally.
The SPI is a seam for testing and alternative dispatch strategies (virtual-thread pool, Temporal).

### Capability registry tenant scope (HD-D.4)

Every `CapabilityRegistry.resolve(name)` call MUST accept a `RunContext` (or at minimum a
`tenantId`) and reject capability lookups for capabilities not authorised for that tenant.

```java
// W2 extension to capability_registry_spi
public interface CapabilityRegistry {
    Skill resolve(String name, RunContext ctx);  // tenant-scoped; throws if unauthorised
    void register(String name, Skill skill, Set<String> authorisedTenants); // or ALL
}
```

"Authorised for all tenants" is the default for VETTED Java skills registered at startup.
UNTRUSTED plugin skills may be restricted to specific tenants by operator configuration.

### §Future — Intent-driven bidding (HD-D.5, W4+)

The reviewer proposed a "Dynamic Registry" where agents bid on task assignments by capability and
intent. This is a valid W4+ multi-agent topology feature. It composes with the `CapabilityRegistry`
above by adding an intent-scoring axis to `resolve()`. No yaml row or implementation commitment at W0.

### Rebuttal of "Temporal-as-God" critique

The reviewer's core concern is that "introducing a heavyweight workflow orchestrator makes the system
a rigid approval flow that stifles emergence." At W0:

- `Orchestrator` SPI is one method: `Object run(UUID, String, ExecutorDefinition, Object)`.
- `SyncOrchestrator` is 102 lines; entirely synchronous.
- Temporal is a **W4 durability tier swap** behind the same SPI (ADR-0021 Layer 3). It is not on
  the W0–W3 critical path. The architectural commitment is the SPI interface, not the Temporal SDK.
- The `AwaitApproval` `SuspendReason` variant (ADR-0019) exists as a _contract_, not an
  implementation. There is no approval flow code in production at W0.

The SPI-tier design (ADR-0021) is explicitly the hedge against "Temporal-as-God": if Temporal
proves too rigid for a specific use case, the Orchestrator SPI is replaced with an alternative
(Postgres async at W2, Actor model at a hypothetical W5) without changing any executor or skill code.

### Consequences

**Positive:**
- Heartbeats remain independent of data backpressure — false cancellations eliminated.
- Out-of-band cancel does not require the caller to first subscribe to the data stream.
- `RunDispatcher` SPI separates the intent-enqueue step from the execute step — enables async
  dispatch, testing, and priority queueing without SPI change.
- Tenant-scoped `CapabilityRegistry` prevents cross-tenant capability access at W2.

**Negative:**
- Three channels require the W2 caller adapter (e.g., SSE endpoint) to multiplex onto a single
  HTTP response. Track 3 heartbeats can be encoded as SSE comments (`": heartbeat\n\n"`).
- `RunDispatcher` SPI is a new type; W2 wiring must integrate it with the Postgres executor pool.

### Reversal cost

Medium — the three-channel contract is a W2 API surface. Once operators build clients against it,
collapsing to a single channel would break them. Design carefully.

### Forward note — operates under ADR-0048 microservice commitment

Under ADR-0048 (Service-Layer Microservice-Architecture Commitment, §4 #46), the
three-track bus operates across multiple long-running Agent Service instances
(cross-docker, cross-service). ADR-0048 locks the **bus traffic split** for the
cross-process implementation: Track 2 (Data Channel) is **P2P** between Agent Service
instances — heavy payloads never traverse a central broker — while Track 1 (Control
Channel) operates on a **centralized event bus** (Kafka / NATS JetStream / Redpanda;
substrate choice deferred). The in-process Java-side SPI defined above remains the seam.
Collapsing data + control onto a single broker would re-introduce the whitepaper §5.2
congestion-deadlock failure mode and is forbidden by ADR-0048.

### Forward note — extended cross-service by ADR-0050 (Rhythm restored)

Under ADR-0050 (Workflow Intermediary, Mailbox Backpressure, Rhythm Track, §4 #48,
whitepaper-alignment remediation 2026-05-13), the three tracks are also enforced
**cross-service** — not only in-process. Specifically:

- **Track 1 (Control)** operates as ADR-0048 describes (centralized event bus).
- **Track 2 (Data)** operates as ADR-0048 describes (P2P).
- **Track 3 (Heartbeat / Rhythm)** is **restored as an independently protected
  cross-service track** under ADR-0050. ADR-0048 originally placed heartbeats on
  the centralized control event bus; that placement is **amended**. Heartbeats,
  `SleepDeclaration`, `WakeupPulse`, `TickEngine` ticks, lease renewal, and
  `ChronosHydration` triggers now flow on Track 3, physically isolated from Track 1
  so that control-traffic congestion cannot delay survival signals.

The in-process Java-side SPI defined in this ADR (`RunControlSink`, `Flux<RunEvent>`,
`Flux<Instant>`) remains the seam for the northbound HTTP/SSE surface (C-Side ↔ S-Side
`SubStreamFrame` per ADR-0049). The cross-process wire formats for the same three
tracks are defined in ADR-0050; substrate selection and detailed wire formats are
W2+ work.

## References

- Fifth-reviewer document: `docs/logs/reviews/spring-ai-ascend-implementation-guidelines-en.md` §4
- Response document: `docs/logs/reviews/2026-05-12-fifth-reviewer-response.en.md` (Cat-D)
- ADR-0019: SuspendReason taxonomy (Cancel → RunStatus.CANCELLED via RunControlSink)
- ADR-0021: Layered SPI taxonomy (CapabilityRegistry, W4 Temporal tier)
- ADR-0022: PayloadCodec SPI (NodeCompleted.output is EncodedPayload)
- ADR-0048: Service-Layer Microservice-Architecture Commitment (data-P2P / control-event-bus split locked; substrate choice deferred to expanded ADR-0031)
- §4 #9 (dual-mode runtime — Orchestrator SPI is the tier seam)
- §4 #11 (northbound handoff contract — amended by this ADR to reference #28)
- §4 #28 (new, this ADR)
- §4 #46 (Service-Layer Microservice-Architecture Commitment — under which the cross-process bus operates)
- Rule 15 (deferred W2 — Streamed Handoff Mode Conformance)
- `architecture-status.yaml` rows: `three_track_channel_isolation`, `run_dispatcher_spi`, `service_layer_microservice_commitment`
- W2 wave plan: `docs/archive/2026-05-13-plans-archived/engineering-plan-W0-W4.md` §4.2 (archived per ADR-0037)
