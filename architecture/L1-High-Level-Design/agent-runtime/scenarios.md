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

`agent-runtime` 的技术场景围绕运行时接入、Task 生命周期桥接、异构 Agent 框架适配和结果回传展开。场景中的客户端、业务需求和版本取舍不是本文档的主要对象；本文只描述 active 架构现状中已经成立的运行机制。

## TS-01 A2A 客户端调用本地 Agent

### 场景目标

A2A 客户端通过标准 JSON-RPC 端点调用 runtime 内注册的 Agent，runtime 将请求转换为框架无关的执行上下文，并把 Agent 输出转换回 A2A 响应。

### 参与组件

| 组件 | 角色 |
|---|---|
| A2A JSON-RPC endpoint | 接收 `message/send` 或流式消息请求。 |
| A2A SDK RequestHandler | 创建或推进 Task，并管理协议层请求语义。 |
| A2aAgentExecutor | 将 A2A SDK 执行请求桥接到 runtime 执行 SPI。 |
| AgentRuntimeHandler | 业务或框架适配方实现的 Agent 执行入口。 |
| StreamAdapter | 将框架原生结果映射为 runtime 中立结果。 |

### 基本路径

1. 客户端向 `/a2a` 发送 A2A JSON-RPC 请求。
2. A2A SDK 解析请求，创建或推进 runtime Task。
3. `A2aAgentExecutor` 将 A2A 请求上下文转换为 `AgentExecutionContext`。
4. `AgentRuntimeHandler` 执行目标 Agent。
5. `StreamAdapter` 将框架原生输出转换为 `AgentExecutionResult`。
6. A2A SDK 将结果回写为同步响应、流式事件或 Task 状态。

### 验证关注点

- A2A 协议对象不泄漏到框架适配器内部。
- Task 生命周期由 A2A SDK 管理，Agent 执行通过 runtime SPI 进入。
- 同步和流式路径共享同一套执行 SPI。

## TS-02 异构 Agent 框架接入

### 场景目标

runtime 通过统一 SPI 接入一个本地 Agent handler。不同宿主实例可以选择 openJiuwen、AgentScope 等不同框架适配；同一实例多本地 handler 路由不属于当前版本能力，留待未来版本提案。调用方不需要感知当前 handler 背后的框架输入输出差异，也不需要感知远端 Agent 工具的 A2A 调用细节。

### 参与组件

| 组件 | 角色 |
|---|---|
| AgentRuntimeHandler | 统一 Agent 执行 SPI。 |
| StreamAdapter | 框架原生结果到 runtime 中立结果的转换。 |
| MemoryProvider | 可选 memory init/search/save 窄接缝。 |
| Trajectory SPI | 可选执行轨迹发射与导出接缝。 |
| RemoteAgentToolSpec | 远端 Agent 工具的协议中立描述。 |
| OpenJiuwen adapter | openJiuwen 框架适配。 |
| AgentScope adapter | AgentScope 框架适配。 |
| Remote agent outbound support | 远端 A2A Agent 目录与调用支撑。 |

### 基本路径

1. runtime 使用当前唯一注册的 handler 承接本地 Agent 执行。
2. 该 handler 背后的框架适配器把中立执行上下文转换为目标框架输入。
3. 目标框架执行 Agent 逻辑并产出原生结果。
4. 适配器把原生结果转换为 runtime 中立结果。
5. runtime 将结果交还给 A2A SDK 处理外部响应。

### 验证关注点

- 新增框架适配不要求修改 runtime 核心执行路径。
- 框架适配器依赖 `engine.spi`，不反向依赖 A2A 协议桥。
- 远端 Agent 调用支撑不替代跨边界 A2A 总线治理。

## TS-03 Task/Session 与业务 Agent 状态分离

### 场景目标

runtime 管理 A2A Task、Session、队列和事件推进；FEAT-003 可把 runtime Task 状态接入 Redis-backed TaskStore。业务 Agent 的 checkpoint、memory 或框架内部状态不由 runtime 解释或接管。

### 参与组件

| 组件 | 角色 |
|---|---|
| A2A SDK TaskStore | 管理 runtime Task 数据，默认 InMemory，FEAT-003 可替换为 Redis-backed TaskStore。 |
| A2A SDK QueueManager / EventBus | 管理 Task 与事件队列关系。 |
| AgentExecutionContext | 承载执行身份和中立上下文。 |
| Agent framework checkpoint | 由具体框架或外部能力管理业务 Agent 状态。 |

### 基本路径

1. A2A SDK 为请求创建或更新 Task。
2. runtime 在执行上下文中传递 tenant、user、session、task、agent 等身份信息。
3. Agent 框架按自身机制读取或写入 checkpoint。
4. runtime 只消费执行结果，不把业务 checkpoint 当作 runtime Task 状态写入 TaskStore。

### 验证关注点

- runtime Task/Session 与业务 Agent checkpoint 不混写。
- Redis-backed TaskStore 属于 runtime Task 状态存储实现，不改变业务 Agent 状态归属。
- Agent memory、业务数据源和外部系统状态不进入 runtime 的状态所有权。

## TS-04 S2C 输出模式统一回传

### 场景目标

runtime 将 Agent 执行结果统一表达为中立结果，再由 A2A SDK 对外表现为同步、流式或异步查询路径。

### 参与组件

| 组件 | 角色 |
|---|---|
| AgentExecutionResult | runtime 中立结果语义。 |
| StreamAdapter | 框架结果到中立结果的转换。 |
| A2A SDK emitter / RequestHandler | 将中立结果映射为 A2A 响应或事件。 |
| TaskStore | 保存异步路径下的 Task 状态。 |

### 基本路径

1. Agent 框架产出原生输出、失败或中断信号。
2. `StreamAdapter` 转换为 `OUTPUT`、`COMPLETED`、`FAILED` 或 `INTERRUPTED` 等中立结果。
3. A2A SDK 根据请求模式返回同步响应、推送 SSE 流或更新 Task 状态。
4. 客户端通过流式订阅或 Task 查询获得结果。

### 验证关注点

- Handler 始终通过统一结果语义输出，不直接拼装 A2A 响应。
- 流式输出和最终状态顺序一致。
- 中断、失败、取消路径不会绕过 Task 状态推进。

## TS-05 嵌入式 runtime 启动

### 场景目标

业务应用可以把 `agent-runtime` 作为 SDK 集成，通过纯 Java 入口或 Spring Boot 自动装配启动 A2A runtime。

### 参与组件

| 组件 | 角色 |
|---|---|
| RuntimeApp | 纯 Java runtime 入口。 |
| RuntimeHost | 框架无关 host 抽象。 |
| LocalA2aRuntimeHost | Spring Boot host 实现。 |
| RuntimeAutoConfiguration | Spring Boot 自动装配 A2A SDK 和 runtime bridge。 |

### 基本路径

1. 业务方提供 `AgentRuntimeHandler`。
2. runtime 通过纯 Java API 或 Spring Boot 自动配置启动。
3. host 暴露 A2A 端点和 Agent Card 发现端点。
4. 请求进入后按 TS-01 的运行路径执行。

### 验证关注点

- `runtime.app` 保持框架无关，Spring 依赖限制在 host/boot 边界。
- 自动装配不要求业务方手动创建 A2A SDK 基础设施。
- SDK 嵌入不改变 `agent-runtime` 与 `agent-service` 的依赖方向。

## TS-06 Task 查询、订阅与取消

### 场景目标

A2A 客户端可以围绕已创建的 runtime Task 执行查询、事件订阅和取消操作，runtime 保持 Task 生命周期状态的唯一写入路径，不让客户端或 Agent 框架绕过 TaskStore 修改状态。

### 参与组件

| 组件 | 角色 |
|---|---|
| A2A JSON-RPC endpoint | 接收 `tasks/get`、`tasks/cancel` 和 `tasks/resubscribe` 等请求。 |
| A2A SDK RequestHandler | 处理 Task 查询、订阅和取消协议语义。 |
| A2A SDK TaskStore | 保存 runtime Task 状态，默认 InMemory，FEAT-003 可替换为 Redis-backed TaskStore。 |
| A2A SDK QueueManager | 维护 Task 事件队列和订阅关系。 |
| A2aAgentExecutor | 承接协作式 cancel 接缝。 |

### 基本路径

1. 客户端使用 Task ID 发起查询、订阅或取消请求。
2. RequestHandler 通过 TaskStore 或 QueueManager 读取 Task 状态和事件。
3. 订阅路径把已有 Task 的后续事件转换为 SSE 流。
4. 取消路径通过 runtime executor 的 cancel 语义推进 Task 状态。
5. Agent handler 可以响应协作式取消，但不直接写 Task 生命周期状态。

### 验证关注点

- 查询、订阅和取消都以 runtime Task ID 为中心，不引入第二个生命周期状态。
- Subscribe 不创建新的 Agent 执行。
- Cancel 不绕过 TaskStore 或 task-centric-control 直接改写状态。
- 当前默认 InMemory 形态下，同一 Task 的后续请求必须回到拥有该 Task 状态的实例；Redis-backed TaskStore 可共享 Task 查询状态，但订阅和在途执行仍需实例亲和。

## TS-07 人工输入中断与继续执行

### 场景目标

Agent 执行需要人工输入时，runtime 将中断折叠为 Task 状态和事件；客户端继续消息进入同一 Task 语义后，handler 通过中立上下文恢复执行。

### 参与组件

| 组件 | 角色 |
|---|---|
| AgentRuntimeHandler | 在执行过程中产生需要人工输入的原生信号。 |
| StreamAdapter | 将原生信号转换为 `AgentExecutionResult.INTERRUPTED`。 |
| A2aAgentExecutor / emitter | 将中断路由为 `INPUT_REQUIRED` Task 状态。 |
| AgentExecutionContext | 承载继续执行所需的状态键、消息和可选状态快照。 |
| A2A SDK RequestHandler | 将继续消息绑定回 Task/context 语义。 |

### 基本路径

1. Handler 执行中需要人工输入。
2. StreamAdapter 产出 `AgentExecutionResult.interrupted(prompt)`。
3. Runtime emitter 将 Task 推进到 `INPUT_REQUIRED`，并通过 SSE 或查询表面暴露提示。
4. 客户端发送继续消息，携带既有 context/task 语义。
5. Runtime 再次调用 handler，并传入中立 `AgentExecutionContext`。

### 验证关注点

- 中断状态是 runtime Task 状态，不是框架私有 checkpoint。
- 继续消息不会创建独立的第二生命周期 owner。
- Runtime 只桥接状态键、上下文和可选 Redis cache；业务 Agent checkpoint 仍归属框架或外部状态能力。
- 默认 InMemory 形态不承诺跨进程重启后的中断恢复；Redis-backed TaskStore 只保留已写入 Task 快照，不恢复事件队列或执行线程。

## TS-08 远端 A2A Agent 工具化调用

### 场景目标

Runtime 可以把远端 A2A Agent Card 投影为中立 `RemoteAgentToolSpec`，供本地框架适配器把远端 Agent 作为工具调用。该能力只描述当前 outbound 支撑，不替代跨实例、跨部门或跨数据边界的 agent-bus 治理。

### 参与组件

| 组件 | 角色 |
|---|---|
| Remote Agent Card cache | 读取并缓存远端 Agent Card。 |
| RemoteAgentToolSpec | 向框架适配器暴露协议中立的远端工具规格。 |
| A2aRemoteInvocationOrchestrator | 编排远端调用、中断和回灌。 |
| RemoteAgentInvocationService | 执行远端 A2A 调用。 |
| AgentRuntimeHandler / adapter | 把远端 Agent 当作工具消费。 |

### 基本路径

1. Host 配置远端 Agent URL。
2. Runtime 读取远端 Agent Card，并投影为 `RemoteAgentToolSpec`。
3. 本地 handler 或框架适配器选择远端 Agent 工具。
4. Runtime outbound 调用远端 A2A Agent。
5. 远端完成后，runtime 以 `REMOTE_RESUME` 语义回灌给本地 handler。

### 验证关注点

- 框架适配器消费中立 `RemoteAgentToolSpec`，不依赖 A2A bridge 类型。
- 远端 Task 生命周期由远端 Agent/runtime 拥有，本地 runtime 不抢占远端 owner。
- 该路径不声明已经具备跨边界 agent-bus 治理、权限中介或数据引用信封。
