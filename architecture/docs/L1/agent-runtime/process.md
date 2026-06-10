---
level: L1
view: process
module: agent-runtime
status: implemented
authority: "ADR-0152 (Uniform L1 per-view mechanism + L0 mounting)"
---

# `agent-runtime` — 进程视图

## 1. 并发模型

### 1.1 线程架构

```
                          ┌──────────────────────────┐
                          │   HTTP IO Threads         │
                          │   (Tomcat/Netty)          │
                          └──────────┬───────────────┘
                                     │ 接收 A2A 请求
                                     ▼
                          ┌──────────────────────────┐
                          │   A2aJsonRpcController   │
                          │   (IO 线程)               │
                          └──────────┬───────────────┘
                                     │ 委托给 RequestHandler
                                     ▼
                          ┌──────────────────────────┐
                          │   DefaultRequestHandler   │
                          │   (IO 线程，同步路径)      │
                          │   或 (IO 线程，异步投递)   │─── 投递到 MainEventBus
                          └──────────┬───────────────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              │                      │                      │
              ▼                      ▼                      ▼
    ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────┐
    │  QueueManager   │  │  MainEventBus    │  │  InMemoryTask-   │
    │  (同步/IO线程)   │  │  (同步发布)       │  │  Store           │
    └────────┬────────┘  └────────┬─────────┘  │  (同步/IO线程)    │
             │                    │             └──────────────────┘
             │                    │
             └────────┬───────────┘
                      │ 事件投递
                      ▼
          ┌──────────────────────────┐
          │  MainEventBusProcessor   │
          │  (后台线程:               │
          │   CachedThreadPool)      │
          └──────────┬───────────────┘
                     │ 调用 AgentExecutor.execute()
                     ▼
          ┌──────────────────────────┐
          │  A2aAgentExecutor        │
          │  (后台线程)               │
          │  → AgentRuntimeHandler   │
          │    .execute(context)     │
          └──────────┬───────────────┘
                     │ emitter 回调
                     ▼
          ┌──────────────────────────┐
          │  AgentEmitter 回调通知    │
          │  → MainEventBus 发布     │
          │  → 最终 SSE 返回 IO 线程  │
          └──────────────────────────┘
```

### 1.2 线程安全保证

| 组件 | 线程安全策略 | 说明 |
|---|---|---|
| `InMemoryAgentStateStore` | `ConcurrentHashMap` | 线程安全的原子操作，无需额外加锁 |
| `InMemoryTaskStore`（A2A SDK） | A2A SDK 内部保证 | SDK 内部使用并发安全的数据结构 |
| `InMemoryQueueManager`（A2A SDK） | A2A SDK 内部保证 | 队列操作线程安全 |
| `MainEventBus`（A2A SDK） | A2A SDK 内部保证 | 发布-订阅模式天然线程安全 |
| `AgentExecutionContext.replaceAgentState()` | volatile + 不可变 Map | 写入使用 volatile 保证可见性，Map.copyOf() 保证不可变 |
| `AgentRuntimeProviderChain` | 无共享状态 | 每次调用创建新的 providers 列表副本（`List.copyOf()`），`AtomicBoolean` 保证 close 一次 |

### 1.3 并发限制

```yaml
app:
  runs:
    dispatch:
      core-threads: 4       # 核心线程数
      max-threads: 16       # 最大线程数
      queue-capacity: 256   # 队列容量
      rejection-policy: CALLER_RUNS  # 过载策略（调用者线程执行）
```

- 默认拒绝策略为 `CALLER_RUNS`：队列满时由调用者线程执行，提供背压
- Spring Boot 虚拟线程支持已开启（`spring.threads.virtual.enabled: true`），在高并发场景下降低线程开销

## 2. 异步/同步边界

### 2.1 同步路径

```
A2A 请求 → A2aJsonRpcController.handle()
    → RequestHandler.onMessageSend()     // 同步
    → AgentExecutor.execute()            // 同步
    → AgentRuntimeHandler.execute()      // 同步
    → 返回结果 JSON                      // 同步应答
```

适用于 `SendMessage`（非流式）、`GetTask`、`CancelTask`。

### 2.2 异步路径（流式 SSE）

```
A2A 请求 → A2aJsonRpcController.handleSse()
    → RequestHandler.onMessageSendStream()    // 返回 Flow.Publisher
    → 转换为 Flux<ServerSentEvent>            // Reactor 桥接
    → SSE 流式返回                            // 异步流
```

适用于 `SendStreamingMessage`、`SubscribeToTask`。

**流式执行细节**：
1. `MainEventBusProcessor` 在后台线程调用 `AgentExecutor.execute(ctx, emitter)`
2. `A2aAgentExecutor` 通过 `AgentRuntimeProviderChain.execute(handler, context)` 获取 `Stream<?>`
3. 结果流经 `StreamAdapter.adapt()` 转换为 `Stream<AgentExecutionResult>`
4. 每个 result 通过 `emitter.sendMessage()` / `emitter.complete()` / `emitter.fail()` / `emitter.requiresInput()` 回调
5. emitter 回调触发 `MainEventBus` 发布事件
6. 事件通过 `MainEventBusProcessor` → `QueueManager` → `RequestHandler` 最终到达 SSE 流

### 2.3 异步边界图

```
  IO 线程 (同步)          │     后台线程 (异步)        │    IO 线程 (SSE 流)
                         │                            │
  RequestHandler ────────┼──▶ MainEventBus.publish() ──▶ MainEventBusProcessor
                         │                            │
                         │                    AgentExecutor.execute()
                         │                            │
                         │                  AgentRuntimeHandler
                         │                      .execute()
                         │                            │
                         │                     emitter 回调
                         │                            │
                         │              MainEventBus.publish()
                         │                            │
  SSE Flux ◀────────────┼────────────────────────────┘
                         │
```

## 3. 执行流程

### 3.1 主流程：A2A 消息发送 → Agent 执行 → 应答

```
时间轴 ──────────────────────────────────────────────────────────────▶

Client          Controller       RequestHandler    TaskStore    A2aAgentExecutor   AgentRuntimeHandler
  │                 │                  │               │               │                │
  │ POST /a2a       │                  │               │               │                │
  │────────────────▶│                  │               │               │                │
  │                 │                  │               │               │                │
  │                 │ parseRequestBody │               │               │                │
  │                 │─────────────────▶│               │               │                │
  │                 │                  │               │               │                │
  │                 │                  │ create Task   │               │                │
  │                 │                  │──────────────▶│               │                │
  │                 │                  │ (SUBMITTED)   │               │                │
  │                 │                  │               │               │                │
  │                 │                  │ publish event │               │                │
  │                 │                  │───▶ MainEventBus              │                │
  │                 │                  │               │               │                │
  │                 │                  │   [MainEventBusProcessor 消费]                │
  │                 │                  │               │               │                │
  │                 │                  │               │  execute(ctx, emitter)         │
  │                 │                  │               │──────────────▶│                │
  │                 │                  │               │               │                │
  │                 │                  │               │               │ toExecutionContext()
  │                 │                  │               │               │                │
  │                 │                  │               │               │ execute(context)
  │                 │                  │               │               │───────────────▶│
  │                 │                  │               │               │                │
  │                 │                  │               │               │  Stream<?>     │
  │                 │                  │               │               │◀───────────────│
  │                 │                  │               │               │                │
  │                 │                  │               │    resultAdapter().adapt(raw)  │
  │                 │                  │               │               │                │
  │                 │                  │               │  [遍历 AgentExecutionResult]   │
  │                 │                  │               │               │                │
  │                 │                  │               │  emitter.sendMessage(text)     │
  │                 │                  │◀──────────────│               │                │
  │                 │                  │               │               │                │
  │                 │                  │ update Task   │               │                │
  │                 │                  │ (WORKING)     │               │                │
  │                 │                  │──────────────▶│               │                │
  │                 │                  │               │               │                │
  │  SSE: event     │                  │               │               │                │
  │◀────────────────│                  │               │               │                │
  │                 │                  │               │               │                │
  │                 │                  │  [最终结果]   │               │                │
  │                 │                  │  emitter.complete() / fail() │                │
  │                 │                  │               │               │                │
  │                 │                  │ update Task   │               │                │
  │                 │                  │ (COMPLETED)   │               │                │
  │                 │                  │──────────────▶│               │                │
  │                 │                  │               │               │                │
  │  SSE: final     │                  │               │               │                │
  │◀────────────────│                  │               │               │                │
```

### 3.2 中断/恢复流程

```
1. AgentRuntimeHandler 执行过程中需要人工输入:
   → StreamAdapter 返回 AgentExecutionResult.interrupted(prompt)
   → A2aAgentExecutor.route(): emitter.requiresInput()
   → Task 状态推进到 INPUT_REQUIRED
   → 客户端收到 SSE 事件（含 prompt）

2. 客户端发送继续消息:
   → POST /a2a with SendMessage (context_id = taskId)
   → RequestHandler 识别为 resume
   → AgentExecutor.execute() 再次调用
   → AgentRuntimeHandler 从 AgentExecutionContext.getAgentState() 恢复上下文
   → 继续执行
```

### 3.3 取消流程

```
Client                Controller         RequestHandler      A2aAgentExecutor
  │                       │                    │                    │
  │ POST /a2a             │                    │                    │
  │ (CancelTask)          │                    │                    │
  │──────────────────────▶│                    │                    │
  │                       │                    │                    │
  │                       │ onCancelTask()     │                    │
  │                       │───────────────────▶│                    │
  │                       │                    │                    │
  │                       │                    │ cancel(ctx, emitter)
  │                       │                    │───────────────────▶│
  │                       │                    │                    │
  │                       │                    │   emitter.cancel() │
  │                       │                    │◀───────────────────│
  │                       │                    │                    │
  │                       │                    │ Task → CANCELED    │
  │                       │                    │                    │
  │  Response (JSON)      │                    │                    │
  │◀──────────────────────│                    │                    │
```

### 3.4 错误处理流程

```
AgentRuntimeHandler.execute() 抛出异常:
  → AgentRuntimeProviderChain.closeEntered() 关闭已进入的 providers
  → A2aAgentExecutor 捕获异常:
      LOG.error("[A2A] execute failed ...")
      emitter.fail()
  → Task 状态推进到 FAILED
  → 客户端收到失败通知

AgentRuntimeProvider.beforeExecute() 抛出异常:
  → AgentRuntimeProviderChain 关闭已进入的 providers，重新抛出异常
  → A2aAgentExecutor 捕获 → emitter.fail()

AgentRuntimeProvider.afterExecute() 抛出异常:
  → AgentRuntimeProviderChain 捕获并 LOG.warn()，不中断主流程
  → 其他 providers 的 afterExecute 继续执行
```
