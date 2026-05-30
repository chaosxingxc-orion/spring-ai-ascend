---
level: L1
view: logical
status: draft
authority: "ADR-0159 (progressive learning curve, node N2 + the value axis) extends ADR-0156 (Product Authority and Traceability Chain). Requirement data authority: product/requirements.yaml (L2-REQUIREMENT lane). This matrix is a READABLE INTERPRETATION layer (ADR-0159 §8); it mints no authority."
---

# Requirement -> Feature -> FunctionPoint Traceability Matrix

## 1. Purpose and authority

This document is the traceability projection for the **value axis** of the
eight-node progressive learning curve (ADR-0159):

```text
ProductClaim -> Requirement -> Feature -> FunctionPoint
```

ADR-0156 wired the chain `ProductClaim -> Feature -> FunctionPoint` directly,
skipping node N2 (Requirement Definition, ISO/IEC/IEEE 29148). ADR-0159 §2
names the Requirement node and assigns it the `L2-REQUIREMENT` authority lane.
This matrix makes the now-complete four-hop value axis queryable in one place.

Authority boundaries (ADR-0159 §8, single-source-of-truth cascade
`generated facts > DSL > Card/prose`):

- **Requirement data authority** is `product/requirements.yaml` (the
  `L2-REQUIREMENT` lane: stable `REQ-NNN` id, source ProductClaim, rationale,
  priority, status, acceptance criteria). This matrix does **not** restate
  requirement wording, priority, or acceptance criteria; it links the ids.
- **Feature / FunctionPoint authority** is the DSL spine:
  `architecture/features/features.dsl` (the `FEAT-` elements and their
  `requires` edges to FunctionPoints) and
  `architecture/features/function-points.dsl` (the `FP-` elements). The
  `saa.requirement` back-reference property on those elements is the wired
  edge; this table reflects it, it does not own it.
- **ProductClaim authority** is `product/claims.yaml` (`PC-NNN`).
- **Decision authority** for each row is the cited ADR.

This matrix is a readable interpretation layer. It invents no element id and
no relationship; every id below resolves to an existing authored artifact (or,
for `REQ-NNN`, to a row in `product/requirements.yaml`).

## 2. The Requirement id scheme

`REQ-NNN` ids are derived one-to-one from the nine value-thread Features
already registered in `architecture/features/features.dsl`. ECR-2
(`docs/reviews/2026-05-29-progressive-learning-curve-engineering-correction-checklist.en.md`)
requires that *every requirement either maps to an existing Feature/FunctionPoint
or is marked out-of-scope/deferred*; deriving one requirement per existing
value-thread Feature satisfies that bar by construction and keeps the value
axis a clean refinement (each `REQ-NNN` refines the source ProductClaim into a
single demanded capability, which the Feature realises and the FunctionPoints
make behavioural).

The structural-axis EngineeringFrames in `features.dsl`
(`EF-ACCESS-ADMISSION`, `EF-ENGINE-DISPATCH`, `EF-INTERNAL-EVENT-QUEUE`,
`EF-SESSION-TASK-STATE`, `EF-TASK-CONTROL`, `EF-TRANSLATION-INTERCEPT`) are
**not** value-thread Features (`saa.structuralAxis: true`); they carry no
ProductClaim and therefore no Requirement. They are reached on the structure
axis (`Module -> EngineeringFrame -> FunctionPoint`), not the value axis.
Likewise the design-only A2A / MQ FunctionPoints (`FP-A2A-MESSAGE-SEND`,
`FP-A2A-TASKS-CANCEL`, `FP-A2A-TASKS-RESUBSCRIBE`, `FP-MQ-INBOUND`) are
frame-anchored ingress points with no value-thread Feature `requires` edge yet,
so they carry no Requirement in this matrix; they enter the value axis when a
Feature requires them or an ADR creates the structural need.

## 3. Requirement -> ProductClaim -> Feature index

Each requirement inherits its source ProductClaim(s) from the Feature it maps
to (`saa.productClaim` on the `FEAT-` element). The matrix is the join, not the
authority, for those `PC-NNN` values.

| Requirement | Source ProductClaim | Feature (realises it) | Decision (ADR) |
|---|---|---|---|
| `REQ-001` | `PC-001`, `PC-003` | `FEAT-RUN-LIFECYCLE-CONTROL` | ADR-0020 |
| `REQ-002` | `PC-002` | `FEAT-EDGE-COMPUTE-INGRESS` | ADR-0049 |
| `REQ-003` | `PC-004` | `FEAT-SERVER-CLIENT-CALLBACK` | ADR-0088 |
| `REQ-004` | `PC-003` | `FEAT-SUSPEND-RESUME-CONTROL` | ADR-0058 |
| `REQ-005` | `PC-003` | `FEAT-IDEMPOTENCY-AND-REPLAY` | ADR-0027 |
| `REQ-006` | `PC-003` | `FEAT-TENANT-ISOLATION` | ADR-0030 |
| `REQ-007` | `PC-003` | `FEAT-POSTURE-BOOTSTRAP` | ADR-0055 |
| `REQ-008` | `PC-001`, `PC-005` | `FEAT-GRAPH-MEMORY` | ADR-0064 |
| `REQ-009` | `PC-004` | `FEAT-ENGINE-DISPATCH-AND-HOOKS` | ADR-0088 |

## 4. Requirement -> Feature -> FunctionPoint matrix

The Feature -> FunctionPoint column is the set of `requires` edges declared in
`architecture/features/features.dsl`. Each FunctionPoint also carries a
`saa.requirement` back-reference to the requirement(s) it serves; that
back-reference is what makes the chain traversable in both directions.

| Requirement | Feature | FunctionPoint(s) (`requires` edge) | FunctionPoint status |
|---|---|---|---|
| `REQ-001` | `FEAT-RUN-LIFECYCLE-CONTROL` | `FP-CREATE-RUN` | shipped |
| `REQ-001` | `FEAT-RUN-LIFECYCLE-CONTROL` | `FP-CANCEL-RUN` | shipped |
| `REQ-001` | `FEAT-RUN-LIFECYCLE-CONTROL` | `FP-GET-RUN-STATUS` | shipped |
| `REQ-001` | `FEAT-RUN-LIFECYCLE-CONTROL` | `FP-RUN-STATE-TRANSITION` | shipped |
| `REQ-002` | `FEAT-EDGE-COMPUTE-INGRESS` | `FP-INGRESS-ENVELOPE` | shipped |
| `REQ-003` | `FEAT-SERVER-CLIENT-CALLBACK` | `FP-S2C-CALLBACK` | shipped |
| `REQ-004` | `FEAT-SUSPEND-RESUME-CONTROL` | `FP-SUSPEND-RESUME` | shipped |
| `REQ-004` | `FEAT-SUSPEND-RESUME-CONTROL` | `FP-CHILD-RUN-SPAWN` | shipped |
| `REQ-005` | `FEAT-IDEMPOTENCY-AND-REPLAY` | `FP-IDEMPOTENCY-CLAIM` | shipped |
| `REQ-006` | `FEAT-TENANT-ISOLATION` | `FP-TENANT-CROSS-CHECK` | shipped |
| `REQ-007` | `FEAT-POSTURE-BOOTSTRAP` | `FP-POSTURE-BOOT-GUARD` | shipped |
| `REQ-008` | `FEAT-GRAPH-MEMORY` | `FP-GRAPH-MEMORY-STORE` | shipped |
| `REQ-009` | `FEAT-ENGINE-DISPATCH-AND-HOOKS` | `FP-ENGINE-DISPATCH` | shipped |
| `REQ-009` | `FEAT-ENGINE-DISPATCH-AND-HOOKS` | `FP-HOOK-DISPATCH` | shipped |

Note: a FunctionPoint's `saa.status` is the lifecycle of the element itself.
The owning Feature may still be `design_only` (e.g. `FEAT-EDGE-COMPUTE-INGRESS`,
`FEAT-GRAPH-MEMORY`) while its seed FunctionPoint is registered as `shipped`;
this matrix reports the element-level status and defers Feature lifecycle to
`features.dsl` (Rule G-14).

## 5. Reverse coverage check

The traceability invariant runs both directions; the matrix above is the
forward (`REQ -> FEAT -> FP`) projection, and this section is the reverse audit.

**Every value-thread Feature has exactly one Requirement.** The nine `FEAT-`
elements in `features.dsl` that carry `saa.productClaim` (i.e. are value-thread,
not `saa.structuralAxis: true`) each map to exactly one `REQ-NNN` in §3. No
value-thread Feature is left without a Requirement; no Requirement points at a
non-existent Feature.

**Every Requirement maps to >= 1 FunctionPoint.** Each `REQ-NNN` row in §4 has
at least one FunctionPoint, sourced from the Feature's `requires` edges. There
is no requirement that terminates at a Feature with zero FunctionPoints.

**Every ProductClaim retains >= 1 downstream Requirement.** `PC-001` (REQ-001,
REQ-008), `PC-002` (REQ-002), `PC-003` (REQ-001, REQ-004..REQ-007), `PC-004`
(REQ-003, REQ-009), `PC-005` (REQ-008). All five claims keep a downstream
consumer through the new Requirement hop, so inserting node N2 does not orphan
any claim (the Rule G-18 completeness invariant is preserved across the
re-wiring).

## 6. How to extend

When a new value-thread Feature is authored in `features.dsl`:

1. Add the requirement row to `product/requirements.yaml` (data authority:
   `REQ-NNN`, source ProductClaim, rationale, priority, status, acceptance
   criteria) — product-owner sign-off for any new normative wording.
2. Add `saa.requirement "REQ-NNN"` to the `FEAT-` element and to each `FP-`
   element the Feature requires, in `features.dsl` / `function-points.dsl`.
3. Add the `REQ -> FEAT -> FP` rows to §3 and §4 here, and update §5.

A FunctionPoint introduced from prose discussion alone is forbidden (ECR-2
acceptance bar): it must trace back to a Requirement row here or to an
ADR-created structural need recorded on the EngineeringFrame that anchors it.
