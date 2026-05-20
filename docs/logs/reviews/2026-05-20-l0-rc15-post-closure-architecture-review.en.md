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
  - skill capacity
  - memory and knowledge boundaries
  - dynamic planning scope
responds_to:
  - docs/logs/releases/2026-05-20-l0-rc15-structural-carrier-parity-and-terminal-state-scope.en.md
  - docs/logs/reviews/2026-05-20-l0-rc14-post-closure-architecture-review-response.en.md
  - docs/logs/reviews/2026-05-20-spring-ai-ascend-ultimate-architecture-ledger-response.en.md
related_adrs:
  - ADR-0070
  - ADR-0072
  - ADR-0074
  - ADR-0085
  - ADR-0086
  - ADR-0091
  - ADR-0092
---

# L0 rc15 Post-Closure Architecture Review

## Verdict

Do not publish a no-findings L0 completion release note yet.

The rc15 implementation state is strong. The Java reactor verifies, the architecture gate verifies, the gate self-test harness verifies, and the graph baseline is internally consistent at 386 nodes / 594 edges. The main L0 module boundaries also look healthy: `agent-service` owns the shipped service runtime, `agent-execution-engine` owns engine runtime and orchestration contracts, `agent-bus` owns cross-plane envelopes and S2C, and the memory / knowledge / planning surfaces remain correctly scoped instead of half-shipped.

The remaining issue is narrower but still L0-significant: the skill-capacity authority chain has not fully converged after the rc11/rc15 semantic narrowing. Some authoritative human and machine-readable documents still describe Rule R-K.b as deferred, still cite the old overclaiming test method name, or still say over-cap callers are suspended as a current companion-rule fact. That conflicts with the active kernel, Java behavior, and the new rc15 release claim.

## Assumptions And Strongest Interpretation

Assumption: `docs/logs/releases/2026-05-20-l0-rc15-structural-carrier-parity-and-terminal-state-scope.en.md` is the latest architecture release because it is the newest resolver-selected release under `docs/logs/releases`.

Strongest valid interpretation: the architecture team is asking whether L0 is now reliable as the baseline authority for future Java microservice and agent-driven work, not whether all W2-W4 runtime features are implemented today.

Root cause: rc15 fixed the main shipped surfaces, but the sweep and prevention layer still do not cover active rule/principle card prose, principle-coverage deferred edges, and companion-rule explanatory text. Evidence: `CLAUDE.md:184` and `docs/governance/rules/rule-R-K.md:24` say actual Run/step suspension is deferred to Rule R-K.c, while `docs/governance/principle-coverage.yaml:153` still lists `Rule-R-K.b` as a deferred operationaliser and `docs/governance/principles/P-K.md:30` / `docs/governance/rules/rule-R-K.md:23` still cite the removed method `SkillCapacityResolutionIT.suspendsSecondCallerWhenCapacityIsOne`.

## What Looks Architecturally Healthy

- The L0 service decomposition is proportionate. The current eight-module reactor has clear microservice ownership and no obvious over-splitting at L0.
- The engine contract split is sound: `engine.runtime` carries dispatch authority, `engine.spi` carries pluggability, and `engine.orchestration.spi` carries orchestration control primitives.
- Dynamic planning is correctly bounded. `plan-projection.v1.yaml` remains design-only, and no current doc appears to claim a fully shipped planner loop.
- Memory and knowledge remain correctly separated by ownership authority. Graph memory is a service-side SPI / adapter seam, while ontology and business knowledge are not prematurely treated as platform-owned truth.
- ADR-0092 is a useful scope boundary. Agent-OS / openEuler / NPU / hardware co-design items are correctly declared outside this repository's L0 authority rather than forced into the Java service architecture.
- I do not see a material overdesign problem. The risk is authority drift after necessary ratchets, not excessive runtime structure.

## Findings

### P1-1 - Rule R-K active/deferred state is still contradictory across authoritative sources

**Evidence**

- `CLAUDE.md:184` says the shipped surface returns `SkillResolution.reject(SuspendReason.RateLimited)` and the actual `Run` / dependent-step suspension transition is deferred to Rule R-K.c.
- `docs/governance/rules/rule-R-K.md:24` and `:32` repeat that Rule R-K.c is the W2 scheduler-admission deferred transition.
- `docs/CLAUDE-deferred.md:290` defines "Rule R-K.c - Run/Step Suspension Transition [Deferred to W2]".
- `docs/governance/principle-coverage.yaml:153` still lists `Rule-R-K.b` as a deferred operationaliser with the comment "ResilienceContract.resolve runtime enforcement - W1.x Phase 6".
- `docs/governance/principles/P-K.md:30` says Rule R-K.b was activated, but cites the old method name `SkillCapacityResolutionIT.suspendsSecondCallerWhenCapacityIsOne`.
- `docs/governance/rules/rule-R-K.md:23` and `:31` also cite the same removed method name.
- `agent-service/src/test/java/ascend/springai/service/runtime/resilience/SkillCapacityResolutionIT.java:42` now contains `rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne`, which is the correct shipped behavior.
- `docs/governance/rules/rule-R-H.md:24` still summarizes the companion rule as "over-cap callers are SUSPENDED", even though that is now the deferred R-K.c / W2 scheduler behavior.
- `docs/CLAUDE-deferred.md:366` still refers to "Rule R-K.b runtime integration" as if it lands alongside the W2 wake-pulse machinery.

**Why this matters**

This is not cosmetic naming drift. Skill capacity is one of the core agent-driven architecture contracts. At W1, the Java and Rule R-K kernel define a decision envelope. At W2, scheduler admission translates the rejected decision into an actual suspended Run or step. If the principle graph says R-K.b is deferred while the rule card says R-K.b is active, and companion prose says over-cap callers are already suspended, the L0 authority graph gives three different answers to the same architectural question.

**Recommendation**

- Update `docs/governance/principle-coverage.yaml` so P-K's deferred operationaliser is Rule R-K.c, not Rule R-K.b.
- Update `docs/governance/principles/P-K.md` and `docs/governance/rules/rule-R-K.md` to cite `SkillCapacityResolutionIT.rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne`.
- Rewrite `docs/governance/rules/rule-R-H.md:24` to say the current R-K companion emits a rate-limited decision envelope and that actual suspension is deferred to R-K.c.
- Rewrite `docs/CLAUDE-deferred.md:366` so it references Rule R-K.c / W2 scheduler admission rather than Rule R-K.b runtime integration.
- Regenerate the architecture graph after the principle-coverage correction.

### P1-2 - The rc15 prevention layer still misses rule/principle card evidence anchors

**Evidence**

- `bash gate/check_parallel.sh` passes all 118 rules.
- `bash gate/test_architecture_sync_gate.sh` passes all 194 self-test cases.
- `python gate/build_architecture_graph.py --check --no-write` validates 386 nodes / 594 edges.
- Despite that, active rule and principle cards still cite a non-existent Java method name:
  - `docs/governance/principles/P-K.md:30`
  - `docs/governance/rules/rule-R-K.md:23`
  - `docs/governance/rules/rule-R-K.md:31`
- The existing anchor-resolution rule catches `enforcers.yaml` artifacts, but not free-text evidence anchors in active rule/principle cards.

**Why this matters**

rc15 explicitly renamed the test to remove the old terminal-state overclaim. The enforcer row was updated, so the current gate sees a valid anchor. But the authority cards that humans and agents read still cite the old method. This is precisely the kind of cross-authority drift L0 is trying to eliminate.

**Recommendation**

- Add an authority-evidence anchor check for active `docs/governance/rules/*.md` and `docs/governance/principles/*.md`.
- Scope the check narrowly to code-like evidence tokens that match `<ClassName>.<methodName>` or `path#anchor`, so it does not become an expensive prose parser.
- If a method reference is intentionally historical, require an adjacent marker such as `historical`, `renamed from`, or `pre-rc15`.
- Add positive and negative self-test fixtures for a renamed Java test method in a rule card.

### P2-1 - Namespaced rule authority is incomplete in principle cards and engine contract docs

**Evidence**

- `docs/governance/principles/P-K.md:7` still has `enforced_by_rules: [41]` even though the human body says "Enforced by Rule R-K".
- The same numeric-frontmatter pattern remains across multiple principle cards, for example `P-A.md`, `P-B.md`, `P-C.md`, `P-D.md`, `P-E.md`, `P-F.md`, `P-G.md`, `P-H.md`, `P-I.md`, `P-J.md`, `P-L.md`, and `P-M.md`.
- `docs/governance/principle-coverage.yaml` already uses namespaced rules such as `Rule-R-K`, `Rule-R-M`, and `Rule-M-2.a`, so the human principle-card front matter now disagrees with the machine coverage source.
- `agent-execution-engine/ARCHITECTURE.md:9`, `:37`, `:47`, `:61`, and `:63` still use numeric-only Rule 43 / 44 / 48 references on current engine contract text.
- `docs/contracts/engine-envelope.v1.yaml:16` and `:34` still use numeric-only Rule 48.c / Rule 43 references.
- `docs/contracts/contract-catalog.md:83` and `:103` still use numeric-only Rule 44 / Rule 48 references, while nearby rows have already adopted namespaced aliases such as Rule R-M.a.

**Why this matters**

ADR-0086 moved the live authority vocabulary to D- / R- / G- / M- namespaced rules. Numeric aliases are acceptable as historical parentheticals, but current contract surfaces should not require readers to remember the old mapping. The current state creates a split-brain authority model: machine coverage uses namespaced rules, some human docs use namespaced rules, and other active contract docs still use numeric-only references.

**Recommendation**

- Convert principle-card front matter to namespaced values or explicitly mark numeric values as `legacy_enforced_by_rules`.
- Convert active engine contract references to the current namespaced forms:
  - Rule 43 -> Rule R-M.a
  - Rule 44 -> Rule R-M.b
  - Rule 48 / 48.c -> Rule M-2.a / the relevant deferred sub-clause
- Preserve numeric values only as `(formerly Rule N)` where useful.
- Add a ratchet that rejects numeric-only rule references in current contract docs, principle-card front matter, and module architecture docs unless the line is historical or a gate-rule implementation reference.

### P2-2 - The rc15 closure response contains stale graph verification evidence

**Evidence**

- `docs/logs/reviews/2026-05-20-l0-rc14-post-closure-architecture-review-response.en.md:74` says graph regeneration wrote 384 nodes / 577 edges.
- The rc15 release note correctly says the baseline moved to 386 nodes / 594 edges at `docs/logs/releases/2026-05-20-l0-rc15-structural-carrier-parity-and-terminal-state-scope.en.md:95` and `:115`.
- `docs/governance/architecture-status.yaml:99-100` also says 386 / 594.
- `python gate/build_architecture_graph.py --check --no-write` confirms 386 / 594 today.

**Why this matters**

The canonical baseline is correct, so this is not a blocking graph implementation bug. But the closure response is part of the published audit trail. A reviewer reading that response sees a different graph result than the final release, which weakens the credibility of the self-check narrative.

**Recommendation**

- Update the rc15 closure response verification block to either:
  - show the final 386 / 594 result, or
  - explicitly mark 384 / 577 as an intermediate pre-ledger snapshot before ADR-0092 and the ledger response landed.
- Extend the release-note numeric-truth check to closure-response verification blocks, at least for the latest response file linked by the latest release.

## Overdesign Assessment

No material L0 overdesign was found.

The current architecture is intentionally layered, but the layering is doing useful work:

- Engine runtime vs engine SPI prevents heterogeneous engine dispatch from becoming a side-channel in `agent-service`.
- Skill capacity as a decision envelope is the right W1 contract because it avoids pretending that a scheduler exists before W2.
- Dynamic planning, S2C production transport, data-locality routing, and Agent-OS / hardware acceleration are correctly deferred or scoped out.
- The number of gates is high, but most recent failures show why these gates exist: they prevent authority drift after architectural refactors.

The one caution is prevention-layer granularity. The next fix should widen existing Rule G-3 / G-8 style checks where possible rather than adding another top-level rule for every textual drift class.

## Required Closure Criteria

Before declaring L0 complete:

1. P-K must have a single active/deferred truth: R-K.b active decision-envelope behavior, R-K.c deferred Run/step suspension transition.
2. All active R-K evidence anchors must resolve to the current Java method name `rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne`.
3. Rule R-H and deferred S2C prose must not describe R-K.b as the W2 suspension-transition authority.
4. Principle-card front matter and machine `principle-coverage.yaml` must use the same namespaced rule vocabulary, or the legacy numeric front matter must be explicitly marked non-authoritative.
5. Current engine contract docs must use Rule R-M / Rule M-2 namespaced references, with numeric aliases only as historical parentheticals.
6. The rc15 closure response must reconcile its graph verification evidence with the final 386 / 594 graph baseline.
7. A prevention fixture must prove that stale rule/principle-card method evidence fails closed.

## Verification Performed

- `bash gate/check_parallel.sh` -> PASS; 118 rules executed.
- `bash gate/test_architecture_sync_gate.sh` -> PASS; 194/194 self-test fixtures.
- `python gate/build_architecture_graph.py --check --no-write` -> PASS; 386 nodes / 594 edges.
- `./mvnw.cmd clean verify` -> PASS; Maven reactor build success across all modules.
- `git status --short` -> clean before this review document was added.

The green verification result is meaningful. The remaining blocker is authority-system consistency, not Java implementation correctness.
