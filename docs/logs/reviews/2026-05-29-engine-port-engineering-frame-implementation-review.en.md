# EnginePort and EngineeringFrame Implementation Review

Date: 2026-05-29
Reviewer: Codex
Reviewed commit: `d66749ba8024d8c4caa8858247c3b4f415f23403`
Scope: ADR-0157 EngineeringFrame ontology, ADR-0158 EnginePort boundary, Agent Service EngineeringFrame anchoring, generated fact-layer integrity, architecture gate readiness.

## Executive verdict

The implementation is not ready to accept as complete.

`./mvnw.cmd clean verify` and `./mvnw.cmd -Pquality verify` both passed locally, so the Java code compiles and the current unit/integration suite is green. However, `bash gate/check_architecture_sync.sh` is still red, and the delivered architecture surfaces contain material drift from ADR-0157 and ADR-0158. The most serious issue is that the code introduces `EnginePort` as a transport-agnostic contract in name, while its Java shape is still the old in-process `RunContext + ExecutorDefinition + SuspendSignal` call path. That makes the boundary non-neutral for the three deployment forms promised by ADR-0158.

The required changes below are blocking before acceptance.

## Finding 1 - ADR-0158 frame work was not completed

Severity: P0

ADR-0158 explicitly requires two structural changes in the EngineeringFrame map: re-home `EF-ORCHESTRATION-SPI` to `agent-bus` and add `EF-ENGINE-PORT` owned by `agent-bus`.

Evidence:

- `docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:122-124` says `EF-ORCHESTRATION-SPI` must be re-homed to `agent-bus` and `EF-ENGINE-PORT` must be added under `agent-bus`.
- `docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:158-159` repeats this as a lockstep implementation requirement.
- `architecture/features/engineering-frames.dsl:107-124` still declares `EF-ORCHESTRATION-SPI` with `saa.owner = agent-execution-engine` and `genModule_agent_execution_engine -> efOrchestrationSpi`.
- `architecture/docs/L1/engineering-frames.md:47-48` still lists `agent-bus` without `EF-ENGINE-PORT` and still lists `EF-ORCHESTRATION-SPI` under `agent-execution-engine`.
- `rg "EF-ENGINE-PORT|efEnginePort|Engine Port Frame" architecture/features architecture/docs docs/governance/architecture-workspace-graph.yaml` returns no matches.

Required change:

1. Add `EF-ENGINE-PORT` as a first-class `SAA EngineeringFrame` owned by `agent-bus`.
2. Move `EF-ORCHESTRATION-SPI` ownership and `contains` relationship from `agent-execution-engine` to `agent-bus`, or publish a new superseding ADR if the team intentionally rejects ADR-0158's accepted decision.
3. Update `architecture/docs/L1/engineering-frames.md`, `architecture/features/engineering-frames.dsl`, generated graph projection, and baseline counts in lockstep.
4. Add a gate or focused test that fails if an accepted ADR requests a frame re-home or new frame and the DSL map does not reflect it.

Acceptance checks:

- `rg "EF-ENGINE-PORT" architecture/features architecture/docs docs/governance/architecture-workspace-graph.yaml` finds the new frame.
- `rg -n "EF-ORCHESTRATION-SPI|efOrchestrationSpi" architecture/features/engineering-frames.dsl` shows `saa.owner = agent-bus` and a `genModule_agent_bus -> efOrchestrationSpi` relationship.
- `bash gate/check_architecture_sync.sh` passes after the baseline counts are updated from the live projection.

## Finding 2 - Java EnginePort does not match the ADR or wire contract

Severity: P0

The delivered `EnginePort` is still an in-process execution interface. It does not implement the neutral boundary promised by ADR-0158 and `engine-port.v1.yaml`.

Evidence:

- `docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:74-77` defines the EnginePort surface as execute, stream events, suspend, resume, describe/health, and says it carries no tenant/session/run semantics.
- `docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:98-101` requires engine-facing context neutralization: tenant/session/run ownership stays in Service; the engine-facing context carries only opaque correlation plus checkpointer and suspend capability; `RunContext` becomes a Service-side subtype.
- `docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:103-109` requires over-the-wire suspend/resume as checkpoint-token protocol and requires `ExecutorDefinition` to become a serializable named reference.
- `docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:111-113` requires reconciliation under `Flow.Publisher<AgentEvent>` streaming with errors as terminal events.
- `docs/contracts/engine-port.v1.yaml:18-35` defines `execute` as request fields plus a streamed event response with exactly one terminal event.
- `docs/contracts/engine-port.v1.yaml:38-47` defines suspend/resume as checkpoint-token terminal event plus fresh execute with `startCheckpointRef`.
- `docs/contracts/engine-port.v1.yaml:58-62` repeats that EnginePort carries no tenant/session semantics.
- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/engine/EnginePort.java:22-35` exposes only `Object execute(RunContext ctx, ExecutorDefinition def, Object input) throws SuspendSignal`.
- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/engine/RunContext.java:16-18`, `:37-41`, and `:60-62` still expose `runId`, `tenantId`, `sessionId`, and child execution via inline `ExecutorDefinition`.
- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/engine/ExecutorDefinition.java:18-47` still contains inline JVM function/lambda definitions.
- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/engine/DefinitionRef.java:6-16` correctly documents the desired wire reference, but `EnginePort` does not consume it.

Required change:

Choose one of these two coherent options:

1. Full ADR-0158 implementation: make the `agent-bus` `EnginePort` interface match the neutral contract. Introduce an engine-facing `ExecutionContext` without tenant/session semantics. Make remote dispatch use `DefinitionRef` rather than inline `ExecutorDefinition`. Return a typed event stream, for example `Flow.Publisher<AgentEvent>` or an equivalent project-approved event abstraction, with errors and interrupts represented as terminal events rather than thrown boundary exceptions. Add describe/health operations or explicitly defer them in the contract and ADR.
2. Staged implementation: if this wave is intentionally only the in-process adapter, do not label the current Java interface as the transport-agnostic EnginePort contract. Keep the current call path as an in-process adapter and leave `engine-port.v1.yaml` as design-only until the neutral Java boundary lands. In this case, update ADR-0158 with an explicit staged exception and do not claim `EnginePort` is shipped.

Acceptance checks:

- A test fails if an engine-facing context exposes `tenantId()` or `sessionId()`.
- A test fails if network-boundary dispatch requires an inline JVM lambda or `ExecutorDefinition`.
- A conformance test asserts streamed terminal events for success, failure, and interrupt/checkpoint-token flows.
- The Java `EnginePort` Javadoc and `docs/contracts/engine-port.v1.yaml` describe the same boundary shape.

## Finding 3 - Transport selection is hardwired to in-process

Severity: P1

ADR-0158 requires three transport realizations and says deployment form is selected by packaging/adapter choice. The current Service path hardwires in-process execution inside `SyncOrchestrator`, so the Service cannot actually select RPC or A2A without modifying the orchestrator.

Evidence:

- `docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:87-90` requires separate EnginePort implementations for in-process, internal RPC, and A2A.
- `docs/adr/0158-engine-port-transport-agnostic-boundary.yaml:93-96` fixes the dependency direction as `agent-service -> agent-bus(bus.spi.engine) <- agent-execution-engine`.
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/inmemory/SyncOrchestrator.java:79-93` accepts `EngineRegistry` and then constructs `new InProcessEnginePort(this.engineRegistry)` internally.
- `agent-service/src/main/java/com/huawei/ascend/service/platform/engine/RunExecutionAutoConfiguration.java:41-44` wires `SyncOrchestrator` from `RunRepository`, `Checkpointer`, and `EngineRegistry`; it does not provide or select an `EnginePort`.
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/RpcEngineAdapter.java:26-34` and `A2aEngineAdapter.java:22-30` exist but only fail fast and are not selected by configuration.

Required change:

1. Change `SyncOrchestrator` to receive an `EnginePort` or `EnginePortRouter` via constructor injection.
2. Provide an `EnginePort` bean in `RunExecutionAutoConfiguration`.
3. Add a deployment-form property, for example `app.engine.transport = in_process | internal_rpc | a2a`, with `in_process` as the dev-posture default.
4. Keep RPC and A2A design-only fail-fast if selected before live transport provisioning, but make the selection path real and testable.
5. Move service-side adapter selection into `agent-service`; keep engine implementation details inside `agent-execution-engine`.

Acceptance checks:

- A test can instantiate `SyncOrchestrator` with a fake `EnginePort` and prove it does not construct `InProcessEnginePort` internally.
- A Spring configuration test proves `app.engine.transport=in_process` selects the in-process port.
- A Spring configuration test proves `internal_rpc` and `a2a` fail fast with clear unsupported-transport errors until live transports are implemented.

## Finding 4 - EngineeringFrames still mix structure with ProductClaim and Feature inventory

Severity: P1

ADR-0157 established two separate axes: structure is `Module -> EngineeringFrame -> FunctionPoint`; value is `ProductClaim -> Feature -> FunctionPoint`, with `Feature -> EngineeringFrame` as a traversal edge. The current corpus violates this by putting ProductClaim fields on frames and by leaving traversal edges absent.

Evidence:

- `docs/adr/0157-engineering-frame-ontology.yaml:50-64` introduces `EngineeringFrame` as the structural layer and says frames are claim-agnostic.
- `docs/adr/0157-engineering-frame-ontology.yaml:57-60` defines the relationship vocabulary: `anchors` for structure and `traverses` for Feature to EngineeringFrame.
- `docs/adr/0157-engineering-frame-ontology.yaml:70-73` says the old layer features are re-tagged and the remaining features are true value threads.
- `architecture/docs/L1/engineering-frames.md:20-27` repeats that frames are structural carriers and claim-agnostic.
- `architecture/features/README.md:23-24` says `features.dsl` is the Feature inventory and `engineering-frames.dsl` is the EngineeringFrame inventory.
- `architecture/features/engineering-frames.dsl:8-10` explicitly says frames declare no `saa.productClaim`.
- `architecture/features/features.dsl:347-480` declares six `SAA EngineeringFrame` elements in `features.dsl`, and each one carries `saa.productClaim` at lines `350`, `374`, `398`, `422`, `446`, and `470`.
- `rg -n "traverses" architecture/features/features.dsl architecture/features/engineering-frames.dsl` finds comments only, no actual `saa.rel = traverses` relationships.

Required change:

1. Remove `saa.productClaim` from all `SAA EngineeringFrame` elements.
2. Keep ProductClaim linkage on the value axis through `SAA Feature`.
3. Add concrete `Feature -> EngineeringFrame` relationships with `saa.rel = traverses` so a value demand can be read as a route across the structural map.
4. Prefer moving the Agent Service frame element declarations into `architecture/features/engineering-frames.dsl` so that the structural inventory is read before the feature corpus. If the team keeps a staged declaration split, update `architecture/features/README.md`, L1 docs, and gates so the split is explicit and cannot confuse AI consumers.

Acceptance checks:

- `rg -n "saa.productClaim" architecture/features | Select-String "EngineeringFrame"` returns no frame-scoped product claims.
- `rg -n "saa.rel\" \"traverses" architecture/features` returns real relationships, not comments.
- The generated graph has Feature-to-EngineeringFrame traversal edges.

## Finding 5 - Generated fact layer is stale and incomplete for this delivery

Severity: P1

The repository requires generated facts to be the primary input for AI understanding. The current commit changes code, contracts, tests, module metadata, and ADRs, but the generated facts are not consistently refreshed to the reviewed commit.

Evidence:

- `AGENTS.md:30-50` says generated facts must be read before prose, generated facts outrank prose, and stale/drifted generated artifacts must not be used.
- `architecture/facts/README.md:30-43` says files under `architecture/facts/generated/` are produced only by deterministic extractors and must be regenerated from source authorities.
- `architecture/facts/README.md:45-64` defines `repo_commit` as required provenance.
- Reviewed HEAD is `d66749ba8024d8c4caa8858247c3b4f415f23403`.
- `architecture/facts/generated/code-symbols.json:2-7` and `contract-surfaces.json:2-7` carry commit `c1b2c8671e8fa4755af2599895b0459fd16ab5ee`.
- `architecture/facts/generated/code-symbols.json:234-475` still reports old `com.huawei.ascend.engine.orchestration.spi.*` facts, including `code-symbol/com-huawei-ascend-engine-orchestration-spi-runcontext`.
- `architecture/facts/generated/tests.json:42-88` still reports old `com.huawei.ascend.engine.orchestration.spi.*` test FQNs.
- `architecture/facts/generated/contract-surfaces.json:1-20` has no `engine-port.v1.yaml` surface in the shown contract facts, while `docs/contracts/engine-port.v1.yaml` is newly added.

Required change:

1. Re-run the fact extractor from the reviewed commit and commit all changed generated fact files.
2. Ensure `code-symbols.json` contains `com.huawei.ascend.bus.spi.engine.EnginePort`, `DefinitionRef`, and the relocated SPI types under their current packages.
3. Ensure `tests.json` contains current test FQNs under `com.huawei.ascend.bus.spi.engine.*` where applicable.
4. Ensure `contract-surfaces.json` includes `docs/contracts/engine-port.v1.yaml`; if the extractor does not support this contract shape yet, update the extractor rather than hand-editing facts.

Acceptance checks:

- `git rev-parse HEAD` and every `architecture/facts/generated/*.json` `_provenance.repo_commit` are intentionally aligned or the extractor's documented byte-identity mode explains why not.
- `rg -n "com.huawei.ascend.engine.orchestration.spi" architecture/facts/generated/code-symbols.json architecture/facts/generated/tests.json` does not report current-source facts for relocated types.
- `rg -n "engine-port.v1.yaml|EnginePort|DefinitionRef" architecture/facts/generated` finds generated fact entries.

## Finding 6 - Authoritative prose and templates still contain old SPI ownership

Severity: P1

The implementation updated module metadata and the contract catalog, but many authoritative architecture surfaces still describe orchestration SPI as owned by `agent-execution-engine`.

Evidence:

- `architecture/docs/L0/ARCHITECTURE.md:77`, `:134`, `:150`, `:257`, `:324`, `:446`, and `:538` still reference the old `engine.orchestration.spi` ownership path.
- `architecture/docs/L1/agent-service/ARCHITECTURE.md:41` still says orchestration SPI lives in `agent-execution-engine`.
- `architecture/docs/L1/agent-service/spi-appendix.md:141` still lists `Orchestrator`, `RunContext`, `Checkpointer`, `SuspendSignal`, `TraceContext`, `ExecutorDefinition`, and `RunMode` under `agent-execution-engine` / `engine.orchestration.spi`.
- `architecture/docs/L1/agent-service/logical.md:347` still names `engine.orchestration.spi.SuspendSignal`.
- `docs/governance/templates/root-architecture.md.j2:150`, `:257`, `:324`, `:446`, and `:538` still contain the old ownership path.
- `docs/governance/templates/agent-execution-engine-architecture.md.j2:27` and `:128-134` still document the old `engine.orchestration.spi` home.

Required change:

1. Update L0, L1, DFX, and template surfaces in the same wave as the code move.
2. Preserve historical release logs as historical snapshots, but active authority surfaces and templates must match the new ADR.
3. Add a grep-based gate or extend an existing gate so active docs/templates cannot retain old ownership paths after a package re-home ADR is accepted.

Acceptance checks:

- `rg -n "engine\\.orchestration\\.spi" architecture/docs docs/governance/templates docs/contracts docs/dfx` returns only historical logs or explicitly marked historical context.
- `architecture/docs/L1/agent-service/spi-appendix.md` and the agent-execution-engine template both describe the same owner as `agent-bus` for `bus.spi.engine`.

## Finding 7 - Architecture gate is red

Severity: P1

The canonical architecture gate does not pass after the delivery.

Evidence:

- `./mvnw.cmd clean verify` passed locally.
- `./mvnw.cmd -Pquality verify` passed locally.
- `bash gate/check_architecture_sync.sh` failed after quality reports existed.
- Gate failure 1: `architecture_refresh_defect_family_re_eval_required` failed because `docs/governance/recurring-defect-families.yaml` content was unchanged since refresh-signal commit `d66749ba`.
- Gate failure 2: workspace baseline parity failed: `docs/governance/architecture-status.yaml:166-167` still declares `workspace_elements: 608` and `workspace_relationships: 469`, while `docs/governance/architecture-workspace-graph.yaml:14-15` reports `node_count: 609` and `edge_count: 478`.

Required change:

1. Fix the structural model first. Do not simply bump counts around a wrong frame map.
2. Re-emit the workspace projection and update `architecture-status.yaml` baseline metrics to the live projection counts.
3. Refresh the recurring-defect-family ledger as required by Rule G-9.b because this commit touched ADR/governance architecture surfaces.

Acceptance checks:

- `bash gate/check_architecture_sync.sh` passes from a clean workspace after `./mvnw.cmd -Pquality verify`.

## Finding 8 - Frame status and anchoring overclaim shipped scope

Severity: P2

Some Agent Service frames are marked shipped without matching function-point anchors, and `EF-INTERNAL-EVENT-QUEUE` is marked shipped despite the ontology example explicitly treating it as a design-only zero-anchor frame.

Evidence:

- `docs/adr/0157-engineering-frame-ontology.yaml:67-68` explicitly permits a design-only frame with zero shipped anchors and names `EF-INTERNAL-EVENT-QUEUE` as the example.
- `architecture/features/features.dsl:395-404` marks `EF-INTERNAL-EVENT-QUEUE` as `saa.status = shipped`.
- `architecture/features/engineering-frames.dsl:325-329` only contains the module-to-frame edge for `EF-INTERNAL-EVENT-QUEUE`; it has no anchored function point.
- `architecture/features/features.dsl:371-383` marks `EF-ENGINE-DISPATCH` as shipped.
- `architecture/features/engineering-frames.dsl:319-323` only contains the module-to-frame edge for `EF-ENGINE-DISPATCH`; it has no anchored function point.

Required change:

1. If the frame has no shipped function-point anchor, keep it `design_only` or `implemented_unverified`, not `shipped`.
2. If the team believes the frame is shipped, add the missing FunctionPoint(s), Contract references, CodeFact references, TestFact references, and `anchors` relationships.
3. Add a gate rule for shipped EngineeringFrames: either at least one anchored shipped FunctionPoint or an explicit approved exception.

Acceptance checks:

- `EF-INTERNAL-EVENT-QUEUE` is design-only unless real event-queue code, contract, and tests are mounted.
- Shipped frames with zero anchors are rejected unless explicitly allowlisted with an ADR-backed reason.

## Minimum acceptance bar for the next delivery

The next delivery should not be accepted until all of the following are true:

1. The `EnginePort` Java surface, `engine-port.v1.yaml`, and ADR-0158 describe one coherent boundary.
2. `EF-ENGINE-PORT` exists and `EF-ORCHESTRATION-SPI` ownership matches ADR-0158.
3. EngineeringFrames are claim-agnostic and value features traverse frames through explicit relationships.
4. Generated facts are refreshed or extractors are extended so facts reflect the current code, contracts, tests, and ADRs.
5. Active L0/L1/template prose no longer teaches the old orchestration SPI owner.
6. `./mvnw.cmd clean verify`, `./mvnw.cmd -Pquality verify`, and `bash gate/check_architecture_sync.sh` all pass from a clean workspace.

