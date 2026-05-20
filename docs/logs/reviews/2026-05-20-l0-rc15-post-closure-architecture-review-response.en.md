---
level: L0
view: process
affects_level: L0
affects_view: process
proposal_status: closure
date: 2026-05-20
authors: ["rc16 corrective wave"]
responds_to:
  - docs/logs/reviews/2026-05-20-l0-rc15-post-closure-architecture-review.en.md
related_adrs:
  - ADR-0070
  - ADR-0086
  - ADR-0090
  - ADR-0091
  - ADR-0092
  - ADR-0093
affects_artefact: [CLAUDE.md, docs/CLAUDE-deferred.md, docs/governance/principle-coverage.yaml, docs/governance/principles/P-A.md, docs/governance/principles/P-B.md, docs/governance/principles/P-C.md, docs/governance/principles/P-D.md, docs/governance/principles/P-E.md, docs/governance/principles/P-F.md, docs/governance/principles/P-G.md, docs/governance/principles/P-H.md, docs/governance/principles/P-I.md, docs/governance/principles/P-J.md, docs/governance/principles/P-K.md, docs/governance/principles/P-L.md, docs/governance/principles/P-M.md, docs/governance/rules/rule-R-K.md, docs/governance/rules/rule-R-H.md, docs/governance/rules/rule-R-D.md, docs/governance/rules/rule-G-2.md, docs/governance/rules/rule-107.md, docs/governance/rules/rule-108.md, docs/governance/rules/rule-109.md, docs/governance/rules/rule-110.md, docs/governance/architecture-status.yaml, docs/governance/enforcers.yaml, docs/governance/logs-folder-policy.md, agent-execution-engine/ARCHITECTURE.md, agent-evolve/ARCHITECTURE.md, agent-middleware/ARCHITECTURE.md, agent-bus/ARCHITECTURE.md, agent-service/ARCHITECTURE.md, docs/contracts/engine-envelope.v1.yaml, docs/contracts/engine-hooks.v1.yaml, docs/contracts/openapi-v1.yaml, docs/contracts/contract-catalog.md, docs/adr/0093-rc16-recurring-family-comprehensive-closure-and-meta-scope-completeness.yaml, gate/check_architecture_sync.sh, gate/test_architecture_sync_gate.sh, docs/logs/reviews/2026-05-20-l0-rc14-post-closure-architecture-review-response.en.md]
---

# rc16 Closure Response — L0 rc15 Post-Closure Architecture Review

**Verdict:** 4 of 4 cited findings closed (P1-1 + P1-2 + P2-1 + P2-2 partially-accepted, second recommendation REJECTED with policy rationale); 3 hidden defects surfaced by Family A sweep and closed in batch; 0 hidden defects in Family B sweep; multiple hidden defects in Family C sweep closed corpus-wide; live gate green; Maven + tests green.

## Why this wave: the family-recurrence reflection

The user's rc16 directive was twofold: (1) close the 4 cited findings; (2) systematically reflect on whether prior "closures" were actually closing the underlying defect FAMILIES or only the cited surfaces — the goal being to publish a release note that no future reviewer can pick apart.

Family-recurrence audit confirmed the user's suspicion: every rc16 cited finding is a recurrence of a family that prior waves declared closed. Each prior wave's prevention rule was scoped to the reviewer-cited surface, not to every surface where the family could manifest. The lesson was documented in rc10/rc11/rc12 release notes ("Reviewer scope can be narrower than defect scope") but never operationalized as a gate. rc16 makes the lesson enforceable.

## Family recurrence inventory

| Family | Class | Prior closure waves + rule | Why prior rule missed rc16 defect | rc16 finding caused |
|---|---|---|---|---|
| **A** Cross-authority parity | clause-name truth across active/deferred declarations | I-γ (rc10, no dedicated rule); K-α (rc12 Rule 101 narrow); L-α (rc14 G-8.a graph-baseline-only); M-α (rc15 G-8.e structural-carrier-only) | Each wave added a SURFACE dimension (numerics, paths, carrier-class, module-count) but **clause-name parity across active/deferred declarations** (principle-coverage.yaml ↔ rule cards ↔ CLAUDE-deferred ↔ kernels) was never the target | P1-1 R-K.b vs R-K.c orphaned clause name |
| **B** Text-vs-code anchor truth | free-text references to Java entities that no longer resolve | G-α (rc8 Rule 88 on architecture-status.yaml `shipped:true`); H-α (rc9 Rule 91 on enforcers / `active_gate_checks`) | Both prior rules scoped to YAML structured fields; free-text in rule/principle cards never targeted | P1-2 stale `SkillCapacityResolutionIT.suspendsSecondCallerWhenCapacityIsOne` |
| **C** Namespace migration completeness | numeric Rule N refs surviving post-ADR-0086 in semantic-authority surfaces | K-α (rc12 Rule 101 authority-surfaces-only per ADR-0086); M-η (rc15 agent-service only) | Rule 101 EXPLICITLY excluded principle-card frontmatter + engine contract docs + module ARCHITECTURE.md (other than agent-service); the migration shipped piecemeal | P2-1 numeric refs in 13 principle frontmatters + 4 ARCHITECTURE.md + 3 contracts |
| **D** Log-folder snapshot evidence | numeric values in docs/logs/* drift from current graph | I-α (rc10 Rule 97 LATEST release note only); M-ε (rc15 inline reconciliation) | User policy: docs/logs/ are interaction records by design and are NOT gated against the live graph | P2-2 rc14 closure response shows 384/577 vs current 386/594 |

## Per-finding closure

### P1-1 — R-K active/deferred state contradictory across authoritative sources (Family A)

**Decision:** accept (cited + 3 hidden).

**Closure evidence:**
- `docs/governance/principle-coverage.yaml:153` — `Rule-R-K.b` → `Rule-R-K.c` (cited).
- `docs/governance/rules/rule-R-K.md:23,31` — method name updated to `rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne` (rc15 rename per ADR-0091); "original 41.b deferral closed" reworded to acknowledge R-K.b is closed and R-K.c is the surviving deferred clause.
- `docs/governance/principles/P-K.md:30` — "Rule R-K.b activated" → "Rule R-K kernel activated" with corrected method name.
- `docs/CLAUDE-deferred.md:366` — "Rule R-H / Rule R-K.b runtime integration" → "Rule R-H / Rule R-K.c (W2 scheduler admission)".
- `docs/governance/rules/rule-R-H.md:24` — Companion-rule prose rewritten to reflect W1 decision envelope + W2 deferred SUSPENDED transition (was: "over-cap callers are SUSPENDED via the same Chronos Hydration interlock" — overclaim).
- **HIDDEN 1:** `docs/governance/principle-coverage.yaml:141` — `Rule-R-J.b.d` orphan surfaced by sweep. Closure: added `## Rule R-J.b.d — RunLifecycle Resume + Retry Re-Authorization [Deferred to W2]` heading to CLAUDE-deferred.md.
- **HIDDEN 2/3:** `rule-R-J.md:12` (kernel) + `rule-R-J.md:54` (card list) — both reference `R-J.b.d` which is now resolved by the new CLAUDE-deferred.md heading.

**Prevention:** Rule 107 (`cross_authority_clause_parity`) — gate-script in `gate/check_architecture_sync.sh#cross_authority_clause_parity`; enforcer E152; 2 self-test fixtures across principle-coverage.yaml + CLAUDE-deferred.md surfaces.

### P1-2 — rc15 prevention layer still misses rule/principle card evidence anchors (Family B)

**Decision:** accept (cited; 0 hidden surfaced by sweep — encouraging signal).

**Closure evidence:**
- `docs/governance/principles/P-K.md:30` + `docs/governance/rules/rule-R-K.md:23,31` — method name updated (3 instances; verified via corpus-wide grep that no other stale `suspendsSecondCallerWhenCapacityIsOne` references exist in active surfaces).
- Family B sweep across `docs/governance/rules/*.md` + `docs/governance/principles/P-*.md` + `architecture-status.yaml` + `docs/contracts/*.yaml` + `agent-*/ARCHITECTURE.md` for `<ClassName>.<methodName>` patterns found NO additional stale references. Java anchors in governance text are otherwise clean.

**Prevention:** Rule 108 (`governance_text_java_anchor_truth`) — gate-script in `gate/check_architecture_sync.sh#governance_text_java_anchor_truth`; enforcer E153; 2 self-test fixtures across rule cards + principle cards.

### P2-1 — Namespaced rule authority incomplete in principle cards and engine contract docs (Family C)

**Decision:** accept (cited + 18+ hidden).

**Closure evidence:**
- **13 principle card frontmatters** (`docs/governance/principles/P-A.md` through `P-M.md`): `enforced_by_rules:` migrated from numeric `[N]` to namespaced form with same-line `# formerly Rule N` comment.
- **P-M.md kernel body** (line 18): `Enforced by Rules 43–47` → `Enforced by Rules R-M.a–R-M.e (formerly Rules 43–47); cross-cutting structural invariant operationalised by Rule M-2 sub-clause .a (formerly Rule 48)`.
- **`agent-execution-engine/ARCHITECTURE.md`** lines 9, 37, 47, 61, 63: numeric → namespaced with `(formerly Rule N)` parentheticals.
- **`agent-evolve/ARCHITECTURE.md`** lines 9, 27: Rule 47 → Rule R-M.e.
- **`agent-middleware/ARCHITECTURE.md`** lines 9, 40, 73: Rule 45 → Rule R-M.c; Rule 32 → Rule R-D.
- **`agent-bus/ARCHITECTURE.md`** line 59: Rule 38 → Rule R-H.
- **`agent-service/ARCHITECTURE.md`** lines 36, 40, 140, 296, 298, 420, 605: Rule 33 / 21 / 20 / 32 / 11 → namespaced equivalents.
- **`docs/contracts/engine-envelope.v1.yaml`** lines 16, 34: Rule 48.c / 43 → namespaced.
- **`docs/contracts/engine-hooks.v1.yaml`** lines 51, 63, 69, 96: Rule 45 / 45.b → R-M.c / R-M.c.b with (formerly) markers.
- **`docs/contracts/openapi-v1.yaml`** lines 36, 129, 235, 267, 276, 295: Rule 36 / Rule 20 / Rule 36.b → R-F / R-C.d / R-F.b.
- **`docs/contracts/contract-catalog.md`** lines 83, 103: Rule 44 / 48 → R-M.b / M-2.a.
- **`docs/governance/rules/rule-G-2.md`** line 108: `Rule 27` clarified as `Gate Rule 27` (gate-layer per ADR-0086 — intentional numeric).
- **`docs/governance/rules/rule-R-D.md`** line 272: `Rule 66` clarified as `Gate Rule 66`.

**Prevention:** Rule 109 (`namespaced_rule_reference_completeness`) — gate-script in `gate/check_architecture_sync.sh#namespaced_rule_reference_completeness`; enforcer E154; 2 self-test fixtures across principle card + module ARCHITECTURE.md surfaces. Widens rc12 Rule 101's authority-surfaces-only scope per ADR-0086 to every manifestation surface of the post-ADR-0086 namespace migration.

### P2-2 — rc15 closure response contains stale graph verification evidence (Family D)

**Decision:** **partial accept + partial reject.**

**Accept:** the **first** reviewer recommendation (update rc14 closure response text to reconcile the 384/577 numeric).

**Reject:** the **second** reviewer recommendation (extend Rule 97 release-note numeric-truth gate scope to scan closure-response verification blocks under `docs/logs/reviews/`).

**Closure evidence (accepted half):**
- `docs/logs/reviews/2026-05-20-l0-rc14-post-closure-architecture-review-response.en.md` — historical-snapshot marker added after the top-level heading: `> **Historical artifact frozen at SHA 8a733ca (rc15 wave merge commit; PR #13).**` plus explanatory text pointing readers to `docs/governance/architecture-status.yaml#baseline_metrics` for current canonical values. Matches the convention already in use for rc7/rc9/rc10/rc11 closure responses.

**Disagreement rationale (rejected half):**

The user's directive (translated): *"interaction records in logs/ shouldn't be locked by the architecture graph, otherwise other architecture teams will spend their energy on format adjustments."*

The reasoning is structural:

1. **Logs/ are interaction records by architectural design.** Each review document and closure response captures a conversation at a point in time. The numeric values inside were correct at that moment; the document itself is the audit trail of what was discussed and decided, not a normative authority surface.
2. **Locking logs/ to the live graph forces retroactive edits.** Every architecture team that publishes a review or response would have to either (a) match the live graph at the moment of writing, OR (b) carry exemption markers AND keep updating those markers as the graph advances. Either path drains team energy into format chasing that yields no architectural value.
3. **Canonical baseline already lives in a single authoritative place.** `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics` is the single normative source for current counts. Log-folder files that disagree are snapshots; status.yaml is truth.
4. **The historical-snapshot marker convention already handles this organically.** Closure responses for rc7/rc9/rc10/rc11 already carry the marker pattern. The rc14 closure response was the first to lag this convention; rc16 retrofits the marker. This is documentation hygiene, not a gate requirement.

**Policy codification:** `docs/governance/logs-folder-policy.md` (new this wave) declares the policy explicitly so future reviewers can see the boundary at-a-glance. Future proposals to extend numeric-truth gates to `docs/logs/reviews/` would need (a) an explicit ADR amending this policy with clear cost-benefit, or (b) demonstration that the marker convention has materially failed.

**Prevention path NOT taken:** no new gate rule for closure-response numeric truth. The marker convention + the canonical baseline in status.yaml + this policy document are the prevention layer.

## META prevention — Rule 110 closes the documented-but-never-gated meta-lesson

**Decision:** ship META Rule 110 (`prevention_rule_scope_completeness`).

Every rule card whose frontmatter declares `scope_surfaces:` MUST have ≥2 self-test fixture functions named `test_rule_<rule_id>_*` in `gate/test_architecture_sync_gate.sh`. Pre-rc16 rules without `scope_surfaces:` are grandfathered.

The rule dogfoods itself: rule cards 107/108/109/110 each declare `scope_surfaces:` AND carry ≥2 fixtures.

This operationalises the rc10/rc11/rc12 documented meta-lesson "Reviewer scope can be narrower than defect scope" that prior waves recorded in release notes but never gated. Without this, the same families recur next wave under different letter labels.

## Reviewer's Required Closure Criteria — pass status matrix

| # | Criterion | Status |
|---|---|---|
| 1 | P-K must have a single active/deferred truth: R-K kernel active decision-envelope behaviour; R-K.c deferred Run/step suspension transition | ✓ PASS (Track A; Rule 107 prevention) |
| 2 | All active R-K evidence anchors resolve to the current method `rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne` | ✓ PASS (3 instances updated; Rule 108 prevention) |
| 3 | Rule R-H and deferred S2C prose must not describe R-K.b as the W2 suspension-transition authority | ✓ PASS (rule-R-H.md:24 + CLAUDE-deferred.md:366 rewritten) |
| 4 | Principle-card front matter and machine `principle-coverage.yaml` use the same namespaced rule vocabulary | ✓ PASS (13 principle frontmatters migrated; Rule 109 prevention) |
| 5 | Current engine contract docs use Rule R-M / Rule M-2 namespaced references, with numeric aliases only as historical parentheticals | ✓ PASS (engine-envelope.v1.yaml + engine-hooks.v1.yaml + contract-catalog.md + openapi-v1.yaml + 4 module ARCHITECTURE.md migrated) |
| 6 | rc15 closure response (= rc14 closure response, the doc with 384/577) reconciles its graph verification evidence with the final canonical baseline | ✓ PASS (historical-snapshot marker added; canonical baseline in architecture-status.yaml; gate scope NOT extended per user policy) |
| 7 | A prevention fixture proves that stale rule/principle-card method evidence fails closed | ✓ PASS (Rule 108 has positive + negative fixtures: `test_rule_108_java_anchor_truth_pos` + `test_rule_108_java_anchor_truth_neg`) |

## Baseline metric deltas

| Metric | rc15 | rc16 | Δ |
|---|---|---|---|
| ADR count | 91 | 92 | +1 (ADR-0093) |
| active_gate_checks | 118 | 122 | +4 (Rules 107/108/109/110) |
| gate_executable_test_cases | 194 | 202 | +8 (2 fixtures × 4 new rules) |
| enforcer_rows | 150 | 154 | +4 (E152/E153/E154/E155) |
| architecture_graph_nodes | 386 | 396 | +10 (post-ADR-0093 regen) |
| architecture_graph_edges | 594 | 615 | +21 (post-ADR-0093 regen) |
| maven_tests_green | 374 | 374 | 0 (no Java code changes) |
| active_engineering_rules | 31 | 31 | 0 (Rules 107-110 are gate-layer, not engineering rules) |

## Verification (executed pre-merge)

```bash
# Gate self-test
wsl bash gate/check_architecture_sync.sh            # see release note for live result
wsl bash gate/test_architecture_sync_gate.sh        # 202/202 expected

# Graph parity
wsl python3 gate/build_architecture_graph.py        # 396 / 615 deterministic across regens
wsl python3 gate/build_architecture_graph.py --check --no-write  # validation OK

# Maven (Windows per memory feedback_linux_first_dev)
./mvnw.cmd clean verify                              # BUILD SUCCESS, 374 tests
```

## Out-of-scope (deferred to future waves)

- Retrofit `scope_surfaces:` to all 30 existing namespaced rules (pre-rc16). Rationale: Rule 110 grandfathers existing rules; opt-in retrofit can happen as cards are touched.
- Rule 109 ADR-body scope (currently scans contracts/principles/rules/module-ARCHITECTURE.md; ADR `.yaml` body fields with stale numeric Rule references like ADR-0072..0077 are NOT in scope this wave — ADRs are historical authority records frozen at acceptance time).
- Standalone freshness-check gate rule for the `Historical artifact frozen at SHA` marker pattern on `docs/logs/reviews/*.md` (rc16 documents the convention; no gate enforcement per Family D policy).
