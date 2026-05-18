---
rule_id: 40
title: "Storage-Engine Tenant Isolation"
level: L1
view: physical
principle_ref: P-J
authority_refs: [ADR-0069]
enforcer_refs: [E69]
status: active
kernel_cap: 8
kernel: |
  **Every Flyway migration that creates a table with a `tenant_id` column MUST enable Postgres Row-Level Security in the same migration (`ALTER TABLE <name> ENABLE ROW LEVEL SECURITY` plus per-tenant `CREATE POLICY`). Migrations predating this rule are listed in `gate/rls-baseline-grandfathered.txt` and MUST be retrofitted in W2.**
---

## Motivation

The L0 motivation (LucioIT W1 §7.2): application-layer tenant isolation is "insecure" — a single bypass (path traversal, ORM injection, broken filter) breaks every tenant. RLS at the storage engine ensures even a fully-compromised application tier cannot read across tenants.

## Cross-references

- Enforced by Gate Rule 50 (`rls_for_new_tenant_tables`) — scans every `agent-*/src/main/resources/db/migration/V*.sql` for tables with `tenant_id` and requires either matching `ENABLE ROW LEVEL SECURITY` in the same file OR an entry in the grandfather list.
- Architecture reference: ADR-0069 / LucioIT W1 §7.2.
- Grandfather list: `gate/rls-baseline-grandfathered.txt` (V1/V2 migrations grandfathered).
- Grandfather retrofit deferred to W2 per `CLAUDE-deferred.md` 40.b.
- Companion rule: Rule 21 ([`rule-21.md`](rule-21.md)) — Tenant Propagation Purity (application-layer tenant identity discipline; RLS is the storage-layer defence-in-depth).

## Deferred sub-clauses

Rule 40.b (see `docs/CLAUDE-deferred.md` for the deferred-runtime obligation(s) and re-introduction trigger(s)). Rule 96 (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.
