---
level: L0
view: process
affects_level: L0
affects_view: process
proposal_status: review
authors: ["Codex architecture review"]
review_scope:
  - contracts
  - authority
  - constraints
  - agent-driven architecture components
responds_to:
  - docs/logs/releases/2026-05-19-l0-rc11-corrective.en.md
  - docs/logs/reviews/2026-05-19-l0-rc10-post-corrective-architecture-review-response.en.md
related_adrs:
  - ADR-0068
  - ADR-0078
  - ADR-0079
  - ADR-0081
  - ADR-0082
  - ADR-0086
---

# L0 rc11 Systematic Contract / Authority / Constraint Review

## Verdict

Do not publish a no-findings L0 completion release note yet.

The core Java microservice and agent architecture is directionally sound: the current split between `agent-service`, `agent-runtime-core`, `agent-execution-engine`, `agent-middleware`, and skeleton boundary modules is coherent; dynamic planning is correctly held as `design_only`; memory ownership is intentionally kept near the adapter host; and the engine envelope / hook / S2C surfaces are scoped as contracts rather than prematurely over-implemented runtime code.

The remaining defects are not mainly "agent architecture overdesign" defects. They are contract, authority, and constraint-system defects introduced by the rc11 governance migration and by partial coverage of active operational artefacts. Several gates pass while their own authority claims are no longer true. That is a Layer-0 blocker because L0 is supposed to define which sources are authoritative and which constraints are enforceable.

## Findings

### P1-1 - Rule namespace ratchet is incomplete and contradicts active authority

**Evidence**

- `CLAUDE.md` currently exposes 30 active rule kernels, all in the D/R/G/M namespace (`#### Rule D-1` through `#### Rule G-5`).
- `docs/adr/0086-rule-namespace-ratchet.yaml` declares the final decision as 30 active rules, with gate parser support for headers of the form `# Rule <prefix>-<id>[.<letter>]`.
- `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics.active_engineering_rules` still declares `67` and says the later consolidation will reduce the count to 30, even though the CLAUDE kernel surface has already been reduced to 30.
- `gate/check_architecture_sync.sh` still has numeric gate-section headers and zero namespaced gate-section headers.
- `gate/rules/` still uses numeric filenames such as `rule-001.sh`, while ADR-0086 says `gate/rules/rule-NNN.sh` files were renamed to namespaced rule files.
- Many rule cards have namespaced filenames but old numeric `rule_id:` front matter. Examples include `docs/governance/rules/rule-R-K.md` with `rule_id: 41`, `rule-R-F.md` with `rule_id: 36`, and `rule-D-1.md` with `rule_id: 1`.
- `docs/governance/enforcers.yaml` still contains numeric `constraint_ref` rows such as `Rule 28a` through `Rule 28i`, while ADR-0086 says every enforcer row references a current rule through the migration map.
- `docs/logs/migration-coverage-report.md` claims the post-ratchet gate passed with graph output `394 nodes, 533 edges` and that gate logic now accepts the new namespace, but the live gate and graph do not match those claims.

**Why this matters**

The authority system currently has three competing interpretations:

1. CLAUDE and ADR-0086 say the active rule system is the 30-rule namespaced system.
2. Architecture status still claims 67 active engineering rules.
3. The executable gate remains numeric at the section / shadow-rule-file layer.

That is a direct authority conflict, not just stale prose. A downstream agent cannot know whether `Rule R-K`, `Rule 41`, or both are the canonical identifier for a constraint.

**Recommendation**

- Decide whether the D/R/G/M namespace is canonical only for CLAUDE + rule cards, or canonical for every rule-bearing surface including gate headers and `gate/rules/`.
- If numeric gate headers are intentionally retained as a compatibility layer, amend ADR-0086 and the migration coverage report to state that gate sections remain numeric by design.
- If ADR-0086 is the intended target state, finish the ratchet atomically: gate headers, `gate/rules/`, rule-card `rule_id`, enforcer refs, rule history, architecture graph node ids, and baseline metrics must agree in the same change.
- Add a prevention check that compares ADR-0086's ratchet scope against live artefacts. The current gates do not catch this class.

### P1-2 - "Latest release" gates select rc9 instead of rc11

**Evidence**

- Lexicographic sorting of `docs/logs/releases/*.md` places `2026-05-19-l0-rc9-corrective.en.md` after `2026-05-19-l0-rc11-corrective.en.md`.
- `gate/check_architecture_sync.sh` Rule 33 and Rule 97 use `find ... | sort | tail -1` to identify the latest release.
- `gate/rules/rule-033.sh` and `gate/rules/rule-097.sh` use the same pattern.
- `docs/governance/rules/rule-G-2.md` documents the same lex-sort-tail behavior for latest-release numeric truth.

**Why this matters**

The release-note gates are currently validating rc9 as "latest" on this filename set, while README and the architecture team treat rc11 as latest. That invalidates the release-note pillar and graph-count checks for the actual current release.

**Recommendation**

- Stop deriving release recency from lexicographic filename order.
- Parse a structured `release_id`, `version`, or `rc` integer from front matter, or use a sortable naming convention with zero-padded rc numbers.
- Add a self-test fixture containing rc9, rc10, and rc11 filenames. The latest resolver must choose rc11.

### P1-3 - Architecture graph baseline is stale in the canonical status ledger

**Evidence**

- `docs/governance/architecture-graph.yaml` declares `node_count: 364` and `edge_count: 525`.
- Running `python gate/build_architecture_graph.py --check --no-write` reports `Built graph (not written): 364 nodes, 525 edges` and `Graph validation: OK`.
- `docs/governance/architecture-status.yaml` still declares `architecture_graph_nodes: 394` and `architecture_graph_edges: 533`.
- `docs/logs/migration-coverage-report.md` also claims `394 nodes, 533 edges`.

**Why this matters**

The generated graph is the machine-readable L0 relationship index. If the canonical baseline ledger disagrees with the live generated graph, baseline claims cannot be used as a release invariant.

**Recommendation**

- Update `architecture-status.yaml` to the live generated graph count, or remove graph counts from baseline metrics if they are intentionally not release-gated.
- Extend the baseline metric gate to compare `architecture_graph_nodes` and `architecture_graph_edges` against the live graph header.
- Consider making `build_architecture_graph.py --check --no-write --mermaid` respect `--no-write` for the Mermaid sibling as well; the current CLI text says the Mermaid file is written even under `--no-write`.

### P1-4 - Active container and operator-shape paths still target deleted modules

**Evidence**

- The root `Dockerfile` still identifies itself as an `agent-platform` Dockerfile and copies/builds `agent-platform` and `agent-runtime`.
- The same Dockerfile runs Maven with `-pl agent-platform` and copies `/workspace/agent-platform/target/agent-platform-*.jar`.
- `ops/compose.yml` defines the active `agent-service` service but builds from that root `Dockerfile`.
- `ops/runbooks/digest-pin.md` instructs operators to update `Dockerfile`, making this Dockerfile an active operational artefact rather than an archived file.
- `gate/run_operator_shape_smoke.sh` still probes `agent-platform/pom.xml`, `agent-runtime/pom.xml`, and `agent-platform/target/agent-platform-*.jar`.
- The current reactor modules are `agent-service`, `agent-runtime-core`, `agent-execution-engine`, `agent-middleware`, `agent-bus`, `agent-client`, `agent-evolve`, `spring-ai-ascend-dependencies`, and `spring-ai-ascend-graphmemory-starter`.

**Why this matters**

The canonical architecture gate passes, but an active deployment path cannot build the current repository shape. This is a contract/constraint coverage gap: deleted-module truth checks cover many markdown, YAML, Java, and ops files, but miss root `Dockerfile` and the operator-shape smoke script.

**Recommendation**

- Update the root Dockerfile to build `agent-service` with the current reactor dependencies, or mark it archived and remove `ops/compose.yml` references to it.
- Update or retire `gate/run_operator_shape_smoke.sh`; if it is intentionally dev-only and fail-closed, it should still fail for a current W4 absence reason, not for pre-Phase-C deleted modules.
- Add a deploy-build-entrypoint truth rule covering root Dockerfiles, compose build contexts, and runnable smoke scripts.

### P1-5 - Source-of-truth L0 diagrams still show deleted modules as current components

**Evidence**

- `docs/architecture-views/README.md` says the directory contains the source-of-truth diagram set for L0/C1 4+1 views.
- The same README says L0 architecture diagrams should not use the retired `Agent Runtime` wording as a core capability name, and should keep terminology aligned with the latest architecture.
- `docs/architecture-views/plantuml/l0/l0-development.puml` still defines current containers named `agent-platform` and `agent-runtime`.
- Running the standalone `gate/check_architecture_views.sh` in this workspace did not provide reliable coverage: it invoked an unexecutable `rg` path under the Codex app bundle and then failed with a missing-capability-label error. Independent text inspection still confirms stale module names in the PlantUML source.

**Why this matters**

The diagrams are explicitly described as a source-of-truth visual companion. A reviewer using the L0 development view would learn the pre-Phase-C module layout instead of the current nine-module reactor layout.

**Recommendation**

- Regenerate or rewrite the L0 development view to show the current reactor layout and the six L0 capability blocks without promoting lower-level implementation objects.
- Either wire `gate/check_architecture_views.sh` into canonical verification or downgrade the directory from "source-of-truth" to historical/non-normative until it is reliable.
- Ensure the view gate uses a portable `rg` resolution strategy under Windows/Git Bash/WSL, or document Linux-only execution for this gate.

### P1-6 - `docs/logs` is both an archive and an active governance workflow surface

**Evidence**

- `docs/logs/README.md` says `docs/logs/` is an archive partition and is "not loaded by AI agents reading the normative authority surface."
- `CLAUDE.md` says change proposals for phase-released L0/L1 artefacts flow through `docs/logs/reviews/*.md`.
- `CLAUDE.md` and Rule R-B say the most recent `docs/logs/releases/*.md` release note must mention the competitive-baseline pillars.
- The current user and architecture-team workflow uses `docs/logs/releases/` and `docs/logs/reviews/` as the live review/release exchange surface.

**Why this matters**

This creates an authority-boundary ambiguity for autonomous agents: the path is simultaneously "not loaded as normative authority" and the place where active review/release constraints are evaluated.

**Recommendation**

- Split the semantics explicitly:
  - `docs/logs/releases/` and `docs/logs/reviews/` are audit/review workflow artefacts, not normative design authority, but are still active gate inputs.
  - Normative outcomes must be copied into CLAUDE, ADRs, contract YAML, module metadata, architecture status, or root/per-module architecture docs.
- Amend `docs/logs/README.md` and Rule G-1 wording so agents know when to read logs for forensic/review context and when not to treat them as canonical design truth.

### P2-1 - HTTP contract catalog still marks shipped run lifecycle endpoints as planned

**Evidence**

- `docs/contracts/http-api-contracts.md` still labels:
  - `POST /v1/runs` as `(planned; W1)`
  - `GET /v1/runs/{id}` as `(planned; W1)`
  - `POST /v1/runs/{id}/cancel` as `(planned; W1)`
- `docs/contracts/contract-catalog.md` also says these routes are planned W1 routes.
- `docs/contracts/openapi-v1.yaml` ships those routes, with the cursor-flow note and cancellation semantics.
- `agent-service/src/main/java/.../RunController.java` implements the routes.
- `docs/governance/architecture-status.yaml#run_http_contract` says the W1 HTTP contract is shipped and points to `RunController` plus integration tests.

**Why this matters**

The human-readable contract surface contradicts the machine-readable OpenAPI and runtime implementation. This can mislead API consumers and future W2 implementers.

**Recommendation**

- Update `http-api-contracts.md` and `contract-catalog.md` to mark the run lifecycle endpoints as shipped, with the current `202 + TaskCursor` create response and cancel semantics.
- Add a cross-check that an OpenAPI operation implemented by a live controller cannot remain marked as planned in the human-readable contract catalog unless the row is explicitly historical.

### P2-2 - Skill capacity contract still carries old rule identifiers and suspension overclaims in comments/Javadocs

**Evidence**

- `docs/governance/skill-capacity.yaml` still opens with `Skill-dimensional capacity matrix (Rule 41)`.
- The `model-call` row says `over-cap callers are CHRONOS_HYDRATED, not rejected`.
- The active Rule R-K kernel now correctly narrows the shipped behavior to `SkillResolution.reject(SuspendReason.RateLimited)`, deferring the actual Run/dependent-step suspension transition to Rule R-K.c.
- `DefaultSkillResilienceContract` returns a decision envelope, not a `Run.SUSPENDED` transition.
- `SkillResolution` Javadoc still references old `Rule 41` and uses "park the agent process" language, although it later says the caller is responsible for the actual W2 transition.

**Why this matters**

The code path is correct for W1.x, but the config/Javadoc wording can reintroduce the exact semantic overclaim that rc11 tried to close: decision envelope vs actual suspended workflow state.

**Recommendation**

- Change config and Javadoc authority references from old numeric `Rule 41` to `Rule R-K`.
- Replace `CHRONOS_HYDRATED` and "park the agent process" comments with "returns `SkillResolution.reject(SuspendReason.RateLimited)`; W2 scheduler admission maps that decision to suspension."
- Extend the terminal-verb check beyond CLAUDE kernels to active contract YAML comments and SPI Javadocs where those comments are used as implementation guidance.

### P2-3 - Developer quickstart still contains a stale package path

**Evidence**

- `docs/quickstart.md` references `ascend.springai.runtime.orchestration.spi.Orchestrator#run`.
- The current SPI package is `ascend.springai.service.runtime.orchestration.spi.Orchestrator`.

**Why this matters**

The quickstart is an onboarding contract. A stale package path causes new contributors to learn the pre-consolidation import path.

**Recommendation**

- Update the package path in the quickstart.
- If the quickstart snippet is intended to be executable, compile it as a smoke fixture or keep it deliberately pseudo-code and label it as such.

### P2-4 - Gate README and some status prose lag behind rc11 / ADR-0086

**Evidence**

- `gate/README.md` top matter says 112 active gate rules and 172 self-tests, but its wave narrative still stops at rc8/rc10-era Rules 97-98 and ADR-0084.
- The same README later says the self-test harness runs `161` self-tests and prints `Tests passed: 161/161`, while the live harness reports `Tests passed: 172/172`.
- `docs/governance/architecture-status.yaml` still says ADR count is 85 and active engineering rules are 67, while ADR-0086 exists and CLAUDE exposes 30 active rules.

**Why this matters**

These are not isolated stale sentences because `gate/README.md` is a main operational entrypoint for contributors running the gate. The current prevention rules catch some numeric drift, but not the stale narrative and not every baseline key.

**Recommendation**

- Update gate README wave history and self-test description to match live rc11/ADR-0086 state.
- Add prevention checks for stale executable-test count prose and graph-count baseline keys, not only active-gate-count prose.

### P2-5 - Some run lifecycle status prose still describes W2 materialization alongside already-shipped HTTP endpoints

**Evidence**

- `docs/governance/architecture-status.yaml#run_lifecycle_spi` is correctly `shipped: false`, but its `allowed_claim` says the `RunLifecycle` interface will be materialized at W2 "alongside RunController HTTP endpoints."
- The cancel HTTP endpoint is already shipped in `RunController`, `openapi-v1.yaml`, and `RunHttpContractIT`.

**Why this matters**

The design-only `RunLifecycle` SPI and the shipped cancel HTTP edge are different surfaces. The prose currently blurs them.

**Recommendation**

- Reword the row to say: `RunLifecycle` SPI remains design-only for W2 resume/retry/cancel orchestration, while the W1 HTTP cancel edge is shipped and independently re-authorized by Rule R-J.b.

## Overdesign Assessment

I do not see a material overdesign problem in the core agent-driven architecture at L0.

- Dynamic planning is not over-implemented: `plan-projection.v1.yaml` is explicitly `design_only`, has no Java type, and has a W2 promotion trigger.
- Skill capacity is appropriately split between a shipped decision envelope and deferred scheduler suspension, but comments and rule identifiers must be cleaned up to preserve that split.
- Memory and knowledge ownership is defensible: `GraphMemoryRepository` remains on `agent-service` per ADR-0082, avoiding a premature extraction into `agent-runtime-core`.
- Engine contracts are appropriately SPI/contract oriented. The current issue is not too much runtime machinery; it is authority drift around the rule and graph surfaces.
- The main overdesign risk is in the governance layer: new prevention rules keep being added for each discovered leak, but several rules are path-specific and still miss root Dockerfiles, PlantUML source, quickstart snippets, and latest-release sorting. The remedy is not "more broad rules everywhere"; it is a smaller set of source-of-truth category checks for release selection, deploy entrypoints, visual architecture sources, and contract catalogs.

## Required Closure Criteria

Before claiming L0 is complete:

1. Resolve the 30-vs-67 rule authority conflict across CLAUDE, ADR-0086, architecture status, rule cards, gate headers, `gate/rules`, enforcer refs, and graph nodes.
2. Fix latest-release resolution so rc11 is selected over rc9.
3. Align architecture graph counts across the generated graph, architecture status, migration report, and release note claims.
4. Make Dockerfile / compose / operator-shape smoke paths current, or explicitly archive them.
5. Regenerate or downgrade the L0 architecture views so they no longer present deleted modules as current source-of-truth components.
6. Clarify `docs/logs` as active gate/review workflow input versus normative design authority.
7. Update human-readable HTTP contracts and contract catalog to match the shipped OpenAPI/runtime surface.
8. Clean up skill-capacity rule identifiers and decision-envelope wording.
9. Update quickstart package paths and gate README numeric/narrative drift.
10. Add regression tests for rc9/rc10/rc11 release sorting, graph baseline truth, rule namespace ratchet completeness, and deploy entrypoint deleted-module references.

## Verification Notes

- `bash gate/check_parallel.sh` passed in this workspace: `parallel_summary: executed 112 rules; serial source defined 112 rules`.
- `bash gate/test_architecture_sync_gate.sh` passed in this workspace: `Tests passed: 172/172`.
- `python gate/build_architecture_graph.py --check --no-write` passed and reported `364 nodes, 525 edges`.
- `bash gate/check_architecture_views.sh` did not complete reliably in this workspace because it invoked an unexecutable `rg` path and then reported missing capability labels; independent text inspection still found stale `agent-platform` / `agent-runtime` definitions in `l0-development.puml`.
- No Java code was changed by this review, so Maven verification is not required for the review document itself.
