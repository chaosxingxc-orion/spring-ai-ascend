---
level: L1
view: architecture-overview
module: agent-runtime
status: implemented
freeze_id: null
covers_views: [logical, development, process, physical]
spans_levels: [L1]
authority: "ADR-0159 (agent-runtime consolidation + agent-service serviceization refounding)"
---

# agent-runtime — L1 架构总览

> 所属团队: AgentRuntime | 平面: compute_control | 成熟度: 已实现（基于开源 A2A SDK）

## 状态

`agent-runtime` 是 **run-owning runtime SDK**：一个自包含、可独立启动的运行时，开发者通过集成它来驱动基于异构 Agent 框架构建的 Agent 实例。根据 **ADR-0159**，它合并了原 `agent-execution-engine`（其模块身份已解散）与原 `agent-service` 的运行时内部组件——除了 serviceization 外观层保留在 `agent-service` 中。包根路径为 `com.huawei.ascend.runtime.*`。

当前实现以开源 **A2A SDK**（`org.a2aproject.sdk`）替代了原自定义的 5 层实现，但逻辑上仍然遵循 5 层架构模型。A2A SDK 提供了 JSON-RPC 协议接入、任务存储、事件总线、队列管理等基础设施，`agent-runtime` 在其上构建了框架无关的 Agent 执行 SPI 和具体的 Agent 框架适配器。

## 0.5 Canonical L1 4+1 View 源文件

agent-runtime 的 4+1 视图由 5 个 per-view 文件 + 1 个 SPI 附录组成（与 agent-service 的 per-view 机制对齐，ADR-0152）：

- **架构总览（本文）:** [`ARCHITECTURE.md`](ARCHITECTURE.md) — 模块目的、组件清单、5 层架构、依赖、公共契约、测试、范围
- **逻辑视图:** [`logical.md`](logical.md) — 领域模型、内部划分、5 层层间交互
- **物理视图:** [`physical.md`](physical.md) — 部署平面、拓扑、资源模型
- **进程视图:** [`process.md`](process.md) — 线程模型、同步/异步边界、执行时序
- **开发视图:** [`development.md`](development.md) — 包结构树、模块依赖图、SPI 设计原则、自动装配
- **SPI 附录:** [`spi-appendix.md`](spi-appendix.md) — 5 个 SPI 接口 + 3 个辅助类的完整契约 + A2A SDK 消费的 10 个外部接口

## 1. Purpose

`agent-runtime` 是 **run-owning runtime kernel**：提供可嵌入的 Agent 执行运行时，通过框架无关的 SPI（`AgentRuntimeHandler`）驱动异构 Agent 框架（openJiuwen、AgentScope 等）构建的 Agent 实例。它拥有：

- **框架无关的运行时 SPI**：`AgentRuntimeHandler` 是引擎与 Agent 框架之间的唯一解耦面
- **A2A 协议接入**：通过开源 A2A SDK 实现 JSON-RPC over HTTP 接入
- **Task 生命周期管理**：由 A2A SDK 提供的 task 状态机
- **内部事件队列**：A2A SDK 提供的异步事件传递基础设施
- **可嵌入的启动入口**：`RuntimeApp` 纯 Java API + Spring Boot 自动装配两种方式

## 2. Shipped components

> 路径约定：所有 Java 路径根为 `agent-runtime/src/main/java/com/huawei/ascend/runtime/...`。中立编排/引擎 SPI 位于 `agent-bus`（`bus.spi.engine`，ADR-0158）。

### 2.A 引擎 SPI 层（`runtime.engine.spi`）

框架无关的运行时 SPI 接口，零外部依赖（仅 `java.*` + `bus.spi.engine` 中立载体）：

| 类型 | 种类 | 语义 | 状态 |
|---|---|---|---|
| `AgentRuntimeHandler` | interface | Agent 执行 SPI：`agentId()` / `isHealthy()` / `execute()` / `resultAdapter()` | shipped |
| `AgentRuntimeProvider` | interface | 可选生命周期钩子：`beforeExecute()` / `afterExecute()` | shipped |
| `AgentRuntimeProviderChain` | final class | Handler + providers 编排 + 失败隔离 | shipped |
| `AgentExecutionResult` | final class | 4 种结果类型：OUTPUT / COMPLETED / FAILED / INTERRUPTED | shipped |
| `StreamAdapter` | @FunctionalInterface | 框架结果 → 中立结果流的转换器 | shipped |
| `AgentCardProvider` | interface | A2A Agent Card 供应接口 | shipped |
| `AgentCards` | final class | 默认 Agent Card 工厂方法 | shipped |
| `StateProvider` | interface | 继承 AgentRuntimeProvider，框架手动状态桥接标记 | shipped |

### 2.B A2A SDK 桥接层（`runtime.engine.a2a`）

| 类型 | 种类 | 语义 | 状态 |
|---|---|---|---|
| `A2aAgentExecutor` | final class | 实现 A2A SDK 的 `AgentExecutor`，将 A2A 协议桥接到 `AgentRuntimeHandler` SPI | shipped |

### 2.C Agent 框架适配器

#### openJiuwen（`runtime.engine.openjiuwen`）

| 类型 | 语义 | 状态 |
|---|---|---|
| `OpenJiuwenAgentRuntimeHandler` | 抽象基类，提供 runtime 侧的 agentId + 输入/结果映射辅助方法 | shipped |
| `OpenJiuwenMessageAdapter` | `AgentExecutionContext` → openJiuwen 输入（query + conversation_id） | shipped |
| `OpenJiuwenStreamAdapter` | openJiuwen 结果（answer/error/interrupt） → `AgentExecutionResult` | shipped |

#### AgentScope（`runtime.engine.agentscope`）

| 类型 | 语义 | 状态 |
|---|---|---|
| `AbstractAgentScopeRuntimeHandler` | 抽象基类 | shipped |
| `AgentScopeAgentRuntimeHandler` | 本地 AgentScope Agent 处理器 | shipped |
| `AgentScopeRuntimeClientHandler` | 远程 AgentScope runtime client 处理器 | shipped |
| `AgentScopeHarnessRuntimeHandler` | AgentScope Harness 处理器 | shipped |
| `AgentScopeStreamAdapter` | AgentScope 结果 → `AgentExecutionResult` | shipped |
| `AgentScopeMessageAdapter` | `AgentExecutionContext` → AgentScope 调用输入 | shipped |

### 2.D Engine 服务（`runtime.engine.service`）

> 注意：以下服务是 engine 层为远端 A2A agent 调用提供的支撑能力，不是 agent-runtime 自身 5 层架构的组成部分。

| 类型 | 语义 | 状态 |
|---|---|---|
| `RemoteAgentCatalog` | 远端 runtime 的 agent-card 目录：按部署配置的 URL 解析/刷新远端卡片，暴露可用工具规格 | shipped |
| `RemoteAgentInvocationService` | 经 outbound adapter 调用远端 agent 的服务入口 | shipped |

### 2.E 接入与启动层

#### Spring Boot 自动配置（`runtime.boot`）

| 类型 | 语义 | 状态 |
|---|---|---|
| `RuntimeAutoConfiguration` | 装配 A2A SDK 组件（TaskStore / EventBus / QueueManager / RequestHandler / AgentExecutor）+ AgentCard bean | shipped |
| `A2aJsonRpcController` | `/a2a` JSON-RPC 端点（SendMessage / GetTask / CancelTask / Streaming / Subscribe） | shipped |
| `AgentCardController` | `/.well-known/agent-card.json` + `/.well-known/agent.json` 端点 | shipped |

#### 可嵌入启动入口（`runtime.app`）

| 类型 | 语义 | 状态 |
|---|---|---|
| `RuntimeApp` | 纯 Java 入口：`RuntimeApp.create(handler).run(host)` | shipped |
| `RuntimeHost` | 框架无关的 runtime host SPI | shipped |
| `LocalA2aRuntimeHost` | Spring Boot host 实现（唯一的 Spring 依赖点） | shipped |
| `RunningRuntime` | 运行时句柄，支持 try-with-resources | shipped |
| `RuntimeComponents` | 组件容器 record（holder for handler + future service injections） | shipped |

#### 共享类型（`runtime.common`）

| 类型 | 语义 | 状态 |
|---|---|---|
| `RuntimeIdentity` | record(tenantId, userId, sessionId, taskId, agentId) | shipped |

### 2.F A2A SDK 提供的组件（非 agent-runtime 拥有，由 RuntimeAutoConfiguration 装配）

| 组件 | 来源 | 角色 | 对应 5 层 |
|---|---|---|---|
| `InMemoryTaskStore` | A2A SDK | Task 对象 CRUD 存储 | session-task-manager |
| `InMemoryPushNotificationConfigStore` | A2A SDK | 推送通知配置存储 | session-task-manager |
| `MainEventBus` | A2A SDK | 内部事件发布-订阅总线 | internal-event-queue |
| `InMemoryQueueManager` | A2A SDK | 任务队列管理器 | internal-event-queue |
| `MainEventBusProcessor` | A2A SDK | 后台线程消费事件 → 推动状态机 → 调用 AgentExecutor | internal-event-queue / task-centric-control |
| `DefaultRequestHandler` | A2A SDK | 处理所有 A2A 请求，管理 Task 生命周期 | task-centric-control |

## 3. Sub-package layering invariant

**`runtime.app` 不依赖 Spring Boot**（仅 `LocalA2aRuntimeHost` 例外，作为唯一的 Spring 实现）。**`runtime.engine.spi` 不依赖 Spring、Micrometer、OTel 或任何参考实现**，由 `SpiPurityGeneralizedArchTest`（E48）强制执行。

`agent-runtime → agent-bus`（消费中立 `bus.spi.engine` 词汇）。绝不允许 `agent-runtime → agent-service`。`agent-service → agent-runtime` 是唯一合法的跨模块边（Rule 10 / ArchUnit）。

## 4. OSS dependencies

| 依赖 | 角色 | 侧 |
|---|---|---|
| A2A SDK (`org.a2aproject.sdk`) | JSON-RPC 协议、Task 存储、事件总线、队列管理、RequestHandler | 核心 |
| Spring Boot (starter-web) | HTTP 服务器（仅 `LocalA2aRuntimeHost` + `boot/`） | 接入 |
| Spring Boot AutoConfiguration | SPI 机制用于自动装配 | 接入 |
| Spring Security (optional) | 由 A2A SDK 消费 | 接入 |
| Reactor (WebFlux) | SSE 流式应答的 Flux 桥接 | 接入 |
| Jackson | JSON 序列化 | 接入 |
| SLF4J | 日志（结构化 key=value 格式） | 全局 |
| agent-bus | 中立 EnginePort / 编排 SPI 词汇 | 核心 |

## 5. Public contract

- **A2A 协议**: JSON-RPC over HTTP at `/a2a`（由 A2A SDK 的 `RequestHandler` 提供标准实现）
  - `SendMessage` — 同步发送消息（非流式）
  - `SendStreamingMessage` — 流式发送消息
  - `GetTask` — 查询 Task 状态
  - `CancelTask` — 取消 Task
  - `SubscribeToTask` — 订阅 Task 事件流
- **Agent Card 发现**: `/.well-known/agent-card.json`（GET → `AgentCard` JSON）
- **Agent Card 兼容路径**: `/.well-known/agent.json`
- **SPI**: `AgentRuntimeHandler` 是业务方必须实现的唯一接口
  - `agentId()` — 返回此 handler 服务的唯一 Agent ID
  - `execute(AgentExecutionContext)` — 执行 Agent，返回框架原生结果流
  - `resultAdapter()` — 返回 `StreamAdapter`，将框架结果映射为 `AgentExecutionResult`
- **Agent State**: 会话/checkpoint 状态委托各框架自身的 checkpointer（如 openJiuwen 的 `conversation_id`），runtime 不提供状态存储
- **Agent Card 自定义**: 可选实现 `AgentCardProvider` 自定义元数据，否则自动从 `AgentRuntimeHandler.agentId()` 生成默认 Card

## 6. 5 层逻辑架构

当前实现通过开源 A2A SDK 达成了以下 5 层架构。

### 6.1 access-layer（接入层）

**对应代码**：`runtime.boot.A2aJsonRpcController` + `runtime.boot.AgentCardController` + A2A SDK 的 `RequestHandler` / `DefaultRequestHandler`

**职责**：
- 对外接入与用户可见输出通道。当前只实现 A2A 接入（JSON-RPC over HTTP），收到请求后转换为服务内部的标准对象（`AgentExecutionContext`）
- 对外暴露 `/.well-known/agent-card.json` 用于 Agent 发现
- 将请求任务通过 task-centric-control 暴露的 API 插入异步任务队列，等待 task 分发处理
- 内部为每个请求独立维护一条数据队列（由 A2A SDK 的 `InMemoryQueueManager` + `MainEventBus` 提供），订阅队列数据，将内部数据转换为外部应答格式
- 维护每次请求的接入和退出状态，定义通用事件（`OUTPUT` / `COMPLETED` / `FAILED` / `INTERRUPTED`），保证用户请求正常响应、资源无泄漏、无异常

**扩展设计原则**：接入层定义了内部数据标准（`RuntimeIdentity` + `AgentExecutionContext`），允许扩展多种不同入口数据转换（当前只实现 A2A 默认最小集）。应答格式抽象允许用户自定义适配层，当前只实现一种默认最小集（JSON-RPC streaming/non-streaming）。

### 6.2 session-task-manager（会话-任务管理器）

**对应代码**：A2A SDK 的 `InMemoryTaskStore` + `InMemoryPushNotificationConfigStore` + `InMemoryQueueManager`（由 `RuntimeAutoConfiguration` 装配）

**职责**：
- 作为 agent-runtime 自身的**会话与任务数据管理层**，负责 Task 对象生命周期中涉及的存储和队列管理
- **Session 管理**：A2A SDK 内部的 `contextId`（即 `sessionId`）作为会话标识，在执行上下文中贯穿整个 Task 生命周期。会话作为业务数据的"集散地"，当前实现为 InMemory，设计上预留了分布式扩展点——未来如需分布式部署，只需将 A2A SDK 的 Storage 实现替换为分布式版本
- **Task 存储**：`InMemoryTaskStore` 负责 Task 的 CRUD 操作（由 A2A SDK 提供标准实现）
- **推送通知配置存储**：`InMemoryPushNotificationConfigStore` 负责推送通知配置的持久化
- **队列管理**：`InMemoryQueueManager` 管理 Task 与事件的队列关系

**区分：业务 Agent 自身的 checkpoint 状态**

> 业务 Agent 实例层面的 state（执行过程中的 checkpoint 数据）**不属于** agent-runtime 的 session-task-manager 层，也不由 runtime 持久化——它委托各框架自身的 checkpointer（如 openJiuwen 的 `conversation_id`）。session-task-manager（A2A SDK）只管理"runtime 层面的 Task/Session"——Task 状态机、Task 与事件队列的关系。

**设计原则**：A2A SDK 提供了标准的 `TaskStore` 和 `QueueManager` 接口，允许替换 InMemory 实现为 Redis、JDBC 等分布式实现。通过接口隔离，agent-runtime 自身的会话管理层对扩展开放、对修改关闭。

### 6.3 internal-event-queue（内部事件队列）

**对应代码**：A2A SDK 的 `MainEventBus` + `InMemoryQueueManager` + `MainEventBusProcessor`（由 `RuntimeAutoConfiguration` 装配）

**职责**：
- 负责服务内部异步事件传递的基础设施
- 它是一个**无业务语义**的事件队列，提供基础的发布-订阅和队列管理能力，供业务模块（task-centric-control）实例化使用
- `MainEventBus` 作为事件总线，提供 publish / subscribe 的基础能力
- `InMemoryQueueManager` 管理 Task 与事件队列的映射关系
- `MainEventBusProcessor` 在后台线程（`Executor`）消费事件，驱动后续的状态推进和 engine 调用
- `RuntimeAutoConfiguration` 作为工厂类和管理类装配这些组件，增强维护能力

### 6.4 task-centric-control（任务中心控制层）

**对应代码**：A2A SDK 的 `DefaultRequestHandler` + `MainEventBusProcessor`（由 `RuntimeAutoConfiguration` 装配）

**职责**：
- 负责任务生命周期控制、状态机推进（SUBMITTED → WORKING → COMPLETED / FAILED / CANCELED，以及 INPUT_REQUIRED 中断/恢复）、父子 task 编排、中断/恢复/取消等控制逻辑
- 暴露 API 供 access-layer 调用（`RequestHandler` 的 `onMessageSend` / `onMessageSendStream` / `onGetTask` / `onCancelTask` / `onSubscribeToTask`）
- 模块内部维护 task queue 实例（`InMemoryQueueManager`），提供的 API 方便调用者塞入 task event，自身侦听 event 处理 task 状态跃迁
- `MainEventBusProcessor` 在后台消费事件：识别需要执行 engine 的事件 → 调用 `AgentExecutor.execute(ctx, emitter)`
- A2A SDK 的 `DefaultRequestHandler` 实现了完整的 task 生命周期管理

**状态机**：
```
SUBMITTED ──▶ WORKING ──▶ COMPLETED
                 │
                 ├──▶ FAILED
                 │
                 ├──▶ CANCELED
                 │
                 └──▶ INPUT_REQUIRED ──▶ WORKING (resume)
```

### 6.5 engine（引擎层）

**对应代码**：`runtime.engine.spi.AgentRuntimeHandler` + `runtime.engine.a2a.A2aAgentExecutor` + `runtime.engine.openjiuwen.*` + `runtime.engine.agentscope.*` + `runtime.engine.service.*`

**职责**：
- 负责 Agent 执行调度与具体 Agent 框架适配
- 接收 task-centric-control 的最小执行请求（通过 `A2aAgentExecutor.execute(ctx, emitter)`），入队后消费
- 目前实现 openJiuwen 和 AgentScope 两种适配器（最小实现，后续可按需扩展）
- `AgentRuntimeHandler` SPI 是框架与引擎之间的唯一接口：`agentId()` / `isHealthy()` / `execute(AgentExecutionContext)` / `resultAdapter()`
- `AgentRuntimeProvider` 提供可选的生命周期钩子（`beforeExecute` / `afterExecute`），支持 Agent State 恢复/导出、沙箱准备、工具调用覆盖等组合能力
- `AgentRuntimeProviderChain` 统一执行 handler + providers 的编排和失败隔离
- **engine 服务**（`runtime.engine.service`）：`RemoteAgentCatalog` + `RemoteAgentInvocationService` 支撑把部署配置声明的远端 A2A agent 作为工具调用。Agent 自身的 checkpoint 状态委托各框架的 checkpointer，runtime 不提供状态持久化

**数据流**：access-layer 接入 → task（task-centric-control 管理状态）→ engine（AgentRuntimeHandler 执行）→ 结果流经 A2A SDK 回到 access-layer 应答

## 7. 框架无关的 SPI 设计

### AgentRuntimeHandler

引擎与具体 Agent 框架之间的唯一解耦面：

```java
public interface AgentRuntimeHandler {
    String agentId();          // 此 handler 服务的 agent ID
    boolean isHealthy();       // 健康检查
    Stream<?> execute(AgentExecutionContext context);  // 执行 agent
    default List<AgentRuntimeProvider> providers() { return List.of(); }  // 可选生命周期钩子
    StreamAdapter resultAdapter();  // 结果适配器
}
```

### AgentExecutionResult

中立的执行结果类型，五种语义：
- `OUTPUT` — 中间输出（流式输出的一个片段）
- `COMPLETED` — 最终完成（可带最终输出文本）
- `FAILED` — 执行失败（带 errorCode + errorMessage）
- `INTERRUPTED` — 需要人工输入（带 prompt 提示文本）
- `REMOTE_INVOCATION` — 请求调用远端 A2A agent（由 remote 调用编排消费）

### AgentRuntimeProvider

可组合的生命周期钩子，替代深层继承：
- `beforeExecute(context)` — 在 handler 执行前准备上下文
- `afterExecute(context)` — 在结果流结束后导出副作用

### StreamAdapter

函数式接口，将框架原生结果流映射为 `Stream<AgentExecutionResult>`。

## 8. 启动与嵌入

`agent-runtime` 提供两级入口：

**纯 Java 嵌入**（无 Spring 依赖）：
```java
try (RunningRuntime runtime = RuntimeApp.create(handler).run(LocalA2aRuntimeHost.port(8080))) {
    // 在 runtime.port() 上提供 A2A 服务
}
```

**Spring Boot 自动装配**：
`RuntimeAutoConfiguration` 通过 `META-INF/spring/...AutoConfiguration.imports` 自动装配所有 A2A SDK 组件：`InMemoryTaskStore`、`MainEventBus`、`QueueManager`、`DefaultRequestHandler`、`A2aAgentExecutor` 等。业务方只需提供一个 `AgentRuntimeHandler` Bean（和可选的 `AgentCardProvider`），运行时即自动可用。

## 9. 测试

### L1 shipped tests（全部绿色）

| 测试 | 层 | 断言 | 侧 |
|---|---|---|---|
| `RuntimeAppTest` | Unit | `RuntimeApp.create(handler).run(host)` 启动和关闭 | app |
| `A2aJsonRpcControllerTest` | Unit | JSON-RPC 端点请求解析和响应 | boot |
| `AgentRuntimeProviderChainTest` | Unit | Provider 编排顺序、before/after 执行、异常隔离 | engine.spi |
| `SuspendSignalLibraryTest` | Unit | agent-bus SuspendSignal 类型可用性 | engine |
| `SpanTenantAttributeRequiredTest` | ArchUnit | span 声明 tenant.id 属性（预埋 W2） | architecture |
| `RunRepositorySaveGuardTest` | ArchUnit | Run 存储访问守卫 | architecture |
| `RunContextIdentityAccessorsTest` | ArchUnit | RunContext 暴露 traceId/spanId/sessionId | architecture |
| `AudienceBExtensionSeamsArchTest` | ArchUnit | Audience B 扩展接缝检查 | architecture |
| `RuntimePackageBoundaryTest` | ArchUnit | 包边界纯度检查 | architecture |

## 10. Out of scope at L1

- **Run record / 恢复 / 幂等**：权威 `Run` 记录、崩溃恢复与幂等派发归属 `agent-service`（design target，尚未实现）；模块边界叙述见 [`agent-runtime/README.md`](../../../../agent-runtime/README.md) 的 "What runtime does NOT own" 段
- **分布式 Task/Session 存储**：当前 InMemory 实现。Redis、JDBC 替换为未来扩展方向
- **多协议接入**：当前仅 A2A JSON-RPC。gRPC、WebSocket 等协议接入为未来扩展方向
- **Agent 框架适配器扩展**：当前仅 openJiuwen + AgentScope。其他 Agent 框架适配器按需添加
- **Telemetry / Metrics**：当前仅 SLF4J 结构化日志。Micrometer 指标 + OTel 追踪为未来扩展方向
- **沙箱 / 工具覆盖 / 安全策略**：通过 `AgentRuntimeProvider` SPI 预留了扩展点，具体实现未包含

## 11. 部署位面

`deployment_loci: [platform_centric, business_centric]` — 运行时与部署位置无关，同一套 engine envelope 支持两种位面。

---

## 新贡献者阅读顺序

1. `module-metadata.yaml` — 模块身份与依赖承诺
2. `runtime.engine.spi.AgentRuntimeHandler` — 框架无关的运行时 SPI
3. `runtime.engine.a2a.A2aAgentExecutor` — A2A SDK 桥接实现
4. `runtime.app.RuntimeApp` / `LocalA2aRuntimeHost` — 可启动入口
5. `runtime.boot.RuntimeAutoConfiguration` — Spring Boot 自动装配
6. ADR-0159 — 整合与重定位权威
7. `docs/dfx/agent-runtime.yaml` — Design-for-X 声明
