---
level: L1-HLD
TAG:
  - physical-view
  - deployment-boundary
  - runtime-topology
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - logical.md
  - development.md
  - process.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-runtime L1 架构物理视图

## 1. 物理视图定位

本文档描述 `agent-runtime` 当前 active 架构下的部署归属、宿主进程关系、单实例物理拓扑、状态存储与资源模型。

物理视图回答以下问题：

- `agent-runtime` 运行在哪类宿主进程中。
- Runtime 作为 service 聚合模块时，与业务应用或独立宿主进程如何组合。
- 当前单实例 topology 中 HTTP 接入、A2A SDK 组件、Agent 执行适配器和外部依赖位于哪里。
- 当前默认 InMemory 状态与可选 Redis-backed Task 状态缓存对重启、故障和横向扩展有什么约束。
- 当前线程、内存、并发和关闭排水模型是什么。

本文档只描述 active 架构现状。

### 1.1 当前 active 物理形态

`agent-runtime` 当前通过 `service/agent-service-*` 聚合模块提供 Spring Boot 自动装配、A2A controller、执行 SPI 和框架适配器，可嵌入宿主 JVM 进程或由专门 host application 启动。

当前 active 物理形态可以概括为：

```text
Host JVM process
  -> service/agent-service-app
  -> embedded HTTP server provided by host Spring Boot application
  -> in-process A2A SDK runtime components
  -> optional Redis-backed TaskStore
  -> in-process AgentHandler adapters
```

### 1.2 与其他视图的边界

| 视图 | 关注点 |
|---|---|
| `logical.md` | 领域对象、状态归属、逻辑责任面和依赖方向。 |
| `development.md` | Maven artifact、包结构、自动配置、SPI 扩展和代码边界。 |
| `process.md` | 请求执行流程、异步边界、Task 状态推进和并发执行路径。 |
| `physical.md` | 宿主进程、部署形态、单实例 topology、进程内状态和资源约束。 |

## 2. 部署归属与运行形态

### 2.1 Library artifact 与宿主进程关系

`agent-runtime` 的物理部署单元由宿主应用或专门 host application 承载，核心能力来自 `service/agent-service-app`、`agent-service-spec` 和 adapter 模块。

| 物理项 | 当前 active 事实 |
|---|---|
| Artifact 形态 | service 聚合模块提供的 Spring Boot 自动装配与公共 SPI。 |
| 进程所有者 | 宿主 Spring Boot 应用或纯 Java host。 |
| HTTP server | 由宿主应用提供，通常是 Tomcat 或 Netty。 |
| Runtime 启动 | 通过 Spring Boot 自动配置装配。 |
| Runtime 状态 | 默认保存在进程内 InMemory A2A SDK 组件中；启用 FEAT-003 后，Task 状态可通过 Redis-backed TaskStore 写入 Redis。 |

### 2.2 嵌入式部署

嵌入式部署是当前最直接的物理形态。业务应用把 `agent-runtime` 作为依赖引入，在同一个 JVM 内暴露 A2A 接入和 Agent Card 发现能力。

```text
Business Application JVM
  -> business controllers / services
  -> service/agent-service-app
     -> /a2a
     -> Agent Card endpoint
     -> A2A SDK in-memory components
     -> AgentHandler adapters
```

这种形态下，业务应用和 runtime 共用同一 JVM、同一进程生命周期、同一资源预算和同一部署单元。

### 2.3 独立宿主部署

独立宿主部署指使用一个专门的 host application 包装 `agent-runtime`，使该进程主要作为 A2A Agent 节点对外暴露。

```text
Dedicated Host JVM
  -> Spring Boot host application
  -> service/agent-service-app
  -> /a2a JSON-RPC endpoint
  -> Agent Card endpoint
```

这里的“独立”来自宿主应用的部署选择，不表示 `agent-runtime` 当前必须产出单独的可执行 fat jar。

### 2.4 混合式部署

混合式部署指同一个宿主进程既承担业务服务职责，也通过 `agent-runtime` 对外暴露 A2A Agent 能力。

| 职责 | 所在位置 |
|---|---|
| 业务 HTTP/API | 宿主应用自身。 |
| A2A JSON-RPC | `agent-service-app` 自动配置或 host 装配。 |
| Agent 执行 | 同进程 `AgentHandler` 适配器。 |
| Task / Event 状态 | 默认同进程 InMemory A2A SDK 组件；Task 状态可由 Redis-backed TaskStore 持久化，事件队列和 SSE 连接仍属于同进程组件。 |

混合式部署需要由宿主应用统一治理端口、线程、内存、限流、鉴权和生命周期。

## 3. 单实例物理拓扑

### 3.1 进程边界

当前 active topology 是单 JVM、单 runtime 实例、默认进程内 InMemory 状态；FEAT-003 允许 Task 状态切换为 Redis-backed 缓存。

```text
+------------------------------------------------+
| Host JVM process                               |
|                                                |
|  +------------------------------------------+  |
|  | Spring Boot host / agent-service-app     |  |
|  |                                          |  |
|  |  +------------------------------------+  |  |
|  |  | A2A JSON-RPC Controller (/a2a)     |  |  |
|  |  | Agent Card endpoint                |  |  |
|  |  +----------------+-------------------+  |  |
|  |                   |                      |  |
|  |  +----------------v-------------------+  |  |
|  |  | A2A SDK server components          |  |  |
|  |  | RequestHandler                     |  |  |
|  |  | TaskStore (InMemory / Redis-backed)|  |  |
|  |  | InMemoryQueueManager               |  |  |
|  |  | MainEventBus + Processor           |  |  |
|  |  +----------------+-------------------+  |  |
|  |                   |                      |  |
|  |  +----------------v-------------------+  |  |
|  |  | A2aAgentExecutor                   |  |  |
|  |  +----------------+-------------------+  |  |
|  |                   |                      |  |
|  |  +----------------v-------------------+  |  |
|  |  | AgentHandler SPI adapters          |  |  |
|  |  | openJiuwen / AgentCore / others    |  |  |
|  |  +------------------------------------+  |  |
|  +------------------------------------------+  |
|                                                |
|  External calls through configured adapters     |
+------------------------------------------------+
```

### 3.2 HTTP 接入端点

当前 runtime 在宿主 HTTP server 上暴露 A2A northbound 接入和 Agent Card 发现能力。

| Endpoint | 用途 | 说明 |
|---|---|---|
| `/a2a` | A2A JSON-RPC over HTTP | 处理 SendMessage、SendStreamingMessage、GetTask 等当前入口范围内的 A2A 请求。 |
| Agent Card endpoint | Agent 元数据发现 | 由当前 Agent Card controller 暴露，具体路径遵循实现中的 A2A Agent Card 发现约定。 |

HTTP server 的端口、TLS、反向代理、鉴权入口和网络策略由宿主应用或部署环境负责，不由 `agent-runtime` library 单独拥有。

### 3.3 进程内组件分布

```text
A2A Client
  -> Host HTTP Server
     -> A2A JSON-RPC Controller
        -> A2A SDK RequestHandler
           -> TaskStore (InMemory / Redis-backed)
           -> InMemoryQueueManager
           -> MainEventBus
           -> MainEventBusProcessor
              -> A2aAgentExecutor
                 -> AgentHandler adapter
```

所有 active QueueManager、EventBus 和 Agent execution bridge 都位于同一个 JVM 内。TaskStore 默认位于同一个 JVM 内，启用 FEAT-003 后可使用同一 runtime 实例装配的 Redis-backed TaskStore。当前 active 架构不包含跨 JVM 的 runtime 内部事件转发或远端执行节点拆分。

### 3.4 外部依赖边界

`agent-runtime` 可以通过适配器或 engine 层调用外部能力，但这些外部能力不成为 runtime 物理状态的一部分。

| 外部依赖类型 | Runtime 关系 |
|---|---|
| Agent 框架依赖 | 由对应 `AgentHandler` 适配器加载和调用。 |
| 远端 A2A Agent | 通过 runtime 侧目录和 outbound A2A 调用支撑能力访问。 |
| Redis | FEAT-003 可选依赖，承接 runtime Task 状态缓存和 Agent checkpoint cache 桥接；Redis-backed 缓存生命周期受统一 TTL 配置约束。 |
| `agent-bus` / 下游 service | 通过中立接口或 host 装配边界交互，不把下游服务状态写入 runtime TaskStore。 |
| 模型、工具、记忆等服务 | 由具体 Agent 框架、适配器或宿主应用负责连接和治理。 |

## 4. 状态与存储物理模型

### 4.1 当前状态存储形态

当前 active runtime 默认使用 A2A SDK 的 InMemory 组件保存 Task、事件队列和事件发布状态。启用 FEAT-003 后，Task 状态与 Agent checkpoint cache 可以写入 Redis，并按 FEAT-003 TTL 配置过期；事件队列、事件发布状态、流取消句柄和临时连接表仍保持进程内形态。

| 状态组件 | 物理位置 | 说明 |
|---|---|---|
| `InMemoryTaskStore` | 宿主 JVM 内存 | 保存 Task metadata、messages、status 等 runtime Task 状态。 |
| Redis-backed TaskStore | Redis | 可选替代 `InMemoryTaskStore`，保存 runtime Task 状态缓存，缓存生命周期受 TTL 约束。 |
| `InMemoryQueueManager` | 宿主 JVM 内存 | 保存 Task 事件队列和 SSE 消费所需事件。 |
| `MainEventBus` | 宿主 JVM 内存 | 连接事件发布者与消费者。 |
| Agent checkpoint cache | 默认不由 runtime 持久化；FEAT-003 可桥接 Redis | 业务 checkpoint 语义仍归属具体 Agent 框架或外部状态能力，runtime 只提供受 TTL 约束的缓存桥。 |

### 4.2 TaskStore / QueueManager / EventBus 归属

TaskStore、QueueManager 和 EventBus 属于 runtime 的任务生命周期基础设施。TaskStore 保存 runtime 层 Task 状态，物理实现可以是默认 InMemory 或 FEAT-003 Redis-backed；QueueManager 和 EventBus 保存事件状态，仍是进程内组件。它们不保存业务应用数据库状态，也不替代 Agent 框架自己的 checkpoint。FEAT-003 不把流取消句柄、临时连接表或其他进程内运行态迁入 Redis。

```text
Runtime Task state
  -> A2A SDK InMemoryTaskStore
  -> Redis-backed TaskStore (optional)

Runtime event queue
  -> A2A SDK InMemoryQueueManager

Runtime event dispatch
  -> A2A SDK MainEventBus

Agent checkpoint / memory
  -> Agent framework or external state service
```

### 4.3 重启与故障影响

由于当前 active 状态默认保存在宿主 JVM 内存中，进程重启会丢失 runtime 层 InMemory Task、事件队列和 SSE 连接状态。启用 Redis-backed TaskStore 时，Task 状态可在 Redis 保存期限内跨进程重启保留；事件队列和 SSE 连接仍不具备跨重启恢复保证。

| 事件 | 影响 |
|---|---|
| 宿主进程正常关闭 | 在关闭排水窗口内等待在途执行结束；默认 InMemory 未完成 Task 不具备跨重启恢复保证，Redis-backed TaskStore 可保留已写入的 Task 快照。 |
| 宿主进程异常退出 | 默认 InMemory TaskStore、QueueManager 和 EventBus 状态丢失；Redis-backed TaskStore 中已提交的 Task 状态仍受 Redis 可用性和 TTL 约束。 |
| 客户端 SSE 断开 | 客户端可通过 `GetTask` 查询仍可读取的 Task；若进程已重启，事件队列和 SSE 连接状态仍不可恢复。 |
| Agent 框架内部故障 | 由 handler 或框架适配器转换为 runtime 任务层失败。 |

### 4.4 存储替换边界

当前实现通过 Spring 条件装配保留组件替换接缝，宿主可以在自身应用上下文中提供同类型 bean 覆盖默认 InMemory bean。FEAT-003 Redis-backed TaskStore 是当前 active 架构认可的 TaskStore 替换形态。

```text
A2A SDK storage abstractions
  -> default InMemory beans
  -> Redis-backed TaskStore
  -> host-provided replacement beans
```

该接缝是 active 代码装配能力；本文档不展开宿主替换实现的候选设计。

## 5. 资源模型

### 5.1 线程资源

| 组件 | 线程 | 说明 |
|---|---|---|
| HTTP 请求处理 | Tomcat / Netty IO 线程池 | 由宿主 HTTP server 提供，负责 A2A JSON-RPC 接入和 SSE 连接。 |
| `MainEventBusProcessor` | A2A SDK 后台处理线程 | 消费事件队列并触发执行路径。 |
| Agent 执行 | A2A request handler executor | `A2AAgentExecutor` 调用 `ServeOrchestrator` 与 `AgentHandler`。 |
| `A2AAutoConfiguration` executor | runtime 后台执行资源 | 具体线程池与关闭策略由 auto-configuration 和 host lifecycle 决定。 |

宿主应用可自行设置 `spring.threads.virtual.enabled: true` 开启 Java 21 虚拟线程支持。Runtime library 不预置该策略。

### 5.2 内存占用

| 组件 | 内存占用特征 | 说明 |
|---|---|---|
| `InMemoryTaskStore` | Task 对象 × 活跃 Task 数 | 每个 Task 包括 metadata、messages、status 等。 |
| Redis-backed TaskStore | Redis key/value 占用与连接池资源 | 取决于活跃 Task 数、Task 快照大小、TTL 和 Redis endpoint 拓扑；key schema 和 value 序列化格式不是外部稳定契约。 |
| `InMemoryQueueManager` | 待处理事件数 × 事件大小 | 取决于并发 Task 数、消息速率和 SSE 消费速度。 |
| `MainEventBus` | 消费者注册和事件分发结构 | 通常相对 Task 与事件队列更小。 |
| Agent 框架适配器 | 框架自身对象和执行上下文 | 由具体 handler、模型客户端、工具客户端和框架运行时决定。 |

### 5.3 并发容量参考

当前默认 InMemory 单实例模式的容量主要受以下因素影响；启用 Redis-backed TaskStore 后，还需要纳入 Redis 延迟、连接池、key 数量和 Redis 服务容量。

- HTTP server 线程、连接数和 SSE 长连接数量。
- `A2aServerExecutor` cached pool 在高并发执行下的线程增长。
- 活跃 Task 数、每个 Task 消息体大小和事件队列积压。
- Agent 框架执行耗时、模型调用耗时和外部工具调用耗时。
- 宿主 JVM heap、GC、CPU 和网络资源。
- Redis endpoint 的网络延迟、连接池大小、命令耗时和容量配额。

原始容量估算可按单实例约 1000 并发 Task、每个 Task 约 5-50KB 作为粗略参考，但实际容量必须由宿主压测和生产运行指标校准。

### 5.4 关闭与排水

Runtime 关闭时，A2A server executor 先调用 `shutdown()` 进入排水阶段，等待在途执行结束。当前宽限时间为 10s；超时后调用 `shutdownNow()` 尝试强停。

```text
close runtime
  -> executor.shutdown()
  -> wait in-flight executions, up to 10s
  -> executor.shutdownNow() on timeout
```

该策略保护正常关闭时的在途执行。Redis-backed TaskStore 可以保留已写入 Task 快照，但不提供跨进程执行迁移、事件队列恢复或平台级 drain coordination。

## 6. 当前部署约束

### 6.1 单实例状态约束

当前 active runtime 的 Task 和事件状态默认存在单个宿主 JVM 内存中，因此同一个 Task 的查询和继续执行语义依赖该宿主进程仍然存活。启用 Redis-backed TaskStore 后，同一个 Redis 数据源上的 Task 查询可以读取 TTL 内已写入的 Task 状态，但事件队列、流取消句柄和在途执行仍依赖原宿主进程。

如果部署多个宿主实例，默认情况下每个实例拥有各自独立的 InMemory TaskStore、QueueManager 和 EventBus。启用 Redis-backed TaskStore 时，多个实例可以共享 Task 状态存储，但仍需要由外部负载均衡或上层治理处理在途执行、SSE 连接和事件流的实例亲和性。

### 6.2 横向扩展前提

当前 active 架构支持通过增加宿主实例扩展整体接入容量。默认 InMemory 形态下每个实例仍是独立 runtime 状态岛；Redis-backed TaskStore 只共享 Task 状态，不把事件队列和执行线程池变成分布式组件。

| 扩展方式 | 当前语义 |
|---|---|
| 多实例部署不同 Agent | 可行，各实例独立暴露自己的 Agent Card 和 A2A endpoint。 |
| 多实例无状态负载均衡同一 Task | 默认 InMemory 形态不保证；Redis-backed TaskStore 可共享 Task 查询状态，但在途执行和事件流仍需实例亲和。 |
| 同一 Task 跨实例查询 / 恢复 | 默认 InMemory 形态不保证；Redis-backed TaskStore 只改善 Task 状态读取，执行恢复不自动跨实例。 |
| 进程重启后 Task 恢复 | 默认 InMemory 形态不保证；Redis-backed TaskStore 可保留已写入 Task 快照，但不恢复事件队列和执行线程。 |
