---
level: L1
view: logical
module: agent-middleware
status: extracted-spi
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0073 (Engine Hooks + Runtime Middleware SPI) + ADR-0103 (naming resolution + capability-services distribution) + ADR-0120..0133 (middleware-tier SPI surface: ModelGateway / Skill / Memory / Vector / Retriever / Embedding / Prompt / Advisor); Layer-0 principle P-M (Heterogeneous Engine Contract); Rule R-M.c (Runtime-Owned Middleware via Engine Hooks)"
---

# agent-middleware — L1 architecture (module-root grounding)

> **Altitude discipline (L1).** This module-root file is the
> **shipped-state grounding** surface — purpose, the shipped frames
> (package clusters) and their responsibility, the public-SPI surface
> (named by package, with the rendered appendix as the per-type
> authority), dependencies, deployment locus, and status. It deliberately
> does NOT carry code-level detail: per-type method shapes, hook dispatch
> ordering, the outcome-to-run-state transition, and concrete test-class
> inventories are **L2 / contract / verification** material. Those live in
> the hook contract [`engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml)
> under `docs/contracts/`, the per-view 4+1 files in this directory
> (`logical.md` / `process.md` / `physical.md` / `development.md` /
> `scenarios.md`), the rendered [`spi-appendix.md`](spi-appendix.md), and
> the generated facts under `architecture/facts/generated/`. The 4+1
> views are the canonical architectural surface; this file cross-links to
> them.

## 0.5 Canonical L1 4+1 View Source

The 4+1 view of this module lives as per-view files under this directory:

- **Index:** [`./README.md`](./README.md)
- **Scenarios:** [`./scenarios.md`](./scenarios.md) — a cross-cutting policy injected at a hook point + a fail-fast short-circuit.
- **Logical:** [`./logical.md`](./logical.md) — the hook SPI + the middleware-tier primitive SPIs (model / skill / memory / vector / retrieval / embedding / prompt / advisor) + the dispatcher's place in the model.
- **Process:** [`./process.md`](./process.md) — hook dispatch as an L1 narrative (the normative ordering + failure-propagation policy lives in the hook contract).
- **Physical:** [`./physical.md`](./physical.md) — in-process compute-control plane; rides with whichever modules host the cross-cutting hooks.
- **Development:** [`./development.md`](./development.md) — package tree (rendered from source; do not hand-edit).
- **SPI Appendix:** [`./spi-appendix.md`](./spi-appendix.md) — active SPIs with generated-fact parity (rendered; the per-type authority).

Governing rule: Rule R-C — Code-as-Contract (ADR-0059). Every constraint
below maps to at least one row in `docs/governance/enforcers.yaml`.

## 1. Purpose

`agent-middleware` is the **runtime-owned middleware module**. It
implements Rule R-M.c / P-M: cross-cutting policies (model gateway, tool
authz, memory governance, tenant policy, quota, observability, sandbox
routing, checkpoint, failure handling) are expressed as `RuntimeMiddleware`
listeners attached at canonical `HookPoint` events, and it additionally
hosts the **middleware-tier SPI surface** — the intermediary primitives
between the engine and external systems (model invocation, skills, memory,
vector, retrieval, embedding, prompt rendering, advisors) per ADR-0120.
The hook contract is the team-facing boundary; the dispatcher is the
in-module realization.

## 2. Shipped frames (package clusters) and their responsibility

> Path convention: every Java path below is rooted at
> `agent-middleware/src/main/java/com/huawei/ascend/middleware/...`.
> This section names each frame's **responsibility** and its **public
> boundary**; the runtime behaviour (hook dispatch ordering, outcome
> propagation, per-type method shapes) is delegated to the hook contract
> + the rendered SPI appendix + the per-view files.

| Frame (package) | Responsibility | Boundary surface · where the behaviour is defined |
|---|---|---|
| `middleware.spi` | The hook SPI: the canonical hook-point enum + the listener interface + the per-fire context carrier + the sealed outcome type; plus the dispatcher (at the package root, not under `.spi`). | Hook list + ordering + failure-propagation owned by [`engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml) (gate Rule 57 validates yaml↔enum consistency). SPI purity per Rule R-D (enforcer E48). |
| `middleware.model.spi` | Tenant-scoped LLM invocation boundary + structured-output conversion (ADR-0121 / ADR-0129 / ADR-0130). | Per-type shapes in [`spi-appendix.md`](spi-appendix.md); reference Spring AI adapters live in `agent-service` (ADR-0125). |
| `middleware.skill.spi` | Unified Tool/Skill SPI with a skill-kind discriminator + tenant-scoped registry (ADR-0127 / ADR-0122). | Per-type shapes in [`spi-appendix.md`](spi-appendix.md). |
| `middleware.memory.spi` | Memory store/reader/writer split + memory-category markers + conversation-memory variant (ADR-0123 / ADR-0133). | Per-type shapes in [`spi-appendix.md`](spi-appendix.md). |
| `middleware.vector.spi` · `middleware.retrieval.spi` · `middleware.embedding.spi` | Vector storage + similarity search; retrieval composition over vector stores; text-embedding boundary (ADR-0124). | Per-type shapes in [`spi-appendix.md`](spi-appendix.md). |
| `middleware.prompt.spi` | Tenant-scoped prompt-rendering boundary with a sealed prompt source (ADR-0131). | Per-type shapes in [`spi-appendix.md`](spi-appendix.md); reference adapter in `agent-service`. |
| `middleware.advisor.spi` | Interceptor SPI around model invocation (call + streaming variants) with typed same-package carriers (ADR-0132). | Per-type shapes + chain composition in [`spi-appendix.md`](spi-appendix.md). |

The consumer hook implementations (token counter, PII redaction, cost
attribution, span emitter) are populated by the W2 Telemetry Vertical and
do not ship in this module today.

## 3. Hook surface + dispatch (boundary identity)

At L1 the boundary identity is: **cross-cutting policies are listeners
attached at canonical hook points, dispatched in a declared sequence, and
the chain is fail-fast** (a non-proceeding outcome stops the remaining
listeners for that hook point). The canonical hook-point set is owned by
[`engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml)
(the Java enum mirrors the contract's `hooks:` list; gate Rule 57 enforces
consistency), as is the normative dispatch ordering and the
failure-propagation policy. Run-state consumption of outcomes (when a
failing outcome transitions a run to its failed state, or a short-circuit
bypasses the engine) is **deferred to the W2 Telemetry Vertical** per Rule
R-M sub-clause .c.b (`deferred_sub_clauses` block of
`docs/governance/rules/rule-R-M.md`) — that outcome chain is an L2 /
contract concern, narrated at L1 in [`process.md`](process.md).

## 4. SPI purity (Rule R-D)

Every `middleware..spi..` package imports only `java.*` and same-package
SPI siblings; purity is enforced by the generalized SPI-purity ArchUnit
mechanism (enforcer E48). Cross-SPI dependency is not an allowed design
escape hatch; adapter layers translate between packages. Constructive
implementations under `com.huawei.ascend.middleware.*` may use any
`agent-*` dependency listed in `module-metadata.yaml#allowed_dependencies`
(today: empty — the module is SPI-pure).

## 5. Dependencies

Dependency versions are managed by the parent POM and the
`spring-ai-ascend-dependencies` BoM; module files do not duplicate
version pins.

| Direction | Dependency | Reason |
|---|---|---|
| upstream (allowed) | (none) | SPI-pure: `java.*` only, per Rule R-D sub-clause .d. |
| forbidden | `agent-service`, `agent-execution-engine`, `agent-bus`, `agent-client`, `agent-evolve` | middleware is upstream of the engine and service; the engine references middleware (the hook enum), not the reverse. Direction owned by `module-metadata.yaml#forbidden_dependencies` (Rule R-C). |

## 6. Deployment locus

`deployment_loci: [platform_centric]` — middleware always rides with
whichever modules host the cross-cutting hooks (compute-control plane).
See [`physical.md`](physical.md).

## 7. Tests + out-of-scope

The test inventory is **verification material**, owned by the verification
layer and the generated facts `architecture/facts/generated/tests.json`;
it is not enumerated here. Three-layer testing discipline per Rule D-4.

**Out of scope at L1**: the W2 outcome-consumption work (failing outcome →
run failed state); any L2 design it produces MUST carry Boundary Contracts
per Rule G-1.1.c. The Knowledge capability service is deferred to W3+.

## 8. Status

The hook SPI + dispatcher are **extracted and shipped**; the
middleware-tier primitive SPIs (model / skill / memory / vector /
retrieval / embedding / prompt / advisor) are declared per ADR-0120..0133.
Module `kind: domain`, `semver_compatibility: experimental`. Reference
Spring AI adapters for the primitive SPIs live in `agent-service`.

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises.
2. [`engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml) — canonical hook surface (list + ordering + failure propagation).
3. [`docs/adr/0073-engine-hooks-and-runtime-middleware.yaml`](../../../../docs/adr/0073-engine-hooks-and-runtime-middleware.yaml) — hook + middleware SPI authority.
4. [`docs/adr/0103-rc22-agent-middleware-naming-and-capability-services-distribution.yaml`](../../../../docs/adr/0103-rc22-agent-middleware-naming-and-capability-services-distribution.yaml) — naming resolution + capability-services distribution (READ FIRST if you arrived expecting this module to be Memory/Skills/Sandbox/Knowledge).
5. `docs/dfx/agent-middleware.yaml` — Design-for-X declarations.

## What this module is NOT (ADR-0103)

The term "Agent Middleware" carries two distinct meanings in the wider
agent-platform community:

1. **In-process runtime hooks + middleware-tier primitives** — THIS
   module's scope: cross-cutting policy injection via canonical
   `HookPoint` events, plus the model/skill/memory/vector/retrieval/
   embedding/prompt/advisor SPI primitives between engine and external
   systems.
2. **Cloudified Agentic Capability Services** — Memory Systems, Skill
   Registry, Sandbox Execution, Knowledge Index, deployed as standalone
   services. This is what some external proposals want the term to mean.

The six L1 reactor modules are the grounding architecture; there is no
seventh module for Capability Services. Per ADR-0103 the capability-service
concepts distribute across the existing six:

| Capability concept | Home in the six modules | Status |
|---|---|---|
| **Memory** | `spring-ai-ascend-graphmemory-starter` (SPI consumer) + `agent-service` (`GraphMemoryRepository` SPI per ADR-0082); tenant-scoped read/write filters bind via this module's memory hooks. | shipped (SPI) + W2 (filters) |
| **Skills** | `agent-execution-engine` (skill registry + resilience resolution) + `agent-service` (capacity governance); authz binds via this module's tool hook. | shipped (W1) |
| **Sandbox** | `agent-execution-engine` (sandbox executor SPI) + `docs/governance/sandbox-policies.yaml` SSOT per Rule R-L; runtime refusal deferred per Rule R-L.b. | policy shipped, runtime W2 |
| **Knowledge** | DEFERRED to W3+ — no active module owns Knowledge capability service today. | deferred |

The substantive insight (capability services are first-class concerns) is
accepted; the structural solution (a new module) is rejected. See ADR-0103
for full rationale.

## *SPI Interface Appendix* (Rule G-1.1.b)

`agent-middleware` produces the hook SPI package (`middleware.spi`) plus
the middleware-tier primitive SPI packages (`middleware.model.spi`,
`middleware.skill.spi`, `middleware.memory.spi`, `middleware.vector.spi`,
`middleware.retrieval.spi`, `middleware.embedding.spi`,
`middleware.prompt.spi`, `middleware.advisor.spi`). The canonical
per-type listing — FQN, kind, semantic, generated-fact ref — is the
**rendered** [`spi-appendix.md`](spi-appendix.md); it is emitted from
`module-metadata.yaml#spi_packages` + the Java source scan and is the
per-type authority. The dispatcher is implementation at the package root,
not SPI surface. Records, sealed carriers, and enums in the SPI packages
are SPI-adjacent and are not counted as SPI interfaces; their
generated-fact refs live in `code-symbols.json`. Reference Spring AI
adapters for the primitive SPIs live in `agent-service` under
`service.integration.springai` (ADR-0125).

## *L2 Constraint Linkage* (Rule G-1.1.c)

No L2 design is authored for this module today. The W2 outcome-consumption
work (when a failing outcome actually transitions a run to its failed
state) is the likely first L2 zone; if authored it MUST declare its inputs
/ outputs / DFX obligations as a Boundary Contract.

## *Cross-reference to the ON_YIELD hook* (ADR-0100)

The `ON_YIELD` hook point (in the hook contract per ADR-0100
Yield/SuspendSignal coexistence) is a cooperative-scheduling hint: the
engine fires it to request rescheduling WITHOUT a state-machine
transition. It is distinct from `SuspendSignal` (the checked exception
that remains canonical for state-machine suspension).
