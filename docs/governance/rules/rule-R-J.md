---
rule_id: R-J
title: "Storage-Engine Tenant Isolation + Cancel Re-Authorization"
level: L1
view: physical
principle_ref: P-J
authority_refs: [ADR-0069, ADR-0020, ADR-0078]
enforcer_refs: [E69, E106]
status: active
product_claim: "PC-003"
scope_phase: design
kernel_cap: 8
kernel: |
  **Tenant isolation is enforced at the storage engine: every Flyway migration creating a `tenant_id`-bearing table MUST enable Postgres Row-Level Security in the same migration (sub-clause .a; pre-rule migrations grandfathered in `gate/rls-baseline-grandfathered.txt` for W2 retrofit). At the HTTP edge, `POST /v1/runs/{runId}/cancel` MUST re-validate `(request.tenantId == Run.tenantId)`; cross-tenant access collapses to 404 `not_found` at W0 (the 403 `tenant_mismatch` + `WARN+` audit MDC `(runId, fromStatus, toStatus, actor, occurredAt)` is the W1-widening direction per ADR-0108, not W0 shipped); idempotent terminal→terminal same-status returns 200; illegal transitions return 409 `illegal_state_transition` (sub-clause .b; read/resume re-auth widening + cancel audit deferred per ADR-0108, resume/retry to Rule R-J.b.d / W2 async orchestrator).**
deferred_sub_clauses:
  - id: ".a.b"
    title: "RLS Retrofit for Grandfathered Tables [Deferred to W2]"
    re_introduction_trigger: "first multi-tenant production tenant goes live with the `idempotency_dedup` table populated (target: W2)."
    deferred_body: |
      **Rule (draft)**: A new Flyway migration (V3 or later) MUST `ALTER TABLE idempotency_dedup ENABLE ROW LEVEL SECURITY` and add per-tenant `CREATE POLICY` rules. After landing, the table is removed from `gate/rls-baseline-grandfathered.txt` and Rule R-J.a enforces RLS on it directly.

      Composes with: ARCHITECTURE.md §7.2; ADR-0069; Rule R-J.a; LucioIT W1 §7.2.
    relates_to: ["ADR-0069", "Rule R-J.a", "ARCHITECTURE.md §7.2", "LucioIT W1 §7.2"]
  - id: ".b.d"
    title: "RunLifecycle Resume + Retry Re-Authorization [Deferred to W2]"
    re_introduction_trigger: "first W2 async orchestrator implementation that introduces a non-cancel resume or retry transition on the RunLifecycle HTTP edge (target: W2 async orchestrator wave; conditioned on the same trigger as Rule R-M sub-clause .d.c)."
    deferred_body: |
      **Rule (draft)**: For every `POST /v1/runs/{runId}/resume` and `POST /v1/runs/{runId}/retry` operation that ships post-W1, the controller MUST re-validate `(request.tenantId == Run.tenantId)`. At W0/W1 cancel scope, run-owner mismatch collapses to 404 `not_found`; the future resume/retry surface will adopt the ADR-0108/ADR-0116 widened semantics only when structured 403 `tenant_mismatch` audit is promoted with that wave. Resume / retry MUST refuse to advance a Run whose `Run.tenantId` no longer resolves to a live tenant (HTTP 410 `tenant_revoked`). The orchestrator-side resume path MUST consult `RunStateMachine.validate(currentStatus, RUNNING)` before any state transition; illegal transitions return HTTP 409 `illegal_state_transition`. Structured `WARN+` audit logs MUST carry `(runId, fromStatus, toStatus, actor, occurredAt)` MDC fields, matching the future widened cancel surface. Re-introduction trigger composes with the durable `run_state_change` audit table deferred per ADR-0020 (lands together).

      **Background**: At W1.x scope only `cancel` exists as a Run-lifecycle HTTP verb; `resume` and `retry` are W2 async-orchestrator features. The Rule R-J kernel + sub-clause .b deferred-list explicitly defers the matching re-authorization surface to R-J.b.d. The rc16 corpus-truth wave (P1-1 / Family A reconciliation per ADR-0093) added this heading after a Family A sweep surfaced the orphaned reference in `principle-coverage.yaml#deferred_operationalisers` (R-J.b.d had been listed as deferred but lacked a backing heading here).

      Composes with: Rule R-J.b (cancel re-authorization, W1 active surface); Rule R-C.d (Run State Transition Validity); Rule R-M sub-clause .d.c (W2 async orchestrator landing — shared trigger); ADR-0020 (RunLifecycle durable audit table); ADR-0069 / LucioIT W1 §7.2; ADR-0093 (rc16 cross-authority parity wave authority).
    relates_to: ["ADR-0020", "ADR-0069", "ADR-0093", "ADR-0108", "ADR-0116", "Rule R-J.b", "Rule R-C.d", "Rule R-M sub-clause .d.c"]
---

# Rule R-J — Storage-Engine Tenant Isolation + Cancel Re-Authorization

Operationalises principle **P-J** (Storage-Engine Tenant Isolation) across two surfaces: the storage layer (sub-clause .a) and the HTTP cancel edge (sub-clause .b).

## Sub-clauses

### .a — Storage-Engine Tenant Isolation (was Rule 40)

**Enforcer**: E69 (`rls_for_new_tenant_tables`).

Every Flyway migration that creates a table with a `tenant_id` column MUST enable Postgres Row-Level Security in the same migration (`ALTER TABLE <name> ENABLE ROW LEVEL SECURITY` plus per-tenant `CREATE POLICY`). Migrations predating this rule are listed in `gate/rls-baseline-grandfathered.txt` and MUST be retrofitted in W2.

**Motivation** (LucioIT W1 §7.2): application-layer tenant isolation is insecure — a single bypass (path traversal, ORM injection, broken filter) breaks every tenant. RLS at the storage engine ensures even a fully-compromised application tier cannot read across tenants.

**Cross-references**:
- Gate Rule 50 (`rls_for_new_tenant_tables`) scans every `agent-*/src/main/resources/db/migration/V*.sql` for tables with `tenant_id`; requires either matching `ENABLE ROW LEVEL SECURITY` in the same file OR an entry in the grandfather list.
- Architecture reference: ADR-0069 / LucioIT W1 §7.2.
- Grandfather list: `gate/rls-baseline-grandfathered.txt` (V1/V2 migrations grandfathered).
- Grandfather retrofit deferred to W2 per `CLAUDE-deferred.md` 40.b.
- Companion clause: Rule R-C.2 sub-clause .c (Tenant Propagation Purity; was Rule R-C.e pre-rc17 per ADR-0094 — application-layer tenant identity discipline; RLS is the storage-layer defence-in-depth).

### .b — RunLifecycle Re-Authorization (cancel-only at W1) (was Rule 24)

**Enforcer**: E106 (`runlifecycle_cancel_reauthz_shipped`).

Every `POST /v1/runs/{runId}/cancel` operation MUST re-validate `(request.tenantId == Run.tenantId)`. At W0 the shipped controller collapses cross-tenant access into HTTP 404 `not_found`; the 403 `tenant_mismatch` response and the structured `WARN+` cancel-audit MDC `(runId, fromStatus, toStatus, actor, occurredAt)` are the W1-widening direction per ADR-0108 (which `extends` this rule), not W0 shipped behaviour. Idempotent already-CANCELLED calls return 200; a terminal non-CANCELLED state returns 409 `illegal_state_transition`. Resume and retry sub-clauses (R-J.b.d) remain deferred to the W2 async orchestrator.

**Active surface (W1)**: `RunController.cancel(runId, tenantHeader)` in `agent-service/src/main/java/com/huawei/ascend/service/platform/web/runs/RunController.java`:
- Reads `Run` from `RunRepository.findById(runId)`; returns 404 if missing OR the Run belongs to another tenant (W0 cross-tenant collapse; the 403 split is the W1-widening direction per ADR-0108).
- Routes the cancel write through `RunRepository.updateIfNotTerminal(runId, r -> r.withStatus(CANCELLED))` so the re-read, terminal check, and write are one atomic step — a parallel terminal write (orchestrator SUCCEEDED/FAILED) can never be silently overwritten.
- Returns 200 if the resolved Run is CANCELLED (either just cancelled, or already CANCELLED — idempotent).
- Returns 409 `illegal_state_transition` when the re-read status is terminal and not CANCELLED.
- Structured `WARN+` cancel-audit emission is deferred to the W1-widening wave per ADR-0108; at W1 the audit trail is the application log stream.

**Audit table**: A durable `run_state_change` audit table is deferred to W2 per ADR-0020. At W1 the audit trail lives in the application log stream (Logback JSON).

## Deferred sub-clauses

- Rule R-J.a.b (legacy id 40.b) — V1/V2 grandfathered RLS retrofit — W2.
- Rule R-J.b.d — resume + retry re-authorization, W2 async orchestrator.

See `docs/CLAUDE-deferred.md` for the deferred-runtime obligations and re-introduction triggers. Rule G-3 sub-clause .d (`kernel_deferred_clause_coherence`) asserts the bidirectional link between this active rule and each deferred sub-clause.
