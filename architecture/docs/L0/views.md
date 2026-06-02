---
level: L0
view: scenarios
status: draft
authority: "Consolidated L0 4+1 view map from ARCHITECTURE.md, architecture/views/, and docs/architecture/l0/architecture-views/"
source_of_truth: true
---

# L0 4+1 Views

## Purpose

This document organizes L0 architecture facts through 4+1 views. It is the
architecture fact view system, not the version scope scenario backlog.

The scenarios view here contains representative architecture stress scenarios
that validate architectural shape. Version-scoped business scenarios and feature
use cases should live in the version scope system and reference this document
when they rely on architecture constraints.

## View Map

| View | Question Answered | Primary Inputs |
|---|---|---|
| Logical | What concepts, capabilities, and cross-cutting verticals exist? | L0 overview, capability facts, constraints, glossary. |
| Development | Which modules and source boundaries may exist? | module metadata, generated modules DSL, L1 docs. |
| Process | How does runtime control move through the system? | runtime path, suspend/resume, A2A, callback, telemetry. |
| Physical | Where do components run and what trust/data boundaries exist? | deployment variants, posture, tenant, bus channels, data paths. |
| Scenarios | Which representative flows stress the architecture? | BA scenarios and technical scenarios after promotion. |

## Logical View

The system is an agent runtime platform with the following L0 logical concepts:

- Tenant and actor identity.
- Runtime intent, Run/Task execution control, and lifecycle state.
- Session, context package, memory, retrieval, and knowledge boundaries.
- Agent definition, planner, model gateway, skill, hook, and middleware surfaces.
- S2C callback, A2A control, federation, data-reference path, and rhythm signals.
- Trace, span, audit, cost attribution, and replay-safe evidence.
- Policy, posture, capacity, sandbox, and idempotency controls.

Cross-cutting verticals are defined in `constraints.md`:

- Tenant Vertical.
- Posture Vertical.
- Telemetry Vertical.
- Audit and policy vertical.
- Capacity and backpressure vertical.

## Development View

The L0 development view separates generated module facts from core architecture
responsibility.

The current generated module set contains:

- `agent-client`.
- `agent-bus`.
- `agent-service`.
- `agent-execution-engine`.
- `agent-middleware`.
- `agent-evolve`.
- `spring-ai-ascend-dependencies`.
- `spring-ai-ascend-graphmemory-starter`.

The six domain modules form the core runtime architecture. The BoM and starter
are valid reactor/module-metadata artifacts, but their L0 responsibilities are
build/version governance and adapter/starter packaging, not primary runtime
control ownership.

L1 architecture lives under `architecture/docs/L1/`. L2 technical design lives
under `architecture/docs/L2/`.

## Process View

The top-level runtime process is:

1. A client submits an intent or request.
2. Entry processing binds tenant, actor, idempotency, posture, and trace context.
3. The service-side runtime owner creates or locates the execution control
   aggregate according to the accepted Run/Task vocabulary.
4. Execution is dispatched through engine/orchestration surfaces.
5. Model, tool, memory, retrieval, prompt, and advisor work enters through
   middleware and hook surfaces.
6. If local capability, approval, or cross-boundary collaboration is required,
   the system uses S2C/Yield or A2A/federation control surfaces.
7. Long waits become suspend/resume, not held physical connections or blocked
   threads.
8. External realtime output uses service streaming surfaces such as SSE; bus
   control does not become token streaming.
9. Large or sensitive payloads use data-reference paths rather than bus payloads.
10. Trace, audit, metrics, and cost attribution evidence is emitted throughout.

## Physical View

The physical view is governed by deployment mode and trust boundary.

| Plane | Typical Owner | Notes |
|---|---|---|
| Edge / client | Business application or integrating developer | SDK, local capability endpoint, cursor and stream consumption. |
| Compute control | Platform or business-hosted service | `agent-service`, execution control, engine realization, middleware binding. |
| Bus and state hub | Platform by default | Ingress, S2C, A2A, federation, rhythm, data-reference envelopes. |
| Middleware / adapters | Platform or configured provider | Model, skill, memory, retrieval, prompt, advisor and hook surfaces. |
| Evolution | Platform | Governed export and future evolution pipeline integration. |
| External data path | Customer, object store, provider, or third-party system | Large payloads and business data stay outside bus control messages. |

Trust boundaries include:

- HTTP edge to runtime.
- C-Side to S-Side.
- Parent to child execution boundary.
- Run/Task to skill permission boundary.
- Cross-workflow or cross-service handoff.
- Tenant-scoped storage and telemetry replay boundary.

## Scenarios View

The L0 scenarios view is limited to architecture-shaping scenarios. The current
draft candidates from `docs/architecture/l0/02-scenarios/` are:

| Scenario | Architecture Role | Status |
|---|---|---|
| BA-001 Agent Handles Business Request | End-to-end request, context, tool, model, observability, and developer evidence. | candidate_promote |
| BA-002 Human Approval Tool Call | Suspend/resume, S2C callback, approval, audit, and tool governance. | candidate_promote |
| BA-003 Multi-Agent Delegation | Parent/child execution, same-service coordination, A2A/federation boundary, task/run tree. | candidate_promote |
| S1 Create Run/Task | Entry, idempotency, tenant, initial lifecycle state. | candidate_promote |
| S2 Execute Agent Step | Engine dispatch and terminal or intermediate execution result. | candidate_promote |
| S3 Build Context Package | Session, memory, retrieval, and context projection. | candidate_promote |
| S4 Tool Call With Governance | Tool authorization, capacity, audit, policy, and idempotency. | candidate_promote |
| S5 Suspend / Resume | Long wait, checkpoint, callback, resume, timeout, and cancellation. | candidate_promote |
| S6 Child Run/Task / Federation | Multi-agent collaboration, federation, join, and cross-boundary control. | candidate_promote |

These scenarios should not be treated as accepted runtime authority until
conflicts in `governance.md` are resolved and the scenarios are promoted through
the architecture or version scope system.

## View Outputs

The machine-readable L0 system context view is currently represented by
`architecture/views/L0-system-context.dsl`.

Rendered PlantUML and image exports under `docs/architecture/l0/architecture-views/`
are historical draft delivery views. They may be useful visual references, but
they should be regenerated from accepted architecture facts before becoming
canonical.
