# 适配器总览

agent-runtime 支持三种适配器接入不同类型的 Agent 实现。所有适配器对外暴露统一的
调用语义——提交、获取结果流、取消——上层调用方以相同的方式使用任意 Agent。

## 适配器类型

| 适配器 | 包 | 接入方式 | 适用场景 |
|---|---|---|---|
| **OpenJiuwen** | `engine.openjiuwen` | 进程内直接调用 | 低延迟、同进程部署 |
| **AgentScope** | `engine.agentscope` | 进程内直接调用 | 基于 AgentScope SDK 或 Harness 构建的 Agent |
| **Versatile** | `engine.versatile` | REST 代理调用远端服务 | 独立部署、跨进程、异构语言实现的 Agent |

## 统一调用契约

每个适配器都实现了 `AgentRuntimeHandler`（或继承框架基类）。Runtime 只看到
这个接口——适配器内部细节对上层透明：

```
A2A 客户端
    │
    ▼
┌─────────────────────────────┐
│ A2aJsonRpcController        │  JSON-RPC 解析, SSE 传输
├─────────────────────────────┤
│ A2aAgentExecutor            │  任务生命周期, 调度
├─────────────────────────────┤
│ AgentRuntimeHandler (SPI)   │  统一执行契约
├──────────┬──────────┬───────┤
│ OpenJiuwen│AgentScope│Versatile│  适配器层（框架相关）
└──────────┴──────────┴───────┘
```

## 每个适配器必须实现的 SPI 方法

| 方法 | 用途 |
|---|---|
| `agentId()` | 稳定唯一标识，用作 A2A 路由键 |
| `isHealthy()` | 健康检查闸门，执行前及健康端点调用 |
| `doExecute(context, trajectory)` | 核心执行；返回 `Stream<?>` 原始结果 |
| `resultAdapter()` | 将原始结果映射为框架中立的 `AgentExecutionResult` 流 |
| `start()` / `stop()` | 生命周期回调 |

## 选型指南

| 场景 | 适配器 |
|---|---|
| 已有 openJiuwen ReActAgent 或 DeepAgent | OpenJiuwen |
| 已有 AgentScope SDK 或 Harness Agent | AgentScope |
| Agent 用 Python / Node.js / Go 编写 | Versatile（REST 代理） |
| Agent 运行在独立进程或容器中 | Versatile（REST 代理） |
| 需要最低延迟 | OpenJiuwen 或 AgentScope |
| 需要将已有 HTTP API 包装为 Agent | Versatile（REST 代理） |

## 新增适配器

为新的 Agent 框架 F 添加适配器的步骤：

1. 在 `agent-runtime` 下创建包 `engine.f`
2. 实现 `AgentRuntimeHandler`（或继承 `AbstractAgentRuntimeHandler`）
3. 实现 `StreamAdapter` 将框架原生结果映射为 `AgentExecutionResult`
4. 可选：实现 `AgentCardProvider` 提供 A2A 卡片元数据
5. 在 `examples/` 下编写示例模块展示适配器用法

Runtime 核心（`A2aAgentExecutor`、`A2aJsonRpcController`、
`RuntimeAutoConfiguration`）无需修改——通过 Spring 的
`ObjectProvider<AgentRuntimeHandler>` 自动发现新的 Handler。

## 各适配器详情

### OpenJiuwen

- 基类：`OpenJiuwenAgentRuntimeHandler`
- 流适配器：`OpenJiuwenStreamAdapter`（`InteractionOutput` → `AgentExecutionResult`）
- 消息适配器：`OpenJiuwenMessageAdapter`（A2A `Message` → openJiuwen `query` + `conversation_id`）
- 记忆：`MemoryRuntimeRail`（ReActAgent）或 `ExternalMemoryRail`（DeepAgent）
- 远端工具：`OpenJiuwenRemoteToolInstaller`
- 文档：[openjiuwen-adapter.md](openjiuwen-adapter.md)

### AgentScope

- Handler：`AgentScopeAgentRuntimeHandler`（SDK）、`AgentScopeHarnessRuntimeHandler`（Harness）、`AgentScopeRuntimeClientHandler`（远端）
- 流适配器：`AgentScopeStreamAdapter`
- 支持进程内 SDK Agent、Harness Agent 和远端 AgentScope Runtime
- 配置：`AgentScopeRuntimeClientProperties`

### Versatile

- Handler：`VersatileAgentRuntimeHandler`（同时实现 `AgentRuntimeHandler` 和 `AgentCardProvider`）
- 客户端：`VersatileClient`（REST/SSE 客户端，可配置 URL 模板）
- 消息适配器：`VersatileMessageAdapter`（A2A Message → REST body + URL 模板变量）
- 流适配器：`VersatileStreamAdapter`（REST SSE 事件 → `AgentExecutionResult`）
- 配置：`VersatileProperties`（URL、请求头、查询参数、元数据映射）
- 示例：`examples/agent-runtime-a2a-versatile-e2e/`

## 相关

- [Handler SPI](handler-spi.md) — 所有适配器实现的接口
- [通讯模型](communication-models.md) — 适配器如何服务同步/流式/异步
- [配置属性](configuration-properties.md) — 各适配器专用配置
