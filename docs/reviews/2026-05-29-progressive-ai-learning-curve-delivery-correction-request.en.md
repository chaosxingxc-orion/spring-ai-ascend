# Progressive AI Learning Curve Delivery - Correction Request

Date: 2026-05-29
Status: acceptance blocked
Audience: engineering team, architecture owners, AI-agent workflow owners
Reviewed commit: `d66749ba8024d8c4caa8858247c3b4f415f23403`

## Executive Decision

Do not start a new repository for this delivery. The core assets now exist in this repo: product authority, ADR corpus, Structurizr workspace, generated facts, contract catalog, L1 feature/function-point inventory, and gates. Starting over would likely copy the same failure mode into a cleaner shell.

However, the current delivery is not acceptable yet as a "progressive learning curve for AI agents." It compiles, and the new targeted EnginePort tests pass, but the authority surfaces do not converge. A new AI agent reading the repository today can still receive a biased or stale understanding because generated facts, ADR intent, contract YAML, Java SPI, product authority text, and workspace baselines disagree.

Root cause: the implementation treated the in-process EnginePort refactor as the architecture landing, but did not complete the lockstep authority update across generated facts, workspace baselines, product authority, active templates, and the Java/contract surface declared by ADR-0158.

## Verification Performed

PASS:

```text
.\mvnw -pl agent-service -am -DskipTests compile
```

PASS:

```text
.\mvnw -pl agent-service -am '-Dtest=DefinitionRefDispatchByNameTest,EngineTransportAdapterDesignOnlyTest,SuspendResumeWireReadinessSpikeTest,InProcessEnginePortConformanceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: 12 tests run, 0 failures, 0 errors.

FAIL:

```text
.\mvnw -f tools\architecture-workspace\pom.xml exec:java@extract-facts '-Dexec.args=--repo . --out architecture/facts/generated --check'
```

Observed failure:

```text
--check: drift detected on ...\architecture\facts\generated\module-build.json
```

FAIL:

```text
bash gate/check_architecture_sync.sh
```

Observed failures:

```text
FAIL: architecture_refresh_defect_family_re_eval_required
ARCHITECTURE WORKSPACE: workspace baseline parity FAILED
workspace_elements=608 but live node_count=609
workspace_relationships=469 but live edge_count=478
```

## P0-1: Regenerate and Reconcile the Generated Fact Layer

Required change:

Regenerate the full fact layer from deterministic extractors after a clean build, and update every dependent feature/function-point reference to the new fact IDs. Do not hand-edit files under `architecture/facts/generated/`. The delivery is not acceptable until `ExtractFactsCli --check` and the quality profile byte-identity check pass at the current workspace HEAD.

Why this is required:

- `AGENTS.md:30`, `AGENTS.md:32`, and `AGENTS.md:45` require AI agents to read `architecture/facts/generated/*.json` before prose for factual claims.
- `architecture/facts/README.md:26` defines generated facts as the AI primary input for implementation decisions.
- `architecture/facts/README.md:62` says generated facts carry the workspace HEAD in `repo_commit`.
- `architecture/facts/README.md:110` says facts outrank prose.
- `architecture/facts/README.md:122` documents `--check` as the byte-identical regeneration check.
- `docs/governance/rules/rule-G-15.md:21` and `docs/governance/rules/rule-G-15.md:94-96` require byte-identical re-emission at the same workspace HEAD.

Current evidence:

- Current HEAD is `d66749ba8024d8c4caa8858247c3b4f415f23403`.
- `architecture/facts/generated/code-symbols.json` still reports `repo_commit=c1b2c8671e8fa4755af2599895b0459fd16ab5ee`.
- `architecture/facts/generated/contract-surfaces.json` still reports `repo_commit=c1b2c8671e8fa4755af2599895b0459fd16ab5ee`.
- `architecture/facts/generated/tests.json` still reports `repo_commit=c1b2c8671e8fa4755af2599895b0459fd16ab5ee`.
- `architecture/facts/generated/module-build.json`, `runtime-config.json`, and `adrs.json` report `repo_commit=d65ae9f344ce70cea4df270be38df1e4e6ac403d`, also not current HEAD.
- `architecture/facts/generated/code-symbols.json:234-247` still records `code-symbol/com-huawei-ascend-engine-orchestration-spi-checkpointer` under the old `com.huawei.ascend.engine.orchestration.spi` package.
- `architecture/facts/generated/tests.json:42-55` still records the old `test/com-huawei-ascend-engine-orchestration-spi-orchestrationspiarchtest` package path.
- The new Java surface exists in source at `agent-bus/src/main/java/com/huawei/ascend/bus/spi/engine/EnginePort.java:22` and `DefinitionRef.java:18`, but those facts are not reflected in the generated fact files.
- `docs/contracts/engine-port.v1.yaml:1-9` exists, but the generated contract facts are still from the older commit and do not represent this contract as current generated ground truth.

Acceptance criteria:

- Run a clean compile/verify path that gives the extractors current class files.
- Regenerate all fact files with the documented extractor command.
- `ExtractFactsCli --check` passes.
- `mvn -Pquality verify` or the repository's quality-equivalent fact-byte-identity test passes.
- The generated facts include current fact IDs for the new `bus.spi.engine` types, the new EnginePort tests, and `engine-port.v1.yaml`.
- Any `architecture/features/function-points.dsl` references affected by renamed fact IDs are updated in the source authority, not patched into generated JSON.

## P0-2: Make the Canonical Architecture Gate Pass

Required change:

Complete the lockstep architecture refresh for ADR-0158. The canonical architecture gate must pass before this delivery is considered complete.

Why this is required:

- ADR-0158 explicitly marks its implementation phase as touching authority surfaces and says the design is not complete until the workspace, generated fragments, baselines, contracts, and gates are updated in lockstep (`docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:152-163`).
- Rule G-8 requires canonical authority surfaces to agree, including baseline parity (`docs/governance/rules/rule-G-8.md:14`, `rule-G-8.md:32`).
- Rule G-9 requires an architecture refresh signal to update `docs/governance/recurring-defect-families.yaml` with a real content diff, not a no-op (`docs/governance/rules/rule-G-9.md:21`, `rule-G-9.md:70-89`).

Current evidence:

- `bash gate/check_architecture_sync.sh` fails `architecture_refresh_defect_family_re_eval_required`.
- The same gate fails workspace baseline parity.
- `docs/governance/architecture-status.yaml:167` says `workspace_elements: 608`.
- `docs/governance/architecture-status.yaml:168` says `workspace_relationships: 469`.
- `docs/governance/architecture-workspace-graph.yaml:14` says `node_count: 609`.
- `docs/governance/architecture-workspace-graph.yaml:15` says `edge_count: 478`.

Acceptance criteria:

- Update workspace baselines through the repository's normal generated/source-authority path.
- Update `docs/governance/recurring-defect-families.yaml` with a real family-state/content change that addresses the ADR-0158 refresh signal.
- `bash gate/check_architecture_sync.sh` passes.

## P0-3: Converge ADR-0158, `engine-port.v1.yaml`, and the Java `EnginePort` Surface

Required change:

Choose one truth and make all three surfaces agree. The recommended direction is to implement the semantic contract declared by ADR-0158 and `engine-port.v1.yaml` in the Java SPI, then keep the existing synchronous in-process path as a compatibility adapter behind that semantic port. If the team instead wants the current synchronous Java surface to be the real v1.0 port, then ADR-0158 and `engine-port.v1.yaml` must be amended to say so honestly and must not call the broader stream/suspend/resume/describe surface frozen or shipped.

Why this is required:

- ADR-0158 declares the EnginePort surface as execute, stream events, suspend, resume, describe/health (`docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:74-75`).
- ADR-0158 requires an engine-facing `ExecutionContext` carrying only opaque correlation, checkpointer, and suspend capability, while `RunContext` becomes service-side and adds tenant/session (`docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:98-101`).
- ADR-0158 chooses `Flow.Publisher<AgentEvent>` streaming with errors as terminal events (`docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:111-112`).
- `engine-port.v1.yaml` declares `definitionRef` as the request carrier (`docs/contracts/engine-port.v1.yaml:24`), `events: stream` as the response shape (`docs/contracts/engine-port.v1.yaml:30`), `describe` as an operation (`docs/contracts/engine-port.v1.yaml:35`), and no tenant/session semantics across EnginePort (`docs/contracts/engine-port.v1.yaml:58-60`).

Current evidence:

- Java `EnginePort` is currently `Object execute(RunContext ctx, ExecutorDefinition def, Object input) throws SuspendSignal` (`agent-bus/src/main/java/com/huawei/ascend/bus/spi/engine/EnginePort.java:35`).
- `RunContext` still exposes `tenantId()` and `sessionId()` (`agent-bus/src/main/java/com/huawei/ascend/bus/spi/engine/RunContext.java:18`, `RunContext.java:41`).
- `DefinitionRef` exists as the intended wire-form reference (`agent-bus/src/main/java/com/huawei/ascend/bus/spi/engine/DefinitionRef.java:18`) but is not used by `EnginePort.execute(...)`.
- The current Java port throws `SuspendSignal`, while the contract YAML says over-wire suspend is a terminal event with checkpoint and correlation data.

Acceptance criteria:

- Add `ExecutionContext` or an equivalent engine-facing context with no tenant/session accessors.
- Use `DefinitionRef` or a clearly versioned semantic request object at the EnginePort boundary.
- Represent stream/result/error/suspend/resume according to the contract, or amend the ADR/YAML/catalog to downgrade those parts to future design-only intent.
- Add a conformance test that fails if the exported Java `EnginePort` signature remains the old `Object execute(RunContext, ExecutorDefinition, Object) throws SuspendSignal` shape while the contract claims the broader semantic surface.

## P1-1: Fix Adapter Ownership and Deployment-Time Selection

Required change:

Make `EnginePort` an injectable deployment-selected boundary. `SyncOrchestrator` should not hard-code `new InProcessEnginePort(...)`. Either move the in-process driver to `agent-service` as ADR-0158 says, or amend ADR-0158 to say the engine module owns the in-process implementation and the service selects it as a bean. Networked adapters may remain design-only, but they must not be accidentally selectable in v1.0.

Why this is required:

- ADR-0158 says the three transports are separate EnginePort implementations living inside `agent-service` (`docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:87-90`).
- ADR-0158 says the single reactor serves all three forms by packaging plus EnginePort adapter (`docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:174-187`).

Current evidence:

- `InProcessEnginePort` lives under `agent-execution-engine` (`agent-execution-engine/src/main/java/com/huawei/ascend/engine/runtime/InProcessEnginePort.java:20`).
- `SyncOrchestrator` imports that concrete engine runtime class (`agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/inmemory/SyncOrchestrator.java:4`).
- `SyncOrchestrator` constructs it directly (`SyncOrchestrator.java:92`).
- `RunExecutionAutoConfiguration` constructs `SyncOrchestrator` from `EngineRegistry`, not an `EnginePort` bean (`agent-service/src/main/java/com/huawei/ascend/service/platform/engine/RunExecutionAutoConfiguration.java:41-44`).
- `RpcEngineAdapter` and `A2aEngineAdapter` currently throw `UnsupportedOperationException` (`agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/RpcEngineAdapter.java:26-30`, `A2aEngineAdapter.java:22-26`).

Acceptance criteria:

- The orchestrator receives an `EnginePort` through constructor or Spring bean injection.
- The in-process adapter is the default dev/in-process bean.
- RPC and A2A adapters remain unregistered unless an explicit design-only or future profile selects them.
- ADR-0158, module metadata, contract catalog, and tests all describe the same ownership and selection model.

## P1-2: Remove Active Old-Package Drift

Required change:

Update every active authority/template/DFX/enforcer surface that still names `com.huawei.ascend.engine.orchestration.spi` as the current SPI home. Historical ADRs and logs may keep old names only when clearly marked historical.

Why this is required:

- Rule G-8 requires SPI path parity across rule kernels, module metadata, on-disk packages, and enforcer text (`docs/governance/rules/rule-G-8.md:14`).
- ADR-0158 moved the neutral orchestration/engine contract to `com.huawei.ascend.bus.spi.engine` (`docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:79-84`).

Current evidence:

- `docs/governance/enforcers.yaml:338` still permits/imports `engine.orchestration.spi.*`.
- `docs/governance/templates/root-architecture.md.j2:150` still says orchestration SPI lives in `agent-execution-engine.engine.orchestration.spi`.
- `docs/governance/templates/agent-execution-engine-architecture.md.j2:24` still lists the old path.
- `docs/governance/templates/agent-service-architecture.md.j2:41` still frames the orchestration SPI as living in `agent-execution-engine`.
- `docs/governance/templates/contract-catalog.md.j2:251` still describes `agent-execution-engine` as owning the orchestration SPI.
- `docs/governance/architecture-status.yaml:1688` still claims the SPI package is `com.huawei.ascend.engine.orchestration.spi.*`.
- `docs/dfx/agent-execution-engine.yaml:39` and `docs/dfx/agent-execution-engine.yaml:48` still name `engine.orchestration.spi`.

Acceptance criteria:

- `rg "engine\.orchestration\.spi|com\.huawei\.ascend\.engine\.orchestration\.spi|engine/orchestration/spi"` returns only explicitly historical ADR/log entries or migration notes.
- Active templates, DFX files, enforcer assertions, architecture-status current claims, and generated catalogs name `com.huawei.ascend.bus.spi.engine` where appropriate.
- Gate and targeted path-parity checks pass.

## P1-3: Repair the Product Authority So AI Agents Can Consume It Without Bias

Required change:

Replace corrupted/mojibake product text and non-English auto-loaded authority text with a clean English source of truth. If the original user input must be preserved, store it outside the auto-loaded prompt path with a checksum or archival reference, and keep the AI-facing product authority in English.

Why this is required:

- `product/PRODUCT.md:3` declares itself the Tier-1 product authority auto-loaded on every AI session.
- `AGENTS.md:5` says all instructions must be translated into English before any model call and non-English text must not be passed into prompts, tool arguments, or task goals.
- ADR-0156 exists because AI sessions could not answer which product claim an artefact served, and it requires the product authority/claim chain to make that answer explicit (`docs/adr/0156-product-authority-and-traceability.yaml:34`, `0156:71-75`, `0156:144`).

Current evidence:

- `product/PRODUCT.md:12` declares "Authoritative user inputs (verbatim - do not paraphrase)".
- `product/PRODUCT.md:16-20` contains corrupted mojibake text in that verbatim block.
- `product/PRODUCT.md:23`, `PRODUCT.md:30`, `PRODUCT.md:42`, `PRODUCT.md:55-56` contain corrupted deployment/regulatory/persona labels.
- `product/claims.yaml:53-76`, `product/personas.yaml:17-56`, and `product/journey.md:25-76` contain the same corrupted labels.

Acceptance criteria:

- `product/PRODUCT.md`, `product/claims.yaml`, `product/personas.yaml`, and `product/journey.md` are valid English AI-facing authority files.
- Terms such as middle-office mode, capability-reuse mode, MLPS, PIPL, and JR/T 0223-2021 are normalized in English.
- Add a small gate/lint to reject common mojibake markers in always-loaded or Tier-1 authority files.

## P1-4: Remove Non-Portable Local Plan Paths from Authority

Required change:

Do not make a local machine path under `D:\.claude\plans\...` part of an authoritative source chain. Import the relevant plan content into the repository as an English source document, or demote the local path to a non-authoritative historical note.

Why this is required:

- The project goal is unbiased understanding for any AI agent entering the repo. A local private path cannot be resolved by another machine, CI job, or future agent.
- `product/PRODUCT.md:3` says the product authority is auto-loaded and must answer which Product Claim each artefact serves.

Current evidence:

- `product/PRODUCT.md:93` cites `D:\.claude\plans\...` as the source ADR plan.
- `product/claims.yaml:3`, `product/personas.yaml:3`, and `product/journey.md:3` cite the same local path.
- `docs/adr/0156-product-authority-and-traceability.yaml:45` also cites the local plan path.

Acceptance criteria:

- No active authority file depends on a local absolute path.
- The plan material needed for product traceability is committed under a repo path such as `docs/plans/...` or `product/source-inputs/...`.
- A gate or search rule prevents future `D:\.claude\plans\` references in active authority surfaces.

## Required Re-Submission Package

Please resubmit with:

1. A short implementation note mapping each correction above to changed files.
2. Verification output for:

```text
.\mvnw clean verify
.\mvnw -f tools\architecture-workspace\pom.xml exec:java@extract-facts '-Dexec.args=--repo . --out architecture/facts/generated --check'
bash gate/check_architecture_sync.sh
rg "engine\.orchestration\.spi|com\.huawei\.ascend\.engine\.orchestration\.spi|engine/orchestration/spi" -g "!docs/logs/**" -g "!docs/adr/**"
rg "D:\\\\.claude\\\\plans" product docs/adr docs/governance
```

3. Updated acceptance tests for the EnginePort semantic contract, not only the transitional in-process bridge.

4. A mojibake lint check for auto-loaded product authority files. The check should reject common UTF-8/GBK corruption markers without embedding non-English source text in prompts or generated review requests.

Acceptance remains blocked until the generated fact layer, workspace gate, product authority, and EnginePort contract all converge.
