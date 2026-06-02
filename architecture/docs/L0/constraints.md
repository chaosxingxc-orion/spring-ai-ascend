---
level: L0
view: scenarios
status: draft
authority: "Consolidated from ARCHITECTURE.md constraints and docs/architecture/l0 principles/invariants"
source_of_truth: true
---

# L0 Constraints

## Purpose

This document summarizes L0 architectural constraints and invariants. The
existing `ARCHITECTURE.md` remains the raw 65-constraint source during
consolidation; this document groups those constraints into a reviewable shape.

## Cross-Cutting Verticals

### Tenant Vertical

Tenant identity must be carried from the edge into runtime and persistence.
Every persisted platform row that belongs to a tenant must carry tenant identity.
Runtime production code must not rely on HTTP-edge ThreadLocal tenant state
outside the sanctioned boundary.

### Posture Vertical

`APP_POSTURE` is read at boot and controls fail-closed defaults. Development,
research, and production posture behavior must be explicit. Missing required
configuration in stricter postures fails closed.

### Telemetry Vertical

Telemetry is a first-class vertical. Trace, span, LLM call, runtime event, audit,
and cost evidence must be emitted through approved carriers such as
`TraceContext` or hook surfaces. Provider adapters must not become independent
telemetry sinks.

### Audit and Policy Vertical

Security decisions, irreversible tool calls, approval decisions, cross-boundary
handoffs, and lifecycle transitions require audit evidence. Policy refusal must
be observable and must not create hidden side effects.

### Capacity and Backpressure Vertical

Long-running work is admitted through bounded runtime resource claims. Resource
pressure is represented as admission decisions, backpressure signals, suspension,
or yield, not unbounded sockets, threads, or in-flight work.

## Core Invariants

| Invariant | Constraint |
|---|---|
| Platform/business decoupling | Business code extends via SPI and configuration; it does not patch platform internals. |
| Single lifecycle writer | Runtime execution lifecycle state has one owner and one sanctioned writer path. |
| Governed tool calls | Tool/skill calls pass through authorization, capacity, idempotency, audit, and observability boundaries. |
| Context through context boundary | Context packages are produced through service/middleware context and memory/retrieval surfaces, not hidden engine logic. |
| Business state externality | Business systems own business facts; platform records references, traces, and controlled results. |
| Suspend instead of hold | Long waits use suspend/resume, cursor, or callback rather than retained physical resources. |
| Trace context propagation | Cross-module execution propagates tenant, trace, and runtime identity. |
| Side-effect safety | Irreversible side effects require idempotency or duplicate protection plus audit. |
| Child work visibility | Child execution is correlated under the parent run/task tree or explicit cross-workflow handoff. |
| Control/data/stream separation | Bus control, service streams, gateway ingress, and object-reference data paths remain separate. |

## System Boundary Constraints

- The platform is self-hostable and targets Spring Boot 4 + Java 21.
- Admin UI, multi-region replication, on-device models, and Python sidecars are
  out of current L0 scope unless accepted ADRs reintroduce them.
- In-process polyglot is treated differently from out-of-process sidecars.
- Vertical examples in historical documents are not product identity unless
  accepted architecture decisions make them so.

## Module Constraints

- Each reactor module must have module metadata and obey allowed dependency
  direction.
- Domain modules must expose SPI packages where required by module metadata and
  DFX/TCK co-design rules.
- Generated architecture fragments are not hand-edited.
- Capability aggregates do not become modules without module admission and ADR
  support.

## Runtime Control Constraints

- Entry must bind tenant, actor, idempotency, posture, and trace.
- Runtime lifecycle transition must go through the sanctioned service/runtime
  owner path.
- Engine behavior returns intents or execution results; it does not bypass the
  lifecycle owner.
- Middleware surfaces enforce model, tool, memory, retrieval, prompt, and hook
  governance.
- Same-service child work is not outsourced to the bus.
- Cross-service, cross-department, or cross-deployment A2A control is mediated
  by bus/federation contracts.

## Data and State Constraints

- Tenant mismatch must fail closed at tenant-scoped surfaces.
- Business facts remain owned by business systems or delegated business-side
  stores.
- Platform trajectory, checkpoint, trace, audit, and cost evidence are platform
  runtime concerns.
- Large payloads and multimodal artifacts use data-reference paths rather than
  bus payloads.
- Raw prompt, completion, tool input, or tool output must not appear as span
  attributes in stricter postures.

## Observability Constraints

- Every core scenario should define expected trace/event/audit evidence.
- LLM generation spans must carry model, token, cost, and latency evidence when
  runtime binding exists.
- Replay surfaces must be tenant-scoped and fail closed on mismatch.
- MCP-only replay remains the current L0 telemetry replay direction unless an
  accepted ADR changes it.

## Design Honesty Constraints

- `draft`, `candidate_promote`, `design_only`, `accepted`, and `shipped` must be
  used honestly.
- Design-only contracts must not be described as runtime-enforced behavior.
- Historical documents may provide decision evidence, but they do not override
  current architecture authority.
- Draft contract YAML under `docs/architecture/l0/05-contracts/` must not drive
  production behavior until promoted into the accepted contract system.

## Verification Expectations

Each L0 constraint should eventually map to at least one of:

- Static architecture check.
- State-machine test.
- Contract test.
- Scenario test.
- Golden trace test.
- Failure injection test.
- Security review.
- Manual architecture review.

Unverified constraints must be listed in `governance.md` as missing verification
or pending promotion.
