---
level: L2
view: physical
status: active
authority: "ADR-0158 (Engine Boundary / EnginePort) + ADR-0101 (polymorphic deployment topology)"
relates_to:
  - architecture/docs/L1/agent-bus/ARCHITECTURE.md
  - architecture/docs/L1/agent-execution-engine/ARCHITECTURE.md
  - architecture/docs/L1/agent-service/ARCHITECTURE.md
extends:
  - ADR-0158
  - ADR-0101
---

# EnginePort Boundary — Physical View

> L0 declares the constraint "one artifact set serves three deployment forms"
> (ADR-0101 / ADR-0158) without naming jars, classifiers, or wire transports. This
> view homes that L2 detail: how the single reactor is packaged into each form by
> packaging + adapter selection, and the repo-split readiness check.

## 1. One artifact set → three forms (packaging + adapter selection, no code fork)

The single reactor serves all three ADR-0101 forms with no source fork — the only
variables are *which jars are deployed together* and *which `EnginePort`
realization is selected*. The Service is ALWAYS the front door (Client↔Service is
the external boundary in every form).

| Form | Topology | Packaging | EnginePort realization | Boundary |
|---|---|---|---|---|
| **Form 2** (business microservice) | Service + Engine co-deployed as one microservice | `agent-service` Spring Boot app jar bundling `agent-execution-engine` in-process | `InProcessEnginePort` | in-JVM direct call |
| **Form 3** (SDK / embedded) | Service + Engine merged into a library embedded in the business app | `agent-bus` + `agent-execution-engine` + `agent-service` library-classifier jars of the same artifacts | `InProcessEnginePort` | in-JVM direct call |
| **Form 1** (centralized) | Service and Engine as SEPARATE microservices (engine may be 1..N) | `agent-execution-engine` packaged + run standalone | `RpcEnginePort` (engines you own) / `A2aEnginePort` (external / federated) | internal RPC / A2A |

Form 2 is the default reactor build and the **v1.0 deployable**. Form 3 is the
*library classifier* of the same jars — no separate source set. Form 1's networked
adapters are mock-functional (serialize / round-trip / dispatch, selected by
`app.engine.transport`) until a live wire transport is provisioned; they ship in
v1.1+ per the PC-004 / PC-002 cadence.

## 2. Where the neutral contract physically lives

`bus.spi.engine` is packaged inside `agent-bus`. Because `agent-bus` depends on
nothing and is on the path of both peers, the contract is owned by no single
engine and **stays in the main repo when the engine is later extracted**. This is
the physical reason the boundary is neutral: the contract jar never moves with the
engine.

## 3. Checkpoint-store placement by form

| Form | Run row + token index | Checkpoint bytes | Atomicity |
|---|---|---|---|
| 2 / 3 (in-process) | Service-owned, shared store | Engine-owned (`Checkpointer`) in the same store | Same-transaction |
| 1 (separate microservices) | Service-owned store | Engine-owned store (separate) | Transactional-outbox across stores |

There is no distributed transaction across the boundary in Form 1; cross-store
atomicity is achieved with the outbox pattern (consistent with the platform's
outbox-over-Kafka decision, ADR-0007).

## 4. Repo-split readiness (W7.3 gate)

The engine is physically extractable because it carries no reverse dependency. The
readiness check:

```text
mvn -pl agent-execution-engine -am package
```

builds and packages the engine **against `agent-bus` (the neutral contract)
WITHOUT `agent-service` on the path**. A green run proves the engine compiles and
packages with only the neutral contract behind it.

User-gated (W7.4): the literal new-repo creation + first push for
`agent-execution-engine` is a maintainer action. Once the engine consumes a
**published** `agent-bus` artifact (instead of a reactor-relative path), the
extraction is mechanical — the source already carries no `agent-service` import.

## 5. Cross-references

- Adapter homes + selection key (`app.engine.transport`): [`development.md`](development.md) §3.
- Suspend-store ownership mechanics + outbox atomicity: [`process.md`](process.md) §4.
- Deployment-form sequences: [`scenarios.md`](scenarios.md).
- Deployment topology authority: ADR-0101 (the three forms this one contract serves).
