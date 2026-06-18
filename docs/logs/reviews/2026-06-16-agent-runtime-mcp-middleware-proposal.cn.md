---
affects_level: L1
affects_view: development
proposal_status: review
authors: ["EuphoriaYan", "Codex"]
related_adrs: []
related_rules: [D-1, G-15]
affects_artefact:
  - agent-runtime
  - architecture/docs/L1/agent-runtime
  - examples
---

# Proposal: Agent Runtime MCP 中间件接入

> **Date:** 2026-06-16
> **Status:** Pending Review / 非 ADR / 非已批准实现
> **Affects:** `agent-runtime` L1 设计、OpenJiuwen adapter、后续 MCP examples。
> **Fact baseline:** 本 proposal 先读 `architecture/facts/generated/` 后读 prose。关键事实 ID：`code-symbol/com-huawei-ascend-runtime-engine-spi-agentruntimehandler`、`code-symbol/com-huawei-ascend-runtime-engine-spi-memoryprovider`、`code-symbol/com-huawei-ascend-runtime-engine-openjiuwen-openjiuwenagentruntimehandler`、`code-symbol/com-huawei-ascend-runtime-engine-openjiuwen-openjiuwenremotetoolinstaller`。

---

## 0. 摘要

本提案定义 `agent-runtime` 的 MCP 第一波接入方式。MCP 在本项目中定位为 **工具中间件**：它负责从外部 MCP Server 发现工具，并把这些工具注入到具体 Agent 框架中供模型调用。

核心决策：

- 第一阶段只写 proposal，不改 Java 代码。
- MCP 作为可选中间件接入，不污染 `AgentRuntimeHandler` 执行 SPI。
- 公共层只保留窄 Provider 语义，具体 Agent 框架通过本地 adapter / installer 使用自己的工具机制。
- OpenJiuwen adapter 优先复用 OpenJiuwen `agent-core-java:0.1.12` 已提供的 MCP client / tool 能力。
- 可参考其他优秀开源项目的 MCP client + Toolkit 注册模式

一句话定位：

```text
MCP Provider 发现和调用远端 MCP tools；
OpenJiuwen adapter 决定如何把这些 tools 注册进各自框架。
```

---

## 1. 背景与问题

`agent-runtime` 已经有 Memory / State 中间件实践：公共 SPI 保持很窄，OpenJiuwen 等框架通过本地 Rail、checkpointer 或 wiring 接入具体能力。这条路线避免了两个问题：

1. `AgentRuntimeHandler` 变成通用 before / after provider chain。
2. 某个 Agent 框架的内部类型泄漏到公共 `engine.spi`。

MCP 与 Memory 类似，也是可选能力；但 MCP 的语义不是长期记忆或 prompt 片段，而是 **工具发现与工具调用**。因此，MCP 不能简单复用 `MemoryProvider`，也不应该进入 A2A protocol bridge。它应落在 Agent framework adapter 的工具注册层。

当前仓库已经有远端 A2A agent-as-tool 机制：A2A Agent Card 会被转换成 `RemoteAgentToolSpec`，再由 `OpenJiuwenRemoteToolInstaller` 注入 OpenJiuwen Agent。MCP 与它相似，但两者调用语义不同：

| 能力 | 来源 | 调用目标 | 调用语义 |
|---|---|---|---|
| Remote A2A tool | 远端 Agent Card | 另一个 A2A Agent | 可能产生 task、stream、input-required |
| MCP tool | MCP Server tools/list | MCP Server tool | 一次工具调用，返回工具结果 |

因此，MCP 可以复用“工具描述 -> 框架 ToolCard -> 模型可见 tool”的安装思路，但不复用 A2A remote-agent interrupt rail。

---

## 2. 调研结论

### 2.1 AgentScope

AgentScope 的 MCP 思路是：

```text
MCP client 连接 MCP server
  -> list tools
  -> 把 MCP tools 注册进 Toolkit
  -> Agent 执行时通过 Toolkit 调用 tools
```

这个模型说明 MCP 在 AgentScope 中也是工具生态，而不是 prompt / skill 资产。对 `spring-ai-ascend` 的启发是：

- runtime 公共层只表达 MCP tool discovery / call。
- AgentScope adapter 未来应把 MCP tools 注册到 AgentScope Toolkit，而不是复用 OpenJiuwen 的 ToolCard。
- AgentScope 的具体 client、Toolkit、tool object 不应进入 `engine.spi`。

### 2.2 OpenJiuwen

本地依赖检查显示，OpenJiuwen `agent-core-java:0.1.12` 已经带有 MCP 相关能力：

- pom 引入 `io.modelcontextprotocol.sdk:mcp-core` 与 `mcp-json-jackson2`，MCP SDK 版本为 `1.1.1`。
- jar 内存在 `com.openjiuwen.core.foundation.tool.mcp.*`。
- 关键类包括 `McpClient`、`McpServerConfig`、`McpTool`、`McpToolCard`。
- client 形态包括 `StreamableHttpClient`、`SseClient`、`StdioClient`、`PlaywrightClient`。

因此，OpenJiuwen 第一版不应该手写 MCP protocol client。更合理的路径是：

```text
runtime 中立 MCP 配置 / Provider
  -> OpenJiuwen adapter 内部转换为 OpenJiuwen McpServerConfig / McpClient
  -> 复用 OpenJiuwen McpTool / McpToolCard
  -> 注册到 OpenJiuwen BaseAgent
```

如果 OpenJiuwen 原生 MCP 能力无法满足 runtime 的鉴权、观测或错误码要求，再在 `runtime.engine.openjiuwen` 包内增加薄 adapter；公共 SPI 仍不绑定 OpenJiuwen MCP 包名。

### 2.3 ModelScope MCP 广场

ModelScope MCP 广场适合作为后续 examples 的候选来源。第一版 proposal 不绑定具体广场协议，只要求 examples 能接入一个 HTTP Streamable / SSE MCP Server，并通过 README 写明配置步骤。

ModelScope 官方 MCP Server 的公开用法也是标准 MCP 形态：客户端可以通过 `mcpServers` 配置接入 stdio server，也可以在本地以 `--transport http` 或 `--transport sse` 启动后通过 `http://127.0.0.1:8000/mcp/` 接入。因此，ModelScope MCP 广场可以作为后续真实 Server 候选，但第一版 example 不应该强依赖公网广场可用性。

### 2.4 MCP 标准工具模型

MCP 官方工具协议的核心形态是：

```text
tools/list -> Tool[]
tools/call(name, arguments) -> ToolResult
notifications/tools/list_changed -> 工具列表变化通知
```

其中 Tool 的标准字段包括：

- `name`：工具唯一名，唯一性只在单个 MCP Server 内成立。
- `title`：可选展示名。
- `description`：工具描述。
- `inputSchema`：JSON Schema 参数定义。
- `outputSchema`：可选 JSON Schema 输出定义。
- `annotations`：可选工具行为说明，不能默认视为可信。
- `_meta`：协议扩展元数据。

ToolResult 的标准字段包括：

- `content`：多段非结构化内容，例如 text、image、audio、resource link、embedded resource。
- `structuredContent`：可选结构化 JSON 结果，应与 `outputSchema` 对齐。
- `isError`：工具执行错误标识。
- `_meta`：协议扩展元数据。

因此，本项目的 `McpToolSpec` / `McpToolResult` 需要尽量保留这些标准字段。runtime 可以额外增加 `serverId`、`errorCode`、`message` 等可观测字段，但不能把 MCP 标准字段折叠成单个 `Object content` 后丢失结构。

---

## 3. 设计目标

### 3.1 要做

- 支持 runtime 通过 MCP Server 获取可调用工具。
- 支持 OpenJiuwen Agent 在执行时看见 MCP tools。
- 支持模型触发 MCP tool call 后，runtime / adapter 调用 MCP Server 并把结果返回给模型。
- 保持 MCP 为可选组件；无 MCP 配置时 Agent 正常启动。
- 保持公共 SPI 窄而稳定，不把 OpenJiuwen / AgentScope / MCP SDK 具体类型泄漏到中立包。

### 3.2 不做

- 暂时不做 Skills Hub；Skill Registry 后续继续调研并单独设计。
- 不做 stdio MCP Server 进程托管。
- 不做 MCP resources / prompts 接入。
- 不做 MCP Server marketplace 管理后台。
- 不做多租户权限模型和企业级密钥托管；第一版只保留接口位置和错误可观测性。
- 不改 `AgentRuntimeHandler` 方法签名。
- 不把 MCP 接入放进 A2A protocol bridge。

---

## 4. 公共接口草案

> 本节只定义语义，不要求本轮写 Java 实现。命名可在代码实现前再做一次 API review。

### 4.1 `McpProvider`

```java
public interface McpProvider {
    List<McpToolSpec> listTools(AgentExecutionContext context);

    McpToolResult callTool(AgentExecutionContext context, String serverId, String toolName,
            Map<String, Object> arguments);
}
```

语义：

- `listTools(...)` 返回当前执行上下文可见的 MCP tools。
- `callTool(...)` 调用某个 MCP tool，并返回结构化结果。
- provider 可以内部缓存 server tools；缓存策略不进入公共接口。
- provider 必须按租户、用户、Agent 或环境隔离自己的鉴权上下文，但第一版不定义统一权限模型。

### 4.2 `McpToolSpec`

```java
public record McpToolSpec(
        String serverId,
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> annotations,
        Map<String, Object> meta,
        Map<String, Object> metadata) {
}
```

语义：

- `serverId` 标识 MCP Server，用于日志、观测、错误定位。
- `name` 对应 MCP Tool `name`。由于 MCP 只保证单 server 内唯一，聚合多个 server 时 adapter 必须处理命名冲突，例如用 `serverId + name` 生成框架内 tool id，同时保留原始 MCP name。
- `title`、`description`、`inputSchema`、`outputSchema`、`annotations`、`meta` 对齐 MCP 标准 Tool 字段。
- `metadata` 承载 runtime 自有字段，例如 server label、auth mode、tool version、discoveredAt。
- 对第一版实现，如果代码希望继续保持更窄的数据结构，至少必须在 `metadata` 中无损保留 `outputSchema`、`annotations`、`_meta`，避免后续 adapter 无法恢复 MCP 原始语义。

### 4.3 `McpToolResult`

```java
public record McpToolResult(
        List<Map<String, Object>> content,
        Object structuredContent,
        boolean isError,
        String errorCode,
        String message,
        Map<String, Object> meta,
        Map<String, Object> metadata) {
}
```

语义：

- `content` 对齐 MCP ToolResult `content`，每个 item 保留 `type` 及对应字段，例如 `text`、`data`、`mimeType`、`resource`。
- `structuredContent` 对齐 MCP ToolResult `structuredContent`，可以是 object、array、string、number、boolean 或 null。
- `isError` 对齐 MCP ToolResult `isError`。工具执行错误应尽量以 `isError=true` 返回给模型，使模型有机会自修复；协议级错误再映射为 runtime 错误。
- `errorCode` 和 `message` 是 runtime 可观测字段，不替代 MCP 标准结果字段。
- `meta` 对齐 MCP `_meta`。
- `metadata` 承载 runtime 自有字段，例如 serverId、latencyMs、traceId。
- 第一版建议错误码至少覆盖：
  - `MCP_SERVER_UNAVAILABLE`
  - `MCP_TOOL_NOT_FOUND`
  - `MCP_TOOL_TIMEOUT`
  - `MCP_BAD_RESPONSE`
  - `MCP_AUTH_FAILED`

---

## 5. OpenJiuwen 落地草案

### 5.1 Adapter 职责

OpenJiuwen MCP 接入应收敛在 `runtime.engine.openjiuwen` 包内。

建议新增 `OpenJiuwenMcpToolInstaller`，职责类似当前 `OpenJiuwenRemoteToolInstaller`：

```text
McpProvider.listTools(context)
  -> OpenJiuwen McpTool / ToolCard / AbilityManager
  -> BaseAgent 注册 MCP tools
  -> 模型发起 tool call
  -> OpenJiuwen 原生 McpTool 调用 MCP Server
```

如果必须经过 runtime 中立 `McpProvider.callTool(...)`，则使用 OpenJiuwen 本地薄 Tool wrapper，但 wrapper 仍留在 `runtime.engine.openjiuwen` 包内。

`OpenJiuwenRemoteToolInstaller` 不建议直接复用为 MCP installer。它目前面向 A2A remote agent-as-tool，安装的是占位 tool，并通过 `OpenJiuwenRemoteAgentInterruptRail` 截获后转成远端 A2A 调用；MCP tool 更接近真实 tool call，应该直接由 OpenJiuwen 原生 `McpTool` / `McpClient` 或薄 wrapper 调用 MCP Server。可以复用的是“ToolSpec -> ToolCard -> BaseAgent 注册”的安装模式，必要时抽取极小的 ToolCard helper，而不是复用 A2A interrupt rail。

### 5.2 与现有 hook 的关系

当前 `OpenJiuwenAgentRuntimeHandler` 已经有两个适合接入 MCP 的位置：

- `createOpenJiuwenAgent(context)`：业务构造 OpenJiuwen Agent。
- `installRuntimeTools(agent, context)`：runtime 安装远端工具。

MCP 第一版建议走 `installRuntimeTools(...)`，因为 MCP 是 runtime 提供的可选工具能力，不应该要求业务每次手工把 tools 添加到 Agent。

### 5.3 与 Memory Rail 的关系

MCP 不复用 `MemoryRuntimeRail`。两者职责不同：

| 中间件 | 接入点 | 主要动作 |
|---|---|---|
| Memory | Rail / native external memory | 检索记忆、注入上下文、执行后写回 |
| MCP | Tool installer / Tool wrapper | 发现工具、注册工具、调用工具 |

MCP 可以被 trace / trajectory 观察，但不应被当成 memory prompt 处理。

---

## 6. AgentScope 后续落地草案

AgentScope 第一版不写代码，但 proposal 固定边界：

- `McpProvider` 返回中立 `McpToolSpec`。
- AgentScope adapter 把这些 tools 注册进 AgentScope Toolkit。
- 不复用 OpenJiuwen `McpTool`、`ToolCard`、`AgentRail`。
- AgentScope 的 MCP client、Toolkit、function wrapper 全部留在 `runtime.engine.agentscope` 或 examples 中。

---

## 7. Examples 策略

第一版实现后需要新增一个 MCP example，要求：

- 无额外 scripts；外部环境配置都写入 README。
- README 提供 curl 级步骤。
- 支持配置一个 HTTP Streamable / SSE MCP Server。
- 推荐候选：
  - ModelScope MCP 广场中的可访问 Server。
  - 或一个最小本地真实 MCP Server，作为 CI / 无公网环境 fallback。

本地 fallback 不应是伪造 provider 或 mock 调用链，而应该是一个可通过 MCP `tools/list` 和 `tools/call` 访问的真实 MCP Server。第一版建议实现最简单的 date / time server，例如暴露 `get_current_date`、`get_current_time`，以保证：

- 工具 schema 简单，便于 reviewer 理解。
- 无外部 API key，便于测试团队复现。
- 可以用 MCP Inspector 或 curl 级流程验证工具发现和调用。

- 验证路径：

```text
启动 MCP Server
  -> 启动 OpenJiuwen Agent runtime
  -> Agent 启动时发现 MCP tools
  -> curl 调 A2A /a2a
  -> LLM 选择 MCP tool
  -> MCP Server 返回结果
  -> Agent 最终回答包含工具结果
```

---

## 8. DFX 与错误处理

第一版至少保留以下可观测点：

- tool discovery 数量、serverId、耗时。
- tool call 的 toolName、serverId、耗时、成功/失败。
- MCP Server 不可达时不阻塞 Agent 启动，工具调用时快速失败。
- MCP tool not found、timeout、bad response、auth failed 均返回明确错误码。
- 如果 MCP Server 声明 `tools.listChanged`，第一版可以先记录能力并在收到 `notifications/tools/list_changed` 时标记工具缓存 dirty；不建议在一次 Agent 执行中热替换工具列表，避免模型上下文中的 tools 与运行时实际 tools 不一致。
- 工具参数与结果日志要遵守已有敏感字段脱敏策略。

暂不承诺：

- MCP Server 高可用管理。
- 多租户密钥托管。
- server tools changed 的热更新和跨执行灰度。
- marketplace 同步与灰度发布。

---

## 9. 后续实现计划

Proposal 通过后，新开代码 PR，第一版只做：

1. 公共 MCP 窄 SPI。
2. OpenJiuwen MCP tool installer。
3. runtime 中立 HTTP Streamable / SSE MCP provider，以及 OpenJiuwen 原生 MCP provider 包装。
4. 一个端到端 example。
5. 单元测试和 curl 级手工验证说明。

实现前置约束：

- 从 clean `main` 新建分支。
- 当前本地非 main 分支和未跟踪 `agent-runtime/bin/` 不作为实现基线。
- 如果 OpenJiuwen 原生 MCP API 不满足需求，只在 `runtime.engine.openjiuwen` 内加 adapter，不扩大公共 SPI。

---

## 10. Test Plan

### 10.1 Proposal 检查

```bash
git diff --check -- docs/logs/reviews/2026-06-16-agent-runtime-mcp-middleware-proposal.cn.md
rg -n "MCP|OpenJiuwen|AgentScope|Provider|Tool|Streamable|SSE|stdio|Skills Hub|Nacos" docs/logs/reviews/2026-06-16-agent-runtime-mcp-middleware-proposal.cn.md
```

### 10.2 后续代码验收

- 无 MCP 配置时 Agent 正常启动。
- 配置一个 HTTP MCP Server 后，OpenJiuwen Agent 能看到 MCP tool。
- LLM 触发 tool call 后，runtime 能调用 MCP Server 并把结果返回。
- MCP Server 不可达、tool 不存在、返回非法 JSON 时有明确错误。
- 不影响现有 Memory / State / OpenJiuwen remote A2A tool 测试。

---

## 11. Open Questions

- 第一版是否直接使用 OpenJiuwen 原生 MCP client 作为唯一实现，还是同时保留一个 runtime 中立 HTTP MCP provider？
  - 建议：保留 runtime 中立 HTTP MCP provider。OpenJiuwen adapter 可以优先用 OpenJiuwen 原生 MCP，但 runtime 中立 provider 能服务 AgentScope / 未来框架，也能给 examples 和测试团队一个不绑定 OpenJiuwen 内部类的基线。
- examples 是否优先使用 ModelScope MCP 广场，还是先用最小本地 MCP Server 保证可复现？
  - 建议：examples 先用最小本地真实 MCP Server，例如 date / time server；README 再补充 ModelScope MCP 广场作为可选替换目标。
- MCP tools changed 是否需要第一版支持刷新，还是只在启动和定时刷新时发现？
  - 建议：第一版支持启动发现和手动 / TTL 刷新；如果底层 client 收到 `notifications/tools/list_changed`，只标记缓存 dirty，下一次执行或下一次安装前刷新。AgentScope 的实现更偏 MCP client 注册到 Toolkit，工具注册/移除是显式操作；我们也不应在单次执行中隐式热替换工具集。
- 鉴权信息第一版放在 Spring 配置中，还是只允许业务自定义 `McpProvider` Bean？
  - 这里的“鉴权信息”指连接或调用 MCP Server 所需的凭据和身份材料，例如 Bearer token、API key、自定义 header、OAuth token、mTLS 证书、租户级 server URL、scopes。建议第一版支持 Spring 配置 / 环境变量中的静态 server 级 header，用于 examples；生产态允许业务自定义 `McpProvider` Bean 处理租户级动态鉴权。任何鉴权信息都不能进入 AgentCard、Tool description 或普通日志。
