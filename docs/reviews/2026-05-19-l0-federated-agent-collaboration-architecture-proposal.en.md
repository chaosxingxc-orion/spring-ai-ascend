---
affects_level: L0
affects_view: process
proposal_status: proposal
authors: ["Codex architecture review"]
responds_to:
  - current post-rc10 architecture state
related_adrs:
  - ADR-0049
  - ADR-0068
  - ADR-0069
  - ADR-0070
  - ADR-0074
  - ADR-0078
  - ADR-0079
  - ADR-0084
related_rules:
  - Rule 29
  - Rule 36
  - Rule 39
  - Rule 41
  - Rule 43
  - Rule 46
  - Rule 79
affects_artefact:
  - ARCHITECTURE.md
  - README.md
  - docs/quickstart.md
  - docs/contracts/openapi-v1.yaml
  - docs/contracts/contract-catalog.md
  - docs/contracts/s2c-callback.v1.yaml
  - docs/governance/architecture-status.yaml
  - docs/governance/skill-capacity.yaml
  - docs/cross-cutting/non-functional-requirements.md
  - docs/cross-cutting/posture-model.md
  - agent-bus/ARCHITECTURE.md
  - agent-client/ARCHITECTURE.md
  - agent-service/ARCHITECTURE.md
  - agent-execution-engine/ARCHITECTURE.md
---

# L0 Federated Agent Collaboration Architecture Proposal

## Executive verdict

The current L0 corpus has strong governance mechanics, but it still reads too
much like a central runtime architecture. For the target organisation model, L0
should be reframed as a federated agent collaboration architecture:

1. The company platform provides the agent bus, capability directory, protocol
   contracts, cross-domain identity, audit, trace, policy, and optional hosted
   runtime.
2. Some departments run platform-hosted agents inside the central runtime.
3. Strong domain departments may run sovereign services and execution engines
   inside their own boundary, while exposing governed agent capabilities through
   the company bus.

This distinction is architectural, not merely implementation detail. It decides
where business data lives, who owns execution, what the platform is allowed to
inspect, and how cross-department agents collaborate.

## Root cause and strongest reading

Root cause: the post-rc10 architecture closes many corpus-truth and module-truth
loops, but the L0 product shape is still under-specified for federated service
ownership, client debugging, long-running control flow, payload transfer, and
resource arbitration.

Evidence:

- `agent-client` is explicitly a skeleton with no production code yet
  (`agent-client/ARCHITECTURE.md`).
- The quickstart still references deleted pre-Phase-C modules such as
  `agent-platform` and `agent-runtime` (`docs/quickstart.md`), which means the
  self-service path is not a reliable L0 adoption story.
- The OpenAPI cursor contract talks about polling, SSE, and webhook callback
  semantics (`docs/contracts/openapi-v1.yaml`), while the implemented client
  module is not yet a usable entrypoint.
- `docs/cross-cutting/non-functional-requirements.md` still defers SLOs for
  `POST /v1/runs`, cancellation, tool calls, cost, durability, and capacity
  targets.

Strongest valid reading: the project is not trying to force every domain agent
into one central service. It needs a platform that supports both centrally
hosted agents and sovereign domain agents that participate through a governed
agent bus.

## Definitions

### Platform-hosted department

A department that accepts central platform hosting for its agent service,
runtime, execution engine, and a controlled subset of its data adapters. It
mostly consumes platform-provided SDKs, templates, capabilities, observability,
and governance.

### Federated sovereign department

A department that keeps its core data assets, service runtime, execution engine,
authorisation model, audit rules, and domain tools inside its own boundary. It
exposes a governed agent capability surface through the company bus so other
departments' agents can request work, receive cursors, and collaborate without
direct access to the department's core data.

### Hybrid department

A department that hosts low-risk or shared capabilities on the central platform
while keeping sensitive execution and data inside a sovereign service.

## Findings and proposals

### F1. L0 must make federation a first-class topology

Current concern: the architecture describes modules and governance rules well,
but it does not clearly state that a business department may own its own
service and engine while only joining the platform through the bus.

Proposal:

- Add a top-level "Federation Model" section to `ARCHITECTURE.md`.
- Treat `PlatformHostedAgent` and `FederatedDomainAgent` as first-class L0
  deployment modes.
- Define the agent bus as the collaboration boundary, not merely an internal
  transport module.
- Make it explicit that central platform ownership stops at protocol, routing,
  policy, trace, audit, and optional hosting. It does not imply ownership of
  every department's core data or execution internals.

Acceptance criteria:

- L0 diagrams and text distinguish central platform runtime from sovereign
  domain services.
- The platform-hosted and federated modes are both shown in the process and
  physical views.
- `agent-bus/ARCHITECTURE.md` describes cross-domain collaboration, not only
  internal bus scaffolding.

### F2. Data sovereignty boundary is under-specified

Current concern: strong domain departments will reject a design that appears to
centralise their data assets. L0 needs a precise data sovereignty contract.

Proposal:

- Define a "Data Sovereignty Boundary" in L0.
- Default rule: core business data remains in the owning department's service.
- Cross-bus exchange carries intent, authorisation, trace context, task cursor,
  references, summaries, and policy-scoped results. It does not default to raw
  data transfer.
- Federated departments may reject, degrade, redact, async-approve, or return
  references instead of raw payloads.

Acceptance criteria:

- The bus protocol includes `caller`, `purpose`, `scope`, `tenant`, `trace`,
  `retention`, and `data_access_policy` fields or equivalent concepts.
- L0 states that `data_ref` / `payload_ref` is preferred for sensitive or large
  payloads crossing domain boundaries.
- The platform cannot require direct attachment of sovereign databases,
  knowledge graphs, or private tools to the central runtime.

### F3. Client boundary and debug experience are not yet an L0 closed loop

Current concern: cursor flow is a good principle, but L0 does not yet define the
full developer journey for either central clients or federated domain agents.

Proposal:

- Add a "Client Boundary and Debug Experience" section.
- Define three entrypoints:
  - SDK for typed submit, cursor polling, stream subscription, cancellation,
    idempotency, trace propagation, and error decoding.
  - CLI for weak departments and support teams to reproduce, inspect, cancel,
    and replay runs.
  - Raw HTTP as the lowest-level contract, not the primary experience.
- Define a minimum debugging loop:
  `submit -> cursor -> status/events -> failure envelope -> trace/audit lookup
  -> replay or retry`.

Acceptance criteria:

- L0 explicitly names the observability data a client must receive: run id,
  cursor URL, trace id, stable error code, tenant/caller echo, and retry hint.
- `agent-client` has a roadmap tied to these L0 obligations, not only a W3+
  placeholder statement.
- Quickstart separates platform-hosted local development from federated service
  integration.

### F4. Strong and weak adoption models should be architectural roles

Current concern: the earlier "strong vs weak department" language should not be
interpreted as engineering skill. The real distinction is deployment and data
sovereignty.

Proposal:

- Rename the concept in L0 to "hosted adoption" and "sovereign federated
  adoption".
- Define who owns service runtime, engine runtime, data, policy, audit, and
  on-call responsibility in each mode.
- Provide a compatibility path for hybrid adoption.

Suggested ownership matrix:

| Concern | Platform-hosted | Federated sovereign | Hybrid |
|---|---|---|---|
| Agent service | Central platform | Domain department | Split |
| Execution engine | Platform engine | Domain engine | Both |
| Core data | Platform-approved adapters | Domain boundary | Sensitive data stays domain-side |
| Capability discovery | Company directory | Company directory | Company directory |
| Invocation path | Local platform runtime | Agent bus | Agent bus plus local runtime |
| Trace and audit | Central plus local logs | Cross-bus trace plus local audit | Both |
| Policy enforcement | Central policy | Central envelope plus local policy | Both |

Acceptance criteria:

- L0 explains when a department should choose each mode.
- No L0 text implies that all departments must deploy inside `agent-service`.
- Federated domain service is treated as a peer runtime reachable through the
  bus, not as a downstream tool hidden inside platform execution.

### F5. Run control plane is an L0 concern, not just a future implementation

Current concern: the architecture has Run state and cursor flow, but the system
control plane is not fully defined. For long-horizon agent work, L0 must define
admission, dispatch, scheduling, backpressure, cancellation, retry, and recovery.

Proposal:

- Add a "Run Control Plane" L0 view.
- Define these components conceptually:
  - Ingress: validates request, creates run, returns cursor.
  - Admission: evaluates tenant quota, domain policy, skill capacity, and
    system load.
  - Dispatcher: routes to local platform engine or federated domain service.
  - Scheduler: handles fairness, priorities, timeouts, retries, and
    backpressure.
  - Execution engine: graph, agent-loop, future multi-agent, or domain-owned
    engine.
  - Recovery: checkpoint, resume, dead-letter, manual intervention.
  - Observation: run events, trace, audit, metrics, and replay evidence.

Acceptance criteria:

- L0 defines when a run is admitted, queued, suspended, rejected, failed, or
  delegated to a federated domain service.
- Cancellation is defined as a protocol-level request, with best-effort and
  domain-sovereign semantics for federated services.
- The bus path and local engine path share compatible cursor and result
  envelopes.

### F6. Payload and resume contract needs an L0 boundary

Current concern: implementation currently allows generic object payloads in
several runtime contracts. Even if acceptable in early code, L0 must define the
future-proof cross-boundary payload model.

Proposal:

- Add a "Payload and Resume Contract" section.
- Require a typed envelope for payloads that cross node, run, service, bus, or
  client/server boundaries.
- Require `schema_id`, `schema_version`, `content_type`, `size_bytes`,
  `pii_classification`, and either inline content or `payload_ref`.
- Prefer `payload_ref` for large, sensitive, or sovereign-domain payloads.
- Forbid Java closures, arbitrary object graphs, and unversioned payloads from
  crossing durable or federated boundaries.

Acceptance criteria:

- Suspend/resume, S2C callback, bus invocation, and federated result contracts
  share compatible payload envelope concepts.
- L0 states how payload version migration and audit replay are expected to work.
- Payload limits are tied to resource and data sovereignty rules.

### F7. Idempotency, retry, and replay are public API semantics

Current concern: idempotency is currently documented partly as an HTTP behavior
and partly as a storage detail. For a client and bus architecture, it must be an
L0 reliability semantic.

Proposal:

- Define a cross-mode idempotency state machine.
- Same key plus same request body should return the same cursor or terminal
  response summary, not an ambiguous conflict, once the request has been
  accepted.
- Same key plus different body remains a body-drift conflict.
- A stale accepted-but-not-dispatched claim must have an explicit lease and
  recovery rule.
- Federated calls must carry idempotency keys across the bus so sovereign
  services can deduplicate within their own boundary.

Acceptance criteria:

- L0 specifies first submit, in-flight replay, terminal replay, body drift,
  stale claim recovery, and domain-delegated replay.
- OpenAPI, bus contracts, and S2C contracts use compatible language.
- Client SDK and CLI can give a deterministic answer after network loss:
  "created", "already in flight", "completed", "failed", or "body drift".

### F8. Resource model should cover both central and federated execution

Current concern: skill capacity is a good start, but L0 needs a broader resource
model that includes central platform pools and domain-owned federated services.

Proposal:

- Define resources across:
  - HTTP request workers or virtual threads.
  - Dispatch queue depth.
  - Database connection pool.
  - LLM provider concurrency.
  - Tool and MCP concurrency.
  - Memory/vector/graph sidecar capacity.
  - Client callback capacity.
  - Federated domain service capacity.
  - Trace, log, and audit export volume.
- Define common outcomes: admit, queue, suspend, reject, shed, degrade, and
  retry-after.
- Require each federated domain capability to publish capacity hints and
  backpressure behavior.

Acceptance criteria:

- L0 explains how tenant quota and skill capacity compose with federated
  service-level capacity.
- The agent bus can propagate backpressure and retry hints.
- Resource decisions map to stable run statuses and client-visible error codes.

### F9. Artifact layering should prevent server-heavy starter dependencies

Current concern: the current module shape risks making downstream integrations
depend on the full runnable service to access a small SPI surface. That is an
architecture smell when the platform must support many departments and
federated services.

Proposal:

- L0 should define artifact roles:
  - API/kernel artifacts: pure contracts, records, SPI, no server runtime.
  - Server artifacts: runnable services.
  - Starter artifacts: Spring Boot integration over API/kernel artifacts.
  - Reference adapters: optional implementations.
- Starters should depend on lightweight API/kernel artifacts, not the full
  central server artifact, unless the starter is explicitly a server extension.

Acceptance criteria:

- `agent-service` is not the default dependency for departments that only need
  SPI types.
- Future starters choose `agent-runtime-core` or a dedicated API artifact as
  their primary dependency.
- L0 states which artifacts are stable public contracts versus deployment
  units.

## Proposed L0 document additions

1. `ARCHITECTURE.md` new section: Federation Model.
2. `ARCHITECTURE.md` new section: Data Sovereignty Boundary.
3. `ARCHITECTURE.md` new section: Client Boundary and Debug Experience.
4. `ARCHITECTURE.md` new section: Run Control Plane.
5. `ARCHITECTURE.md` new section: Payload and Resume Contract.
6. `ARCHITECTURE.md` new section: Resource Model.
7. `agent-bus/ARCHITECTURE.md` update: bus as inter-department agent
   collaboration boundary.
8. `agent-client/ARCHITECTURE.md` update: roadmap from skeleton to SDK/CLI
   obligations.
9. `docs/contracts/contract-catalog.md` update: bus invocation, cursor, result,
   error, policy, trace, audit, and payload envelope contracts.
10. `docs/quickstart.md` update: split platform-hosted quickstart from
    federated domain-service quickstart.

## Suggested future contract names

The exact file names can be decided by the architecture team. Suggested L0/L1
contract surfaces:

- `docs/contracts/agent-capability.v1.yaml`
- `docs/contracts/agent-invocation.v1.yaml`
- `docs/contracts/task-cursor.v1.yaml`
- `docs/contracts/result-envelope.v1.yaml`
- `docs/contracts/payload-envelope.v1.yaml`
- `docs/contracts/policy-envelope.v1.yaml`
- `docs/contracts/federated-audit-event.v1.yaml`

## Priority order

1. Establish federation and data sovereignty in L0 before adding more central
   runtime rules.
2. Define client/debug and run-control semantics before implementing the W3
   SDK.
3. Define payload and idempotency semantics before expanding suspend/resume and
   S2C.
4. Define resource outcomes before binding skill capacity to production
   scheduling.
5. Only then add prevention gates. Gates should protect the L0 shape after the
   shape is correct.

## Non-goals for this proposal

- This proposal does not require immediate implementation changes.
- This proposal does not reject the current `agent-runtime-core`,
  `agent-service`, `agent-execution-engine`, or `agent-middleware` split.
- This proposal does not require strong departments to expose internal data,
  engines, or databases to the central platform.
- This proposal does not require every department to use the central execution
  engine.

## Final recommendation

Reframe L0 from "central agent runtime plus governance" to "federated agent
collaboration platform". The central platform should own bus protocols,
capability discovery, identity propagation, policy envelopes, cross-domain
trace, audit, and optional hosted runtime. Strong domain departments should be
able to keep service and execution sovereignty while exposing controlled agent
capabilities through the company bus.

This framing preserves the existing governance strength while making the
architecture acceptable to departments that will not place core data assets
inside the central platform.
