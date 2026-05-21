---
principle_id: P-J
title: "Storage-Engine Tenant Isolation"
level: L0
view: physical
authority: "Layer 0 governing principle (CLAUDE.md); LucioIT W1 L0 §6-§7"
enforced_by_rules: [R-J]   # formerly Rule 40 (numeric pre-ADR-0086)
kernel: |
  P-J — Storage-Engine Tenant Isolation.
  Tenant isolation lives at the storage engine, not the application code.
  Every tenant-scoped table MUST enable Row-Level Security policies;
  even a fully-compromised application tier cannot leak across tenants.
  Enforced by Rule R-J.a (V1/V2 grandfathered per
  `gate/rls-baseline-grandfathered.txt`; W2 retrofit per
  `CLAUDE-deferred.md` 40.b).
---

## Motivation

This principle exists because **application-layer tenant isolation is structurally insecure** — a single bypass (path traversal, ORM injection, broken WHERE filter, missing tenant_id propagation in a timer-driven resume) breaks every tenant at once. The historical record across multi-tenant SaaS shows this is the #1 cause of cross-tenant data leaks. Postgres Row-Level Security at the storage engine ensures that even if the application tier is fully compromised — even if every WHERE clause is stripped — the database itself refuses to return rows from a tenant other than the one in the connection's session context. V1/V2 migrations predating Rule R-J.a are grandfathered with a documented W2 retrofit obligation rather than silently exempted.

## Operationalising rules

- Rule R-J — Storage-Engine Tenant Isolation + Cancel Re-Authorization ([`docs/governance/rules/rule-R-J.md`](../rules/rule-R-J.md))

## Cross-references

- ADR-0069 (origin of Rules 35–42 and the LucioIT W1 §7.2 RLS doctrine)
- Grandfather list: [`gate/rls-baseline-grandfathered.txt`](../../../gate/rls-baseline-grandfathered.txt)
- Deferred sub-clause 40.b — V1/V2 grandfather retrofit (W2 trigger) — see [`docs/CLAUDE-deferred.md`](../../CLAUDE-deferred.md)
- Related: Rule R-C.2.c (Tenant Propagation Purity) — runtime code must source tenant from `RunContext.tenantId()`, not request-scoped `TenantContextHolder`
- Related: P-I (Five-Plane Topology) — Bus & State Hub plane owns persistence enforcement
