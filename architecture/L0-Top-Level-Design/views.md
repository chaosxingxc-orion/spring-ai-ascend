---
level: L0-TLD
TAG:
  - 4+1
  - logical-view
  - development-view
  - process-view
  - physical-view
  - scenarios-view
  - architecture-fact
status: 架构事实
dependency:
  - README.md
  - overview.md
  - boundaries.md
  - constraints.md
  - glossary.md
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
- Runtime intent, Task execution control, Task hierarchy, and lifecycle state.
- Engine-internal execution state such as workflow node state and ReAct loop
  state.
- Session, context package, memory, retrieval, and knowledge boundaries.
- Agent definition, planner, model gateway, skill, hook, and middleware surfaces.
- Platform Gateway governance, S2C callback, A2A control, federation,
  data-reference path, and rhythm signals.
- Trace, span, audit, cost attribution, and replay-safe evidence.
- Policy, posture, capacity, sandbox, and idempotency controls.

Cross-cutting verticals are defined in `constraints.md`:

- Tenant Vertical.
- Posture Vertical.
- Telemetry Vertical.
- Audit and policy vertical.
- Capacity and backpressure vertical.

## Development View

The L0 development view starts from six logical modules and then lets L1/L2
development views decide source modules, package layout, BoMs, starters,
adapters, generated facts, and build governance.

The six L0 logical modules are:

- `agent-client`.
- `agent-service`.
- `agent-execution-engine`.
- `agent-bus`.
- `agent-middleware`.
- `agent-evolve`.

Generated reactor facts, dependency BoMs, and Java starters are development or
deployment artifacts. They must be assigned under the relevant logical module or
build/deployment governance view; they are not additional L0 logical modules.

L1 architecture lives under `architecture/L1-High-Level-Design/`. L2 technical
design lives under `architecture/L2-Low-Level-Design/`.

## Process View

The top-level runtime process is:

1. A client submits an intent or request.
2. Entry processing binds tenant, actor, idempotency, posture, and trace context.
3. The service-side runtime owner creates or locates the Task execution control
   aggregate.
4. The `agent-service` Task owner dispatches execution through the Execution
   Engine SPI; the engine does not pull Tasks directly from bus or Platform
   Gateway surfaces.
5. Model, tool, memory, retrieval, prompt, and advisor work enters through
   middleware and hook surfaces.
6. If local capability, approval, or cross-instance/cross-boundary
   collaboration is required, the system uses S2C/Yield or A2A/federation
   control surfaces.
7. Long waits become `agent-service`-owned suspend/resume, not held physical
   connections or blocked threads. Cross-instance wakeup or timing signals may
   be governed by `agent-bus`.
8. External realtime output uses service streaming surfaces such as SSE by
   default; narrow event/control channels do not become token streaming.
9. Large or sensitive payloads use data-reference paths rather than narrow
   event/control channel payloads. `agent-bus` may govern the reference envelope,
   routing metadata, and permission handoff.
10. Trace, audit, metrics, and cost attribution evidence is emitted throughout.

## Physical View

The physical view is governed by deployment mode and trust boundary.

| Plane | Typical Owner | Notes |
|---|---|---|
| Edge / client | Business application or integrating developer | SDK, local capability endpoint, cursor and stream consumption. |
| Compute control | Platform or business-hosted service | `agent-service`, execution control, engine realization, middleware binding. |
| Bus and interaction governance | Platform by default | Platform Gateway governance, S2C, A2A/federation, routing, permission mediation, rhythm signals, data-reference envelopes, and narrower event/control transport units. |
| Middleware / adapters | Platform or configured provider | Model, skill, memory, retrieval, prompt, advisor and hook surfaces. |
| Sandbox execution | Platform or trusted isolation provider | Untrusted generated code and unverified third-party tools run in isolated sandbox capacity, not in the normal compute-control process. |
| Evolution | Platform | Governed export and future evolution pipeline integration. |
| External data path | Customer, object store, provider, or third-party system | Large payloads and business data stay outside narrow event/control messages; `agent-bus` may govern the reference envelope and authorized handoff. |

This refines the five-plane deployment proposal from the 2026-05-14 L0 review:
edge access, compute/control, bus/interaction governance, sandbox execution, and
evolution are separate physical concerns even when early delivery co-locates
some of them.

Trust boundaries include:

- HTTP edge to runtime.
- C-Side to S-Side.
- Parent to child execution boundary.
- Task to skill permission boundary.
- Cross-workflow, cross-instance, or cross-boundary handoff.
- Tenant-scoped storage and telemetry replay boundary.

## Scenarios View

The L0 scenarios view is limited to architecture-shaping scenarios. The current
draft candidates from `docs/architecture/l0/02-scenarios/` are:

| Scenario | Architecture Role | Status |
|---|---|---|
| BA-001 Agent Handles Business Request | End-to-end request, context, tool, model, observability, and developer evidence. | candidate_promote |
| BA-002 Human Approval Tool Call | Suspend/resume, S2C callback, approval, audit, and tool governance. | candidate_promote |
| BA-003 Multi-Agent Delegation | Parent/child execution, same-instance coordination, A2A/federation boundary, and Task tree. | candidate_promote |
| S1 Create Task | Entry, idempotency, tenant, initial lifecycle state. | candidate_promote |
| S2 Execute Agent Step | Engine dispatch and terminal or intermediate execution result. | candidate_promote |
| S3 Build Context Package | Session, memory, retrieval, and context projection. | candidate_promote |
| S4 Tool Call With Governance | Tool authorization, capacity, audit, policy, and idempotency. | candidate_promote |
| S5 Suspend / Resume | Long wait, checkpoint, callback, resume, timeout, and cancellation. | candidate_promote |
| S6 Child Task / Federation | Multi-agent collaboration, federation, join, and cross-boundary control. | candidate_promote |

These scenarios should not be treated as accepted runtime authority until
conflicts in `governance.md` are resolved and the scenarios are promoted through
the architecture or version scope system.

## View Outputs

This branch does not keep a separate machine-readable L0 workspace authority.
The L0 view model is recorded here as architecture fact.

Rendered PlantUML and image exports under `docs/architecture/l0/architecture-views/`
are historical draft delivery views. They may be useful visual references, but
they should be regenerated from current architecture facts before being promoted
back into `architecture/`.
