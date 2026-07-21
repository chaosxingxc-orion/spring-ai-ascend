---
level: L1-HLD
TAG:
  - scenarios
  - technical-scenario
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - logical.md
  - development.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-runtime L1 架构场景视图

## 目的

本文档从 `agent-runtime` 的现状架构反推技术场景，用于把 L1 架构概览、逻辑视图、开发视图、进程视图和物理视图连接到可验证的运行路径。

本文档不承载业务场景。业务场景应从需求导入，并按版本实现范围维护在根目录 `version-scope/` 下的版本范围文档中。

## 场景边界

`agent-runtime` 的技术场景围绕运行时接入、Task 生命周期桥接、Agent 框架适配和结果回传展开。场景中的客户端、业务需求和版本取舍不是本文档的主要对象；本文只描述 active 架构现状中已经成立的运行机制。

## TS-01 A2A 客户端调用本地 Agent

### 场景目标

A2A 客户端通过标准 JSON-RPC 端点调用 runtime 内注册的 Agent，runtime 将请求转换为内部 `ServeRequest`，并把 Agent 输出转换回 A2A 响应。

### 参与组件

| 组件 | 角色 |
|---|---|
| A2A JSON-RPC endpoint | 接收 `SendMessage`、`SendStreamingMessage` 或 `GetTask` 请求。 |
| A2A SDK RequestHandler | 创建、推进或查询 Task，并管理协议层请求语义。 |
| A2AAgentExecutor | 将 A2A SDK 执行请求桥接到 runtime 执行 SPI。 |
| ServeOrchestrator | 协调 Agent 执行、远端委派和会话控制。 |
| AgentHandler | 业务或框架适配方实现的 Agent 执行入口。 |

### 基本路径

1. 客户端向 `/a2a` 发送 A2A JSON-RPC 请求。
2. `A2aJsonRpcController` 按 JSON-RPC `method` 分发。
3. A2A SDK 创建、推进或查询 runtime Task。
4. `A2AAgentExecutor` 将 A2A 请求上下文转换为 `ServeRequest`。
5. `ServeOrchestrator` 调用 `AgentHandler`。
6. A2A SDK 将结果回写为同步响应、流式事件或 Task 状态。

### 验证关注点

- A2A 协议对象不泄漏到业务 handler 内部。
- Task 生命周期由 A2A SDK 管理，Agent 执行通过 runtime SPI 进入。
- 同步和流式路径共享同一套执行 SPI。

## TS-02 Agent 框架接入

### 场景目标

runtime 通过统一 SPI 接入一个本地 Agent handler。不同宿主实例可以选择不同框架适配；同一实例多本地 handler 路由不属于当前版本能力，留待未来版本提案。

### 参与组件

| 组件 | 角色 |
|---|---|
| AgentHandler | 统一 Agent 执行 SPI。 |
| ServeOrchestrator | 统一 query / streamQuery 编排入口。 |
| QueryStreamObserver | 框架流式结果到 runtime 的回调接缝。 |
| Remote agent outbound support | 远端 A2A Agent 目录与调用支撑。 |

### 基本路径

1. runtime 使用当前唯一注册的 handler 承接本地 Agent 执行。
2. handler 背后的框架适配器把 `ServeRequest` 转换为目标框架输入。
3. 目标框架执行 Agent 逻辑并产出结果。
4. handler 以 `QueryResponse` 或 `QueryChunk` 形式返回结果。
5. runtime 将结果交还给 A2A SDK 处理外部响应。

### 验证关注点

- 新增框架适配不要求修改 A2A controller。
- 框架适配器不直接拼装 A2A JSON-RPC 响应。
- 远端 Agent 调用支撑不替代跨边界 A2A 总线治理。

## TS-03 Task/Session 与业务 Agent 状态分离

### 场景目标

runtime 管理 A2A Task、上下文、队列和事件推进；FEAT-003 可把 runtime Task 状态接入 Redis-backed TaskStore，并为 Agent checkpoint 提供受 TTL 约束的 Redis cache 桥接。业务 Agent 的 checkpoint、memory 或框架内部状态不由 runtime 解释或接管。

### 参与组件

| 组件 | 角色 |
|---|---|
| A2A SDK TaskStore | 管理 runtime Task 数据，默认 InMemory，FEAT-003 可替换为 Redis-backed TaskStore。 |
| A2A SDK QueueManager / EventBus | 管理 Task 与事件队列关系。 |
| ServeRequest | 承载会话、消息、用户、空间和 metadata 等内部执行上下文。 |
| Agent framework checkpoint | 由具体框架或外部能力管理业务 Agent 状态；FEAT-003 可提供受 TTL 约束的 Redis cache 桥接。 |

### 基本路径

1. A2A SDK 为请求创建或更新 Task。
2. runtime 在 `ServeRequest` 中传递 user、space、session、task、agent 和 metadata 等信息。
3. Agent 框架按自身机制读取或写入 checkpoint。
4. runtime 只消费执行结果，不把业务 checkpoint 写入 runtime TaskStore。

### 验证关注点

- runtime Task/Session 与业务 Agent checkpoint 不混写。
- Redis-backed TaskStore 属于 runtime Task 状态存储实现；Agent checkpoint cache 只作为桥接，二者均受 FEAT-003 TTL 约束且不改变业务 Agent 状态归属。
- Agent memory、业务数据源和外部系统状态不进入 runtime 的状态所有权。

## TS-04 S2C 输出模式统一回传

### 场景目标

runtime 将 Agent 执行结果统一表达为 A2A Task / Artifact / Status 表面，对外表现为同步、流式或异步查询路径。

### 参与组件

| 组件 | 角色 |
|---|---|
| QueryResponse | 非流式 handler 结果。 |
| QueryChunk | 流式 handler 结果。 |
| ChunkMapper | 将 QueryChunk 转为 A2A Part。 |
| A2A SDK emitter / RequestHandler | 将结果映射为 A2A 响应或事件。 |
| TaskStore | 保存异步查询路径下的 Task 状态。 |

### 基本路径

1. Agent 框架产出原生输出、失败或中断信号。
2. handler 转换为 `QueryResponse` 或 `QueryChunk`。
3. A2A SDK 根据请求模式返回同步响应、推送 SSE 流或更新 Task 状态。
4. 客户端通过流式响应或 `GetTask` 获得结果。

### 验证关注点

- Handler 不直接拼装 A2A JSON-RPC 响应。
- 流式输出和最终状态顺序一致。
- 中断和失败路径不会绕过 Task 状态推进。

## TS-05 嵌入式 runtime 启动

### 场景目标

业务应用可以通过 Spring Boot 自动装配启动 A2A runtime，并由 `service/agent-service-app` 提供 A2A controller、Agent Card controller、生命周期和执行桥接。

### 参与组件

| 组件 | 角色 |
|---|---|
| AgentServiceAutoConfiguration | 生命周期、identity、readiness 和 controller 扫描装配。 |
| A2AAutoConfiguration | A2A SDK、TaskStore、RequestHandler、executor 和 remote client 装配。 |
| A2aJsonRpcController | A2A JSON-RPC HTTP 入口。 |
| AgentCardController | Agent Card 发现端点。 |

### 基本路径

1. 业务方提供 `AgentHandler` 或由框架适配自动注册 handler。
2. Spring Boot 自动配置启动服务入口。
3. host 暴露 A2A 端点和 Agent Card 发现端点。
4. 请求进入后按 TS-01 的运行路径执行。

### 验证关注点

- 自动装配不要求业务方手动创建 A2A SDK 基础设施。
- SDK 嵌入不改变 `agent-runtime` 与上层服务入口的职责边界。
- 业务 handler 不依赖 A2A wire 类型。

## TS-06 Task 查询

### 场景目标

A2A 客户端可以围绕已创建的 runtime Task 执行查询，runtime 保持 Task 生命周期状态的唯一写入路径，不让客户端或 Agent 框架绕过 TaskStore 修改状态。

### 参与组件

| 组件 | 角色 |
|---|---|
| A2A JSON-RPC endpoint | 接收 `GetTask` 请求。 |
| A2A SDK RequestHandler | 处理 Task 查询协议语义。 |
| A2A SDK TaskStore | 保存 runtime Task 状态，默认 InMemory，FEAT-003 可替换为 Redis-backed TaskStore。 |

### 基本路径

1. 客户端使用 Task ID 发起 `GetTask` 请求。
2. RequestHandler 通过 TaskStore 读取 Task 状态。
3. Runtime 返回该 Task 的当前 A2A 快照。

### 验证关注点

- 查询以 runtime Task ID 为中心，不引入第二个生命周期状态。
- 查询路径不创建新的 Agent 执行。
- 当前默认 InMemory 形态下，同一 Task 的后续请求必须回到拥有该 Task 状态的实例；Redis-backed TaskStore 可共享 Task 查询状态，但事件队列和在途执行仍需实例亲和。

## TS-07 人工输入中断与继续执行

### 场景目标

Agent 执行需要人工输入时，runtime 将中断折叠为 Task 状态和事件；客户端继续消息进入同一 Task 语义后，handler 通过内部上下文恢复执行。

### 参与组件

| 组件 | 角色 |
|---|---|
| AgentHandler | 在执行过程中产生需要人工输入的原生信号。 |
| QueryChunk | 承载 interrupt 信号与提示内容。 |
| A2AAgentExecutor / emitter | 将中断路由为 `INPUT_REQUIRED` Task 状态。 |
| ServeRequest | 承载继续执行所需的上下文与消息。 |
| A2A SDK RequestHandler | 将继续消息绑定回 Task/context 语义。 |

### 基本路径

1. Handler 执行中需要人工输入。
2. Handler 产出 interrupt chunk。
3. Runtime emitter 将 Task 推进到 `INPUT_REQUIRED`，并通过 SSE 或查询表面暴露提示。
4. 客户端发送继续消息，携带既有 context/task 语义。
5. Runtime 再次调用 handler，并传入内部执行上下文。

### 验证关注点

- 中断状态是 runtime Task 状态，不是框架私有 checkpoint。
- 继续消息不会创建独立的第二生命周期 owner。
- Runtime 只桥接上下文和可选 Redis cache；业务 Agent checkpoint 仍归属框架或外部状态能力，Redis cache 生命周期受 FEAT-003 TTL 约束。
- 当前默认 InMemory 形态不承诺跨进程重启后的中断恢复；Redis-backed TaskStore 只保留已写入 Task 快照，不恢复事件队列或执行线程。

## TS-08 远端 A2A Agent 工具化调用

### 场景目标

Runtime 可以读取远端 A2A Agent Card 并将远端 Agent 注册为本地可调用能力。该能力只描述当前 outbound 支撑，不替代跨实例、跨部门或跨数据边界的 agent-bus 治理。

### 参与组件

| 组件 | 角色 |
|---|---|
| A2ARemoteAgentCardRegistry | 保存远端 Agent Card 与超时配置。 |
| A2AAgentCardDiscovery | 启动期发现远端 Agent Card。 |
| A2ARemoteAgentClient | 执行远端 A2A 调用。 |
| ServeOrchestrator / AgentHandler | 把远端 Agent 当作工具消费。 |

### 基本路径

1. 配置远端 Agent URL。
2. Runtime 读取远端 Agent Card，并解析其 capabilities / skills。
3. 本地 handler 或 orchestrator 在需要时调用远端 Agent。
4. 远端结果回到本地执行链路。

### 验证关注点

- 远端 Agent 调用属于 outbound 能力，不是本 runtime inbound 服务入口。
- 远端 Agent 工具化不改变本 runtime 的 `/a2a` northbound 方法范围。
