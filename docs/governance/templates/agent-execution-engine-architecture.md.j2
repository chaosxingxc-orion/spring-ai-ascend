---
level: L1
view: logical
module: agent-execution-engine
status: extracted-spi-and-registry
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0072 (Engine Envelope + Strict Matching); Layer-0 principle P-M (Heterogeneous Engine Contract); Rule R-M.a (Engine Envelope Single Authority, formerly Rule 43), Rule R-M.b (Strict Engine Matching, formerly Rule 44)"
---

# agent-execution-engine — L1 architecture (SPI + registry + envelope extracted)

> Owner: AgentExecutionEngine team | Wave: W2 | Maturity: SPI + 2 reference adapters
> Created: 2026-05-17 (six-module materialization PR); extraction landed 2026-05-18 (ADR-0079)

## Status

**Engine SPI + EngineRegistry + EngineEnvelope extracted per ADR-0079 (2026-05-18); package layout updated per ADR-0088 (rc13, 2026-05-20) and ADR-0090 (rc14, 2026-05-20 — engine semantic-home alignment).**

Code now lives under this module:

- `agent-execution-engine/src/main/java/com/huawei/ascend/engine/spi/` — `ExecutorAdapter`, `GraphExecutor`, `AgentLoopExecutor`, `EngineHookSurface`, `EngineMatchingException` (engine contract surface; package root `com.huawei.ascend.engine.spi.*` to keep SPI purity per Rule 77 / OrchestrationSpiArchTest).
- The neutral orchestration/engine SPI (`RunMode`, `RunContext`, `SuspendSignal`, `Checkpointer`, `Orchestrator`, `TraceContext`, `ExecutorDefinition`, `ExecutionContext`) lives in **agent-bus** under `com.huawei.ascend.bus.spi.engine` per ADR-0158 (transport-agnostic EnginePort boundary); this module no longer owns it and instead provides the `InProcessEnginePort` realization. ADR-0088 had transiently co-located these types here before the ADR-0158 re-home.
- `agent-execution-engine/src/main/java/com/huawei/ascend/engine/runtime/` — `EngineRegistry`, `EngineEnvelope` (engine implementation home; relocated from `com.huawei.ascend.service.runtime.engine.*` in rc14 per ADR-0090 — the ADR-0079 source-compat exception is retired since rc13 redistribution already broke any consumer that bound to the old kernel-shim module).

The back-dep cycle that previously blocked extraction (engine → service → engine) was resolved by creating a transient `agent-runtime-core` module (per ADR-0079, 2026-05-18) that hosted `Run` / `RunContext` / `SuspendSignal` / `ExecutorDefinition` / S2C SPI types. Per ADR-0088 (rc13, 2026-05-20) `agent-runtime-core` was DISSOLVED and the kernel types relocated to semantic-home modules. Per ADR-0158 the neutral orchestration/engine SPI was re-homed once more to `agent-bus` under `bus.spi.engine` (transport-agnostic EnginePort boundary owned by the Bus & State Hub plane); runs/idempotency kernel stays consolidated in `agent-service`, S2C SPI in `agent-bus.bus.spi.s2c`. The build graph is now a strict DAG without the intermediate kernel-shim node.

**Reference adapters stay in `agent-service.runtime`.** `SequentialGraphExecutor` and `IterativeAgentLoopExecutor` implement the engine SPI but wire `Run` / `RunContext` from the runtime kernel and therefore live on the runtime side, not in this module. The engine contract surface (SPI + registry + envelope) is the team-facing artefact this module owns; reference implementations are intentionally where the kernel state is.

## 0.4 Layered 4+1 view map

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | heterogeneous engine contract surface |
| §2 Envelope schema | logical | `docs/contracts/engine-envelope.v1.yaml` |
| §3 Matching strictness | process | Rule R-M.b (formerly Rule 44) — `engine_type=X` MUST be executed only by adapter X |

## 1. Role

`agent-execution-engine` is the **engine contract surface**. It owns:

- `EngineEnvelope` — execution-engine request shape (envelope_version,
  engine_type, payload_class_ref, schema_ref).
- `EngineRegistry` — single authority for `resolve(envelope)` /
  `resolveByPayload(def)`; pattern-matching on `ExecutorDefinition`
  subtypes OUTSIDE this module is forbidden (Rule R-M.a, formerly Rule 43).
- `ExecutorAdapter` + `ExecutorDefinition` SPIs.
- Engine-type-specific executor interfaces (`GraphExecutor`,
  `AgentLoopExecutor`).
- Boot-time self-validation against
  `docs/contracts/engine-envelope.v1.yaml` (every `known_engines` id
  has a registered adapter; every registered adapter is `known`).

## 2. Envelope schema (authority)

`docs/contracts/engine-envelope.v1.yaml` is the single source of truth.
The `EngineEnvelope` Java record mirrors the schema (required fields
validated on construction). `known_engines` membership is enforced by
`EngineRegistry.resolve(...)` + registry boot validation; constructor-
level membership validation is deferred per Rule M-2.a.c (formerly Rule 48.c).

## 3. Strict matching (Rule R-M.b, formerly Rule 44)

A Run with `engine_type=X` executes only on the adapter registered
under `X`. Mismatch → `EngineMatchingException` → `Run.FAILED` with
reason `engine_mismatch`. **No fallback policy.** No silent
reinterpretation of payloads as another engine's configuration.

## 4. Forbidden imports

`com.huawei.ascend.engine.spi.*` imports only `java.*` + `agent-middleware`
SPI (for `HookPoint` reference). Enforced by `SpiPurityGeneralizedArchTest`
(E48, extended in T2.G to scan this module).

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises.
2. `docs/contracts/engine-envelope.v1.yaml` — envelope schema.
3. `docs/contracts/engine-hooks.v1.yaml` — hook surface this engine
   fires (consumed via `agent-middleware`).
4. ADR-0072 — module authority.
5. `docs/dfx/agent-execution-engine.yaml` — Design-for-X declarations.

---

## 5. Development View (Rule G-1.1.a — rc22 / ADR-0099)

Target directory tree (current namespace; rc22.5 migrates to `com.huawei.ascend.*` per ADR-0104):

```text
agent-execution-engine/
└── src/main/java/
    └── com/huawei/ascend/engine/
        ├── spi/                                # engine contract SPI (purity per Rule R-D)
        │   ├── ExecutorAdapter.java
        │   ├── GraphExecutor.java
        │   ├── AgentLoopExecutor.java
        │   ├── EngineHookSurface.java
        │   └── EngineMatchingException.java
        │   # NEW per ADR-0158: the neutral orchestration/engine SPI (Orchestrator, RunContext,
        │   # SuspendSignal, Checkpointer, TraceContext, RunMode, ExecutorDefinition, ExecutionContext)
        │   # re-homed to agent-bus under com.huawei.ascend.bus.spi.engine; this module realizes the
        │   # EnginePort boundary rather than owning the SPI vocabulary.
        └── runtime/                             # engine implementation home (relocated rc14 / ADR-0090) + InProcessEnginePort realization (ADR-0158)
            ├── EngineRegistry.java
            └── EngineEnvelope.java              # mirrors engine-envelope.v1.yaml
```

Mode-A (Platform-Centric per ADR-0101): `agent-execution-engine` lives on the platform.
Mode-B (Business-Centric per ADR-0101): `agent-execution-engine` joins `agent-service` on the business side for zero-latency local-direct-connect C/S loops. Same SPI, same engine envelope — location-agnostic.

## *SPI Interface Appendix* (Rule G-1.1.b — rc22 / ADR-0099)

`agent-execution-engine` produces the `engine.spi` engine-adapter SPI package (cross-validates against `module-metadata.yaml#spi_packages`, `docs/contracts/contract-catalog.md`, `docs/dfx/agent-execution-engine.yaml`). The neutral orchestration/engine SPI (`Orchestrator`, `RunContext`, `SuspendSignal`, `Checkpointer`, `TraceContext`, `RunMode`, `ExecutorDefinition`, `ExecutionContext`) lives in **agent-bus** under `com.huawei.ascend.bus.spi.engine` per ADR-0158 and is consumed here, not owned here:

| Interface FQN | SPI package | Purpose |
|---|---|---|
| `com.huawei.ascend.engine.spi.ExecutorAdapter` | `engine.spi` | Unified engine adapter contract |
| `com.huawei.ascend.engine.spi.GraphExecutor` | `engine.spi` | Workflow-graph execution |
| `com.huawei.ascend.engine.spi.AgentLoopExecutor` | `engine.spi` | ReAct-loop execution |
| `com.huawei.ascend.engine.spi.EngineHookSurface` | `engine.spi` | Engine-side hook declaration (cooperates with `agent-middleware` HookPoint) |
| `com.huawei.ascend.engine.spi.EngineMatchingException` | `engine.spi` | Throw on `engine_type` mismatch (Rule R-M.b) |

Implementation home (NOT SPI):
- `com.huawei.ascend.engine.runtime.EngineRegistry` — the only authority for `resolve(envelope)`; pattern-matching on `ExecutorDefinition` outside this class forbidden (Rule R-M.a).
- `com.huawei.ascend.engine.runtime.EngineEnvelope` (record) — mirrors `engine-envelope.v1.yaml`.

## *L2 Constraint Linkage* (Rule G-1.1.c — rc22 / ADR-0099)

Vacuously green. The W2 Telemetry Vertical (hook outcome consumption per Rule R-M.c.b) will likely produce an L2 design; when authored MUST include Boundary Contracts.

## Deployment loci (rc22 / ADR-0101)

`deployment_loci: [platform_centric, business_centric]` — engine is location-agnostic; supports both loci.

## *Cross-reference to ADR-0100 StatelessEngine SPI* (rc22)

ADR-0100 records the addition of a new SPI `com.huawei.ascend.service.engine.spi.StatelessEngine` (homed in `agent-service`, NOT here). Relationship: `StatelessEngine` may be expressed as a `ExecutorAdapter` extension or sibling — the decision lands in rc23 alongside the Java refactor. Until then `StatelessEngine` is declared in `agent-service` and consumed via the cross-module dependency direction `agent-service` → `agent-execution-engine`.
