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
responds_to:
  - docs/logs/releases/2026-05-20-l0-rc14-cross-authority-parity-and-engine-semantic-home.en.md
  - docs/logs/reviews/2026-05-20-l0-rc13-post-ratchet-architecture-review-response.en.md
related_adrs:
  - ADR-0070
  - ADR-0074
  - ADR-0078
  - ADR-0088
  - ADR-0089
  - ADR-0090
---

# L0 rc14 Post-Closure Architecture Review

## Verdict

Do not publish a no-findings L0 completion release note yet.

The implementation layer is healthy: the Maven reactor is green, the architecture gate is green, the gate self-test suite is green, and the generated architecture graph validates. The rc14 direction is also architecturally sound. Moving `EngineRegistry` / `EngineEnvelope` to `ascend.springai.engine.runtime`, keeping S2C and ingress in `agent-bus`, and adding Rule G-8 as a cross-authority parity gate all improve the L0 design. This is not overdesign.

However, rc14 did not finish the closure it claims. Several active contract and architecture surfaces still describe old package homes, old module topology, or old skill-capacity runtime semantics. The important pattern is that the new parity gate proves several surface classes agree, but it does not yet cover structural-carrier package homes, root architecture current constraints, or module-level terminal-state overclaims.

## Assumptions And Strongest Interpretation

Assumption: `docs/logs/releases/2026-05-20-l0-rc14-cross-authority-parity-and-engine-semantic-home.en.md` is the latest release because `bash gate/lib/latest_release.sh docs/logs/releases` resolves to that file.

Strongest valid interpretation: the architecture team is asking whether the L0 contract system is now reliable enough to act as the baseline for future Java microservice and agent-driven work, not whether all W2-W4 runtime features are implemented.

Root cause: rc14 corrected the code and several canonical ledgers, but the sweep and prevention rule were scoped to selected authority classes. Active root/module architecture text and structural-carrier catalog rows still escaped. Evidence: `EngineRegistry.java` now lives under `agent-execution-engine/src/main/java/ascend/springai/engine/runtime/`, while root `ARCHITECTURE.md`, `agent-service/ARCHITECTURE.md`, and `docs/contracts/contract-catalog.md` still name `service.runtime.engine` as the package home.

## What Looks Architecturally Healthy

- The 8-module reactor is now coherent for L0: `agent-service` owns Run/idempotency/runtime reference implementations, `agent-execution-engine` owns engine and orchestration contracts, `agent-bus` owns cross-plane ingress and S2C, `agent-middleware` owns hook middleware, and `agent-client` / `agent-evolve` remain honest skeletons.
- `IngressGateway` is correctly held as `design_only` at W1. It is a topology guardrail for future SDK work, not premature runtime code.
- `plan-projection.v1.yaml` remains correctly `design_only`; full dynamic planning is deferred, while W2 scheduler projection is explicitly bounded.
- Memory and knowledge ownership remains coherent: `GraphMemoryRepository` is a service-side SPI scaffold, and business ontology remains C-side unless delegated by explicit authority.
- Skill capacity is directionally correct in code and rule kernels: W1 returns a decision envelope, while the W2 scheduler maps that decision into actual suspension.

## Findings

### P1-1 - Engine semantic-home rename is incomplete across active architecture and contract surfaces

**Evidence**

- Current Java source lives under `agent-execution-engine/src/main/java/ascend/springai/engine/runtime/EngineRegistry.java` and `EngineEnvelope.java`.
- `docs/contracts/engine-envelope.v1.yaml` correctly says the Java record is `ascend.springai.engine.runtime.EngineEnvelope`.
- `agent-execution-engine/module-metadata.yaml` correctly says `EngineRegistry` + `EngineEnvelope` live in `engine.runtime`.
- Root `ARCHITECTURE.md` still lists `agent-execution-engine/src/main/java/ascend/springai/service/runtime/engine/` for `EngineRegistry` and `EngineEnvelope`, with text saying the package is kept and rename is deferred to W3+.
- `agent-service/ARCHITECTURE.md` still says the engine registry + envelope home is package `ascend.springai.service.runtime.engine.*`, even while the same paragraph points at the new source path.
- `docs/contracts/contract-catalog.md` still lists `EngineRegistry` and `EngineEnvelope` under `...service.runtime.engine`.
- The rc14 response and release note explicitly claim this family was closed by the rename.

**Why this matters**

`EngineRegistry` is the dispatch authority for heterogeneous engines. If active architecture and contract surfaces disagree on its package home, new implementers can add imports or documentation against the old package while all gates remain green. This is exactly the semantic-home drift rc14 was supposed to close.

**Recommendation**

- Rewrite root `ARCHITECTURE.md`, `agent-service/ARCHITECTURE.md`, and `docs/contracts/contract-catalog.md` so all current-state `EngineRegistry` / `EngineEnvelope` references use `ascend.springai.engine.runtime`.
- Remove "package preserved" / "rename deferred" wording from active current-state surfaces. Keep it only as explicitly historical ADR-0079 narration.
- Extend Rule G-8 beyond SPI package parity. Structural carriers listed in `docs/contracts/contract-catalog.md` and root/module `ARCHITECTURE.md` path claims must resolve to the real Java package and source path.

### P1-2 - Root ARCHITECTURE.md still carries current-state `agent-runtime-core` constraints

**Evidence**

- Root `ARCHITECTURE.md` still says `RunContext.tenantId()` is the sole carrier of tenant identity inside the runtime kernel "extracted to `agent-runtime-core` per ADR-0079".
- Root `ARCHITECTURE.md` still states the Service Layer includes `agent-service.service.runtime` plus "shared kernel in `agent-runtime-core`" plus engine SPI in `agent-execution-engine`.
- The same file elsewhere correctly says `agent-runtime-core` was dissolved by ADR-0088.
- `docs/governance/architecture-status.yaml` has been corrected to the post-ADR-0088 topology, so the root architecture document is now the outlier.

**Why this matters**

These are not harmless historical notes. They are inside active L0 constraint sections and define the system boundary. A reader following the root architecture document can still believe `agent-runtime-core` is part of the current Service Layer.

**Recommendation**

- Rewrite the RunContext propagation constraint to say RunContext lives under `agent-execution-engine.engine.orchestration.spi` and runtime Run state lives under `agent-service.service.runtime`.
- Rewrite the Service-Layer Microservice-Architecture Commitment to remove the live `agent-runtime-core` component.
- Extend Rule G-8.d current-claim grammar to catch phrases such as "shared kernel in <deleted-module>", "extracted to <deleted-module>", and "<deleted-module> is deployed" in active constraint sections, not only the current verb list.

### P1-3 - agent-service architecture still overclaims skill-capacity suspension semantics

**Evidence**

- `CLAUDE.md` Rule R-K says over-capacity resolution must return `SkillResolution.reject(SuspendReason.RateLimited)` and that the actual Run/dependent-step suspension transition is deferred to Rule R-K.c.
- `docs/governance/rules/rule-R-K.md` repeats the same active-vs-deferred split and explains that the old "callers are SUSPENDED" wording was narrowed to a decision-envelope contract.
- `DefaultSkillResilienceContract.resolve(tenant, skill)` returns `SkillResolution.reject(new SuspendReason.RateLimited(...))` on capacity exhaustion.
- `agent-service/ARCHITECTURE.md` still says `ResilienceContract.resolve(tenant, skill)` consults `skill-capacity.yaml` and "over-cap callers are SUSPENDED, not rejected (Chronos Hydration interlock with Rule 38)".

**Why this matters**

This is an agent-driven architecture contract, not just prose style. Skill capacity controls whether the runtime admits, rejects, or suspends work. At W1, the shipped Java returns a decision envelope; the W2 scheduler admission step maps that decision into a suspension. The module architecture document still describes the deferred W2 state transition as if it shipped.

**Recommendation**

- Rewrite `agent-service/ARCHITECTURE.md` to match Rule R-K: over-capacity returns `SkillResolution.reject(SuspendReason.RateLimited)` today; translating that decision into `RunStatus.SUSPENDED` is deferred to Rule R-K.c / W2 scheduler admission.
- Rename or clarify the `SkillCapacityResolutionIT.suspendsSecondCallerWhenCapacityIsOne` evidence wording if the test asserts a rejected decision envelope rather than an actual Run state transition.
- Extend the terminal-verb vs shipped-decision prevention beyond CLAUDE kernels and rule cards into active module architecture documents.

### P1-4 - Rule G-8 passes while the same defect families remain

**Evidence**

- `bash gate/check_parallel.sh` passes all 118 rules, including `cross_authority_parity`.
- `bash gate/test_architecture_sync_gate.sh` passes 190/190 tests.
- The corpus still contains the P1-1 engine package contradiction, the P1-2 root architecture deleted-module current-state contradiction, and the P1-3 skill-capacity terminal-state overclaim.

**Why this matters**

Rule G-8 is the right type of rule, but its current implementation is too narrow:

- Sub-clause .b checks SPI packages named in kernels/cards, but `EngineRegistry` and `EngineEnvelope` are structural carriers, not SPI packages.
- Sub-clause .d checks deleted-module tokens plus a limited verb list, but active architecture can still express current ownership through phrases such as "shared kernel in".
- Rule G-3.e checks terminal verbs in active kernels, but not terminal-state claims in module architecture documents.

**Recommendation**

- Add a Rule G-8 structural-carrier parity sub-check: structural carrier rows in `contract-catalog.md` must match actual Java package declarations and source paths.
- Add a root/module architecture path parity sub-check for non-SPI public contract carriers such as `EngineRegistry`, `EngineEnvelope`, `Run`, `RunContext`, `SuspendSignal`, `S2cCallbackEnvelope`, and `IngressEnvelope`.
- Broaden terminal-state overclaim detection to active architecture and contract docs when the rule/deferred split is known.

### P2-1 - rc14 release and response carry stale graph evidence

**Evidence**

- `docs/governance/architecture-status.yaml` and `docs/governance/architecture-graph.yaml` now agree on `382` nodes and `573` edges.
- The rc14 release baseline table correctly says `382` / `573`.
- The same release note later says live graph parity should write `381` nodes and `566` edges.
- The rc14 closure response says P1-1 was closed by reconciling to `381` / `566`, and its verification block repeats `381` / `566`.

**Why this matters**

The canonical graph baseline is now correct, but the published self-check evidence is internally inconsistent. A future reviewer reading only the response document will think the live graph is `381` / `566`.

**Recommendation**

- Rewrite the rc14 response and rc14 release verification snippets to use `382` / `573`.
- Extend release-note numeric truth to verification snippets and closure-evidence tables, not only the headline baseline table.

### P2-2 - contract-catalog metadata still labels itself as rc13

**Evidence**

- `docs/contracts/contract-catalog.md` says "Last refreshed: 2026-05-20 (rc13 - agent-runtime-core dissolution + ingress gateway mandate per ADR-0088 + ADR-0089)".
- rc14 changed contract-relevant package ownership and added ADR-0090, but the catalog header was not refreshed.

**Why this matters**

The contract catalog is one of the main authority surfaces for consumers. If it contains rc14 changes but labels itself rc13, readers cannot tell whether ADR-0090 is included.

**Recommendation**

- Refresh the contract catalog header to rc14 and include ADR-0090.
- Add a latest-release-to-contract-catalog freshness check when contract rows are touched by a release.

### P2-3 - Legacy numeric rule references remain in active module architecture

**Evidence**

- `agent-service/ARCHITECTURE.md` still uses references such as Rule 41, Rule 46, Rule 43, Rule 44, Rule 21, and Rule 28 in active current-state text.
- Some references are harmless historical aliases, but at least Rule 41 is attached to the stale skill-capacity semantics in P1-3.

**Why this matters**

The repository has moved its canonical rule language to D/R/G/M namespaces. Keeping numeric-only references in active module architecture increases the chance that a corrected kernel and stale module doc drift apart again.

**Recommendation**

- Convert active module architecture references to namespaced rules, keeping numeric aliases only as explicitly historical parentheticals when necessary.
- Prioritize references attached to agent runtime behavior: skill capacity, S2C capacity, engine matching, and tenant/runtime layering.

## Overdesign Assessment

I do not see a need to roll back rc14's architectural shape. The current L0 design is not over-engineered for the intended platform:

- `agent-bus` as the cross-plane owner is a simplifying boundary, not an extra runtime service obligation at W1.
- `IngressGateway` as `design_only` is appropriately lightweight.
- `engine.runtime` plus `engine.spi` plus `engine.orchestration.spi` is a reasonable split once `EngineRegistry` has become a first-class dispatch authority.
- Dynamic planning and full scheduler admission remain deferred rather than half-built.
- Memory and knowledge are split by ownership authority, not by premature storage implementation.

The remaining risk is not excessive architecture. It is incomplete authority propagation after necessary architecture changes.

## Required Closure Criteria

Before declaring L0 complete:

1. All active current-state references to `EngineRegistry` / `EngineEnvelope` must use `ascend.springai.engine.runtime` and resolving `agent-execution-engine/src/main/java/ascend/springai/engine/runtime/` paths.
2. Root `ARCHITECTURE.md` must no longer describe `agent-runtime-core` as a live Service Layer or RunContext kernel component.
3. `agent-service/ARCHITECTURE.md` must describe skill-capacity overflow as a shipped `SkillResolution.reject(SuspendReason.RateLimited)` decision envelope, with Run suspension deferred to Rule R-K.c / W2.
4. Rule G-8 or a sibling rule must cover structural-carrier package parity and module architecture terminal-state overclaims.
5. rc14 release / response graph evidence must consistently say `382` nodes and `573` edges.
6. `docs/contracts/contract-catalog.md` must be refreshed to rc14 / ADR-0090 and its structural-carrier rows must match the code.

## Verification Performed

- `bash gate/lib/latest_release.sh docs/logs/releases` -> `docs/logs/releases/2026-05-20-l0-rc14-cross-authority-parity-and-engine-semantic-home.en.md`.
- `bash gate/check_parallel.sh` -> PASS; 118 rules executed.
- `bash gate/test_architecture_sync_gate.sh` -> PASS; 190/190 tests.
- `python gate/build_architecture_graph.py --check --no-write` -> PASS; generated graph is 382 nodes and 573 edges.
- `./mvnw clean verify` -> PASS across all 8 current reactor modules.
- `git status --short` -> clean before this review document was added.

The green implementation result is meaningful. The remaining blocker is that active architecture and contract authority still allows readers to see a different L0 truth than the code and corrected ledgers express.
