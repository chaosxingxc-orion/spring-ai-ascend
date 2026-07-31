---
level: L1-HLD
TAG:
  - process-view
  - runtime-flow
  - concurrency-boundary
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - logical.md
  - development.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-runtime L1 架构进程视图

## 1. 进程视图定位

本文档描述 `agent-runtime` 在运行时的请求入口、执行推进、异步边界、Task 状态流转、错误处理和并发资源约束。

进程视图回答以下问题：

- 外部 A2A 请求进入 runtime 后，由哪些运行时角色协作完成处理。
- 非流式请求、流式请求和 Task 查询分别走怎样的执行路径。
- Task 如何在提交、执行、中断、完成和失败之间推进。
- HTTP 线程、A2A SDK 事件组件、后台执行线程和 SSE 回流之间的边界在哪里。
- 当前实现有哪些线程安全保证、并发限制和关闭排水策略。

本文档只描述 active 运行事实，不展开类级接口签名、配置项全集、测试矩阵或未来 serviceization 方案。领域对象和状态归属见 `logical.md`，代码组织和依赖边界见 `development.md`，部署和运行形态见 `physical.md`。

### 1.1 运行时参与者

`agent-runtime` 的进程内协作由 A2A SDK 运行组件、runtime bridge 和 Agent 执行 SPI 共同完成。

| 参与者 | 运行职责 |
|---|---|
| `A2aJsonRpcController` | 接收 `/a2a` JSON-RPC 请求，按 JSON-RPC `method` 分发。 |
| `DefaultRequestHandler` | 处理 A2A SDK server 侧请求，创建或查询 Task，并把可执行任务投递到事件组件。 |
| `TaskStore` | 保存 runtime 层 Task 状态和消息事件，默认 `InMemoryTaskStore`，可使用 Redis-backed 实现。 |
| `InMemoryQueueManager` | 维护 Task 事件队列，支撑 SSE 事件回流。 |
| `MainEventBus` | 承担 A2A SDK 内部事件发布和消费连接。 |
| `MainEventBusProcessor` | 在后台执行器中消费事件并触发 Agent 执行。 |
| `A2AAgentExecutor` | 把 A2A SDK 执行请求转换为 `ServeRequest`，调用 `ServeOrchestrator`，并把结果路由回 emitter。 |
| `ServeOrchestrator` | 协调本地 `AgentHandler` 执行、远端 Agent 委派和会话重置。 |
| `AgentHandler` | 业务或框架适配方实现的 Agent 执行入口。 |
| `QueryStreamObserver` | 流式 query 的回调接缝，向 A2A bridge 输出 `QueryChunk`。 |

### 1.2 请求入口与执行域

Runtime 的 northbound 入口是 A2A JSON-RPC over HTTP。当前运行时存在三类执行域：

| 执行域 | 说明 |
|---|---|
| HTTP 线程域 | HTTP 容器线程接收请求、解析 JSON-RPC、进入 A2A SDK request handler，并返回同步 JSON 或建立 SSE 流。 |
| A2A SDK 事件域 | TaskStore、QueueManager、MainEventBus 和 RequestHandler 管理 Task 生命周期、事件发布和事件消费。 |
| Agent 后台执行域 | `MainEventBusProcessor` 使用 `A2AAutoConfiguration` 中创建的 executor 调用 `A2AAgentExecutor` 与业务 handler。 |

进程视图中的“执行”指 runtime 已经接管的 Task 执行路径；业务 Agent 自身 checkpoint、工具内部状态和中间件服务状态不归 `agent-runtime` 进程视图拥有。FEAT-003 Redis cache 只作为 Task 状态缓存和 Agent checkpoint cache 桥接，缓存生命周期受 TTL 约束，不改变业务 checkpoint 的所有权。

### 1.3 Task / Event / SSE 的关系

Task 是 runtime 层面的执行状态单元，Event 是 Task 状态和消息变化的传递载体，SSE 是事件向客户端流式回传的外部连接形态。

```text
A2A Request
  -> RequestHandler
  -> TaskStore: create / update Task
  -> MainEventBus: publish Task event
  -> MainEventBusProcessor: consume and execute
  -> AgentEmitter: publish result event
  -> QueueManager
  -> SSE stream or JSON response
```

非流式请求最终返回 JSON-RPC response；流式请求通过 QueueManager 把 Task 事件转换为 SSE 事件流。

## 2. 主执行流程

### 2.1 非流式 SendMessage 流程

非流式 `SendMessage` 在 controller 中设置 `_a2a_stream=false`，进入 A2A SDK `RequestHandler#onMessageSend(...)`。SDK 创建或推进 Task 后，后台执行器调用 `A2AAgentExecutor`，最终由 `ServeOrchestrator.query(...)` 执行业务 Agent。

```text
A2A 请求
  -> A2aJsonRpcController.handleJsonRpc()
  -> ctx.state["_a2a_stream"] = false
  -> RequestHandler.onMessageSend()
  -> TaskStore: create / update Task
  -> MainEventBus.publish()
  -> MainEventBusProcessor consume
  -> background executor
  -> A2AAgentExecutor.execute(ctx, emitter)
  -> A2AMessageContext.from(ctx)
  -> A2AProtocolAdapter.toServeRequest()
  -> ServeOrchestrator.query(req)
  -> emitter.addArtifact(...) / emitter.complete()
  -> RequestHandler aggregates final event
  -> 返回最终 JSON-RPC response
```

同步路径与流式路径共享 A2A SDK Task 表面；差异在于响应载体是一次性 JSON，而不是 SSE 事件流。

### 2.2 流式 SendStreamingMessage 流程

流式 `SendStreamingMessage` 在 controller 中设置 `_a2a_stream=true`，进入 A2A SDK `RequestHandler#onMessageSendStream(...)`。controller 将 SDK publisher 事件转换为 `SseEmitter` 帧。

```text
时间轴 ------------------------------------------------------------->

Client          Controller       RequestHandler    TaskStore    A2AAgentExecutor   ServeOrchestrator / AgentHandler
  |                 |                  |               |               |                         |
  | POST /a2a       |                  |               |               |                         |
  |---------------->|                  |               |               |                         |
  |                 | handleJsonRpc()  |               |               |                         |
  |                 | onMessageSendStream(...)         |               |                         |
  |                 |----------------->|               |               |                         |
  |                 |                  | create Task   |               |                         |
  |                 |                  |-------------->|               |                         |
  |                 |                  | publish event |               |                         |
  |                 |                  |-- MainEventBus                |                         |
  |                 |                  | [MainEventBusProcessor consume]                         |
  |                 |                  |               | execute(ctx, emitter)       |
  |                 |                  |               |-------------->|                         |
  |                 |                  |               |               | streamQuery(req, observer) |
  |                 |                  |               |               |------------------------->|
  |                 |                  |               | QueryChunk / complete       |
  |                 |                  |<--------------|               |                         |
  | SSE jsonrpc     |                  |               |               |                         |
  |<----------------|                  |               |               |                         |
```

流式执行细节如下：

1. `MainEventBusProcessor` 在后台线程调用 `AgentExecutor.execute(ctx, emitter)`。
2. `A2AAgentExecutor` 构造 `ServeRequest`，并按 `_a2a_stream` 标记选择 `ServeOrchestrator.streamQuery(...)`。
3. `ServeOrchestrator` 通过 `QueryStreamObserver` 向 bridge 输出 `QueryChunk`。
4. `ChunkMapper` 将 chunk 转成 A2A parts，`AgentEmitter` 将其写为 artifact / status event。
5. controller 将 `StreamingEventKind` 包装为 JSON-RPC envelope，并通过 SSE event `jsonrpc` 返回。

### 2.3 Task 状态推进

当前 process 视图中的 Task 状态推进集中在 A2A SDK Task 生命周期和 `A2AAgentExecutor` emitter 回调。

| 触发 | Task 状态 | 说明 |
|---|---|---|
| 收到可执行消息并创建 Task | `SUBMITTED` | RequestHandler 创建 runtime Task，并发布待执行事件。 |
| Agent 开始执行 | `WORKING` | executor 调用 `emitter.startWork()`。 |
| Agent 需要人工输入 | `INPUT_REQUIRED` | interrupt chunk 被路由为 `emitter.requiresInput()`。 |
| Agent 正常完成 | `COMPLETED` | `emitter.complete()` 推进最终完成事件。 |
| Agent 执行失败 | `FAILED` | handler 异常或 observer error 被路由为 `emitter.fail()`。 |

## 3. 分支与终止流程

### 3.1 中断 / 人工输入 / 恢复

Agent 执行过程中需要人工输入时，runtime 将中断表达为 Task 状态和 SSE 事件，而不是把框架私有 checkpoint 暴露给客户端。

```text
AgentHandler 执行过程中需要人工输入
  -> QueryChunk(type = "interrupt", data.message = prompt)
  -> A2AAgentExecutor: emitter.requiresInput(statusMessage)
  -> Task 状态推进到 INPUT_REQUIRED
  -> 客户端收到 SSE 事件或后续通过 GetTask 查询到状态

客户端发送继续消息
  -> POST /a2a with SendMessage(contextId / taskId)
  -> RequestHandler 识别为 resume
  -> A2AAgentExecutor.execute() 再次调用
  -> ServeOrchestrator 根据上下文继续执行
```

Runtime 只负责把恢复所需的上下文和消息传入 `ServeRequest`。具体 Agent checkpoint 的持久化和解释仍归属框架或外部状态能力。

### 3.2 协议层错误

协议层错误指请求本身无法被 runtime 接收为有效 Task 的错误。此类错误没有 Task 载体，直接返回标准 JSON-RPC 错误响应。

`A2aJsonRpcController` 在请求无法解析、未知方法或非法参数时返回 `A2AErrorResponse(id, A2AError)`。

| 错误场景 | 错误码 |
|---|---|
| 畸形 JSON | parse error (-32700) |
| 结构不匹配当前方法参数 | invalid request 或 internal error |
| 未知方法 | method-not-found (-32601) |
| handler 抛出的 `A2AError` | 原码透传 |
| 其他未处理异常 | internal error (-32603) |

### 3.3 任务层失败

任务层失败指请求已经形成 runtime Task，但 Agent 执行或结果适配失败。此类错误通过 Task 状态和失败表面回传。

```text
AgentHandler / ServeOrchestrator 抛出异常
  -> A2AAgentExecutor 捕获异常
  -> emitter.fail()
  -> Task 状态推进到 FAILED
```

## 4. 同步与异步边界

### 4.1 HTTP 线程同步段

HTTP 线程同步段负责接收 HTTP 请求、解析 JSON-RPC、进入 request handler，并为非流式请求返回 JSON 响应或为流式请求建立 SSE 响应。

```text
A2A 请求
  -> A2aJsonRpcController.handleJsonRpc()
  -> RequestHandler.onMessageSend()
     or RequestHandler.onMessageSendStream()
     or RequestHandler.onGetTask()
```

`GetTask` 体现为同步查询路径；非流式 `SendMessage` 在 HTTP 线程上等待最终 JSON 响应，但 Agent 执行仍通过 `MainEventBusProcessor` 后台派发；`SendStreamingMessage` 在 HTTP 线程上建立流式响应后，后续事件通过异步通道回流。

### 4.2 MainEventBus 异步投递段

`MainEventBus` 是 A2A SDK 内部的事件发布边界。RequestHandler 将可执行 Task 事件发布出去，`MainEventBusProcessor` 在后台执行器中消费事件。

```text
RequestHandler
  -> TaskStore update
  -> MainEventBus.publish()
  -> MainEventBusProcessor consume
```

这个边界把请求接入和 Agent 执行解耦，使流式执行可以持续产生事件并通过队列回传。

### 4.3 后台执行线程段

后台执行线程段由 `A2AAutoConfiguration` 创建的 request handler executor 承担，由 `MainEventBusProcessor` 用于调用 `AgentExecutor.execute()`。

```text
MainEventBusProcessor
  -> A2AAgentExecutor.execute(ctx, emitter)
  -> A2AProtocolAdapter.toServeRequest(...)
  -> ServeOrchestrator.query(...) / streamQuery(...)
  -> AgentHandler.query(...) / streamQuery(...)
```

业务 handler 执行期间产生的结果被折叠为 A2A Task / Artifact / Status 事件。

### 4.4 SSE 回流段

SSE 回流段把 Task 事件从 runtime 内部队列转换为客户端可消费的 Server-Sent Events。

```text
AgentEmitter callback
  -> MainEventBus.publish()
  -> QueueManager
  -> RequestHandler Flow.Publisher
  -> SseEmitter
  -> Client
```

SSE 是事件回传载体，不改变 Task 状态的归属。Task 状态仍由 A2A SDK TaskStore 管理。

## 5. 并发与资源约束

### 5.1 共享状态边界

| 状态 | 共享边界 | 并发策略 |
|---|---|---|
| TaskStore | A2A SDK 内部保证或 Redis-backed 实现保证 | 默认进程内实现使用 SDK 数据结构；Redis-backed 实现需保证并发读写、TTL 写入和 Redis 操作原子性符合 Task 状态语义。 |
| QueueManager | A2A SDK 内部保证 | 队列操作由 SDK 管理。 |
| MainEventBus | A2A SDK 内部保证 | 发布订阅模型由 SDK 管理。 |
| ActiveStreamRegistry | runtime lifecycle / cancel 接缝 | 使用并发集合管理活跃 stream handle。 |

### 5.2 关闭与排水

Runtime 关闭时，生命周期组件会停止接收新工作并等待活跃 stream 排水。具体宽限时间和关闭策略由 host lifecycle 配置和当前实现决定。
