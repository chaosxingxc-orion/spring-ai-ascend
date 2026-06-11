---
level: L1
view: logical
module: agent-runtime
status: implemented
authority: "ADR-0152 (Uniform L1 per-view mechanism + L0 mounting)"
---

# `agent-runtime` — 逻辑视图

## 1. 领域模型

### 1.1 核心抽象

```
┌──────────────────────────────────────────────────────────────────────┐
│                          agent-runtime                                │
│                                                                       │
│  ┌──────────┐    ┌──────────────┐    ┌────────────────────┐          │
│  │ access-  │───▶│ task-centric │───▶│     engine         │          │
│  │  layer   │    │   -control   │    │  ┌──────────────┐  │          │
│  │          │◀───│              │◀───│  │AgentRuntime  │  │          │
│  │ (A2A     │    │ (A2A SDK:    │    │  │Handler (SPI) │  │          │
│  │  JSON-   │    │  TaskStore + │    │  ├──────────────┤  │          │
│  │  RPC)    │    │  QueueMgr +  │    │  │openJiuwen    │  │          │
│  │          │    │  EventBus    │    │  │Adapter       │  │          │
│  │          │    │  Processor)  │    │  ├──────────────┤  │          │
│  └──────────┘    └──────────────┘    │  │AgentScope    │  │          │
│       │                 │            │  │Adapter       │  │          │
│       │                 │            │  └──────────────┘  │          │
│       │                 │            └────────────────────┘          │
│       │                 │                      │                     │
│       ▼                 ▼                      ▼                     │
│  ┌──────────────────────────────────────────────────┐               │
│  │            内部事件队列 (A2A SDK MainEventBus)     │               │
│  └──────────────────────────────────────────────────┘               │
│       │                 │                      │                     │
│       ▼                 ▼                      ▼                     │
│  ┌──────────────────────────────────────────────────┐               │
│  │      会话-任务管理器                               │               │
│  │  (AgentStateStore + A2A SDK InMemoryTaskStore)    │               │
│  └──────────────────────────────────────────────────┘               │
└──────────────────────────────────────────────────────────────────────┘
```

### 1.2 关键类型关系

```
RuntimeIdentity (record)
├── tenantId: String       # 租户标识（必填）
├── userId: String         # 用户标识（必填）
├── sessionId: String      # 会话标识（必填）
├── taskId: String         # 任务标识（创建后赋值）
└── agentId: String        # Agent 标识（必填）

AgentExecutionContext (class)
├── scope: RuntimeIdentity     # 调用身份
├── inputType: String          # 输入类型（默认 "USER_MESSAGE"）
├── messages: List<Message>    # A2A SDK 消息列表
├── variables: Map             # 扩展变量
├── agentStateKey: String     # Agent 状态存储键
└── agentState: Map            # Agent 状态快照（懒加载）

AgentExecutionResult (class)
├── Type enum: OUTPUT | COMPLETED | FAILED | INTERRUPTED
├── outputContent: String      # 输出文本（OUTPUT / COMPLETED 时有效）
├── errorCode: String          # 错误码（FAILED 时有效）
├── errorMessage: String       # 错误消息（FAILED 时有效）
└── prompt: String             # 提示文本（INTERRUPTED 时有效）

AgentRuntimeHandler (interface)
├── agentId(): String
├── isHealthy(): boolean
├── execute(AgentExecutionContext): Stream<?>
├── providers(): List<AgentRuntimeProvider>   # 默认空列表
└── resultAdapter(): StreamAdapter

AgentRuntimeProvider (interface)
├── beforeExecute(AgentExecutionContext): void   # 默认空实现
└── afterExecute(AgentExecutionContext): void    # 默认空实现
```

### 1.3 状态机

Task 状态转换（由 A2A SDK 管理）：

```
SUBMITTED ──▶ WORKING ──▶ COMPLETED
                 │
                 ├──▶ FAILED
                 │
                 ├──▶ CANCELED
                 │
                 └──▶ INPUT_REQUIRED ──▶ WORKING (resume)
```

Agent 执行结果类型驱动 task 状态推进：
- `OUTPUT` → 保持 WORKING（流式输出的中间片段）
- `COMPLETED` → 推进到 COMPLETED
- `FAILED` → 推进到 FAILED
- `INTERRUPTED` → 推进到 INPUT_REQUIRED（等待人工输入后 resume）

## 2. 内部划分

### 2.1 5 层架构

#### access-layer（接入层）

**包**：`runtime.boot`（Spring Boot 控制器层） + A2A SDK `RequestHandler`

**核心类**：
| 类 | 职责 |
|---|---|
| `A2aJsonRpcController` | JSON-RPC over HTTP 端点，处理 `SendMessage` / `GetTask` / `CancelTask` / `SendStreamingMessage` / `SubscribeToTask` |
| `AgentCardController` | 提供 `/.well-known/agent-card.json` 和 `/.well-known/agent.json` |
| `A2A SDK RequestHandler` | 协议请求分发器，将 A2A 请求转换为内部调用 |

**设计决策**：
- A2A 是当前唯一的外部协议实现，但 `AgentExecutionContext` 和 `RuntimeIdentity` 作为内部标准数据对象，与 A2A 协议解耦
- 接入层将 A2A 的 `RequestContext` 转换为 `AgentExecutionContext`（通过 `A2aAgentExecutor.toExecutionContext()`）
- 流式应答通过 `AgentEmitter` 回调接口实现，支持 `sendMessage` / `complete` / `fail` / `requiresInput` 四种事件

#### session-task-manager（会话-任务管理器）

**包**：`runtime.engine.service` + A2A SDK `InMemoryTaskStore`

**核心类**：
| 类 | 职责 |
|---|---|
| `AgentStateStore` | Agent 状态持久化接口：`load(key)` / `save(key, state)` / `delete(key)` |
| `InMemoryAgentStateStore` | 基于 `ConcurrentHashMap` 的内存实现 |
| `InMemoryTaskStore`（A2A SDK） | Task 对象的 CRUD 存储 |
| `InMemoryPushNotificationConfigStore`（A2A SDK） | 推送通知配置存储 |

**设计决策**：
- 会话层定位为业务数据"集散地"：存储不耦合业务逻辑，只提供数据存取接口
- 接口设计遵循开闭原则：`AgentStateStore` 定义最小契约，通过接口扩展而非修改来支持新能力
- InMemory 作为第一版实现，未来可替换为 Redis / JDBC 分布式实现
- Task 的存储使用 A2A SDK 的 `InMemoryTaskStore`，Agent 自有状态使用 `AgentStateStore`

#### internal-event-queue（内部事件队列）

**包**：A2A SDK `MainEventBus` + `InMemoryQueueManager` + `MainEventBusProcessor`

**核心类**：
| 类 | 职责 |
|---|---|
| `MainEventBus`（A2A SDK） | 内部事件总线，发布-订阅模式 |
| `InMemoryQueueManager`（A2A SDK） | 任务队列管理器，内存实现 |
| `MainEventBusProcessor`（A2A SDK） | 后台线程消费事件，触发状态推进和 engine 调用 |

**设计决策**：
- 事件队列本身无业务语义，只提供基础的发布-订阅和队列管理能力
- 业务模块（task-centric-control）实例化并使用队列能力
- 由 `RuntimeAutoConfiguration` 作为工厂类装配所有组件
- `MainEventBusProcessor` 在独立线程（`Executor`）上运行，不阻塞接入线程

#### task-centric-control（任务中心控制层）

**包**：A2A SDK `DefaultRequestHandler` + `InMemoryTaskStore` + `MainEventBusProcessor`

**核心类**：
| 类 | 职责 |
|---|---|
| `DefaultRequestHandler`（A2A SDK） | 处理所有 A2A 请求：消息发送、任务查询、取消、订阅 |
| `InMemoryTaskStore`（A2A SDK） | Task 的持久化存储 |
| `MainEventBusProcessor`（A2A SDK） | 消费事件，管理 Task 状态机推进 |

**设计决策**：
- 所有 task 状态变更通过单一入口（`RequestHandler`）进行，保证状态一致性
- `MainEventBusProcessor` 是后台异步处理器，负责从事件队列消费事件并根据状态发起 engine 调用
- 状态跃迁由 A2A SDK 内部管理，业务层通过 `AgentExecutor` 接口与 engine 交互
- `A2aAgentExecutor.cancel()` 提供任务取消的单一入口

#### engine（引擎层）

**包**：`runtime.engine.spi` + `runtime.engine.a2a` + `runtime.engine.openjiuwen` + `runtime.engine.agentscope`

**核心类**：
| 类 | 职责 |
|---|---|
| `AgentRuntimeHandler` | Agent 框架与引擎之间的 SPI 解耦面 |
| `AgentRuntimeProvider` | 可选的生命周期钩子（before/after execute） |
| `AgentRuntimeProviderChain` | 按序执行 providers + handler，保证失败隔离 |
| `AgentExecutionResult` | 引擎中立的结果载体（4 种类型） |
| `StreamAdapter` | 框架结果 → 中立结果流的转换器 |
| `AgentCardProvider` | A2A Agent Card 供应接口 |
| `AgentCards` | 默认 Agent Card 工厂方法 |
| `StateProvider` | 框架状态桥接标记（继承 `AgentRuntimeProvider`） |
| `A2aAgentExecutor` | A2A SDK `AgentExecutor` 实现，桥接 A2A 协议与 SPI |
| `AgentExecutionContext` | 最小执行上下文（与 `RequestContext` 解耦） |
| `OpenJiuwenAgentRuntimeHandler` | openJiuwen ReAct Agent 抽象基类 |
| `AbstractAgentScopeRuntimeHandler` | AgentScope 适配器抽象基类 |

**设计决策**：
- `AgentRuntimeHandler` 是引擎与框架之间的唯一 SPI：一个 Agent ID 对应一个 Handler
- 框架适配器通过组合（`AgentRuntimeProvider`）而非深层继承来扩展功能
- `AgentExecutionContext` 刻意与 A2A SDK 的 `RequestContext` 解耦，保证框架适配器不依赖 A2A 协议
- `A2aAgentExecutor` 是 A2A 接入与 engine SPI 之间的胶水层：将 `RequestContext` 转为 `AgentExecutionContext`，将 `AgentExecutionResult` 转为 `AgentEmitter` 回调
- 当前实现了 openJiuwen 和 AgentScope 两个框架的适配器

### 2.2 层间交互协议

```
access-layer                task-centric-control           engine
    │                              │                         │
    │  1. A2A 请求到达              │                         │
    │  (SendMessage/etc.)          │                         │
    │                              │                         │
    ├──2. 调用 RequestHandler──────▶│                         │
    │                              │                         │
    │                              │  3. 创建 Task            │
    │                              │  (SUBMITTED → WORKING)  │
    │                              │                         │
    │                              ├──4. 调用 AgentExecutor──▶│
    │                              │   execute(ctx, emitter) │
    │                              │                         │
    │                              │                         │  5. toExecutionContext()
    │                              │                         │  6. AgentRuntimeProviderChain
    │                              │                         │     .execute(handler, context)
    │                              │                         │  7. handler.execute(context)
    │                              │                         │  8. resultAdapter().adapt(raw)
    │                              │                         │
    │                              │◀──9. emitter 回调────────┤
    │                              │   sendMessage/complete/ │
    │                              │   fail/requiresInput    │
    │                              │                         │
    │                              │  10. 更新 Task 状态      │
    │                              │                         │
    │◀──11. SSE/JSON 应答─────────│                         │
    │                              │                         │
```

### 2.3 依赖方向

```
agent-runtime
    │
    ├── engine.spi          ← 零外部依赖（仅 java.* + bus.spi.engine 载体）
    │   ├── AgentRuntimeHandler
    │   ├── AgentRuntimeProvider
    │   ├── AgentExecutionResult
    │   └── StreamAdapter
    │
    ├── engine.a2a          ← 依赖 engine.spi + A2A SDK (AgentExecutor)
    │   └── A2aAgentExecutor
    │
    ├── engine.openjiuwen   ← 依赖 engine.spi + openJiuwen SDK
    │   └── OpenJiuwenAgentRuntimeHandler
    │
    ├── engine.agentscope   ← 依赖 engine.spi + AgentScope SDK
    │   └── AbstractAgentScopeRuntimeHandler
    │
    ├── boot                ← 依赖 A2A SDK + Spring Boot + engine.spi + engine.a2a
    │   ├── RuntimeAutoConfiguration
    │   └── A2aJsonRpcController
    │
    └── app                 ← 依赖 engine.spi（纯 Java，无 Spring）
        ├── RuntimeApp
        └── LocalA2aRuntimeHost  ← 依赖 Spring Boot（唯一 Spring 依赖点）
```
