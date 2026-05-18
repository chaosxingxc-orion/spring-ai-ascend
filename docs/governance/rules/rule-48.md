---
rule_id: 48
title: "Schema-First Domain Contracts"
level: L0
view: development
principle_ref: P-M
authority_refs: [ADR-0077]
enforcer_refs: [E85]
status: active
kernel_cap: 8
kernel: |
  **Every NEW domain enum or fixed-vocabulary taxonomy introduced in `ARCHITECTURE.md` (root) or `agent-*/ARCHITECTURE.md` (per-module) on or after 2026-05-16 MUST cite a yaml schema under `docs/contracts/` or `docs/governance/` within ±5 lines of the prose definition. Prose-defined enums of the shape `<TYPE> | <TYPE>` (uppercase identifiers separated by pipes) outside fenced code blocks (` ``` `) and yaml blocks are forbidden unless either (a) the section also references such a yaml schema or (b) the file is listed with a matching prefix in `gate/schema-first-grandfathered.txt`. The grandfather list is closed to new additions; every entry MUST declare a `sunset_date` (format `YYYY-MM-DD`) in the second pipe-delimited field. Gate Rule 60 fails closed once today's date exceeds any entry's sunset_date without retrofit; advancing a sunset_date forward requires an ADR cited inline in the entry description. Per-entry retrofit triggers and the default sunset schedule are documented in `CLAUDE-deferred.md` 48.b.**
---

## Motivation

This rule codifies the W2.x doctrine "yaml schema → Java type → runtime self-validate" into a permanent engineering rule. Defect family F1 (text-drift between prose taxonomies and Java enums / yaml schemas) accounts for 79 of 158 historical closed defects (~50%). Every prior wave closed individual F1 instances by hand; none codified the structural prohibition that prevents recurrence. Rule 33 (Layered 4+1 Discipline) and Rule 34 (Architecture-Graph Truth) gave the corpus its STRUCTURAL substrate (level × view × graph indices); Rule 48 is the LEAF-LEVEL companion — structure says WHERE a constraint lives, Rule 48 says WHAT SHAPE its taxonomy takes.

## Cross-references

- Authority: ADR-0077 / P-M cross-cutting invariant.
- Enforced by Gate Rule 60 (`schema_first_domain_contracts`, enforcer E85) with self-tests positive + negative.
- Grandfather list: `gate/schema-first-grandfathered.txt` (closed to new additions; sunset_date required per entry).
- Companion W2.x contracts ADR-0072 (engine envelope, Rules 55/56) and ADR-0073 (engine hooks, Rule 57) are the first two domain enums to follow the schema-first shape.
- Companion rule: Rule 33 ([`rule-33.md`](rule-33.md)) — Layered 4+1 Discipline (structure says WHERE).
- Companion rule: Rule 34 ([`rule-34.md`](rule-34.md)) — Architecture-Graph Truth (graph encodes the joins).
- Deferred sub-clauses: Rule 48.b (per-entry retrofit triggers and default sunset schedule), Rule 48.c (constructor-level membership validation for `EngineEnvelope`; re-introduction trigger: first envelope built outside the Spring-boot test harness). Rule 96 (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.
