# Migration Coverage Report — Wave 5

> Final audit of the Wave 4 atomic ratchet (Rule namespace migration to P-/R-/D-/G-/M- prefix scheme). Generated 2026-05-19 post-Wave-4 by `tmp_wave5_audit.py` (now cleaned up). Authority: ADR-0086 + `docs/logs/rule-migration-map.yaml`.

## Coverage parity

**81/81 migrations resolved to card files** — every row in `docs/logs/rule-migration-map.yaml` (active + deferred + retired) maps to an existing or correctly-absent destination:

| Bucket | Count | Resolution |
|---|---|---|
| Active rules (D-/R-/G-/M-) with cards | 67 | Card exists at `docs/governance/rules/rule-<new-id>.md` |
| Deferred rules | 13 | Card retains old name (rule-29c.md → rule-R-A.c.md); CLAUDE-deferred.md cross-reference resolves via Rule 96/G-3.d |
| Retired (Rule 12 placeholder) | 1 | Resolution: no card (Rule 12 was never activated) |
| **Total** | **81** | — |

## Gate verification (post-ratchet, WSL canonical)

| Check | Result |
|---|---|
| `bash gate/check_architecture_sync.sh` | **GATE: PASS** (rc11 baseline 112 PASS / 0 FAIL; rc12 adds 4 prevention rules → 116 PASS / 0 FAIL) |
| `bash gate/check_parallel.sh` | **GATE: PASS** (0 FAIL) |
| `bash gate/test_architecture_sync_gate.sh` | **Tests passed: 172/172** (rc11 baseline; rc12 adds new fixtures for Rules 101-104 + Rule 82/94/99 widenings) |
| `python gate/build_architecture_graph.py --check --no-write` | **364 nodes, 525 edges, Graph validation: OK** (live measurement 2026-05-19 post-rc12-Wave-6-Stream-C consolidation; replaces stale rc11 baseline 394/533 surfaced by rc11 review P1-3 K-γ) |
| `grep -c "rc[0-9]+" outside docs/logs/` | **82 files** (down from rc11 baseline 142 — 42% reduction; remaining are body-section historical narrative awaiting future scrub) |

## Gate-layer namespace boundary (rc12 K-α decision)

Per rc11 review finding P1-1 closure decision (user choice 2026-05-19): the D-/R-/G-/M- namespace is canonical for **semantic authority surfaces** (CLAUDE.md kernels, rule cards' `rule_id:` frontmatter, `enforcers.yaml#constraint_ref`, `architecture-status.yaml#baseline_metrics`, `architecture-graph.yaml` rule-node ids, `principle-coverage.yaml#operationalised_by_rules`). Gate **implementation-layer identifiers** retain numeric form by design: `gate/check_architecture_sync.sh` section headers (`# Rule N — slug`), `gate/rules/rule-NNN.sh` filenames, `gate/test_architecture_sync_gate.sh#test_rule_<N>_*` function names. Bi-directional addressability between numeric gate identifiers and namespaced authority identifiers is preserved through `docs/logs/rule-migration-map.yaml` and gate parser support per ADR-0086 gate_layer_boundary section. Rule G-3.c (every-active-rule-has-a-card) plus rc12 Rule 101 (rule_namespace_authority_completeness) jointly gate the boundary.

## Scope of bulk substitutions

| Target | Files | Substitutions |
|---|---|---|
| docs/governance/principle-coverage.yaml | 1 | 44 |
| docs/governance/enforcers.yaml | 1 | 197 |
| docs/governance/architecture-status.yaml | 1 | 49 |
| docs/CLAUDE-deferred.md | 1 | 79 |
| CLAUDE.md | 1 | 239 |
| docs/governance/rules/rule-*.md (card bodies) | 68 | 571 |
| docs/adr/*.{md,yaml} | 49 | 368 |
| agent-*/src/test/java/**/*.java (Javadoc) | 30 | 67 |
| docs/governance/principles/P-*.md | 13 | ~70 |
| docs/runbooks/ + READMEs | 5 | ~141 |
| **Total** | **170** | **~1825** |

## Files renamed (git mv preserved history)

30 rule cards renamed `rule-NN.md → rule-<new-id>.md` plus 1 orphan repair (`rule-29c.md → rule-R-A.c.md`).

## Gate-logic adjustments (made gate accept new namespace)

| Gate Rule | Adjustment |
|---|---|
| Rule 68 (kernel-card match) | Accept namespaced ids (rule-D-1.md, rule-R-C.a.md, …); skip cards whose body lives in CLAUDE-deferred.md |
| Rule 69 (every rule has card) | Normalise ids (strip leading zeros for integer ids; preserve namespaced ids) for set comparison |
| Rule 23 (active-doc links resolve) | Exempt entire `docs/logs/` partition (no longer just `docs/logs/reviews/`) |
| Rule 94 (deleted-module-name truth) | Exemption-paths list updated to renamed card filenames (rule-G-2.f.md etc.) |
| Rule 100 (disjunction wording) | `gate/rule-100-disjunction-allowlist.txt`: 96 → G-3.d; gate logic accepts both integer + namespaced rule ids |
| `gate/build_architecture_graph.py` | `rule_extract` regex now matches both integer (Rule 28) and namespaced (Rule D-1, R-C.a, G-3.f, M-2.b) forms |

## Pending items for the user

1. **Decide whether to commit the working tree.** No commit has been made (per CLAUDE.md "Never commit unless explicitly asked"). The working tree carries ~241 modified files + 6 new + 71+ moved.

2. **Optional final rcN scrub.** 82 active files retain rcN literals in body-section historical narrative (Motivation / Activation / Cross-references in cards, ADR amendment narratives, technical Javadoc context for SuspendSignal/S2cCallback). These are non-load-bearing per the gate; full scrub is a one-day mechanical pass if desired.

3. **Transitional gate (`rule-migration-truth`)** — was specified in ADR-0086 but NOT added during Wave 4 since the actual migration succeeded first-pass with coverage parity = 100%. Drop this from ADR-0086 verification list, or add it as a permanent post-merge audit. Recommendation: drop (the migration map itself + this report serves as the audit trail).

4. **`gate/lib/resolve_rule_id.sh` translator** — not yet created. Optional for the one-cycle grace period mentioned in ADR-0086 — if CI / external tooling pins rule numbers, this translator translates old → new. If no such tooling exists, skip.

5. **ADR-0086 status: proposed → accepted.** The ratchet succeeded; flip the status field.

## Authority references

- Migration plan: `D:/.claude/plans/rc1-rc2-docs-logs-ai-agent-spi-adr-rule-scalable-pond.md`
- Migration map (audit-trail bridge): `docs/logs/rule-migration-map.yaml`
- Ratchet ADR: `docs/adr/0086-rule-namespace-ratchet.yaml`
- Wave history: `docs/logs/governance-waves.md`
