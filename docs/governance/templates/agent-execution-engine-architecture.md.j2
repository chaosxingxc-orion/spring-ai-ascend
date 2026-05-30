---
level: L1
view: logical
module: agent-execution-engine
status: extracted-spi-and-registry
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0072 (Engine Envelope + Strict Matching) + ADR-0079 (engine SPI + registry extraction) + ADR-0088 (agent-runtime-core dissolution) + ADR-0090 (engine semantic-home alignment) + ADR-0126 (Planner SPI) + ADR-0158 (transport-agnostic EnginePort boundary); Layer-0 principle P-M (Heterogeneous Engine Contract); Rule R-M.a (Engine Envelope Single Authority) + Rule R-M.b (Strict Engine Matching)"
---

# agent-execution-engine — L1 architecture (module-root grounding)

> **Altitude discipline (L1).** This module-root file is the
> **shipped-state grounding** surface — purpose, the shipped frames
> (package clusters) and their responsibility, the public-SPI surface
> (named, with generated-fact refs), dependencies, deployment loci, and
> status. It deliberately does NOT carry code-level detail: envelope
> field shapes, the dispatch-and-fail outcome chain, method signatures
> and call chains, hook dispatch ordering, and concrete test-class
> inventories are **L2 / contract / verification** material. Those live
> in [`engine-envelope.v1.yaml`](../../../../docs/contracts/engine-envelope.v1.yaml)
> and [`engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml)
> under `docs/contracts/`, the per-view 4+1 files in this directory
> (`logical.md` / `process.md` / `physical.md` / `development.md` /
> `scenarios.md`), and the generated facts under
> `architecture/facts/generated/`. The 4+1 views are the canonical
> architectural surface; this file cross-links to them.

## 0.5 Canonical L1 4+1 View Source

The 4+1 view of this module lives as per-view files under this directory:

- **Index:** [`./README.md`](./README.md)
- **Scenarios:** [`./scenarios.md`](./scenarios.md) — canonical dispatch happy path + the type-mismatch failure path.
- **Logical:** [`./logical.md`](./logical.md) — the engine-contract domain model (envelope, registry, adapter SPI, planner SPI) + the heterogeneous-engine boundary.
- **Process:** [`./process.md`](./process.md) — strict type-matched dispatch + the matching-failure policy as an L1 narrative (the normative outcome chain lives in the contracts).
- **Physical:** [`./physical.md`](./physical.md) — in-process compute-control plane + the location-agnostic Mode-A / Mode-B loci.
- **Development:** [`./development.md`](./development.md) — package tree (rendered from source; do not hand-edit).
- **SPI Appendix:** [`./spi-appendix.md`](./spi-appendix.md) — active SPIs with generated-fact parity.

Governing rule: Rule R-C — Code-as-Contract (ADR-0059). Every constraint
below maps to at least one row in `docs/governance/enforcers.yaml`.

## 1. Purpose

`agent-execution-engine` is the **engine contract surface** — the
team-facing boundary that lets heterogeneous compute engines (workflow
graph, ReAct agent loop, and future engine kinds) be dispatched
uniformly. It owns:

- the **engine envelope** — the neutral request identity that names which
  engine a run targets and references its typed payload;
- the **engine registry** — the single authority that resolves an
  envelope to its adapter (Rule R-M.a);
- the **engine-adapter SPI** (`ExecutorAdapter` + the engine-kind
  executor interfaces `GraphExecutor` / `AgentLoopExecutor` +
  `EngineHookSurface` + `EngineMatchingException`);
- the **planner SPI** — engine-side plan generation that feeds scheduler
  admission (ADR-0126);
- the **`InProcessEnginePort` realization** of the transport-agnostic
  EnginePort boundary (ADR-0158), and the in-module reference executors.

The neutral orchestration vocabulary the engine consumes — `Orchestrator`,
`RunContext`, `SuspendSignal`, `Checkpointer`, `TraceContext`, `RunMode`,
`ExecutorDefinition`, `ExecutionContext` — is **not owned here**: it lives
in `agent-bus` under `com.huawei.ascend.bus.spi.engine` per ADR-0158 (the
Bus & State Hub owns the transport-agnostic EnginePort boundary). This
module realizes the port; it does not define the vocabulary. See
[`docs/adr/0158-engine-port-transport-agnostic-boundary.yaml`](../../../../docs/adr/0158-engine-port-transport-agnostic-boundary.yaml)
for the re-home rationale (the engine registry + envelope reached their
semantic home here per ADR-0090; the transient `agent-runtime-core`
shim was dissolved per ADR-0088).

## 2. Shipped frames (package clusters) and their responsibility

> Path convention: every Java path below is rooted at
> `agent-execution-engine/src/main/java/com/huawei/ascend/engine/...`
> **except where noted as consumed from `agent-bus`** (neutral
> orchestration/engine SPI `bus.spi.engine`, re-homed per ADR-0158).
> This section names each frame's **responsibility** and its **public
> boundary**; the runtime behaviour (envelope field shapes, dispatch
> outcome, hook ordering) is delegated to the contracts + per-view files.

| Frame (package) | Responsibility | Boundary surface · where the behaviour is defined |
|---|---|---|
| `engine/spi` | The engine-adapter plug-in surface: the unified adapter contract + the engine-kind executor interfaces + the engine-side hook declaration + the mismatch exception. | Adapter + executor SPI named in [`spi-appendix.md`](spi-appendix.md); the hook surface cooperates with the `agent-middleware` hook contract [`engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml). SPI purity per Rule R-D (enforcer E48). |
| `engine/planner/spi` | Engine-side plan generation (`Planner` + `Plan` + `PlanStep` + `PlanningRequest` + `PlanningResult` + branch/loop annotations). | Plan shape feeds scheduler admission per ADR-0126; generated-fact refs in [`spi-appendix.md`](spi-appendix.md). |
| `engine/runtime` | Engine implementation home: the registry (resolve authority) + the envelope record + the `InProcessEnginePort` realization + the outcome channel. | Resolve authority is Rule R-M.a (enforcer E84); the envelope mirrors [`engine-envelope.v1.yaml`](../../../../docs/contracts/engine-envelope.v1.yaml) (Rule R-M.a, enforcer E85); membership/self-validation is the contract's `known_engines` invariant (enforcer E64-adjacent registry validation). |
| `engine/exec` | In-module reference executors (`SequentialGraphExecutor` + `IterativeAgentLoopExecutor`) realizing the adapter SPI (ADR-0158). | Verification material (`architecture/facts/generated/tests.json`); the adapters wire run state from the bus-hosted orchestration SPI. |

**Engine consumed by `agent-service`.** `agent-service` depends on this
module for the engine SPI surface and the registry/envelope; every run
dispatch resolves through the registry (Rule R-M.a), and strict matching
(Rule R-M.b) admits a run only to the adapter for its declared engine
type — no fallback. The split-package arrangement is protected by Rule 76.

## Development View (Rule G-1.1.a — ADR-0099)

Package decomposition (the type inventory under each package is owned by
the generated code facts, `architecture/facts/generated/code-symbols.json`,
and is not restated here; the full source-rendered tree lives in
[`./development.md`](./development.md)):

```text
agent-execution-engine/
└── src/main/java/
    └── com/huawei/ascend/engine/
        ├── spi/        # engine-adapter + engine-kind executor SPIs + planner SPI (ADR-0079 / ADR-0126)
        ├── exec/       # reference executors for the shipped engine kinds
        └── runtime/    # engine envelope + registry realization (ADR-0072)
```

The neutral orchestration vocabulary this module consumes
(`RunMode` / `RunContext` / `SuspendSignal` / `Checkpointer` /
`Orchestrator` / `ExecutorDefinition`) is owned by `agent-bus`
(`bus.spi.engine`, ADR-0158) and is not re-declared here.

## 3. Heterogeneous-engine boundary (Rule R-M.a / R-M.b)

At L1 the boundary identity is: **the registry is the single authority
that maps an envelope to its adapter, and dispatch is strictly
type-matched with no fallback.** A run targeting one engine type is
admitted only to the adapter registered for that type; pattern-matching
on `ExecutorDefinition` subtypes outside the registry is forbidden (Rule
R-M.a). The matching-failure *outcome* (how a mismatch terminates a run)
is an L2 / contract concern — its normative form lives in the engine
envelope + run-event contracts and is narrated at L1 in
[`process.md`](process.md). The envelope's field-level shape and the
`known_engines` membership list are owned by
[`engine-envelope.v1.yaml`](../../../../docs/contracts/engine-envelope.v1.yaml)
(the single source of truth); the Java envelope record mirrors that
schema (Rule R-M.a, enforcer E85).

## 4. SPI purity (Rule R-D)

The `engine.spi` and `engine.planner.spi` packages import only `java.*`
plus the `agent-middleware` hook reference; the neutral orchestration
vocabulary is consumed from `agent-bus`, not redefined here. Purity is
enforced by the generalized SPI-purity ArchUnit mechanism (enforcer
E48). Cross-SPI dependency is not an allowed design escape hatch; adapter
layers translate between packages.

## 5. Dependencies

Dependency versions are managed by the parent POM and the
`spring-ai-ascend-dependencies` BoM; module files do not duplicate
version pins.

| Direction | Dependency | Reason |
|---|---|---|
| upstream (allowed) | `agent-middleware` | references the hook enum + listener interface for the engine hook surface. |
| upstream (allowed) | `agent-bus` | consumes the neutral orchestration/engine SPI (`bus.spi.engine`) and the S2C transport SPI (`bus.spi.s2c`). |
| forbidden | `agent-service`, `agent-client`, `agent-evolve` | engine is upstream of the service edge; the reverse direction is forbidden per `module-metadata.yaml#forbidden_dependencies` (Rule R-C module dependency direction). |

## 6. Deployment loci

`deployment_loci: [platform_centric, business_centric]` — the engine is
location-agnostic. In Mode-A it runs on the platform; in Mode-B it joins
`agent-service` on the business side for zero-latency local execution
loops (ADR-0101). Same SPI, same envelope — see [`physical.md`](physical.md).

## 7. Tests + out-of-scope

The test inventory (which classes assert which enforcers) is
**verification material**, owned by the verification layer and the
generated facts `architecture/facts/generated/tests.json`; it is not
enumerated here. Three-layer testing discipline per Rule D-4.

**Out of scope at L1** (delegated to later waves): the W2 Telemetry
Vertical hook-outcome consumption (Rule R-M.c.b); any L2 design that the
telemetry work produces MUST carry Boundary Contracts per Rule G-1.1.c.

## 8. Status

Engine SPI + registry + envelope are **extracted and shipped** here;
two reference adapters realize the SPI. Module `kind: domain`,
`semver_compatibility: experimental`. The neutral orchestration SPI is
consumed from `agent-bus` (ADR-0158); the planner SPI is declared
(ADR-0126). The build graph is a strict DAG without the former
kernel-shim node.

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises.
2. [`engine-envelope.v1.yaml`](../../../../docs/contracts/engine-envelope.v1.yaml) — envelope schema (field shapes + `known_engines`).
3. [`engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml) — hook surface this engine fires (consumed via `agent-middleware`).
4. [`docs/adr/0072-engine-envelope-and-strict-matching.yaml`](../../../../docs/adr/0072-engine-envelope-and-strict-matching.yaml) + [`docs/adr/0158-engine-port-transport-agnostic-boundary.yaml`](../../../../docs/adr/0158-engine-port-transport-agnostic-boundary.yaml) — module authority.
5. `docs/dfx/agent-execution-engine.yaml` — Design-for-X declarations.

## *SPI Interface Appendix* (Rule G-1.1.b)

`agent-execution-engine` publishes the `engine.spi` engine-adapter SPI
package and the `engine.planner.spi` plan-generator SPI package. The
canonical listing with generated-fact refs lives in
[`spi-appendix.md`](spi-appendix.md); the table below is the module-root
summary. The neutral orchestration vocabulary (`Orchestrator`,
`RunContext`, `SuspendSignal`, `Checkpointer`, `TraceContext`, `RunMode`,
`ExecutorDefinition`, `ExecutionContext`) is **consumed from `agent-bus`**
(`com.huawei.ascend.bus.spi.engine`, ADR-0158), not owned here.

| Interface FQN | SPI package | Generated-fact ref | Status |
|---|---|---|---|
| `…engine.spi.ExecutorAdapter` | `engine.spi` | [`…engine-spi-executoradapter`](../../../../architecture/facts/generated/code-symbols.json) | shipped |
| `…engine.spi.GraphExecutor` | `engine.spi` | [`…engine-spi-graphexecutor`](../../../../architecture/facts/generated/code-symbols.json) | shipped |
| `…engine.spi.AgentLoopExecutor` | `engine.spi` | [`…engine-spi-agentloopexecutor`](../../../../architecture/facts/generated/code-symbols.json) | shipped |
| `…engine.spi.EngineHookSurface` | `engine.spi` | [`…engine-spi-enginehooksurface`](../../../../architecture/facts/generated/code-symbols.json) | shipped |
| `…engine.spi.EngineMatchingException` | `engine.spi` | [`…engine-spi-enginematchingexception`](../../../../architecture/facts/generated/code-symbols.json) | shipped |
| `…engine.planner.spi.Planner` | `engine.planner.spi` | [`…engine-planner-spi-planner`](../../../../architecture/facts/generated/code-symbols.json) | declared |

Implementation home (NOT SPI): `…engine.runtime.EngineRegistry` (the
resolve authority, Rule R-M.a) and `…engine.runtime.EngineEnvelope` (the
record mirroring the envelope contract). Records, sealed carriers, and
enums in the SPI packages are SPI-adjacent and are not counted as SPI
interfaces; their generated-fact refs live in `code-symbols.json`.

## *L2 Constraint Linkage* (Rule G-1.1.c)

No L2 design is authored for this module today. The W2 Telemetry Vertical
(hook-outcome consumption per Rule R-M.c.b) is the likely first L2 zone;
when authored it MUST declare its inputs / outputs / DFX obligations as a
Boundary Contract. The matching-failure outcome chain noted in §3 is a
forward-declared L2 migration target (dispatch + outcome design).

## *Cross-reference to the StatelessEngine SPI*

ADR-0100 records a `StatelessEngine` SPI homed in `agent-service` (NOT
here). Its relationship to `ExecutorAdapter` — extension or sibling —
lands alongside the engine Java refactor; until then `StatelessEngine` is
declared in `agent-service` and consumed across the dependency direction
`agent-service` → `agent-execution-engine`.
