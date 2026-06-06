---
affects_level: L1
affects_view: [logical, development]
review_status: blocking_findings
review_target: "PR-138"
review_url: "https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/138"
reviewed_base: "4ac81dfd60f01b0b1a72151f170e839abf2249fe"
reviewed_head: "4bf101e319e92081a6e9f9941366362249659e1f"
authors: ["Codex"]
related_rules: [D-1, G-15, G-13, R-C, R-F, R-G]
affects_artefact:
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/dispatch/dispatch/EngineDispatcher.java
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/dify/DifyAgentDriver.java
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/dify/DifyOutputConverter.java
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/access/config/AccessLayerConfiguration.java
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/registry/DefaultAgentDriverRegistry.java
  - architecture/facts/generated/
  - docs/governance/architecture-status.yaml
---

# PR-138 Review - agent-runtime neutral execution core rebuild

> Date: 2026-06-06
> Reviewer: Codex
> Scope: PR-138, head `4bf101e319e92081a6e9f9941366362249659e1f`
> Recommendation: do not merge until P1 items are fixed and the generated fact layer is refreshed at final head.

## 0. Executive summary

PR-138 moves `agent-runtime` from the old `AgentHandler` / EnginePort-era execution stack to a neutral `RunCoordinator` + `AgentDriver` + `OutputConverter` core. The direction is coherent: the new seam is narrow, text-first, JDK `Flow.Publisher`-based, and most openJiuwen-specific mechanics stay inside `engine.adapters.openjiuwen`. The PR also introduces a Dify remote adapter and a new A2A starter packaging mode.

However, the current head still has merge-blocking issues in the native execution path and governance/fact layer:

- Reactive stream failures can be swallowed after the run has been marked RUNNING.
- The Dify adapter does not persist upstream `conversation_id`, so normal A2A sessions cannot reliably keep multi-turn Dify state.
- Generated facts are stale relative to the PR head, and one generated fact already contradicts the current tree.
- Multi-driver startup can fail while the new registry claims multi-driver support.
- Dify "streaming" is currently buffered into a whole response string.
- Registry validation regressed: duplicate or blank driver names silently pass.
- Retired surfaces still leak through live docs, dependency management, and canonical governance claims.

Root cause, per Rule D-1: `agent-runtime` was rewired to the new neutral core, but the old dispatcher tests and governance/fact refresh chain were removed or only partially replaced, leaving async error semantics and architecture authority surfaces under-verified.

## 1. Review method and factual baseline

The review followed the repository's AI consumption contract:

1. Read generated facts under `architecture/facts/generated/` before relying on prose.
2. Cross-check current source files against generated fact IDs.
3. Treat prose and PR description as intent, not as source of truth.
4. Verify gate behavior with the canonical bash gate.

Important caveat: the generated facts are themselves stale at this PR head. Every inspected generated JSON still carries `repo_commit = ebfc2a0073b75a04b1f201cf2537f48c332c1d0e`, while PR head is `4bf101e319e92081a6e9f9941366362249659e1f`. Therefore fact IDs below are useful as last-extracted symbol identities, but they are not sufficient proof that the final tree was re-extracted.

Relevant fact IDs observed:

- `code-symbol/com-huawei-ascend-runtime-dispatch-dispatch-enginedispatcher`
- `code-symbol/com-huawei-ascend-runtime-engine-adapters-dify-difyagentdriver`
- `code-symbol/com-huawei-ascend-runtime-engine-adapters-dify-difyoutputconverter`
- `code-symbol/com-huawei-ascend-runtime-access-config-accesslayerconfiguration`
- `code-symbol/com-huawei-ascend-runtime-engine-registry-defaultagentdriverregistry`
- `test/com-huawei-ascend-runtime-engine-adapters-dify-difyagentdrivertest`
- `test/com-huawei-ascend-runtime-engine-runcoordinatorseamtest`
- `build-module/agent-runtime-spring-boot-starter-a2a`

## 2. Blocking findings

### R1 [P1] Reactive stream errors do not become failed runs

Affected file:

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/dispatch/dispatch/EngineDispatcher.java`

Evidence:

- `runDriver()` routes `EngineStartedEvent` before collecting the run stream.
- `collect()` subscribes to `Flow.Publisher<RunEvent>`, but `onError(Throwable)` only calls `done.countDown()`.
- After `collect()` returns partial events, the outer `try/catch` never sees the stream failure.
- `InterruptedException` during `done.await(...)` is also swallowed after re-interrupting the thread; the method still returns partial events.
- No replacement `EngineDispatcherTest` exists after deleting the old dispatcher test.

Impact:

An adapter that reports model, transport, or framework failures asynchronously through `Publisher.onError()` can leave the task in RUNNING after `markRunning`, with no `EngineFailedEvent` sent to task-control or access. For A2A callers this can mean no terminal output event and a leaked/unfinished reply channel.

This is a regression risk because the new seam explicitly allows real asynchronous publishers. The current openJiuwen converter is synchronous, but Dify and future framework adapters are exactly the cases where async transport failures are expected.

Recommended fix:

- Capture `Throwable` in `collect()` and rethrow after the latch opens.
- Convert timeout into an explicit `EngineFailedEvent`, not a silent empty or partial result.
- Convert interrupted wait into a failed event after `Thread.currentThread().interrupt()`.
- Consider validating that each run emits exactly one terminal event (`COMPLETED` or `FAILED`) unless it emits `WAITING_INPUT`; if not, synthesize failure.

Minimum test coverage:

- `EngineDispatcher` with an async publisher that calls `onError()` after `onSubscribe()` should call `taskControlClient.markFailed(...)` and `accessLayerClient.failOutput(...)`.
- A publisher that never completes should produce a timeout failure.
- A publisher that emits `ACCEPTED`, `CHUNK`, then `onError()` should append the chunk and still fail terminally.

### R2 [P1] Dify conversation IDs are not mapped per A2A session

Affected file:

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/dify/DifyAgentDriver.java`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/dify/DifyOutputConverter.java`

Evidence:

- `DifyAgentDriver.invoke()` sets `conversationId = request.sessionId() == null ? "" : request.sessionId()`.
- The first request therefore sends the arbitrary A2A session id as Dify `conversation_id`.
- `DifyOutputConverter` reads Dify events for answer text and terminal status, but ignores returned `conversation_id`.
- The only Dify test, `streamsDifyChatSseAsRunEvents()`, includes `conversation_id = c1` in mock SSE, but does not assert request body conversation handling or second-turn behavior.

Impact:

Dify expects callers to send an empty `conversation_id` for a new conversation, then reuse the `conversation_id` returned by the API for subsequent turns. Normal A2A callers only know the local session id, not Dify's upstream id. Without a `sessionId -> conversation_id` mapping, the adapter can fail first-turn requests, lose multi-turn state, or require callers to know Dify internals.

Recommended fix:

- Maintain a thread-safe `sessionId -> difyConversationId` map inside `DifyAgentDriver` or a small collaborator.
- Send empty `conversation_id` on first request when no mapping exists.
- Parse/store returned `conversation_id` from `message`, `message_end`, `workflow_finished`, and possibly `error` events where Dify includes it.
- Decide lifecycle: map eviction, tenant scoping, and whether failed runs should store or discard the upstream id.

Minimum test coverage:

- First A2A request with `sessionId = sess-1` sends `conversation_id = ""`.
- Mock Dify response returns `conversation_id = c1`.
- Second A2A request with same session sends `conversation_id = c1`.
- Different A2A sessions do not share upstream conversation IDs.

### R3 [P1] Generated facts are stale relative to PR head

Affected files:

- `architecture/facts/generated/*.json`
- `agent-runtime-spring-boot-starter-a2a/module-metadata.yaml`

Evidence:

- Current PR head: `4bf101e319e92081a6e9f9941366362249659e1f`.
- Every inspected generated fact still records `repo_commit = ebfc2a0073b75a04b1f201cf2537f48c332c1d0e`.
- `architecture/facts/generated/module-build.json` records `build-module/agent-runtime-spring-boot-starter-a2a` with `module_metadata_present: false`.
- The current tree contains `agent-runtime-spring-boot-starter-a2a/module-metadata.yaml`.

Impact:

Rule G-15 says generated facts outrank prose and must be read before L1 prose for factual claims. At this PR head, generated facts contradict the current source tree, so reviewers and follow-on agents cannot safely use the fact layer as authority. This undermines the PR's architecture-governance claims even if the Java implementation compiles.

Recommended fix:

- Re-run the deterministic extractor after all source/metadata/docs changes land:

```bash
./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts
```

- Confirm `repo_commit` in all generated JSON reflects final head or the repository's expected deterministic commit marker for current extraction.
- Confirm `build-module/agent-runtime-spring-boot-starter-a2a` has `module_metadata_present: true`.
- Re-run `bash gate/check_architecture_sync.sh`.

Follow-up hardening:

- Gate currently passed despite this mismatch. Consider making "generated fact repo_commit/finality" or byte freshness blocking for changed generated facts, at least when `architecture/facts/generated/` is touched in the PR.

## 3. High-risk non-blocking findings

### R4 [P2] AgentCard auto-configuration requires a unique AgentDriver

Affected file:

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/config/AccessLayerConfiguration.java`

Evidence:

- `a2aAgentCard(ObjectProvider<AgentDriver> drivers)` calls `drivers.getIfAvailable()`.
- Spring `ObjectProvider.getIfAvailable()` throws when multiple beans match and no unique candidate exists.
- The new `DefaultAgentDriverRegistry` supports multiple drivers by agent id and framework id.

Impact:

A runtime that publishes more than one `AgentDriver` bean can fail during Spring context startup before dispatch is reached. That contradicts the multi-driver registry shape and makes "heterogeneous" runtime hosting fragile.

Recommended fix:

- Use `getIfUnique()` if the generic card should be used in multi-driver mode.
- Or choose a deterministic primary driver via `orderedStream().findFirst()`.
- Or require an explicit `AgentCard` bean/config property when multiple drivers exist.

Minimum test coverage:

- Spring context with two `AgentDriver` beans starts successfully.
- AgentCard name/description are deterministic under one driver and under multiple drivers.

### R5 [P2] Dify adapter buffers the streaming response

Affected files:

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/dify/DifyAgentDriver.java`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/dify/DifyOutputConverter.java`

Evidence:

- `DifyAgentDriver.invoke()` uses `HttpClient.send(..., HttpResponse.BodyHandlers.ofString())`.
- It returns `response.body()` as a full `String`.
- `DifyOutputConverter.convert(Object frameworkStream)` splits the completed string by lines and emits a synchronous publisher.

Impact:

The adapter advertises REST + SSE streaming, but callers receive neutral `RunEvent` items only after Dify closes the HTTP response. For long model responses or workflows, A2A chunk streaming is lost. If Dify keeps the SSE connection open longer than the request timeout, the adapter can block until timeout rather than streaming partial chunks.

Recommended fix:

- Use a streaming body handler (`ofInputStream`, `ofLines`, or a custom subscriber) and expose a live `Flow.Publisher<RunEvent>`.
- Keep JSON parsing incremental and surface `message`/`agent_message` chunks as they arrive.
- Wire `onError()` through the R1 dispatcher fix.

Minimum test coverage:

- Mock server writes two SSE chunks with a delay between them.
- Subscriber receives first `CHUNK` before the server writes the terminal event.
- Transport error mid-stream produces a terminal failed run through `EngineDispatcher`.

### R6 [P2] Driver registry silently overwrites duplicate or blank agent IDs

Affected file:

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/registry/DefaultAgentDriverRegistry.java`

Evidence:

- `register(AgentDriver driver)` performs `byAgentId.put(driver.name(), driver)`.
- There is no validation for null/blank names.
- Duplicate names overwrite the previous driver.
- The old `DefaultAgentHandlerRegistry` test rejected duplicate and blank ids; that test was deleted with no equivalent replacement.

Impact:

Misconfigured driver beans can silently change routing depending on bean order. A blank id can also be registered and only fail later at dispatch time, making startup diagnostics worse.

Recommended fix:

- Require non-null driver, non-blank `driver.name()`, and non-blank `driver.frameworkId()`.
- Use `putIfAbsent` and throw `IllegalStateException` on duplicate agent ids.
- Add tests for blank id, null id, duplicate id, and duplicate framework grouping behavior.

## 4. Governance and retirement cleanup findings

### R7 [P2] Retired surfaces still appear as live authority

Affected files include:

- `pom.xml`
- `README.md`
- `docs/contracts/contract-catalog.md`
- `docs/governance/architecture-status.yaml`
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md`
- `spring-ai-ascend-dependencies/module-metadata.yaml`

Evidence:

- Root `pom.xml` still manages a `com.huawei.ascend:agent-middleware` coordinate, although the module was deleted from the reactor.
- `README.md` still lists `agent-middleware` and describes `agent-runtime` as owning `EngineRegistry`, `EngineEnvelope`, and `ExecutorAdapter` SPI.
- `docs/contracts/contract-catalog.md` still lists `AgentResultAdapter`, `AgentHandler`-era dispatch SPI, `RuntimeMiddleware`, `HookDispatcher`, `EnginePort`, `EngineRegistry`, `EngineEnvelope`, and `ExecutorAdapter` as shipped surfaces.
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md` still describes the old EnginePort / EngineRegistry / AgentHandler architecture.
- `docs/governance/architecture-status.yaml#architecture_sync_gate.allowed_claim` still states highest ADR-0159, workspace `403 elements + 240 relationships`, and `15 FEAT- elements`, while nearby metrics now state ADR-0160 and workspace `375 + 206`.

Impact:

The code-level retirement may be mostly complete, but the authoritative prose and metadata still advertise the retired mechanism. This creates conflicting source-of-truth surfaces and can cause future agents to reintroduce the deleted design.

Recommended fix:

- Update root README module table to reflect the current reactor and new starter.
- Update contract catalog SPI tables and module rows to remove retired shipped surfaces or mark them historical/design-only.
- Update `architecture/docs/L1/agent-runtime/ARCHITECTURE.md` to the new neutral core, using generated facts first.
- Update `architecture_sync_gate.allowed_claim` to match the new canonical metrics and ADR-0160.
- Remove stale `agent-middleware` managed dependency if no longer produced by the reactor or BoM.
- Regenerate `architecture/generated/*` and generated facts after the source authority changes.

## 5. Cancellation semantics need an explicit decision

Affected files:

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/dispatch/dispatch/EngineDispatcher.java`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/RunCoordinator.java`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/AgentDriver.java`

Observation:

`EngineDispatcher.cancel()` currently routes only `taskControlClient.markCancelled(...)`. It does not stop an in-flight `RunCoordinator`, cancel a publisher subscription, or call `AgentDriver.stop()` for the running request. The `AgentDriver` SPI has `start/stop/isRunning`, but no request-scoped cancellation handle.

Impact:

If a long-running `EXECUTE` and a `CANCEL` command run concurrently, task-control can move to CANCELLING/CANCELLED while the underlying model call continues. Later completion/failure may be rejected by task-control state validation but still route to access-layer output because `EngineDispatcher.route()` does not inspect whether task-control accepted the transition.

Recommended decision:

- If cancellation is in scope for ADR-0160, add request-scoped cancellation semantics to the neutral core.
- If cancellation is explicitly out of scope, document the limitation and ensure access-layer terminal output is not emitted after a cancel has been accepted.

Minimum tests:

- Long-running publisher plus cancel command should not emit final success to access after cancellation.
- Driver/publisher cancellation hook should be invoked if the new SPI supports it.

## 6. Test coverage gaps

Current generated test facts show useful new coverage for:

- `RunCoordinatorSeamTest`
- `AgentDriverRegistryTest`
- `DifyAgentDriverTest`
- `OpenJiuwenOutputConverterTest`
- `OpenJiuwenAgentDriverEngineE2eTest`

The remaining gaps are concentrated around the native A2A execution boundary:

- No replacement for deleted `EngineDispatcherTest`.
- No dispatcher test for `WAITING_INPUT -> EngineInterruptedEvent -> markWaiting + requestUserInput`.
- No dispatcher test for async `Publisher.onError`.
- No dispatcher test for timeout or interrupted wait.
- No Dify second-turn session/conversation test.
- No Dify delayed-SSE incremental streaming test.
- No Spring context test for multiple `AgentDriver` beans.
- No registry validation test for duplicate or blank driver ids.

Recommended test additions:

1. `EngineDispatcherTest` rebuilt around `AgentDriverRegistry`.
2. `DifyAgentDriverConversationTest` with captured request bodies.
3. `DifyOutputConverterStreamingTest` or a higher-level adapter test using delayed SSE.
4. `AccessLayerConfigurationMultiDriverTest`.
5. `DefaultAgentDriverRegistryValidationTest`.

## 7. Verification performed

Commands run:

```bash
git rev-parse HEAD
git rev-parse origin/main
bash gate/check_architecture_sync.sh
```

Observed:

- `HEAD = 4bf101e319e92081a6e9f9941366362249659e1f`
- `origin/main = 4ac81dfd60f01b0b1a72151f170e839abf2249fe`
- `bash gate/check_architecture_sync.sh` exited 0 with `GATE: PASS`.
- The same gate reported advisory generated-zone drift:
  - `ModulesFragmentEmitter wrote 5 module elements to architecture/generated/modules.dsl`
  - `ARCHITECTURE WORKSPACE (ADVISORY): generated-zone drift`
  - `FAIL: drift detected in 1 fragment(s): modules.dsl`

Not run:

- `./mvnw clean verify`
- examples e2e with real A2A and real LLM

Reason:

This review focused on static code-path correctness and governance consistency. The blocking issues above are visible from current source plus generated facts and do not require full Maven execution to reproduce conceptually. Full `clean verify` is still required before merge after fixes.

## 8. Recommended fix order

1. Fix `EngineDispatcher.collect()` error/timeout/interruption handling and add dispatcher tests.
2. Fix Dify `sessionId -> conversation_id` mapping and add two-turn tests.
3. Decide whether Dify must truly stream in this PR. If yes, replace `BodyHandlers.ofString()` with incremental streaming. If no, rename/document the adapter as buffered and remove streaming claims.
4. Fix `AccessLayerConfiguration` multi-driver behavior.
5. Add registry validation for duplicate/blank driver ids.
6. Refresh generated facts and generated DSL fragments at final head.
7. Update `architecture-status.yaml#architecture_sync_gate.allowed_claim`, README, contract catalog, and L1 agent-runtime architecture docs so retired surfaces are historical or removed.
8. Run:

```bash
bash gate/check_architecture_sync.sh
./mvnw clean verify
```

For final acceptance, the PR should have no P1 findings, no stale generated facts, and no live authority surface claiming the deleted `agent-middleware` / `AgentHandler` / EnginePort execution mechanism is shipped.
