---
level: L0
view: development
status: draft
authority: "Consolidated from ARCHITECTURE.md module layout, generated modules DSL, and docs/architecture/l0 module/state drafts"
source_of_truth: true
---

# L0 Boundaries

## Purpose

This document defines L0 module admission, module responsibility boundaries,
runtime component boundaries, artifact treatment, and state ownership rules.

It intentionally combines module boundaries and state ownership because most
high-risk architecture conflicts are writer-boundary conflicts.

## Module Admission Rules

L0 distinguishes three categories:

| Category | Meaning |
|---|---|
| Core runtime architecture module | A domain module that owns runtime behavior, state, control, extension, or cross-boundary interaction. |
| Runtime component boundary | A runtime-facing module or component that participates in architecture but may not be in the primary execution path for all deployments. |
| Build/starter artifact | A valid reactor artifact that supports dependency governance, packaging, or adapter bootstrap but does not own primary runtime control. |

Generated module facts remain authoritative for reactor identity and module
metadata. L0 boundary admission decides how those artifacts participate in the
runtime architecture.

## Current Module Classification

| Module / Artifact | Classification | L0 Boundary Treatment |
|---|---|---|
| `agent-service` | Serviceization façade skeleton | Enterprise serviceization edge that drives `agent-runtime`-hosted Agent instances; registration/discovery deferred (ADR-0159). |
| `agent-runtime` | Core runtime architecture module | Run-owning runtime kernel — engine, dispatch, access (A2A), session, task-control, queue, and the bootable runtime (ADR-0159). |
| `agent-middleware` | Core runtime architecture module | Model, skill, memory, retrieval, prompt, advisor, runtime middleware, hook, and governance SPI boundary. |
| `agent-bus` | Core runtime architecture module | Bus/state hub plane, ingress, S2C, neutral engine port, A2A/federation, and three-track channel boundary. |
| `agent-client` | Runtime component boundary | SDK, edge access, local capability endpoint, cursor/callback/SSE consumption. |
| `agent-evolve` | Runtime component boundary | Evolution plane, governed export, future ML pipeline adapter. |
| `spring-ai-ascend-dependencies` | Build artifact | BoM and dependency/version governance; not a primary runtime module. |
| `spring-ai-ascend-graphmemory-starter` | Starter artifact | Adapter/starter packaging for GraphMemory; not a primary runtime control owner. |

## Responsibility Cards

### `agent-service`

Owns:

- HTTP-facing service boundary.
- Tenant, auth, idempotency, and trace entry behavior.
- Runtime control aggregate according to the accepted Run/Task vocabulary.
- Service-side reference adapters.
- Query and external realtime stream surfaces such as SSE.
- Same-service parent/child execution relationship and join behavior.

Does not own:

- Bus physical channels.
- Model, skill, memory, vector, prompt, or advisor global SPI semantics.
- Customer business facts or customer data-source permission models.
- Cross-boundary A2A private connections that bypass bus governance.

### `agent-runtime`

Owns:

- Engine adapter SPI and engine registry/envelope surfaces where accepted.
- Execution dispatch, planner, and orchestration behavior assigned to the engine
  boundary.
- Conversion of execution results into state-transition intent, tool intent,
  context request, suspend request, child-work intent, or terminal result.

Does not own:

- HTTP ingress.
- Direct writes to runtime lifecycle state outside the sanctioned owner path.
- Business tool/provider internals.
- Default remote-service boundary to `agent-service`.

### `agent-middleware`

Owns:

- Runtime middleware and hook dispatch.
- Model gateway, skill, memory, vector, retriever, embedding, prompt, and advisor
  SPI boundaries.
- Tool/model/memory policy, capacity, audit, and trace evidence shapes.

Does not own:

- Runtime lifecycle state.
- Customer business state.
- Direct provider telemetry as the only observability sink.
- Cross-boundary A2A control transport.

### `agent-bus`

Owns:

- Ingress gateway and S2C callback surfaces.
- Neutral EnginePort and orchestration SPI placement where ADR-0158 applies.
- Cross-boundary A2A, federation, data-reference envelope, control channel, and
  rhythm channel contracts.

Does not own:

- Same-service multi-agent coordination.
- Runtime lifecycle state.
- Large payload transport.
- Token-by-token external stream.
- Microservice gateway business orchestration.

### `agent-client`

Owns:

- SDK packaging and developer-facing request/response convenience.
- Client-side cursor, callback, and service stream consumption.
- Local capability endpoint for local tools, context, memory, retrieval, and
  approval UI.

Does not own:

- Server-side lifecycle state.
- Platform audit or trace mutation.
- Direct dependency on service, engine, or middleware internals.

### `agent-evolve`

Owns:

- Evolution-plane boundary.
- Governed export contract and future Java adapter shell for ML pipelines.

Does not own:

- Main request execution path.
- Runtime lifecycle mutation.
- Business data extraction outside export governance.

## Capability Aggregates Are Not Modules

The following names may appear in scenarios, capability maps, and contracts, but
they are not accepted as independent reactor modules by L0:

- Gateway.
- Workflow.
- Context Engine.
- Tool Gateway.
- Runtime Governance.
- Observability.
- Capability Placement.
- A2A / Federation.

Each aggregate must map to real modules and contracts before implementation.

## State Ownership Rules

Every state must have:

- One semantic owner.
- A bounded writer path.
- Known readers.
- Forbidden writers.
- Replay and audit expectations when relevant.

Any new writer or second lifecycle owner is an L0 architecture change.

## Core State Matrix

| State | Current / Proposed Owner | Allowed Writers | Forbidden Writers | Status |
|---|---|---|---|---|
| Run execution state | `agent-runtime` run-owning runtime (Run kernel is a design target) | Sanctioned `agent-runtime` repository path | Gateway, bus, middleware, client, provider adapters | accepted_current |
| Task execution state | `agent-runtime` task-control in draft delivery material | `agent-runtime` controlled lifecycle entry | Gateway, bus, engine adapter direct writes, middleware, client | pending_decision |
| Client invocation reference | `agent-client` local handle plus `agent-runtime` query/reference surface | `agent-runtime` creates authoritative mapping; client stores local reference | Any writer treating it as independent server lifecycle state | candidate_promote |
| Session state | `agent-runtime` session/context shell | Session owner and approved context projection paths | Memory owner, tool gateway, business agent direct platform mutation | candidate_promote |
| Memory / knowledge state | `agent-middleware` memory SPI and external memory provider boundary | Memory store writer or configured adapter | Runtime lifecycle state owner; hidden engine context builder | accepted_direction |
| Workflow checkpoint | Checkpointer SPI implementation under runtime governance | Orchestrator/checkpointer sanctioned path | Gateway, tool gateway, client | accepted_direction |
| Context package | Context capability across service and middleware | Context projector / retrieval / memory pipeline | Gateway, lifecycle state store | candidate_promote |
| Tool call record | Middleware governance plus service integration | Skill wrapper / runtime middleware / audit writer | Business agent direct external call bypassing governance | candidate_promote |
| Approval state | Service + bus callback governance | Approval callback handler / S2C transport path | Tool implementation direct write | candidate_promote |
| Trace / span / event | Telemetry vertical | TraceContext, hook chain, runtime event emitter | Provider adapter direct sink bypass | accepted_direction |
| Audit record | Platform audit writer | Append-only audit writer | Business code overwriting platform records | accepted_direction |
| Business state | External business system | Business system owner | Agent runtime platform | accepted_direction |
| Tenant / policy state | Runtime governance / identity and policy owner | Auth/policy owner | Skill implementation bypass | accepted_direction |
| Task or Run tree | `agent-service` relationship owner plus observability | Service parent/child creation or accepted federation result | Bus, engine adapter, remote service direct lifecycle mutation | pending_vocabulary |

## Run / Task Vocabulary Guard

Current accepted L0 and shipped evidence still use Run, RunContext,
RunRepository, RunStatus, and RunStateMachine. Draft delivery material proposes
that Task should become the server-side canonical execution state and Run should
be limited to client invocation or implementation compatibility.

Until this is resolved by ADR:

- Do not replace all Run terms with Task terms mechanically.
- Do not introduce a second server-side lifecycle state owner.
- Treat Task-canonical material as a candidate promotion item.
- Treat Run-based shipped material as current authority unless an accepted ADR
  changes it.

## Boundary Conflict Escalation

Open an L0 decision item when a change:

- Adds a lifecycle-state writer.
- Moves a neutral SPI between bus, engine, service, or middleware.
- Treats a capability aggregate as a new module.
- Moves business facts into platform state.
- Makes bus carry large payloads or token streams.
- Changes whether same-service multi-agent coordination is service-owned or
  bus-owned.
