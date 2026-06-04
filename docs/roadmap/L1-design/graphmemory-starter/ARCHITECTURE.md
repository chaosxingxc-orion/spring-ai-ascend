---
level: L1
view: development
module: spring-ai-ascend-graphmemory-starter
status: active
freeze_id: null
covers_views: [development, logical]
spans_levels: [L1]
authority: "ADR-0147 (Structurizr Workspace Authority) + ADR-0150 (Wave 8 docs consolidation)"
---

# spring-ai-ascend-graphmemory-starter — L1

**Plane:** Bus & State Hub.
**Module-metadata:** `spring-ai-ascend-graphmemory-starter/module-metadata.yaml` (`architecture_doc: null` — content is here).
**Workspace element:** `architecture/workspace.dsl` declares `graphMemoryStarter` container with `saa.id MOD-GRAPHMEMORY-STARTER`.

## What this module is

`spring-ai-ascend-graphmemory-starter` is a Spring Boot auto-configuration starter that wires a default `GraphMemoryRepository` implementation when a consumer imports it. The starter is intentionally minimal — the architectural surface is owned by `agent-service` (`com.huawei.ascend.service.runtime.memory.spi.GraphMemoryRepository`). The starter contributes the `@Conditional` registration and the in-memory reference implementation suitable for `dev` posture.

## Boundary contracts

- **SPI surface owned by agent-service:** `com.huawei.ascend.service.runtime.memory.spi.GraphMemoryRepository` (and supporting carrier types in the same package). The starter implements this SPI; the contract surface itself is `agent-service`'s responsibility.
- **Auto-configuration class:** `com.huawei.ascend.starter.graphmemory.GraphMemoryAutoConfiguration` (or equivalent). Registers a bean when no consumer bean of the same type exists.
- **No new SPI packages declared by the starter** — `module-metadata.yaml#spi_packages` is empty for this module. The starter is a downstream consumer of `agent-service`'s memory.spi.

## Posture behaviour

- `dev` — in-memory implementation is registered by default; consumers can override with `@Bean`.
- `research`/`prod` — consumers MUST provide their own `GraphMemoryRepository` bean (e.g. backed by Neo4j or pgvector); the auto-configuration steps aside.

## Cross-references

- Authority ADR: [ADR-0081](../../../../docs/adr/0081-resilience-contract-dual-surface-reconciliation.yaml) (ResilienceContract dual-surface reconciliation; covers the GraphMemoryRepository alignment).
- Function point: `FP-GRAPH-MEMORY-STORE` in [`../features/function-points.dsl`](../../../features/function-points.dsl) (workspace closure).
- Module-metadata: [`spring-ai-ascend-graphmemory-starter/module-metadata.yaml`](../../../../spring-ai-ascend-graphmemory-starter/module-metadata.yaml).
- Workspace entry: [`architecture/workspace.dsl`](../../../workspace.dsl) — `graphMemoryStarter` container declaration.
