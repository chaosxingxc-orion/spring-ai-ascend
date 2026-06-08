---
title: "PR 140 第五轮复审报告：pure rebuild 收敛验证与剩余规则/基线漂移"
review_target: "PR-140 round-5"
review_url: "https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/140"
reviewed_at: "2026-06-07"
reviewed_base: "4b1673df6d15ace4db993d2508f16eb139fff8d2"
reviewed_head: "3bdd75ad52d0d2eca23757ec5baa4197ede14c8e"
previous_reviews:
  - "docs/logs/reviews/2026-06-07-pr-140-agent-runtime-pure-rebuild-review.cn.md"
  - "docs/logs/reviews/2026-06-07-pr-140-agent-runtime-pure-rebuild-follow-up-review.cn.md"
  - "docs/logs/reviews/2026-06-07-pr-140-agent-runtime-pure-rebuild-round3-review.cn.md"
  - "docs/logs/reviews/2026-06-07-pr-140-agent-runtime-pure-rebuild-round4-review.cn.md"
status: "needs_follow_up_before_merge"
---

# PR 140 第五轮复审报告：pure rebuild 收敛验证与剩余规则/基线漂移

## 结论

本轮复审基于 PR 140 最新 head `3bdd75ad52d0d2eca23757ec5baa4197ede14c8e`。上一轮 round-4 中指出的核心问题大多已经关闭：`engine-envelope.v1.yaml` / `engine-hooks.v1.yaml` 已降为 `design_only`，`agent-runtime` 不再把这两个 retired contracts 打进 classpath，active capability registry 中的两个 `agent-middleware` hook capability 已删除，example 增加了 `run-server` / `run-client` 脚本，Dockerfile 改为构建 example runnable jar，本地 Ollama E2E 也能继续跑通。

但仍建议合并前再修一轮。当前剩余问题主要集中在“权威文本/规则仍和事实层不一致”：`architecture-status.yaml#allowed_claim` 仍宣称 workspace 是 `372 elements`，而实际 gate 与 baseline 已是 `370`; active Rule R-M 仍要求所有 Run 走已退休的 `EngineRegistry` / `ExecutorAdapter` / `HookPoint` / `RuntimeMiddleware`; L0 §4 仍把 `agent-runtime -> agent-middleware` 作为当前依赖方向写入约束正文。这些不是运行时代码 bug，但会继续误导开发团队、gate 维护者和 AI agent。

## 本轮先读事实层

按 AGENTS.md / Rule G-15，本轮先读取 `architecture/facts/generated/`，再读 prose / DSL。关键事实如下：

- `architecture/facts/generated/module-build.json:33-52`，fact ID `build-module/agent-runtime`：当前 `agent-runtime` 的 `allowed_dependencies` 只有 `agent-bus`，`forbidden_dependencies` 包含 `agent-service`。
- `architecture/facts/generated/module-build.json:56-75`，fact ID `build-module/agent-service`：当前 `agent-service` 允许依赖 `agent-runtime` 与 `agent-bus`。
- `architecture/facts/generated/contract-surfaces.json:269-294`，fact IDs `contract-yaml/engine-envelope` 与 `contract-yaml/engine-hooks`：两者当前 status 均为 `design_only`。
- `architecture/facts/generated/code-symbols.json:1542-1549` / `1845-1849` / `2327-2331`：当前 engine dispatch 事实层是 `AgentRuntimeHandlerRegistry`、`EngineDispatcher`、`AgentRuntimeHandler`。
- `architecture/facts/generated/tests.json:301-327`：当前 shipped runtime engine 测试事实为 `EngineClosedLoopIntegrationTest` 与 `EngineDispatcherTest`。
- 本地源树搜索 `class EngineRegistry|record EngineEnvelope|interface ExecutorAdapter|enum HookPoint|interface RuntimeMiddleware|class HookDispatcher` 在 `agent-runtime/src`、`agent-service/src`、`agent-bus/src` 下无结果。

## 已验证关闭项

### 已关闭：round-4 P1 contract runtime_enforced 漂移

`docs/contracts/engine-envelope.v1.yaml:7-14` 已明确声明 retired：没有当前 Java type，`EngineEnvelope` / `EngineRegistry` / `ExecutorAdapter` 已移除，dispatch 现在是 `EngineDispatcher -> AgentRuntimeHandler`。

`docs/contracts/engine-envelope.v1.yaml:25-27` 的 status 已为 `design_only`。

`docs/contracts/engine-hooks.v1.yaml:7-12` 已明确声明 hook / middleware surface 已移除，没有当前 Java mirror type。

`docs/contracts/engine-hooks.v1.yaml:23` 的 status 已为 `design_only`。

`docs/contracts/contract-catalog.md:97-98` 也已把二者标为 `design_only`。

### 已关闭：round-4 P1 classpath contract 打包漂移

`agent-runtime/pom.xml:243-255` 当前只把 `s2c-callback.v1.yaml` 打进 `docs/contracts` classpath，并注释说明 `engine-envelope` / `engine-hooks` 已退休，不再被读取或打包。

本轮 `./mvnw -Pquality verify` 输出也显示 `agent-runtime` 只从 `../docs/contracts` copy 了 1 个 resource。

### 已关闭：round-4 P1 capability owner 漂移

`architecture/features/capabilities.dsl` 已删除上一轮指出的 `cap_hook_safety_critical_carve_out` 与 `cap_hook_tie_break_determinism` 两个 `agent-middleware` capability。当前 gate workspace projection 为 `370 nodes / 201 edges`，对应删除了 round-3 的两个 hook capability 节点。

### 已关闭：round-4 P2 manual example 脚本缺口

新增脚本：

- `examples/agent-runtime-a2a-llm-e2e/scripts/run-server.sh`
- `examples/agent-runtime-a2a-llm-e2e/scripts/run-server.ps1`
- `examples/agent-runtime-a2a-llm-e2e/scripts/run-client.sh`
- `examples/agent-runtime-a2a-llm-e2e/scripts/run-client.ps1`

shell / PowerShell 两套脚本都复用 env 文件加载逻辑，满足“本地 Ollama / OpenAI-compatible 云端 API 只换 env 文件”的 outsider 体验。

## 仍需处理的问题

### P1-1 `architecture-status.yaml#allowed_claim` 仍保留 372 workspace elements，和当前 canonical baseline / gate 输出冲突

**位置：**

- `docs/governance/architecture-status.yaml:122`
- `docs/governance/architecture-status.yaml:129`

**问题：**

同一个文件里，`baseline_metrics.workspace_elements` 已经更新为 `370`，注释也说明 round-4 closure 删除了两个 `agent-middleware` hook capabilities；但 `architecture_sync_gate.allowed_claim` 仍写：

```text
Workspace closure at architecture/workspace.dsl: 372 elements + 201 relationships
```

AGENTS.md 明确把 `docs/governance/architecture-status.yaml` 的 `architecture_sync_gate.allowed_claim` 称为 canonical baseline 来源。现在 canonical 字段自己和派生 baseline / gate 输出冲突。

**影响：**

后续 README、PR body、release note 或 AI agent 若按 AGENTS.md 引用 `allowed_claim`，会继续传播 `372` 的旧基线。PR body 当前也仍写了 “workspace baseline 372/201”，与本轮 gate 的 `370 nodes / 201 edges` 不一致。

**建议修复：**

把 `allowed_claim` 中的 workspace closure 数字改为 `370 elements + 201 relationships`，并同步 PR body / 任何 release-facing baseline 文案。建议补一个小的 guard，至少检查 `allowed_claim` 中的 workspace elements / relationships 与 `baseline_metrics.workspace_elements` / `workspace_relationships` 一致。

### P1-2 Active Rule R-M 仍强制旧 EngineRegistry / HookPoint 设计，和 pure rebuild 后事实层冲突

**位置：**

- `docs/governance/rules/rule-R-M.md:2-14`
- `docs/governance/rules/rule-R-M.md:89-128`

**问题：**

Rule R-M frontmatter 仍是 `status: active`，但 kernel 仍写：

- 每个 Run dispatch 必须走 `EngineRegistry.resolve(envelope)`。
- `engine_type=X` 必须通过注册在 X 下的 `ExecutorAdapter` 执行。
- cross-cutting policies 必须由 `RuntimeMiddleware` 监听 `HookPoint` 事件表达。

这与本轮事实层和 contract surface 冲突：`contract-yaml/engine-envelope` / `contract-yaml/engine-hooks` 已是 `design_only`；当前 code-symbol facts 是 `EngineDispatcher` / `AgentRuntimeHandlerRegistry` / `AgentRuntimeHandler`；源树不存在 `EngineRegistry`、`EngineEnvelope`、`ExecutorAdapter`、`HookPoint`、`RuntimeMiddleware` 或 `HookDispatcher`。

**影响：**

这是 enforceable rule surface，不是普通 prose。它会让后续实现者认为 pure rebuild 后仍必须重建已退休的 envelope-matching / middleware hook island；也会让规则卡、enforcer、contract catalog 之间继续互相矛盾。

**建议修复：**

重写 Rule R-M，使 active kernel 对齐当前事实层：engine dispatch 应描述为 `EngineDispatcher` 按 `agentId` 路由到 `AgentRuntimeHandlerRegistry` 中的 `AgentRuntimeHandler`，unknown `agentId` 收敛为 `AGENT_ID_INVALID`；已退休的 envelope / hook 子条款应标记为 historical / design_only / deferred，并与 `engine-envelope.v1.yaml`、`engine-hooks.v1.yaml` 的 retired 声明一致。相关 enforcer_refs 也要同步清理或标记 retired，避免继续指向不存在的 `EngineRegistry` / `HookPoint` test surface。

### P1-3 L0 §4 当前约束仍写 8-module reactor 与 `agent-runtime -> agent-middleware` 依赖

**位置：**

- `architecture/docs/L0/ARCHITECTURE.md:295`
- `architecture/docs/L0/ARCHITECTURE.md:302`
- `architecture/docs/L0/ARCHITECTURE.md:428`
- `architecture/docs/L0/ARCHITECTURE.md:436`

**问题：**

L0 §4 #1 仍写 “Maven-level direction in the 8-module reactor”，并声明 `agent-runtime` 依赖 `agent-bus` 和 `agent-middleware`，其中 `agent-middleware` 用于 `HookPoint`。L0 §4 #16 仍把 “每个 LLM invocation / tool call / memory access / suspension / resume / error boundary 都经过 hook chain” 写成当前约束，并声明 hooks 由 `agent-middleware.HookDispatcher` 分发。

这与同一文件上方 `architecture/docs/L0/ARCHITECTURE.md:165` 的“historical modules retired / NOT in the reactor”相矛盾，也与 `build-module/agent-runtime` fact 中 `allowed_dependencies: [agent-bus]` 冲突。

**影响：**

L0 §4 是 declarative constraint corpus。即使 workspace DSL / generated facts 已经收敛，L0 当前约束还在表达已删除的模块和 hook runtime，会导致架构审查时出现两套“当前架构”。

**建议修复：**

把 §4 #1 改成当前 4-module reactor 的依赖方向：`agent-service -> agent-runtime, agent-bus`; `agent-runtime -> agent-bus`; `agent-bus` 无 inner peer；BoM 无运行时依赖。把 §4 #16 改成 design-only / retired hook vision，或移到 deferred capability/contract 说明中；不要继续用 MUST / Every boundary flows through 这类当前强约束语气描述已删除 runtime。

### P2-1 L1 agent-service 仍保留旧 engine island 的功能与测试叙述

**位置：**

- `architecture/docs/L1/agent-service/features/README.md:108`
- `architecture/docs/L1/agent-service/features/README.md:159-160`
- `architecture/docs/L1/agent-service/features/engine-dispatch-execution.md:17`
- `architecture/docs/L1/agent-service/process.md:33-35`

**问题：**

L1 agent-service 入口仍说每个 Run 通过 `EngineRegistry.resolve(envelope)`，并列出 `EngineRegistryIT` / `HookDispatchTest` 作为验证测试；process view 仍画 `EngineRegistry`、`ExecutorAdapter`、`RuntimeMiddleware chain`。这些内容和当前 feature DSL / verification DSL 已更新后的 `EngineDispatcherTest` 不一致。

**影响：**

L1 prose 按 AGENTS.md 是 module design 的阅读入口。虽然 generated facts 优先级更高，但开发者实际阅读时会被 L1 agent-service 的旧引导带回 pure rebuild 前的设计。

**建议修复：**

至少把 L1 agent-service 的 engine-dispatch feature catalog、process view 和 verification list 改成 current-state 或明确标记 historical/design_only。当前 shipped runtime engine 文档应指向 `agent-runtime` 的 `EngineDispatcher` / `AgentRuntimeHandler` / `EngineDispatcherTest` / `EngineClosedLoopIntegrationTest`。

### P2-2 `./mvnw -Pquality verify` 成功，但 Checkstyle 实际报告了 3 个 error

**位置：**

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/jsonrpc/A2aJsonRpcHandler.java:35`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/AbstractAgentRuntimeHandler.java:3`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/session/InMemoryRuntimeSessionRepository.java:5`
- `pom.xml:427-428`
- `pom.xml:473-476`

**问题：**

本轮执行 `./mvnw -Pquality verify` 返回 `BUILD SUCCESS`，但 Maven 输出中有：

```text
There are 3 errors reported by Checkstyle 10.20.2
```

`agent-runtime/target/checkstyle-result.xml` 中三个 error 分别是：

- `A2aJsonRpcHandler.java:35` unused import `SendStreamingMessageRequest`。
- `AbstractAgentRuntimeHandler.java:3` redundant same-package import `AgentRuntimeHandler`。
- `InMemoryRuntimeSessionRepository.java:5` redundant same-package import `RuntimeSessionRepository`。

同时 parent POM 里 `maven-checkstyle-plugin` 配置了 `failsOnError=false` 与 `failOnViolation=false`，quality profile 绑定的是 report goal `checkstyle`，所以这些 error 不会阻断 `verify`。

**影响：**

这不是运行时风险，但会破坏“quality verify green == lint green”的直觉；也和 AGENTS.md 里的 pre-commit “lint green” 约定不一致。PR body 说 `./mvnw -Pquality verify` green 时，实际仍有 lint error 被报告但未 fail。

**建议修复：**

先删除三个 import，使本轮源码 lint 真实干净。后续可单独决定是否把 quality profile 的 Checkstyle 从 report-only 调整为 fail-closed；如果短期仍保持 report-only，PR 验证记录里应避免把它表述成 lint green。

## 验证记录

本轮实际执行：

```bash
git rev-parse HEAD
bash gate/check_architecture_sync.sh
python gate/lib/sync_baseline.py --check
cd examples/agent-runtime-a2a-llm-e2e
bash scripts/test-e2e.sh .env.ollama.example
cd ../..
./mvnw -Pquality verify
```

结果摘要：

- 当前 head：`3bdd75ad52d0d2eca23757ec5baa4197ede14c8e`
- `bash gate/check_architecture_sync.sh`：PASS；workspace validator 输出 `ELEMENTS: 370`、`RELATIONSHIPS: 214`；projection 输出 `370 nodes / 201 edges`。
- `python gate/lib/sync_baseline.py --check`：PASS，`baseline_metrics: all derivable fields match canonical counts.`
- `bash scripts/test-e2e.sh .env.ollama.example`：PASS；加载本地 Ollama OpenAI-compatible endpoint `http://localhost:11434/v1`，model `gemma4:latest`；`Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`。
- `./mvnw -Pquality verify`：BUILD SUCCESS；reactor 5/5 成功；但 Checkstyle report 中存在 3 个 error，见 P2-2。
- 工作区在写本报告前为 clean；本轮仅新增本报告文件。

## 合并建议

建议暂缓合并，先处理 P1-1 到 P1-3。它们都是 authority / rule drift，修复范围主要在文档、规则卡和基线文案，不需要推翻当前运行时代码。P2-1 / P2-2 可以同一轮顺手处理：一个是 L1 reader experience，一个是 import 清理与 quality signal 诚实性。
