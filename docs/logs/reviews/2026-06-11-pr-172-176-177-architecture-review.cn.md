# PR 172 / 176 / 177 复审报告：AgentScope 修复、LangGraph adapter、agent-sdk/agent-bus 清理

日期：2026-06-11

评审对象：

- PR #172: `Post-#158 review fixes: AgentScope SSE hardening, A2A task finalization, agent-sdk migration`
- PR #176: `feat(runtime,agent-sdk): close review gaps — A2A hardening, real tool execution, LangGraph adapter`
- PR #177: `fix: close remaining review findings — runtime lows, agent-bus contract honesty, agent-sdk cleanup`

评审基准：以 PR #177 merge commit `5f521b06b18f3eb26bd5daf9dfcea0773ffad9c9` 为最终快照，向前核对 #172、#176 的增量。按照 `AGENTS.md` / Rule G-15 的消费约束，本次先读取 `architecture/facts/generated/*.json`，再对照 L0/L1/contract prose 与代码实现。

## 总体结论

代码层面，#172 对 AgentScope SSE / A2A 终态处理的修复质量较高，#177 对 `agent-bus` SPI 的 record 校验也明显增强；本地验证显示主 reactor、架构同步 gate、`agent-sdk` standalone test、example test override 都能通过。

但从架构师视角看，#176/#177 仍不宜被视为“架构闭环完成”。主要原因有三类：

1. #176 新增 `agent-runtime.engine.langgraph` shipped-looking adapter 包，facts 和 ArchUnit 已承认它存在，但 L0/L1/contract authority 尚未承认 LangGraph 是 shipped adapter。这违反当前“新增文件夹/子包必须有架构授权且层次保持简洁”的治理要求。
2. #176 新增 `agent-sdk` 的真实 HTTP tool executor，直接对任意配置 URL 发起网络请求，且默认跟随重定向；目前没有平台 policy / sandbox / allowlist / audit / egress guard 接入点。它复用了 JDK `HttpClient`，但没有复用平台治理边界。
3. `agent-sdk` 作为“customer SDK”被大量修改，但不在 root reactor、不在 generated facts、不在 module metadata，也不在 shipped contract catalog 中形成清晰身份。当前主验证无法证明它作为 shipped surface 的架构一致性。

建议：在关闭本轮之前，至少补齐 Findings P1-1、P1-2、P1-3；P2 项可在同一个修复 PR 中一并收敛。

## Findings

### P1-1：#176 新增 `engine.langgraph` 包已进入代码事实层，但没有 L0/L1/contract 授权闭环

**影响**

PR #176 新增了 `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/langgraph/`，包含 `LangGraphRuntimeClient`、`LangGraphRuntimeClientHandler`、`LangGraphRuntimeClientProperties`、`LangGraphStreamAdapter`。这些类不是测试辅助，也不是隐藏实验代码：`LangGraphRuntimeClientHandler` 实现了 `AgentRuntimeHandler`，`LangGraphStreamAdapter` 实现了 `StreamAdapter`，并且 `RuntimePackageBoundaryTest` 已把 `com.huawei.ascend.runtime.engine.langgraph..` 加入包边界白名单。

机器事实层也已经收录：

- `architecture/facts/generated/code-symbols.json:1146` fact `code-symbol/com-huawei-ascend-runtime-engine-langgraph-langgraphruntimeclient`
- `architecture/facts/generated/code-symbols.json:1166` fact `code-symbol/com-huawei-ascend-runtime-engine-langgraph-langgraphruntimeclienthandler`
- `architecture/facts/generated/code-symbols.json:1186` fact `code-symbol/com-huawei-ascend-runtime-engine-langgraph-langgraphruntimeclientproperties`
- `architecture/facts/generated/code-symbols.json:1206` fact `code-symbol/com-huawei-ascend-runtime-engine-langgraph-langgraphstreamadapter`
- `architecture/facts/generated/tests.json:266` fact `test/com-huawei-ascend-runtime-engine-langgraph-langgraphruntimeclienthandlertest`
- `architecture/facts/generated/tests.json:282` fact `test/com-huawei-ascend-runtime-engine-langgraph-langgraphstreamadaptertest`

但是架构 prose 仍只承认 openJiuwen + AgentScope：

- `architecture/docs/L0/ARCHITECTURE.md:77` W0 shipped subset 仍描述 openJiuwen adapter，没有 LangGraph。
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:70` 到 `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:87` 只列出 openJiuwen 与 AgentScope adapter。
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:245` 的对应代码清单只列 `runtime.engine.openjiuwen.*`、`runtime.engine.agentscope.*`。
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:326` 明确说当前仅 openJiuwen + AgentScope，其他框架适配器按需添加。
- `docs/contracts/contract-catalog.md:46` 与 `docs/contracts/contract-catalog.md:176` 的 shipped runtime surface 也只列 openJiuwen + AgentScope。

这不是单纯“文档漏写”。新增子包本身已经改变 `agent-runtime` 的 adapter surface，并且 PR #176 还同步改了 ArchUnit 白名单。这等价于用代码先扩展架构边界，再等待文档追认；与当前仓库强调的架构授权模型相反。

**建议**

- 若 LangGraph adapter 已获架构批准：补 ADR 或现有 ADR amendment，更新 L0 shipped subset、L1 agent-runtime adapter inventory、contract catalog、feature/function-point inventory 与 generated facts 的期望基线；同时说明为什么该新增子包符合“子文件夹尽量简洁、避免过深嵌套”的约束。
- 若只是实验能力：不要放在 shipped package 白名单里；改为 example/test fixture 或明确 experimental module，并从 shipped contract 文案中隔离。
- 在修复前，不建议把 #176 的 LangGraph 部分作为已完成的 shipped adapter 交付。

### P1-2：#176 的 `HttpToolExecutor` 绕过平台治理边界，真实网络 tool execution 缺少 policy/sandbox/audit guard

**影响**

#176 新增 `agent-sdk/src/main/java/com/huawei/ascend/agentsdk/adapter/HttpToolExecutor.java`。实现上它会：

- 默认构造 `HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()`：见 `HttpToolExecutor.java:30` 到 `HttpToolExecutor.java:31`。
- 在 `execute(...)` 中直接构建并发送 HTTP 请求：见 `HttpToolExecutor.java:38` 到 `HttpToolExecutor.java:42`。
- 对 `GET` / `HEAD` / `DELETE` 直接把输入拼到 query；其他方法发送 JSON body：见 `HttpToolExecutor.java:58` 到 `HttpToolExecutor.java:76`。
- 直接使用 `HttpExecutionHandle.url()`、headers、method、timeout；resolver 只解析配置，不做 scheme、host、私网地址、租户、姿态、allowlist、redirect 目标复核等治理校验：见 `HttpToolResolver.java:22` 到 `HttpToolResolver.java:26`，`HttpExecutionHandle.java:7` 到 `HttpExecutionHandle.java:16`。
- `OpenJiuwenToolMapper` 遇到 `HttpExecutionHandle` 时直接调用 `httpExecutor.execute(...)`：见 `OpenJiuwenToolMapper.java:60` 到 `OpenJiuwenToolMapper.java:61`。

这与当前 L0/L1 对 governed tool calls 的要求存在冲突：

- `architecture/docs/L0/constraints.md:57`：tool/skill calls 需要通过 authorization、capacity、idempotency、audit、observability boundaries。
- `architecture/docs/L1/agent-service/features/task-centric-control.md:22`：RuntimeMiddleware governance 负责 policy、quota、sandbox routing、observability、failure handling。
- `architecture/docs/L1/agent-service/features/translation-tool-intercept.md:26`：tool/memory/retrieval invocation profile 需要把 policy-sensitive decisions 交给 RuntimeMiddleware。
- `architecture/docs/L0/ARCHITECTURE.md:570` 到 `architecture/docs/L0/ARCHITECTURE.md:581`：Skill SPI 要声明 `SkillResourceMatrix`、posture-mandatory sandbox 等治理信息。

我认可这里复用了标准 JDK `HttpClient`，没有手写底层 HTTP 协议；但架构问题不在 HTTP 客户端库，而在真实副作用/网络出口绕过了平台治理层。特别是默认 follow redirect 会放大 SSRF / egress bypass 风险：原始 URL 通过校验也不代表重定向目标仍然可信。

**建议**

- 如果 `agent-sdk` 的 HTTP tool execution 是本地 customer-owned process 的 convenience feature，请在 SDK 文档和 API 名称中明确“local/dev/customer-owned only”，并默认关闭真实网络执行。
- 如果它要成为平台支持的真实 tool execution，需要至少补齐：scheme allowlist、host allowlist、私网/链路本地地址默认拒绝、redirect 禁用或逐跳重校验、method/header allowlist、request/response size limit、timeout 上限、审计事件、trace tags、tenant/posture/idempotency 上下文、失败可观测性。
- 更符合现有架构的做法是：SDK 只把 tool ref 解析成中立 invocation descriptor，真实执行交给平台 RuntimeMiddleware / Tool SPI / governed client，而不是在 adapter mapper 中直接出网。

### P1-3：`agent-sdk` 被当作客户 SDK 修改，但仍游离于 root reactor、generated facts、module metadata 和 contract catalog 之外

**影响**

`agent-sdk` 当前看起来是客户侧 SDK：

- `agent-sdk/pom.xml:13` 到 `agent-sdk/pom.xml:14` 声明 artifact/name 为 `agent-sdk`。
- PR #176/#177 在其中新增、删除、重构了大量 public-looking API。

但仓库治理链路没有把它作为正式模块承认：

- root `pom.xml:34` 到 `pom.xml:39` 的 reactor 只包含 `spring-ai-ascend-dependencies`、`agent-bus`、`agent-runtime`、`agent-service`，没有 `agent-sdk`。
- `architecture/facts/generated/module-build.json:10` 到 `architecture/facts/generated/module-build.json:101` 只包含上述四个 build module，没有 `agent-sdk`。
- `agent-sdk/module-metadata.yaml` 不存在。
- `architecture/facts/generated/code-symbols.json` 中没有 `agentsdk` / `HttpToolExecutor` / `ModelResolver` 等 fact。

这会造成两个实际后果：

1. `./mvnw clean verify` 的 PASS 不能覆盖 `agent-sdk`，除非 reviewer 额外手动跑 `./mvnw -f agent-sdk/pom.xml test`。
2. Rule G-15 要求“对代码、contract、test 的 factual claim 先看 facts”，但 facts extractor 当前看不到 `agent-sdk`，所以任何关于 SDK API 的“已收敛/已验证/已迁移”声明都缺少事实层支撑。

**建议**

- 由架构师明确 `agent-sdk` 身份：正式 shipped customer SDK、experimental standalone module、还是 examples/support 工具。
- 若是 shipped customer SDK：加入 root reactor 或建立等价的独立 CI/gate；新增 `module-metadata.yaml`、DFX 文档、contract catalog 条目、BoM 管理策略、facts extractor 覆盖与 deprecation policy。
- 若仍是 experimental：README、设计方案、PR body、release note 都应明确“不属于 shipped surface”，并避免用它证明平台能力已闭环。

### P2-1：E2E example 的文档和脚本声称会跑测试，但默认命令实际跳过测试

**影响**

从 outsider 视角看，`examples/agent-runtime-a2a-llm-e2e` 的配置方向是对的：`.env.ollama.example` 已经把本地 Ollama 映射为 OpenAI-compatible `/v1` 服务：

- `examples/agent-runtime-a2a-llm-e2e/.env.ollama.example:1` 到 `:5` 说明本地 Ollama 通过 OpenAI-compatible surface 接入。
- `examples/agent-runtime-a2a-llm-e2e/.env.ollama.example:8` 到 `:9` 默认 `http://localhost:11434/v1`。
- `examples/agent-runtime-a2a-llm-e2e/README.md:143` 到 `:162` 说明本地 Ollama 与云端 OpenAI-compatible API 使用同一套 env 模板和命令。
- `examples/agent-runtime-a2a-llm-e2e/README.md:197` 到 `:221` 使用 Spring placeholder 暴露 provider/api-base/model 等配置。

问题在于可运行脚本与 Maven 默认值相互抵消：

- `examples/agent-runtime-a2a-llm-e2e/pom.xml:28` 到 `:30` 设置 `<skipTests>true</skipTests>`，并要求手动 `-DskipTests=false` 才跑测试。
- `examples/agent-runtime-a2a-llm-e2e/scripts/test-e2e.sh:22` 到 `:23` 安装 runtime 后执行 `./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml test`，没有传 `-DskipTests=false`。
- `examples/agent-runtime-a2a-llm-e2e/scripts/test-e2e.ps1:23` 到 `:24` 同样没有传 `-DskipTests=false`。
- README 在 `examples/agent-runtime-a2a-llm-e2e/README.md:147` 和 `:332` 声称 `bash scripts/test-e2e.sh .env` 会运行 E2E suite；`README.md:348` 的直接 Maven 命令也没有 `-DskipTests=false`。

本地验证结果：直接运行 README/脚本等价命令 `./mvnw.cmd -f examples\agent-runtime-a2a-llm-e2e\pom.xml clean test` 会 BUILD SUCCESS，但 Surefire 输出 `Tests are skipped.`。加上 `-DskipTests=false` 后，测试才真正执行并通过：`Tests run: 36, Failures: 0, Errors: 0, Skipped: 3`。

这不是 #172/#176/#177 新引入的文件变更，但它直接影响这些 PR 关于 example/e2e readiness 的可信度。开发团队如果按 README 复现，会得到一个“绿色但没跑测试”的结果。

**建议**

- 修改 `scripts/test-e2e.sh` 与 `scripts/test-e2e.ps1`，对 example Maven 命令显式加 `-DskipTests=false`。
- README 的直接运行命令也同步加 `-DskipTests=false`。
- 脚本最好在输出中检查 Surefire 是否出现 `Tests are skipped.`，出现则 fail fast。
- 对需要真实模型/API key 的分支保留条件 skip 是合理的，但应该让“可离线跑的 36 个测试”默认真实执行。

### P2-2：#177 删除 public-looking SDK 类，但设计文档仍引用旧 API，兼容策略不清晰

**影响**

PR #177 删除了以下 `agent-sdk` 类：

- `agent-sdk/src/main/java/com/huawei/ascend/agentsdk/spec/model/ModelResolver.java`
- `agent-sdk/src/main/java/com/huawei/ascend/agentsdk/spec/model/ModelResolutionException.java`
- `agent-sdk/src/main/java/com/huawei/ascend/agentsdk/spec/prompt/PromptLoader.java`
- `agent-sdk/src/main/java/com/huawei/ascend/agentsdk/support/cache/ResourceLocalCache.java`
- `agent-sdk/src/main/java/com/huawei/ascend/agentsdk/support/cache/LocalCacheException.java`

但 `agent-sdk/agent-sdk设计方案.md:377` 到 `:381` 仍列出 `ModelResolver`、`ModelResolutionException`、`PromptLoader`，`agent-sdk/agent-sdk设计方案.md:419` 到 `:420` 仍列出 `ResourceLocalCache`、`LocalCacheException`。

如果 `agent-sdk` 是 customer SDK，这属于 public API removal，需要兼容/迁移/废弃说明。`docs/contracts/contract-catalog.md:163` 对稳定 surface 的策略是 current MINOR deprecate、next MAJOR remove。虽然 `agent-bus` / `agent-runtime` 的 metadata 是 experimental，`agent-sdk` 目前又不在正式 metadata 中，但这正是问题：开发团队既把它当客户 SDK 改动，又没有给出它的成熟度与兼容规则。

**建议**

- 先按 P1-3 决定 `agent-sdk` 身份。
- 若正式 shipped：补 migration note、deprecation rationale、替代 API、release note，并避免无废弃周期删除。
- 若 experimental：删除或重写过期设计方案中的旧树形清单，避免新同学按 stale design doc 集成已不存在的类。

### P3-1：A2A executor 仍把入站 message 压平为单条 `ROLE_USER` 文本，生产路径没有保留多 part/metadata/role

**影响**

#172 在 example 中增加了 `AgentScopeWireMessages`，它会通过 metadata 保存 `wireRole`，再恢复 system/tool/assistant/user role：

- `examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/AgentScopeWireMessages.java:14` 到 `:20`
- `examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/AgentScopeWireMessages.java:25` 到 `:31`
- `examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/AgentScopeWireMessages.java:44` 到 `:50`

但生产路径 `A2aAgentExecutor` 在构造 `AgentExecutionContext` 时仍先 `extractText(ctx)`，再构造一条新的 A2A `Message`：

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a/A2aAgentExecutor.java:71`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a/A2aAgentExecutor.java:198`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a/A2aAgentExecutor.java:215`

这意味着 example 的 role-preservation helper 只能保护 example controller 自己构造的消息，不能保证通用 A2A executor 对任意入站 message 的多 part、metadata、role 语义保真。如果未来生产路径需要 system/tool role 或非文本 part，这里会再次出现语义丢失。

**建议**

- 如果当前 W0 只承诺单轮 user text，可以在 L1/contract 明确边界。
- 如果要支持更完整 A2A message semantics，`A2aAgentExecutor` 应把原始 `Message` 列表或保真投影传给 `AgentExecutionContext`，由具体 adapter 再做最小转换。

### Advisory：架构 gate 通过，但 generated-zone 存在 drift advisory

本地运行 `bash gate/check_architecture_sync.sh` 结果为 `GATE: PASS`，但 gate 输出了 generated-zone drift advisory：

- `architecture/generated/modules.dsl`
- `architecture/generated/rules.dsl`

因为 gate 将该项 demote 为 advisory，所以不是本轮 blocking defect；但在这组 PR 已经新增 facts、adapter、SPI 校验的背景下，建议在后续修复 PR 中刷新并提交生成物，避免下一轮 review 继续被 drift 噪音干扰。

## 正向观察

- #172 的 AgentScope SSE hardening 明显比上一轮扎实：facts 中能看到 AgentScope client/stream adapter 测试覆盖，代码也处理了连接失败、非 2xx、mid-stream failure、`[DONE]` / null sentinel 等边界。
- #177 对 `agent-bus` record 的构造期校验是好方向。例如 `AgentEvent` 对 runId/errorClass 做非空/非 blank 校验，`ExecutorDefinition` 校验 nodes/edges/startNode 一致性，`S2cCallbackResponse` 校验 OK/ERROR/TIMEOUT 的 trace/error 字段组合。这些改动让 SPI 更难被错误构造污染下游。
- example 的配置抽象方向是对的：`.env.ollama.example`、`.env.openai-compatible.example`、Spring placeholder 组合，已经满足“让大家填充本地模型或云端 API Key”的基本形态。需要修的是脚本实际执行测试的问题，而不是配置方向。

## Verification

以下命令在 PR #177 merge snapshot `5f521b06b18f3eb26bd5daf9dfcea0773ffad9c9` 的独立 worktree 中执行：

| 命令 | 结果 |
|---|---|
| `.\mvnw.cmd clean verify` | PASS。root reactor 只覆盖 `spring-ai-ascend-dependencies`、`agent-bus`、`agent-runtime`、`agent-service`；`agent-bus` 17 tests，`agent-runtime` 95 tests。 |
| `bash gate/check_architecture_sync.sh` | PASS，但有 generated-zone drift advisory：`architecture/generated/modules.dsl`、`architecture/generated/rules.dsl`。 |
| `.\mvnw.cmd -pl agent-runtime -am -DskipTests install` | PASS，用于给 standalone `agent-sdk` 测试提供本地 snapshot dependency。 |
| `.\mvnw.cmd -f agent-sdk\pom.xml clean test` | PASS。22 tests，0 failures，0 errors，0 skipped。注意：这是额外手动跑的，不属于 root reactor verify。 |
| `.\mvnw.cmd -f examples\agent-runtime-a2a-llm-e2e\pom.xml clean test` | BUILD SUCCESS，但测试被跳过。该结果证明 README/脚本等价命令不能证明 E2E 可运行。 |
| `.\mvnw.cmd -f examples\agent-runtime-a2a-llm-e2e\pom.xml -DskipTests=false test` | PASS。36 tests，0 failures，0 errors，3 skipped；真实模型/API key 分支按条件跳过。 |

## 给架构师和开发团队的开放问题

1. LangGraph adapter 是否已经被批准进入 `agent-runtime` shipped adapter surface？如果是，请补齐 ADR/L0/L1/contract/facts/gate 闭环；如果不是，应从 shipped package 边界撤出。
2. `agent-sdk` 的正式身份是什么？如果它是客户 SDK，需要进入 reactor/gate/facts/contract/deprecation 管理；如果只是实验工具，文档和 PR 描述需要显式降级其承诺。
3. 真实 HTTP tool execution 应该发生在 customer-owned SDK 进程中，还是必须通过平台 RuntimeMiddleware / Tool SPI 治理边界？这会决定 #176 的 `HttpToolExecutor` 是保留、重命名、还是下沉为受控 client。
4. example E2E 的目标是“默认离线跑测试”还是“默认只编译，显式打开测试”？当前 README 文案选择了前者，但 POM/scripts 行为是后者。

## 建议修复清单

必须修复：

- 补齐或撤回 LangGraph adapter 的架构授权闭环。
- 为 `HttpToolExecutor` 增加治理边界，或明确降级为 local/dev/customer-owned experimental feature 并默认关闭。
- 明确 `agent-sdk` 模块身份，并把它纳入相应的 reactor/facts/metadata/contract/CI 管理。
- 修正 example E2E scripts/README，使 advertised command 真实执行测试。

建议修复：

- 更新 `agent-sdk/agent-sdk设计方案.md` 中已删除 API 的旧引用，并补迁移说明。
- 在 `A2aAgentExecutor` 或 L1 contract 中明确 message semantic preservation 的当前边界。
- 刷新 generated-zone drift，减少下一轮架构 gate 噪音。
