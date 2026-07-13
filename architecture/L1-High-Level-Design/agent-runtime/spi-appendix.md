---
level: L1-HLD
TAG:
  - spi-appendix
  - extension-contract
  - architecture-fact
  - module-boundary
status: active
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - logical.md
  - development.md
  - process.md
  - physical.md
  - api-appendix.md
---

# agent-runtime SPI 接口附录

## 1. SPI 附录定位

本文档描述 `agent-runtime` 当前 active 代码中的 Agent 执行 SPI、公共值对象、协议桥边界和 A2A SDK 外部接口消费关系。

SPI 附录回答以下问题：

- 业务 Agent 框架适配器应该依赖哪些 runtime 接口。
- 哪些类型属于 Agent 执行入口、编排入口和流式回调接缝。
- A2A Agent Card、A2A server 机械、远端调用编排等非业务 SPI 能力位于哪里。
- 当前实现如何隔离 A2A wire 类型和业务 handler。

本文档以当前 `service/agent-service-*` 代码为准。若本文档与代码或测试不一致，以代码和测试事实为准。

## 2. SPI 包与边界

当前 Agent Service 公共契约位于：

```text
com.openjiuwen.service.spec
```

其中 `com.openjiuwen.service.spec.spi` 承载业务 handler 和 runtime 编排入口；DTO、lifecycle 和 paths 分别位于同级包。A2A SDK wire/server 类型不穿透到业务 handler SPI。

| 类型范围 | 所在包 | 边界 |
|---|---|---|
| Agent 执行 SPI、编排 SPI、流式回调 | `com.openjiuwen.service.spec.spi` | 业务与框架适配可依赖的公共契约。 |
| Query / Serve DTO | `com.openjiuwen.service.spec.dto` | REST / A2A bridge 共用的内部执行请求与响应模型。 |
| 生命周期与 identity | `com.openjiuwen.service.spec.lifecycle` | handler 启停、readiness 和服务身份接缝。 |
| A2A server bean 装配、controller、protocol adapter | `com.openjiuwen.service.app.controller.a2a`、`com.openjiuwen.service.app.autoconfigure` | Spring Boot host 与 A2A 协议桥。 |
| openJiuwen 框架适配器 | `com.openjiuwen.service.adapters.agentcore` | 依赖公共 SPI 和具体框架，不直接拼装 A2A JSON-RPC 响应。 |

## 3. SPI 类型清单

### 3.1 执行契约

| 类型 | 语义 |
|---|---|
| `AgentHandler` | 一个可执行 Agent 的 handler SPI，承接非流式 query、流式 query、生命周期和会话清理。 |
| `ServeOrchestrator` | runtime 内部编排入口，统一 query / streamQuery / cancelActive / resetConversation。 |
| `QueryStreamObserver` | handler 流式输出回调，承载 chunk、complete、error 和取消感知。 |

### 3.2 DTO 契约

| 类型 | 语义 |
|---|---|
| `ServeRequest` | Agent 执行请求，承载 conversation、stream 标记、messages、user、space 和 metadata。 |
| `QueryResponse` | 非流式 query 响应。 |
| `QueryChunk` | 流式 query chunk，可承载普通输出、interrupt 或 error 等类型。 |

### 3.3 生命周期契约

| 类型 | 语义 |
|---|---|
| `AgentReadiness` | 暴露 process / agent loaded 状态。 |
| `AgentServiceIdentity` | 暴露 app name / version 等服务身份。 |
| `AgentInitHook` / `AgentShutdownHook` | handler 启停窗口的扩展钩子。 |
| `AgentInterruptHandler` | lifecycle interrupt 场景的扩展钩子。 |

## 4. 接口详细规范

### 4.1 AgentHandler

代码事实：

```java
public interface AgentHandler {
    QueryResponse query(ServeRequest request);
    void streamQuery(ServeRequest request, QueryStreamObserver observer);
    default void start() { }
    default void stop() { }
    default void clearSession(String conversationId) { }
}
```

契约：

- `query()` 执行非流式请求，返回聚合后的 `QueryResponse`。
- `streamQuery()` 执行流式请求，通过 `QueryStreamObserver` 逐步返回 `QueryChunk`。
- `start()` / `stop()` 由 runtime lifecycle 在服务窗口前后调用。
- `clearSession()` 用于重置 conversation 对应的框架会话或 checkpoint。
- handler 不直接构造 A2A JSON-RPC response、Task 或 SSE frame。

### 4.2 ServeOrchestrator

代码事实：

```java
public interface ServeOrchestrator {
    QueryResponse query(ServeRequest request);
    void streamQuery(ServeRequest request, QueryStreamObserver observer);
    void cancelActive(String conversationId);
    void resetConversation(String conversationId);
}
```

契约：

- `query()` 和 `streamQuery()` 是 A2A bridge 与 REST query 入口共享的内部编排接缝。
- `cancelActive()` 是 runtime lifecycle / reset 使用的内部协作式取消接缝，不等同于 `/a2a` northbound 暴露的 A2A method。
- `resetConversation()` 负责取消活跃流并清理 handler 会话状态。
- 默认 A2A 编排实现为 `A2AEnabledServeOrchestrator`，可处理远端 A2A 委派和 pending shadow task。

### 4.3 QueryStreamObserver

代码事实：

```java
public interface QueryStreamObserver {
    void onNext(QueryChunk chunk);
    void onComplete();
    void onError(Throwable error);
    boolean isCancelled();
}
```

契约：

- handler 通过 `onNext()` 输出流式 chunk。
- handler 必须在完成时调用 `onComplete()`，失败时调用 `onError()`。
- handler 应在长时间执行中检查 `isCancelled()`，避免继续消耗已取消的内部执行。

### 4.4 ServeRequest / QueryResponse / QueryChunk

`ServeRequest` 是 A2A bridge 和 REST query 入口共享的内部执行请求。当前 A2A bridge 主要填充 conversation、stream、metadata 和 messages；user / space 等字段可由其他入口填充。

`QueryResponse` 是非流式聚合响应，A2A bridge 会从其中提取 `result.content` 或 `_interrupt` 语义并投射到 Task 表面。

`QueryChunk` 是流式输出单元。A2A bridge 使用 `ChunkMapper` 将普通 chunk 转成 A2A parts；`type=interrupt` 的 chunk 被投射为 `INPUT_REQUIRED`。

## 5. Trajectory SPI 规范

当前 FEAT-001 服务入口范围不定义独立 trajectory SPI。若 handler 或框架适配器需要输出 trajectory，应由对应特性文档和实现模块定义，不在本接口附录中扩展 A2A northbound 入口承诺。

## 6. 非中立但公共的协议桥接口

### 6.1 Agent Card 生成

当前 Agent Card 由 `AgentCardController` 基于 `A2AProperties`、`AgentServiceIdentity` 和 `ServiceProperties` 生成。当前公共 SPI 不暴露独立的 Agent Card 覆盖接口。

### 6.2 Remote Agent 支撑

远端 Agent 支撑位于 `com.openjiuwen.service.app.controller.a2a.client` 和 `A2AEnabledServeOrchestrator`。它负责远端 Agent Card 发现、registry、JSON-RPC client 调用和远端中断续接。

这些类型不是业务 Agent SPI。框架适配器需要远端 Agent 工具时，应通过 runtime 提供的编排接缝消费，而不是直接依赖 A2A controller。

## 7. A2A SDK 提供的非自有接口

以下接口或类型由 A2A SDK 提供，`agent-runtime` 消费但不拥有：

| FQN | 类型 | runtime 消费方 |
|---|---|---|
| `org.a2aproject.sdk.server.agentexecution.AgentExecutor` | interface | `A2AAgentExecutor` 实现。 |
| `org.a2aproject.sdk.server.agentexecution.RequestContext` | class | `A2AAgentExecutor` 内部转换为 `A2AMessageContext` / `ServeRequest`。 |
| `org.a2aproject.sdk.server.tasks.AgentEmitter` | interface | `A2AAgentExecutor` 发射 Task 状态和 artifact。 |
| `org.a2aproject.sdk.server.requesthandlers.RequestHandler` | interface | `A2aJsonRpcController` 调用。 |
| `org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler` | class | `A2AAutoConfiguration` 装配。 |
| `org.a2aproject.sdk.server.tasks.TaskStore` | interface | `A2AAutoConfiguration` 装配，默认实现为 `InMemoryTaskStore`，FEAT-003 可替换为 Redis-backed TaskStore。 |
| `org.a2aproject.sdk.server.tasks.InMemoryTaskStore` | class | 默认进程内 TaskStore。 |
| `org.a2aproject.sdk.server.events.QueueManager` | interface | `A2AAutoConfiguration` 装配，默认实现为 `InMemoryQueueManager`。 |
| `org.a2aproject.sdk.server.events.InMemoryQueueManager` | class | 默认进程内 QueueManager。 |
| `org.a2aproject.sdk.server.events.MainEventBus` | class | 默认进程内事件总线。 |
| `org.a2aproject.sdk.server.events.MainEventBusProcessor` | class | 事件消费和执行调度连接。 |
| `org.a2aproject.sdk.server.tasks.PushNotificationConfigStore` | interface | push notification config store 装配。 |
| `org.a2aproject.sdk.server.tasks.PushNotificationSender` | interface | 当前默认 no-op sender。 |
| `org.a2aproject.sdk.spec.AgentCard` | class | `AgentCardController` 和远端 card discovery 使用。 |
| `org.a2aproject.sdk.spec.Message` / `Part` / `TextPart` | class | A2A 协议桥内部消费。 |
| `org.a2aproject.sdk.spec.Task` / `Artifact` | class | A2A bridge 内部读取 Task 快照和 artifact 状态。 |

## 8. SPI 纯度约束

- `com.openjiuwen.service.spec.spi` 不应依赖具体 Agent 框架实现。
- 业务 handler 不应直接拼装 A2A JSON-RPC response、SSE frame 或 Task wire 对象。
- A2A SDK 类型应限制在 A2A controller、executor、protocol adapter、auto-configuration 和 remote client 边界内。
- 框架适配器应通过 `AgentHandler`、`ServeOrchestrator`、`ServeRequest`、`QueryResponse` 和 `QueryChunk` 接入 runtime。

当文档与上述代码事实不一致时，以当前代码为准。
