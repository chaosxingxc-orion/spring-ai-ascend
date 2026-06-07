---
title: "PR 140 复审报告：agent-runtime pure rebuild 后续评审"
review_target: "PR-140 follow-up"
review_url: "https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/140"
reviewed_at: "2026-06-07"
reviewed_base: "4b1673df6d15ace4db993d2508f16eb139fff8d2"
reviewed_head: "ecb6f24cd4dfb668b17e64685011764128d5ca02"
previous_review: "docs/logs/reviews/2026-06-07-pr-140-agent-runtime-pure-rebuild-review.cn.md"
status: "needs_follow_up_before_merge"
---

# PR 140 复审报告：agent-runtime pure rebuild 后续评审

## 结论

本次复审基于 PR 140 最新 head `ecb6f24cd4dfb668b17e64685011764128d5ca02`。上一轮报告中的主要运行时缺陷已经被工程团队修复：unknown `agentId` 已能通过单一 task-control 权威收敛为 `FAILED`，A2A output registry 也已改成 task-scoped replay，日志产物和 CI 超时保护也已处理。

但当前 PR 仍不建议合并。阻断点已经从运行时代码缺陷转为权威事实层与架构/契约文档层漂移：generated facts 仍停在上一提交 `f414a31c...`，`agent-service/module-metadata.yaml` 仍允许依赖已删除的 `agent-middleware`，多处架构/契约权威文档仍把已删除的旧 SPI、旧包、旧模块作为 active surface 描述。这会直接违反 AGENTS.md / Rule G-15 的 AI consumption contract，并让后续评审、门禁和实现者读取到错误架构。

## 本次读取的事实层

按仓库要求，复审先读取了 `architecture/facts/generated/`。这些文件自身显示已经过期：

- `architecture/facts/generated/module-build.json:2-8` 的 `_provenance.repo_commit` 为 `f414a31c245558bc18ca0f0e6bc538cc4a62db32`，不是当前 PR head `ecb6f24cd4dfb668b17e64685011764128d5ca02`。
- `architecture/facts/generated/module-build.json:56-77` 中 fact ID `build-module/agent-service` 仍把 `agent-middleware` 列在 `allowed_dependencies`。
- `architecture/facts/generated/module-build.json:33-54` 中 fact ID `build-module/agent-runtime` 仍是旧 head 提取结果；当前源码已有复审修复，但 facts 尚未重新提取。

因此，本报告对 generated facts 的使用方式是：承认其事实 ID 与当前输出内容，但把它们标记为 stale，不能作为当前 head 的架构验收依据。

## 阻断问题

### P0-1 Generated facts 没有随最新修复提交重新提取

**位置：**

- `architecture/facts/generated/module-build.json:2-8`
- `architecture/facts/generated/module-build.json:56-77`，fact ID `build-module/agent-service`

**问题：**

PR 140 最新 head 是 `ecb6f24cd4dfb668b17e64685011764128d5ca02`，但 generated facts 的 provenance 仍停在 `f414a31c245558bc18ca0f0e6bc538cc4a62db32`。这不是单纯的元数据滞后：`build-module/agent-service` 仍记录 `allowed_dependencies: [agent-runtime, agent-bus, agent-middleware]`，而本次 pure rebuild 已删除 `agent-middleware`，根 POM 也已回到 4 个 reactor modules。

AGENTS.md 明确要求事实声明先读 `architecture/facts/generated/*.json`，并且 generated facts 高于 prose。当前状态会让任何后续 AI session、门禁解释或架构报告基于旧提交做判断，尤其会继续认为 `agent-service -> agent-middleware` 是被允许的模块关系。

**影响：**

这是合并阻断。PR 不能一边声称完成 pure rebuild / 单写 authority，一边留下指向旧 head 的事实层。否则后续 review、ADR 对齐、architecture sync gate 的输入都会出现不可信状态。

**建议修复：**

1. 先修正当前源码权威中的 stale module metadata，尤其是 `agent-service/module-metadata.yaml`。
2. 使用仓库约定命令重新生成事实层：`./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts`。
3. 运行 `bash gate/check_architecture_sync.sh`。
4. 确认所有 `architecture/facts/generated/*.json` 的 `_provenance.repo_commit` 指向当前 PR head，且 fact ID `build-module/agent-service` 不再包含已删除模块。

### P1-1 `agent-service/module-metadata.yaml` 仍允许依赖已删除的 `agent-middleware`

**位置：**

- `agent-service/module-metadata.yaml:17-20`
- `architecture/facts/generated/module-build.json:56-77`，fact ID `build-module/agent-service`

**问题：**

`agent-service/module-metadata.yaml` 当前仍声明：

```yaml
allowed_dependencies:
  - agent-runtime
  - agent-bus
  - agent-middleware
```

但 PR 140 的 root POM 已回到 4 个模块，`agent-middleware` 不再是当前 reactor module。生成事实 `build-module/agent-service` 又把这个 stale 声明提取进事实层，进一步扩大了漂移范围。

**影响：**

这会破坏 module boundary 的可信度：未来如果有人在 `agent-service` 中重新引入对 `agent-middleware` 的依赖，metadata 仍会给出“允许”的信号。对一个目标是删除旧模块、重建纯 runtime 的 PR 来说，这属于架构权威没有收尾。

**建议修复：**

删除 `agent-service/module-metadata.yaml` 中的 `agent-middleware`，并同步刷新 generated facts。若团队认为 `agent-middleware` 只是临时删除、后续要恢复，则需要新的 ADR 或 explicit design-only 标注，而不是保留在 `allowed_dependencies`。

### P1-2 L1 架构文档与契约目录仍把旧运行时架构描述为 active surface

**位置：**

- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:42-55`
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:90-101`
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:118-130`
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:144-158`
- `docs/contracts/contract-catalog.md:31-72`
- `docs/contracts/contract-catalog.md:190-196`

**问题：**

agent-runtime 的 L1 文档仍描述旧的 package / class / SPI 结构，例如：

- `runtime.engine.spi` 仍写着 `ExecutorAdapter`, `GraphExecutor`, `AgentLoopExecutor`, `EngineHookSurface`, `EngineMatchingException`。
- `runtime.engine.planner.spi`, `runtime.engine.runtime`, `runtime.engine.exec`, `runtime.dispatch.spi` 等旧 pure rebuild 前结构仍被列为 module contents。
- bootable app 仍被称作 `AgentRuntimeApplication`，而当前实现已经是 `app.RuntimeApp` / `LocalA2aRuntimeHost` 路线。
- forbidden imports 仍声明 `runtime.engine.spi.*` 可引用 `agent-middleware` SPI，并要求读取 `engine-hooks.v1.yaml` 供 `agent-middleware` 消费。

契约目录也仍把旧 SPI inventory 当作 active surface：`docs/contracts/contract-catalog.md:33-72` 仍保留 “Active SPI interfaces (24 total)” 和多个已删除/旧模块/旧 package 的条目，包括 `RunRepository`, `GraphMemoryRepository`, `ResilienceContract`, `SkillCapacityRegistry`, `ContextProjector`, `TaskStateStore`, `agent-evolve`, `agent-client`, `spring-ai-ascend-graphmemory-starter` 等。

**影响：**

这会让读者在同一个 PR 中看到两套互相冲突的架构：源码已经是 pure runtime rebuild，L1/contract catalog 却仍在指导实现者使用旧 dispatch、旧 engine SPI、旧 middleware hook。对当前仓库的 AI consumption contract 来说，prose 是 intent / rationale，但仍是架构阅读路径的一部分；保留 active-sounding 旧内容会继续制造误导。

**建议修复：**

把 L1 agent-runtime 文档收敛到当前实际架构：`engine.spi.AgentRuntimeHandler` + `StreamAdapter`、`runtime.engine.EngineDispatcher`、`runtime.access.protocol.a2a.*`、`runtime.app.RuntimeApp` / `LocalA2aRuntimeHost`、task-control single authority。契约目录需要把旧 SPI inventory 移到历史段或删除 active claim，只保留当前 shipped / design-only 真实边界，并与 generated facts 重新对齐。

### P1-3 `architecture-status.yaml` 的 canonical counts 注释与 allowed claim 仍引用旧 8 模块/旧 engine-middleware 架构

**位置：**

- `docs/governance/architecture-status.yaml:75-85`
- `docs/governance/architecture-status.yaml:1188-1195`

**问题：**

`architecture-status.yaml` 的字段值已出现 `reactor_modules: 4` 与 `total_reactor_modules: 4`，但同一段 `note` 仍声称 “Reactor returns to 8 modules = ... client/service/bus/engine/middleware/evolve + BoM + GraphMemory starter”。同一段的 `internal_modules` / `skeleton_modules` 注释也仍引用 `agent-middleware`、`agent-execution-engine` 等旧模块。

更严重的是 `allowed_claim` 仍说 engine SPI lives on `agent-execution-engine`，middleware SPI lives on `agent-middleware`。这与 PR 140 的 pure rebuild 目标和当前源码结构不一致。

**影响：**

`architecture-status.yaml` 是 AGENTS.md 指定的 baseline / ledger authority。字段值改成 4，但正文 claim 仍是 8 模块旧叙述，会让 architecture sync gate、人工评审和后续 AI session 都无法判断哪个说法才是 canonical。

**建议修复：**

把 `repository_counts.note`、相关注释和 `allowed_claim` 改写为当前 PR 的真实边界：4 reactor modules、`agent-runtime` 内部承载 runtime app / engine SPI pair / A2A access / task control，`agent-bus` 保留 neutral bus SPI，删除对 `agent-execution-engine` 与 `agent-middleware` 的 active claim。

## 非阻断但建议本轮一并处理

### P2-1 PR body 仍保留上一轮“待修复”叙述

**位置：**

- GitHub PR 140 body

**问题：**

PR body 仍描述 architecture facts/gate pending、SampleA2aClient failed path issue pending 等上一轮状态。当前源码显示部分问题已经修复，例如 `SampleA2aClient` 已把 `completed / failed / canceled / rejected / cancelled` 作为 terminal run status 处理，见 `examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/SampleA2aClient.java:137-145`。

**建议修复：**

更新 PR body，把已修复项、剩余事实层/架构文档层待办、已执行验证命令分开列出。否则 review 入口仍会误导后续 reviewer。

## 已验证修复项

### 已修复：unknown `agentId` 不再导致已 accepted task 挂起

`EngineDispatcher` 现在先 route `EngineStartedEvent`，再解析 handler；如果 registry 无法解析 `agentId`，会 route `EngineFailedEvent`，错误码为 `AGENT_ID_INVALID`，并返回。见 `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/EngineDispatcher.java:51-67`。

闭环测试 `unknownAgentId_convergesTheAcceptedTaskToTerminalFailureThroughControl()` 覆盖了该路径：enqueue 返回 `SUCCESS`，task-control transition 为 `RUNNING:task-x`、`FAILED:task-x`，并断言错误码 `AGENT_ID_INVALID`。见 `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/EngineClosedLoopIntegrationTest.java:85-97`。

### 已修复：A2A output registry 不再被同 session 旧 terminal 抑制

`A2aOutputHandle` 已从 tenant/session scope 改成 tenant/session/task scope，见 `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputHandle.java:3-10`。

`A2aOutputRegistry.subscribe()` 会 replay 当前 task 已 buffer 的所有 output，包括 terminal；如果 replay 已含 terminal，则不再注册 subscriber。append 与 subscribe 在 per-handle buffer 上同步，避免 replay 与注册之间丢事件。见 `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputRegistry.java:15-53`。

对应测试覆盖 late subscriber、open stream replay、同 session 不同 task 不互相抑制，见 `agent-runtime/src/test/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputRegistryTest.java:12-52`。

### 已修复：示例 A2A client 已识别失败/取消/拒绝终态

`SampleA2aClient` 现在把 `completed / failed / canceled / rejected / cancelled` 都视作 terminal run status，见 `examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/SampleA2aClient.java:137-145`。

### 已修复：运行时日志产物和 CI 超时保护

本地复核显示 `.gitignore` 已覆盖 `*.log` 与 `**/logs/run/*.log*`，当前 Git index 中未再发现 `logs/run` 或 `.log.gz` 运行日志产物。CI workflow 已设置 job-level `timeout-minutes: 45`，root POM 中 Surefire / Failsafe 也保留了 fork timeout 保护。

## 建议修复顺序

1. 先清理 source authority：删除 `agent-service/module-metadata.yaml` 中的 `agent-middleware`，改写 `architecture-status.yaml` 的旧 8 模块/旧 engine-middleware claims。
2. 再清理 prose authority：更新 `architecture/docs/L1/agent-runtime/ARCHITECTURE.md` 与 `docs/contracts/contract-catalog.md`，只保留当前 pure rebuild 的 active surfaces。
3. 重新提取 generated facts，确保 `_provenance.repo_commit` 指向当前 PR head，且 fact ID `build-module/agent-service` 不再出现 `agent-middleware`。
4. 运行 `bash gate/check_architecture_sync.sh` 与 `./mvnw clean verify`。
5. 更新 PR body，明确本轮已修复项和剩余验证状态。

## 本次复审命令记录

本次复审以静态读取和 GitHub PR metadata 为主，未运行完整 Maven verify，也未主动刷新 generated facts，避免在 review 报告之外改写事实产物。

已执行的检查包括：

- `git rev-parse HEAD`
- `git status --short --branch`
- `git log --oneline -8`
- `rg --files`
- 读取 `architecture/facts/generated/module-build.json`
- 读取 `agent-service/module-metadata.yaml`
- 读取 `architecture/docs/L1/agent-runtime/ARCHITECTURE.md`
- 读取 `docs/contracts/contract-catalog.md`
- 读取 `docs/governance/architecture-status.yaml`
- 读取 `EngineDispatcher.java`、`EngineClosedLoopIntegrationTest.java`
- 读取 `A2aOutputHandle.java`、`A2aOutputRegistry.java`、`A2aOutputRegistryTest.java`
- 读取 `SampleA2aClient.java`

