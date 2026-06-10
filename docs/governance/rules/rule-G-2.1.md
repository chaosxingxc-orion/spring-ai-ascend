---
rule_id: G-2.1
title: "Deleted-Module Scope Prevention"
level: L0
view: scenarios
principle_ref: P-D
authority_refs: ["ADR-0078 (retired)", ADR-0083, ADR-0084, "ADR-0094 (retired)"]
enforcer_refs: [E120, E129, E130, E137, E138, E154]
status: active
governance_infra: true
scope_phase: commit
kernel_cap: 8
scope_surfaces:
  - docs/governance/architecture-status.yaml#allowed_claim
  - "agent-*/ARCHITECTURE.md"
  - "docs/governance/rules/rule-*.md"
  - "agent-*/src/test/java/**/*{Test,IT}.java Javadoc"
  - "docs/contracts/*.yaml"
  - "**/module-metadata.yaml"
  - Dockerfile
  - ".github/workflows/*.yml"
  - "docs/**/*.puml"
kernel: |
  **Deleted-module-name leakage across the active corpus: `architecture-status.yaml#allowed_claim` text (sub-clause .a, from former G-2.e), every active `.md/.yaml/.yml/.java` outside historical-by-location exemptions (sub-clause .b, from former G-2.f), AND files under `docs/contracts/*.yaml` / `**/module-metadata.yaml` / Dockerfile / `.github/workflows/*.yml` / `docs/**/*.puml` (sub-clause .c, from former G-2.h + Rule 103) MUST NOT contain current-tense pre-Phase-C module names (`agent-platform`, `agent-runtime` with negative lookahead on `agent-runtime-core`) outside marker windows listed in `gate/active-corpus-name-exemption-markers.txt`; file-path exemptions in `gate/active-corpus-name-exemption-paths.txt`. Detection uses the word-boundary regex `\bagent-platform\b` OR (`\bagent-runtime\b` AND NOT `\bagent-runtime-core\b`) with ±3-line historical-marker scan.**
---

# Rule G-2.1 — Deleted-Module Scope Prevention

Consolidated in the rc17 wave (per ADR-0094) from the rc6/rc9/rc10/rc12
prevention chain: Rule G-2.e (`status_yaml_allowed_claim_module_name_truth`),
Rule G-2.f (`active_corpus_deleted_module_name_truth`), Rule G-2.h
(`broad_corpus_deleted_module_name_truth`), Gate Rule 103
(`deploy_entrypoint_deleted_module_truth`), and Gate Rule 109
(`namespaced_rule_reference_completeness` — partial overlap).

## Motivation

Across rc9 → rc16 the F-deleted-module-name-leakage family fired six
times, each time finding a new file partition the previous prevention
rule did not scan. The chronology demonstrates "reviewer scope < defect
scope":

- rc6 (Rule G-2.e) — narrow to `architecture-status.yaml#allowed_claim` field.
- rc9 (Rule G-2.f) — widened to `.md/.yaml/.java` in three specific surfaces.
- rc10 (Rule G-2.h) — sibling rule, widened to `ops/`, `docs/contracts/`, `**/module-metadata.yaml`.
- rc11 (Rule G-2.f widening) — expanded `.f` from narrow find to "scan everything minus exemption list".
- rc12 (Rule 103) — added deploy entrypoints (Dockerfile, .github/workflows, .puml).
- rc16 (Rule 109) — added namespaced rule-reference layer.

The rc17 consolidation collapses these into one rule with three
sub-clauses, plus thin wrapper sections in `gate/check_architecture_sync.sh`
that retain the existing gate Rule 94 / 98 / 109 numbers and their
self-test fixtures (no behaviour change, just clearer kernel-card
attribution).

## Sub-clauses

### .a — Status YAML Allowed Claim Truth (was G-2.e)

**Enforcer**: E120 (positive + negative self-tests).

For each `^\s+allowed_claim:\s*<value>` line in
`docs/governance/architecture-status.yaml`:

1. Grep the value for `\bagent-platform\b` OR (`\bagent-runtime\b` AND
   NOT `\bagent-runtime-core\b`).
2. If a stale match is found, scan ±3 lines for any historical marker
   (`historical`, `moved`, `formerly`, `extracted per ADR-NNNN`,
   `superseded`, `deferred`, `pre-ADR-NNNN`, `pre-Phase-C`,
   `consolidated`, `merged`, `archived`, `deprecated`).
3. If no marker is found, fail with the offending line + stale module
   name.

The negative lookahead on `agent-runtime-core` is the important detail:
ADR-0079 introduced a new module whose name starts with the substring
`agent-runtime`. A naive substring search would false-fire on every
reference to the new module.

### .b — Active Corpus Truth (was G-2.f)

**Enforcers**: E129 (positive self-test) + E130 (negative self-test).

For each `.md`, `.yaml`, `.yml`, `.java` file in the active corpus
(minus the build-artefact + historical-by-location + frozen-release
exemption list in `gate/check_architecture_sync.sh`):

1. Track fenced-code-block state (`^\`\`\``).
2. Skip yaml comment lines (`^[[:space:]]*#`).
3. For each remaining line, test the word-boundary regex.
4. On a match, ±3-line marker scan; if no marker, flag as violation.

Exemption paths declared in `gate/active-corpus-name-exemption-paths.txt`;
exemption markers in `gate/active-corpus-name-exemption-markers.txt`.

### .c — Broad Corpus Truth (was G-2.h + Rule 103)

**Enforcers**: E137 (positive self-test) + E138 (negative self-test).

Scans the surfaces NOT covered by sub-clause .b:

- `docs/contracts/*.yaml` — live API contracts.
- `**/module-metadata.yaml` (any depth ≤ 3) — per-module metadata.
- `Dockerfile` — deploy entrypoint.
- `.github/workflows/*.yml` — CI workflow.
- `docs/**/*.puml` — architecture diagrams.

The exemption marker list includes `forbidden_dependencies` so that
intentional `forbidden_dependencies: - agent-platform` entries in
`agent-bus/module-metadata.yaml` etc. pass — those lists exist precisely
to NAME deleted modules and prevent reintroduction.

## Algorithm sharing

All three sub-clauses share the same word-boundary regex and ±3-line
marker scan. The DIFFERENCE between sub-clauses is file-discovery scope.
Wrapper sections in `gate/check_architecture_sync.sh` retain the legacy
Rule 94 / 98 / 109 numbers for traceability — they delegate the
regex+marker logic to one shared helper but apply different `find`
filters.

## Activation history

- 2026-05-18 (rc6 per ADR-0080) — original Rule G-2.e activation.
- 2026-05-19 (rc9 per ADR-0083) — Rule G-2.f activation.
- 2026-05-19 (rc10 per ADR-0084) — Rule G-2.h activation; Rule G-2.f
  widened from narrow find to broad scan.
- 2026-05-19 (rc12 per ADR-0086) — Rule 103 activation for deploy
  entrypoints.
- 2026-05-20 (rc16 per ADR-0093) — Rule 109 namespace ratchet.
- 2026-05-21 (rc17 per ADR-0094) — consolidated into Rule G-2.1.
  Existing gate Rule numbers (94/98/103/109) retained as thin wrappers;
  self-test fixtures (E129/E130/E137/E138/E154) unchanged. No behaviour
  delta — the consolidation is taxonomic only.

## Why consolidate

The four prior sub-rules (G-2.e/.f/.h + Rule 103 + Rule 109's
deleted-name layer) all enforce ONE invariant — "no deleted-module name
in current-tense active prose" — but they were authored in five waves
across two namespaces (G-2.x semantic + numeric Rule N gate). Reviewers
had to know all five to assess "is this surface covered?" The
consolidation makes the answer one sentence: "Rule G-2.1.{a|b|c} covers
the surfaces listed in `scope_surfaces:` frontmatter."

## Enforcement

Enforced by gate Rules 94 (sub-clause .b), 98 (sub-clause .c subset),
103 (sub-clause .c subset), 109 (sub-clause .c subset). Self-tests
E120/E129/E130/E137/E138/E154 cover positive + negative fixtures across
all three sub-clauses.

## Cross-references

- ADR-0078 — Phase-C consolidation (the structural deletion that creates
  the leakage surface).
- ADR-0083 — rc9 corpus-truth wave; original G-2.f authority.
- ADR-0084 — rc10 corpus-truth + prevention-widening; original G-2.h
  authority.
- ADR-0094 — rc17 rule-consolidation authority (this consolidation).
- Rule G-2 — Authority-Text Reality (sibling; G-2 covers per-surface
  self-consistency, G-2.1 covers deleted-name leakage across surfaces).
- Rule R-M.f — S2cCallbackSignal Historical-Only in Authority (shares
  the marker-convention family for deleted-type references).
- Rule 110 META — `prevention_rule_scope_completeness` (gates the
  `scope_surfaces:` frontmatter declaration this rule uses).
- Recurring family: [`F-deleted-module-name-leakage`](../recurring-defect-families.md#f-deleted-module-name-leakage-deleted-module-name-leakage-after-refactor).
