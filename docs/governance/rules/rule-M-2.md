---
rule_id: M-2
title: "Domain Contract Discipline (schema-first + design-only registration + DFX-stem truth)"
level: L0
view: development
principle_ref: P-D
authority_refs: [ADR-0077, ADR-0032, ADR-0052, ADR-0083]
enforcer_refs: [E85, E116, E127, E128]
status: active
governance_infra: true
scope_phase: design
kernel_cap: 8
kernel: |
  **Domain contract discipline (sub-clause .a): every NEW domain enum or fixed-vocabulary taxonomy in `ARCHITECTURE.md` (root or per-module) on or after 2026-05-16 MUST cite a yaml schema under `docs/contracts/` or `docs/governance/` within ±5 lines; prose `<TYPE> | <TYPE>` enums outside fenced/yaml blocks are forbidden unless schema-referenced or grandfathered in `gate/schema-first-grandfathered.txt` (sunset_date required; advancing requires inline ADR). Every `docs/contracts/*.v1.yaml` whose `status: design_only` OR `runtime_enforced: false` MUST be listed by basename in `docs/contracts/contract-catalog.md` AND cite ≥1 `ADR-NNNN` whose file exists in `docs/adr/` (sub-clause .b). Every `docs/dfx/*.yaml` (excluding `docs/archive/`) MUST have a basename stem matching a `<module>` entry in root `pom.xml` (sub-clause .c).**
deferred_sub_clauses:
  - id: ".a.b"
    title: "W3 Prose-Enum Schema-First Retrofit [Deferred to W3]"
    re_introduction_trigger: "W3 contract-design sprint kickoff (default target: 2026-09-30 — the working sunset date encoded in `gate/schema-first-grandfathered.txt`). Activates earlier if any grandfather entry's `sunset_date` is moved earlier via ADR."
    deferred_body: |
      **Rule (draft)**: Every entry remaining in `gate/schema-first-grandfathered.txt` after its declared `sunset_date` MUST be retrofitted to a yaml schema under `docs/contracts/` or `docs/governance/`. The retrofit (a) replaces the prose enum with a fenced code block referencing the schema file, (b) lands a Java enum + ctor-level schema validation per Rule M-2 sub-clause .a doctrine, (c) removes the entry from the grandfather list. ADR-0078 (or successor) MUST schedule the retrofit waves; gate Rule 60 fails closed once any entry's sunset_date passes without retrofit (the sunset-fail logic is gate-enforced as of W2.x Phase 8).

      Composes with: Rule M-2 sub-clause .a (Schema-First Domain Contracts); ADR-0077; ADR-0068 (Layered 4+1 corpus); gate Rule 60 self-tests.
    relates_to: ["ADR-0077", "ADR-0068", "ADR-0078", "Rule M-2 sub-clause .a"]
  - id: ".a.c"
    title: "EngineEnvelope Strict-Construction Validation [Deferred to W2]"
    re_introduction_trigger: "first `EngineEnvelope` construction outside a Spring-boot test harness — i.e., production code that constructs envelopes from external input (REST controller, message-bus consumer, client SDK) rather than from a programmer-controlled literal."
    deferred_body: |
      **Rule (draft)**: The `EngineEnvelope` record constructor MUST reject `engineType` values not present in `docs/contracts/engine-envelope.v1.yaml#known_engines[].id`. Today the constructor validates only nullability; `engineType` membership is enforced lazily by `EngineRegistry.resolve()` at dispatch time. W2 promotion: load `known_engines` once at JVM startup (or on first envelope construction), cache, and reject in the ctor. Rationale for the deferral: today every envelope is built inside a Spring-managed context where `EngineRegistry.validateAgainstSchema()` has already run at boot per ADR-0076; user-supplied envelopes do not yet exist.

      Composes with: Rule M-2 sub-clause .a (Schema-First Domain Contracts); Rule R-M sub-clause .b (Strict Engine Matching); ADR-0072; ADR-0076.
    relates_to: ["ADR-0072", "ADR-0076", "Rule M-2 sub-clause .a", "Rule R-M sub-clause .b"]
  - id: ".legacy28kb"
    title: "(legacy Rule 28k.b) Schema-Java-Shape Parity ArchUnit [Deferred to W3]"
    re_introduction_trigger: "W3 contract-design sprint kickoff — the same trigger as Rule M-2 sub-clause .a.b, since both close the structural gap surfaced by the v2.0.0-rc1 second-pass review F-α category audit."
    deferred_body: |
      **Rule (draft)**: For every `docs/contracts/*.v1.yaml` schema that declares a `required_fields:` or `hooks:` (or equivalent ordered enum) block AND a paired Java type whose name is named in the YAML header comment, an ArchUnit test MUST assert bidirectional shape parity:
      - Every Java record component / enum constant has a matching YAML entry (no extra Java symbols).
      - Every YAML entry has a matching Java record component / enum constant (no extra YAML entries).

      **Background**: The W2.x wave shipped two strong parity enforcers — E77 (`engine_registry_covers_all_known_engines`: bidirectional `known_engines[].id` ↔ `ENGINE_TYPE`) and E78 (`engine_hooks_yaml_present_and_wellformed`: bidirectional `hooks:` list ↔ `HookPoint` enum). Three other mirror claims ship only schema-presence checks, not shape parity:
      - `EngineEnvelope` record `required_fields:` ↔ Java record components (only nullability validated today, partially covered by E76+E84).
      - `engine-hooks.v1.yaml` hook order ↔ `HookPoint` enum declaration order (E78 checks set membership, not order — though order is asserted in `engine-hooks.v1.yaml#ordering: declared`).
      - `evolution-scope.v1.yaml` discriminators ↔ `EvolutionExport` enum constants (E87 is armed-empty until W2 RunEvent variants ship).

      The rc2 second-pass review flagged these as P1 F-α instances (parity claims without binding cross-check). The reviewer's narrowed wording closes them at the prose level; this deferral records the structural fix.

      Composes with: Rule R-C.a (Code-as-Contract Coverage); Rule M-2 sub-clause .a (Schema-First Domain Contracts); ADR-0072; ADR-0073; ADR-0075; v2.0.0-rc1 second-pass review F-α category audit.
    relates_to: ["ADR-0072", "ADR-0073", "ADR-0075", "ADR-0086", "legacy Rule 28k.b", "Rule M-2 sub-clause .a", "Rule R-C sub-clause .a"]
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
