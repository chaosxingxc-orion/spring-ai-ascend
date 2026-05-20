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
  - docs/logs/releases/2026-05-20-l0-rc13-runtime-core-dissolution-and-ingress-mandate.en.md
  - docs/logs/reviews/2026-05-20-l0-rc13-runtime-core-dissolution-proposal.en.md
related_adrs:
  - ADR-0068
  - ADR-0078
  - ADR-0079
  - ADR-0086
  - ADR-0087
  - ADR-0088
  - ADR-0089
---

# L0 rc13 Post-Ratchet Architecture Review

## Verdict

Do not publish a no-findings L0 completion release note yet.

The Java codebase is green and the rc13 architectural direction is sound. Dissolving `agent-runtime-core` reduces the L0 mental model, puts the Run/idempotency kernel back in `agent-service`, puts orchestration SPI in `agent-execution-engine`, and puts both S2C and ingress traffic surfaces under `agent-bus`. This is not overdesign. It is a useful simplification of the prior kernel-shim module.

The remaining blockers are authority and constraint-system defects. Several canonical documents still describe pre-rc13 ownership as current truth, while the executable code and contract catalog describe the new ownership. The main architecture gate passes, so the current prevention layer is not yet strong enough to protect the L0 authority system from partial ratchets.

## Assumptions And Strongest Interpretation

Assumption: `docs/logs/releases/2026-05-20-l0-rc13-runtime-core-dissolution-and-ingress-mandate.en.md` is the latest release note because `bash gate/lib/latest_release.sh docs/logs/releases` resolves to that file.

Strongest valid interpretation: the architecture team is not asking whether every W1-W4 capability is implemented. The question is whether the L0 contract, authority, and constraint system is internally consistent enough to be treated as the governing baseline for future Java microservice and agent-driven work.

Root cause: rc13 performed a correct module dissolution in code and several high-value authority surfaces, but did not finish the same move across every canonical current-state surface or add gates that compare those surfaces against each other. Evidence: `architecture-graph.yaml` now says `node_count: 376` / `edge_count: 558`, while `architecture-status.yaml` still carries `architecture_graph_nodes: 363` / `architecture_graph_edges: 539`; `CLAUDE.md` Rule R-M still points S2C to `ascend.springai.service.runtime.s2c.spi`, while `contract-catalog.md` and `agent-bus/module-metadata.yaml` point to `ascend.springai.bus.spi.s2c`.

## What Looks Architecturally Healthy

The agent architecture is directionally coherent:

- `agent-service` as compute/control owner for Run state, idempotency, API handlers, and reference runtime implementations is appropriate for W0/W1.
- `agent-execution-engine` as executor, engine, and orchestration SPI owner is appropriate, especially after `RunMode` was co-located with orchestration SPI to avoid recreating the old back-dependency.
- `agent-bus` owning both S2C (`bus.spi.s2c`) and C2S ingress (`bus.spi.ingress`) is the right cross-plane boundary. Keeping `IngressGateway` as a design-only SPI at W1 is a good anti-drift contract, not premature runtime implementation.
- Dynamic planning, skills, memory, knowledge, and evolution are not over-implemented at L0. The current posture keeps dynamic planning as contract/design surface, keeps skill-capacity distinct from operation-policy resilience, keeps graph memory near the service adapter host, and keeps evolution export explicitly scoped.
- The local verification result is strong at the implementation layer: `bash gate/check_parallel.sh`, `bash gate/test_architecture_sync_gate.sh`, `python gate/build_architecture_graph.py --check --no-write`, and `./mvnw clean verify` all pass.

## Findings

### P1-1 - Canonical graph baseline still disagrees with the generated graph

**Evidence**

- `docs/governance/architecture-graph.yaml` declares `node_count: 376` and `edge_count: 558`.
- `python gate/build_architecture_graph.py --check --no-write` reports `Built graph (not written): 376 nodes, 558 edges` and `Graph validation: OK`.
- `docs/governance/architecture-status.yaml` still declares `architecture_graph_nodes: 363` and `architecture_graph_edges: 539`.
- The comments in `architecture-status.yaml` explicitly say rc13 will regenerate and reconcile the live header count in the same commit, but the reconciliation did not happen.
- `bash gate/check_parallel.sh` still passes.

**Why this matters**

`architecture-status.yaml#architecture_sync_gate.baseline_metrics` is listed as the canonical baseline source. If the generated graph and the canonical baseline disagree, downstream release notes, README summaries, and agent self-checks can all claim incompatible L0 states while the gate remains green.

**Recommendation**

- Update `architecture-status.yaml#architecture_sync_gate.baseline_metrics.architecture_graph_nodes` to `376` and `architecture_graph_edges` to `558`, unless a subsequent graph regeneration intentionally changes both values.
- Add a prevention check that compares `architecture-status.yaml` graph metrics against the live `architecture-graph.yaml` header or the no-write graph builder output. The check should fail before release notes can claim a reconciled baseline.

### P1-2 - Rule R-M still points S2C to the pre-rc13 package

**Evidence**

- `CLAUDE.md` Rule R-M says S2C invocation goes through `S2cCallbackEnvelope` + `S2cCallbackTransport` under `ascend.springai.service.runtime.s2c.spi`.
- `docs/governance/rules/rule-R-M.md` repeats the same package path.
- `docs/contracts/s2c-callback.v1.yaml` still comments that the Java records live under `ascend.springai.service.runtime.s2c.spi`.
- The actual Java package is `agent-bus/src/main/java/ascend/springai/bus/spi/s2c`.
- `agent-bus/module-metadata.yaml` declares `ascend.springai.bus.spi.s2c`.
- `docs/contracts/contract-catalog.md` correctly lists `S2cCallbackTransport` owner `agent-bus` and package `ascend.springai.bus.spi.s2c`.

**Why this matters**

This is a direct active-contract contradiction on one of the most important agent-driven surfaces: server-to-client capability invocation. The rule kernel, rule card, YAML contract comment, module metadata, contract catalog, and source code do not agree on the authoritative package.

**Recommendation**

- Rewrite Rule R-M sub-clause `.d` in both `CLAUDE.md` and `docs/governance/rules/rule-R-M.md` to name `ascend.springai.bus.spi.s2c`.
- Rewrite `docs/contracts/s2c-callback.v1.yaml` comments to match the bus package.
- Update any enforcer descriptions that still assert purity for `ascend.springai.service.runtime.s2c.spi`.
- Add a contract-path parity check for SPI packages: every SPI package named by a rule kernel or contract YAML comment must exist in exactly one `module-metadata.yaml#spi_packages` entry and on disk.

### P1-3 - Root ARCHITECTURE.md still contains active current-state constraints for the dissolved module

**Evidence**

- `ARCHITECTURE.md` correctly documents the rc13 eight-module state in the module layout section.
- The same file still has active constraint text saying `agent-service` depends on `agent-runtime-core`, `agent-execution-engine` depends on `agent-runtime-core`, and `agent-runtime-core` depends on no inner peer.
- The service-layer constraint still says the Service Layer includes a shared kernel in `agent-runtime-core`.
- The verification matrix still describes `RuntimeMustNotDependOnPlatformTest` as spanning `agent-service + agent-runtime-core` post-ADR-0079.

**Why this matters**

The root architecture document now carries both post-ADR-0088 truth and post-ADR-0079 truth as if both were current. This is more severe than historical prose drift because the stale references are in constraint and verification guidance sections, not only in background narrative.

**Recommendation**

- Rewrite the module-direction constraint to the post-ADR-0088 DAG: Run/idempotency in `agent-service`; orchestration SPI in `agent-execution-engine`; S2C and ingress in `agent-bus`; no `agent-runtime-core` node.
- Rewrite the service-layer constraint so serverless-friendly primitives are described by semantic home, not by the removed kernel-shim module.
- Rewrite the verification matrix entry for `RuntimeMustNotDependOnPlatformTest` so it scopes only current packages and modules.
- Treat root `ARCHITECTURE.md` constraint sections as a stronger scan class than historical narrative. Marker-based deleted-module gates are too permissive when a sentence contains `post-ADR-0079` but is still written as current architecture.

### P1-4 - architecture-status.yaml still contains current allowed_claims for the pre-rc13 module topology

**Evidence**

- `architecture-status.yaml` still says the Maven-level direction constraint is `agent-service` to `agent-runtime-core`, `agent-execution-engine` to `agent-service`, and `agent-runtime-core` to everything else.
- It still says the Service Layer includes shared kernel in `agent-runtime-core` and that SPI primitives live in `agent-runtime-core`.
- It still says each of the 9 reactor modules includes `agent-runtime-core`, even though rc13 returns the reactor to 8 modules.
- It still says `agent-runtime-core` declares SPI packages for orchestration / runs / S2C.
- These entries pass current gates because the deleted module name appears near historical or rc13 markers.

**Why this matters**

`allowed_claim` is not a changelog field. It is used as the canonical per-capability shipped/deferred ledger. A current allowed claim that names the wrong module topology will be copied into future reviews, release notes, and automated checks.

**Recommendation**

- Rewrite the affected allowed claims to state the post-ADR-0088 topology directly.
- Keep ADR-0079 history only in explicitly historical clauses, not as the current allowed claim.
- Strengthen Rule 87 or add a new rule that distinguishes historical narration from current-state claim grammar. In particular, a same-line marker such as `post-ADR-0079` should not exempt a sentence containing "now reads", "lives in", "declares", "includes", or "each of the N modules" when that sentence contradicts live module metadata.

### P1-5 - The prevention layer passes despite cross-authority contradictions

**Evidence**

- `bash gate/check_parallel.sh` passes all 117 rules.
- `bash gate/test_architecture_sync_gate.sh` passes 182/182 tests.
- The same corpus still contains the P1 graph baseline mismatch, the P1 S2C package mismatch, and stale current-state topology claims in root architecture and architecture-status.

**Why this matters**

L0 does not require every future runtime feature to be complete, but it does require the governing contract system to be reliable. The current gates catch many single-surface issues, but they do not consistently compare canonical surfaces against each other.

**Recommendation**

Add a small number of cross-authority parity checks rather than broadening every existing scanner:

- Graph baseline parity: `architecture-status.yaml` baseline metrics equal generated graph header.
- SPI path parity: rule kernels, contract catalog, contract YAML comments, module metadata, and source directories agree on SPI package ownership.
- Module topology parity: root POM module list, repository counts, `architecture-status.yaml` allowed claims, root `ARCHITECTURE.md` module-direction constraints, and module metadata agree on live modules.
- Current-claim grammar: deleted-module markers cannot exempt present-tense current-state claims.

### P2-1 - Contract catalog still carries old numeric rule references after the namespace ratchet

**Evidence**

- `docs/contracts/contract-catalog.md` still says `GraphMemoryRepository` and `SkillCapacityRegistry` are "Rule 11" compliant.
- `docs/contracts/contract-catalog.md` still says `ResilienceContract.resolve(tenant, skill)` is per "Rule 41.b".
- `docs/contracts/s2c-callback.v1.yaml` still mentions "Rule 41.b ResilienceContract.resolve".

**Why this matters**

This is lower severity than the stale S2C package path, but it weakens the rc12 namespace-ratchet closure claim. Contract readers still have to translate between old numeric rules and the canonical D/R/G/M rule namespace.

**Recommendation**

- Replace old numeric references with the canonical rule names, optionally keeping the numeric identifier only as a parenthetical historical alias.
- Add contract-catalog scanning to the namespace authority completeness check.

### P2-2 - Engine classes still use a `service.runtime.engine` package inside `agent-execution-engine`

**Evidence**

- `agent-execution-engine` contains `EngineRegistry` and `EngineEnvelope` under `ascend.springai.service.runtime.engine`.
- `agent-execution-engine/ARCHITECTURE.md` says this is preserved for backwards source compatibility per ADR-0079.
- ADR-0079 is now superseded by ADR-0088.

**Why this matters**

This may be an intentional compatibility choice, but it is now an exception against the semantic-home model that rc13 otherwise strengthens. In a 0.x L0 contract, keeping a `service.runtime` package in the engine module may confuse package ownership, ArchUnit scope, and future SPI documentation.

**Recommendation**

- Either document this as an explicit ADR-0088 compatibility exception with an owner and sunset trigger, or rename the package before the W1 public surface hardens.
- If retained, add a rule-card note explaining why `service.runtime.engine` is owned by `agent-execution-engine` and not `agent-service`.

### P2-3 - Deploy-entrypoint deleted-module scanner does not include `agent-runtime-core`

**Evidence**

- Rule 103 checks deploy entrypoints for `agent-platform` and `agent-runtime`, with an explicit comment excluding the `-core` variant.
- rc13 widened Rule 87, Rule 94, and Rule 98 to include `agent-runtime-core`, but Rule 103 remains scoped to the two older deleted modules.

**Why this matters**

This is not currently causing a known wrong deploy artifact, but it leaves a prevention gap exactly where rc12 added a deploy-entrypoint truth rule. If a Dockerfile, compose file, chart, or CI deployment path references `agent-runtime-core`, Rule 103 will not be the rule that catches it.

**Recommendation**

- Either widen Rule 103 to include all deleted module names, including `agent-runtime-core`, or document that Rule 94 / Rule 98 intentionally own that broader deleted-name scan and Rule 103 is only the legacy deploy-entrypoint closure rule.

### P2-4 - rc13 release-note wording still sounds prospective in places

**Evidence**

- The rc13 proposal says the graph will be regenerated post-merge.
- `architecture-status.yaml` comments say rc13 will reconcile graph counts in the same commit.
- The released state still has the graph baseline mismatch.

**Why this matters**

Release notes should be evidence-bearing, not plan-bearing, once published as the team's self-check output. Prospective wording is acceptable in a proposal, but not in the authoritative release note unless clearly labeled as pending.

**Recommendation**

- In the corrective response, separate `planned during proposal` from `verified after merge`.
- Include the actual command outputs or exact metric values after the fixes land.

## Required Closure Criteria

Before declaring L0 complete, close these items:

1. `architecture-status.yaml` graph baseline equals the generated graph: `376` nodes and `558` edges, or both values are updated to the next intentional generated output.
2. Rule R-M, its rule card, the S2C YAML contract comment, enforcer descriptions, module metadata, contract catalog, and source tree all agree that S2C SPI lives under `ascend.springai.bus.spi.s2c`.
3. Root `ARCHITECTURE.md` has no active current-state constraint that treats `agent-runtime-core` as a live module.
4. `architecture-status.yaml` allowed claims for module direction, service-layer commitment, module metadata, and SPI DFX/TCK co-design describe the post-ADR-0088 topology.
5. The gate has at least one cross-authority parity check for graph metrics and at least one for SPI package ownership.
6. Old numeric rule references in active contract surfaces are either replaced by namespaced rule ids or explicitly marked as historical aliases.
7. The `service.runtime.engine` package exception is either renamed or formally accepted as a compatibility exception under ADR-0088.
8. Rule 103's `agent-runtime-core` scope decision is explicit.

## Verification Performed

- `bash gate/check_parallel.sh` - PASS; 117 rules executed.
- `bash gate/test_architecture_sync_gate.sh` - PASS; 182/182 tests.
- `python gate/build_architecture_graph.py --check --no-write` - PASS; live generated graph is 376 nodes and 558 edges.
- `./mvnw clean verify` - PASS across all current reactor modules.

The green verification result is important: the implementation is healthy. The release blocker is that the L0 authority system still allows multiple canonical sources to disagree.
