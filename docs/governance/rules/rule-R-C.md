---
rule_id: R-C
title: "Code-as-Contract"
level: L1
view: development
principle_ref: P-C
authority_refs: [ADR-0064, ADR-0068, ADR-0094]
enforcer_refs: [E15, E16, E17, E18, E19, E27, E28, E29, E30]
status: active
scope_phase: design
kernel_cap: 8
kernel: |
  **Every active normative constraint in the platform corpus MUST be enforced by code, registered in `docs/governance/enforcers.yaml`, and reach ≥1 of: an ArchUnit test, a `gate/check_architecture_sync.sh` rule, an integration test, a storage-layer schema constraint (NOT NULL / UNIQUE / CHECK / PRIMARY KEY), or a compile-time check (`@ConfigurationProperties + @Valid`, sealed types, package-info enforcement). Module-evolution invariants split to Rule R-C.1; run-spine invariants split to Rule R-C.2 per ADR-0094.**
---

# Rule R-C — Code-as-Contract

Operationalises principle **P-C** (Code-as-Everything, Rapid Evolution, Independent Modules) on its single remaining sub-clause `.a` — every active normative constraint must be enforced by code AND registered in `docs/governance/enforcers.yaml`. Per ADR-0094 (rc17), the original sub-clauses .b/.c/.d/.e were extracted to sibling sub-rules:

| Was (in R-C, pre-rc17) | Now (post-rc17 ADR-0094) | Card |
|---|---|---|
| .b — Independent Module Evolution | **Rule R-C.1** | [`rule-R-C.1.md`](rule-R-C.1.md) |
| .c — Contract Spine Completeness | **Rule R-C.2** sub-clause .a | [`rule-R-C.2.md`](rule-R-C.2.md) |
| .d — Run State Transition Validity | **Rule R-C.2** sub-clause .b | [`rule-R-C.2.md`](rule-R-C.2.md) |
| .e — Tenant Propagation Purity | **Rule R-C.2** sub-clause .c | [`rule-R-C.2.md`](rule-R-C.2.md) |

Readers seeking those invariants should consult the sibling cards above; this card is now bounded to the Code-as-Contract obligation only.

## Sub-clause .a — Code-as-Contract (was legacy Rule 28)

**Enforcers**: E15, E16, E17, E18, E19, E27, E28, E29, E30.

Every active normative constraint MUST be enforced by code, registered in `docs/governance/enforcers.yaml`, and reach at least one of:

1. An ArchUnit test that fails when the constraint is violated.
2. A gate-script rule in `gate/check_architecture_sync.sh` that exits non-zero.
3. An integration test that asserts the observable behaviour.
4. A schema constraint (NOT NULL / UNIQUE / CHECK / PRIMARY KEY) at the storage layer.
5. A compile-time check (`@ConfigurationProperties` + `@Valid`, sealed types, package-info enforcement).

## Cross-references

- ADR-0064 — Layer-0 Governing Principles authority.
- ADR-0094 — rc17 rule-consolidation authority (R-C three-way split).
- **Rule R-C.1** — Independent Module Evolution (sibling extracted).
- **Rule R-C.2** — Run Contract Spine: tenantId + RunStateMachine + tenant-purity (sibling extracted).
- Companion: Rule R-J.a (Storage-Engine Tenant Isolation — R-C.2.c is the application-layer dual).
