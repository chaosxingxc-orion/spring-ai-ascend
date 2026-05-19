---
level: L1
view: logical
module: agent-execution-engine
status: extracted-spi-and-registry
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0072 (Engine Envelope + Strict Matching); Layer-0 principle P-M (Heterogeneous Engine Contract); Rule 43 (Engine Envelope Single Authority), Rule 44 (Strict Engine Matching)"
---

# agent-execution-engine — L1 architecture (SPI + registry + envelope extracted)

> Owner: AgentExecutionEngine team | Wave: W2 | Maturity: SPI + 2 reference adapters
> Created: 2026-05-17 (six-module materialization PR); extraction landed 2026-05-18 (ADR-0079)

## Status

**Engine SPI + EngineRegistry + EngineEnvelope extracted per ADR-0079 (2026-05-18).**

Code now lives under this module:

- `agent-execution-engine/src/main/java/ascend/springai/engine/spi/` — `ExecutorAdapter`, `GraphExecutor`, `AgentLoopExecutor`, `EngineHookSurface`, `EngineMatchingException` (engine contract surface; package root `ascend.springai.engine.spi.*` to keep SPI purity per Rule 77 / OrchestrationSpiArchTest).
- `agent-execution-engine/src/main/java/ascend/springai/service/runtime/engine/` — `EngineRegistry`, `EngineEnvelope` (engine implementation; package preserved at `ascend.springai.service.runtime.engine.*` for backwards source compatibility per ADR-0079 §Consequences).

The back-dep cycle that previously blocked extraction (engine → service → engine) was resolved by creating a transient `agent-execution-engine` (rc13 dissolution per ADR-0088) module that hosted (later dissolved per ADR-0088 - kernel types relocated to this module under engine.orchestration.spi) `Run` / `RunContext` / `SuspendSignal` / `ExecutorDefinition` / S2C SPI types. Both `agent-service` and `agent-execution-engine` depend on `agent-execution-engine` (rc13 dissolution per ADR-0088); the build graph is now a strict DAG.

**Reference adapters stay in `agent-service.runtime`.** `SequentialGraphExecutor` and `IterativeAgentLoopExecutor` implement the engine SPI but wire `Run` / `RunContext` from the runtime kernel and therefore live on the runtime side, not in this module. The engine contract surface (SPI + registry + envelope) is the team-facing artefact this module owns; reference implementations are intentionally where the kernel state is.

## 0.4 Layered 4+1 view map

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | heterogeneous engine contract surface |
| §2 Envelope schema | logical | `docs/contracts/engine-envelope.v1.yaml` |
| §3 Matching strictness | process | Rule 44 — `engine_type=X` MUST be executed only by adapter X |

## 1. Role

`agent-execution-engine` is the **engine contract surface**. It owns:

- `EngineEnvelope` — execution-engine request shape (envelope_version,
  engine_type, payload_class_ref, schema_ref).
- `EngineRegistry` — single authority for `resolve(envelope)` /
  `resolveByPayload(def)`; pattern-matching on `ExecutorDefinition`
  subtypes OUTSIDE this module is forbidden (Rule 43).
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
level membership validation is deferred per Rule 48.c.

## 3. Strict matching (Rule 44)

A Run with `engine_type=X` executes only on the adapter registered
under `X`. Mismatch → `EngineMatchingException` → `Run.FAILED` with
reason `engine_mismatch`. **No fallback policy.** No silent
reinterpretation of payloads as another engine's configuration.

## 4. Forbidden imports

`ascend.springai.engine.spi.*` imports only `java.*` + `agent-middleware`
SPI (for `HookPoint` reference). Enforced by `SpiPurityGeneralizedArchTest`
(E48, extended in T2.G to scan this module).

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises.
2. `docs/contracts/engine-envelope.v1.yaml` — envelope schema.
3. `docs/contracts/engine-hooks.v1.yaml` — hook surface this engine
   fires (consumed via `agent-middleware`).
4. ADR-0072 — module authority.
5. `docs/dfx/agent-execution-engine.yaml` — Design-for-X declarations.
