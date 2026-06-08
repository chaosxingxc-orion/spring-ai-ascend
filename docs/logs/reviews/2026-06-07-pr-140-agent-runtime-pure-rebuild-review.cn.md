---
review_target: "PR-140"
review_url: "https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/140"
reviewed_base: "4b1673df6d15ace4db993d2508f16eb139fff8d2"
reviewed_head: "f414a31c245558bc18ca0f0e6bc538cc4a62db32"
review_status: "blocking_findings"
affects_level: "L1"
affects_view:
  - development
  - logical
  - process
authors:
  - "Codex"
created_at: "2026-06-07"
---

# PR 140 Review Report - agent-runtime pure rebuild

## 结论

建议 **暂缓合并**。

PR 140 的主方向是正确的：删除未触达的 engine design-island、收敛 `dispatch -> engine` 命名、把 engine 输出收束到单一 `TaskControlClient` outbound port，这些都符合 Doc 2 里的“做减法”和 single-write authority 目标。但当前分支还存在两个合并阻断问题：

1. 架构权威面、生成 facts、合同目录、README 与实际 4-module reactor 严重不一致；PR 正文也承认 architecture-sync gate 当前会红。
2. `EngineDispatcher` 的 handler lookup 在兜底 `try/catch` 外，未知 `agentId` 会让已入队任务停在非终态，而不是通过 task-control 失败收敛。

下面按严重度列出发现。

## Review Scope

- Repository: `chaosxingxc-orion/spring-ai-ascend`
- PR: `#140 Rebuild agent-runtime: pure restructure per Doc 2 + single-write authority`
- Local branch: `redo/agent-runtime-pure-rebuild`
- Local HEAD: `f414a31c245558bc18ca0f0e6bc538cc4a62db32`
- Base: `origin/main` at `4b1673df6d15ace4db993d2508f16eb139fff8d2`

Rule G-15 caveat: generated fact files were read first, but their `_provenance.repo_commit` is `f7a116e071db463c444b8acaa72db9811250e924`, not the reviewed head `f414a31c...`. Therefore current source lines were used as review evidence, and the stale fact layer itself is listed as a blocking finding.

Relevant stale fact IDs observed:

- `build-module/agent-runtime` in `architecture/facts/generated/module-build.json`
- `code-symbol/com-huawei-ascend-runtime-engine-enginedispatcher` in `architecture/facts/generated/code-symbols.json`
- `code-symbol/com-huawei-ascend-runtime-engine-port-taskcontrolclient` in `architecture/facts/generated/code-symbols.json`
- `test/com-huawei-ascend-runtime-engine-enginedispatchertest` in `architecture/facts/generated/tests.json`

## Findings

### P0-1. Fact layer and architecture authority are not synchronized with the PR shape

Evidence:

- Current reactor has only 4 modules in `pom.xml:34-39`.
- Generated facts are stamped at old commit `f7a116e...`, not reviewed head:
  - `architecture/facts/generated/module-build.json:2-8`
  - `architecture/facts/generated/code-symbols.json:2-8`
  - `architecture/facts/generated/tests.json:2-8`
- `README.md` still says runtime is split across 8 Maven modules and still lists deleted/skeleton modules such as `agent-client`, `agent-middleware`, `agent-evolve`, and `spring-ai-ascend-graphmemory-starter` at `README.md:67-82`.
- `architecture/workspace.dsl` still declares “8 Maven modules” and retains `agentMiddleware`, `agentClient`, `agentEvolve`, and `graphMemoryStarter` containers at `architecture/workspace.dsl:14`, `architecture/workspace.dsl:43-118`.
- `docs/governance/architecture-status.yaml` has numeric fields partly updated to 4 modules, but comments and canonical claim text still describe the old 8-module topology at `docs/governance/architecture-status.yaml:75-86` and `docs/governance/architecture-status.yaml:128`.
- `docs/contracts/contract-catalog.md` still lists old runtime SPI names and package homes (`EngineDispatchApi`, `AgentHandler`, `AgentResultAdapter`, `runtime.dispatch.spi`) at `docs/contracts/contract-catalog.md:50-63`, while the PR renamed these to `engine.*` vocabulary.
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md` still says `agent-runtime -> agent-middleware` is a dependency at `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:67-74` and still documents deleted/renamed SPI entries at `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:181-188`.

Impact:

- This violates the repository's Rule G-15 consumption contract: generated facts must precede prose and act as factual ground truth, but they are stale relative to the PR head.
- Reviewers and agents will get contradictory module topology depending on which authority surface they load.
- The PR cannot honestly pass the canonical architecture-sync gate until facts, generated DSL, module catalogs, contract catalog, and L1 docs are regenerated/refreshed together.

Recommendation:

- Regenerate facts with the deterministic extractor (`./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts`).
- Refresh `architecture/generated/*.dsl`, `architecture/workspace.dsl`, `README.md`, `docs/contracts/contract-catalog.md`, `architecture/docs/L1/**`, and `docs/governance/architecture-status.yaml` so all surfaces agree on the actual module set and renamed SPI/class vocabulary.
- Run `bash gate/check_architecture_sync.sh` and include the pass log in the PR evidence.

Suggested verification:

```bash
./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts
bash gate/check_architecture_sync.sh
python3 gate/lib/sync_baseline.py --check
```

### P1-1. Unknown `agentId` escapes the dispatcher and leaves the task non-terminal

Evidence:

- `TaskControlService.run()` prepares a task and enqueues execution, then returns an accepted result once enqueue succeeds: `agent-runtime/src/main/java/com/huawei/ascend/runtime/control/TaskControlService.java:144-157`, `agent-runtime/src/main/java/com/huawei/ascend/runtime/control/TaskControlService.java:241-286`.
- `InternalEngineCommandGateway.publish()` always returns `true` after `queue.offer(event)`, so enqueue success does not mean the handler exists: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/command/InternalEngineCommandGateway.java:21-24`.
- `EngineWorker` wraps `dispatcher.dispatch(command)` only in `finally`; it logs completion but does not catch and convert runtime exceptions to task-control failure: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/command/EngineWorker.java:68-78`.
- `EngineDispatcher.runHandler()` resolves the handler before the `try/catch`: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/EngineDispatcher.java:51-64`. The catch that routes `EngineFailedEvent` starts later at `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/EngineDispatcher.java:85-103`.
- The registry throws when no handler is registered: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/DefaultAgentRuntimeHandlerRegistry.java:25-29`.
- The current dispatcher tests cover success, emitted failure, and duplicate/blank registration, but not registry miss after a task has already been accepted: `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/EngineDispatcherTest.java:35-78`. Stale fact `test/com-huawei-ascend-runtime-engine-enginedispatchertest` confirms the same method inventory.

Impact:

- A request with an unregistered `agentId` can be accepted and enqueued, then the worker thread throws out of `dispatch()` without calling `TaskControlClient.markFailed(...)`.
- The task remains in a non-terminal state, the A2A/SSE caller may wait forever, and the single-write authority path is bypassed exactly on a common invalid-input failure branch.

Recommendation:

- Move handler lookup inside the guarded execution block, or add a dispatcher-level wrapper that converts any pre-handler exception into `EngineFailedEvent`.
- Prefer emitting `AGENT_ID_INVALID` for missing handler rather than a generic `RuntimeException` failure.
- Add a closed-loop test where task-control accepts a request for an unknown `agentId`, engine execution runs, and the final task state becomes `FAILED` with an egress terminal error.

Suggested verification:

```bash
./mvnw -pl agent-runtime -Dtest=EngineDispatcherTest,EngineClosedLoopIntegrationTest test
./mvnw -pl agent-runtime -Dit.test=AgentServiceEndToEndIT verify
```

### P1-2. Deleted module `agent-middleware` is still reintroduced as a managed dependency and active contract surface

Evidence:

- The `agent-middleware` directory is gone locally (`rg --files agent-middleware` fails with path-not-found).
- This PR removes `agent-runtime-spring-boot-starter-a2a` from `<modules>`, but adds `agent-middleware` to the root parent dependencyManagement at `pom.xml:263-267`.
- `docs/contracts/contract-catalog.md` still advertises `RuntimeMiddleware` as shipped under `agent-middleware` at `docs/contracts/contract-catalog.md:54`, still lists `agent-middleware` in the SPI count table at `docs/contracts/contract-catalog.md:84`, and still lists it as an active BoM artifact at `docs/contracts/contract-catalog.md:203-214`.
- `architecture/docs/L1/README.md` still lists `agent-middleware` as an active L1 module at `architecture/docs/L1/README.md:25-34`.
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md` still says `agent-runtime` depends on `agent-middleware` at `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:69-72`.

Impact:

- External consumers importing the parent/BoM-style dependency management can be pointed at a coordinate that the reactor no longer builds.
- The PR's stated retirement of `agent-middleware` is not reflected in the contract catalog, L1 module index, or runtime dependency promises.
- This is separate from prose drift: `pom.xml` is an executable build contract and the stale managed coordinate was introduced by this PR diff.

Recommendation:

- Remove `agent-middleware` from dependencyManagement unless a published-but-out-of-reactor compatibility artifact is intentionally retained, in which case document that artifact lifecycle explicitly.
- Update contract catalog and L1 docs to mark `agent-middleware` retired/absorbed, with its surviving concepts re-homed to the correct module.
- Add a dependency-management parity check that fails when a local module coordinate is managed but the module directory/artifact authority no longer exists.

### P2-1. `SampleA2aClient` recognizes `cancelled`, but runtime wire status is `canceled`

Evidence:

- `RunStatus.CANCELED.wire()` lowercases the enum name, producing `canceled`: `agent-runtime/src/main/java/com/huawei/ascend/runtime/schema/RunStatus.java:20`, `agent-runtime/src/main/java/com/huawei/ascend/runtime/schema/RunStatus.java:32-35`.
- A2A task-state normalization also maps `TASK_STATE_CANCELED` to `canceled`: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/jsonrpc/A2aJsonRpcHandler.java:480-492`.
- `SampleA2aClient` terminal set contains `completed`, `failed`, and `cancelled`, but not `canceled`: `examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/SampleA2aClient.java:137-143`.
- The sample-client tests only cover `completed`: `examples/agent-runtime-a2a-llm-e2e/src/test/java/com/huawei/ascend/examples/a2a/SampleA2aClientTest.java:73-89`.

Impact:

- The PR appears to have fixed the stale “failed is not terminal” issue mentioned in the PR body, but cancellation still uses the wrong spelling.
- Any terminal cancellation event surfaced with the runtime's canonical `canceled` wire value will not count down the client latch, so the sample client can time out or surface a stream failure on cancellation.

Recommendation:

- Change the terminal set to include `canceled` and consider accepting `cancelled` only as a compatibility alias.
- Add sample-client tests for `failed` and `canceled` terminal metadata.

### P2-2. Runtime log archives are committed into the PR

Evidence:

- Git tracks these runtime artifacts:
  - `agent-runtime/logs/run/run.2026-06-03.0.log.gz`
  - `agent-runtime/logs/run/run.2026-06-06.0.log.gz`
  - `examples/agent-runtime-a2a-llm-e2e/logs/run/run.2026-06-06.0.log.gz`
- `.gitignore` ignores `*.log` but not rotated/compressed `*.log.gz` files: `.gitignore:30-36`.
- Local sizes observed: approximately 25 KB, 80 KB, and 10 KB respectively; uncompressed `run.log` files also exist locally and are ignored.

Impact:

- These files are binary runtime evidence, not source authority.
- They can contain prompts, model responses, local endpoints, correlation IDs, tenant/session IDs, or other operational traces.
- They add review noise and make future log rotation easy to accidentally commit again.

Recommendation:

- Remove the tracked `logs/run/*.gz` files from the PR.
- Add a targeted ignore such as `**/logs/run/*.log*` while keeping `docs/logs/**` tracked.
- If live-LLM evidence is needed, summarize it in a text verification log under `docs/logs/reports` or `docs/logs/reviews`, with sensitive content redacted.

### P2-3. CI/test hang safety net was weakened

Evidence:

- The PR removes the GitHub Actions job `timeout-minutes: 30` from `.github/workflows/ci.yml`.
- The PR removes Surefire's `forkedProcessTimeoutInSeconds` safety net from `pom.xml`; current Surefire config at `pom.xml:324-344` has no fork timeout.
- Failsafe still has a 900-second fork timeout at `pom.xml:355-377`, but that only covers `*IT.java`; unit-test forks remain unbounded.
- The CI workflow still documents known Spring Boot context boot deadlock risk and relies on bounded execution in comments: `.github/workflows/ci.yml:30-37`.

Impact:

- A unit-test deadlock or stuck fork can burn the default GitHub runner timeout and delay feedback by hours.
- This is a process reliability regression unrelated to the runtime refactor's functional goal.

Recommendation:

- Restore the workflow-level timeout and Surefire fork timeout, or replace them with an equivalent bounded-execution policy.
- Keep the Failsafe timeout as-is for integration tests.

### P2-4. Existing A2A streaming registry has a late-subscriber terminal replay race on the PR's e2e path

Scope note: this file is not directly changed by PR 140, so this is listed as an adjacent risk discovered while reviewing the PR's A2A live-e2e claims.

Evidence:

- `A2aJsonRpcHandler.openStream()` submits the task before returning the output handle: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/jsonrpc/A2aJsonRpcHandler.java:110-128`.
- The controller subscribes to output only after `openStream()` returns and after it sends the accepted response: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/ingress/A2aJsonRpcController.java:43-62`.
- `A2aOutputRegistry.subscribe()` replays existing outputs only when the current output list contains no terminal output; if any terminal output already exists, it replays nothing: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputRegistry.java:28-33`.
- The registry handle is tenant + session only, not task-scoped: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputHandle.java:3-6`.

Impact:

- A fast task can complete between task submission and SSE subscription. In that case, the terminal output is already in the registry, but the new subscriber receives no replay and may wait indefinitely.
- Because the handle is session-scoped, an earlier terminal output in the same session can also suppress replay for later subscriptions.

Recommendation:

- Make the stream handle task-scoped or filter replay by `taskId`.
- On subscribe, replay all outputs for the target task, including terminal, then complete the stream if terminal has already been observed.
- Add a test for “terminal output appended before subscribe still reaches the subscriber and completes SSE.”

## Positive Observations

- `EngineDispatcher` now reports lifecycle and output through `TaskControlClient` only: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/EngineDispatcher.java:140-154`.
- `EngineTaskControlAdapter` gates completion/failure egress on accepted task-control transitions: `agent-runtime/src/main/java/com/huawei/ascend/runtime/control/EngineTaskControlAdapter.java:63-79`.
- Registry duplicate and blank-agent validation is now explicit: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/DefaultAgentRuntimeHandlerRegistry.java:15-39`.
- Source-tree design markdown files under `agent-runtime/src/main/java` were deleted/moved; `rg --files agent-runtime/src/main/java | rg '\.(md|gz)$'` returned no matches.

## Verification Performed

Static review commands run:

```bash
git status --short --branch
git rev-parse HEAD
git rev-parse origin/main
git diff --name-status origin/main...HEAD
rg --files architecture/facts/generated
rg -n "agent-runtime|agent-middleware|repo_commit" architecture/facts/generated/*.json
rg -n "agent-middleware|8 Maven modules|runtime.dispatch.spi" README.md architecture docs/contracts pom.xml
rg --files agent-runtime/src/main/java | rg '\.(md|gz)$'
git ls-files | rg '(^|/)logs/run/|\.gz$'
```

Not run:

- Full `./mvnw clean verify`
- Canonical `bash gate/check_architecture_sync.sh`

Reason: PR body already states architecture-sync is red until a follow-up regeneration wave; running the gate during review would be expected to fail and may rewrite generated artifacts. The report therefore treats the stale fact/gate state as a blocking finding.

## Recommended Fix Order

1. Fix `EngineDispatcher` registry-miss handling and add the missing closed-loop test.
2. Remove the stale `agent-middleware` managed coordinate or document a deliberate compatibility artifact.
3. Regenerate facts and architecture/generated DSL from source authority, then update README, L1 docs, contract catalog, and architecture-status together.
4. Remove committed runtime log archives and tighten `.gitignore`.
5. Restore CI/test timeout guards.
6. Fix `SampleA2aClient` canceled terminal spelling and add tests.
7. Either fix or explicitly backlog the A2A registry late-subscriber replay race; do not use live-e2e success alone as proof that this path is race-free.

## Merge Recommendation

Do not merge PR 140 as-is. The runtime refactor direction is promising, but it should be merged only after the task non-terminal bug and architecture/fact drift are closed, and after the PR evidence includes a clean architecture-sync gate plus Java verification appropriate for this repository.
