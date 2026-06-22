---
level: L1
view: features
module: agent-runtime
status: release
updated: 2026-06-22
authority: "agent-runtime-iteration-2-features.cn.md + architecture/facts/generated + current source"
covers: [agent-runtime能力补齐, Skill Hub接入, DeepAgent, MCP协议接入, agent-sdk声明式Agent生成]
---

# spring-ai-ascend — 迭代二发布特性清单（v0.2.0）

> 本文档只刷新 `agent-runtime-iteration-2-features.cn.md` 中规划的特性项；不额外扩展其它 release 能力。
> ✅ = 已实现，⬜ = 未实现或尚未闭合。完成判断以当前源码、测试、examples 和 `architecture/facts/generated/*.json` 为准，不以规划文档描述本身作为完成依据。

---

## 特性 1：agent-runtime 能力补齐与生态接入

在 v0.1.0 已实现能力基础上，接入 Skill Hub 和 MCP 生态，支持 DeepAgent 执行模式，补齐 OpenJiuwen Workflow 适配。

### 1.1 Skill Hub 接入

- ✅ Skill Hub 协议适配：已提供 runtime-neutral `SkillHubProvider` SPI，支持 `listSkills(ctx)`、`loadSkill(ctx, skillId)`、`loadSkillPackage(ctx, skillId)`；本地目录和 remote JSON 两个 example 已落地。
- ✅ Skill 注册：`OpenJiuwenSkillHubInstaller` 已支持把 SkillHub 返回的 OpenJiuwen skill path 注册到 ReAct agent；DeepAgent 路径注册到内部 ReActAgent。

### 1.2 DeepAgent 支持

- ✅ DeepAgent 适配器：已新增 `OpenJiuwenDeepAgentRuntimeHandler`，通过 OpenJiuwen harness `DeepAgent.stream(input, null, streamModes)` 进程内调用 DeepAgent。
- ✅ DeepAgent 执行模型适配：已将 DeepAgent 的多步骤推理和工具调用映射到 runtime `AgentExecutionResult` stream，并支持 trajectory、runtime remote tool、runtime MCP tool、runtime SkillHub installer 等运行时接入。

### 1.3 OpenJiuwen Workflow 适配

- ✅ 支持多步骤 Workflow Agent 的托管和执行：已新增 `OpenJiuwenWorkflowAgentRuntimeHandler`，子类实现 `createOpenJiuwenWorkflow(ctx): Workflow` 即可托管 OpenJiuwen Workflow。
- ✅ Workflow 执行中断/恢复：已将 interaction chunk / `GraphInterrupt` 映射为 `INPUT_REQUIRED`，并通过 `pendingResumes` 在下一轮输入后构造 `InteractiveInput` 继续执行。

### 1.4 MCP 协议接入

- ✅ MCP 工具接入：已提供 `McpProvider` SPI、`HttpMcpProvider` JSON-RPC client、`McpAutoConfiguration`，并通过 `OpenJiuwenMcpToolInstaller` 将 MCP tools 注册到 ReAct 和 DeepAgent；`examples/agent-runtime-middleware-mcp-localtime` 和 `examples/agent-runtime-middleware-mcp-remote-json` 已落地。

---

## 特性 2：agent-sdk — YAML 配置驱动 Agent 生成

开发者通过 YAML 配置文件声明 Agent 的模型连接、系统提示词、工具、技能和 MCP 服务器，SDK 自动构建可运行的 Agent 实例，并支持 DeepAgent 深度推理执行模式。

### 2.1 核心配置能力

#### 2.1.1 模型配置

- ✅ 接入模型服务：`AgentYamlLoader` / `AgentYamlParser` 已支持 YAML schema，`ModelSpec` / `ModelRequestSpec` 已支持 provider、model name、baseUrl、apiKey、headers 和 request 参数；`AgentYamlEnvironmentResolver` 已支持 `${ENV}` 凭证注入，缺失环境变量会失败。

#### 2.1.2 提示词配置

- ✅ 设置系统提示词：已支持 `prompt.system`，并支持与 `AGENT.md` 合并。

#### 2.1.3 工具配置

- ✅ 接入外部 API 工具：`HttpToolResolver` + `HttpToolExecutor` 已支持 HTTP GET/POST、timeout、redirect、响应大小限制与错误暴露策略。
- ✅ 接入本地代码工具：`JavaFileToolResolver` 已支持按 classpath class/method 暴露本地 Java method。

#### 2.1.4 技能配置

- ✅ 加载技能文件：`SkillSourceLoader` 已支持扫描 filesystem skill source，并拒绝重复 skill name。

### 2.2 MCP (Model Context Protocol) 接入

- ⬜ 接入 MCP Server：YAML `mcps` 已解析为 `McpSpec`，`OpenJiuwenMcpMapper` 已将 `McpSpec` 转换为 OpenJiuwen `McpServerConfig`，DeepAgent builder 已写入 `DeepAgentConfig.mcps(...)`。

### 2.3 DeepAgent 支持

- ✅ 启用深度推理模式：`OpenJiuwenDeepAgentBuilder` 已支持从 YAML spec 构造 OpenJiuwen `DeepAgent`，并支持 maxIterations、workspacePath、language、skillMode、taskLoop、taskPlanning、completionTimeout 等 DeepAgent options。

---

## 未完成 / 未闭合项

| 规划项关联 | 未闭合项 | 说明 |
|---|---:|---|
| 1.1 Skill Hub 接入 | SkillHub 多源聚合 | 当前单 provider，未做多源合并。 |
| 1.2 DeepAgent 支持 | `TaskCompletionRail` runtime 接入 | 完成评估器仍由 `DeepAgentConfig.rails` 独占，`openJiuwenDeepAgentRails` 不提升。 |
| 1.2 DeepAgent 支持 | task-level memory helper | 需用户自写 `TaskIterationRail`，handler 未提供现成 helper。 |

---

## 附录：已支持特性关联资产

| 特性 | L2 设计文档 | 开发指导 | Example |
|---|---|---|---|
| Skill Hub 协议适配 / Skill 注册 | `architecture/docs/L2/agent-runtime/skillhub-runtime-middleware-design.md` | `agent-runtime/docs/guides/skillhub.md` | `examples/agent-runtime-middleware-skillhub-local/`, `examples/agent-runtime-middleware-skillhub-remote-json/` |
| DeepAgent 适配器 / 执行模型适配 | `architecture/docs/L2/agent-runtime/openjiuwen-deepagent-runtime-adapter-design.md` | `agent-runtime/docs/guides/openjiuwen-deepagent-adapter.md` | `examples/agent-runtime-a2a-openjiuwen-deepagent-e2e/` |
| OpenJiuwen Workflow 托管 / 中断恢复 | `architecture/docs/L2/agent-runtime/openjiuwen-workflow-runtime-adapter-design.md` | `agent-runtime/docs/guides/openjiuwen-workflow-adapter.md` | `examples/agent-runtime-a2a-openjiuwen-workflow/` |
| MCP 工具接入 / MCP Server 配置 | `architecture/docs/L2/agent-runtime/mcp-runtime-tool-middleware-design.md` | `agent-runtime/docs/guides/mcp-tools.md` | `examples/agent-runtime-middleware-mcp-localtime/`, `examples/agent-runtime-middleware-mcp-remote-json/` |
| agent-sdk YAML 模型 / 提示词 / 工具 / 技能配置 | `architecture/docs/L2/agent-runtime/agent-sdk-openjiuwen-yaml-assembly-design.md` | `agent-runtime/docs/guides/agent-sdk-openjiuwen-yaml.md` | `examples/agent-sdk-example/` |
| agent-sdk YAML `mcps[]` 到 DeepAgent 映射 | `architecture/docs/L2/agent-runtime/agent-sdk-openjiuwen-yaml-assembly-design.md` | `agent-runtime/docs/guides/agent-sdk-openjiuwen-yaml.md` | —（源码和单测覆盖；`examples/agent-sdk-example` 尚无 `mcps:` YAML） |
| agent-sdk YAML DeepAgent 生成 | `architecture/docs/L2/agent-runtime/agent-sdk-openjiuwen-yaml-assembly-design.md` | `agent-runtime/docs/guides/agent-sdk-openjiuwen-yaml.md` | `examples/agent-sdk-example/openjiuwen/deepagent.yaml` |
