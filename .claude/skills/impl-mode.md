---
name: impl-mode
description: |
  Load the engineering-implementation phase contract. Use this skill when:
  - Writing or editing production Java under `agent-*/src/main/java/**`.
  - Writing or editing production yaml config (`application*.yml`,
    schemas under `docs/contracts/`, `docs/governance/*.yaml`).
  - Authoring or amending a Flyway migration under `*/db/migration/`.
  - Implementing an SPI that was declared in the design phase.
  - Wiring a `@ConfigurationProperties` block, an `AutoConfiguration`,
    or a `@Bean`.
  Reads `docs/governance/contracts/engineering-implementation.md` and
  emits the phase entry checklist. Per ADR-0098, this is the default
  fallback skill when phase is ambiguous (widest coverage).
scope: project
---

# /impl-mode — Load Engineering Implementation Phase Contract

## Purpose

This skill is the implementation phase entry point. The phase
contract names the rules that bind your code edits (D-4, R-C.2,
R-G, R-H primary; D-1, D-2, D-6, D-7, D-8, D-9, G-7, R-A, R-C, etc.
as cross-references), the forbidden patterns, and the exit criteria.

This is the **default** skill when phase is ambiguous — it has the
widest active-rules set, so loading it adds the least risk of
missing a constraint.

## When to invoke

- About to write production Java, yaml, or shell code.
- About to author a Flyway migration.
- About to implement an SPI whose contract was decided in
  `/design-mode`.
- About to wire dependency injection or `@AutoConfiguration`.

## What this skill does

1. **Read** `docs/governance/contracts/engineering-implementation.md`
   and surface its content.
2. **Highlight** the Active Rules table — primary rules D-4 (testing
   structure), R-C.2 (Run contract spine), R-G (reactive I/O), R-H
   (no Thread.sleep).
3. **Surface** the forbidden-patterns block (no RestTemplate /
   JdbcTemplate, no Thread.sleep, tenantId required, version/log
   metadata banned per D-9).
4. **State** the exit criteria (unit + integration + smoke green,
   gate green, no new D-9 grandfather entries).
5. **Suggest** the next phase at exit — typically `/verify-mode`
   once the implementation lands.

## What this skill does NOT do

- Does NOT compile or run tests; you invoke `./mvnw` / `./mvnw.cmd`.
- Does NOT skip design work — if no design exists for what you are
  about to implement, branch to `/design-mode` first.
- Does NOT auto-add files to `gate/d9-grandfathered-files.txt` — the
  grandfather list is frozen; new code must be D-9-clean.

## Composes with

- `/design-mode` — branch back if no design exists for the change.
- `/verify-mode` — natural next phase after implementation lands.
- `/commit-mode` — when the implementation is verified and ready to
  ship.

## See also

- ADR-0098 — rc21 6-phase scenario-loaded contracts.
- `docs/governance/contracts/engineering-implementation.md` — the
  contract this skill loads.
- `gate/d9-grandfathered-files.txt` — the freeze list; do not extend.
