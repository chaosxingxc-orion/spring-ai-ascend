---
level: L0-TLD
TAG:
  - overview
  - logical-view
  - runtime-path
  - deployment-variants
  - architecture-fact
status: 架构事实
dependency:
  - README.md
  - views.md
  - boundaries.md
  - constraints.md
  - glossary.md
---

# L0 Overview

## Purpose

This document gives the top-level mental model for `spring-ai-ascend`. It
summarizes the system goal, target audiences, problem domain, runtime path,
deployment variants, module boundary shape, quality attributes, and known risks.

It does not define contract schemas, API signatures, database tables, message
topics, retry values, or implementation methods.

## System Goal

`spring-ai-ascend` is a self-hostable, governable, extensible agent runtime
platform built on Java 21 and Spring Boot. It lets Spring developers compose
agents, model gateways, skills, memory, retrieval, planners, hooks, and
middleware through SPI, configuration, and contracts while the platform retains
runtime control, tenant isolation, observability, auditability, capacity
governance, and change governance.

It is also an open-source Agent development and runtime foundation aligned with
the Kunpeng and Ascend ecosystem. The platform is intentionally compatible with
heterogeneous agent frameworks: rigid workflow-style execution and flexible
agent-loop execution can be integrated through the `agent-execution-engine`
boundary instead of a closed single-framework runtime. The official execution
engine is the openJiuwen implementation. Other agent frameworks are treated as
heterogeneous execution engines adapted to the same Execution Engine SPI.

The target architecture accepts authenticated tenant requests, drives LLM and
tool-calling execution with audit-grade evidence, supports long-horizon
suspend/resume behavior, and separates business-owned facts from platform-owned
runtime trajectory.

The intended business outcome is to help enterprise developers build digital
employee and agent-collaboration applications without making the platform the
owner of business facts, business approval rules, or long-running business
process semantics.

## Audience Boundary

| Audience | Primary Need | Timing |
|---|---|---|
| Framework-internal contributors | SPI surface, gate rules, architecture workspace, contract truth, module boundaries. | W0/W1/W2 primary |
| External Spring developers | SDK, quickstart, local and platform capabilities, agent integration, debugging evidence. | W2/W3 primary |
| Regulated-industry self-host operators | Packaged runtime, isolation, compliance evidence, audit, posture-aware operations. | W3+ |

Finance and other industry references in older material are decision-history
examples unless an accepted ADR makes a vertical the current product identity.
The current framing is vertical-agnostic and Ascend/Kunpeng hardware-synergy-led.

## Problem Domain

The platform has to solve several long-lived architecture problems:

- Agent execution crosses HTTP ingress, lifecycle state, orchestration,
  model/tool/memory interaction, bus, observability, governance, and verification.
- State types such as Task, Session, Memory, Checkpoint, Tool Call, Audit,
  Trace, Policy, and engine-internal execution state can conflict unless each
  has a clear owner and writer rule.
- Long-running work must not hold physical threads, sockets, or client
  connections while waiting for external input.
- Business facts and customer permission models must not become hidden platform
  state.
- Architecture documents must be traceable to ADRs, generated facts, verification
  edges, and implementation constraints.

## Core Architecture Principles

| Principle | L0 Meaning |
|---|---|
| Platform/business decoupling | Platform code does not carry business customizations; business extends through SPI and configuration. |
| Contract-first interaction | Cross-module behavior is described before implementation and verified by contract or scenario evidence. |
| Single state owner | Every core state has one owner and a restricted writer path. |
| Suspend instead of hold | Long waits are expressed as cursor, suspend, resume, or handoff, not retained physical resources. |
| Runtime-owned governance | Model, skill, memory, planner, callback, and policy behavior enters through runtime hooks, middleware, capacity, and audit surfaces. |
| Explicit capability placement | Each tool, context, memory, retriever, approval UI, Gateway capability, and A2A action declares where it executes and which data boundary it crosses. |
| Boundary-mediated A2A | Multi-agent collaboration inside one `agent-service` instance is closed by that instance; cross-instance or cross-boundary A2A control flows through `agent-bus`. |
| Control/data/stream separation | Platform Gateway governance, Service Task API, service realtime stream, `agent-bus` interaction governance, event/control channels, and object-reference data paths are separate mechanisms. |
| Heterogeneous framework compatibility | Official openJiuwen execution and heterogeneous framework execution both enter through the `agent-execution-engine` boundary and the Execution Engine SPI. |
| Developer lifecycle support | Development, debugging, observability, operations evidence, and verification harnesses are first-class architecture concerns. |
| Harness-first development | Core scenarios and invariants should produce mocks, stubs, assertions, and tests before runtime binding is called complete. |

## Top-Level Runtime Path

```text
External Client
  -> agent-client or external HTTP caller
  -> Platform Gateway capability or Service Task API
  -> agent-service.platform
  -> agent-service runtime state owner and reference adapters
  -> agent-execution-engine through Execution Engine SPI
  -> agent-middleware for model, skill, memory, retrieval, prompt, and hook surfaces
  -> agent-bus for Platform Gateway governance, S2C, cross-boundary A2A,
     federation, control, and rhythm signals
  -> observability, audit, cost attribution, and verification evidence
```

For V1, `Task` is the unified server-side authoritative execution lifecycle
state. It has the same semantic level as an A2A protocol task: it can be created
or bound by a client-to-server request, or by an `agent-service` instance request
to another `agent-service` instance through A2A/federation control.

An `agent-service` instance owns Task-level lifecycle and parent/child state for
work created inside that instance. Cross-instance, cross-department,
cross-deployment, or cross-trust-boundary collaboration uses `agent-bus` for
A2A/federation control. The remote Task lifecycle remains owned by the remote
`agent-service` instance; the local instance keeps the relationship reference,
join state, and observability evidence. `agent-execution-engine` owns
finer-grained execution state below the Task boundary, such as workflow node
state or ReAct loop state.

## Deployment Variants

| Variant | Runtime Placement | Architecture Meaning |
|---|---|---|
| Platform-centric | `agent-client` in business side; service, engine, bus, middleware in platform side. | Platform hosts context, tools, model governance, observability, and runtime controls. |
| Weak department / PaaS tenant | Runtime fully hosted by platform; business provides configuration, data-source authorization references, release acceptance, and operations input. | Platform provides hosted runtime and tenant isolation without owning business facts. |
| Protected local capability | Sensitive tools, local context, local memory/retrieval, or approval UI remain on C-Side. | Platform issues S2C/Yield instructions and receives controlled results. |
| Business-centric / federated | Client, service, and engine may run in business side; bus and middleware can remain platform services. | Local low-latency execution is allowed; cross-boundary A2A still uses platform bus contracts. |
| Hybrid enterprise individual | Local personal tools and platform public services participate in one activity. | Capability placement may vary inside one Task. |

## Module Boundary Summary

L0 defines six top-level logical modules. These modules are logical domains and
future L1 architecture domains, not a one-to-one list of runtime processes,
reactor artifacts, or Java packaging units. A logical module may contain several
runtime units, adapters, services, deployable components, or development
artifacts below L0.

Future official openJiuwen implementation projects may use community project
names, such as `agent-runtime-java` for `agent-service` and `agent-core-java` for
`agent-execution-engine`. Those names describe implementation projects, not new
L0 logical modules.

| L0 Logical Module | Summary |
|---|---|
| `agent-client` | Client-side integration, SDK, local capability endpoint, cursor/callback/SSE consumption, and business-side capability boundary. |
| `agent-service` | Server-side agent service boundary, Task lifecycle and hierarchy owner, Service Task API, service-side adapters, external realtime stream surfaces, and runtime query surfaces. |
| `agent-execution-engine` | Execution engine domain for workflow, ReAct, planner, engine adapter, engine registry/envelope, and finer-grained execution state below Task. |
| `agent-bus` | Broad platform interaction governance domain for Platform Gateway governance, S2C, A2A/federation, routing, permission mediation, control, rhythm signals, data-reference envelopes, and narrower event/control transport units. |
| `agent-middleware` | Agent middleware foundation domain for selectable and integrable memory, knowledge, sandbox, skill, tool, model, retrieval, prompt, advisor, and hook services. |
| `agent-evolve` | Evolution-plane domain for governed export, learning/evaluation loops, optimization, and future ML pipeline integration. |

Build artifacts, starters, dependency BoMs, adapters, and deployable units are
not promoted into L0 logical modules. They are determined in the appropriate
development or deployment view under the relevant L1/L2 domain.

Evolution-plane work consumes governed runtime evidence through export or bus
contracts and must not synchronously block the main execution path. Heavy
analysis, scoring, prompt optimization, knowledge graph construction, or
fine-tuning pipelines remain off the primary request path unless a future ADR
changes that boundary.

## Quality Attributes

| Attribute | L0 Expression |
|---|---|
| Traceability | Every architectural claim should link to ADRs, modules, constraints, generated facts, or verification. |
| Recoverability | Long-horizon work must preserve enough checkpoint or resume evidence before entering suspended states. |
| Idempotency | Irreversible effects require idempotency or equivalent duplicate protection. |
| Tenant isolation | Tenant identity is propagated and checked across runtime and storage boundaries. |
| Observability | Core scenarios emit trace, event, audit, and cost evidence. |
| Evolvability | Breaking boundary changes require ADR/change governance and downstream impact propagation. |
| Honesty | `design_only`, `draft`, `accepted`, and `shipped` are not interchangeable. |

## Known Risks

| ID | Risk |
|---|---|
| L0-RISK-001 | Some historical material still uses Run-based names; V1 L0 treats those as compatibility or implementation-history terms, not canonical lifecycle vocabulary. |
| L0-RISK-002 | Some L1 agent-service files contain unresolved merge markers and can taint downstream boundary interpretation. |
| L0-RISK-003 | Draft capability placement and harness material is useful but not yet promoted into accepted architecture or scope planning. |
| L0-RISK-004 | Some older trustworthy/DFX material has SPI ownership drift and must be aligned before promotion. |
| L0-RISK-005 | Contract/interface drafts exist outside the accepted contract catalog and must not be treated as runtime authority. |
