---
level: L0
view: logical
status: draft
authority: "Consolidated from architecture/docs/L0/ARCHITECTURE.md and docs/architecture/l0/00-overview/"
source_of_truth: true
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

The target architecture accepts authenticated tenant requests, drives LLM and
tool-calling execution with audit-grade evidence, supports long-horizon
suspend/resume behavior, and separates business-owned facts from platform-owned
runtime trajectory.

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
- State types such as Run, Task, Session, Memory, Checkpoint, Tool Call, Audit,
  Trace, and Policy can conflict unless each has a clear owner and writer rule.
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
| Explicit capability placement | Each tool, context, memory, retriever, approval UI, and A2A action declares where it executes and which data boundary it crosses. |
| Boundary-mediated A2A | Same-service multi-agent collaboration is closed by `agent-runtime` (access / A2A); cross-boundary A2A control flows through `agent-bus`. |
| Control/data/stream separation | Gateway, service SSE, bus control, and object-reference data paths are separate mechanisms. |
| Harness-first development | Core scenarios and invariants should produce mocks, stubs, assertions, and tests before runtime binding is called complete. |

## Top-Level Runtime Path

```text
External Client
  -> agent-client or external HTTP caller
  -> Gateway capability
  -> agent-runtime access layer (A2A ingress)
  -> agent-runtime run-owning runtime: Run / session / task-control kernel + reference adapters
  -> agent-runtime engine and/or neutral engine port
  -> agent-middleware for model, skill, memory, retrieval, prompt, and hook surfaces
  -> agent-bus for ingress, S2C, cross-boundary A2A, federation, control, and rhythm
  -> observability, audit, cost attribution, and verification evidence
```

Current authority still contains Run-based shipped behavior. Draft delivery
material proposes a Task-canonical interpretation. That difference is recorded
as a pending decision in `governance.md` and must not be silently resolved by
word substitution.

## Deployment Variants

| Variant | Runtime Placement | Architecture Meaning |
|---|---|---|
| Platform-centric | `agent-client` in business side; service, engine, bus, middleware in platform side. | Platform hosts context, tools, model governance, observability, and runtime controls. |
| Weak department / PaaS tenant | Runtime fully hosted by platform; business provides configuration, data-source authorization references, release acceptance, and operations input. | Platform provides hosted runtime and tenant isolation without owning business facts. |
| Protected local capability | Sensitive tools, local context, local memory/retrieval, or approval UI remain on C-Side. | Platform issues S2C/Yield instructions and receives controlled results. |
| Business-centric / federated | Client, service, and engine may run in business side; bus and middleware can remain platform services. | Local low-latency execution is allowed; cross-boundary A2A still uses platform bus contracts. |
| Hybrid enterprise individual | Local personal tools and platform public services participate in one activity. | Capability placement may vary inside one task or run. |

## Module Boundary Summary

The reactor currently has eight generated module facts: six substantive domain
modules plus the BoM and graphmemory starter. The L0 architecture distinguishes
between reactor/module-metadata identity and core runtime architecture modules.
The detailed policy is in `boundaries.md`.

| Runtime Boundary | Summary |
|---|---|
| `agent-service` | Enterprise serviceization façade skeleton: registration/discovery of `agent-runtime`-hosted Agent instances (deferred, ADR-0159). Former HTTP ingress / runtime state ownership / query surfaces relocated to `agent-runtime`. |
| `agent-runtime` | Run-owning runtime kernel: engine adapter + registry/envelope, dispatch, access (A2A ingress), session, task-control, internal queue, Run lifecycle state, planner/orchestration realization, and the bootable runtime (ADR-0159). |
| `agent-middleware` | Runtime middleware, hooks, model/skill/memory/vector/retriever/prompt/advisor SPI and governance surfaces. |
| `agent-bus` | Bus and state hub plane: ingress, S2C, neutral engine port per ADR-0158, cross-boundary A2A, federation, three-track channels. |
| `agent-client` | SDK, edge access, local capability endpoint, cursor/callback/SSE consumption. |
| `agent-evolve` | Evolution-plane boundary and future Java-side adapter for governed export into ML pipelines. |

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
| L0-RISK-001 | Run/Task canonical state vocabulary is not settled across all architecture material. |
| L0-RISK-002 | Some L1 agent-service files contain unresolved merge markers and can taint downstream boundary interpretation. |
| L0-RISK-003 | Draft capability placement and harness material is useful but not yet promoted into accepted architecture or scope planning. |
| L0-RISK-004 | Some older trustworthy/DFX material has SPI ownership drift and must be aligned before promotion. |
| L0-RISK-005 | Contract/interface drafts exist outside the accepted contract catalog and must not be treated as runtime authority. |
