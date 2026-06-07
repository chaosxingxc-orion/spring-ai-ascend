---
title: "PR 140 第四轮复审报告：R3 修复验证与剩余契约/特性漂移"
review_target: "PR-140 round-4"
review_url: "https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/140"
reviewed_at: "2026-06-07"
reviewed_base: "4b1673df6d15ace4db993d2508f16eb139fff8d2"
reviewed_head: "9e00fb08927dbb23920b76a5ca2a205aa1ba9297"
previous_reviews:
  - "docs/logs/reviews/2026-06-07-pr-140-agent-runtime-pure-rebuild-review.cn.md"
  - "docs/logs/reviews/2026-06-07-pr-140-agent-runtime-pure-rebuild-follow-up-review.cn.md"
  - "docs/logs/reviews/2026-06-07-pr-140-agent-runtime-pure-rebuild-round3-review.cn.md"
status: "needs_follow_up_before_merge"
---

# PR 140 第四轮复审报告：R3 修复验证与剩余契约/特性漂移

## 结论

本轮复审基于 PR 140 最新 head `9e00fb08927dbb23920b76a5ca2a205aa1ba9297`。工程团队已经接受并处理上一轮 R3 review 中的主要问题：canonical `bash gate/check_architecture_sync.sh` 现在通过，`agent-runtime` boot classifier jar 已移除，`agent-runtime/README.md` 已改为 library-style `RuntimeApp` 入口，E2E example 也新增了 `.env*.example` 与 `scripts/test-e2e.{sh,ps1}`。本地用 Ollama `gemma4:latest` 跑新增脚本，真实 LLM E2E 未 skip，16 个测试全部通过。

但仍建议合并前继续修一轮。当前剩余问题不是运行时代码能否工作，而是 active architecture / contract surfaces 仍保留 pure rebuild 前的 `EngineRegistry` / `ExecutorAdapter` / `EngineEnvelope` / `HookPoint` / `agent-middleware` 叙述，并且部分 verifier 指向已不存在的测试文件。由于 `architecture/workspace.dsl` 是架构 authoring root，generated facts 又要求先于 prose 读取，这类残留会继续误导后续实现者和 AI agent。

## 本轮先读取的事实层

按 AGENTS.md / Rule G-15 要求，本轮先读取 generated facts，再看 prose / DSL。事实层显示当前可用的 runtime engine 测试是：

- `architecture/facts/generated/tests.json:298-311`，fact ID `test/com-huawei-ascend-runtime-engine-engineclosedloopintegrationtest`，source file guess 为 `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/EngineClosedLoopIntegrationTest.java`。
- `architecture/facts/generated/tests.json:314-327`，fact ID `test/com-huawei-ascend-runtime-engine-enginedispatchertest`，source file guess 为 `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/EngineDispatcherTest.java`。

相反，`EngineRegistryResolveTest.java` 不存在；本地 `Test-Path agent-service/src/test/java/com/huawei/ascend/engine/runtime/EngineRegistryResolveTest.java` 返回 `False`。这与 `architecture/features/verification.dsl` 当前 shipped test surface 冲突。

## 已验证修复项

### 已修复：canonical architecture sync gate 通过

本地执行：

```bash
bash gate/check_architecture_sync.sh
```

结果为 `GATE: PASS`，其中 workspace gate 输出：

```text
WORKSPACE: Spring AI Ascend
ELEMENTS: 372
CUSTOM_ELEMENTS: 365
RELATIONSHIPS: 214
PROFILE: OK
ARCHITECTURE WORKSPACE: PASS (W5+ blocking mode — ADR-0147)
```

上一轮 P0 的 `agentMiddleware -> fpHookDispatch` parse failure 已关闭。

### 已修复：baseline parity 通过

本地执行：

```bash
python gate/lib/sync_baseline.py --check
```

结果：

```text
baseline_metrics: all derivable fields match canonical counts.
```

### 已修复：`agent-runtime` 不再发布旧 bootstrap boot jar

`agent-runtime/pom.xml:261-269` 只保留 Failsafe plugin，并用注释声明 `agent-runtime` 作为 plain library 发布，不再配置 Spring Boot repackage / boot classifier。上一轮提到的 `mainClass=com.huawei.ascend.runtime.bootstrap.AgentRuntimeApplication` 已移除。

### 已修复：E2E example 提供本地/云端模型配置模板与测试脚本

新增文件包括：

- `examples/agent-runtime-a2a-llm-e2e/.env.example`
- `examples/agent-runtime-a2a-llm-e2e/.env.ollama.example`
- `examples/agent-runtime-a2a-llm-e2e/.env.openai-compatible.example`
- `examples/agent-runtime-a2a-llm-e2e/scripts/test-e2e.sh`
- `examples/agent-runtime-a2a-llm-e2e/scripts/test-e2e.ps1`

本地 Ollama 服务可用，`curl http://localhost:11434/v1/models` 返回 `gemma4:latest`。执行：

```bash
cd examples/agent-runtime-a2a-llm-e2e
bash scripts/test-e2e.sh .env.ollama.example
```

结果：

```text
loaded env: .env.ollama.example  (provider=openai apiBase=http://localhost:11434/v1 model=gemma4:latest)
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

日志显示真实 LLM 分支使用 `provider=openai apiBase=http://localhost:11434/v1 model=gemma4:latest`，`OpenJiuwenReactAgentA2aE2eTest` 未 skip。

## 剩余问题

### P1-1 Active feature / function-point / verification DSL 仍描述已删除的 `EngineRegistry` / `ExecutorAdapter` dispatch

**位置：**

- `architecture/features/function-points.dsl:222-234`
- `architecture/features/function-points.dsl:331-337`
- `architecture/features/features.dsl:226-244`
- `architecture/features/features.dsl:370-383`
- `architecture/features/engineering-frames.dsl:100-117`
- `architecture/features/verification.dsl:74-83`
- `architecture/features/verification.dsl:134-138`

**问题：**

R3 修复了 `agentMiddleware -> fpHookDispatch` 导致的 DSL parse failure，但 active DSL 中仍有旧 engine-contract 叙述：

- `fpEngineDispatch` 仍写 `EngineRegistry.resolve(envelope) -> typed ExecutorAdapter dispatch via engine-envelope.v1.yaml`。
- `agentRuntime -> fpEngineDispatch` 的关系仍写 `implements EngineRegistry.resolve`。
- `featEngineDispatchAndHooks` 仍声明每个 Run 都经过 `EngineRegistry.resolve(envelope)`，并通过 `RuntimeMiddleware` / `HookPoint` 表达 cross-cutting policies。
- `efEngineRegistry` 仍是 shipped engineering frame，描述 `EngineRegistry strict matching, EngineEnvelope, ExecutorAdapter lifecycle, EngineHookSurface`。
- `testEngineRegistryTest` 仍是 shipped unit test，source file 指向 `agent-service/src/test/java/com/huawei/ascend/engine/runtime/EngineRegistryResolveTest.java`，但该文件不存在。

当前源码事实已经是 `EngineDispatcher` + `AgentRuntimeHandlerRegistry` + `AgentRuntimeHandler` / `StreamAdapter`。generated facts 中可见的实际测试是 `EngineDispatcherTest` 与 `EngineClosedLoopIntegrationTest`，而不是 `EngineRegistryResolveTest`。

**影响：**

这是 active architecture root 的事实漂移。虽然 `bash gate/check_architecture_sync.sh` 已通过，但它没有捕捉到这个语义层面的旧 surface。后续开发者按 DSL 读架构，会以为当前运行时仍有 `EngineRegistry` / `ExecutorAdapter` / `EngineEnvelope` dispatch；AI agent 也可能据此生成或恢复已删除的设计岛。

**建议修复：**

把这组 active elements 改写到当前 pure rebuild surface：

- `fpEngineDispatch` 描述改为 `EngineDispatcher dispatches EngineCommandEvent -> AgentRuntimeHandler by agentId`，并引用当前 `AgentRuntimeHandlerRegistry` / `TaskControlClient` / single control authority。
- `efEngineRegistry` 重命名或替换为 `Engine Dispatcher Frame` / `AgentRuntimeHandler Frame`。
- `testEngineRegistryTest` 删除或替换为当前事实层中的 `test/com-huawei-ascend-runtime-engine-enginedispatchertest` 与 `test/com-huawei-ascend-runtime-engine-engineclosedloopintegrationtest`。
- `featEngineDispatchAndHooks` 若仍是历史/设计残留，应降级为 historical/design_only；若要保留 current shipped feature，必须改成 `AgentRuntimeHandler` + `EngineDispatcher` 的真实实现边界。

### P1-2 `engine-envelope.v1.yaml` / `engine-hooks.v1.yaml` 仍标记 runtime_enforced，并被打包进 `agent-runtime` classpath

**位置：**

- `agent-runtime/pom.xml:246-258`
- `docs/contracts/engine-envelope.v1.yaml:7-20`
- `docs/contracts/engine-envelope.v1.yaml:32-37`
- `docs/contracts/engine-envelope.v1.yaml:54-70`
- `docs/contracts/engine-hooks.v1.yaml:7-18`
- `docs/contracts/engine-hooks.v1.yaml:22-34`
- `docs/contracts/engine-hooks.v1.yaml:95-120`
- `docs/contracts/contract-catalog.md:97`

**问题：**

`agent-runtime/pom.xml` 仍把 `engine-envelope.v1.yaml`、`engine-hooks.v1.yaml`、`s2c-callback.v1.yaml` 复制到 runtime classpath，并且注释仍写：

```text
EngineRegistry.readYaml() tries filesystem first,
then classpath fallback at "/docs/contracts/<name>.yaml".
```

但当前 `agent-runtime/src/main/java` 中没有 `EngineRegistry.readYaml()`，也没有 `EngineRegistry` / `ExecutorAdapter` / `EngineEnvelope` / `HookPoint` / `HookDispatcher` runtime implementation。对应的 contract YAML 仍标记 `status: runtime_enforced`，并描述不存在的 Java 类型：

- `engine-envelope.v1.yaml` 说 Java record `com.huawei.ascend.runtime.engine.runtime.EngineEnvelope` mirrors this schema，且 `EngineRegistry.resolve(...)` enforcing `known_engines`。
- `engine-hooks.v1.yaml` 说 Java enum `com.huawei.ascend.middleware.spi.HookPoint` mirrors hooks，并由 HookDispatcher enforce ordering。

**影响：**

这会把 pure rebuild 已删除的 contract surface 继续作为 runtime artifact 发布。即便代码路径不使用它们，消费者从 `agent-runtime` jar classpath 看到这些 YAML，也会合理推断这些是当前 runtime-enforced contracts。`contract-catalog.md:97` 同样把 `engine-envelope.v1.yaml` 标为 `runtime_enforced`，扩大了误导范围。

**建议修复：**

合并前二选一：

1. 如果 `EngineEnvelope` / `EngineHooks` 已被 pure rebuild 退休，则从 `agent-runtime` resources 打包清单中移除 `engine-envelope.v1.yaml` 与 `engine-hooks.v1.yaml`，并把 contract catalog / YAML status 改为 historical 或 archived。
2. 如果这些合同仍是未来设计目标，则把 status 降为 `design_only` / `deferred`，删除 `runtime_enforced` 字样和不存在 Java 类型声明，并确保它们不会被当前 runtime classpath 当作已执行合同发布。

### P1-3 Capabilities DSL 仍有 accepted capability 归属到已退休的 `agent-middleware`

**位置：**

- `architecture/features/capabilities.dsl:510-529`

**问题：**

`cap_hook_safety_critical_carve_out` 与 `cap_hook_tie_break_determinism` 仍是 `saa.status = accepted`，且 owner 仍为 `agent-middleware`。PR 140 当前叙述是 `agent-middleware` 已退休并不在 reactor，也不再有 active L1 module directory。

**影响：**

这虽然不像上一轮的 dangling relationship 那样让 workspace 解析失败，但仍是 active capability registry 中的 owner 漂移。后续 capability 盘点会继续把 hook 能力归给一个已退休模块。

**建议修复：**

如果 hook capability 随 `agent-middleware` 一起退休，应移出 active capabilities 或标记 historical/deferred；如果它们要迁移到 `agent-runtime` 或 `agent-bus`，需要明确 owner、status、ADR authority 和验证路径。

### P2-1 E2E 配置脚本已可用，但只覆盖自动化 test，manual server/client 脚本仍缺失

**位置：**

- `examples/agent-runtime-a2a-llm-e2e/scripts/test-e2e.sh`
- `examples/agent-runtime-a2a-llm-e2e/scripts/test-e2e.ps1`
- `examples/agent-runtime-a2a-llm-e2e/README.md`

**问题：**

本轮新增的 `test-e2e` 脚本已经满足“复制 env 文件后跑自动化 E2E”的核心场景。但 README 仍保留手工启动 server/client 的命令，尚未提供同等的 `run-server` / `run-client` 脚本来加载同一份 env 文件。

**影响：**

这不是合并阻断，因为 automated E2E 已经可复现；但从 outsider 体验看，手工验证仍需要用户自己记得加载 env。团队若把 example 作为客户参考，建议把手工路径也脚本化。

**建议修复：**

补充 `scripts/run-server.{sh,ps1}` 与 `scripts/run-client.{sh,ps1}`，复用与 `test-e2e` 相同的 env loading 逻辑。这样本地 Ollama、LiteLLM gateway、云端 OpenAI-compatible API 都只需要换 env 文件，不需要改命令。

## 验证记录

本轮实际执行：

```bash
git fetch origin redo/agent-runtime-pure-rebuild
git rev-parse HEAD
git status --short --branch
bash gate/check_architecture_sync.sh
python gate/lib/sync_baseline.py --check
cd examples/agent-runtime-a2a-llm-e2e
bash scripts/test-e2e.sh .env.ollama.example
```

结果摘要：

- 当前 head：`9e00fb08927dbb23920b76a5ca2a205aa1ba9297`
- 工作区：干净
- `bash gate/check_architecture_sync.sh`：PASS
- `python gate/lib/sync_baseline.py --check`：PASS
- `bash scripts/test-e2e.sh .env.ollama.example`：PASS，16 tests / 0 failures / 0 errors / 0 skipped
- 未重新运行完整 `./mvnw -Pquality verify`

