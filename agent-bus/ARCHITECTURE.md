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
cross-plane control surfaces now ship under `com.huawei.ascend.bus.spi.*`:

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
  Chronos Hydration (Rule R-H (formerly Rule 38) / P-H) (planned W2).
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

Three Java types under `com.huawei.ascend.bus.spi.ingress`:

- `IngressGateway` — single-method SPI interface; `routeClientRequest(IngressEnvelope) → IngressResponse`.
- `IngressEnvelope` — immutable record with 6 mandatory fields (request_id, tenant_id, idempotency_key, request_type, payload, trace_id) + optional deadline + attributes. Tenant scope validated per Rule R-C sub-clause .c.
- `IngressResponse` — immutable record with 4 fields (request_id, status, cursor, rejection_reason); `IngressStatus` enum sealed: `ACCEPTED | REJECTED | DEFERRED`.

Authority + wire shape: `docs/contracts/ingress-envelope.v1.yaml` (status
`design_only` at W1). Negative invariant on the consumer side (edge plane
MUST NOT bypass this SPI): ArchUnit `EdgeToComputeDirectLinkArchTest` (E143)
+ gate Rule 105 (`edge_no_direct_compute_link`).

Promotion trigger: first agent-client SDK release (W3+ per ADR-0049).

## 3b. S2C transport SPI (`bus.spi.s2c` — relocated rc13, ADR-0088)

Three Java types under `com.huawei.ascend.bus.spi.s2c` (relocated here from
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

W2 will introduce under `com.huawei.ascend.bus.spi` siblings:

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

---

## 3d. Development View (Rule G-1.1.a — rc22 / ADR-0099)

Target directory tree (current namespace; rc22.5 migrates to `com.huawei.ascend.*` per ADR-0104):

```text
agent-bus/
└── src/main/java/
    └── com/huawei/ascend/bus/
        ├── spi/
        │   ├── ingress/                # C2S ingress SPI (rc13 / ADR-0089)
        │   │   ├── IngressGateway.java
        │   │   ├── IngressEnvelope.java
        │   │   └── IngressResponse.java
        │   ├── s2c/                    # S2C transport SPI (rc13 / ADR-0088)
        │   │   ├── S2cCallbackTransport.java
        │   │   ├── S2cCallbackEnvelope.java
        │   │   └── S2cCallbackResponse.java
        │   └── (W2 sibling: WorkflowIntermediary, Mailbox, AdmissionDecision, BackpressureSignal, SleepDeclaration, WakeupPulse, TickEngine)
        └── (W2+ implementation classes — broker bindings; today no impl ships)
```

Mode-A (Platform-Centric per ADR-0101): `agent-bus` lives on the platform.
Mode-B (Business-Centric per ADR-0101): `agent-bus` lives on the platform AS A FEDERATION HUB. An in-process bus shim implementing the same `IngressGateway` SPI continues to live on the business side; cross-network requests forward to the platform Federation Hub. Federation broker choice (Kafka / NATS / in-house) deferred to a separate future ADR.

## *SPI Interface Appendix* (Rule G-1.1.b — rc22 / ADR-0099)

`agent-bus` produces 3 SPI packages (cross-validates against `module-metadata.yaml#spi_packages`, `docs/contracts/contract-catalog.md`, `docs/dfx/agent-bus.yaml`):

| Interface / Record FQN | SPI package | Purpose | Wire contract |
|---|---|---|---|
| `com.huawei.ascend.bus.spi.ingress.IngressGateway` | `bus.spi.ingress` | Single-method C2S entry: `routeClientRequest(IngressEnvelope) → IngressResponse` | `ingress-envelope.v1.yaml` |
| `com.huawei.ascend.bus.spi.ingress.IngressEnvelope` | `bus.spi.ingress` | Immutable record: 6 mandatory + optional deadline + attributes | same |
| `com.huawei.ascend.bus.spi.ingress.IngressResponse` | `bus.spi.ingress` | Immutable record: 4 fields + `IngressStatus` sealed enum (ACCEPTED \| REJECTED \| DEFERRED) | same |
| `com.huawei.ascend.bus.spi.s2c.S2cCallbackTransport` | `bus.spi.s2c` | Server-to-client capability invocation SPI (ADR-0074) | `s2c-callback.v1.yaml` |
| `com.huawei.ascend.bus.spi.s2c.S2cCallbackEnvelope` | `bus.spi.s2c` | 6-required-field request envelope | same |
| `com.huawei.ascend.bus.spi.s2c.S2cCallbackResponse` | `bus.spi.s2c` | Outcome enum (OK \| ERROR \| TIMEOUT) + correlation fields | same |
| `com.huawei.ascend.bus.spi.s2c.ReflectionEnvelopeRouter` | `bus.spi.s2c` | rc26: S2C delivery of ReflectionEnvelope for online evolution (rc27 moved under .spi per Rule R-D.d) | `reflection-envelope.v1.yaml` |
| `com.huawei.ascend.bus.spi.federation.FederationGateway` | `bus.spi.federation` | rc26: Mode B Business-Centric federation forwarding (rc27 moved under .spi) | `federation-envelope.v1.yaml` |

## *L2 Constraint Linkage* (Rule G-1.1.c — rc22 / ADR-0099)

Vacuously green at rc22. The W2 Workflow primitives (`§3c`) and the W3+ Federation Hub broker integration will likely each warrant an L2 design document; each MUST carry a Boundary Contracts sub-section under §3d when authored.

## Deployment loci (rc22 / ADR-0101)

`deployment_loci: [platform_centric, business_centric_hub]` — `agent-bus` always lives on the platform (acts as Federation Hub in business-centric deployments). The in-process bus shim on the business side (Mode-B) is NOT a separate module; it is a local stand-in that forwards eligible requests to the platform hub.

## *Cross-reference to ADR-0102 Online Evolution* (rc22)

`agent-bus` carries the `ReflectionEnvelope` S2C contract (`docs/contracts/reflection-envelope.v1.yaml`, status `design_only` at rc22) for online evolution updates. Runtime impl in rc26 (`ReflectionEnvelopeRouter` per ADR-0102 timeline).
