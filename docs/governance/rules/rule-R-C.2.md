---
rule_id: R-C.2
title: "Run Contract Spine"
level: L1
view: logical
principle_ref: P-C
authority_refs: [ADR-0068, ADR-0078, ADR-0088, ADR-0094]
enforcer_refs: [E2, E4, E9, E11]
status: active
scope_phase: impl
kernel_cap: 8
scope_surfaces:
  - "agent-service/src/main/java/ascend/springai/service/runtime/runs/**/*.java"
  - "agent-service/src/main/java/ascend/springai/service/runtime/idempotency/**/*.java"
  - "agent-service/src/main/java/ascend/springai/service/runtime/**/*.java (import scan)"
kernel: |
  **Every persistent record under `agent-service/src/main/java/ascend/springai/service/runtime/{runs,idempotency}/**/*.java` MUST declare a `String tenantId` validated by `Objects.requireNonNull` (sub-clause .a — Contract Spine Completeness; relocated from agent-runtime-core per ADR-0088). Every `Run.withStatus(newStatus)` MUST call `RunStateMachine.validate(this.status, newStatus)` (sub-clause .b — Run State Transition Validity). No production class under `service.runtime..` may import `service.platform..`; the original narrow `TenantContextHolder` ban is asserted independently as defence-in-depth (sub-clause .c — Tenant Propagation Purity).**
---

# Rule R-C.2 — Run Contract Spine

Split out from Rule R-C.c + .d + .e in the rc17 wave (per ADR-0094) to
group the three **run-entity invariants** under one rule. Originally
existed as separate Rules 11 / 20 / 21.

## Motivation

The Run entity is the persistence-layer spine of the platform. Three
invariants must hold for every Run instance to be trustworthy across
serialisation boundaries, multi-tenant isolation, and state-machine
correctness:

1. **Tenant identity** — every persistent record carries `tenantId`, so a
   compromised process or a misrouted message cannot leak cross-tenant.
2. **State-machine validity** — Run status transitions go through the
   declared DFA, so out-of-order or impossible transitions are caught at
   write time, not after corruption.
3. **Module isolation** — runtime code cannot import platform code, so
   the dependency direction stays acyclic and runtime stays
   independently testable.

These three invariants were previously expressed as separate sub-clauses
of Rule R-C (.c, .d, .e), which made the larger R-C kernel a 5-sub-clause
catch-all. Splitting them into R-C.2 clarifies that they form ONE
coherent contract about Run-entity discipline.

## Sub-clauses

### .a — Contract Spine Completeness (was Rule R-C.c)

**Enforcers**: E2, E11.

Every persistent record class committed under
`agent-service/src/main/java/ascend/springai/service/runtime/{runs,idempotency}/**/*.java`
(relocated from `agent-runtime-core/src/main/java/ascend/springai/service/runtime/**/*.java`
per ADR-0088 rc13 dissolution) MUST declare a `String tenantId` component
validated by `Objects.requireNonNull(tenantId, "tenantId is required")`
in its compact constructor.

Process-internal value objects exempt themselves with a
`// scope: process-internal` reason comment.

Activated 2026-05-18 (Wave 4 Track B) — trigger met by `Run` and
`IdempotencyRecord` carrying `tenantId`.

### .b — Run State Transition Validity (was Rule R-C.d)

**Enforcer**: E9 (RunStatusTransitionIT).

Every `Run.withStatus(newStatus)` mutation MUST call
`RunStateMachine.validate(this.status, newStatus)` before constructing
the updated record. Illegal transitions MUST throw
`IllegalStateException`.

### .c — Tenant Propagation Purity (was Rule R-C.e)

**Enforcers**: E2, E4.

No production class under `ascend.springai.service.runtime..` (main
sources) may import any class under `ascend.springai.service.platform..`.
The original narrow case — no import of `TenantContextHolder` — remains
the specific instance most likely to be violated and is asserted
independently as defence-in-depth.

## Why split from R-C

The original Rule R-C bundled three orthogonal invariants:

- R-C.a (code-as-contract): every constraint maps to ≥1 enforcer surface
  — a governance-system invariant.
- R-C.b (module evolution, now R-C.1): every module has metadata +
  builds in isolation — a build-system invariant.
- R-C.c/.d/.e (this rule, R-C.2): tenantId required, state-machine
  validates, runtime can't import platform — persistence-system
  invariants on the Run entity.

Reviewers reading "Rule R-C" had to mentally partition a 5-sub-clause
rule into three concerns. Splitting clarifies which sub-rule a finding
belongs to and which test layer covers it.

## Activation history

- 2026-05-18 (Wave 4 per ADR-0068) — original legacy Rule 11 / Rule R-C.c.
- 2026-05-XX (W1) — original legacy Rule 20 / Rule R-C.d.
- 2026-05-XX (W1) — original legacy Rule 21 / Rule R-C.e.
- 2026-05-20 (rc13 per ADR-0088) — Rule R-C.c relocated from
  agent-runtime-core to agent-service.
- 2026-05-21 (rc17 per ADR-0094) — grouped under standalone rule R-C.2.
  Enforcers E2/E4/E9/E11 unchanged; no behaviour delta.

## Cross-references

- ADR-0078 — Phase-C consolidation.
- ADR-0088 — agent-runtime-core dissolution (sub-clause .a relocation).
- ADR-0094 — rc17 rule-consolidation authority (this split).
- Rule R-C — Code-as-Contract (sibling: governance-system invariant).
- Rule R-C.1 — Independent Module Evolution (sibling: build-system
  invariant).
- Rule R-J.a — Storage-Engine Tenant Isolation (storage-layer dual to
  sub-clause .a; tenantId at app layer + RLS at db layer).
