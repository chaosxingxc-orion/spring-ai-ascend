---
rule_id: R-J
title: "Storage-Engine Tenant Isolation + Cancel Re-Authorization"
level: L1
view: physical
principle_ref: P-J
authority_refs: [ADR-0069, ADR-0020, ADR-0078]
enforcer_refs: [E69, E106]
status: active
scope_phase: design
kernel_cap: 8
kernel: |
  **Tenant isolation is enforced at the storage engine: every Flyway migration creating a `tenant_id`-bearing table MUST enable Postgres Row-Level Security in the same migration (sub-clause .a; pre-rule migrations grandfathered in `gate/rls-baseline-grandfathered.txt` for W2 retrofit). At the HTTP edge, `POST /v1/runs/{runId}/cancel` MUST re-validate `(request.tenantId == Run.tenantId)`; cross-tenant access collapses to 404 `not_found` at W0 (the 403 `tenant_mismatch` + `WARN+` audit MDC `(runId, fromStatus, toStatus, actor, occurredAt)` is the W1-widening direction per ADR-0108, not W0 shipped); idempotent terminal→terminal same-status returns 200; illegal transitions return 409 `illegal_state_transition` (sub-clause .b; read/resume re-auth widening + cancel audit deferred per ADR-0108, resume/retry to Rule R-J.b.d / W2 async orchestrator).**
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
