---
level: L1
view: logical
status: active
authority: "ADR-0072 (Engine Envelope + Strict Matching) + ADR-0126 (Planner SPI) + ADR-0158 (transport-agnostic EnginePort boundary)"
---

# `agent-execution-engine` — Logical View

## Domain model

The module's domain is the **heterogeneous-engine contract** — the
abstractions that let different compute engines be dispatched uniformly:

- **Engine envelope** — the neutral request identity. It names the target
  engine and references a typed payload, while keeping engine-kind details
  out of the dispatch path. The field-level shape and the `known_engines`
  membership list are owned by
  [`engine-envelope.v1.yaml`](../../../../docs/contracts/engine-envelope.v1.yaml);
  the Java record mirrors that schema.
- **Engine registry** — the single resolution authority. The registry is
  the one place that maps an envelope to an adapter, so engine selection
  cannot drift across call sites (Rule R-M.a).
- **Engine-adapter SPI** — the plug-in surface: a unified adapter
  contract plus the engine-kind executor interfaces (workflow graph,
  agent loop), an engine-side hook declaration that cooperates with the
  `agent-middleware` hook surface, and the mismatch exception type.
- **Planner SPI** — engine-side plan generation (plan, plan step,
  planning request/result, branch and loop annotations) that feeds
  scheduler admission (ADR-0126).
- **EnginePort realization** — the in-process realization of the
  transport-agnostic EnginePort boundary (ADR-0158), with two reference
  adapters: a sequential workflow-graph executor and an iterative
  agent-loop executor.

The **neutral orchestration vocabulary** the engine consumes
(`Orchestrator`, `RunContext`, `SuspendSignal`, `Checkpointer`,
`TraceContext`, `RunMode`, `ExecutorDefinition`, `ExecutionContext`) is
**not part of this module's domain** — it lives in `agent-bus`
(`com.huawei.ascend.bus.spi.engine`, ADR-0158). This module realizes the
port; it does not define the vocabulary.

## Internal partitioning

| Cluster | Role |
|---|---|
| `engine.spi` | The engine-adapter plug-in surface (SPI-pure per Rule R-D). |
| `engine.planner.spi` | The plan-generator SPI (SPI-pure). |
| `engine.runtime` | Implementation home: the registry (resolution authority), the envelope record, the EnginePort realization, the outcome channel. |
| `engine.exec` | In-module reference executors realizing the adapter SPI. |

SPI purity (no leakage of implementation types into the SPI packages) is
enforced by the generalized SPI-purity ArchUnit mechanism (enforcer E48).
The per-type generated-fact refs live in
[`spi-appendix.md`](spi-appendix.md) and `code-symbols.json`.
