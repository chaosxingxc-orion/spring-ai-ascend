---
level: L1
view: logical
status: active
authority: "ADR-0073 (Engine Hooks + Runtime Middleware SPI) + ADR-0120 (middleware-tier SPI surface) + ADR-0103 (naming resolution)"
---

# `agent-middleware` — Logical View

## Domain model

The module's domain is **runtime-owned cross-cutting middleware** plus the
**middleware-tier primitives** between the engine and external systems:

- **Hook SPI** — the canonical hook-point enum, the `RuntimeMiddleware`
  listener interface, the per-fire context carrier, and the sealed outcome
  type. The sealed outcome type expresses proceed, fail, and short-circuit
  dispositions for a single listener. The canonical hook-point set, its
  ordering, and the failure-propagation policy are owned by the hook
  contract [`engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml)
  (the Java enum mirrors the contract's `hooks:` list).
- **Dispatcher** — the in-module realization that fans a hook point out to
  its attached listeners. It is implementation at the package root, not
  part of the SPI surface.
- **Middleware-tier primitive SPIs** — the intermediary boundaries the
  engine uses to reach external systems (ADR-0120): a tenant-scoped model
  invocation boundary, a unified Tool/Skill boundary, memory store/reader/
  writer boundaries, vector / retrieval / embedding boundaries, a
  prompt-rendering boundary, and an advisor interceptor boundary. Their
  per-type shapes are owned by the rendered
  [`spi-appendix.md`](spi-appendix.md).

Reference Spring AI adapters for the primitive SPIs live in `agent-service`
(ADR-0125), not in this module.

## Internal partitioning

| SPI package | Concern |
|---|---|
| `middleware.spi` | Hook point + listener + context + sealed outcome (+ the dispatcher at the package root). |
| `middleware.model.spi` | Tenant-scoped LLM invocation + structured-output conversion. |
| `middleware.skill.spi` | Unified Tool/Skill + tenant-scoped registry. |
| `middleware.memory.spi` | Memory store/reader/writer + category markers + conversation variant. |
| `middleware.vector.spi` · `middleware.retrieval.spi` · `middleware.embedding.spi` | Vector storage + retrieval composition + embedding. |
| `middleware.prompt.spi` | Tenant-scoped prompt rendering with a sealed prompt source. |
| `middleware.advisor.spi` | Interceptor SPI around model invocation (call + streaming). |

Every `middleware..spi..` package is SPI-pure (`java.*` + same-package
siblings only), enforced by the generalized SPI-purity ArchUnit mechanism
(enforcer E48). The `Agent` SPI lives in `agent-service`; the `Planner`
SPI lives in `agent-execution-engine` — neither is owned here (ADR-0103).
