---
level: L1-HLD
TAG:
  - logical-view
  - domain-model
  - architecture-fact
  - module-boundary
status: active
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - development.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-runtime L1 架构逻辑视图

## 1. 逻辑视图定位

`agent-runtime` 是可嵌入、可独立启动的 task-owning runtime SDK。逻辑视图描述该 runtime 内部的领域对象、状态归属、职责分层和边界方向。

本视图回答以下问题：

- 外部 A2A 请求进入 runtime 后，被抽象成哪些中立领域对象。
- Runtime 如何绑定 Session、Task 与 Agent 执行上下文。
- Runtime 内部五个逻辑责任面分别承担什么职责。
- Task 状态、Agent 执行结果、Agent checkpoint 和中间件服务状态分别归属于哪里。
- 协议、框架适配、状态读写和上层 service 之间的依赖方向如何保持隔离。

## 2. 领域对象模型

### 2.1 Runtime 身份与调用上下文

`RuntimeIdentity` 表示一次 runtime 调用的身份范围。

```text
RuntimeIdentity
├── tenantId     租户标识
├── userId       用户标识
├── sessionId    会话标识
├── taskId       任务标识
└── agentId      Agent 标识
```

`AgentExecutionContext` 是 engine 层消费的中立执行上下文。它承载 runtime 已经确认的身份、输入、消息、变量和可选状态快照，不直接暴露外部协议对象。

```text
AgentExecutionContext
├── scope         RuntimeIdentity
├── inputType     输入类型
├── messages      中立运行时消息
├── variables     扩展变量
├── agentStateKey Agent 状态读取键
└── agentState    Agent 状态快照
```

`AgentExecutionContext` 的逻辑职责是把外部协议请求转为框架无关的执行语义。A2A 的 `RequestContext`、JSON-RPC 请求结构、SSE 响应细节不向框架适配器扩散。

### 2.2 Task / Session / Agent State 归属

`agent-runtime` 同时处理三类状态，但它们的归属不同。

| 状态对象 | 归属 | Runtime 职责 |
|---|---|---|
| Session | runtime 会话域 | 作为用户连续交互的关联范围，用于绑定 Task 与调用身份 |
| Task | runtime 任务域 | 作为 task-owning 的状态单元，承载提交、执行、完成、中断、失败、取消等生命周期 |
| Agent checkpoint | 具体 Agent 框架或外部状态能力 | Runtime 只提供状态键、上下文传递和 Redis cache 桥接接缝，不解释或接管业务 Agent checkpoint 语义 |
| 中间件服务状态 | 中间件服务自身 | Runtime 通过 engine 层代理调用，不接管服务内部状态 |

Session 与 Task 是 runtime 自己的执行状态边界；Agent checkpoint 与中间件服务状态是被执行能力或被代理能力的内部状态边界。二者不能混写。

### 2.3 Agent 执行结果语义

`AgentExecutionResult` 是 engine 层返回给 task-centric-control 的中立结果语义。

```text
AgentExecutionResult
├── OUTPUT       中间输出片段
├── COMPLETED    执行完成
├── FAILED       执行失败
└── INTERRUPTED  等待外部协作后继续，包括人工输入或远端 Agent 回灌
```

该结果语义不等同于任一具体 Agent 框架的原生输出，也不等同于 A2A 外部响应。它是 runtime 内部连接 Agent 执行与 Task 状态推进的稳定契约。

### 2.4 Handler / Adapter / Extension 抽象关系

Engine 层通过一组框架无关抽象封装异构执行能力。

```text
AgentRuntimeHandler
├── 标识一个可执行 Agent
├── 接收 AgentExecutionContext
├── 返回框架原生或中立结果流
├── 提供 start / stop 生命周期
└── 提供按 taskId 的协作式 cancel 接缝

StreamAdapter
└── 将框架原生结果转换为 AgentExecutionResult 流

MemoryProvider
└── 提供 runtime-provided memory init / search / save 窄接缝

TrajectorySource / TrajectoryEmitter / TrajectorySink
└── 提供框架中立的执行轨迹发射与导出接缝

RemoteAgentToolSpec
└── 以协议中立方式描述可作为工具使用的远端 Agent

RuntimeRedisClient
└── 以客户端中立方式描述 runtime Redis 读写能力
```

`AgentRuntimeHandler` 表示 Agent 执行入口，`StreamAdapter` 负责结果语义转换。Memory、Trajectory、RemoteAgentToolSpec 和 RuntimeRedisClient 是当前代码中的可选扩展面；它们通过中立 SPI 被框架适配器或 runtime 内部组件消费，而不是把某个 Agent 框架、A2A 协议对象或 Redis 客户端实现提升为 runtime 的唯一执行模型。

## 3. 五层逻辑架构

### 3.1 分层总览

```text
┌──────────────────────────────────────────────────────────────┐
│                        agent-runtime                          │
│                                                              │
│  access-layer                                                 │
│  请求 I/O：A2A 请求入口、响应出口、Agent Card 暴露             │
│        │                                                     │
│        ▼                                                     │
│  session-task-manager                                         │
│  会话与任务绑定、runtime 状态读写隔离                         │
│        │                                                     │
│        ▼                                                     │
│  internal-event-queue                                         │
│  事件驱动：隔离请求 I/O 与 Agent 执行                         │
│        │                                                     │
│        ▼                                                     │
│  task-centric-control                                         │
│  任务中心化控制、Task 状态机推进                              │
│        │                                                     │
│        ▼                                                     │
│  engine                                                       │
│  执行层封装：Agent 框架适配与中间件服务代理                   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

五层架构对应 runtime 内部五个逻辑责任面。它们不是单纯的代码目录分组，而是围绕 Task 生命周期拥有语义形成的职责边界。

### 3.2 access-layer：请求 I/O

`access-layer` 是 runtime 的外部 I/O 边界，负责接收 A2A 请求、暴露 Agent Card，并把外部协议对象交给 runtime 内部控制面处理。

该层的逻辑职责包括：

- 接收 JSON-RPC over HTTP 请求。
- 暴露 Agent 发现端点。
- 维持外部协议入口和内部执行语义之间的转换边界。
- 将协议请求交给 task-centric-control，不直接执行业务 Agent。

`access-layer` 可以识别 A2A 协议语义，但协议对象不能穿透到框架适配器和中立 SPI。

### 3.3 session-task-manager：会话任务绑定与状态读写隔离

`session-task-manager` 管理 runtime 会话域和任务域之间的绑定关系，是 Task/Session 状态读写的逻辑边界。

该层的逻辑职责包括：

- 建立 Session 与 Task 的关联范围。
- 保存 runtime Task 状态和查询视图。
- 将状态读写与 Agent 执行逻辑隔离。
- 支撑同步查询、订阅、取消等控制路径读取一致的 Task 状态。

该层只管理 runtime 自身的 Task/Session 状态，不解释或接管业务 Agent checkpoint，也不接管中间件服务内部状态。FEAT-003 Redis-backed TaskStore 只改变 Task 状态的物理存储实现，不改变该逻辑边界。

### 3.4 internal-event-queue：I/O 与执行的异步隔离

`internal-event-queue` 是 runtime 内部事件驱动责任面，负责隔离请求 I/O 与 Agent 执行。

该层的逻辑职责包括：

- 承接由请求入口产生的执行事件。
- 将外部请求线程与 Agent 执行线程解耦。
- 为流式输出、状态推进和订阅响应提供事件传播基础。
- 保证 runtime 控制面围绕事件处理，而不是让接入层直接驱动执行层。

事件队列本身不拥有业务 Agent 语义。它承载 runtime 内部的执行事件、状态事件和输出事件，使 Task 生命周期推进具备异步隔离边界。

### 3.5 task-centric-control：任务中心化与状态机

`task-centric-control` 是 runtime Task 生命周期拥有语义的中心控制面。所有执行都围绕 Task 生命周期收敛。

该层的逻辑职责包括：

- 将外部消息发送、任务查询、取消、订阅等请求统一落到 Task 语义。
- 创建和推进 Task 状态。
- 根据 Agent 执行结果更新 Task。
- 以 Task 为中心连接 session-task-manager、internal-event-queue 和 engine。

该层不暴露某个 Agent 框架的原生状态，也不绕过 Task 状态机直接向外部返回执行过程。

### 3.6 engine：智能体与中间件服务代理封装

`engine` 是 runtime 的执行封装层，负责把异构 Agent 框架和中间件服务代理能力统一为 runtime 可消费的执行语义。

该层的逻辑职责包括：

- 通过 `AgentRuntimeHandler` 接入具体 Agent。
- 通过 `StreamAdapter` 将框架原生输出转换为 `AgentExecutionResult`。
- 通过 `MemoryProvider`、Trajectory SPI、`RemoteAgentToolSpec` 和 `RuntimeRedisClient` 暴露 memory、观测、远端 Agent 工具和 runtime Redis 操作等可选扩展面。
- 隔离 openJiuwen、AgentScope 等框架差异。
- 代理调用 memory、trajectory、remote Agent 等中间件服务能力，但不接管这些服务的内部状态。

Engine 层是框架差异和 runtime Task 语义之间的边界。框架适配器可以依赖自身框架 SDK，但不能把协议接入模型或上层 service 模型带入中立 SPI。

## 4. 状态模型

### 4.1 Task 状态机

Task 是 runtime 的执行状态单元。当前 Task 状态由 A2A SDK 管理，runtime 以该状态机承载 Task 生命周期拥有语义；FEAT-003 Redis-backed TaskStore 只替换状态存储介质，不改变 Task 状态机。

```text
SUBMITTED ──▶ WORKING ──▶ COMPLETED
   │             │
   └──▶ REJECTED │
                 ├──▶ FAILED
                 │
                 ├──▶ CANCELED
                 │
                 └──▶ INPUT_REQUIRED ──▶ WORKING
```

状态含义：

| 状态 | 逻辑含义 |
|---|---|
| SUBMITTED | 请求已进入 runtime，并形成 Task |
| WORKING | Task 正在被 runtime 推进执行 |
| COMPLETED | Agent 执行完成，Task 正常结束 |
| FAILED | 执行失败，Task 以错误结束 |
| CANCELED | 外部取消或 runtime 取消逻辑使 Task 终止 |
| INPUT_REQUIRED | Agent 执行被中断，等待外部协作后继续，包括人工输入或远端 Agent 回灌 |
| REJECTED | Task 已创建但未进入 WORKING，通常由未注册 handler 或 runtime 未 ready 的前置拒绝触发 |

### 4.2 AgentExecutionResult 到 Task 状态的映射

Agent 执行结果驱动 Task 状态推进。

| AgentExecutionResult | Task 状态影响 | 逻辑含义 |
|---|---|---|
| OUTPUT | 保持 WORKING | 产生中间输出，Task 尚未结束 |
| COMPLETED | 推进到 COMPLETED | Agent 执行完成 |
| FAILED | 推进到 FAILED | Agent 执行失败 |
| INTERRUPTED + UserInputInterrupt | 推进到 INPUT_REQUIRED | 需要外部人工输入后恢复执行 |
| INTERRUPTED + RemoteAgentInterrupt | 交给远端调用编排；远端需要输入时推进到 INPUT_REQUIRED，远端完成后以 REMOTE_RESUME 重新进入本地 handler | 需要远端 Agent 调用并回灌后继续 |

该映射是 engine 与 task-centric-control 之间的核心逻辑契约。框架原生输出必须先转换为 `AgentExecutionResult`，再进入 Task 状态语义。

### 4.3 Runtime State 与 Agent Checkpoint 边界

Runtime state 与 Agent checkpoint 分属不同状态域。

```text
Runtime state
├── Session
├── Task
├── Task status
├── Task output / event view
└── Runtime execution context

Agent checkpoint
├── Agent memory
├── Agent framework checkpoint
├── Tool / middleware local state
└── Business-specific execution state
```

`agent-runtime` 对 runtime state 负责。FEAT-003 可以把 runtime Task 状态写入 Redis-backed TaskStore；对 Agent checkpoint，runtime 只提供状态键、执行上下文和 Redis cache 桥接接缝，不解释业务 checkpoint payload。Agent checkpoint 的存储、恢复和一致性由具体 Agent 框架或外部状态服务承担。

## 5. 逻辑依赖方向

### 5.1 协议隔离方向

A2A 是当前 runtime 的外部协议入口，但不是 engine SPI 的领域模型。

```text
access-layer
    ↓ consumes protocol objects
task-centric-control
    ↓ consumes neutral execution contract
engine
    ↓ consumes framework-specific SDKs behind adapters
Agent frameworks
```

协议对象允许存在于接入与协议桥接边界内；中立执行上下文、执行结果和 handler/provider/adapter 抽象不得依赖 A2A 协议对象。

### 5.2 框架适配隔离方向

框架适配器依赖 runtime 中立 SPI，并向 runtime 返回中立结果语义。

```text
engine.spi  ←  engine.openjiuwen
engine.spi  ←  engine.agentscope
engine.spi  ←  future framework adapters
```

适配器可以理解具体框架的输入、输出、流式事件、checkpoint 和工具调用模型，但这些差异必须被吸收在 adapter/provider 内部。

### 5.3 状态读写边界

Runtime Task/Session 状态读写集中在 session-task-manager 与 task-centric-control 的职责范围内。

```text
access-layer
    ↓ request intent
task-centric-control
    ↓ state transition intent
session-task-manager
    ↓ state read/write
runtime Task/Session state
```

状态存储实现可以是默认 InMemory，也可以是 FEAT-003 Redis-backed TaskStore；状态推进入口仍集中在 runtime 控制面。Engine 层不直接拥有 Task 状态机。Agent 框架不直接写 runtime Task 状态。外部请求也不绕过 task-centric-control 修改 Task 生命周期。

### 5.4 Service / Runtime / Bus 模块边界

`agent-runtime` 在 L0 边界中处于可嵌入 runtime SDK 位置。

```text
agent-service
    ↓ may embed
agent-runtime
    ↓ may align with / map neutral vocabulary, without compile dependency
agent-bus
```

`agent-service` 可以集成 `agent-runtime` 作为执行运行时；`agent-runtime` 不依赖 `agent-service`。跨实例、跨部门、跨数据边界的 A2A 总线治理不属于 `agent-runtime` 的逻辑边界。
