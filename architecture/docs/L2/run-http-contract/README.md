---
level: L2
view: logical
feature: run-http-contract
status: scaffold
relates_to:
  - "architecture/docs/L1/agent-service/logical.md"
  - "architecture/docs/L1/agent-service/process.md"
  - "architecture/docs/L1/agent-service/features/access-layer.md"
  - "docs/contracts/openapi-v1.yaml"
authority: "ADR-0068 (Layered 4+1 + Architecture Graph) + ADR-0040 (W1 HTTP contract reconciliation) + ADR-0057 (durable idempotency claim/replay) + ADR-0070 (Cursor Flow)"
---

# L2 — `run-http-contract` (Run lifecycle HTTP wire contract)

This L2 feature sink is the **detail home** for the `POST /v1/runs` (and
sibling run-lifecycle verb) **wire-level** behaviour. It exists to receive the
L2-altitude implementation detail that the layer-purity verdict ruled does NOT
belong in L0 / L1 prose:

- HTTP status-code + route-verb + header runtime behaviour;
- on-wire request / response envelope field shapes;
- the idempotency body-lifetime sequence (claim hash, body-drift, replay).

> **Scaffold status.** This directory is the *sink home* created at the
> L2-scaffolding wave. The per-view files below are placeholders that name the
> authoritative source surfaces and sketch the contract; the actual prose
> migration out of L0 / L1 lands in a later reconcile wave. Until then the
> binding wire authority remains the OpenAPI document (see Authority below) —
> these files interpret it, they do not replace it.

## Authority chain (read top-down)

This L2 feature is a **readable interpretation layer**. It MUST NOT invent
operation IDs, status codes, or field names — every wire fact below is sourced
from the authoritative surfaces, in cascade order:

1. **Contract surface (binding wire authority)** —
   [`../../../../docs/contracts/openapi-v1.yaml`](../../../../docs/contracts/openapi-v1.yaml),
   operation `createRun` (`POST /v1/runs`), with `getRun` and `cancelRun` as
   the sibling run-lifecycle verbs. Schemas `CreateRunRequest`, `TaskCursor`,
   `RunResponse`, `ErrorEnvelope`; parameters `TenantIdHeader`,
   `IdempotencyKeyHeader`.
2. **L1 module design (structural parent)** — the agent-service Access Layer
   (Layer 1), where `POST /v1/runs` is the ingress edge:
   [`../../L1/agent-service/features/access-layer.md`](../../L1/agent-service/features/access-layer.md)
   (feature AS-L1-F01 protocol ingress convergence, AS-L1-F05 tenant / auth /
   idempotency binding).
3. **L1 process view (sequence parent)** —
   [`../../L1/agent-service/process.md`](../../L1/agent-service/process.md)
   §P1 (synchronous intake → state machine) shows the same ingress at L1
   structural altitude; this L2 sink carries its wire-level expansion.
4. **L0 constraint authority** —
   [`../../L0/ARCHITECTURE.md`](../../L0/ARCHITECTURE.md) §4 (the §4 constraint
   corpus that names the boundary without carrying its wire detail).

## View files

| File | View | Carries |
|---|---|---|
| [`logical.md`](logical.md) | logical | `POST /v1/runs` request / response wire shapes + the run-lifecycle HTTP status-code matrix (the migrated wire contract placeholder). |
| [`process.md`](process.md) | process | Idempotency body-lifetime sequence — claim hash, `idempotency_conflict` vs `idempotency_body_drift`, W2 replay branch. |

A view is added only when its detail actually migrates here; this scaffold ships
the two views the L2 README's trigger row named for `run-http-contract`.

## What stays upstream (NOT migrated here)

Per the verdict keep-list, the following remain at L0 / L1 and are only
*referenced* from this sink, never duplicated:

- naming `RunController` / the Access Layer as a boundary identity;
- the development-view package decomposition of the Access Layer;
- citing the ArchUnit / gate enforcer that pins the boundary.

## Gate behaviour

- Rule 37 (`architecture_artefact_front_matter`): every file here declares
  `level: L2` + a `view:` from `{logical|development|process|physical|scenarios}`.
- Rule 38 (`architecture_graph_well_formed`): the `relates_to:` front-matter
  links upward to the L1 agent-service views (structural parent) and the
  OpenAPI contract surface (wire authority).
- Rule 145 (`l2_detail_sink`, advisory): this tree is the sanctioned **sink**
  for the HTTP-runtime / wire-format leak families — detail flagged in L0 / L1
  prose migrates *here* to clear the finding.

## Authority

- ADR-0068 — Layered 4+1 + Architecture Graph as twin sources of truth
  ([`../../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml`](../../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml)).
- ADR-0040 — W1 HTTP contract reconciliation
  ([`../../../../docs/adr/0040-w1-http-contract-reconciliation.md`](../../../../docs/adr/0040-w1-http-contract-reconciliation.md)).
- ADR-0057 — durable idempotency claim / replay
  ([`../../../../docs/adr/0057-durable-idempotency-claim-replay.md`](../../../../docs/adr/0057-durable-idempotency-claim-replay.md)).
- ADR-0070 — Cursor Flow & skill-capacity runtime
  ([`../../../../docs/adr/0070-cursor-flow-and-skill-capacity-runtime.yaml`](../../../../docs/adr/0070-cursor-flow-and-skill-capacity-runtime.yaml)).
- L2 corpus index: [`../README.md`](../README.md).
