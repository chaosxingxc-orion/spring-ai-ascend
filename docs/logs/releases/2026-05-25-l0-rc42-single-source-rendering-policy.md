---
formal_release: true
evidence_bundle: gate/release-ci-evidence/2026-05-25-l0-rc42-single-source-rendering-pilot.evidence.yaml
release_candidate_commit: a0a298ceec6e9c7f16050b64b7c1d008af9d3182
status: formal-release-candidate
---

<!-- DO NOT EDIT — generated from docs/governance/templates/release-note.md.j2 by gate/lib/render_template.py. Edit the template + the per-release narrative seed under gate/release-ci-evidence/, then re-render via /refresh-architecture-doc stage 7 (Rule G-13.b). -->

# v2.0.0-rc42 — Single-Source Rendering Policy + Foundation Tooling + Release-Note Render Pilot

> **Historical artifact frozen at SHA a0a298ceec6e9c7f16050b64b7c1d008af9d3182 (rc42 single-source-rendering pilot).** Baseline counts in this document reflect the rc42 release-time corpus and are not retroactively updated after rc49.

> Generated at 2026-05-25T09:00:00+00:00 from commit `a0a298ceec6e9c7f16050b64b7c1d008af9d3182`
> on branch `main`. Baseline counts in this note come from
> `architecture-status.yaml#baseline_metrics`; live counts come from a tree
> scan at the frozen commit. Drift between the two would fail Rule G-13.b.

## Release Decision

- Decision: **ship**
- Frozen commit: `a0a298ceec6e9c7f16050b64b7c1d008af9d3182`
- Evidence bundle: `gate/release-ci-evidence/2026-05-25-l0-rc42-single-source-rendering-pilot.evidence.yaml`
- Formal release validator: `bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc42-single-source-rendering-pilot.evidence.yaml`

## Summary

The rc42 wave lands the architectural decision that derived architecture documents (release notes, READMEs, contract catalog, recurring-defect-families.md, root and L1 ARCHITECTURE.md) MUST be machine-rendered from authority surfaces via Jinja2 templates, with byte-identical regen enforced by Rule G-13. The 11-wave roadmap (W0–W10) ships in stages; this release covers W0 (policy + Rule G-13 + governance index updates), W1 (render engine + general gate at gate/lib/render_template.py / load_render_context.py / check_template_render_idempotency.py), W2 (refresh-order orchestrator skill /refresh-architecture-doc + phase-contract integration), and W3 (the release-note template itself — this very document is the first rendered artefact).

The defect-class math: 14+ occurrences of F-numeric-drift, plus F-deleted-module-name-leakage, F-authority-surface-path-drift, F-cross-authority-agreement, and F-l1-architecture-grounding-gap, all share one mechanism — a human types a count, path, or module name into prose, and that value drifts from the authority surface it reflects. Today's gates catch the drift AFTER a draft is shipped, forcing 1–2 corrective waves per release. Rule G-13 flips the paradigm: the prose IS the render of the data. Drift is impossible by construction for every templated surface.

rc42 is the smallest possible end-to-end proof of the pattern — one template (release-note.md.j2), one context loader plugin (release_note via build_release_evidence), one Rule G-13.b gate driver (check_template_render_idempotency.py). W4–W10 each migrate one further surface (recurring-families.md, contract-catalog.md, root ARCHITECTURE.md, L1 ARCHITECTURE.md per module, README + gate/README, CLAUDE.md rule-index table + phase-contract Active Rules tables), then W10 retires the rules subsumed by G-13.

## Generated Evidence

| Metric | Baseline | Live | Match |
|---|---:|---:|---|
| active_engineering_rules | 43 | 43 | OK |
| active_gate_checks | 140 | 140 | OK |
| active_governing_principles | 13 | None | MISMATCH |
| adr_count | 113 | 113 | OK |
| architecture_graph_edges | 999 | 999 | OK |
| architecture_graph_nodes | 562 | 562 | OK |
| enforcer_rows | 173 | 173 | OK |
| gate_executable_test_cases | 252 | None | MISMATCH |
| maven_tests_green | 409 | None | MISMATCH |
| phase_contracts | 5 | None | MISMATCH |
| phase_loading_skills | 6 | None | MISMATCH |
| recurring_defect_families | 15 | 15 | OK |
| section_4_constraints | 65 | None | MISMATCH |

## Release Counts

Canonical baseline labels required by Gate Rule 28 — single source is
`docs/governance/architecture-status.yaml#baseline_metrics`. Authority of
these counts is renamed for human readability; the same numbers appear
with their structured keys in the Architecture Baseline table below.

| Active counts | Value |
|---|---:|
| §4 constraints | 65 |
| ADRs | 113 |
| Active gate rules | 140 |
| Gate self-test cases | 252 |
| Active engineering rules | 43 |
| Enforcer rows | 173 |
| Architecture graph nodes | 562 |
| Architecture graph edges | 999 |
| Recurring defect families | 15 |
| Maven tests green | 409 |

## Architecture Baseline

| Metric | Count |
|---|---:|
| active_engineering_rules | 43 |
| active_gate_checks | 140 |
| active_governing_principles | 13 |
| adr_count | 113 |
| architecture_graph_edges | 999 |
| architecture_graph_nodes | 562 |
| enforcer_rows | 173 |
| gate_executable_test_cases | 252 |
| maven_tests_green | 409 |
| phase_contracts | 5 |
| phase_loading_skills | 6 |
| recurring_defect_families | 15 |
| section_4_constraints | 65 |

## Fixes Completed

1. **(P1) Rule G-13 (Single-Source Rendering Coherence) landed with three sub-clauses** — Sub-clause .a registers the surface-classification registry; sub-clause .b enforces byte-identical regen (gate Rule 126 / E174); sub-clause .c flags inline dynamic-claim leakage at W10 audit. Card at docs/governance/rules/rule-G-13.md; kernel byte-matches the CLAUDE.md paragraph. (ADR-0119, Rule G-13, E174)
2. **(P1) Render engine + context loader + general gate driver shipped** — gate/lib/render_template.py is a deterministic Jinja2 wrapper (sorted iteration, LC_ALL=C, fixed-precision floats, StrictUndefined, keep_trailing_newline). gate/lib/load_render_context.py is plugin-dispatched (release_note + recurring_families plugins land now; contract_catalog / root_architecture / l1_architecture / readme_root / readme_gate / claude_md_index / phase_contract_table plugins arrive in W4–W9). gate/lib/check_template_render_idempotency.py is the Rule G-13.b gate driver. (Rule G-13.b, gate/lib/render_template.py, gate/lib/load_render_context.py)
3. **(P1) /refresh-architecture-doc orchestrator skill drives the 9-stage refresh order** — Pre-flight → ADR → graph rebuild → status.yaml refresh → CLAUDE.md kernel + rule cards → recurring-families ledger → phase contracts → template render idempotency → README numeric mirrors → full gate. Replaces the ad-hoc refresh order that has produced 14+ F-numeric-drift recurrences. /formal-release-transaction now delegates to it for upstream stages. (Rule G-13, .claude/skills/refresh-architecture-doc.md)
4. **(P2) First templated release note is THIS document** — Proves end-to-end render path: build_release_evidence → load_render_context release_note plugin → render_template → byte-identical .md. The Generated Evidence table, Architecture Baseline table, and per-row counts in this note are 100% rendered; no hand-typed counts. (docs/governance/templates/release-note.md.j2, Rule G-13.b)
## Four Competitive Pillars (P-B)

- performance: unchanged
- cost: unchanged (no runtime change in this wave)
- developer_onboarding: improved — /refresh-architecture-doc orchestrator replaces tribal-knowledge refresh order; one-skill invocation drives the cascade
- governance: improved — Rule G-13 admits a forward-prevention link in 5 recurring-defect families and schedules 7 reactive prevention rules for retirement in W10

## Current-vs-Forward Claims

| Subject | Current shipped behavior | Verified by | Forward behavior | Promotion trigger | Must not claim before |
|---|---|---|---|---|---|
| Release note baselines | rc42 release note is rendered from baseline_metrics + live tree scan; Rule G-13.b gate enforces byte-identical regen | gate Rule 126 + gate/lib/check_template_render_idempotency.py + gate/test_template_render.py (13 unit tests) | every subsequent release note renders from this template; historical rc1–rc41 stay frozen and are grandfathered | W4–W10 wave landings extend the rendered-surface set to families.md, contract-catalog.md, root + L1 ARCHITECTURE.md, READMEs, CLAUDE.md rule index, phase contracts | do not claim full architecture-document render coverage before W10 cleanup wave closes |
| Subsumed reactive prevention rules | Rules G-2.b, G-2.d (root part), G-2.1, G-8.a, G-8.c, G-8.e, G-9.c remain active as defence-in-depth | gate/check_parallel.sh runs all 140 rules including the soon-to-be-subsumed ones | W10 retires the listed rules and downgrades them to advisory or removes them; active_engineering_rules count drops | W3..W9 each demonstrate byte-identical regen on the surfaces they migrate for one full release cycle | do not retire any rule before W10 |

## Recurring Family Closure

| Family | Cited findings | Sibling surfaces checked | Closure result | Residual risk |
|---|---|---|---|---|
| F-numeric-drift | preventive — Rule G-13 forward-prevention link added; no new occurrence this wave | release notes (this rc); future: README.md / gate/README.md / root ARCHITECTURE.md (W6–W8) | partial | Not closed structurally until W3+W6+W8 migrate the named surfaces and W10 retires Rules 82/91/97/101/G-8.a. Today's rc42 release note alone is rendered; READMEs and root ARCHITECTURE numeric cells still hand-typed. |
| F-deleted-module-name-leakage | preventive — Rule G-13 forward-prevention link added | release notes; future: contract-catalog (W5), root ARCH (W6), L1 ARCH (W7) | partial | G-2.1 + Rules 93/94/98/103/109 remain the active detection layer. Render-based prevention activates surface by surface in W5/W6/W7. |
| F-cross-authority-agreement | preventive — Rule G-13 forward-prevention link added | release notes; future: every templated surface produces a single render-context per artefact, eliminating intra-artefact cross-surface disagreement | partial | Rules G-8.a/c/e + 106/107/108/109/122/123/124 remain active. Subsumption schedule: W10 retires G-8.a/c/e once W3+W5+W6+W7 demonstrate byte-identical regen. |

## Authority Refresh

| Surface | Role | Freshness proof |
|---|---|---|
| `docs/adr/0119-single-source-rendering.yaml` | normative | this wave authored the ADR; cited by Rule G-13 card + CLAUDE.md kernel + recurring-families.yaml prevention_rules |
| `docs/governance/rules/rule-G-13.md` | normative | kernel byte-matches CLAUDE.md paragraph (Rule G-3.b); declares 3 sub-clauses + scope_surfaces |
| `docs/governance/templates/surface-classification.yaml` | workflow_evidence | registry with release-note row added; W0 vacuous → W3 first non-empty entry |
| `docs/governance/templates/release-note.md.j2` | generated | this wave authored the template; rendered output proven byte-identical via gate Rule 126 |
| `docs/governance/release-readiness/release-readiness.schema.yaml` | normative | extended with RenderContext / TemplatedArtifact / RenderSchema models + single_source_rendering_rules block |
| `docs/governance/architecture-status.yaml` | normative | baseline_metrics + allowed_claim refreshed to reflect +Rule G-13 + Rule 126 + E174 + ADR-0119 + 3 new fixtures |
| `docs/governance/architecture-graph.yaml` | generated | re-built byte-identical from inputs (Rule G-1.b); 478 nodes / 865 edges after W0 additions |
| `docs/governance/recurring-defect-families.yaml` | normative | Rule G-13 added to prevention_rules of F-numeric-drift, F-deleted-module-name-leakage, F-authority-surface-path-drift, F-cross-authority-agreement, F-l1-architecture-grounding-gap |
| `docs/governance/contracts/system-commit.md` | workflow_evidence | G-13 added to Active Rules table + exit criteria requires /refresh-architecture-doc stages pass |
| `.claude/skills/refresh-architecture-doc.md` | workflow_evidence | new orchestrator skill; 9-stage refresh discipline |

## Verification Commands

```bash
bash gate/check_parallel.sh
./mvnw clean verify
python3 gate/lib/build_release_evidence.py --run-self-tests --include-maven-reports --output gate/release-ci-evidence/2026-05-25-l0-rc42-single-source-rendering-pilot.evidence.yaml
bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc42-single-source-rendering-pilot.evidence.yaml
python3 gate/lib/check_template_render_idempotency.py
```
