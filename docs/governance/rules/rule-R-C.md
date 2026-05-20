---
rule_id: R-C
title: "Code-as-Contract"
level: L1
view: development
principle_ref: P-C
authority_refs: [ADR-0064, ADR-0068, ADR-0094]
enforcer_refs: [E15, E16, E17, E18, E19, E27, E28, E29, E30]
status: active
kernel_cap: 8
kernel: |
  **Every active normative constraint in the platform corpus MUST be enforced by code, registered in `docs/governance/enforcers.yaml`, and reach ≥1 of: an ArchUnit test, a `gate/check_architecture_sync.sh` rule, an integration test, a storage-layer schema constraint (NOT NULL / UNIQUE / CHECK / PRIMARY KEY), or a compile-time check (`@ConfigurationProperties + @Valid`, sealed types, package-info enforcement). Module-evolution invariants split to Rule R-C.1; run-spine invariants split to Rule R-C.2 per ADR-0094.**
---

# Rule R-C — Code-as-Contract + Independent Module Evolution + Run Contract Spine

Operationalises principle **P-C** (Code-as-Everything, Rapid Evolution, Independent Modules) across five sub-clauses.

## Sub-clauses

### .a — Code-as-Contract (was Rule 28)

**Enforcers**: E15, E16, E17, E18, E19, E27, E28, E29, E30.

Every active normative constraint MUST be enforced by code, registered in `docs/governance/enforcers.yaml`, and reach at least one of:

1. An ArchUnit test that fails when the constraint is violated.
2. A gate-script rule in `gate/check_architecture_sync.sh` that exits non-zero.
3. An integration test that asserts the observable behaviour.
4. A schema constraint (NOT NULL / UNIQUE / CHECK / PRIMARY KEY) at the storage layer.
5. A compile-time check (`@ConfigurationProperties` + `@Valid`, sealed types, package-info enforcement).

### .b — Independent Module Evolution (was Rule 31)

**Enforcer**: E31.

Every reactor module under `<module>/pom.xml` MUST own a sibling `<module>/module-metadata.yaml` declaring `module`, `kind ∈ {platform | domain | starter | bom | sample}`, `version`, and `semver_compatibility`. Each module MUST build and test in isolation via `mvn -pl <module> -am test`. Inter-module dependency direction is governed by Rule D-6 (`module_dep_direction`).

### .c — Contract Spine Completeness (was Rule 11)

**Enforcers**: E2, E11.

Every persistent record class committed under `agent-service/src/main/java/ascend/springai/service/runtime/{runs,idempotency}/**/*.java` (relocated from `agent-runtime-core/src/main/java/ascend/springai/service/runtime/**/*.java` per ADR-0088 rc13 dissolution) MUST declare a `String tenantId` component validated by `Objects.requireNonNull(tenantId, "tenantId is required")` in its compact constructor. Process-internal value objects exempt themselves with a `// scope: process-internal` reason comment. Activated 2026-05-18 (Wave 4 Track B) — trigger met by `Run` and `IdempotencyRecord` carrying tenantId.

### .d — Run State Transition Validity (was Rule 20)

**Enforcer**: E9 (RunStatusTransitionIT).

Every `Run.withStatus(newStatus)` mutation MUST call `RunStateMachine.validate(this.status, newStatus)` before constructing the updated record. Illegal transitions MUST throw `IllegalStateException`.

### .e — Tenant Propagation Purity (was Rule 21)

**Enforcers**: E2, E4.

No production class under `ascend.springai.service.runtime..` (main sources) may import any class under `ascend.springai.service.platform..`. The original narrow case — no import of `TenantContextHolder` — remains the specific instance most likely to be violated and is asserted independently as defence-in-depth.

## Cross-references

- ADR-0064 — Layer-0 Governing Principles authority.
- ADR-0066 — Independent Module Evolution authority.
- ADR-0078 — Phase C consolidation (resolves which "successor module" sub-clause .c refers to).
- ADR-0079 — Engine extraction to agent-runtime-core (superseded by ADR-0088).
- ADR-0088 — agent-runtime-core dissolution; runs / idempotency relocated back to agent-service (rc13).
- Companion: Rule R-J.a (Storage-Engine Tenant Isolation — sub-clause .e is the application-layer dual).
