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
- 非流式请求、流式请求和事件订阅分别走怎样的执行路径。
- Task 如何在提交、执行、中断、恢复、完成、失败、拒绝和取消之间推进。
- IO 线程、A2A SDK 事件总线、后台执行线程和 SSE 回流之间的边界在哪里。
- 当前实现有哪些线程安全保证、并发限制和关闭排水策略。

本文档只描述 active 运行事实，不展开类级接口签名、配置项全集、测试矩阵或未来 serviceization 方案。领域对象和状态归属见 `logical.md`，代码组织和依赖边界见 `development.md`，部署和运行形态见 `physical.md`。

### 1.1 运行时参与者

`agent-runtime` 的进程内协作由 A2A SDK 运行组件、runtime 桥接组件和框架中立 Agent SPI 共同完成。

| 参与者 | 运行职责 |
|---|---|
| `A2aJsonRpcController` | 接收 `/a2a` JSON-RPC 请求，区分普通响应和 SSE 响应入口。 |
| `DefaultRequestHandler` | 处理 A2A SDK server 侧请求，创建或查询 Task，并把可执行任务投递到事件总线。 |
| `TaskStore` | 保存 runtime 层 Task 状态和消息事件；默认 `InMemoryTaskStore`，FEAT-003 可替换为 Redis-backed TaskStore。 |
| `InMemoryQueueManager` | 维护 Task 事件队列，支撑 SSE 订阅和事件回流。 |
| `MainEventBus` | 承担 A2A SDK 内部事件发布和消费连接。 |
| `MainEventBusProcessor` | 在后台执行器中消费事件并触发 Agent 执行。 |
| `A2aAgentExecutor` | 把 A2A SDK 执行请求转换为 runtime 中立执行上下文，调用 `AgentRuntimeHandler`，并把执行结果路由回 emitter。 |
| `AgentRuntimeHandler` | 框架中立 Agent 执行 SPI，由 openJiuwen、AgentScope 等适配实现承接。 |
| `StreamAdapter` | 将框架原生结果流转换为 `AgentExecutionResult`。 |
| Trajectory SPI | 可选执行轨迹扩展，由支持 `TrajectorySource` 的 handler 在执行中发射。 |

### 1.2 请求入口与执行域

Runtime 的 northbound 入口是 A2A JSON-RPC over HTTP。当前运行时存在三类执行域：

| 执行域 | 说明 |
|---|---|
| IO 线程域 | HTTP 容器线程接收请求、解析 JSON-RPC、进入 A2A SDK request handler，并返回同步 JSON 或建立 SSE 流。 |
| A2A SDK 事件域 | TaskStore、QueueManager、MainEventBus 和 RequestHandler 管理 Task 生命周期、事件发布和事件消费。 |
| Agent 后台执行域 | `MainEventBusProcessor` 使用 `RuntimeAutoConfiguration.A2aServerExecutor` 调用 `A2aAgentExecutor` 与框架适配器。 |

进程视图中的“执行”指 runtime 已经接管的 Task 执行路径；业务 Agent 自身 checkpoint、工具内部状态和中间件服务状态不归 `agent-runtime` 进程视图拥有。FEAT-003 Redis cache 只作为桥接和 Task 状态缓存，不改变业务 checkpoint 的所有权。

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
  -> SSE subscriber or JSON response
```

非流式请求最终返回 JSON-RPC response；流式请求和订阅请求通过 QueueManager 把 Task 事件转换为 SSE 事件流。

## 2. 主执行流程

### 2.1 非流式 SendMessage 流程

非流式 `SendMessage` 在请求线程中进入 A2A SDK `RequestHandler`，由 `RequestHandler` 创建或推进 Task，并通过 `MainEventBus` / `MainEventBusProcessor` 将 Agent 执行派发到后台执行器。请求线程等待 A2A SDK 聚合最终事件后返回一次性 JSON-RPC 响应。它适用于调用方只需要最终结果、无需持续接收中间事件的场景。

```text
A2A 请求
  -> A2aJsonRpcController.handle()
  -> RequestHandler.onMessageSend()
  -> TaskStore: create / update Task
  -> MainEventBus.publish()
  -> MainEventBusProcessor consume
  -> A2aServerExecutor background thread
  -> AgentExecutor.execute(ctx, emitter)
  -> A2aAgentExecutor.toExecutionContext()
  -> AgentRuntimeHandler.execute(context)
  -> StreamAdapter.adapt(raw)
  -> emitter emits Task events
  -> RequestHandler aggregates final event
  -> 返回最终 JSON-RPC response
```

同步路径仍使用 runtime 中立执行上下文和结果适配语义；差异只在于响应载体是一次性 JSON，而不是 SSE 事件流。它不意味着接入线程直接执行业务 Agent。

### 2.2 流式 SendStreamingMessage 流程

流式 `SendStreamingMessage` 先建立 SSE 响应，再通过 A2A SDK 事件机制把 Agent 执行过程中的消息、状态和最终结果推送给客户端。

```text
时间轴 ------------------------------------------------------------->

Client          Controller       RequestHandler    TaskStore    A2aAgentExecutor   AgentRuntimeHandler
  |                 |                  |               |               |                |
  | POST /a2a SSE   |                  |               |               |                |
  |---------------->|                  |               |               |                |
  |                 | handleSse()      |               |               |                |
  |                 |----------------->|               |               |                |
  |                 |                  | create Task   |               |                |
  |                 |                  |-------------->|               |                |
  |                 |                  | SUBMITTED     |               |                |
  |                 |                  | publish event |               |                |
  |                 |                  |-- MainEventBus                |                |
  |                 |                  | [MainEventBusProcessor consume]               |
  |                 |                  |               | execute(ctx, emitter)          |
  |                 |                  |               |-------------->|                |
  |                 |                  |               |               | toExecutionContext()
  |                 |                  |               |               | execute(context)
  |                 |                  |               |               |--------------->|
  |                 |                  |               |               | Stream<?>      |
  |                 |                  |               |               |<---------------|
  |                 |                  |               | resultAdapter().adapt(raw)     |
  |                 |                  |               | emit AgentExecutionResult      |
  |                 |                  |<--------------|               |                |
  |                 |                  | update Task   |               |                |
  |                 |                  | WORKING       |               |                |
  | SSE event       |                  |               |               |                |
  |<----------------|                  |               |               |                |
  |                 |                  | final result  |               |                |
  |                 |                  | emitter.complete() / fail()   |                |
  |                 |                  | update Task   |               |                |
  |                 |                  | COMPLETED / FAILED            |                |
  | SSE final       |                  |               |               |                |
  |<----------------|                  |               |               |                |
```

流式执行细节如下：

1. `MainEventBusProcessor` 在后台线程调用 `AgentExecutor.execute(ctx, emitter)`。
2. `A2aAgentExecutor` 构造 `AgentExecutionContext`，按配置打开可选 trajectory，并调用 `handler.execute(context)` 获取 `Stream<?>`。
3. 原始结果流经 `StreamAdapter.adapt()` 转换为 `Stream<AgentExecutionResult>`。
4. 每个 result 通过 `emitter.sendMessage()`、`emitter.complete()`、`emitter.fail()`、`emitter.requiresInput()` 或 `emitter.cancel()` 路由。
5. emitter 回调触发 `MainEventBus` 发布 Task 事件。
6. 事件通过 `MainEventBusProcessor`、`QueueManager` 和 `RequestHandler` 最终进入 SSE 流。

### 2.3 SubscribeToTask 事件订阅流程

`SubscribeToTask` 不创建新的 Agent 执行，而是订阅已有 Task 的事件队列。

```text
A2A 请求 SubscribeToTask
  -> A2aJsonRpcController.handleSse()
  -> RequestHandler.onResubscribeToTask()
  -> QueueManager 绑定 Task 事件队列
  -> 转换为 Flux<ServerSentEvent>
  -> 客户端持续接收已有 Task 的后续事件
```

订阅路径的核心边界是：客户端可以重新接入 runtime 层 Task 事件流，但不会绕过 TaskStore 或直接读取 Agent 框架内部状态。

### 2.4 Task 状态推进

当前 process 视图中的 Task 状态推进集中在 A2A SDK Task 生命周期和 `A2aAgentExecutor` emitter 回调。

| 触发 | Task 状态 | 说明 |
|---|---|---|
| 收到可执行消息并创建 Task | `SUBMITTED` | RequestHandler 创建 runtime Task，并发布待执行事件。 |
| Agent 开始产生执行结果 | `WORKING` | emitter 发送普通消息或执行进展，Task 进入工作态。 |
| Agent 需要人工输入 | `INPUT_REQUIRED` | `AgentExecutionResult.interrupted(prompt)` 被路由为 `emitter.requiresInput()`。 |
| Agent 需要远端 Agent 调用并回灌 | `INPUT_REQUIRED` 或继续 `WORKING` | `AgentExecutionResult.interrupted(remoteInvocation)` 交给 `A2aRemoteInvocationOrchestrator`；远端需要输入时父 Task 停在 `INPUT_REQUIRED`，远端完成后以 `REMOTE_RESUME` 重新进入本地 handler。 |
| Agent 正常完成 | `COMPLETED` | `emitter.complete()` 推进最终完成事件。 |
| Agent 执行失败 | `FAILED` | handler 异常或 adapter 失败结果被路由为 `emitter.fail()`。 |
| 未注册 handler | `REJECTED` | 前置拒绝，未进入 `WORKING`。 |
| 客户端取消 Task | `CANCELED` | `CancelTask` 触发 `emitter.cancel()`。 |

## 3. 分支与终止流程

### 3.1 中断 / 人工输入 / 恢复

Agent 执行过程中需要人工输入时，runtime 将中断表达为 Task 状态和 SSE 事件，而不是把框架私有 checkpoint 暴露给客户端。

```text
AgentRuntimeHandler 执行过程中需要人工输入
  -> StreamAdapter 返回 AgentExecutionResult.interrupted(prompt)
  -> A2aAgentExecutor.route(): emitter.requiresInput()
  -> Task 状态推进到 INPUT_REQUIRED
  -> 客户端收到 SSE 事件，事件中携带 prompt

客户端发送继续消息
  -> POST /a2a with SendMessage(context_id = taskId)
  -> RequestHandler 识别为 resume
  -> AgentExecutor.execute() 再次调用
  -> AgentRuntimeHandler 从 AgentExecutionContext.getAgentState() 恢复上下文
  -> 继续执行
```

Runtime 只负责把恢复所需的状态键、上下文和状态快照传入 `AgentExecutionContext`。FEAT-003 可为 checkpoint 提供 Redis cache 桥接，但具体 Agent checkpoint 的持久化、解释和一致性仍归属框架或外部状态能力。

### 3.2 取消流程

取消流程由 A2A `CancelTask` 进入 RequestHandler，再通过 Agent executor 的 cancel 语义推进 Task 状态。

```text
Client                Controller         RequestHandler      A2aAgentExecutor
  |                       |                    |                    |
  | POST /a2a             |                    |                    |
  | (CancelTask)          |                    |                    |
  |---------------------->|                    |                    |
  |                       | onCancelTask()     |                    |
  |                       |------------------->|                    |
  |                       |                    | cancel(ctx, emitter)
  |                       |                    |------------------->|
  |                       |                    |                    |
  |                       |                    |   emitter.cancel() |
  |                       |                    |<-------------------|
  |                       |                    |                    |
  |                       |                    | Task -> CANCELED   |
  | Response (JSON)       |                    |                    |
  |<----------------------|                    |                    |
```

当前取消语义推进 runtime Task 状态为 `CANCELED`。如果底层 Agent 框架需要更强的协作式停止能力，应由对应 handler 或框架适配器在自身执行模型内响应。

### 3.3 协议层错误

协议层错误指请求本身无法被 runtime 接收为有效 Task 的错误。此类错误没有 Task 载体，直接返回标准 JSON-RPC 错误响应。

`A2aJsonRpcController` 在请求无法解析、未知方法或非法参数时，不再静默返回 `{}`，而是返回 `A2AErrorResponse(id, A2AError)`，错误码取自 `A2AErrorCodes`。

| 错误场景 | 错误码 |
|---|---|
| 畸形 JSON | `JSON_PARSE` (-32700) |
| 结构不匹配任何 A2A 请求 | `INVALID_REQUEST` (-32600) |
| 未知方法 | `METHOD_NOT_FOUND` (-32601) |
| handler 抛出的 `A2AError` | 原码透传 |
| 其他未处理异常 | `INTERNAL` (-32603) |

### 3.4 任务层失败

任务层失败指请求已经形成 runtime Task，但 Agent 执行或结果适配失败。此类错误通过 Task 状态和失败 Message 回传。

```text
AgentRuntimeHandler.execute() 抛出异常
  -> A2aAgentExecutor 捕获异常
      RuntimeErrorCode.classify(e)
      LOG.error("[A2A] execute failed ... code=...")
      先交付已收集的 northbound trajectory artifact
      emitter.fail(failureMessage(code, detail, retryable))
  -> 失败 Message 同时携带
      TextPart  "code: detail"
      DataPart  {kind, code, message, retryable, schema_version}
  -> Task 状态推进到 FAILED
```

未捕获异常的稳定错误码集合包括 `INVALID_INPUT`、`TIMEOUT`、`UPSTREAM_UNAVAILABLE`、`CANCELLED` 和 `INTERNAL`，各带 `retryable` 标志，供客户端进行编程式判别。

### 3.5 未注册 handler / reject

当请求已经进入 runtime，但没有可用 `AgentRuntimeHandler` 承接目标 Agent 执行时，`A2aAgentExecutor` 走前置拒绝路径。

```text
未注册 handler
  -> emitter.reject(failureMessage("NO_HANDLER", ...))
  -> Task 状态推进到 REJECTED
```

`REJECTED` 表示任务没有进入 `WORKING`，不同于执行中失败的 `FAILED`。

adapter 产出的 `FAILED` 结果不会再次进入异常分类器，`errorCode` 原样透传，`retryable` 保守为 `false`。

Trajectory sink 抛出的异常由 trajectory fan-out 逻辑隔离并记录，不中断主执行流程。

## 4. 同步与异步边界

### 4.1 IO 线程同步段

IO 线程同步段负责接收 HTTP 请求、解析 JSON-RPC、进入 request handler，并为非流式请求返回 JSON 响应或为流式请求建立 SSE 响应。

```text
A2A 请求
  -> A2aJsonRpcController.handle() / handleSse()
  -> RequestHandler.onMessageSend() / onMessageSendStream()
```

`GetTask` 和 `CancelTask` 主要体现为同步控制路径；非流式 `SendMessage` 在 IO 线程上等待最终 JSON 响应，但 Agent 执行仍通过 `MainEventBusProcessor` 后台派发；`SendStreamingMessage` 和 `SubscribeToTask` 在 IO 线程上建立流式响应后，后续事件通过异步通道回流。

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

后台执行线程段由 `RuntimeAutoConfiguration.A2aServerExecutor` 承担。当前执行器为 `Executors.newCachedThreadPool()`，由 `MainEventBusProcessor` 用于调用 `AgentExecutor.execute()`。

```text
MainEventBusProcessor
  -> A2aAgentExecutor.execute(ctx, emitter)
  -> optional trajectory.open(ctx, context, handler)
  -> AgentRuntimeHandler.execute(context)
  -> StreamAdapter.adapt(raw)
```

框架适配器执行期间产生的结果被折叠为 `AgentExecutionResult`，再由 emitter 路由回 A2A SDK Task 事件体系。

### 4.4 SSE 回流段

SSE 回流段把 Task 事件从 runtime 内部队列转换为客户端可消费的 Server-Sent Events。

```text
AgentEmitter callback
  -> MainEventBus.publish()
  -> QueueManager
  -> RequestHandler Flow.Publisher
  -> Flux<ServerSentEvent>
  -> Client
```

SSE 是事件回传载体，不改变 Task 状态的归属。Task 状态仍由 A2A SDK TaskStore 管理；TaskStore 的物理实现可以是默认 InMemory 或 FEAT-003 Redis-backed。

## 5. 并发与资源约束

### 5.1 线程模型

当前线程模型可以概括为：IO 线程负责接入和响应，A2A SDK 事件组件负责 Task 和事件协调，后台 cached pool 负责 Agent 执行。

```text
                          +--------------------------+
                          | HTTP IO Threads          |
                          | (Tomcat/Netty)           |
                          +------------+-------------+
                                       |
                                       v
                          +--------------------------+
                          | A2aJsonRpcController     |
                          | (IO thread)              |
                          +------------+-------------+
                                       |
                                       v
                          +--------------------------+
                          | DefaultRequestHandler    |
                          | sync path or async post  |
                          +------------+-------------+
                                       |
              +------------------------+------------------------+
              |                        |                        |
              v                        v                        v
    +------------------+     +------------------+     +------------------+
    | QueueManager     |     | MainEventBus     |     | TaskStore        |
    | sync / IO thread |     | sync publish     |     | sync / IO thread |
    +--------+---------+     +--------+---------+     +------------------+
             |                        |
             +-----------+------------+
                         |
                         v
              +--------------------------+
              | MainEventBusProcessor   |
              | background cached pool  |
              +------------+-------------+
                           |
                           v
              +--------------------------+
              | A2aAgentExecutor        |
              | -> AgentRuntimeHandler  |
              +------------+-------------+
                           |
                           v
              +--------------------------+
              | AgentEmitter callbacks  |
              | -> MainEventBus -> SSE  |
              +--------------------------+
```

### 5.2 线程安全保证

| 组件 | 线程安全策略 | 说明 |
|---|---|---|
| `TaskStore` | A2A SDK 内部保证或 Redis-backed 实现保证 | 默认 InMemory 实现由 SDK 内部保证；Redis-backed 实现需保证并发读写和 Redis 操作原子性符合 Task 状态语义。 |
| `InMemoryQueueManager` | A2A SDK 内部保证 | 队列操作线程安全。 |
| `MainEventBus` | A2A SDK 内部保证 | 发布订阅模型由 SDK 管理。 |
| `AgentExecutionContext.replaceAgentState()` | `volatile` + 不可变 `Map` | 写入使用 `volatile` 保证可见性，`Map.copyOf()` 保证不可变。 |
| `A2aAgentExecutor` in-flight registry | `ConcurrentHashMap` + `AtomicBoolean` / `AtomicReference` | 按 taskId 记录在途 raw stream 和取消标志，支持 cancel-through。 |
| `StampingTrajectoryEmitter` | synchronized emit | 对 seq、span stack 和 sink 投递形成单一顺序，兼容框架 worker 线程回调。 |
| `CompositeTrajectorySink` | 异常隔离 | 单个 sink 失败只记录 warn，不影响其他 sink 和主执行。 |

### 5.3 并发上限与背压边界

Agent 执行派发由 `RuntimeAutoConfiguration.A2aServerExecutor` 承担。当前实现使用 `Executors.newCachedThreadPool()`：daemon 线程、按需创建、无固定上限、无专用队列、无专用配置键。

这意味着当前库内不主动提供执行背压。实际并发上限由 HTTP 容器线程、宿主 JVM 资源、底层 Agent 框架能力和部署侧资源治理共同决定。

Spring Boot 虚拟线程是宿主可选项，例如 `spring.threads.virtual.enabled: true`。Runtime 不预置该策略，但在高并发场景下宿主可以用它降低线程开销。

### 5.4 关闭与排水策略

Runtime 关闭时，A2A server executor 先调用 `shutdown()` 进入排水阶段，等待在途执行结束。当前宽限时间为 10s；超时后调用 `shutdownNow()` 尝试强停。

```text
close runtime
  -> executor.shutdown()
  -> wait in-flight executions, up to 10s
  -> executor.shutdownNow() on timeout
```

该策略保护正常关闭时的在途执行，但不等同于业务级任务恢复、跨进程迁移或平台级 drain coordination。这些能力属于更高层运行治理或后续设计范围。
