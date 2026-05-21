---
rule_id: G-11
title: "Phase-Contract Rule-Allocation Coherence"
level: L0
view: process
principle_ref: P-C
authority_refs: ["ADR-0098"]
enforcer_refs: [E165]
status: active
scope_phase: commit
kernel_cap: 10
scope_surfaces:
  - "docs/governance/contracts/*.md"
  - "docs/governance/rules/rule-*.md"
  - "docs/governance/principles/P-*.md"
kernel: |
  **Phase contract ↔ rule allocation coherence: every Active Rules row in `docs/governance/contracts/*.md` MUST reference a rule card that exists under `docs/governance/rules/rule-*.md` (or a principle card under `docs/governance/principles/P-*.md`). Conversely, every active rule card MUST appear in at least one phase contract — either marked **P** (primary phase) in exactly one contract OR marked **X** (cross-reference) in at least one contract. A rule with NO primary phase is an orphan rule; a rule cited in a contract whose card is missing is a ghost rule. Dual-P exception (e.g. G-9 carrying P in both `system-commit.md` and `review-response.md`) is permitted but MUST be enumerated in ADR-0098 §rule-allocation-map.**
---

## Motivation

ADR-0098 (rc21) split rule discovery into phase contracts. Before the
split, every rule's kernel sat in CLAUDE.md, so "is this rule
active?" had one answer (heading-present). After the split, a rule's
phase membership lives in N=5 contract tables, and the coherence
question splits into two:

- **Forward**: every contract row MUST resolve to a real card.
- **Reverse**: every card MUST be cited in ≥1 contract.

Both directions are necessary. Forward-only would allow orphan cards
(rule exists but no phase claims it — invisible at work time).
Reverse-only would allow ghost contracts (table row pointing to a
deleted card — broken link).

G-11 is the meta-rule that polices both directions. It is the
equivalent of Rule G-3.c (kernel-card existence) for the post-rc21
contract layer.

## What is forbidden

- A row in `docs/governance/contracts/<phase>.md` Active Rules table
  whose linked card path does NOT exist on disk.
- An active rule card under `docs/governance/rules/rule-*.md` that
  is NOT cited as **P** or **X** in any phase contract.
- A rule cited as **P** in two contracts WITHOUT a dual-P
  enumeration in `docs/adr/0098-*.yaml` §rule-allocation-map.

## What is permitted

- Dual-P (rule listed as **P** in two contracts) when enumerated in
  ADR-0098. Today only G-9 (Recurring-Defect Family Truth) carries
  dual-P — it is primary for both commit and review phases.
- Cross-reference (rule listed as **X** in multiple contracts).
  Cross-refs may be unbounded.

## Enforcement

`gate/rules/rule-117.sh` scans all 5 phase contracts + the rules and
principles directories, builds two sets, and asserts the two
directional invariants. Dual-P exceptions parsed from
`docs/adr/0098-*.yaml`.

## Cross-references

- ADR-0098 — rc21 6-phase scenario-loaded contracts.
- Rule G-3.c — kernel-card existence (the pre-split equivalent).
- Rule 110 META — scope-completeness discipline this rule
  operationalises for the contract layer.
- Enforcer E165 — `gate/rules/rule-117.sh`.
