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

`agent-runtime` 在当前实现中是独立 Maven 模块和 plain library artifact。它以 `com.huawei.ascend.runtime` 为命名空间根，通过 Spring Boot 自动配置暴露 A2A northbound 接入，通过 `engine.spi` 暴露框架中立 Agent 执行扩展面和 runtime Redis 操作 SPI，通过 `engine.redis` 承载 Redis-backed runtime 状态缓存默认实现，并通过 `engine.a2a` 桥接 A2A SDK server/client 能力。

## 2. 模块与构建形态

### 2.1 Maven 模块身份

`agent-runtime` 是根工程中的一级 Maven 模块。

```xml
<modules>
  <module>spring-ai-ascend-dependencies</module>
  <module>agent-bus</module>
  <module>agent-runtime</module>
  <module>agent-service</module>
</modules>
```

模块自身声明：

```xml
<artifactId>agent-runtime</artifactId>
<name>agent-runtime</name>
```

该模块的构建输出是普通 Java library，不生成可执行 Spring Boot fat jar。业务应用通过嵌入 `RuntimeApp` 与 `LocalA2aRuntimeHost` 启动 runtime。

### 2.2 Parent 与依赖管理

`agent-runtime` 继承根工程 `spring-ai-ascend-parent`，版本由 parent 统一管理。

```xml
<parent>
  <groupId>com.huawei.ascend</groupId>
  <artifactId>spring-ai-ascend-parent</artifactId>
  <version>0.2.0-SNAPSHOT</version>
</parent>
```

根工程的 `dependencyManagement` 统一管理 Spring AI、Spring Cloud、A2A SDK、OpenTelemetry、Resilience4j、Testcontainers、ArchUnit 等依赖版本。模块 POM 不重复声明这些托管依赖的版本。

### 2.3 Library Artifact 边界

`agent-runtime` 是 framework-neutral agent-hosting runtime SDK，不是平台级 service。

当前 library artifact 边界包括：

- A2A northbound HTTP/JSON-RPC 接入。
- A2A Agent Card 暴露。
- A2A SDK task lifecycle、task store、event queue 的 runtime 装配。
- `AgentRuntimeHandler` / `StreamAdapter` 中立执行 SPI。
- openJiuwen、AgentScope、Versatile 等框架适配器。
- remote A2A Agent 调用与工具化安装支撑。
- trajectory 事件与可选 OpenTelemetry 导出。
- 纯 Java 嵌入入口 `RuntimeApp`。

该模块不依赖 `agent-service`，也不把上层 serviceization 能力打包为 runtime 的内部职责。

### 2.4 资源打包

`agent-runtime` 打包自身 `src/main/resources`，并将 S2C callback contract 作为 classpath 资源发布：

```xml
<resource>
  <directory>${project.basedir}/../docs/contracts</directory>
  <targetPath>docs/contracts</targetPath>
  <includes>
    <include>s2c-callback.v1.yaml</include>
  </includes>
</resource>
```

该资源用于 access layer 的 S2C callback 契约暴露。退役的 engine-envelope / engine-hooks 契约不再随 runtime classpath 发布。

## 3. 包结构与代码组织

### 3.1 命名空间根

命名空间根为：

```text
com.huawei.ascend.runtime
```

当前主代码包结构：

```text
agent-runtime/src/main/java/com/huawei/ascend/runtime/
├── app
├── boot
├── common
└── engine
    ├── a2a
    ├── agentscope
    ├── openjiuwen
    ├── otel
    ├── redis
    ├── spi
    └── versatile
```

### 3.2 app：纯 Java 嵌入入口

`app` 包提供 runtime 的嵌入式启动 API。

| 类型 | 职责 |
|---|---|
| `RuntimeApp` | 纯 Java runtime 入口，创建并运行 runtime |
| `RuntimeHost` | runtime host 抽象 |
| `LocalA2aRuntimeHost` | 本地 A2A host 实现 |
| `RunningRuntime` | 运行中 runtime 句柄 |
| `RuntimeComponents` | runtime 组件容器 |

`app` 包没有子包。Spring Boot 相关实现被限制在 host 边界内，避免业务集成方只使用 runtime API 时被迫理解 boot 细节。

### 3.3 boot：Spring Boot 接入与自动配置

`boot` 包承载 Spring Boot 自动配置、HTTP 控制器、生命周期、健康检查、就绪状态和配置属性。

| 类型 | 职责 |
|---|---|
| `RuntimeAutoConfiguration` | runtime server 侧核心自动配置 |
| `A2aJsonRpcController` | A2A JSON-RPC HTTP 入口 |
| `AgentCardController` | Agent Card 发现端点 |
| `AgentRuntimeLifecycle` | runtime 生命周期管理 |
| `AgentRuntimeHealthIndicator` | Actuator 健康检查 |
| `RuntimeReadiness` | runtime 就绪状态 |
| `RuntimeAccessProperties` | northbound 接入属性 |
| `TrajectoryProperties` | trajectory 配置属性 |
| `TrajectoryOtelConfiguration` | OpenTelemetry trajectory 导出配置 |

`boot` 包保持扁平，不建立子包。

### 3.4 common：协议无关共享类型

`common` 包只承载 runtime 中立共享类型。

| 类型 | 职责 |
|---|---|
| `RuntimeIdentity` | tenant/user/session/task/agent 身份范围 |
| `RuntimeMessage` | 协议无关运行时消息 |

`common` 仅允许依赖 JDK 与自身包，不能依赖 Spring、A2A、Agent 框架或 service 模块。

### 3.5 engine：执行封装与适配器包

`engine` 是执行封装根包，根包内包含中立执行上下文和少量通用工具。

| 包 | 职责 |
|---|---|
| `engine` | `AgentExecutionContext`、`SseEventDecoder` 等执行上下文与通用转换 |
| `engine.spi` | 框架中立执行、memory、trajectory 扩展面 |
| `engine.a2a` | A2A server/client 桥接、Agent Card、remote invocation、A2A 结果路由 |
| `engine.openjiuwen` | openJiuwen Agent 框架适配 |
| `engine.agentscope` | AgentScope Agent 框架适配 |
| `engine.redis` | Redis-backed TaskStore、Runtime Redis 默认单机/集群适配、Redis 连接配置解析与诊断 |
| `engine.versatile` | Versatile remote/runtime 适配 |
| `engine.otel` | OpenTelemetry span sink 与工厂 |

`engine.service` 不是当前实现包。远程 Agent 调用、Agent Card cache、outbound adapter 和 invocation orchestration 归属 `engine.a2a`。

## 4. Maven 依赖

### 4.1 生产依赖

`agent-runtime` 的生产依赖包括：

| 依赖 | 用途 |
|---|---|
| `spring-boot-starter-web` | northbound HTTP 接入和本地 host |
| `spring-boot-starter-actuator` | 健康检查与运行时观测，optional |
| `reactor-core` | 异步与流式处理基础 |
| `a2a-java-sdk-server-common` | A2A server request handler、task store、event queue 等协议基底 |
| `a2a-java-sdk-client-transport-jsonrpc` | remote A2A JSON-RPC client transport |
| `a2a-java-sdk-http-client` | remote A2A HTTP client |
| `opentelemetry-api/sdk/exporter-otlp` | trajectory span 导出，optional |
| `com.openjiuwen:agent-core-java` | openJiuwen 框架适配，optional |

### 4.2 Optional 依赖

以下依赖以 optional 形式进入模块：

- `spring-boot-starter-actuator`
- `opentelemetry-api`
- `opentelemetry-sdk`
- `opentelemetry-exporter-otlp`
- `com.openjiuwen:agent-core-java`

Optional 依赖的含义是：runtime library 可在不强制携带特定观测或 Agent 框架实现的情况下被嵌入；部署或业务应用按需启用对应能力。

### 4.3 Test 依赖

测试依赖包括：

- `spring-boot-starter-test`
- `opentelemetry-sdk-testing`
- `spring-security-test`
- `testcontainers-postgresql`
- `testcontainers-junit-jupiter`
- `wiremock-standalone`
- `rest-assured`
- `archunit-junit5`
- `resilience4j-circuitbreaker`
- `reactor-test`

这些依赖用于 Spring Boot 自动配置测试、A2A HTTP 接入测试、remote invocation 测试、trajectory/OTel 测试、架构边界测试和响应式流测试。

### 4.4 Sibling Module 依赖边界

`agent-runtime` 当前不依赖任何 sibling module。

`module-metadata.yaml` 中的模块边界为：

```yaml
allowed_dependencies: []
forbidden_dependencies:
  - agent-service
```

根工程管理 `agent-runtime`、`agent-bus`、`agent-service` 的模块版本，但 `agent-runtime` 的 POM 不声明对 `agent-bus` 或 `agent-service` 的依赖。

## 5. SPI 与扩展面

### 5.1 engine.spi 中立执行 SPI

`engine.spi` 是框架中立扩展面，当前承担四类职责：

- Agent 执行入口和结果转换。
- Memory 接入的窄 SPI。
- Runtime Redis 操作 SPI。
- Trajectory 事件、脱敏、sink、source 和 emitter 扩展。
- Remote Agent 工具规格的协议中立表达。

代表性类型：

| 类型 | 职责 |
|---|---|
| `AgentRuntimeHandler` | Agent 执行入口、生命周期、健康检查与协作式取消 |
| `AbstractAgentRuntimeHandler` | Handler 基类 |
| `AgentExecutionResult` | 中立执行结果 |
| `StreamAdapter` | 框架结果流到中立结果流的转换 |
| `MemoryProvider` | runtime-provided memory init/search/save 接口 |
| `RuntimeRedisClient` | runtime Redis 操作接口，供 Redis-backed TaskStore、checkpointer 和客户 Redis 适配实现复用 |
| `TrajectoryEmitter` | trajectory 事件发射 |
| `TrajectorySink` / `TrajectorySinkFactory` | trajectory sink 扩展 |
| `TrajectoryEvent` / `TrajectoryDraft` | trajectory 事件模型 |
| `TrajectoryMasking` / `TrajectorySettings` | trajectory 脱敏和配置 |
| `RemoteAgentToolSpec` | remote Agent 工具规格 |

`engine.spi` 保持协议中立，不引用 A2A wire/server 类型，也不依赖具体 Agent 框架。

### 5.2 engine.a2a 协议桥与 Agent Card 扩展

`engine.a2a` 是 A2A 协议桥接包，允许依赖 A2A SDK。

代表性类型：

| 类型 | 职责 |
|---|---|
| `A2aAgentExecutor` | A2A SDK `AgentExecutor` 实现，桥接 task 执行到 runtime SPI |
| `A2aClientAutoConfiguration` | A2A client / remote invocation 自动配置 |
| `AgentCardProvider` | A2A Agent Card 供应接口 |
| `AgentCards` | 默认 Agent Card 工厂 |
| `RemoteAgentCardCache` | 远端 Agent Card 缓存 |
| `RemoteAgentInvocationService` | 远端 Agent 调用服务 |
| `A2aRemoteAgentOutboundAdapter` | remote Agent outbound 适配 |
| `A2aRemoteInvocationOrchestrator` | remote invocation 编排 |
| `A2aResultRouter` | A2A 执行结果路由 |
| `A2aTrajectorySupport` | A2A 与 trajectory 的连接 |

Agent Card 相关类型当前位于 `engine.a2a`，不属于 `engine.spi`。

### 5.3 框架适配器扩展

当前实现包含三类框架/远程执行适配器：

| 包 | 适配对象 |
|---|---|
| `engine.openjiuwen` | openJiuwen ReAct Agent 与 memory/checkpointer/remote tool/trajectory rails |
| `engine.agentscope` | AgentScope local agent、runtime client、harness agent |
| `engine.versatile` | Versatile client、HTTP request、message/stream adapter |

框架适配器依赖 `engine.spi`，并将框架原生输入输出转换为 runtime 中立上下文和 `AgentExecutionResult`。框架适配器不能依赖 `engine.a2a` 的协议桥实现。

### 5.4 Trajectory 与 OTel 扩展

Trajectory 是 runtime 当前 SPI 的一等扩展面。

`engine.spi` 提供 trajectory 事件模型、发射器、sink、source、脱敏和 settings；`engine.otel` 提供 `OtelSpanSink` 与 `OtelSpanSinkFactory`；`boot` 中的 `TrajectoryOtelConfiguration` 根据配置启用 OpenTelemetry span 导出。

OpenTelemetry 依赖为 optional。未启用或未携带 OTel 依赖时，runtime 仍作为普通 library 使用。

## 6. 自动装配

### 6.1 自动配置入口

`agent-runtime` 通过 Spring Boot `AutoConfiguration.imports` 暴露三个自动配置入口：

```text
com.huawei.ascend.runtime.boot.RuntimeAutoConfiguration
com.huawei.ascend.runtime.boot.RedisMiddlewareAutoConfiguration
com.huawei.ascend.runtime.engine.a2a.A2aClientAutoConfiguration
```

### 6.2 RuntimeAutoConfiguration

`RuntimeAutoConfiguration` 负责 server/runtime 侧核心装配：

- A2A server 基础设施。
- Task store、push notification store、event bus、queue manager 和 processor。
- Agent executor 与 request handler。
- HTTP controller 所需组件。
- Agent Card 供应与默认值。
- Runtime lifecycle、readiness、health indicator。
- Trajectory 配置与 sink 装配入口。

这些 Bean 以可覆盖为原则，业务方可以通过自定义 Bean 替换默认 runtime 组件。

### 6.3 RedisMiddlewareAutoConfiguration

`RedisMiddlewareAutoConfiguration` 负责 FEAT-003 引入的 runtime Redis 数据源与状态缓存装配：

- Runtime Redis SPI 默认 standalone / cluster 实现。
- Redis 连接配置解析、诊断和启动策略日志。
- Redis-backed TaskStore 与 Agent checkpoint cache 所需组件。
- 客户自定义 `RuntimeRedisClient` Bean 的优先接入。

该自动配置只把 Redis 能力暴露为 runtime SPI 和可覆盖 Bean，不要求业务代码直接依赖具体 Redis 客户端。

### 6.4 A2aClientAutoConfiguration

`A2aClientAutoConfiguration` 负责 remote A2A client / outbound invocation 相关装配：

- remote Agent 属性。
- remote Agent Card cache。
- A2A outbound adapter。
- remote invocation service / orchestrator。
- A2A parent task projection 与结果路由。

该自动配置将远端 Agent 调用保持在 `engine.a2a` 边界内，不把 A2A client 依赖扩散到中立 SPI。

### 6.5 配置属性分组

当前配置属性主要分布在：

| 类型 | 配置范围 |
|---|---|
| `RuntimeAccessProperties` | northbound runtime 接入 |
| `AgentCardProperties` | Agent Card 元数据 |
| `RemoteAgentProperties` | remote Agent 调用 |
| `AgentScopeRuntimeClientProperties` | AgentScope runtime client |
| `VersatileProperties` | Versatile 适配 |
| `TrajectoryProperties` | trajectory / OTel 导出 |
| `MiddlewareProperties` | Redis endpoint、runtime Redis 数据源与状态缓存 |

配置属性属于开发视图事实；单项配置语义和使用示例放在 runtime guide 或 L2 详细设计中维护。

## 7. 架构边界测试

### 7.1 包结构约束

`RuntimePackageBoundaryTest` 固化当前包结构：

- `engine` 只允许根包、`a2a`、`agentscope`、`openjiuwen`、`otel`、`redis`、`versatile`、`spi`。
- `boot` 保持扁平。
- `app` 保持扁平。
- openJiuwen 适配器只能位于 `engine.openjiuwen`。
- AgentScope 适配器只能位于 `engine.agentscope`。

### 7.2 协议隔离约束

可执行约束包括：

- `engine.spi`、`engine` 根包、`engine.otel`、`common`、`engine.agentscope`、`engine.openjiuwen` 不依赖 `org.a2aproject..`。
- `engine.redis` 只有 Redis-backed TaskStore 适配可以依赖 A2A SDK 存储抽象；Runtime Redis SPI 和默认 Redis 连接实现不得把 A2A 类型暴露给 `engine.spi`。
- A2A server machinery 只允许存在于 `engine.a2a` 与 `boot`。
- framework adapters 不依赖 `engine.a2a`。

这些测试是 logical view 中“协议不穿透中立 SPI 和框架适配器”的代码级约束。

### 7.3 SPI 依赖白名单

`engine.spi` 只允许依赖：

- `com.huawei.ascend.runtime.engine.spi..`
- `com.huawei.ascend.runtime.engine`
- `com.huawei.ascend.runtime.common`
- `java..`
- `org.slf4j..`
- `org.springframework.util`

`common` 只允许依赖 JDK 与自身包。

### 7.4 Library Artifact 边界

`LibraryArtifactBoundaryTest` 保护 runtime 作为 library artifact 的边界：runtime 不再提供退役的 bootstrap main class，也不通过 Spring Boot repackage 输出 executable artifact。

## 8. 编码约束

### 8.1 日志与脱敏

Runtime 代码使用 SLF4J。A2A 日志、trajectory 日志和 remote invocation 日志需要避免输出敏感 payload。

当前脱敏相关能力包括：

- `A2aLogMasking`
- `TrajectoryMasking`

### 8.2 不可变数据与空值处理

Runtime 中立对象优先使用不可变或受控构造方式：

- `RuntimeIdentity` 使用 Java record。
- `RuntimeMessage` 使用不可变消息模型。
- `RuntimeComponents` 使用 Java record。
- `AgentExecutionResult` 使用工厂方法表达结果类型。

必填字段使用构造期校验；集合和上下文快照避免向调用方暴露可变内部状态。

### 8.3 Optional 依赖与降级行为

可选能力通过 optional dependency、条件化自动配置和显式配置开关进入 runtime。

典型可选能力包括：

- Actuator health/readiness 暴露。
- OpenTelemetry trajectory 导出。
- openJiuwen 框架适配。

可选能力缺失时，runtime 的核心嵌入式运行、A2A server 接入和中立 SPI 不应被破坏。
