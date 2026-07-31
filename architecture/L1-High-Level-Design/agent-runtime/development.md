---
level: L1-HLD
TAG:
  - development-view
  - code-organization
  - dependency-boundary
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - logical.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-runtime L1 架构开发视图

## 1. 开发视图定位

本文档描述 `agent-runtime` 在代码、构建、依赖、自动配置、SPI 扩展面和可执行边界测试上的 active 架构事实。

当前实现承载在 `service/` 聚合模块下，以 `com.openjiuwen.service` 为命名空间根，通过 Spring Boot 自动配置暴露 A2A northbound 接入，通过 `agent-service-spec` 暴露 Agent 执行 SPI，并通过 `agent-service-app` 桥接 A2A SDK server/client 能力。

## 2. 模块与构建形态

### 2.1 Maven 模块身份

当前相关 Maven 模块位于 `service/` 下：

```text
service/
├── agent-service-spec
├── agent-service-app
├── agent-service-adapters
└── agent-service-demo
```

| 模块 | 职责 |
|---|---|
| `agent-service-spec` | 公共 DTO、SPI、lifecycle 和 paths 契约。 |
| `agent-service-app` | Spring Boot controller、auto-configuration、A2A bridge、orchestrator 和 lifecycle。 |
| `agent-service-adapters` | openJiuwen / AgentCore 等框架适配器。 |
| `agent-service-demo` | 示例应用与集成验证。 |

### 2.2 Parent 与依赖管理

`service` 聚合模块继承根工程依赖管理。根工程统一管理 Spring Boot、A2A SDK、Redis、OpenJiuwen/AgentCore、测试库等依赖版本，各子模块不重复定义托管依赖版本。

### 2.3 Artifact 边界

当前 active artifact 边界包括：

- A2A northbound HTTP/JSON-RPC 接入。
- A2A Agent Card 暴露。
- A2A SDK task lifecycle、task store、event queue 的 runtime 装配。
- `AgentHandler` / `ServeOrchestrator` / `QueryStreamObserver` 执行 SPI。
- openJiuwen / AgentCore 框架适配器。
- remote A2A Agent discovery、registry 和 client 支撑。
- Spring Boot lifecycle、readiness、health 和 reset/query controller。

## 3. 包结构与代码组织

### 3.1 命名空间根

命名空间根为：

```text
com.openjiuwen.service
```

当前主代码包结构：

```text
service/
├── agent-service-spec/src/main/java/com/openjiuwen/service/spec/
│   ├── dto
│   ├── lifecycle
│   ├── paths
│   └── spi
├── agent-service-app/src/main/java/com/openjiuwen/service/app/
│   ├── autoconfigure
│   ├── config
│   ├── controller
│   │   ├── a2a
│   │   ├── probe
│   │   ├── query
│   │   └── reset
│   ├── lifecycle
│   └── orchestrator
└── agent-service-adapters/
    └── agent-service-adapters-agentcore/src/main/java/com/openjiuwen/service/adapters/agentcore/
```

### 3.2 agent-service-spec：公共契约

| 包 | 职责 |
|---|---|
| `spec.spi` | `AgentHandler`、`ServeOrchestrator`、`QueryStreamObserver`。 |
| `spec.dto` | `ServeRequest`、`QueryResponse`、`QueryChunk` 等请求/响应 DTO。 |
| `spec.lifecycle` | `AgentReadiness`、`AgentServiceIdentity`、init/shutdown/interrupt hooks。 |
| `spec.paths` | A2A 与 service endpoint 常量。 |

### 3.3 agent-service-app：Spring Boot 接入与 A2A bridge

| 类型 | 职责 |
|---|---|
| `AgentServiceAutoConfiguration` | lifecycle、identity、readiness、controller scan 等基础装配。 |
| `A2AAutoConfiguration` | A2A SDK task store、queue、request handler、push store/sender、remote client 装配。 |
| `A2aJsonRpcController` | A2A JSON-RPC HTTP 入口。 |
| `AgentCardController` | Agent Card 发现端点。 |
| `A2AAgentExecutor` | A2A SDK `AgentExecutor` 实现，桥接到 `ServeOrchestrator`。 |
| `A2AProtocolAdapter` | A2A Message → `ServeRequest` 转换。 |
| `A2AEnabledServeOrchestrator` | 默认 A2A-aware 编排实现。 |
| `A2AProperties` | `openjiuwen.service.a2a.*` 配置绑定。 |

### 3.4 agent-service-adapters：框架适配器

当前 adapter 模块提供 AgentCore / openJiuwen 相关适配，负责把 `AgentHandler` SPI 转换为具体框架调用，并处理外部服务、middleware、credential 和 sandbox/remote client 装饰。

## 4. Maven 依赖

### 4.1 生产依赖

当前相关生产依赖包括：

| 依赖 | 用途 |
|---|---|
| Spring Boot web / autoconfigure | northbound HTTP 接入、本地 host 和自动配置。 |
| A2A Java SDK server/common/jsonrpc | A2A server request handler、task store、event queue、JSON-RPC wrapper 等协议基底。 |
| A2A Java SDK client/http/jsonrpc | remote A2A JSON-RPC client transport。 |
| Redis / Jedis | Redis-backed TaskStore、Agent checkpoint cache 桥接和 middleware 连接；Redis-backed 状态缓存受 FEAT-003 TTL 约束。 |
| AgentCore / openJiuwen 相关依赖 | Agent 框架适配。 |

### 4.2 Test 依赖

测试依赖用于 Spring Boot 自动配置测试、A2A HTTP 接入测试、remote invocation 测试、probe/query/reset 集成测试和 adapter E2E 验证。具体测试矩阵由各子模块测试目录维护。

### 4.3 Sibling Module 依赖边界

`agent-service-app` 依赖 `agent-service-spec`，adapter 模块依赖 spec/app 暴露的公共接缝。`agent-service-spec` 应保持轻量公共契约，不依赖具体 Spring controller、A2A controller 或具体 Agent 框架实现。

## 5. SPI 与扩展面

### 5.1 spec.spi 执行 SPI

代表性类型：

| 类型 | 职责 |
|---|---|
| `AgentHandler` | Agent 执行入口、生命周期和会话清理。 |
| `ServeOrchestrator` | query / streamQuery 编排入口，以及 reset/cancelActive 内部控制接缝。 |
| `QueryStreamObserver` | 流式输出回调。 |

`spec.spi` 保持与具体 Agent 框架解耦，不直接引用 A2A wire/server 类型。

### 5.2 app.controller.a2a 协议桥与 Agent Card

`app.controller.a2a` 是 A2A 协议桥接包，允许依赖 A2A SDK。

代表性类型：

| 类型 | 职责 |
|---|---|
| `A2AAgentExecutor` | A2A SDK `AgentExecutor` 实现，桥接 task 执行到 `ServeOrchestrator`。 |
| `A2aJsonRpcController` | `/a2a` JSON-RPC 入口。 |
| `AgentCardController` | Agent Card well-known endpoint。 |
| `A2AProtocolAdapter` | A2A message 到 `ServeRequest` 的转换。 |
| `ChunkMapper` | `QueryChunk` 到 A2A part 的转换。 |
| `RedisTaskStore` / `WriteThrottlingTaskStore` | TaskStore 扩展；Redis-backed Task 快照写入受 FEAT-003 TTL 约束。 |

Agent Card 由 `AgentCardController` 基于 `A2AProperties` 和服务身份信息生成。

### 5.3 框架适配器扩展

框架适配器实现或装配 `AgentHandler`，并将框架原生输入输出转换为 `ServeRequest` / `QueryResponse` / `QueryChunk` 语义。框架适配器不能依赖 `A2aJsonRpcController` 的具体 HTTP wire 处理。

## 6. 自动装配

### 6.1 自动配置入口

当前自动配置入口位于：

```text
service/agent-service-app/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
service/agent-service-adapters/agent-service-adapters-agentcore/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 6.2 AgentServiceAutoConfiguration

`AgentServiceAutoConfiguration` 负责基础服务侧装配：

- controller component scan。
- `AgentServiceIdentity`。
- `DefaultAgentReadiness` / `AgentReadiness`。
- lifecycle hooks、manager、bootstrap。
- active stream registry / interruptor。

### 6.3 A2AAutoConfiguration

`A2AAutoConfiguration` 负责 A2A server/client 相关装配：

- TaskStore，默认 `InMemoryTaskStore`，可按 middleware 配置切换受 TTL 约束的 Redis-backed store。
- `MainEventBus`、`QueueManager`、`MainEventBusProcessor`。
- `PushNotificationConfigStore` 与 no-op `PushNotificationSender`。
- `A2AProtocolAdapter`、`A2AAgentExecutor`、`RequestHandler`。
- remote Agent card registry、discovery 和 client。
- 默认 `A2AEnabledServeOrchestrator`。

FEAT-003 还可为 Agent checkpoint 提供受 TTL 约束的 Redis cache 桥接，但不改变业务 checkpoint 的所有权。当前版本不把事件队列、执行线程池、流取消句柄或临时连接表纳入 Redis-backed 状态缓存范围。

### 6.4 配置属性分组

当前配置属性主要分布在：

| 类型 | 配置范围 |
|---|---|
| `A2AProperties` | `openjiuwen.service.a2a.*`，Agent Card、A2A endpoint、remote agents 等配置。 |
| `ServiceProperties` | 服务身份、版本和 agent-id 等配置。 |
| `QueryProperties` | query REST 面相关配置。 |
| `LifecycleProperties` | init/shutdown lifecycle 配置。 |
| Adapter properties | AgentCore / middleware / external service 适配配置。 |

配置属性属于开发视图事实；单项配置语义和使用示例放在 runtime guide 或 L2 详细设计中维护。

## 7. 架构边界测试

当前 L1 只描述边界意图：spec 公共契约应保持轻量，controller / A2A SDK 类型应限制在 app A2A bridge 和 auto-configuration 内，框架适配器通过 SPI 接入 runtime。具体可执行架构测试由当前工程测试目录维护。

## 8. 编码约束

### 8.1 日志与脱敏

Runtime 代码使用 SLF4J。A2A 日志、remote invocation 日志和 adapter 日志需要避免输出敏感 payload。

### 8.2 不可变数据与空值处理

Runtime 公共 DTO 需要保持可序列化、可测试和空值语义明确。A2A bridge 在把 wire 对象转换为 `ServeRequest` 时，应避免将 A2A SDK 类型泄漏到业务 handler。
