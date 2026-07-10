---
level: L2-LLD
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-001
status: active
dependency:
  - ../../L1-High-Level-Design/agent-runtime/README.md
  - ../../L1-High-Level-Design/agent-runtime/development.md
  - ../../L1-High-Level-Design/agent-runtime/process.md
  - ../../../version-scope/FEAT-001-standardized-agent-service-entrypoint.md
---

# 标准化智能体服务入口设计文档

> 目标模块：`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a/`、`boot/`
> 最后更新：2026-06-14

---

## 1. 概述

### 1.1 特性定位

agent-runtime 以 Google A2A 协议作为唯一的对外协议标准。北向对外暴露 A2A JSON-RPC 服务端点，任意 A2A 客户端可通过标准化接口发现和调用 Agent。三种 S2C 通讯模式（阻塞请求-响应、流式 SSE、异步 task 查询）通过选择对应的 A2A 方法实现。

- **解决的问题**：Agent 需要一个标准化、可互操作的对外协议。A2A 提供了 Agent Card 发现、JSON-RPC 调用、SSE 流式响应、Task 生命周期管理等完整能力。
- **适用场景**：所有需要对外暴露 Agent 的场景。A2A 客户端可以是其他 Agent、前端应用、CI/CD 流水线等任意 HTTP 客户端。

### 1.2 当前事实边界

本文只描述 Feat-Func-001 在当前 `agent-runtime` 模块中的已接受实现事实。面向调用方的当前版本事实要求、用户场景和外部行为边界由 `version-scope/FEAT-001-standardized-agent-service-entrypoint.md` 驱动；模块级 API/SPI、逻辑对象归属和部署资源模型以 L1 设计及其附录为准。

### 1.3 设计原则

1. **协议无关核心** — A2A 协议适配层负责 JSON-RPC 解析和序列化，runtime 核心以协议无关方式工作
2. **统一入口** — 单一 `POST /a2a` 端点服务所有 A2A 方法，方法分发由 JSON-RPC `method` 字段驱动
3. **Agent Card 自动发现** — 标准 `GET /.well-known/agent-card.json` 端点，支持 YAML 配置 + Handler 声明 + 自动生成
4. **Handler 始终 Stream** — Handler 始终以 Stream 方式产出结果，`SendMessage` 的阻塞返回由 A2A 层收集 Stream 后一次性 JSON 响应实现

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| A2A Methods | 对外暴露的 JSON-RPC 方法全集 | `A2aJsonRpcController`, `RequestHandler` | ✅ |
| Agent Card 发现 | Agent 能力声明与自动发现 | `AgentCardController`, `AgentCardProperties` | ✅ |
| S2C 通讯模型 | 阻塞/流式/异步三种通讯模式 | `A2aAgentExecutor`, `A2aResultRouter` | ✅ |
| A2A 执行桥接 | SDK AgentExecutor → Handler SPI 转换 | `A2aAgentExecutor` | ✅ |

---

## 2. 特性规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| SendStreamingMessage | ✅ | 流式 SSE 消息，Agent 全流程主入口 |
| SendMessage | ✅ | 阻塞请求-响应（A2A 层收集 Stream 后返回 JSON） |
| GetTask | ✅ | 按 taskId 查询任务状态与结果 |
| CancelTask | ✅ | 取消执行中任务（OpenJiuwen 仅阻止消费，不中断 LLM） |
| ListTasks | ✅ | 任务列表查询 |
| SubscribeToTask | ✅ | 断线重连恢复订阅 SSE 流 |
| Push Notification Config CRUD | ✅ | Create/Get/List/Delete Push 配置（SDK 层支持，实际推送未激活） |
| Agent Card 发现 | ✅ | `GET /.well-known/agent-card.json` + `/.well-known/agent.json` |
| Agent Card YAML 配置 | ✅ | YAML 驱动 + Handler 声明 + 自动生成 |
| Agent Card skills 声明 | ✅ | YAML 或 AgentCardProvider 声明 skills，供远程 Agent 发现并注册为 Tool |
| Agent Card capabilities 声明 | ✅ | streaming / pushNotifications 等能力宣告 |
| JSON-RPC 错误处理 | ✅ | Method Not Found / Invalid Request / Parse Error / Internal Error |
| Tenant 标识传播 | ✅ | `X-Tenant-Id` 头提取，贯穿调用链路 |
| Push Notification 实际推送 | ⬜ | SDK 组件已装配，推送未激活 |
| gRPC 传输 | ⬜ | 当前仅 HTTP + SSE |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| A2A 方法名 snake_case 形式 | A2A SDK 使用 PascalCase（`SendStreamingMessage`），`message/stream` 形式不被 parser 识别 | 统一使用 CamelCase |
| 多 Agent 路由 | runtime 当前只承载单 Agent；如果注册多个 Handler Bean 会记录 WARN，并按 `@Order` 选取第一个作为兼容降级 | 每个 Agent 部署独立 runtime 实例 |
| Tenant 认证 | runtime 不认证 tenant header，仅传播 | 在 /a2a 前放置认证网关 |

### 2.3 行为承诺

- **必须**：`SendMessage` 与 `SendStreamingMessage` 接受相同的 `params.message` 结构
- **必须**：SSE 流在 INTERRUPTED / FAILED / CANCELED 终端状态下关闭
- **必须**：JSON-RPC error response 携带原 request `id`
- **禁止**：未就绪时接受请求（`RuntimeReadiness` gate 关闭时拒绝）
- **允许**：`SendMessage` 在 Agent 执行超时后返回当前 task 快照（仍为 WORKING），后续执行产物不再写入 HTTP response

---

## 3. 核心实现

### 3.1 A2A Methods 分发

```
POST /a2a  {"jsonrpc":"2.0", "method":"SendStreamingMessage", ...}

A2aJsonRpcController
  │
  ├─ JSON-RPC 解析: method 字段
  │
  ├─ 流式分支 (Accept: text/event-stream)
  │   ├─ SendStreamingMessage → handler.onMessageSendStream()
  │   └─ SubscribeToTask      → handler.onSubscribeToTask()
  │
  └─ 阻塞分支 (Accept: application/json)
      ├─ SendMessage          → handler.onMessageSend()
      ├─ GetTask              → handler.onGetTask()
      ├─ CancelTask           → handler.onCancelTask()
      ├─ ListTasks            → handler.onListTasks()
      ├─ CreateTaskPushNotificationConfig    → handler.onCreate...()
      ├─ GetTaskPushNotificationConfig       → handler.onGet...()
      ├─ ListTaskPushNotificationConfigs     → handler.onList...()
      └─ DeleteTaskPushNotificationConfig    → handler.onDelete...()

未知 method → JSON-RPC method-not-found error
```

### 3.2 S2C 通讯模式

#### 阻塞请求-响应（SendMessage）

```
A2A Client                    Runtime
  │                              │
  │── SendMessage ──────────────>│
  │                              │ handler.execute() → Stream<?>
  │                              │ A2A 层收集 Stream（阻塞等待）
  │<─────── JSON Task ──────────│
```

Handler 始终以 Stream 方式产出结果。阻塞返回由 A2A SDK `DefaultRequestHandler#onMessageSend` 收集 Stream 后一次性 JSON 响应实现。超时由 `a2a.blocking.agent.timeout.seconds`（默认 30s）和 `a2a.blocking.consumption.timeout.seconds`（默认 5s）控制。

#### 流式（SendStreamingMessage）

```
A2A Client                    Runtime
  │                              │
  │── SendStreamingMessage ─────>│
  │<── SSE: TaskAccepted ───────│
  │<── SSE: ArtifactUpdate ─────│  (× N)
  │<── SSE: ArtifactUpdate ─────│
  │<── SSE: TaskStatusUpdate ───│  (COMPLETED/FAILED/CANCELED)
  │                              │
```

SSE event=`jsonrpc`，data=JSON-RPC response envelope，result=SDK `StreamingEventKind`。流在终端状态后关闭或由 controller 主动终止。

#### 异步（GetTask / CancelTask / ListTasks）

Task 生命周期：`SUBMITTED → WORKING → COMPLETED / FAILED / CANCELED / INPUT_REQUIRED`

SDK 默认组件：`InMemoryTaskStore` / `MainEventBus` / `InMemoryQueueManager` / `DefaultRequestHandler`。

### 3.3 A2A 执行桥接

```
A2aAgentExecutor.consumeHandler()
  │
  ├─ 就绪门控: RuntimeReadiness.get() ? continue : reject
  ├─ 租户传播: X-Tenant-Id → MDC(contextId, taskId, tenantId, agentId)
  ├─ 构建 AgentExecutionContext
  │
  ├─ handler.execute(context) → Stream<?> raw
  ├─ handler.resultAdapter().adapt(raw) → Stream<AgentExecutionResult>
  │
  └─ 逐个消费:
       A2aResultRouter.route(result, emitter)
         ├─ OUTPUT       → emitter.addArtifact(TextPart)
         ├─ COMPLETED    → emitter.complete()
         ├─ FAILED       → emitter.fail() + DataPart(code, message, retryable)
         └─ INTERRUPTED  → task → INPUT_REQUIRED
            ├─ UserInputInterrupt    → 展示 prompt，等待用户输入
            └─ RemoteAgentInterrupt  → 触发远端 A2A 调用
```

### 3.4 Agent Card Skills → 远程 Tool 注入链

Agent Card 中声明的 `skills` 是跨 Agent 协作的起点——远程 Agent 的 Card Cache 读取这些 skills，生成 `RemoteAgentToolSpec`，安装为本地 Agent 的 Tool：

```
Agent Card (skills/capabilities)
  │
  ├─ YAML: agent-runtime.access.a2a.agent-card.skills[].description
  ├─ Provider: AgentCardProvider.describe().skills()
  │
  ▼ GET /.well-known/agent-card.json
  │
远程主 Agent (agent-runtime.remote-agents[0].url = <本 Agent URL>)
  │
  ├─ RemoteAgentCardCache: 拉取 Card → 解析 skills
  ├─ RemoteAgentToolSpec: description = skill.description（LLM 看到的工具描述）
  └─ OpenJiuwenRemoteToolInstaller: 注册为 OpenJiuwen Tool
       └─ LLM 调用 → Interrupt Rail → 远程 A2A 调用
```

**触发条件**：只有 `skills` 非空的 Agent Card 才会被远程主 Agent 注册为 Tool。如果你的 Agent 需要被其他 Agent 作为 Tool 调用，必须在 Agent Card 中声明至少一个 skill。详见 [远程 Agent 编排设计文档](Feat-Func-005-remote-agent-orchestration.md)。

### 3.5 Tenant Header

优先级：`X-Tenant-Id` HTTP header > `params.tenant` > `"default"`。runtime 不认证 tenant——多租户部署需要在 `/a2a` 前放置认证网关。

---

## 4. 代码结构

### 4.1 包结构

```
boot/
├── A2aJsonRpcController.java       # POST /a2a 单一入口，方法分发到阻塞/流式分支
└── AgentCardController.java        # GET /.well-known/agent-card.json

engine/a2a/
├── A2aAgentExecutor.java           # A2A SDK AgentExecutor → Handler SPI 桥接
├── A2aResultRouter.java            # AgentExecutionResult → A2A Task 表面路由
├── A2aTrajectorySupport.java       # 轨迹设置解析 + Sink 扇出
├── A2aLogMasking.java              # 日志敏感信息掩码
├── AgentCardProperties.java        # YAML AgentCard 配置属性
├── AgentCardProvider.java          # Handler 可选实现的 AgentCard 声明接口
├── AgentCards.java                 # AgentCard 工厂
├── RuntimeErrorCode.java           # 异常 → 稳定错误码分类（walk-the-cause-chain）
├── Messages.java                   # A2A Message TextPart 文本提取工具
├── A2aAgentExecutor.java           # `TENANT_STATE_KEY` 定义 header 派生 tenant 的 call-context key
└── RuntimeAccessProperties.java    # `defaultTenantId` 等 A2A access 层默认值配置
```

### 4.2 核心类静态关系

```
«controller»               «executor»                  «router»
A2aJsonRpcController  →   A2aAgentExecutor    →   A2aResultRouter
      │                         │
      │ 分发到                   │ 调用
      ▼                         ▼
RequestHandler           AgentRuntimeHandler
(onMessageSend,          (execute → Stream<?> →
 onMessageSendStream,     resultAdapter().adapt())
 onGetTask, ...)
```

---

## 5. 运行流程

### 5.1 主流程

主流程由第 3 章各子特性的内部实现流程描述；本章只补充跨流程的错误、取消和降级语义，避免重复外部用户场景。

### 5.2 分支流程

分支流程按第 3 章中的状态流转、数据流或 adapter 分支处理。涉及外部调用方式的黑盒场景不在 L2 展开。

### 5.3 错误、取消、降级处理

| 错误场景 | 触发条件 | 行为 | 对外结果 |
|---------|---------|------|---------|
| 非 JSON 请求体 | `JSONRPCUtils.parseRequestBody()` 失败 | 返回 parse error | `{"jsonrpc":"2.0","error":{"code":-32700}}` |
| method 未知 | method 不在 controller 分支中 | 返回 method-not-found | `{"error":{"code":-32601}}` |
| params 缺失/不匹配 | SDK request wrapper 校验失败 | 返回 invalid-request | `{"error":{"code":-32600}}` |
| 阻塞分支 SDK 异常 | `RequestHandler` 抛出 `A2AError` | 带原 request id 的 error response | `{"id":"req-1","error":{...}}` |
| SSE 解析失败 | 请求解析失败 | 一帧 SSE JSON-RPC error | `event:jsonrpc\ndata:{"error":{...}}` |
| SSE 流开始后异常 | Handler 执行中失败 | 末尾追加一帧 SSE JSON-RPC error | 同上 |
| Agent 执行超时 | 超过 `a2a.blocking.agent.timeout.seconds` | 返回当前 task 快照 | `{"result":{"status":{"state":"TASK_STATE_WORKING"}}}` |
| 消费超时 | 超过 `a2a.blocking.consumption.timeout.seconds` | 返回 JSON-RPC error | `{"error":{"code":-32603,"message":"Timeout..."}}` |

---

## 6. 配置使用

### 6.1 完整配置示例

```yaml
server:
  port: 8080
  shutdown: graceful

agent-runtime:
  access:
    a2a:
      default-tenant-id: default
      default-agent-id: my-agent
      public-base-url: https://agents.example.com/runtime
      agent-card:
        name: my-agent
        description: 我的自定义 Agent
        version: "1.0.0"
        organization: My Company
        organization-url: https://example.com
        endpoint: /a2a
        skills:
          - id: my-skill
            name: My Skill
            description: 这个 skill 描述会被远程 Agent 发现并注册为 Tool
            tags: [my-tag]
        capabilities:
          streaming: true
          push-notifications: false
```

### 6.2 配置属性表

| 属性路径 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `agent-runtime.access.a2a.default-tenant-id` | String | `default` | 无 X-Tenant-Id 头时的租户标识 |
| `agent-runtime.access.a2a.default-agent-id` | String | — | 默认 Agent ID |
| `agent-runtime.access.a2a.public-base-url` | String | — | Agent Card 外部 URL，为空时从请求自动检测 |
| `agent-runtime.access.a2a.agent-card.name` | String | handler.agentId() | Agent 名称 |
| `agent-runtime.access.a2a.agent-card.description` | String | `agent-runtime` | Agent 描述 |
| `agent-runtime.access.a2a.agent-card.version` | String | `0.1.0` | 版本号 |
| `agent-runtime.access.a2a.agent-card.organization` | String | `spring-ai-ascend` | 组织名 |
| `agent-runtime.access.a2a.agent-card.endpoint` | String | `/a2a` | A2A 端点路径 |
| `agent-runtime.access.a2a.agent-card.skills` | List | — | Skill 声明，供远程 Agent 发现并注册为 Tool |
| `agent-runtime.access.a2a.agent-card.capabilities` | Map | — | 能力宣告（streaming / pushNotifications） |

---

## 7. 当前限制

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| SendMessage 非完整 Agent 调用入口 | Agent 全流程以 `SendStreamingMessage` 为准 | 使用 `SendStreamingMessage` |
| Push Notification 未激活 | Webhook 回调不可用 | 使用 `GetTask` 轮询 |
| 仅 HTTP + SSE 传输 | 高性能场景 gRPC 不可用 | — |
| Tenant 不认证 | 多租户需自建认证 | 在 `/a2a` 前放置认证网关 |
