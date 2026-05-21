---
level: L0
view: logical
status: draft
authority: "Derived from ARCHITECTURE.md §0.4, §2 and module-metadata.yaml"
---

# L0 / L1 Trustworthy Decomposition

## Decomposition Rule

L0 defines system invariants. L1 binds those invariants to module and interface
contracts. L2 proves that a concrete feature or implementation still satisfies
the L1 contract.

| Layer | Owns | Must Not Own |
|---|---|---|
| L0 | system boundary, deployment planes, core capability blocks, cross-module trust boundaries, system failure/security invariants | concrete class design, per-module package mechanics, feature sequences |
| L1 | module responsibility, SPI and schema surfaces, DFX, allowed dependencies, runtime role, module-level trust controls | system-wide claims not backed by L0, class-level implementation details |
| L2 | feature plan/design/build/review/release evidence, tests, class/package changes, local failure handling | changing L1 contracts without escalation, declaring L0 readiness |

## L0 Trustworthy Capability Blocks

| L0 Capability | Trust Boundary | Main Failure Boundary | Primary Evidence |
|---|---|---|---|
| Edge Access | external caller to platform ingress | unauthenticated or tenant-mismatched request | `agent-client`, ingress schema, OpenAPI, auth/tenant tests |
| Agent Service | HTTP edge plus runtime kernel | request admission, Run lifecycle, idempotency, tenant binding | `agent-service` L1, DFX, Run tests, HTTP contract tests |
| Execution Engine | engine dispatch and strict matching | wrong engine, malformed envelope, adapter mismatch | `agent-execution-engine` L1, engine-envelope schema, registry tests |
| Bus & State Hub | cross-plane C2S/S2C control traffic | mailbox saturation, callback mismatch, ordering failure | `agent-bus` L1, ingress/s2c contracts, future bus tests |
| Middleware | cross-cutting hook enforcement | skipped policy hook, middleware short-circuit ambiguity | `agent-middleware` L1, engine-hooks schema, hook tests |
| Evolution | offline improvement loop | unauthorized export, data-retention violation, feedback poisoning | `agent-evolve` L1, evolution-scope schema |
| Graph Memory Starter | optional external memory adapter | tenant bleed, sidecar misconfiguration, disabled-by-default drift | starter DFX, GraphMemoryRepository SPI |

## L1 Module Trust Responsibilities

### agent-client

Trustworthy role: edge-plane SDK and client-side correlation surface.

Responsibilities:

- Consume `IngressGateway` as the only approved edge-to-compute entry point.
- Preserve trace/cursor/cancellation context.
- Avoid direct dependency on compute-control modules.

Trustworthy gaps:

- Skeleton status means no runtime evidence yet.
- Needs W3 SDK-specific retry, timeout, credential, and trace propagation model.

### agent-service

Trustworthy role: consolidated HTTP edge and cognitive runtime kernel.

Responsibilities:

- Tenant binding and JWT cross-check.
- Idempotency admission and Run lifecycle.
- Posture fail-closed defaults.
- Reference runtime adapters and runtime entities.
- Public HTTP contract and runtime SPI consumption.

Trustworthy gaps:

- Some operational audit paths remain W2-deferred.
- Runtime implementation and HTTP edge share one deployable, so sub-package
  purity must remain a hard gate.

### agent-execution-engine

Trustworthy role: engine envelope, strict matching, and orchestration SPI host.

Responsibilities:

- Own `EngineRegistry`, `EngineEnvelope`, engine SPI, and orchestration SPI.
- Prevent fallback or reinterpretation on engine mismatch.
- Keep engine contract independent from `agent-service`.

Trustworthy gaps:

- Engine failure-to-Run-state mapping is partly cross-module and must be tested
  through integration with `agent-service`.
- Tenant scope is enforced upstream; reviewers must ensure no new engine path
  starts assuming tenant-free execution.

### agent-bus

Trustworthy role: Bus & State Hub for cross-plane traffic.

Responsibilities:

- Own C2S ingress SPI and S2C callback SPI.
- Preserve tenant/correlation/cursor fields across cross-plane control traffic.
- Host future workflow intermediary and three-track channel isolation.

Trustworthy gaps:

- Runtime bus implementation is mostly deferred.
- DFX still contains pending fields that must become release criteria before
  multi-instance or production posture.

### agent-middleware

Trustworthy role: deterministic hook and policy insertion surface.

Responsibilities:

- Own `RuntimeMiddleware`, `HookPoint`, `HookContext`, and `HookOutcome`.
- Provide a single path for model/tool/memory/lifecycle policy hooks.
- Prevent direct telemetry/audit/policy emission bypasses.

Trustworthy gaps:

- Needs strong tests that every engine and orchestrator path emits required
  hooks once W2 implementations land.

### agent-evolve

Trustworthy role: offline evolution and improvement loop.

Responsibilities:

- Consume only allowed evolution exports.
- Preserve tenant, trace, and out-of-scope filtering.
- Avoid blocking compute/control paths.

Trustworthy gaps:

- Skeleton status.
- Needs poisoning, retention, opt-in, and export-control tests before activation.

### spring-ai-ascend-graphmemory-starter

Trustworthy role: optional sidecar adapter for graph memory.

Responsibilities:

- Stay disabled by default.
- Respect `GraphMemoryRepository` tenant scope.
- Avoid creating a hidden default memory owner for customer business ontology.

Trustworthy gaps:

- Sidecar runtime controls and data residency need explicit DFX evidence before
  real adapter use.

## L0-to-L1 Validation Questions

Every L1 release must answer:

1. Does the module still map to the same L0 capability block?
2. Did the module change a system trust boundary?
3. Did the module change a deployment plane or cross-plane route?
4. Did the module change timeout, retry, cancellation, idempotency, ordering, or
   DLQ semantics?
5. Did the module expand credentials, network, data, model, tool, or context
   scope?
6. Did the module introduce an AI-specific risk that L0 must now name?
7. Did the module create or remove evidence used by L0 release claims?

## L1-to-L2 Validation Questions

Every L2 release must answer:

1. Which L1 module/interface does this feature specialize?
2. Does the implementation stay inside the L1 boundary?
3. Did it change public SPI, schema, enum, error taxonomy, or config keys?
4. Are DFX claims updated with tests or explicit deferral?
5. Are trustworthy controls implemented, tested, and auditable?
6. Can the feature be rolled back without invalidating the L1 contract?
