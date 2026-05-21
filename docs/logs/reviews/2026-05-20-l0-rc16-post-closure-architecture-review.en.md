---
level: L0
view: process
affects_level: L0
affects_view: process
proposal_status: review
date: 2026-05-20
authors: ["Codex architecture review"]
review_scope:
  - contracts
  - authority
  - constraints
  - Java microservice architecture
  - agent-driven architecture components
  - prevention gates
  - generated gate-rule corpus
responds_to:
  - docs/logs/releases/2026-05-20-l0-rc16-recurring-family-comprehensive-closure-and-meta-scope.en.md
  - docs/logs/reviews/2026-05-20-l0-rc15-post-closure-architecture-review-response.en.md
related_adrs:
  - ADR-0083
  - ADR-0086
  - ADR-0091
  - ADR-0092
  - ADR-0093
---

# L0 rc16 Post-Closure Architecture Review

## Verdict

Do not publish a no-findings L0 completion release note yet.

The rc16 wave closes the substantive R-K, Java-anchor, and numeric-rule namespace defects from the rc15 review. The shipped Java microservice architecture is healthy: Maven verifies, the architecture gate verifies, the gate self-test suite verifies, and the architecture graph is consistent at 396 nodes / 615 edges. I do not see material overdesign in the service/module split, dynamic-planning staging, skill-capacity decision-envelope model, memory/knowledge ownership boundary, S2C placement, or engine runtime contract.

However, rc16 introduces a new L0 process/authority defect in the META prevention layer. The release, response, ADR-0093, and enforcer E155 say Rule 110 is based on rule-card front matter and that rule cards 107/108/109/110 dogfood the new `scope_surfaces:` discipline. Those files do not exist. The canonical gate implementation actually checks `# scope_surfaces:` comments inside `gate/check_architecture_sync.sh`, while the generated `gate/rules/rule-110.sh` shadow file still checks `docs/governance/rules/rule-*.md` front matter. The gate is green because it checks the monolith comments, not because the claimed rule-card model exists.

## Assumptions And Strongest Interpretation

Assumption: `docs/logs/releases/2026-05-20-l0-rc16-recurring-family-comprehensive-closure-and-meta-scope.en.md` is the latest release because `bash gate/lib/latest_release.sh docs/logs/releases` resolves to that file.

Strongest valid interpretation: the team is asking whether L0 is now stable enough to serve as the baseline authority for future implementation, not whether every W2+ runtime capability is implemented.

Root cause: rc16 correctly recognized the recurring-family problem, but its META closure mixed two authority models: "gate-layer rules declared by canonical gate-script comments" and "first-class governance rule cards with `scope_surfaces:` front matter". Evidence: `gate/check_architecture_sync.sh:5597-5626` implements Rule 110 over gate-script comments, while `docs/governance/enforcers.yaml:1371`, ADR-0093 lines 107-111 / 188-189, and the rc16 release line 78 describe missing rule cards 107/108/109/110.

## What Looks Architecturally Healthy

- The Java reactor remains coherent at L0: `agent-service` owns shipped HTTP/service runtime, `agent-execution-engine` owns engine runtime and orchestration contracts, `agent-bus` owns ingress + S2C cross-plane SPI, and `agent-middleware` owns runtime hooks.
- Dynamic planning is not overclaimed. `plan-projection.v1.yaml` is still `design_only`, with W2 promotion criteria stated clearly.
- Skill capacity now has the right W1/W2 split: W1 returns `SkillResolution.reject(SuspendReason.RateLimited)`, and actual Run/step suspension is deferred to Rule R-K.c / W2 scheduler admission.
- Memory and knowledge ownership remain properly bounded: `GraphMemoryRepository` is a platform SPI, not a blanket claim over customer business ontology.
- ADR-0092 correctly keeps Agent-OS / hardware co-design items outside this repository's L0 authority.

## Findings

### P1-1 - Rule 110 claims rule-card dogfooding, but rule cards 107-110 do not exist

**Evidence**

- `Test-Path docs/governance/rules/rule-107.md`, `rule-108.md`, `rule-109.md`, and `rule-110.md` all return `False`.
- `docs/logs/reviews/2026-05-20-l0-rc15-post-closure-architecture-review-response.en.md:18` lists those four non-existent files under `affects_artefact`.
- `docs/logs/reviews/2026-05-20-l0-rc15-post-closure-architecture-review-response.en.md:120` says "rule cards 107/108/109/110 each declare `scope_surfaces:` AND carry >=2 fixtures".
- `docs/logs/releases/2026-05-20-l0-rc16-recurring-family-comprehensive-closure-and-meta-scope.en.md:78` makes the same dogfooding claim.
- `docs/adr/0093-rc16-recurring-family-comprehensive-closure-and-meta-scope-completeness.yaml:107-111` says Rule 110 applies to rule cards with `scope_surfaces:` front matter and that cards 107/108/109/110 dogfood it.
- `docs/adr/0093-rc16-recurring-family-comprehensive-closure-and-meta-scope-completeness.yaml:188-189` says `rule-107.md` through `rule-110.md` are first-class governance documents.
- `docs/governance/enforcers.yaml:1371` repeats that E155 checks rule cards in `docs/governance/rules/rule-*.md`.
- The actual canonical implementation at `gate/check_architecture_sync.sh:5597-5626` checks `# scope_surfaces:` comments in `gate/check_architecture_sync.sh`, not rule-card front matter.
- The Rule 110 self-tests at `gate/test_architecture_sync_gate.sh:5663-5704` also use synthetic gate-script comments, not synthetic rule cards.

**Why this matters**

This is a META-layer authority conflict. rc16's key claim is that future prevention rules must declare their scope and prove it with fixtures. But the published authority says the declaration lives in rule-card front matter, while the shipped gate and fixtures use gate-script comments. A future architecture team can follow ADR-0093 literally, add `scope_surfaces:` to a rule card, and receive no enforcement from the canonical gate if the monolith comment is missing. Conversely, the current release claims four first-class cards that are absent.

**Recommendation**

Choose one model and make every authority surface agree:

1. If 107-110 are gate-layer only, rewrite ADR-0093, the rc16 release, the rc16 response, and E152-E155 to say the authoritative scope declaration is the gate-script `# scope_surfaces:` comment. Remove the non-existent `docs/governance/rules/rule-107.md` through `rule-110.md` paths from `affects_artefact`. Change E152-E155 `constraint_ref` from `CLAUDE.md Rule 107/108/109/110` to `Gate Rule 107/108/109/110`.
2. If 107-110 are meant to have first-class rule cards, create the four cards, add `scope_surfaces:` front matter, and update the canonical Rule 110 implementation and fixtures to check those cards.

Either way, add an `affects_artefact` path-existence check for post-W1 review/response front matter so non-existent files cannot be listed as closure evidence.

### P1-2 - The generated `gate/rules` shadow corpus is stale, and Rule 92 only checks file presence

**Evidence**

- `gate/check_architecture_sync.sh:5597-5626` implements Rule 110 against canonical gate-script comments.
- `gate/rules/rule-110.sh:10-38` implements Rule 110 against `docs/governance/rules/rule-*.md` front matter, which is a different semantic rule.
- `docs/governance/rules/rule-G-5.md:43` says `gate/rules/` is an IDE-only generated artifact refreshed by `gate/lib/extract_rules.sh`.
- `docs/governance/enforcers.yaml:1125` says every canonical header must have a matching generated `gate/rules/rule-NNN.sh` file.
- `gate/check_architecture_sync.sh:4443-4475` enforces only file existence for `gate/rules/rule-NNN.sh`, not content parity.
- `gate/lib/extract_rules.sh:37` still terminates extraction on `^# Summary$`, while the current canonical boundary is `# === END OF RULES ===` (`docs/governance/rules/rule-G-5.md:25` and `gate/check_architecture_sync.sh:4330-4331`).
- `bash gate/check_parallel.sh` still passes 122/122, so the current gate does not catch this shadow-corpus semantic drift.

**Why this matters**

The production gate consumes the monolith, so this is not a Java/runtime failure. It is still L0-relevant because the repository explicitly keeps `gate/rules/` as a generated inspection corpus. Reviewers and maintainers reading `gate/rules/rule-110.sh` see a different Rule 110 than the one CI executes. This is the same family as prior shadow-corpus precision defects: a generated authority surface exists, is described as refreshed, and is stale while gates remain green.

**Recommendation**

- Update `gate/lib/extract_rules.sh` to terminate on `# === END OF RULES ===`, matching the canonical and parallel gate extractors.
- Regenerate `gate/rules/` so `rule-107.sh` through `rule-110.sh` match the canonical monolith.
- Strengthen Rule G-5.d / Gate Rule 92 from "matching file exists" to content freshness. A stable digest of each extracted canonical body compared with the committed `gate/rules/rule-NNN.sh` body is enough.
- Add a Rule 92 negative fixture where the generated file exists but contains stale body text; the gate should fail.

### P2-1 - Contract catalog metadata still labels itself rc15 after rc16 contract edits

**Evidence**

- `docs/contracts/contract-catalog.md:4` still says "Last refreshed: 2026-05-20 (rc15 - structural-carrier parity + terminal-state scope per ADR-0088 + ADR-0089 + ADR-0090 + ADR-0091)".
- The rc16 response lists `docs/contracts/contract-catalog.md` under `affects_artefact`.
- The rc16 release says contract-catalog rows were migrated for namespaced rules as part of Family C.
- ADR-0093 is not represented in the catalog header even though rc16 changed active catalog content.

**Why this matters**

The catalog body is current enough to pass the functional gates, but the metadata tells readers that the last meaningful refresh was rc15. This is the same freshness family that was previously fixed for rc13 -> rc15, now repeated by rc16.

**Recommendation**

- Refresh the contract catalog header to rc16 / ADR-0093.
- Add a lightweight freshness check: if the latest release or response lists `docs/contracts/contract-catalog.md` under affected artifacts, the catalog header must mention the same wave or ADR unless the line carries a historical marker.

### P2-2 - Logs-folder policy canonical marker conflicts with its first rc16 usage

**Evidence**

- `docs/governance/logs-folder-policy.md:28` defines the canonical marker as `Historical artifact frozen at SHA <40-char-sha>`.
- `docs/logs/reviews/2026-05-20-l0-rc14-post-closure-architecture-review-response.en.md:20` uses the short SHA `8a733ca`.
- `docs/governance/logs-folder-policy.md:59` also says older release notes need `Historical artifact frozen at SHA <merge-sha>`, which is less strict than `<40-char-sha>`.

**Why this matters**

The policy deliberately avoids gate enforcement, which is reasonable. But if the active policy says the canonical marker uses a 40-character SHA, the first rc16-applied marker should either follow that pattern or the policy should explicitly allow short merge SHAs. Otherwise reviewers will keep arguing about whether the marker is compliant.

**Recommendation**

- Either update the rc14 response marker to the full 40-character merge SHA, or relax the policy text from `<40-char-sha>` to `<merge-sha>` / `<short-or-full-merge-sha>`.
- Keep it ungated if that is the intended logs-folder policy; just remove the self-contradiction.

## Overdesign Assessment

No material overdesign was found in the core architecture.

The service decomposition, engine contract split, S2C location, memory/knowledge boundary, and dynamic-planning staging are proportionate for L0. The only caution is process-layer complexity: every new prevention rule adds value only if its own authority surfaces stay synchronized. rc16's Rule 110 direction is good, but its current closure mixes rule-card and gate-comment authority models.

## Required Closure Criteria

Before declaring L0 complete:

1. Resolve the Rule 110 authority model: gate-script comments or rule-card front matter, but not both.
2. Remove or create the claimed `docs/governance/rules/rule-107.md` through `rule-110.md` artifacts.
3. Align ADR-0093, the rc16 release, the rc16 response, and E152-E155 with the chosen model.
4. Regenerate `gate/rules/` and fix `gate/lib/extract_rules.sh` to use the canonical END marker.
5. Strengthen Rule 92 to catch stale generated rule body content, not only missing files.
6. Refresh contract-catalog metadata to rc16 / ADR-0093.
7. Reconcile the logs-folder policy marker format with the marker actually applied.

## Verification Performed

- `bash gate/lib/latest_release.sh docs/logs/releases` -> `docs/logs/releases/2026-05-20-l0-rc16-recurring-family-comprehensive-closure-and-meta-scope.en.md`.
- `bash gate/check_parallel.sh` -> PASS; 122 rules executed.
- `bash gate/test_architecture_sync_gate.sh` -> PASS; 202/202 self-test fixtures.
- `python gate/build_architecture_graph.py --check --no-write` -> PASS; 396 nodes / 615 edges.
- `./mvnw.cmd clean verify` -> PASS; Maven reactor build success across all modules.
- `git diff --check` -> PASS.
- `git status --short` -> clean before this review document was added.

The green verification result is meaningful. The remaining blocker is META-authority consistency, not Java service implementation correctness.
