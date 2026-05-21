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
  **Tenant isolation is enforced at the storage engine: every Flyway migration creating a `tenant_id`-bearing table MUST enable Postgres Row-Level Security in the same migration (sub-clause .a; pre-rule migrations grandfathered in `gate/rls-baseline-grandfathered.txt` for W2 retrofit). At the HTTP edge, `POST /v1/runs/{runId}/cancel` MUST re-validate `(request.tenantId == Run.tenantId)` with HTTP 403 `tenant_mismatch` on miss; idempotent terminal→terminal same-status returns 200; illegal transitions return 409 `illegal_state_transition`; the cancel surface emits structured `WARN+` audit logs carrying `(runId, fromStatus, toStatus, actor, occurredAt)` MDC (sub-clause .b; resume/retry deferred to Rule R-J.b.d / W2 async orchestrator).**
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

Every `POST /v1/runs/{runId}/cancel` operation MUST re-validate `(request.tenantId == Run.tenantId)`; mismatch returns HTTP 403 `tenant_mismatch`. Idempotent terminal→terminal same-status calls return 200; illegal transitions return 409 `illegal_state_transition`. The cancel surface emits a structured `WARN+` audit log line carrying `(runId, fromStatus, toStatus, actor, occurredAt)` MDC fields. Resume and retry sub-clauses (24.d) remain deferred to the W2 async orchestrator.

**Active surface (W1)**: `RunController.cancel(runId, tenantHeader)` in `agent-service/src/main/java/ascend/springai/service/platform/web/runs/RunController.java`:
- Reads `Run` from `RunRepository.findById(runId)`; returns 404 if missing.
- Compares `request.tenantId` with `Run.tenantId`; returns 403 on mismatch.
- Returns 200 if the Run is already terminal in CANCELLED state (idempotent).
- Calls `RunStateMachine.validate(currentStatus, CANCELLED)`; throws `IllegalStateException` (handled as 409) on illegal transition.
- Emits structured WARN log with MDC fields per the kernel above.

**Audit table**: A durable `run_state_change` audit table is deferred to W2 per ADR-0020. At W1 the audit trail lives in the application log stream (Logback JSON).

## Deferred sub-clauses

- 40.b — V1/V2 grandfathered RLS retrofit — W2.
- R-J.b.d — resume + retry re-authorization, W2 async orchestrator.

See `docs/CLAUDE-deferred.md` for the deferred-runtime obligations and re-introduction triggers. Rule G-3 sub-clause .d (`kernel_deferred_clause_coherence`) asserts the bidirectional link between this active rule and each deferred sub-clause.
