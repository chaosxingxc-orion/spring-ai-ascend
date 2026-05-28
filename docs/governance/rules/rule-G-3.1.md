---
rule_id: G-3.1
title: "Kernel-Implementation Disjunction Truth"
level: L0
view: scenarios
principle_ref: P-B
authority_refs: [ADR-0085, ADR-0094]
enforcer_refs: [E141, E142]
status: active
governance_infra: true
scope_phase: commit
kernel_cap: 6
scope_surfaces:
  - CLAUDE.md kernel paragraphs
  - docs/governance/rules/rule-*.md card kernels
  - gate/rule-100-disjunction-allowlist.txt
kernel: |
  **Rules listed in `gate/rule-100-disjunction-allowlist.txt` MUST carry explicit EITHER/OR connective wording in BOTH the CLAUDE.md kernel AND the matching `docs/governance/rules/rule-*.md` card. The allow-list captures only rules whose `||`-style disjunction is structurally load-bearing — meaning the difference between AND-implementation and OR-implementation would change which corpus inputs pass.**
---

# Rule G-3.1 — Kernel-Implementation Disjunction Truth

Split out from Rule G-3.f in the rc17 wave (per ADR-0094) to separate
the **grammar invariant** (disjunction truth, this rule) from the
**structural-coherence invariant** (kernel↔card↔deferred, Rule G-3).
Originally added in rc11 as Rule G-3.f per ADR-0085.

## Motivation

The rc10 post-corrective review (P1-3) noted that Rule G-3.d's kernel was
narrow (`MUST contain` on the CLAUDE.md kernel block) while its
implementation was broad (accepted EITHER the kernel OR the rule card).
This is the worst class of Code-as-Contract drift: a rule whose JOB is
preventing kernel/deferred drift contains kernel/impl drift of its own.

The rc11 wave (per ADR-0085) chose to keep the broader "either surface"
implementation (cards have no kernel_cap, so a long deferred discussion
can live there without bloating CLAUDE.md) and align the kernel + card
wording to match. This rule prevents recurrence by enforcing the
bidirectional declaration: for every rule whose impl uses `||`-style
disjunction on a structurally-important predicate, the kernel AND the
card MUST both carry the EITHER/OR wording.

## Why allow-list scope, not corpus-wide

A fully-general "scan every `_rNN_*` block for `&&` vs `||` and
cross-check against the kernel's `AND`/`OR` connective" rule is fragile:

- Bash predicate grammar varies (`[[ ... && ... ]]`, `[[ ... ]] || [[ ... ]]`,
  multi-stage checks via temp variables).
- Some rules use multi-stage checks where the surface AND-vs-OR doesn't
  map cleanly to a single connective.
- Many `&&`/`||` joins are structural (`[[ $? -eq 0 ]] || _fail=1`), not
  semantically load-bearing.

The allow-list captures only rules where the disjunction is *structurally
load-bearing* — meaning the difference between AND-implementation and
OR-implementation would change which corpus inputs pass.

Initial allow-list (rc11, unchanged at rc17):

- Rule G-3.d — `kernel_deferred_clause_coherence` (CLAUDE.md kernel block
  OR rule card).

Future additions surfaced by sweep families:

- Rule M-2.a (yaml schema OR grandfather entry) — needs verification.
- Rule G-3.c (active heading OR deferred reference) — needs verification.
- Rule R-D.g (catalog row OR `(internal)` mark) — needs verification.

Each addition requires kernel + card to explicitly declare EITHER/OR
wording before the rule id lands in the allow-list. A new addition
without kernel/card alignment will fail Rule G-3.1 on its first run.

## Algorithm

1. Read `gate/rule-100-disjunction-allowlist.txt` (one rule id per line,
   `#` comments).
2. For each rule N in the allow-list:
   - Extract the `#### Rule N` block from `CLAUDE.md`.
   - Read the matching `docs/governance/rules/rule-NN.md` card.
   - Test BOTH for explicit disjunction tokens: `EITHER` (uppercase), `OR`
     (uppercase word), `either surface`, `either ... or`, `either kernel`,
     `either the`.
3. If either surface lacks the disjunction wording → FAIL with the rule
   id + (kernel=Y/N, card=Y/N) tally.

## Enforcement

Enforced by E141 (Gate Rule 100 — `kernel_implementation_disjunction_truth`)
+ E142 (negative self-test fixture).

Positive self-test: a Rule N kernel + card both saying "EITHER the
kernel OR the rule card" → PASS.

Negative self-test: a Rule N kernel saying "the kernel block MUST
contain" with `Rule N` added to the allow-list → FAIL (kernel=0, card=?).

## Activation history

- 2026-05-19 (rc11 per ADR-0085) — original Rule G-3.f activation.
- 2026-05-21 (rc17 per ADR-0094) — extracted from Rule G-3 into a
  standalone rule. The disjunction-grammar invariant is structurally
  distinct from kernel-card structural-coherence; splitting improves
  auditability without changing the gate behaviour. Gate Rule 100 number
  is unchanged.

## Cross-references

- ADR-0085 — rc11 kernel-truth + shadow-corpus-precision authority.
- ADR-0094 — rc17 rule-consolidation authority (this split).
- Rule G-3 — Kernel-Card-Implementation Coherence (sibling structural
  invariant; G-3.1 covers the grammar layer).
- Rule G-3.d — first allow-list entry; the rule whose kernel-AND-impl-OR
  drift this prevention rule catches.
- Rule G-3.e — sibling semantic-verb invariant for deferred sub-clauses.
- `gate/rule-100-disjunction-allowlist.txt` — the canonical allow-list.
- `docs/logs/reviews/2026-05-19-l0-rc10-post-corrective-architecture-review.en.md`
  finding P1-3 — origin.
