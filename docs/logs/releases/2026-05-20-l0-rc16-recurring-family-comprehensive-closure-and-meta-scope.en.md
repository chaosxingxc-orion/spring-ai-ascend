---
level: L0
view: process
affects_level: L0
affects_view: process
release_tag: v2.0.0-rc16
date: 2026-05-20
authority: ADR-0093
supersedes_release_notes:
  - docs/logs/releases/2026-05-20-l0-rc15-structural-carrier-parity-and-terminal-state-scope.en.md
---

# L0 v2.0.0-rc16 — Recurring-Family Comprehensive Closure + META Scope Completeness

> **Historical artifact frozen at SHA 84527ee.** This release note captures rc16
> wave-final baselines (92 ADRs / 122 active gate rules / 31 active engineering
> rules / 202 self-tests / 396 graph nodes / 615 edges). For current canonical
> baselines see `docs/governance/architecture-status.yaml#architecture_sync_gate.allowed_claim`
> (rc17 superseded these counts per ADR-0094: 93 ADRs / 123 gate rules / 37
> engineering rules / 205 self-tests / 407 nodes / 643 edges). Rule 28
> release_note_baseline_truth exempts this file via the `Historical artifact
> frozen at SHA` marker convention (Rule 28 release-note baseline truth lesson).

## Verdict

rc16 closes the rc15 post-closure architecture review (Codex; 4 cited findings: P1-1 / P1-2 / P2-1 / P2-2) by recognising every cited finding as a recurrence of a family that prior waves declared closed. Each prior wave's prevention rule scoped to the reviewer-cited surface, not to every surface where the family could manifest. The rc10/rc11/rc12 documented meta-lesson "Reviewer scope can be narrower than defect scope" was never operationalized — rc16 fixes that.

**Per-finding outcome:**
- **P1-1** (R-K active/deferred drift) — closed (cited + 3 hidden surfaced by Family A sweep).
- **P1-2** (stale Java method name in cards) — closed (3 cited instances; 0 hidden).
- **P2-1** (numeric Rule refs in active authority surfaces) — closed (cited + 18+ hidden across principle frontmatters / module ARCHITECTURE.md / contracts).
- **P2-2** (rc14 closure response numeric drift) — **partial accept + partial reject.** Content marker added (canonical convention). Gate-extension recommendation REJECTED per user policy: `docs/logs/` interaction records are not graph-locked by design.

**Methodology:** Categorize-then-sweep with explicit family taxonomy. Rule 110 META gates the discipline going forward — every new prevention rule MUST declare `scope_surfaces:` + carry ≥2 self-test fixtures across distinct surfaces.

## Family taxonomy

| Family | Class | rc16 closure |
|---|---|---|
| **A** Cross-authority parity | clause-name truth across YAML / cards / kernels | Rule 107 (`cross_authority_clause_parity`) + E152 + 2 fixtures |
| **B** Text-vs-code anchor truth | free-text Java references | Rule 108 (`governance_text_java_anchor_truth`) + E153 + 2 fixtures |
| **C** Namespace migration completeness | numeric Rule refs in semantic-authority surfaces | Rule 109 (`namespaced_rule_reference_completeness`) + E154 + 2 fixtures |
| **D** Log-folder snapshot evidence | numeric drift inside docs/logs/* | Content marker + `docs/governance/logs-folder-policy.md` (no gate) |
| **META** Scope discipline | prevention rules scoped narrowly | Rule 110 (`prevention_rule_scope_completeness`) + E155 + 2 fixtures; dogfoods itself |

## Baseline metrics

Rule 28 release-note table (canonical baseline-truth row format):

| Metric | Count | Delta (rc15 → rc16) | Rationale |
|---|---|---|---|
| §4 constraints | 65 | unchanged (#1–#65) | No new §4 invariants |
| Active ADRs | 92 | +1 | ADR-0093 (rc16 recurring-family comprehensive closure + meta scope completeness) |
| Active gate rules | 122 | +4 | Rule 107 cross_authority_clause_parity + Rule 108 governance_text_java_anchor_truth + Rule 109 namespaced_rule_reference_completeness + Rule 110 prevention_rule_scope_completeness META |
| Gate self-test cases | 202 | +8 | 2 fixtures × 4 new rules |
| Active engineering rules | 31 | unchanged head-count | Rules 107-110 are gate-layer per ADR-0086 gate_layer_boundary, not engineering rules |
| Enforcer rows | 154 | +4 | E152 (Rule 107) + E153 (Rule 108) + E154 (Rule 109) + E155 (Rule 110 META) |
| Layer-0 governing principles | 13 | unchanged | P-A..P-M unchanged (frontmatter migrated [N]→[R-X] but principle count unchanged) |
| Reactor modules | 8 | unchanged | Same 8-module post-rc13 reactor |
| Architecture graph nodes | 396 | +10 vs rc15 (386) | ADR-0093 + 4 enforcer rows + new logs-folder-policy.md + new rc16 release note + new rc15-post-closure-response review doc |
| Architecture graph edges | 615 | +21 vs rc15 (594) | ADR-0093 supersedes/extends + rule-card↔enforcer↔fixture edges |
| Maven tests green | 374 | unchanged | No Java code deltas in rc16 |

## Pillar coverage (per Rule R-B / ADR-0065)

- **performance** — no change; baseline metric `current_value` in `docs/governance/competitive-baselines.yaml` unchanged.
- **cost** — no change.
- **developer_onboarding** — improved (principle cards + contract docs + module ARCHITECTURE.md now use namespaced Rule references; readers no longer need to remember the pre-ADR-0086 numeric mapping). Quickstart unchanged.
- **governance** — strengthened. 4 new gate rules close 3 recurring defect families + add META scope-completeness enforcement.

## What shipped

### Rule 107 — cross_authority_clause_parity (E152)

Family A prevention. Every deferred clause name `Rule-X.<letter>` listed in `principle-coverage.yaml#deferred_operationalisers` MUST have a matching `## Rule X.<letter>` heading in `CLAUDE-deferred.md`. Closes rc15 P1-1 (R-K.b orphaned) AND the 3 hidden defects (R-J.b.d orphaned in principle-coverage.yaml + rule-R-J.md kernel + card list — sweep added `## Rule R-J.b.d` heading to CLAUDE-deferred.md).

### Rule 108 — governance_text_java_anchor_truth (E153)

Family B prevention. Every `<ClassName>.<methodName>` token in active rule cards + principle cards MUST resolve to a real Java method, OR have a nearby historical marker. Closes rc15 P1-2 (3 stale `SkillCapacityResolutionIT.suspendsSecondCallerWhenCapacityIsOne` references after rc15 rename per ADR-0091).

### Rule 109 — namespaced_rule_reference_completeness (E154)

Family C prevention. Every `Rule N` reference where N ∈ [1, 48] (engineering-rule range pre-ADR-0086) in active semantic-authority surfaces MUST carry a SAME-LINE legacy marker. Gate-layer numerics (N ≥ 49) are intentional per ADR-0086 gate_layer_boundary. Closes rc15 P2-1 by widening rc12 Rule 101's authority-surfaces-only scope to every manifestation surface.

### Rule 110 — prevention_rule_scope_completeness (E155) [META]

Operationalises the rc10/rc11/rc12 documented meta-lesson "Reviewer scope can be narrower than defect scope". Every rule card with `scope_surfaces:` frontmatter MUST have ≥2 self-test fixtures across distinct surfaces. Pre-rc16 rules grandfathered. Rules 107/108/109/110 dogfood the rule.

### docs/governance/logs-folder-policy.md

Codifies user's "logs/ ≠ graph-locked" directive into a discoverable policy. `docs/logs/reviews/*.md` and non-latest `docs/logs/releases/*.md` are point-in-time interaction records exempt from architecture-graph numeric-truth gates. Documents the historical-snapshot marker convention as the canonical pattern.

### Content fixes (corpus-wide batch)

- **Family A cited + 3 hidden:** `principle-coverage.yaml:153` (R-K.b→R-K.c), `principle-coverage.yaml:141` (R-J.b.d preserved + heading added to CLAUDE-deferred.md), `rule-R-K.md:23,31` (method rename), `rule-R-H.md:24` (companion-rule prose corrected), `rule-R-J.md:12,54` (R-J.b.d resolved), `P-K.md:30` (Rule R-K.b → Rule R-K kernel; method rename), `CLAUDE-deferred.md:366` (R-K.b → R-K.c cross-ref).
- **Family C cited + 18+ hidden:**
  - 13 principle frontmatter migrations: `[N]` → `[R-X]` with `# formerly Rule N` comment.
  - P-M.md kernel body: `Rules 43–47` → `Rules R-M.a–R-M.e`.
  - 4 module ARCHITECTURE.md files migrated: agent-execution-engine (5 lines), agent-evolve (2 lines), agent-middleware (3 lines), agent-bus (1 line), agent-service (7 lines).
  - 4 contract docs migrated: engine-envelope.v1.yaml (2 lines), engine-hooks.v1.yaml (4 lines), openapi-v1.yaml (6 lines), contract-catalog.md (2 lines).
  - 2 rule cards clarified: rule-G-2.md (Rule 27 → Gate Rule 27), rule-R-D.md (Rule 66 → Gate Rule 66).
- **Family D content fix:** rc14 closure response document carries the historical-snapshot marker after its heading. Future readers know the 384/577 numerics are pre-ADR-0092 snapshot.

## What did NOT ship (intentional out-of-scope)

- **Rule 97 scope extension to docs/logs/reviews/.** Rejected per user policy. Codified in `docs/governance/logs-folder-policy.md`.
- **scope_surfaces: retrofit to all 30 existing namespaced rules.** Grandfathered. Opt-in retrofit as cards are touched.
- **Active ADR body migration to namespaced rule IDs.** ADRs 0072–0077 still reference Rule 43–48 numeric form in their body fields; rc16 leaves them as historical authority records frozen at acceptance time. Rule 109 scope does not include ADR `.yaml` body fields.

## Verification

```bash
# Architecture gate
wsl bash gate/check_architecture_sync.sh         # GATE: PASS (rc16 wave)

# Architecture gate self-tests
wsl bash gate/test_architecture_sync_gate.sh     # 202/202 expected

# Live graph parity
wsl python3 gate/build_architecture_graph.py     # 396 nodes / 615 edges (deterministic)

# Maven (Windows per memory feedback_linux_first_dev)
./mvnw.cmd clean verify                          # BUILD SUCCESS, 374 tests
```

## Methodology codification

The categorize→sweep→batch-fix→prevention discipline (rc8/rc9/rc10/rc11/rc12 release notes) is now enforced at the gate layer:

1. **Categorize:** map each cited finding to a defect family. Document recurrence lineage against prior waves' rules.
2. **Sweep:** corpus-wide read-only sweep for the family's mechanism on EVERY surface (not just the reviewer-cited surface).
3. **Batch-fix:** apply fixes across all surfaces in the same wave.
4. **Prevention:** add a gate rule scoping to all manifestation surfaces. Declare `scope_surfaces:` in the rule card frontmatter. Add ≥2 self-test fixtures across distinct surfaces.
5. **META gate:** Rule 110 enforces step 4. A scope-narrow rule fails gate self-test at PR time.

The rc10/rc11/rc12 hidden-defect counts (rc8=14, rc10=9, rc11=30+, rc12=3, rc15=0) describe the downward trend as discipline takes hold. rc16's hidden-defect count: 3 (Family A) + 0 (Family B) + 18+ (Family C). The Family C count reflects the deliberately wide P2-1 scope rather than reviewer narrowness.

## Lessons learned (for the rc17+ wave)

1. **The recurrence pattern is gateable.** Prior waves documented the meta-lesson in release notes; rc16 puts the gate behind it (Rule 110). The next reviewer cannot find this family of defect because the gate fails closed at PR time.
2. **logs/ policy is a hard line.** Future reviewers proposing to lock log-folder numerics to the architecture graph have a written counter-argument (`docs/governance/logs-folder-policy.md`) AND prior policy precedent.
3. **Numeric Rule ID ratchets need to scope by mechanism.** The rc12 K-α / rc15 M-η piecemeal migration left 18+ surfaces unmigrated for months. rc16 Rule 109's broad scope closed them in one wave; the gate will prevent future drift.
4. **Java anchor truth is its own family.** Reviewers don't typically grep for stale `<Class>.<method>` references in card prose. Rule 108 puts the gate where the reviewer's eye doesn't go.
5. **Historical-snapshot marker convention is enough for logs/.** No gate needed; the marker is a documentation hygiene practice. rc14 closure response was the first to lag it; rc16 retrofits and documents the convention as canonical.
