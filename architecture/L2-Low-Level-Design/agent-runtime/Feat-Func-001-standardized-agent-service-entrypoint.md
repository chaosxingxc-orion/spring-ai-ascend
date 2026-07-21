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

> 目标模块：`service/agent-service-app/src/main/java/com/openjiuwen/service/app/controller/a2a/`、`service/agent-service-app/src/main/java/com/openjiuwen/service/app/autoconfigure/`、`service/agent-service-app/src/main/java/com/openjiuwen/service/app/config/`
> 最后更新：2026-07-09

---

## 1. 概述

### 1.1 特性定位

agent-runtime 以 Google A2A JSON-RPC over HTTP 作为当前北向标准服务入口。外部 A2A client 通过 Agent Card 发现 runtime，并通过统一 `/a2a` JSON-RPC endpoint 发起阻塞调用、流式调用或按 task id 查询结果。

- **解决的问题**：Agent 需要一个标准化、可互操作的对外协议入口。A2A 提供 Agent Card 发现、JSON-RPC 调用、SSE 流式响应和 Task 生命周期表面。
- **适用场景**：所有需要对外暴露 Agent 的场景。A2A 客户端可以是其他 Agent、前端应用、CI/CD 流水线或任意 HTTP 客户端。

### 1.2 当前事实边界

本文只描述 Feat-Func-001 在当前工程中的已接受实现事实。面向调用方的当前版本事实要求、用户场景和外部行为边界由 `version-scope/FEAT-001-standardized-agent-service-entrypoint.md` 驱动；本 L2 只展开当前 controller、SDK bridge、Agent Card controller 和 auto-configuration 的实现结构。

### 1.3 设计原则

1. **协议无关核心** — A2A 协议适配层负责 JSON-RPC 解析和序列化，内部执行仍通过 `ServeOrchestrator` / `AgentHandler` 完成。
2. **统一入口** — `POST /a2a` 和 `POST /a2a/` 承载 A2A JSON-RPC 请求，方法分发由 JSON-RPC `method` 字段驱动。
3. **Agent Card 自动发现** — `GET /.well-known/agent-card.json` 和 `GET /.well-known/agent.json` 返回由运行时配置与服务身份信息生成的 Agent Card。
4. **A2A 层投射 Task 表面** — `A2AAgentExecutor` 将内部 query / streamQuery 结果投射为 A2A Task、Artifact、Status 和 SSE 事件。

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| A2A JSON-RPC Methods | 暴露当前已分发的 JSON-RPC 方法 | `A2aJsonRpcController`, `RequestHandler` | ✅ |
| Agent Card 发现 | Agent 能力声明与自动发现 | `AgentCardController`, `A2AProperties` | ✅ |
| S2C 通讯模型 | 阻塞、流式、异步查询三种通讯模式 | `A2AAgentExecutor`, `A2AProtocolAdapter` | ✅ |
| A2A 执行桥接 | SDK AgentExecutor → ServeOrchestrator / AgentHandler 转换 | `A2AAgentExecutor` | ✅ |

---

## 2. 特性规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| SendStreamingMessage | ✅ | 当前流式 SSE 消息主入口 |
| SendMessage | ✅ | 阻塞请求-响应，由 SDK request handler 收集执行结果后返回 JSON-RPC response |
| GetTask | ✅ | 按 task id 查询任务状态与结果 |
| Push Notification Config Store | ✅ | 装配 SDK `InMemoryPushNotificationConfigStore` |
| Push Notification Config CRUD JSON-RPC 分发 | ⬜ | controller 当前未分发 Create/Get/List/Delete push config 方法 |
| runtime-to-runtime webhook 实际推送 | ⬜ | 当前 `PushNotificationSender` 为 no-op，HTTP webhook 推送未激活 |
| Agent Card 发现 | ✅ | `GET /.well-known/agent-card.json` + `GET /.well-known/agent.json` + `/a2a/.well-known/agent-card.json` |
| Agent Card 配置 | ✅ | 由 `openjiuwen.service.a2a.*` 配置与 `AgentServiceIdentity` / `ServiceProperties` 生成 |
| Agent Card skills 声明 | ✅ | 由 `openjiuwen.service.a2a.skills[]` 声明 |
| Agent Card capabilities 声明 | ✅ | streaming / pushNotifications / extendedAgentCard 能力宣告 |
| JSON-RPC 错误处理 | ✅ | Method Not Found / Invalid Request / Parse Error / Internal Error |
| HTTP + SSE 传输 | ✅ | 当前仅 HTTP JSON-RPC 与 SSE |
| gRPC 传输 | ⬜ | 当前未暴露 gRPC northbound 传输 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| A2A 方法名 snake_case 形式 | 当前 controller 按 PascalCase method 分发 | 使用 `SendStreamingMessage`、`SendMessage`、`GetTask` |
| 多 Agent 路由 | 当前服务实例通过单个 `AgentHandler` / `ServeOrchestrator` 承载 Agent 执行 | 多 Agent 部署由多个 runtime 实例或上层路由承接 |
| 普通 client webhook | 当前 webhook 语义面向受信任 runtime-to-runtime 场景 | 普通 client 使用 SSE、`GetTask` 或应用侧集成通道 |

### 2.3 行为承诺

- **必须**：`SendMessage` 与 `SendStreamingMessage` 接受相同的 `params.message` 主结构。
- **必须**：`SendStreamingMessage` 返回 `text/event-stream`，SSE event 名为 `jsonrpc`，data 为 JSON-RPC envelope。
- **必须**：JSON-RPC error response 尽量携带原 request `id`。
- **允许**：`POST /a2a` / `POST /a2a/` 按 JSON-RPC `method` 选择当前已支持的阻塞或流式处理路径。

---

## 3. 核心实现

### 3.1 A2A Methods 分发

```
POST /a2a  {"jsonrpc":"2.0", "method":"SendStreamingMessage", ...}

A2aJsonRpcController
  │
  ├─ 解析 JSON-RPC body: method / id
  │
  ├─ SendMessage
  │   ├─ ctx.state["_a2a_stream"] = false
  │   ├─ parseParams(rawBody) → MessageSendParams
  │   └─ requestHandler.onMessageSend(params, ctx)
  │
  ├─ SendStreamingMessage
  │   ├─ ctx.state["_a2a_stream"] = true
  │   ├─ parseParams(rawBody) → MessageSendParams
  │   ├─ requestHandler.onMessageSendStream(params, ctx)
  │   └─ Flow.Publisher<StreamingEventKind> → SseEmitter
  │
  └─ GetTask
      ├─ params → TaskQueryParams
      └─ requestHandler.onGetTask(params, ctx)

未知 method → JSON-RPC method-not-found error
```

当前 controller 未分发 push config CRUD method；SDK push config store 已由 auto-configuration 装配。

### 3.2 S2C 通讯模式

#### 阻塞请求-响应（SendMessage）

```
A2A Client                    Runtime
  │                              │
  │── SendMessage ──────────────>│
  │                              │ A2AAgentExecutor.execute()
  │                              │ ServeOrchestrator.query()
  │<─────── JSON Task ──────────│
```

`A2aJsonRpcController` 将 `_a2a_stream=false` 写入 `ServerCallContext.state`。`A2AAgentExecutor` 据此构造非流式 `ServeRequest`，调用 `ServeOrchestrator.query()`，再通过 `AgentEmitter` 写入 artifact 并完成 Task。

#### 流式（SendStreamingMessage）

```
A2A Client                    Runtime
  │                              │
  │── SendStreamingMessage ─────>│
  │<── SSE: TaskAccepted ───────│
  │<── SSE: ArtifactUpdate ─────│  (× N)
  │<── SSE: TaskStatusUpdate ───│  (COMPLETED/FAILED/CANCELED/INPUT_REQUIRED)
  │                              │
```

`A2aJsonRpcController` 将 `_a2a_stream=true` 写入 `ServerCallContext.state`。`A2AAgentExecutor` 调用 `ServeOrchestrator.streamQuery()`，把 `QueryChunk` 转成 A2A parts 后通过 `AgentEmitter.addArtifact()` 输出。controller 通过 `SseEmitter.event().name("jsonrpc")` 输出 JSON-RPC envelope。

#### 异步查询（GetTask）

`GetTask` 通过 SDK `RequestHandler#onGetTask()` 查询 `TaskStore` 中的 task 快照。当前 task store 由 `A2AAutoConfiguration` 装配，默认使用 `InMemoryTaskStore`；配置 Redis checkpointer 时使用 `WriteThrottlingTaskStore` 包装 `RedisTaskStore`。

### 3.3 A2A 执行桥接

```
A2AAgentExecutor.execute()
  │
  ├─ A2AMessageContext.from(RequestContext)
  ├─ A2AProtocolAdapter.toServeRequest(messageContext)
  ├─ 根据 ctx.state["_a2a_stream"] 设置 ServeRequest.stream
  ├─ emitter.submit()
  ├─ emitter.startWork()
  │
  ├─ stream=true
  │   └─ ServeOrchestrator.streamQuery(req, QueryStreamObserver)
  │       ├─ normal chunk      → ChunkMapper.toParts() → emitter.addArtifact()
  │       ├─ interrupt chunk   → emitter.requiresInput(statusMessage)
  │       ├─ onComplete        → emitter.complete()
  │       └─ onError           → emitter.fail()
  │
  └─ stream=false
      └─ ServeOrchestrator.query(req)
          ├─ result.content    → emitter.addArtifact(TextPart)
          ├─ _interrupt        → emitter.requiresInput(statusMessage)
          └─ normal completion → emitter.complete()
```

### 3.4 Agent Card Skills → 远程 Tool 注入链

Agent Card 中声明的 `skills` 是跨 Agent 协作的起点。当前 Agent Card 由 `AgentCardController` 根据 `A2AProperties` 和服务身份信息生成：

```
openjiuwen.service.a2a.skills[]
  │
  ▼
AgentCardController.buildCard()
  │
  ├─ name/version      ← AgentServiceIdentity / ServiceProperties
  ├─ url/interface     ← publicUrl 或当前请求地址 + jsonRpcPath
  ├─ capabilities      ← streaming / pushNotifications / extendedAgentCard
  └─ skills            ← SkillProperties → AgentSkill
```

只有 `skills` 非空的 Agent Card 才会被远程主 Agent 识别为可安装工具集合。远程 Agent 的发现、缓存、工具安装和 outbound 调用由 `Feat-Func-004-remote-agent-orchestration.md` 承接。

---

## 4. 代码结构

### 4.1 包结构

```
service/agent-service-app/src/main/java/com/openjiuwen/service/app/
├── autoconfigure/
│   └── A2AAutoConfiguration.java      # A2A SDK task store、queue、request handler、push store/sender 装配
├── config/
│   └── A2AProperties.java             # openjiuwen.service.a2a.* 配置绑定
└── controller/a2a/
    ├── A2aJsonRpcController.java      # POST /a2a、POST /a2a/ JSON-RPC 入口
    ├── AgentCardController.java       # Agent Card well-known endpoints
    ├── A2AAgentExecutor.java          # A2A SDK AgentExecutor → ServeOrchestrator bridge
    ├── A2AProtocolAdapter.java        # A2A Message → ServeRequest adapter
    ├── A2AMessageContext.java         # RequestContext 中的 A2A message/task/context 包装
    ├── ChunkMapper.java               # QueryChunk → A2A Part 转换
    ├── RedisTaskStore.java            # Redis-backed TaskStore
    └── WriteThrottlingTaskStore.java  # Redis task store 写入节流包装
```

### 4.2 核心类静态关系

```
«controller»                 «sdk bridge»                 «runtime core»
A2aJsonRpcController  →   RequestHandler           →   A2AAgentExecutor
      │                         │                         │
      │                         │                         ▼
      │                         │                 ServeOrchestrator
      │                         │                         │
      ▼                         ▼                         ▼
AgentCardController       TaskStore / QueueManager     AgentHandler
```

---

## 5. 运行流程

### 5.1 主流程

主流程由第 3 章各子特性的内部实现流程描述；本章只补充跨流程的错误和降级语义，避免重复外部用户场景。

### 5.2 分支流程

分支流程按第 3 章中的状态流转、数据流或 adapter 分支处理。涉及外部调用方式的黑盒场景不在 L2 展开。

### 5.3 错误与降级处理

| 错误场景 | 触发条件 | 行为 | 对外结果 |
|---------|---------|------|---------|
| 非 JSON 请求体 | `JsonParser.parseString(rawBody)` 失败 | 返回 parse error | `{"jsonrpc":"2.0","error":{"code":-32700}}` |
| method 未知 | method 不在 controller switch 分支中 | 返回 method-not-found | `{"error":{"code":-32601}}` |
| params 缺失/不匹配 | controller 手写解析或 SDK wrapper 校验失败 | 返回 internal error 或 SDK error response | `{"error":{...}}` |
| 阻塞分支 SDK 异常 | `RequestHandler` 抛出 `A2AError` | 带原 request id 的 error response | `{"id":"req-1","error":{...}}` |
| 流式发送失败 | SSE send / serialization 异常 | 取消 subscription 并 `completeWithError` | HTTP SSE 连接以错误结束 |
| Handler 执行异常 | `A2AAgentExecutor` 捕获执行异常 | `emitter.fail()` | Task 进入 failed 表面或返回 JSON-RPC internal error |

---

## 6. 配置使用

### 6.1 完整配置示例

```yaml
openjiuwen:
  service:
    a2a:
      public-url: https://agents.example.com/runtime
      agent-description: 我的自定义 Agent
      documentation-url: https://example.com/docs
      icon-url: https://example.com/icon.png
      streaming: true
      push-notifications: false
      extended-agent-card: false
      default-input-modes: [text, text/plain]
      default-output-modes: [text, text/plain]
      provider-organization: My Company
      provider-url: https://example.com
      json-rpc-path: /a2a
      agent-card-path: /a2a/.well-known/agent-card.json
      skills:
        - id: my-skill
          name: My Skill
          description: 这个 skill 描述会被远程 Agent 发现并注册为 Tool
          tags: [my-tag]
          input-modes: [text]
          output-modes: [text]
```

### 6.2 配置属性表

| 属性路径 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `openjiuwen.service.a2a.public-url` | String | — | Agent Card 外部 URL，为空时从请求地址自动检测 |
| `openjiuwen.service.a2a.agent-description` | String | `OpenJiuwen Agent Runtime Service` | Agent 描述 |
| `openjiuwen.service.a2a.documentation-url` | String | — | Agent Card 文档 URL |
| `openjiuwen.service.a2a.icon-url` | String | — | Agent Card 图标 URL |
| `openjiuwen.service.a2a.streaming` | boolean | `true` | Agent Card streaming capability |
| `openjiuwen.service.a2a.push-notifications` | boolean | `false` | Agent Card pushNotifications capability |
| `openjiuwen.service.a2a.extended-agent-card` | boolean | `false` | Agent Card extendedAgentCard capability |
| `openjiuwen.service.a2a.default-input-modes` | List | `[text, text/plain]` | Agent Card 默认输入模式 |
| `openjiuwen.service.a2a.default-output-modes` | List | `[text, text/plain]` | Agent Card 默认输出模式 |
| `openjiuwen.service.a2a.provider-organization` | String | `""` | Agent provider organization |
| `openjiuwen.service.a2a.provider-url` | String | `""` | Agent provider URL |
| `openjiuwen.service.a2a.skills` | List | `[]` | Skill 声明，供远程 Agent 发现并注册为 Tool |
| `openjiuwen.service.a2a.remote-agents` | List | `[]` | 远程 Agent 发现配置，由远程编排能力使用 |
| `openjiuwen.service.a2a.json-rpc-path` | String | `/a2a` | Agent Card 中声明的 JSON-RPC endpoint path |
| `openjiuwen.service.a2a.agent-card-path` | String | `/a2a/.well-known/agent-card.json` | 兼容路径配置 |
| `openjiuwen.service.a2a.task-completion-timeout-seconds` | int | `300` | task 完成等待相关配置 |

---

## 7. 当前限制

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| Push Notification Config CRUD 未经 controller 分发 | 调用方无法通过当前 `/a2a` controller 管理 push config | 使用已支持的 SSE 或 `GetTask` |
| Push Notification sender 为 no-op | runtime-to-runtime HTTP webhook 实际推送未激活 | 使用 SSE 或 `GetTask` |
| 仅 HTTP + SSE 传输 | 高性能场景 gRPC 不可用 | — |
