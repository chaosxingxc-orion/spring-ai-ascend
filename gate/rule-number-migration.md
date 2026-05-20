# gate/rule-number-migration.md — Legacy Rule Number → Semantic Rule ID Mapping

Per ADR-0086 (rc12 gate_layer_boundary): the corpus runs two parallel rule
namespaces — **semantic** (`D-/R-/G-/M-`) for engineering rules and
**numeric** (`Rule 1 ... Rule 111`) for gate-implementation rules. ADR-0094
(rc17) further split several semantic rules into `.1` / `.2` sub-rules.

This document records the **historical mapping** from legacy numeric Rule
IDs (used before ADR-0086 migrated the engineering namespace) and pre-rc17
sub-clause names (used before ADR-0094 split rules) to their current
canonical names. It was extracted from `docs/governance/enforcers.yaml`
`constraint_ref` parentheticals in rc18 Wave 4 per ADR-0095, when those
parentheticals were removed to clean up namespace mixing.

## When to add an entry

Whenever a future wave:

- Migrates a numeric `Rule N` → semantic `Rule X-Y` (per ADR-0086 pattern), OR
- Splits a sub-clause `Rule X.b` → standalone sub-rule `Rule X.1` (per ADR-0094 pattern), OR
- Renames an enforcer ID

…add a row below so future auditors can trace the rename without git archaeology.

## How auditors use this document

- Git blame on `enforcers.yaml` no longer shows the legacy parentheticals
  (rc18 Wave 4 removed them). Instead, search this file for the legacy name:

  ```bash
  rg 'Rule 28a' gate/rule-number-migration.md
  # → returns: "Rule 28a → Rule R-C.a (Code-as-Contract, schema-level
  #            tenant_id) — migrated per ADR-0086 (rc12)"
  ```

- For rc17 sub-rule splits, the parent rule kernel + the child rule card
  both carry "(was Rule X.Y pre-rc17 per ADR-0094)" markers; this file
  consolidates them.

---

## Legacy numeric → semantic (ADR-0086 / rc12 wave)

| Legacy | Current | Rule kernel theme | Migration ADR |
|---|---|---|---|
| Rule 28a | Rule R-C.a | tenant_id column schema spine | ADR-0086 |
| Rule 28b | Rule R-C.a | (sub-shape of Code-as-Contract; pre-merge naming) | ADR-0086 |
| Rule 28c | Rule R-C.a | (sub-shape) | ADR-0086 |
| Rule 28d | Rule R-C.a | (sub-shape — out-of-scope clause) | ADR-0086 |
| Rule 28e | Rule R-C.a | (sub-shape — D3 decision) | ADR-0086 |
| Rule 28f | Rule R-C.a | (sub-shape) | ADR-0086 |
| Rule 28g | Rule R-C.a | (sub-shape) | ADR-0086 |
| Rule 28h | Rule R-C.a | (sub-shape — architect guidance §16) | ADR-0086 |
| Rule 28i | Rule R-C.a | (sub-shape — L1 plan §11) | ADR-0086 |
| Rule 11 | Rule R-C.2 sub-clause .a | Contract Spine Completeness — tenantId on persistent records | ADR-0086 (rule namespace) + ADR-0094 (split into R-C.2) |
| Rule 20 | Rule R-C.2 sub-clause .b | Run State Transition Validity — withStatus validates RunStateMachine | ADR-0086 + ADR-0094 |
| Rule 21 | Rule R-C.2 sub-clause .c | Tenant Propagation Purity — service.runtime ↛ service.platform | ADR-0086 + ADR-0094 |
| Rule 31 | Rule R-C.1 | Independent Module Evolution — module-metadata.yaml per module | ADR-0086 + ADR-0094 |

## rc17 sub-rule splits (ADR-0094)

| Pre-rc17 | Post-rc17 | Split reason | Authority |
|---|---|---|---|
| Rule R-C.a | Rule R-C (narrowed) | parent kernel scoped to Code-as-Contract only | ADR-0094 |
| Rule R-C.b | Rule R-C.1 | Independent Module Evolution split to standalone sub-rule | ADR-0094 |
| Rule R-C.c | Rule R-C.2 sub-clause .a | run spine — bundled into R-C.2 | ADR-0094 |
| Rule R-C.d | Rule R-C.2 sub-clause .b | run state transition — bundled into R-C.2 | ADR-0094 |
| Rule R-C.e | Rule R-C.2 sub-clause .c | tenant propagation — bundled into R-C.2 | ADR-0094 |
| Rule R-I.a | Rule R-I (narrowed) | parent kernel scoped to five-plane manifest only | ADR-0094 |
| Rule R-I.b | Rule R-I.1 | edge↔compute ingress routing split (status: design_only / W3+) | ADR-0094 |
| Rule G-2.a–.d, .g | Rule G-2 (narrowed) | per-surface authority-text truth retained | ADR-0094 |
| Rule G-2.e | Rule G-2.1 sub-clause .a | status-yaml deleted-module name truth | ADR-0094 |
| Rule G-2.f | Rule G-2.1 sub-clause .b | active corpus deleted-module name truth | ADR-0094 |
| Rule G-2.h | Rule G-2.1 sub-clause .c | broad corpus deleted-module name truth | ADR-0094 |
| Rule G-3.a–.e | Rule G-3 (narrowed) | kernel-card structural coherence retained | ADR-0094 |
| Rule G-3.f | Rule G-3.1 | kernel-implementation disjunction truth split (grammar concern) | ADR-0094 |
| Rule R-C.b.b | Rule R-C.1.a (deferred) | sub-sub-clause renumbered to track parent split | ADR-0094 |

## Two-namespace layering (per ADR-0086)

| Namespace | Identifier shape | Used where | Counted by baseline_metrics |
|---|---|---|---|
| Semantic engineering rules | `D-1 ... D-8`, `R-A ... R-M` (+ `.1`/`.2` sub-rules per ADR-0094), `G-1 ... G-9` (+ `.1`), `M-1 ... M-2` | CLAUDE.md kernel, rule cards (`rule-*.md`), card frontmatter `rule_id` | `active_engineering_rules: 37` |
| Numeric gate-implementation rules | `Rule 1 ... Rule 111` (each implements 1 sub-clause of a semantic rule) | `gate/check_architecture_sync.sh` rule headers, self-test fixture names (`test_rule_NNN_*`) | `active_gate_checks: 123` |

The two namespaces are NOT interchangeable. A `Rule N` reference in active
prose (engineering-rule range 1-48) without a same-line legacy marker (per
Rule 109 / E154) is a Gate failure — readers must use the semantic name
(consult this file to look up the migration).

## Cross-references

- ADR-0086 — rc12 gate_layer_boundary (numeric→semantic namespace split)
- ADR-0094 — rc17 recurring-defect-family-truth + rule-consolidation (sub-rule splits)
- ADR-0095 — rc18 comprehensive hardening (this migration doc + enforcers.yaml normalisation)
- Gate Rule 109 (`namespaced_rule_reference_completeness`, E154) — enforces semantic-namespace usage in active prose with `(formerly|legacy|...)` marker exemption for legacy refs
- Gate Rule 107 (`cross_authority_clause_parity`, E152) — enforces deferred-clause heading consistency across `principle-coverage.yaml` ↔ `CLAUDE-deferred.md`
- `docs/governance/rules/README.md` — taxonomy doc covering D-/R-/G-/M- prefixes + `.1`/`.2` sub-rule naming convention
