---
level: L0-TLD
TAG:
  - boundaries
  - development-view
  - modules
  - state-ownership
  - architecture-fact
status: 架构事实
dependency:
  - README.md
  - overview.md
  - views.md
  - constraints.md
  - glossary.md
---

# L0 Boundaries

## Purpose

This document defines L0 logical module admission, logical module responsibility
boundaries, downstream artifact treatment, and state ownership rules.

It intentionally combines module boundaries and state ownership because most
high-risk architecture conflicts are writer-boundary conflicts.

## Module Admission Rules

L0 admits only top-level logical modules. These modules correspond to future L1
architecture domains and must not be inferred from reactor artifacts, Java
packaging, starter mechanics, or individual runtime process boundaries.

L0 distinguishes three concerns:

| Concern | Meaning |
|---|---|
| L0 logical module | A top-level domain boundary that groups responsibilities by architecture meaning and becomes a future L1 domain. |
| Runtime unit | A service, adapter, gateway, registry, bus, sandbox, memory service, skill service, or other deployable/operational unit inside a logical module. |
| Development/deployment artifact | A BoM, starter, adapter scaffold, generated module fact, or packaging unit owned by the appropriate L1/L2 development or deployment view. |

Code facts and module metadata describe reactor identity. They do not decide L0
logical module admission.

## Logical Module and Implementation Project Naming

L0 uses architecture-domain names for logical modules. These names are stable
within this L0 package even when future implementation projects, repositories,
or community artifacts use different names.

| L0 Logical Module | Future Official Implementation Project |
|---|---|
| `agent-service` | openJiuwen `agent-runtime-java` |
| `agent-execution-engine` | openJiuwen `agent-core-java` |

The current repository may keep the six L0 module names as top-level directories
for temporary code, architecture work, or consolidation. Future repository
splits under the openJiuwen community can implement these L0 modules with
community project names, but those implementation names do not replace the L0
logical module names.

Historical proposal names such as `Agent Runtime`, `Agent Core`,
`agent-runtime-java`, or `agent-core-java` should be read as implementation or
community-project naming. They must not be used to infer new L0 domains or move
responsibilities without an ADR-backed architecture change.

## L0 Logical Module Classification

| Logical Module | L0 Boundary Treatment |
|---|---|
| `agent-client` | Client-side integration and local capability boundary. |
| `agent-service` | Server-side agent service boundary and Task lifecycle owner. |
| `agent-execution-engine` | Execution engine domain and finer-grained execution state owner below Task. |
| `agent-bus` | Broad platform interaction governance domain, including Platform Gateway governance, S2C, A2A/federation, routing, permission mediation, rhythm signals, data-reference envelopes, and narrower event/control transport units. |
| `agent-middleware` | Agent middleware foundation domain for selectable and integrable intelligent middleware services. |
| `agent-evolve` | Evolution-plane domain for governed learning, evaluation, optimization, and export loops. |

`spring-ai-ascend-dependencies`, `spring-ai-ascend-graphmemory-starter`, and
similar artifacts are not L0 logical modules. Dependency BoMs belong in the
development view of the owning domain or build governance. Starters belong in
the deployment/integration view of the domain they package or adapt.

## Responsibility Cards

### `agent-service`

Owns:

- HTTP-facing service boundary.
- Tenant, auth, idempotency, and trace entry behavior.
- Task execution lifecycle and Task hierarchy state.
- Service Task API surfaces such as create task, query task, stream task, cancel
  task, and Task lifecycle operations.
- Service-side reference adapters.
- Service-to-engine orchestration entry, Task-to-execution invocation, and
  extensible anti-corruption assembly for engine adapters.
- Query and external realtime stream surfaces such as SSE.
- Parent/child execution relationship and join behavior inside the same
  `agent-service` instance.

Does not own:

- Bus physical channels.
- Platform-level ingress governance surfaces assigned to `agent-bus` L1/L2,
  such as cross-service routing or A2A/S2C ingress mediation.
- Execution engine implementation, engine-internal execution state, or
  heterogeneous framework semantics.
- Model, skill, memory, vector, prompt, or advisor global SPI semantics.
- Customer business facts or customer data-source permission models.
- Cross-boundary A2A private connections that bypass bus governance.

### `agent-execution-engine`

Owns:

- Execution Engine SPI: the service-to-engine invocation boundary used by
  `agent-service` to execute Tasks.
- The official openJiuwen execution engine implementation.
- Heterogeneous execution engine adapter domain, including adapter contracts,
  registry/envelope surfaces, and translation to the Execution Engine SPI.
- Execution dispatch, planner, and orchestration algorithms assigned to the
  execution engine boundary.
- Finer-grained execution state below the Task boundary, such as workflow node
  execution state and ReAct loop state.
- Conversion of execution results into state-transition intent, tool intent,
  context request, suspend request, child-work intent, or terminal result.

Does not own:

- HTTP ingress.
- Direct writes to runtime lifecycle state outside the sanctioned owner path.
- Business tool/provider internals.
- Default remote-service boundary to `agent-service`.

## Service-To-Engine Dependency Rule

`agent-service` depends on `agent-execution-engine` through the Execution Engine
SPI. `agent-service` owns Task-level lifecycle and state management.
`agent-execution-engine` owns the execution algorithm and engine-internal
execution state below the Task boundary; it does not own Task lifecycle state.

The Execution Engine SPI belongs to `agent-execution-engine` as the provider
boundary. The official provider is the openJiuwen execution engine. Heterogeneous
agent frameworks are integrated by engine adapters that translate those
frameworks into the Execution Engine SPI.

`agent-service` may provide extension points, registry assembly, routing, and
anti-corruption entry points needed for runtime compatibility with existing
agents. Framework-specific translation and framework dependency knowledge must
not move into the `agent-service` core.

`agent-execution-engine` must not pull Tasks directly from `agent-bus`, brokers,
external queues, or Platform Gateway surfaces. Task admission, dispatch, resume,
cancellation, and lifecycle visibility enter through `agent-service`.
`agent-execution-engine` receives execution requests through the Execution Engine
SPI and returns execution results or intents to the service owner.

### `agent-middleware`

Owns:

- The agent middleware foundation domain.
- Selectable and integrable services such as memory, knowledge, sandbox, skill,
  tool, model, retrieval, prompt, advisor, and hook services.
- Middleware SPI boundaries and the policy, capacity, audit, and trace evidence
  shapes around those services.

Does not own:

- Runtime lifecycle state.
- Customer business state.
- Direct provider telemetry as the only observability sink.
- Cross-boundary A2A control transport.

### `agent-bus`

Owns:

- The broad access, interaction, and collaboration governance domain.
- Runtime units such as registry, event bus, Platform Gateway, permission
  center, S2C callback, cross-instance A2A/federation, control channel,
  data-reference envelope, and rhythm channel surfaces when assigned by
  lower-level design.
- Platform-centralized control, permission, interaction, and governance surfaces
  for cross-boundary collaboration.
- Platform-level ingress governance, including authentication pre-check, tenant
  routing, cross-service routing, traffic governance, A2A/S2C ingress, and
  permission mediation, when assigned by L1/L2 design.
- Governance of data-reference envelopes, including routing metadata, permission
  mediation, and authorized consumer handoff.
- Cross-instance or cross-boundary rhythm signals, wakeup routing, timeout
  signals, and schedule governance when assigned by L1/L2 design.

Does not own:

- Multi-agent coordination inside a single `agent-service` instance.
- Runtime lifecycle state.
- Service Task API surfaces such as create task, query task, stream task, cancel
  task, and Task lifecycle operations.
- Task-level suspend/resume state, deadline state, or sleep ownership inside an
  `agent-service` instance.
- Large payload or multimodal object transport through narrow event/control
  channels.
- Token-by-token external stream through narrow event/control channels.
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

- Platform Gateway / Gateway capability.
- Workflow.
- Context Engine.
- Tool Gateway.
- Runtime Governance.
- Observability.
- Capability Placement.
- A2A / Federation.

Each aggregate must map to one or more L0 logical modules plus concrete L1/L2
runtime units and contracts before implementation.

## Downstream Artifact Rule

L0 logical modules are not inferred from build or framework mechanics.

- A dependency BoM such as `spring-ai-ascend-dependencies` is a development-view
  or build-governance artifact for version alignment. It does not become a
  logical module.
- A Java starter such as `spring-ai-ascend-graphmemory-starter` is an
  integration/deployment packaging mechanism for auto-configuration or adapter
  bootstrap. It belongs under the logical module whose capability it packages.
- A runtime unit may be split, merged, or packaged differently across deployment
  variants without changing the L0 logical module boundary.

## State Ownership Rules

Every state must have:

- One semantic owner.
- A bounded writer path.
- Known readers.
- Forbidden writers.
- Replay and audit expectations when relevant.

Any new writer or second lifecycle owner is an L0 architecture change.

## Core State Matrix

| State | Owner | Allowed Writers | Forbidden Writers | Status |
|---|---|---|---|---|
| Task execution state | `agent-service` Task lifecycle owner | `agent-service` controlled lifecycle entry | Platform Gateway, bus, engine adapter direct writes, middleware, client, provider adapters | accepted |
| Task hierarchy | Local `agent-service` instance relationship owner plus observability | Same-instance parent/child creation, or accepted cross-instance federation result reference | Bus direct hierarchy mutation, engine adapter, remote service direct local lifecycle mutation | accepted |
| Engine-internal execution state | `agent-execution-engine` | Engine execution loop, workflow node executor, ReAct loop executor, or sanctioned engine adapter path | Platform Gateway, bus, middleware, client, provider adapters, direct service lifecycle writers | accepted |
| Client invocation reference | `agent-client` local handle plus `agent-service` query/reference surface | `agent-service` creates authoritative mapping; client stores local reference | Any writer treating it as independent server lifecycle state | accepted |
| Session state | `agent-service` session/context shell | Session owner and approved context projection paths | Memory owner, tool gateway, business agent direct platform mutation | candidate_promote |
| Memory / knowledge state | `agent-middleware` memory SPI and external memory provider boundary | Memory store writer or configured adapter | Runtime lifecycle state owner; hidden engine context builder | accepted_direction |
| Workflow checkpoint | Checkpointer SPI implementation under runtime governance | Orchestrator/checkpointer sanctioned path | Platform Gateway, tool gateway, client | accepted_direction |
| Context package | Context capability across service and middleware | Context projector / retrieval / memory pipeline | Platform Gateway, lifecycle state store | candidate_promote |
| Tool call record | Middleware governance plus service integration | Skill wrapper / runtime middleware / audit writer | Business agent direct external call bypassing governance | candidate_promote |
| Approval state | Service + bus callback governance | Approval callback handler / S2C transport path | Tool implementation direct write | candidate_promote |
| Trace / span / event | Telemetry vertical | TraceContext, hook chain, runtime event emitter | Provider adapter direct sink bypass | accepted_direction |
| Audit record | Platform audit writer | Append-only audit writer | Business code overwriting platform records | accepted_direction |
| Business state | External business system | Business system owner | Agent runtime platform | accepted_direction |
| Tenant / policy state | Runtime governance / identity and policy owner | Auth/policy owner | Skill implementation bypass | accepted_direction |

## Task Vocabulary Rule

For V1, Task is the unified server-side authoritative execution lifecycle state.
This Task aligns with the A2A protocol task semantics: it can be created or
bound by a client-to-server request, or by an `agent-service` instance request
to another `agent-service` instance through A2A/federation control.

An `agent-service` instance owns Task-level lifecycle state, Task hierarchy,
parent/child relationships, joins, terminal states, and query surfaces for Tasks
created inside that instance. Cross-instance, cross-department, cross-deployment,
or cross-trust-boundary collaboration uses `agent-bus` for A2A/federation
control. The remote Task lifecycle remains owned by the remote `agent-service`
instance; the local instance records the relationship reference, join state, and
observability evidence.

`agent-execution-engine` owns finer-grained execution state below the Task
boundary, including workflow node execution state and ReAct loop state.

Task-level long waits, suspend/resume state, deadlines, and next-wake cursors are
owned by the relevant `agent-service` instance. `agent-bus` may provide
cross-instance or cross-boundary rhythm signals, wakeup routing, timeout
signals, and schedule governance, but a bus-level tick engine must not become the
owner of per-service Task sleep state unless a future ADR changes the lifecycle
owner boundary.

Historical Run-based terms such as Run, RunRepository, RunStatus, RunContext, or
run tree may appear in archived documents or implementation-history references.
They are not L0 canonical lifecycle vocabulary and must not introduce a second
server-side state owner.

## Boundary Conflict Escalation

Open an L0 decision item when a change:

- Adds a lifecycle-state writer.
- Moves the Execution Engine SPI out of the `agent-execution-engine` boundary.
- Treats a capability aggregate as a new module.
- Moves business facts into platform state.
- Makes narrow event/control channels carry large payloads or token streams.
- Changes whether same-instance multi-agent coordination is owned by
  `agent-service`, or whether cross-instance A2A/federation control is mediated
  by `agent-bus`.
- Lets `agent-execution-engine` pull Tasks directly from bus, broker, external
  queue, or Platform Gateway surfaces.
- Moves Service Task API ownership or Task-level sleep ownership from
  `agent-service` to `agent-bus`.
