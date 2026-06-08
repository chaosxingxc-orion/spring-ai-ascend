---
level: L1
view: features
module: agent-runtime
status: design-phase
authority: "ADR-0159 (agent-runtime consolidation) + 2026-06-05 five-module refactor proposal"
covers: [异构兼容, 中间件解耦, S2C通讯模型, A2A协议标准]
---

# agent-runtime — 核心特性清单

> 本文档描述 `agent-runtime` 的核心功能特性要求。特性按能力维度组织，贯穿
> runtime 各组件，不绑定到特定模块或包的内部划分。

---

## 特性 1：异构 Agent 框架兼容

### 1.1 能力描述

Runtime 通过统一的 Adapter 抽象接入不同类型的 Agent 实现，使上层调用方无需
感知底层 Agent 框架差异。需要支持三种接入方式：

| Adapter | 接入方式 | 适用场景 |
|---------|---------|---------|
| **OpenJiuwenAdapter** | 进程内直接调用 `openjiuwen-agent-core-java` | 低延迟、同进程部署的 Agent 执行 |
| **AgentScopeAdapter** | 进程内直接调用 `agentscope-java` | 基于 AgentScope 框架构建的 Agent |
| **VersatileAdapter** | 通过 RESTful API 调用远端 Agent 服务 | 独立部署、跨进程、异构语言实现的 Agent 服务 |

三种 Adapter 对外暴露一致的调用语义（提交任务、获取结果流、取消执行），
调用方以相同的方式使用本地 Agent 和远端 Agent。

### 1.2 功能要求

- Adapter 负责将统一的执行请求转换为目标框架或远端服务能理解的输入格式
- Adapter 负责将目标框架或远端服务返回的结果转换为 runtime 统一的输出格式
- 新增 Agent 框架支持只需增加对应的 Adapter，不修改 runtime 核心

---

## 特性 2：中间件解耦

### 2.1 能力描述

Agent 执行过程中依赖的通用基础设施能力从 Agent 框架中解耦，以可注入、
可替换的中间件服务形式由 runtime 统一提供。需要覆盖以下能力：

| 中间件能力 | 作用 | 典型实现方向 |
|-----------|------|-------------|
| **Agent State 持久化** | Agent 执行状态的 checkpoint/save/restore，支持中断恢复 | InMemory / Redis / 数据库 |
| **Memory 服务** | 短期会话记忆 + 长期记忆检索，按 userId/sessionId 隔离 | InMemory / Redis / 向量数据库 |

### 2.2 功能要求

- 中间件服务以接口形式定义，与 Agent 框架解耦——同一套中间件实现可用于不同 Agent 框架
- 用户或平台在启动 runtime 时注入具体的中间件实现，运行时通过执行上下文传递给 Agent Adapter
- Agent Adapter 按需使用中间件能力——不强制所有 Adapter 都使用全部中间件
- 切换存储后端不影响 Agent 代码，只需替换中间件实现
- Runtime session（外部连续会话管理）与 Agent session（框架级 agent 执行会话）概念分离，
  中间件服务作用于 Agent session 而不污染 Runtime session
- 中间件服务接口不依赖任何特定 Agent 框架

---

## 特性 3：三种 S2C（Server-to-Client）通讯模型

### 3.1 能力描述

Runtime 需要同时支持三种通讯模式以满足不同调用场景，三种模式共享统一的请求/响应
类型体系：

| 模式 | 行为 | 适用场景 |
|------|------|---------|
| **同步（Blocking）** | 客户端发送请求，等待完整结果后返回 | 简短问答、批处理 |
| **流式（Streaming）** | 客户端发送请求，服务端通过 SSE 持续推送增量结果 | LLM 流式生成、实时输出 |
| **异步（Async）** | 客户端提交任务后立即返回 taskId，后续通过 taskId 查询结果 | 长时任务、人工审批流程 |

### 3.2 功能要求

- 调用方在请求中声明期望的通讯模式（同步 / 流式 / 异步）
- 响应消息区分四种类型：任务已接受、增量输出、最终结果、异常
- 流式模式下，服务端按顺序推送：任务已接受 → 增量输出（零条或多条）→ 最终结果
- 异步模式下，客户端提交任务后立即获得 taskId，后续通过 taskId 查询任务状态和结果

---

## 特性 4：A2A 协议标准支持

### 4.1 能力描述

Runtime 需要对外暴露符合 Google A2A（Agent-to-Agent）协议标准的服务端点，
使任意 A2A 客户端都能通过标准化 JSON-RPC 接口发现和调用 Agent。

### 4.2 需要支持的 A2A Methods

| A2A Method | 功能 | 对应通讯模式 |
|---|---|---|
| `message/send` | 发送消息并获取完整回复 | 同步 |
| `message/stream` | 发送消息并通过 SSE 获取流式回复 | 流式 |
| `tasks/get` | 查询异步任务状态与结果 | 异步 |
| `tasks/cancel` | 取消正在执行的任务 | — |
| `tasks/resubscribe` | 断线重连后重新订阅输出流 | 流式 |
| Agent Card (`/.well-known/agent.json`) | Agent 能力发现端点 | — |

### 4.3 功能要求

- A2A 协议适配层负责 JSON-RPC 请求解析和 A2A 标准格式的响应序列化
- A2A 专有字段在协议适配层完成映射，不泄露到 runtime 内部——runtime 核心以
  协议无关的方式工作
- 流式响应通过 SSE 协议推送给客户端，同步响应直接返回 JSON
