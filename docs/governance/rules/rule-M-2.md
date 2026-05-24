---
rule_id: M-2
title: "Domain Contract Discipline (schema-first + design-only registration + DFX-stem truth)"
level: L0
view: development
principle_ref: P-D
authority_refs: [ADR-0077, ADR-0032, ADR-0052, ADR-0083]
enforcer_refs: [E85, E116, E127, E128]
status: active
scope_phase: design
kernel_cap: 8
kernel: |
  **Domain contract discipline (sub-clause .a): every NEW domain enum or fixed-vocabulary taxonomy in `ARCHITECTURE.md` (root or per-module) on or after 2026-05-16 MUST cite a yaml schema under `docs/contracts/` or `docs/governance/` within ±5 lines; prose `<TYPE> | <TYPE>` enums outside fenced/yaml blocks are forbidden unless schema-referenced or grandfathered in `gate/schema-first-grandfathered.txt` (sunset_date required; advancing requires inline ADR). Every `docs/contracts/*.v1.yaml` whose `status: design_only` OR `runtime_enforced: false` MUST be listed by basename in `docs/contracts/contract-catalog.md` AND cite ≥1 `ADR-NNNN` whose file exists in `docs/adr/` (sub-clause .b). Every `docs/dfx/*.yaml` (excluding `docs/archive/`) MUST have a basename stem matching a `<module>` entry in root `pom.xml` (sub-clause .c).**
---

# Rule M-2 — Domain Contract Discipline

Operationalises the P-D (SPI-Aligned, DFX-Explicit, Spec-Driven, TCK-Tested) principle on the contract-text surface.

## Sub-clauses

### .a — Schema-First Domain Contracts (was Rule 48)

**Enforcer**: E85 (Gate Rule 60).

Every NEW domain enum or fixed-vocabulary taxonomy introduced in `ARCHITECTURE.md` (root) or `agent-*/ARCHITECTURE.md` (per-module) on or after 2026-05-16 MUST cite a yaml schema under `docs/contracts/` or `docs/governance/` within ±5 lines of the prose definition. Prose-defined enums of the shape `<TYPE> | <TYPE>` (uppercase identifiers separated by pipes) outside fenced code blocks (` ``` `) and yaml blocks are forbidden unless either (a) the section also references such a yaml schema or (b) the file is listed with a matching prefix in `gate/schema-first-grandfathered.txt`. The grandfather list is closed to new additions; every entry MUST declare a `sunset_date` (format `YYYY-MM-DD`) in the second pipe-delimited field. Gate Rule 60 fails closed once today's date exceeds any entry's sunset_date without retrofit; advancing a sunset_date forward requires an ADR cited inline in the entry description. Per-entry retrofit triggers and the default sunset schedule are documented in `CLAUDE-deferred.md` 48.b.

### .b — Design-Only Contract Registered in Catalog (was Rule 83)

**Enforcer**: E116.

Every `docs/contracts/*.v1.yaml` whose `status:` value is `design_only` OR whose `runtime_enforced:` is `false` MUST (a) be listed by file basename in `docs/contracts/contract-catalog.md`, AND (b) cite at least one `ADR-NNNN` whose file exists under `docs/adr/`.

### .c — DFX Stem Matches Module (was Rule 93)

**Enforcers**: E127, E128.

Every `docs/dfx/*.yaml` file (excluding `docs/archive/`) MUST have a basename stem matching a `<module>` entry in root `pom.xml`. Prevents orphan DFX files surviving module deletion (e.g., `docs/dfx/agent-platform.yaml` after the Phase-C consolidation).

## Deferred sub-clauses

- 48.b — Per-entry retrofit triggers + default sunset schedule (CLAUDE-deferred.md).
- Rule M-2 sub-clause .a.b — W3 prose-enum schema-first retrofit (CLAUDE-deferred.md).
- Rule M-2 sub-clause .a.c — EngineEnvelope strict-construction validation (CLAUDE-deferred.md).

Rule G-3 sub-clause .d (`kernel_deferred_clause_coherence`) asserts the bidirectional link between this active rule and each deferred sub-clause.

## Cross-references

- ADR-0077 — schema-first domain contracts authority.
- ADR-0032 + ADR-0052 — design-only contract registration anchor.
- ADR-0083 — DFX-stem orphan detection authority.
