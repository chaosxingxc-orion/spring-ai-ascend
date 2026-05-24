---
level: L0
view: physical
status: draft
authority: "Derived from ARCHITECTURE.md, module-metadata.yaml, DFX YAML, and the system-architecture deployment-readiness review"
---

# Deployment Plane Contract

## Purpose

This document turns the L0 logical planes into production deployment
obligations. A plane is not only a diagram element. For trustworthy
architecture review, each plane must declare its deployable boundary, failure
boundary, state ownership, recovery expectation, and release evidence.

## Plane Contract Rules

1. A module may belong to one primary deployment plane only.
2. A cross-plane route must have an owning contract or SPI.
3. Any plane that owns durable state must define recovery and replay behavior.
4. Any customer-facing plane must define SLO, RTO, RPO, rollback, and
   emergency-disable expectations.
5. Design-only or deferred runtime semantics must not be used as shipped HA
   evidence.

## Deployment Plane Matrix

| Plane | Current Modules | Primary Role | State Ownership | Failure Boundary | Current Evidence | Gap |
|---|---|---|---|---|---|---|
| `edge` | `agent-client` | Client-side access, cursor/correlation propagation, future SDK | No authoritative server state | Client retry and credential misuse must not affect server correctness | `agent-client/module-metadata.yaml`, `docs/dfx/agent-client.yaml` | SDK runtime contract is still skeleton/pending |
| `compute_control` | `agent-service`, `agent-execution-engine`, `agent-middleware` | HTTP admission, Run lifecycle, orchestration SPI, engine selection, hook policy | Run/idempotency/checkpoint state, depending on wave | Runtime worker failure, request admission failure, engine mismatch, hook bypass | L0 architecture, module L1 docs, OpenAPI, engine contracts, DFX | `agent-service` is still broad as one production role |
| `bus_state` | `agent-bus`, `spring-ai-ascend-graphmemory-starter` | C2S ingress, S2C callback, future three-track bus/state hub, optional memory adapter | Bus cursor/mailbox/callback state; optional graph memory adapter state | Central control-route saturation, callback mismatch, mailbox backlog, memory tenant bleed | Ingress/S2C contracts, bus DFX, GraphMemory SPI | Runtime bus semantics and HA evidence are mostly deferred |
| `sandbox` | future sandbox executor | Bounded tool/action execution | Sandbox-local temp state only unless explicitly delegated | Escape, credential leakage, filesystem/network abuse | ADR-0018, DFX references | Runtime sandbox enforcement is deferred |
| `evolution` | `agent-evolve` | Offline improvement loop and evolution exports | Offline datasets, feedback, candidate improvements | Poisoned feedback, unauthorized export, retention drift | `agent-evolve` L1/DFX | Skeleton; needs opt-in and export-control evidence |
| `none` | `spring-ai-ascend-dependencies` | BOM and dependency alignment | None | Supply-chain drift | parent POM, dependency module | Automated SCA gate deferred |

## Minimum Contract Per Plane

Each plane should maintain the following fields in either this document, a
future YAML contract, or promoted L0/L1 authority:

| Field | Required Meaning |
|---|---|
| `deployable_unit` | What can be deployed, scaled, restarted, and rolled back independently |
| `state_owner` | Which state is authoritative in this plane |
| `ingress` | Allowed inbound routes and callers |
| `egress` | Allowed outbound routes and callees |
| `secret_scope` | Which credentials can be present in this plane |
| `data_classification` | Data categories the plane may process or persist |
| `scaling_model` | Horizontal/vertical scaling model and tenant fairness rule |
| `failure_domain` | What can fail without taking down other planes |
| `degradation_mode` | How the system behaves when this plane is impaired |
| `recovery_model` | Replay, reconciliation, sweep, DLQ, checkpoint, or fail-closed behavior |
| `rollback_model` | How a bad release or config can be reverted |
| `slo_class` | Customer-facing, operator-critical, internal async, or offline |
| `evidence` | Tests, gates, metrics, runbooks, drills, and release notes |

## Suggested Continuity Classes

| Class | Use | Example Capabilities | Required Evidence |
|---|---|---|---|
| `C0_customer_critical` | Customer-visible critical path | Run create/query/cancel, admission rejection correctness | SLO, RTO/RPO, idempotency, HA test, rollback, audit |
| `C1_operator_critical` | Operator or recovery path | audit search, sweeper, incident replay, emergency-disable | runbook, alert, drill, tamper-evident audit |
| `C2_internal_async` | Internal async or deferred path | bus delivery, callback retry, evolution export queue | backlog metric, DLQ, retry budget, replay |
| `C3_offline` | Offline analysis and improvement | evolution training, benchmark generation | opt-in, retention, poisoning checks, export control |

## Plane-Specific Recommendations

### Edge

- Treat `agent-client` as untrusted even if it is platform-owned.
- Require trace propagation, timeout, retry, cancellation, and idempotency
  behavior before SDK release.
- Do not allow edge code to bypass `IngressGateway`.

### Compute Control

- Split `agent-service` into explicit runtime roles in documentation even if
  it remains one Maven module:
  - API admission;
  - Run lifecycle owner;
  - runtime worker/reference adapter;
  - durable state adapter.
- Require every role to state failure isolation and rollback behavior.

### Bus State

- Make the three-track bus contract testable:
  - control;
  - data;
  - rhythm/heartbeat.
- Define ordering, retry, DLQ, terminal-event preservation, saturation, and
  recovery behavior per track.

### Sandbox

- Production posture must fail closed when untrusted tools require a sandbox
  but only a NoOp sandbox exists.
- Sandbox events must be auditable.

### Evolution

- Keep evolution opt-in and offline by default.
- Require poisoning, retention, redaction, and export-control checks before
  runtime activation.

## L0 Review Questions

1. Did a module move to a different plane?
2. Did a plane gain new durable state?
3. Did a plane gain new credential, network, model, tool, or data scope?
4. Did a cross-plane route bypass its owning SPI or contract?
5. Can this plane fail without violating the declared continuity class?
6. Are degraded behavior and recovery evidence documented?

