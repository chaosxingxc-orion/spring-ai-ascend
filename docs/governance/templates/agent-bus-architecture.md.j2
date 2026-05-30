---
level: L1
view: logical
module: agent-bus
status: active
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0050 (Bus & State Hub plane); ADR-0074 (S2C Capability Callback Protocol); ADR-0088 (agent-runtime-core dissolution — s2c SPI relocated here); ADR-0089 (Edge-Plane Ingress Gateway Mandate); ADR-0158 (transport-agnostic EnginePort boundary — bus.spi.engine owned here); Layer-0 principles P-E + P-I; CLAUDE.md Rule R-E + Rule R-I sub-clause .b"
---

# agent-bus — L1 architecture

> Owner: AgentBus team | Maturity: active (cross-plane SPI surfaces shipped; runtime impls W2)
> Boundary surfaces: ingress (ADR-0089), s2c (ADR-0074 / ADR-0088), engine (ADR-0158); workflow primitives planned (ADR-0050).

## Status

`agent-bus` is **active** (cross-plane SPI surfaces shipped; runtime impls W2).
Three public boundary surfaces are owned here under `com.huawei.ascend.bus.spi.*`,
each named here as a *boundary identity* — their wire shapes and type inventories
live downstream (contracts + generated facts), never inline in this L1 doc:

- `bus.spi.ingress` — client-to-server entry gate (ADR-0089). Wire shape:
  `docs/contracts/ingress-envelope.v1.yaml` (status `design_only`); runtime
  binding W3+ with the agent-client SDK.
- `bus.spi.s2c` — server-to-client callback transport (ADR-0074), relocated
  here from the dissolved agent-runtime-core module per ADR-0088. Wire shape:
  `docs/contracts/s2c-callback.v1.yaml`.
- `bus.spi.engine` — the neutral, transport-agnostic Service↔Engine boundary
  (ADR-0158); frame `EF-ENGINE-PORT` (owner `agent-bus`). The boundary identity
  lives here; all detail — the EnginePort type inventory, the suspend/resume
  realization, the transport adapters, and the orchestration-SPI re-namespacing
  migration — is L2 and lives in
  [`architecture/docs/L2/engine-port-boundary/`](../../L2/engine-port-boundary/README.md).

The three-track channel isolation contract (`docs/governance/bus-channels.yaml`)
is shipped today; intra-service runtime implementations (WorkflowIntermediary,
Mailbox, AdmissionDecision, etc.) land in W2 per ADR-0050.

## 0.4 Layered 4+1 view map (ADR-0068)

Logical view populated; process + physical views join when the W2 runtime impls land.

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | Bus & State Hub plane — single cross-plane control surface |
| §2 Three-track channel isolation | logical | Rule R-E / P-E |
| §3a Ingress SPI (C2S) | logical | ADR-0089 / Rule R-I sub-clause .b |
| §3b S2C SPI (server-to-client callback) | logical | ADR-0074 + ADR-0088 |
| §3c Workflow primitives (planned) | logical | W2 — ADR-0050 |
| §3e EnginePort boundary (identity only) | logical | ADR-0158 — detail in [`L2/engine-port-boundary/`](../../L2/engine-port-boundary/README.md) |

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

## 3a. Ingress SPI (`bus.spi.ingress` — ADR-0089)

The client-to-server entry gate. The package is the boundary identity; the
single SPI interface is `IngressGateway`, with the request/response envelopes
`IngressEnvelope` / `IngressResponse`. The envelope field set, the response
status vocabulary, and all wire mechanics are defined by the contract — they are
**not** restated here.

- Wire shape (authority): `docs/contracts/ingress-envelope.v1.yaml` (status
  `design_only`). Tenant scope is validated per Rule R-C sub-clause .c.
- Negative invariant (edge plane MUST NOT bypass this SPI): ArchUnit
  `EdgeToComputeDirectLinkArchTest` (E143) + gate Rule 105
  (`edge_no_direct_compute_link`).

Promotion trigger: first agent-client SDK release (W3+ per ADR-0049).

## 3b. S2C transport SPI (`bus.spi.s2c` — ADR-0088)

The server-to-client callback transport, relocated here from the dissolved
`agent-runtime-core` per ADR-0088. The package is the boundary identity; the
SPI interface is `S2cCallbackTransport`, with the request/response envelopes
`S2cCallbackEnvelope` / `S2cCallbackResponse`. Envelope fields, the outcome
vocabulary, and the suspend-over-a-boundary mechanics are owned downstream and
not restated here.

- Wire shape (authority): `docs/contracts/s2c-callback.v1.yaml` (Rule R-C.c
  tenant scope, W3C trace-id validation).
- Suspend/resume realization (which Run suspends and how the callback resumes
  it): this is L2 process detail — the engine-boundary sink generalizes this
  `runtime_enforced` protocol as the suspend-over-a-boundary mechanism in
  [`architecture/docs/L2/engine-port-boundary/process.md`](../../L2/engine-port-boundary/process.md).
- The reference transport implementation lives in `agent-service` (it consumes
  this SPI; it is not owned here).

Status: `runtime_enforced` (ADR-0074 is shipped).

## 3c. Workflow primitives (planned, W2 — ADR-0050)

W2 will introduce under `com.huawei.ascend.bus.spi` siblings:

- `WorkflowIntermediary` — interface for sending work-state events.
- `Mailbox` — per-Run inbox for control intents.
- `AdmissionDecision` — admit / suspend / reject at the bus boundary.
- `BackpressureSignal` — observable pressure metric per channel.
- `SleepDeclaration` + `WakeupPulse` — Chronos Hydration primitives.
- `TickEngine` — the timer-driven resume loop.

Until then, suspension is carried by the in-process reference path; the
cross-process bus replaces it in W2 without changing the Run state-machine DFA
(Rule R-C.d). The in-process suspend/resume mechanics are L2 process detail
(see [`L2/engine-port-boundary/process.md`](../../L2/engine-port-boundary/process.md)).

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises (spi_packages: bus.spi.ingress, bus.spi.s2c, bus.spi.federation, bus.spi.engine).
2. `docs/contracts/ingress-envelope.v1.yaml` — C2S envelope wire shape.
3. `docs/contracts/s2c-callback.v1.yaml` — S2C envelope wire shape.
4. `docs/governance/bus-channels.yaml` — three-track schema.
5. `docs/dfx/agent-bus.yaml` — Design-for-X declarations.
6. ADR-0050, ADR-0069, ADR-0074, ADR-0088, ADR-0089 — bus + ironclad-rule + S2C + dissolution + ingress wave authority.

---

## 3d. Development View (Rule G-1.1.a — ADR-0099)

Package decomposition (the type inventory under each package is owned by the
generated code facts, `architecture/facts/generated/code-symbols.json`, and is
not restated here):

```text
agent-bus/
└── src/main/java/
    └── com/huawei/ascend/bus/
        └── spi/
            ├── ingress/      # C2S ingress SPI (ADR-0089)
            ├── s2c/          # S2C transport SPI (ADR-0088)
            ├── engine/       # neutral EnginePort boundary (ADR-0158) — detail in L2/engine-port-boundary/
            ├── federation/   # Mode-B federation forwarding (ADR-0101)
            └── (W2 workflow primitives sibling: WorkflowIntermediary, Mailbox,
                  AdmissionDecision, BackpressureSignal, SleepDeclaration,
                  WakeupPulse, TickEngine — ADR-0050)
```

Mode-A (Platform-Centric per ADR-0101): `agent-bus` lives on the platform.
Mode-B (Business-Centric per ADR-0101): `agent-bus` lives on the platform AS A FEDERATION HUB. An in-process bus shim implementing the same `IngressGateway` SPI continues to live on the business side; cross-network requests forward to the platform Federation Hub. Federation broker choice (Kafka / NATS / in-house) deferred to a separate future ADR.

## *SPI Interface Appendix* (Rule G-1.1.b — ADR-0099)

`agent-bus` owns the SPI packages below (cross-validated against
`module-metadata.yaml#spi_packages`, `docs/contracts/contract-catalog.md`,
`docs/dfx/agent-bus.yaml`). This table names the **boundary interfaces** and the
wire contract that defines each; method signatures, record field sets, and enum
vocabularies are owned by that contract and the generated code facts
(`architecture/facts/generated/code-symbols.json`) and are not restated here.

| Boundary interface FQN | SPI package | Wire contract |
|---|---|---|
| `com.huawei.ascend.bus.spi.ingress.IngressGateway` | `bus.spi.ingress` | `ingress-envelope.v1.yaml` |
| `com.huawei.ascend.bus.spi.s2c.S2cCallbackTransport` | `bus.spi.s2c` | `s2c-callback.v1.yaml` |
| `com.huawei.ascend.bus.spi.s2c.ReflectionEnvelopeRouter` | `bus.spi.s2c` | `reflection-envelope.v1.yaml` |
| `com.huawei.ascend.bus.spi.federation.FederationGateway` | `bus.spi.federation` | `federation-envelope.v1.yaml` |
| `com.huawei.ascend.bus.spi.engine.EnginePort` | `bus.spi.engine` | `engine-port.v1.yaml` |

The `bus.spi.engine` surface (EnginePort + the neutral execution model:
`ExecutionContext` / `RunContext` / `ExecuteRequest` / `AgentEvent` / `DefinitionRef`
/ `SuspendSignal` / `Checkpointer` / `Orchestrator` / `RunMode` / `TraceContext`)
is relocated here from `agent-execution-engine` per ADR-0158. Its full type
inventory and realization detail are L2 and live in
[`architecture/docs/L2/engine-port-boundary/development.md`](../../L2/engine-port-boundary/development.md).

## *L2 Constraint Linkage* (Rule G-1.1.c — ADR-0099)

The EnginePort boundary (`bus.spi.engine`, frame `EF-ENGINE-PORT`) has a
standing L2 design home at
[`architecture/docs/L2/engine-port-boundary/`](../../L2/engine-port-boundary/README.md),
which carries the development / process / physical / scenarios detail for this
boundary (ADR-0158). The W2 Workflow primitives (`§3c`) and the W3+ Federation
Hub broker integration will each warrant their own L2 design document when
authored; each MUST carry a Boundary Contracts sub-section.

## Deployment loci (ADR-0101)

`deployment_loci: [platform_centric, business_centric_hub]` — `agent-bus` always lives on the platform (acts as Federation Hub in business-centric deployments). The in-process bus shim on the business side (Mode-B) is NOT a separate module; it is a local stand-in that forwards eligible requests to the platform hub.

## *Cross-reference to ADR-0102 Online Evolution*

`agent-bus` carries the `ReflectionEnvelope` S2C contract
(`docs/contracts/reflection-envelope.v1.yaml`, status `design_only`) for online
evolution updates, delivered through `bus.spi.s2c.ReflectionEnvelopeRouter` per
the ADR-0102 timeline.
