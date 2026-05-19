---
level: L1
view: logical
module: agent-bus
status: active
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0050 (Bus & State Hub plane); ADR-0074 (S2C Capability Callback Protocol); ADR-0088 (agent-runtime-core dissolution — s2c SPI relocated here); ADR-0089 (Edge-Plane Ingress Gateway Mandate); Layer-0 principles P-E + P-I; CLAUDE.md Rule R-E + Rule R-I sub-clause .b"
---

# agent-bus — L1 architecture (rc13 active)

> Owner: AgentBus team | Wave: W1+ | Maturity: active (cross-plane SPI surfaces shipped; runtime impls W2)
> Re-elevated from skeleton to active on 2026-05-20 (rc13): adopted s2c SPI (ADR-0088) + ingress SPI (ADR-0089)

## Status

**rc13 (2026-05-20) elevates agent-bus from skeleton to active.** Two active
cross-plane control surfaces now ship under `ascend.springai.bus.spi.*`:

- `bus.spi.ingress` — client-to-server entry gate (ADR-0089). Contract
  `docs/contracts/ingress-envelope.v1.yaml` status `design_only`; runtime
  binding W3+ with agent-client SDK.
- `bus.spi.s2c` — server-to-client callback transport (ADR-0074), relocated
  here from the dissolved agent-runtime-core module per ADR-0088.

The three-track channel isolation contract (`docs/governance/bus-channels.yaml`)
remains shipped today; intra-service runtime implementations (WorkflowIntermediary,
Mailbox, AdmissionDecision, etc.) land in W2 per ADR-0050.

## 0.4 Layered 4+1 view map (W1 — ADR-0068)

Logical view populated; process + physical views join when the W2 runtime impls land.

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | Bus & State Hub plane — single cross-plane control surface |
| §2 Three-track channel isolation | logical | Rule R-E / P-E |
| §3a Ingress SPI (C2S) | logical | rc13 — ADR-0089 / Rule R-I sub-clause .b |
| §3b S2C SPI (server-to-client callback) | logical | rc13 — ADR-0074 + ADR-0088 |
| §3c Workflow primitives (planned) | logical | W2 — ADR-0050 |

## 1. Role

`agent-bus` is the **Bus & State Hub** of the platform. It owns:

- **The cross-plane control fabric in BOTH directions** — client → server
  ingress (`bus.spi.ingress`) and server → client callback (`bus.spi.s2c`).
  Together with Rule R-I sub-clause .b (Edge↔Compute Ingress Routing) this
  guarantees that no module pair in different deployment planes communicates
  directly.
- **Three physical channels** (`control`, `data`, `rhythm`) declared in
  `docs/governance/bus-channels.yaml` and enforced by gate Rule 45
  (`bus_channels_three_track_present`).
- **Workflow state durability** (work-state events, sleep declarations,
  wakeup pulses) that survive process restarts (planned W2).
- **Tick engine** that re-hydrates suspended Runs on wake-pulse, per
  Chronos Hydration (Rule 38 / P-H) (planned W2).
- **Backpressure & admission control** at the bus boundary (planned W2).

## 2. Three-track channel isolation (Rule R-E / P-E)

| Channel | Priority | Cargo | Failure mode if congested |
|---|---|---|---|
| `control` | highest | PAUSE / KILL / CANCEL intents | NEVER congested by `data` |
| `data` | normal | run payload bodies (≤16 KiB inline cap §4 #13); ingress envelopes forward here per `ingress-envelope.v1.yaml#internal_routing` | may queue, never blocks `control` |
| `rhythm` | lowest | heartbeat / liveness pulses | drops oldest if saturated |

Authority: `docs/governance/bus-channels.yaml`. Each channel has a unique
`physical_channel:` identifier; gate Rule 45 enforces 3-channel presence
and uniqueness.

## 3a. Ingress SPI (`bus.spi.ingress` — NEW rc13, ADR-0089)

Three Java types under `ascend.springai.bus.spi.ingress`:

- `IngressGateway` — single-method SPI interface; `routeClientRequest(IngressEnvelope) → IngressResponse`.
- `IngressEnvelope` — immutable record with 6 mandatory fields (request_id, tenant_id, idempotency_key, request_type, payload, trace_id) + optional deadline + attributes. Tenant scope validated per Rule R-C sub-clause .c.
- `IngressResponse` — immutable record with 4 fields (request_id, status, cursor, rejection_reason); `IngressStatus` enum sealed: `ACCEPTED | REJECTED | DEFERRED`.

Authority + wire shape: `docs/contracts/ingress-envelope.v1.yaml` (status
`design_only` at W1). Negative invariant on the consumer side (edge plane
MUST NOT bypass this SPI): ArchUnit `EdgeToComputeDirectLinkArchTest` (E143)
+ gate Rule 105 (`edge_no_direct_compute_link`).

Promotion trigger: first agent-client SDK release (W3+ per ADR-0049).

## 3b. S2C transport SPI (`bus.spi.s2c` — relocated rc13, ADR-0088)

Three Java types under `ascend.springai.bus.spi.s2c` (relocated here from
the dissolved `agent-runtime-core.service.runtime.s2c.spi` package per
ADR-0088):

- `S2cCallbackTransport` — server-to-client capability invocation SPI (ADR-0074).
- `S2cCallbackEnvelope` — 6-required-field request envelope (Rule R-C.c tenant scope, W3C trace-id validation).
- `S2cCallbackResponse` — outcome enum (`OK | ERROR | TIMEOUT`) + correlation fields.

Reference implementation `InMemoryS2cCallbackTransport` stays in
`agent-service.service.runtime.s2c` (consumes the SPI from the new bus
package).

Status: `runtime_enforced` (ADR-0074 is shipped; SyncOrchestrator catches
`SuspendSignal.forClientCallback` once and dispatches through the registered
transport).

## 3c. Workflow primitives (planned, W2 — ADR-0050)

W2 will introduce under `ascend.springai.bus.spi` siblings:

- `WorkflowIntermediary` — interface for sending work-state events.
- `Mailbox` — per-Run inbox for control intents.
- `AdmissionDecision` — admit / suspend / reject at the bus boundary.
- `BackpressureSignal` — observable pressure metric per channel.
- `SleepDeclaration` + `WakeupPulse` — Chronos Hydration primitives.
- `TickEngine` — the timer-driven resume loop.

Until then, the runtime carries an in-process `SuspendSignal` /
`SyncOrchestrator` reference path. The cross-process bus replaces it in
W2 without changing the Run state-machine DFA (Rule R-C.d).

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises (spi_packages: bus.spi.ingress + bus.spi.s2c).
2. `docs/contracts/ingress-envelope.v1.yaml` — C2S envelope wire shape.
3. `docs/contracts/s2c-callback.v1.yaml` — S2C envelope wire shape.
4. `docs/governance/bus-channels.yaml` — three-track schema.
5. `docs/dfx/agent-bus.yaml` — Design-for-X declarations.
6. ADR-0050, ADR-0069, ADR-0074, ADR-0088, ADR-0089 — bus + ironclad-rule + S2C + dissolution + ingress wave authority.
