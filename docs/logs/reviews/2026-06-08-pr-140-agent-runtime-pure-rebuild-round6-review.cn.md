# PR 140 第六轮复审报告：R5 收敛验证与剩余 authority 漂移

- PR: https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/140
- 复审时间: 2026-06-08
- 复审对象: `redo/agent-runtime-pure-rebuild`
- 复审 head: `66f73bc84c7f33cb66be5c75319e348c06a819b6`
- 基线: `main@4b1673df6d15ace4db993d2508f16eb139fff8d2`
- 结论: **建议暂缓合并，先做一轮 authority/L1 文档扫尾。运行代码、gate、example e2e 已明显收敛；剩余风险集中在活动规则卡与 L1 设计入口仍会把读者带回已删除的 EngineRegistry/ExecutorAdapter/HookPoint 设计。**

## 0. 本轮复审的 strongest reading 与根因

我把“之前的 review 意见已经被接受”理解为：本轮不重复追打已关闭事项，而是检查 R5 修复是否真正沿着仓库声明的 reading path 闭环，尤其是 `architecture/facts/generated/`、L0、L1、规则卡、gate baseline 与可运行 example 是否一致。

机械根因: R5 主要修复了被点名的 kernel、局部 L0/L1 段落和 Checkstyle 问题，但没有对同一缺陷族做完整 corpus sweep；因此活动入口文件仍保留旧 engine-registry/hook 设计的正文、frontmatter 或流程图。

## 1. Findings

### P1-1. Rule R-M 仍是“半重写”：kernel 已改，active frontmatter + sub-clause 正文仍把旧 enforcer/test 当成规则证据

证据:

- `docs/governance/rules/rule-R-M.md:8` 的 `enforcer_refs` 仍列出 `E74`, `E75`, `E78` 等旧 enforcer。
- 同文件 `:80` 已声明 `.b/.c` 为 `RETIRED / design_only`，并说明下面的 `EngineRegistry` / hook enforcer note 是 historical。
- 但 `.a/.b/.c` 正文仍以活动 cross-reference 形式引用旧机制:
  - `docs/governance/rules/rule-R-M.md:92` 仍写 Gate Rule 55 + `EnginePayloadDispatchOnlyViaRegistryTest`，并声称 orchestrator depends on `EngineRegistry`。
  - `docs/governance/rules/rule-R-M.md:106` 仍写 `engine_registry_covers_all_known_engines` + `EngineMatchingStrictnessIT`。
  - `docs/governance/rules/rule-R-M.md:126` 仍写 `engine_hooks_yaml_present_and_wellformed`, `EveryEngineDeclaresHookSurfaceTest`, `RuntimeMiddlewareInterceptsHooksIT`。
- 本轮搜索 `docs/governance/enforcers.yaml gate architecture/generated docs/governance/rules/rule-R-M.md`，这些旧测试/规则名基本只剩在 Rule R-M 自身或 grandfathered 列表中；它们不是当前可执行事实。

为什么是 P1:

Rule card 是 active authority surface，不只是历史日志。现在的状态要求读者先相信 `:80` 的总括说明，再反向忽略 `:92/:106/:126` 的正文和 `:8` 的 frontmatter。对开发团队和后续 agent 来说，这会直接破坏 Rule G-3/G-8 类的“规则卡-执行证据一致性”阅读契约。

建议修复:

1. 把 Rule R-M 拆成清晰的 current/historical 两段：active sub-clause `.a` 只引用 `EngineDispatcher` / `AgentRuntimeHandler` / `AgentRuntimeHandlerRegistry` / `TaskControlClient` 的当前 enforcer/test。
2. `.b/.c` 要么移入明确的 historical appendix，要么在每个 sub-clause 标题与 cross-reference 行内标记 `RETIRED / design_only`，不要保留“Enforced by ...”这种现在时。
3. `enforcer_refs` frontmatter 只保留仍然 active 的 enforcer；已退休 enforcer 放入 `historical_enforcer_refs` 或删除。

### P1-2. L1 `agent-service` 当前设计入口仍大量指向已删除 EngineRegistry/ExecutorAdapter/HookPoint 运行模型

证据:

- `architecture/docs/L1/agent-service/process.md` 的 participant 已局部改成当前名:
  - `:33` `EngineDispatcher`
  - `:34` `AgentRuntimeHandler`
  - `:35` `RuntimeMiddleware chain (RETIRED / design_only)`
- 但同一个 sequence diagram 的消息流仍是旧设计:
  - `:51` `dispatch(runId, envelope)`
  - `:59` `resolve(envelope)`
  - `:60` `ExecutorAdapter`
  - `:62` `HookPoint.before_llm`
  - `:73-76` 慢路径重复 `resolve(envelope)` / `ExecutorAdapter` / `HookPoint.before_tool`
- 同文件后续流程仍把 executor 标成 L5a `ExecutorAdapter`:
  - `architecture/docs/L1/agent-service/process.md:170`
  - `architecture/docs/L1/agent-service/process.md:212`
- L1 总入口也仍旧:
  - `architecture/docs/L1/agent-service/ARCHITECTURE.md:333-342` 仍声明 `ExecutorAdapter`, `EngineRegistry`, `EngineEnvelope` 的 source home。
  - `architecture/docs/L1/agent-service/ARCHITECTURE.md:352-356` 仍声明 every Run dispatch through `EngineRegistry.resolve(envelope)` and mismatch raises `EngineMatchingException`。
  - `architecture/docs/L1/agent-service/ARCHITECTURE.md:645-649` 仍说 `EngineRegistry.resolve` boundary remains asserted by Rule R-M.a。
- 其他 L1 子文档也还有同族残留，例如:
  - `architecture/docs/L1/agent-service/development.md:102-104`
  - `architecture/docs/L1/agent-service/logical.md:39-65`
  - `architecture/docs/L1/agent-service/physical.md:18-20`, `:114-115`
  - `architecture/docs/L1/agent-service/scenarios.md:96-103`, `:130`
  - `architecture/docs/L1/agent-service/spi-appendix.md:142-146`

事实层对照:

- `architecture/facts/generated/code-symbols.json` 当前存在:
  - `code-symbol/com-huawei-ascend-runtime-engine-agentruntimehandlerregistry`
  - `code-symbol/com-huawei-ascend-runtime-engine-enginedispatcher`
  - `code-symbol/com-huawei-ascend-runtime-engine-spi-agentruntimehandler`
- `architecture/facts/generated/tests.json` 当前存在:
  - `com.huawei.ascend.runtime.engine.EngineDispatcherTest`
  - `com.huawei.ascend.runtime.engine.EngineClosedLoopIntegrationTest`

为什么是 P1:

AGENTS/reading path 明确要求 L1 module design 是实现阅读面。当前 L1 `README/process/ARCHITECTURE/logical/development/scenarios/spi-appendix` 的组合会让 outsider 读出两套互斥事实：facts 和 runtime 说 `EngineDispatcher -> AgentRuntimeHandler`，L1 流程却还说 `EngineRegistry.resolve -> ExecutorAdapter -> HookPoint`。这会直接影响后续实现、review 和 ADR 决策。

建议修复:

1. 对 `architecture/docs/L1/agent-service/` 做一次完整的 deleted-design sweep，而不是只改 `features/README.md` 和局部 process participant。
2. 对仍需保留的旧 Layer 5a 设计，统一移入 `design_only` / `historical` 小节，并在标题、表格和 cross-reference 里显式标注，避免正文现在时。
3. 把 current-state 流程改成当前事实层对应的 dispatch 语义：runtime host 接受 command/event，`EngineDispatcher` 按 `agentId` 查 `AgentRuntimeHandlerRegistry`，未知 agentId 走 terminal `AGENT_ID_INVALID`，结果写回 `TaskControlClient`，caller-facing egress 由 `control` gating。

### P2-1. L0 §4 #7 的 SPI purity 仍把已删除 `agent-middleware` / `HookPoint` 写成默认或 legacy 载体，和 ADR-0159 后的模块事实不一致

证据:

- `architecture/docs/L0/ARCHITECTURE.md:257-261` 仍说 rc52 strict rule applies to all `com.huawei.ascend.middleware..spi..` packages。
- `architecture/docs/L0/ARCHITECTURE.md:339-345` 在活动约束 #7 下仍把 new agentic SPI surfaces default 写成 `com.huawei.ascend.middleware..spi..`。
- `architecture/docs/L0/ARCHITECTURE.md:347-353` 又说 historical pre-rc52 SPI outside `agent-middleware` includes `agent-runtime.engine.spi` using orchestration carriers and `HookPoint`。
- 同文件 `:422-425` 已正确声明 Runtime Hook SPI retired/design_only，这说明 #16 已收敛，但 #7 没跟上。

为什么是 P2:

这是 L0 活动约束里的表述漂移。它不像 P1-2 那样直接破坏当前流程图，但会让未来新增 SPI 时误以为默认命名空间仍是 `agent-middleware`，或者误把 `HookPoint` 当成 current legacy carrier。

建议修复:

1. 把 #7 的 current default 改为当前事实层和 ADR-0159 后的 SPI home，例如 `agent-bus.bus.spi.*` 与 `agent-runtime.engine.spi.*` 的纯度约束。
2. 如果 `com.huawei.ascend.middleware..spi..` 只是历史规则，应移入 historical note，并说明 `agent-middleware` 已删除。
3. 删除或改写 `agent-runtime.engine.spi uses ... HookPoint` 这类与当前事实冲突的 legacy carrier 例子。

## 2. 已验证关闭/收敛项

以下是本轮认可的已关闭项，不再作为新 finding:

1. `docs/governance/architecture-status.yaml:129` 的 workspace baseline 已收敛到 **370 elements / 201 relationships**，与 PR body 中 R5 claim 一致。
2. Rule R-M kernel 已经从旧 `EngineRegistry.resolve(envelope)` 主语改成当前 `EngineDispatcher -> AgentRuntimeHandler` 主语；问题只剩正文/frontmatter 没完全闭环。
3. L0 §4 #1 的 4-module dependency direction 已经改到 ADR-0159 后状态。
4. L0 §4 #16 已明确标成 `RETIRED / design_only`，并说明 `HookPoint` / `RuntimeMiddleware` / `HookDispatcher` 无当前 Java type。
5. Checkstyle 的 3 个 import 问题已关闭；本轮检查 `agent-runtime/target/checkstyle-result.xml` 没有 `<error`。
6. example 的配置性诉求已基本满足:
   - `examples/agent-runtime-a2a-llm-e2e/.env.example`
   - `examples/agent-runtime-a2a-llm-e2e/.env.ollama.example`
   - `examples/agent-runtime-a2a-llm-e2e/.env.openai-compatible.example`
   - `scripts/test-e2e.sh` / `scripts/test-e2e.ps1`
   - `scripts/run-server.*` / `scripts/run-client.*`

## 3. Outsider e2e example 可运行性复核

从 outsider 视角，这一轮 example 已经比前几轮好很多：开发者可以复制 `.env.example`，或直接用 `.env.ollama.example` 把模型服务映射到本地 Ollama OpenAI-compatible `/v1` surface。

本轮实际复跑:

```text
cd examples/agent-runtime-a2a-llm-e2e
bash scripts/test-e2e.sh .env.ollama.example
```

结果:

- 脚本加载 env 时显示 provider/base/model 来自 `.env.ollama.example`:
  - provider: `openai`
  - apiBase: `http://localhost:11434/v1`
  - model: `gemma4:latest`
- Maven/JUnit 结果: **Tests run: 16, Failures: 0, Errors: 0, Skipped: 0**
- 结论: 本地 Ollama 路径可跑通；“每个人填自己的本地模型或云端 API key”的基本交付已经成立。

非阻断改进建议:

- `application.yaml` 和 README 中的默认 `api-key: ${SAA_SAMPLE_LLM_API_KEY:sk-x00550472}` 是占位值。JUnit real-LLM 分支实际检查的是环境变量 `SAA_SAMPLE_LLM_API_KEY`，所以不会因为该默认值误跑；但手动 `run-server` 时仍可能让用户误以为有默认可用 key。建议把默认值改成空字符串，或者在 README 中明确这是不可用占位符。

## 4. Verification record

本轮复审使用的事实与验证面:

```text
GitHub PR metadata
PR #140 head = 66f73bc84c7f33cb66be5c75319e348c06a819b6
state = open, mergeable = true, draft = false
```

```text
git status --short --branch
## redo/agent-runtime-pure-rebuild...origin/redo/agent-runtime-pure-rebuild
```

```text
git rev-parse HEAD
66f73bc84c7f33cb66be5c75319e348c06a819b6
```

```text
bash gate/check_architecture_sync.sh
GATE: PASS
ARCHITECTURE WORKSPACE: PASS
workspace baseline: 370 elements / 201 relationships
```

```text
python gate/lib/sync_baseline.py --check
baseline_metrics: all derivable fields match canonical counts
```

```text
./mvnw -Pquality verify
BUILD SUCCESS
```

```text
examples/agent-runtime-a2a-llm-e2e
bash scripts/test-e2e.sh .env.ollama.example
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
Select-String agent-runtime/target/checkstyle-result.xml '<error'
no matches
```

## 5. Recommended next commit scope

建议下一次修复不要改 runtime 代码，集中做一个小而完整的 authority sweep:

1. `docs/governance/rules/rule-R-M.md`
2. `architecture/docs/L0/ARCHITECTURE.md` §4 #7
3. `architecture/docs/L1/agent-service/ARCHITECTURE.md`
4. `architecture/docs/L1/agent-service/process.md`
5. `architecture/docs/L1/agent-service/logical.md`
6. `architecture/docs/L1/agent-service/development.md`
7. `architecture/docs/L1/agent-service/physical.md`
8. `architecture/docs/L1/agent-service/scenarios.md`
9. `architecture/docs/L1/agent-service/spi-appendix.md`
10. `architecture/docs/L1/agent-service/features/engine-dispatch-execution.md` and sibling feature pages that still cite `RuntimeMiddleware` / `HookPoint` as current runtime mechanism

合并标准:

- 当前事实层仍以 `EngineDispatcher` / `AgentRuntimeHandler` / `AgentRuntimeHandlerRegistry` 为主语。
- 旧 `EngineRegistry` / `ExecutorAdapter` / `EngineEnvelope` / `HookPoint` / `RuntimeMiddleware` 只出现在明确标记的 `RETIRED`, `design_only`, `historical`, 或 archived context 中。
- Rule R-M frontmatter 不再把 retired enforcer 当成 active enforcer。
- gate 与 `./mvnw -Pquality verify` 继续通过。

