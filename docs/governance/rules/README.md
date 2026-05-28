---
level: L0
view: scenarios
status: active
authority_refs: [ADR-0086, ADR-0094]
---

# Rule Cards — Naming Taxonomy & Conventions

The `docs/governance/rules/` directory holds one card per active engineering
rule named in `CLAUDE.md`. This README documents the **prefix taxonomy**
(D-/R-/G-/M-) and the **sub-rule naming convention** (`.1`, `.2`) introduced
in the rc17 wave (ADR-0094).

## Prefix Taxonomy

The four prefixes correspond to four distinct purposes. A rule's prefix
tells you immediately what KIND of constraint it is.

### D-* — Daily Process Rules

Rules that apply to every development task — root-cause discipline,
simplicity, testing, pre-commit checklist, posture-aware defaults.
Higher `kernel_cap` (typically 12) because they're read on every task.

Current: D-1, D-2, D-3, D-4, D-5, D-6, D-7, D-8 (8 rules).

### R-* — Governing-Principle Rules (Layer-0 Operationalisation)

Rules that operationalise the 13 L0 governing principles (P-A ... P-M).
Each R-rule bridges from a P-principle to a concrete enforcer. R-rules
are the "ironclad" layer — most have ArchUnit / integration-test
backing in addition to gate-script enforcement.

Current (17 rules including `.1`/`.2` sub-rules per rc17 ADR-0094 +
the R-A.c hybrid):
R-A, R-A.c, R-B, R-C, R-C.1, R-C.2, R-D, R-E, R-F, R-G, R-H, R-I,
R-I.1, R-J, R-K, R-L, R-M. (Rule R-A.c is a sub-clause activated post-
deferral that retains the `.a/.b/.c` convention rather than `.1`/`.2`;
see the Sub-Rule vs Sub-Clause table below for the rationale.)

### G-* — Gate / Meta-Governance Rules

Rules that audit the **enforcement system itself** — kernel-card
coherence, architecture-graph well-formedness, baseline-metrics single
source, gate self-consistency, gate machinery integrity, Linux-first
verification, cross-authority parity, recurring-defect-family freshness.

These rules don't directly govern production code; they govern the
governance corpus. Without them, the R-* rules above could drift
silently. G-rules have lower `kernel_cap` (typically 8) because their
detail belongs in the on-demand card.

Current: G-1, G-2, G-2.1, G-3, G-3.1, G-4, G-5, G-6, G-7, G-8, G-9
(11 rules including .1 sub-rules per rc17 ADR-0094).

### M-* — Module / Structural Rules

Rules about module identity and domain-contract discipline — skeleton-
module truth, schema-first contracts, DFX-stem matching.

Current: M-1, M-2 (2 rules).

## Sub-Rule Naming Convention (`.1`, `.2` — rc17 ADR-0094)

When a rule grows enough sub-clauses to become hard to read, the rc17
wave introduces a structured split convention:

- **Parent rule keeps its identifier** and retains its core sub-clauses.
- **Extracted sub-rules get a numeric suffix** (`.1`, `.2`, ...).
- **Filename convention**: `rule-PREFIX-NAME.N.md` with a **dot** before
  the numeric suffix (e.g., `rule-G-3.1.md` for Rule G-3.1, matching the
  pre-existing `rule-R-A.c.md` dotted style). Hyphenated filenames like
  `rule-G-3-1.md` are NOT accepted by Gate Rule 99 / E143
  (rule_namespace_authority_completeness) because the gate maps the
  `#### Rule G-3.1` heading to a `rule-G-3.1.md` filename via dotted
  conversion.
- **Card frontmatter `rule_id`**: matches the dotted form (e.g.,
  `rule_id: G-3.1`).

### Why split rather than nest

A rule with 6-8 sub-clauses bundles concerns that often belong to
different domains. Splitting clarifies which rule a finding belongs to
and which reviewer should care. The split is **taxonomic only** —
existing gate enforcers retain their numeric Rule N identifiers and
self-test fixtures; only kernel attribution shifts.

### Splits performed in rc17 (per ADR-0094)

| Original | New Children | Reason |
|---|---|---|
| Rule G-3 (6 sub-clauses) | G-3 (a-e structural) + G-3.1 (disjunction grammar) | Grammar invariant is structurally distinct from structural-coherence invariant. |
| Rule R-I (2 sub-clauses) | R-I (.a manifest, shipped W1) + R-I.1 (.b ingress routing, design_only/W3+) | Shipped + deferred status mix made W1 surface assessment hard. |
| Rule G-2 (8 sub-clauses) | G-2 (a-d + g — core truth) + G-2.1 (e + f + h + Rule 103/109 — deleted-module scope) | Deleted-name scope had grown across 6 rc waves into a distinct family worth its own rule. |
| Rule R-C (5 sub-clauses) | R-C (.a code-as-contract) + R-C.1 (.b module evolution) + R-C.2 (.c+.d+.e run spine) | Three orthogonal domains (governance / build / persistence) bundled into one rule. |

### Sub-Rule vs Sub-Clause

| Form | Example | Meaning |
|---|---|---|
| Sub-clause | Rule G-2.a, G-2.b | Internal section of one rule's body. Has an enforcer mapping but no separate card. |
| Sub-rule | Rule G-2.1 | A standalone rule extracted from a parent. Has its own card file. The `.1` is a stable identifier (next extraction would be `.2`). |

## Two-Namespace Layering (per ADR-0086)

The repository runs two parallel rule namespaces by design:

- **Semantic namespace** (D-/R-/G-/M-): used in `CLAUDE.md` kernel,
  `docs/governance/rules/rule-*.md` card filenames, and `rule_id`
  frontmatter. This is the **engineering rule** namespace — **37 active
  rules** counted by `architecture-status.yaml#baseline_metrics.active_engineering_rules`.
  The rc17 ADR-0094 split count: rc16 baseline 31 + R-C → R-C+R-C.1+R-C.2 (+2)
  + R-I → R-I+R-I.1 (+1) + G-3 → G-3+G-3.1 (+1) + G-2 → G-2+G-2.1 (+1)
  + new Rule G-9 (+1) = **37**. The 4 "split-from" parent rules (R-C, R-I,
  G-3, G-2) are NOT removed — only narrowed — so the count goes up by the
  net additions, not net replacements.

- **Numeric namespace** (Gate Rule 1 ... Gate Rule 111): used in
  `gate/check_architecture_sync.sh` rule headers and self-test fixture
  names. This is the **gate-implementation** namespace — 122-128 active
  gate checks counted by `architecture-status.yaml#baseline_metrics.active_gate_checks`.

One engineering rule can be implemented by multiple gate rules
(e.g., Rule G-2.1 is implemented by gate Rules 94, 98, 103, 109 — each
covering a different file partition). One gate rule maps to exactly one
sub-clause of one engineering rule.

The two-namespace design (per ADR-0086 `gate_layer_boundary`) prevents
churn: refactoring a gate rule (splitting one section into two) doesn't
require renumbering the engineering-rule namespace, and vice versa.

## Rule Status Vocabulary

The `status:` field in a card's frontmatter takes one of:

- `active` — shipped + enforced in W1.
- `design_only` — kernel + card exist, enforcement is W2+ deferred.
- `deferred` — rule body archived in `docs/governance/retired-rules-audit.md`
  (legacy deferred-rule registry retired 2026-05-28); this card is a
  placeholder (rare; usually deferred rules don't have a card).
- `archived` — historically active, now superseded by ADR; card retained
  for audit but rule no longer enforced.

## Cross-references

- [CLAUDE.md](../../../CLAUDE.md) — kernel index of all active rules.
- [docs/governance/retired-rules-audit.md](../retired-rules-audit.md) —
  legacy deferred-rule registry retired 2026-05-28; disposition of every
  pre-May-2026 numeric rule (migrated / obsolete / unclear).
- [docs/governance/principle-coverage.yaml](../principle-coverage.yaml)
  — mapping from P-* principles to R-* rules.
- [docs/governance/enforcers.yaml](../enforcers.yaml) — every rule
  reaches ≥1 enforcer row.
- [docs/governance/architecture-status.yaml](../architecture-status.yaml)
  — baseline_metrics counts the rule populations.
- ADR-0086 — two-namespace `gate_layer_boundary` authority.
- ADR-0094 — rc17 rule-consolidation + sub-rule naming convention.
