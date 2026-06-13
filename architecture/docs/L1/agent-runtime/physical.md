---
level: L1
view: physical
module: agent-runtime
status: implemented
authority: "ADR-0152 (Uniform L1 per-view mechanism + L0 mounting)"
---

# `agent-runtime` — 物理视图

## 1. 部署平面

`agent-runtime` 部署在 `compute_control` 平面（`module-metadata.yaml#deployment_plane`）。运行时在进程中与引擎内核同处一体。

部署位面：`[platform_centric, business_centric]` — 运行时与部署位置无关，同一套 engine envelope 支持两种位面（ADR-0101）。

### 1.1 部署模式

| 模式 | 描述 | 典型场景 |
|---|---|---|
| **嵌入式** | agent-runtime 作为库嵌入业务应用中 | 业务服务直接集成 Agent 能力 |
| **独立式** | agent-runtime 作为独立进程运行，通过 A2A 协议对外暴露 | Agent 微服务独立部署和伸缩 |
| **混合式** | 业务应用嵌入 agent-runtime，同时作为 A2A server 对外暴露 | 业务服务既是 Agent 宿主又是 A2A 节点 |

### 1.2 进程边界

```
┌──────────────────────────────────────────────┐
│              agent-runtime 进程               │
│                                               │
│  ┌──────────────────────────────────────────┐ │
│  │         Spring Boot (LocalA2aRuntimeHost) │ │
│  │  ┌──────────────────────────────────────┐ │ │
│  │  │  A2A JSON-RPC Controller (/a2a)      │ │ │
│  │  │  Agent Card Controller              │ │ │
│  │  └──────────────┬───────────────────────┘ │ │
│  │                 │                          │ │
│  │  ┌──────────────▼───────────────────────┐ │ │
│  │  │  A2A SDK: RequestHandler             │ │ │
│  │  │  + InMemoryTaskStore                 │ │ │
│  │  │  + InMemoryQueueManager              │ │ │
│  │  │  + MainEventBus + Processor          │ │ │
│  │  └──────────────┬───────────────────────┘ │ │
│  │                 │                          │ │
│  │  ┌──────────────▼───────────────────────┐ │ │
│  │  │  A2aAgentExecutor (AgentExecutor)    │ │ │
│  │  └──────────────┬───────────────────────┘ │ │
│  │                 │                          │ │
│  │  ┌──────────────▼───────────────────────┐ │ │
│  │  │  AgentRuntimeHandler (SPI)           │ │ │
│  │  │  + openJiuwen / AgentScope adapters  │ │ │
│  │  └──────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────┘ │
│                                               │
│  外部依赖（可替换）:                            │
│  ┌──────────────────────────────────────────┐ │
│  │  agent-bus (中立 EnginePort/编排 SPI)     │ │
│  │  agent-service (下游，通过 agent-bus)     │ │
│  └──────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

## 2. 拓扑

### 2.1 单实例拓扑

```
               ┌──────────────┐
               │  A2A Client  │
               └──────┬───────┘
                      │ JSON-RPC over HTTP
                      ▼
┌─────────────────────────────────────────┐
│           agent-runtime 实例             │
│                                          │
│  HTTP Server (Tomcat / Netty)            │
│  ├─ /a2a           (JSON-RPC)            │
│  └─ /.well-known/  (Agent Card)          │
│                                          │
│  内部组件（全 InMemory）:                  │
│  ├─ InMemoryTaskStore                    │
│  ├─ InMemoryQueueManager                 │
│  ├─ MainEventBus                        │
│  └─ 后台线程池 (CachedThreadPool)         │
└─────────────────────────────────────────┘
```

### 2.2 扩展拓扑（未来分布式）

```
               ┌──────────────┐
               │  A2A Client  │
               └──────┬───────┘
                      │
                      ▼
┌─────────────────────────────────────────┐
│           agent-runtime 实例 1           │
│  (access-layer + task-centric-control)  │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│          分布式存储层                     │
│  ├─ Redis (TaskStore + StateStore)      │
│  ├─ Redis Pub/Sub (EventBus)            │
│  └─ JDBC (持久化备份)                    │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│           agent-runtime 实例 2           │
│  (engine 层 - Agent 执行)                │
└─────────────────────────────────────────┘
```

当前版本采用 InMemory 实现，所有组件运行在单进程内。分布式扩展点：
- `InMemoryTaskStore`（A2A SDK）→ 替换为分布式 TaskStore 实现
- `InMemoryQueueManager`（A2A SDK）→ 替换为基于消息队列的实现
- `MainEventBus`（A2A SDK）→ 替换为分布式事件总线

## 3. 资源模型

### 3.1 线程模型

| 组件 | 线程 | 说明 |
|---|---|---|
| HTTP 请求处理 | Tomcat/Netty IO 线程池 | A2A JSON-RPC 接入 |
| `MainEventBusProcessor` | SDK 自有 daemon 线程 | 异步消费事件队列，不阻塞 JVM 退出 |
| Agent 执行 | `A2aServerExecutor` 线程池 | `AgentRuntimeHandler.execute()` 在派发线程上执行 |
| `RuntimeAutoConfiguration.A2aServerExecutor` | `Executors.newCachedThreadPool()`（daemon 线程） | A2A SDK 任务派发线程池；无界 cached pool，无专用配置键；关闭时先排水（10s 宽限）再强停 |

**虚拟线程**：宿主应用可自行设置 `spring.threads.virtual.enabled: true` 开启 Java 21 虚拟线程支持（库不预置）。

### 3.2 内存估算（单实例 InMemory 模式）

| 组件 | 内存占用特征 | 说明 |
|---|---|---|
| `InMemoryTaskStore`（A2A SDK） | Task 对象 × 活跃 task 数 | 每个 Task 包括 metadata、messages、status 等 |
| `InMemoryQueueManager`（A2A SDK） | 事件队列中的待处理事件数 | 取决于并发 task 数和消息速率 |
| `MainEventBus`（A2A SDK） | 订阅者注册 + 事件缓存 | 常量大小 |

**容量参考**：
- 单实例支持 ~1000 并发 Task（InMemory 模式）
- 每个 Task 平均 5-50KB 内存

### 3.3 存储接口替换

当前 InMemory 实现通过 A2A SDK 的存储接口隔离，宿主以 `@ConditionalOnMissingBean` 替换 bean 即可换为分布式实现：

```
TaskStore (A2A SDK 接口)
    ├── InMemoryTaskStore (当前默认)
    └── 分布式实现 (未来: Redis / JDBC，由宿主提供 bean)
```
