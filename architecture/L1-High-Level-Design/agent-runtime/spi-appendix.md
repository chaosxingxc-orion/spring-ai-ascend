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

本文档描述 `agent-runtime` 当前 active 代码中的框架中立 SPI、公共值对象、runtime Redis 操作 SPI、协议桥边界和 A2A SDK 外部接口消费关系。

SPI 附录回答以下问题：

- 业务 Agent 框架适配器应该依赖哪些 runtime 中立接口。
- 哪些类型位于 `engine.spi`，必须保持协议中立和框架中立。
- A2A Agent Card、A2A server 机械、远端调用编排等非中立能力位于哪里。
- SPI 纯度由哪些可执行架构测试守卫。

本文档以顶层 `agent-runtime/` 模块代码为准。若本文档与代码或架构测试不一致，以代码和测试事实为准。

## 2. SPI 包与边界

`module-metadata.yaml#spi_packages` 声明当前 SPI 包：

```text
com.huawei.ascend.runtime.engine.spi
```

`engine.spi` 是 sibling module、业务 handler、框架适配器和可选扩展能力编译依赖的中立契约包。它允许依赖 `engine.AgentExecutionContext` 和 `common.RuntimeIdentity` / `RuntimeMessage`，但不得依赖 A2A SDK wire/server 类型，也不得依赖 openJiuwen、AgentScope 等具体框架类型或具体 Redis 客户端类型。

协议元数据和 A2A 运行机械不属于中立 SPI：

| 类型范围 | 所在包 | 边界 |
|---|---|---|
| Agent 执行 SPI、结果模型、memory、trajectory、remote tool spec、runtime Redis 操作 SPI | `com.huawei.ascend.runtime.engine.spi` | 框架中立、协议中立、Redis 客户端中立。 |
| A2A Agent Card、A2A executor、远端 A2A 调用编排 | `com.huawei.ascend.runtime.engine.a2a` | 可依赖 A2A SDK，属于协议桥。 |
| Redis-backed TaskStore、默认 Redis 连接实现和诊断 | `com.huawei.ascend.runtime.engine.redis` | 可依赖 Redis 客户端和 A2A TaskStore 存储抽象，不向 `engine.spi` 泄漏这些类型。 |
| A2A server bean 装配、controller、lifecycle、readiness | `com.huawei.ascend.runtime.boot` | Spring Boot host 装配层。 |
| openJiuwen / AgentScope / Versatile 适配器 | `engine.openjiuwen` / `engine.agentscope` / `engine.versatile` | 依赖中立 SPI 和各自框架，不依赖 A2A server bridge。 |

## 3. SPI 类型清单

### 3.1 执行契约

| 类型 | 语义 |
|---|---|
| `AgentRuntimeHandler` | 一个可执行 Agent 的 handler SPI，承接启动、停止、健康、执行、结果适配和协作式取消。 |
| `AbstractAgentRuntimeHandler` | 可选基类，固定 `agentId()`，提供默认健康检查，并为支持 trajectory 的 handler 注入标准 invocation trajectory lifecycle。 |
| `StreamAdapter` | 将框架原生 `Stream<?>` 转换为 `Stream<AgentExecutionResult>`。 |
| `AgentExecutionResult` | runtime 中立执行结果，表达输出、完成、失败、中断和远端 Agent 调用中断。 |

### 3.2 Memory、远端工具与 Runtime Redis 契约

| 类型 | 语义 |
|---|---|
| `MemoryProvider` | 窄 memory SPI，提供每次执行的 memory 初始化、检索和保存入口。 |
| `MemoryProvider.MemoryHit` | 一条 memory 检索命中，包含内容、可选分数和元数据。 |
| `MemoryProvider.MemoryRecord` | 一条规范化 memory 写入记录。 |
| `RemoteAgentToolSpec` | 协议中立的远端 Agent 工具规格，由 A2A card cache 解析后供框架适配器消费。 |
| `RuntimeRedisClient` | runtime Redis 操作 SPI，供 Redis-backed TaskStore、Agent checkpoint cache 和客户 Redis 适配实现复用。 |

### 3.3 Trajectory 契约

| 类型 | 语义 |
|---|---|
| `TrajectorySource` | 标记 handler 可为一次执行打开 trajectory。 |
| `TrajectoryEmitter` | 适配器向 runtime 推送 `TrajectoryDraft` 的发射接口。 |
| `TrajectoryDraft` | 框架提供的半成品 trajectory 事件，不包含 runtime correlation。 |
| `TrajectoryEvent` | runtime 盖章后的框架中立 trajectory 事件，当前 schema version 为 `2`。 |
| `TrajectorySink` | trajectory 事件终端消费者，支持每次执行的 open / accept / close。 |
| `TrajectorySinkFactory` | 为每次执行创建新的 `TrajectorySink`。 |
| `CompositeTrajectorySink` | 同步 fan-out 多个 sink，并隔离单个 sink 的异常。 |
| `StampingTrajectoryEmitter` | 为 draft 分配 seq、trace/span、tenant/context/task、时间、schema version，并执行脱敏和 kind 过滤。 |
| `TrajectoryMasking` | trajectory payload 脱敏和截断工具。 |
| `TrajectorySettings` | 每次执行解析后的 trajectory 开关、脱敏正则和截断配置。 |

## 4. 接口详细规范

### 4.1 AgentRuntimeHandler

代码事实：

```java
public interface AgentRuntimeHandler {
    String agentId();
    boolean isHealthy();
    Stream<?> execute(AgentExecutionContext context);
    StreamAdapter resultAdapter();
    default void start() { }
    default void stop() { }
    default void cancel(String taskId) { }
}
```

契约：

- `agentId()` 标识该 handler 服务的 Agent。
- `isHealthy()` 供 health surface 和 readiness gate 消费；不能服务时应返回 `false`。
- `execute()` 接收中立 `AgentExecutionContext`，返回框架原生结果流。
- `resultAdapter()` 返回框架结果到 `AgentExecutionResult` 的适配器。
- `start()` / `stop()` 由 `AgentRuntimeLifecycle` 围绕 serving window 调用。`start()` 异常会导致启动失败并回滚已启动 handler；`stop()` 异常会被记录并吞掉，以保证其他 handler 得到释放机会。
- `cancel(taskId)` 是协作式取消接缝。默认 no-op 只适合懒生成 stream 的 handler；同步计算完整结果后再包装 stream 的 handler 必须覆盖它并传播框架原生中断。

当前 `RuntimeAutoConfiguration` 将 runtime host 限制为单 handler：没有 handler 时允许 A2A surface 启动但执行会被 reject；多个 handler bean 会启动失败，提示拆分为多个 runtime 实例。同实例多本地 handler / 多本地 Agent 路由不属于当前版本能力，应进入未来版本提案。

### 4.2 AbstractAgentRuntimeHandler

`AbstractAgentRuntimeHandler` 是可选基类，不是所有 handler 的必选父类。

它提供以下行为：

- 构造期校验并保存 `agentId`。
- `agentId()` 为 final。
- `isHealthy()` 默认返回 `true`。
- 实现 `TrajectorySource.openTrajectory()`，当 trajectory 开启时向 `AgentExecutionContext` 注入 `StampingTrajectoryEmitter`。
- `execute()` 为 final，自动发出 `RUN_START` / `RUN_END` trajectory 事件，异常时发出 `ERROR` draft，然后委托子类 `doExecute(context, trajectory)`。这里的 `RUN_*` 是 telemetry 事件名，不表示服务端生命周期术语。
- `supportedKinds()` 默认返回 `TrajectoryEvent.MANDATORY_KINDS`，子类可扩大支持的事件种类。

### 4.3 StreamAdapter

代码事实：

```java
@FunctionalInterface
public interface StreamAdapter {
    Stream<AgentExecutionResult> adapt(Stream<?> rawResults);
}
```

契约：

- 输入是 handler 返回的框架原生结果流。
- 输出是 runtime 中立结果流。
- `A2aAgentExecutor` 使用 try-with-resources 同时关闭 raw stream 和 adapted stream。
- adapter 不拥有 Task 状态推进，只负责语义转换。

### 4.4 AgentExecutionContext

`AgentExecutionContext` 位于 `com.huawei.ascend.runtime.engine`，不是 `engine.spi` 包内类型，但它是 SPI 输入模型的一部分。

核心字段：

| 字段 / 方法 | 语义 |
|---|---|
| `RuntimeIdentity scope` | tenant、user、session、task、agent 的中立身份范围。 |
| `inputType` | 输入类型，默认 `USER_MESSAGE`，远端回灌可使用 `REMOTE_RESUME`。 |
| `List<RuntimeMessage> messages` | 协议中立消息列表。 |
| `Map<String, Object> variables` | 请求和消息 metadata 合并后的不可变变量。 |
| `agentStateKey` | Agent 状态读取键，优先来自 `agentStateKey` / `stateKey` 变量，否则使用 `taskId`。 |
| `Optional<Map<String, Object>> getAgentState()` | 可选 Agent 状态快照。 |
| `replaceAgentState()` | 用不可变 Map 原子替换状态快照。 |
| `lastUserText()` | 规范文本抽取：优先最后一条 USER 消息，否则最后一条消息。 |
| `TrajectoryEmitter` | 每次调用的 trajectory emitter，默认 `NOOP`。 |

协议桥只把 A2A message 的 text 和 metadata 折叠进中立上下文；A2A `RequestContext`、`Message`、`Part` 等 wire 类型不穿透到 SPI。

### 4.5 AgentExecutionResult

代码事实中的结果类型：

```java
public enum Type { OUTPUT, COMPLETED, FAILED, INTERRUPTED }
public enum Target { USER, LLM, BOTH }
```

结果语义：

| Type | 语义 | A2A 路由 |
|---|---|---|
| `OUTPUT` | 中间输出片段。 | `A2aResultRouter` 将可展示内容追加为 artifact。 |
| `COMPLETED` | 最终完成。 | 延迟执行 `emitter.complete(...)`，确保 trajectory artifact 先落地。 |
| `FAILED` | 任务层失败。 | `emitter.fail(failureMessage(...))`，失败消息包含 text 和 structured DataPart。 |
| `INTERRUPTED` + `UserInputInterrupt` | 需要外部输入。 | `emitter.requiresInput(...)`，Task 进入 `INPUT_REQUIRED`。 |
| `INTERRUPTED` + `RemoteAgentInterrupt` | 需要远端 Agent 调用并回灌。 | 交给 `A2aRemoteInvocationOrchestrator` 编排；remote resume 后重新进入本地 handler。 |

`Target` 控制结果投递对象：

| Target | 语义 |
|---|---|
| `USER` | 面向用户展示。 |
| `LLM` | 面向模型续接，不直接展示给用户。 |
| `BOTH` | 同时适用于用户和模型，默认值。 |

`RemoteInvocation` 是中立远端调用载体，包含 remote agent、tool、tool call、父 Task/context、本地 conversation 和 arguments。

### 4.6 MemoryProvider

代码事实：

```java
public interface MemoryProvider {
    default void init(AgentExecutionContext context) { }
    List<MemoryHit> search(AgentExecutionContext context, String query, int limit);
    default void save(AgentExecutionContext context, List<MemoryRecord> records) { }
}
```

`MemoryProvider` 是窄 SPI，不是 memory 产品契约。它只为框架适配器提供每次执行的 memory 初始化、检索和写回接缝，不规定后端产品、索引策略、权限模型或长期记忆语义。

### 4.7 RemoteAgentToolSpec

`RemoteAgentToolSpec` 是协议中立的远端 Agent 工具描述：

```java
public record RemoteAgentToolSpec(
        String remoteAgentId,
        String toolName,
        String description,
        Map<String, Object> inputSchema) { }
```

它由 A2A card cache 从远端 Agent Card 解析得到，但放在 `engine.spi` 中，目的是让框架适配器把远端 Agent 当作工具使用时不依赖 `engine.a2a` 或 A2A SDK 类型。

### 4.8 RuntimeRedisClient

`RuntimeRedisClient` 是 FEAT-003 引入的 runtime Redis 操作 SPI。它只表达当前 runtime 状态缓存需要的最小读写、TTL、删除、存在性判断、批量读取和扫描语义，不承诺暴露 Redis 全量命令。

该接口位于 `engine.spi`，目的是让 Redis-backed TaskStore、Agent checkpoint cache 和客户 Redis 适配实现复用同一操作语义。默认 standalone / cluster 实现、连接池、诊断和具体 Redis 客户端依赖位于 `engine.redis` 或宿主提供的适配 Bean 中，不进入中立 SPI。

## 5. Trajectory SPI 规范

Trajectory 是当前 SPI 的一等扩展面，但它是 emit-only telemetry，不参与 Task 状态的真源判定。

### 5.1 事件模型

`TrajectoryEvent` 表示一次 invocation 中的一个可观测步骤。其 correlation 层次为：

```text
tenantId -> contextId -> taskId -> seq
```

span 字段包括：

- `traceId`：当前实现取 `taskId`。
- `spanId`：runtime 分配。
- `parentSpanId`：runtime 根据开放 span 栈分配。
- span-pair kind：`RUN_*`、`MODEL_CALL_*`、`TOOL_CALL_*`。
- point kind：`REASONING`、`ERROR`、`PROGRESS`。

当前事件 kind：

```text
RUN_START
RUN_END
MODEL_CALL_START
MODEL_CALL_END
TOOL_CALL_START
TOOL_CALL_END
REASONING
ERROR
PROGRESS
```

`MANDATORY_KINDS` 为 `RUN_START`、`RUN_END`、`TOOL_CALL_START`、`TOOL_CALL_END`、`ERROR`。其他 kind 只有在框架暴露相应信息且 handler 声明支持时才输出。

### 5.2 Source / Emitter / Sink

```text
TrajectorySource.openTrajectory(context, settings, sink)
  -> context.setTrajectoryEmitter(new StampingTrajectoryEmitter(...))
  -> adapter emits TrajectoryDraft
  -> StampingTrajectoryEmitter stamps correlation and masks payload
  -> TrajectorySink.accept(TrajectoryEvent)
```

`TrajectorySinkFactory` 每次 invocation 创建新的 sink。`CompositeTrajectorySink` 同步 fan-out 多个 sink，并隔离单个 sink 的异常，避免观测能力破坏 Agent 执行主流程。

### 5.3 OTel 与 A2A northbound

`engine.otel` 提供 `OtelSpanSink` / `OtelSpanSinkFactory`；`boot.TrajectoryOtelConfiguration` 在可选依赖和配置满足时启用 OpenTelemetry span 导出。

`engine.a2a.A2aTrajectorySupport` 负责把 trajectory 与 A2A 执行路径连接，并在需要时将 northbound trajectory artifact 交给 A2A caller。该连接属于协议桥，不属于 `engine.spi`。

## 6. 非中立但公共的协议桥接口

### 6.1 AgentCardProvider

`AgentCardProvider` 位于 `com.huawei.ascend.runtime.engine.a2a`：

```java
public interface AgentCardProvider {
    AgentCard agentCard();
}
```

Agent Card 是 A2A 协议元数据，因此该接口不放入中立 SPI 包。业务方可以提供 `AgentCardProvider` bean 覆盖默认 card，也可以让某个对象同时实现 handler 和 card provider，但执行 SPI 仍不依赖 A2A card 类型。

### 6.2 RemoteAgentInvocationService

`RemoteAgentInvocationService`、`RemoteAgentCardCache`、`A2aRemoteAgentOutboundAdapter`、`A2aRemoteInvocationOrchestrator` 位于 `engine.a2a`。它们负责远端 A2A Agent card 缓存、工具规格投影、outbound 调用和 remote resume 编排。

这些类型不是 Agent 框架 SPI。框架适配器需要远端 Agent 工具时，应消费中立的 `RemoteAgentToolSpec` 或通过 runtime 提供的中立接缝，而不是直接依赖 A2A bridge。

## 7. A2A SDK 提供的非自有接口

以下接口或类型由 A2A SDK 提供，`agent-runtime` 消费但不拥有：

| FQN | 类型 | runtime 消费方 |
|---|---|---|
| `org.a2aproject.sdk.server.agentexecution.AgentExecutor` | interface | `A2aAgentExecutor` 实现。 |
| `org.a2aproject.sdk.server.agentexecution.RequestContext` | class | `A2aAgentExecutor` 内部转换为 `AgentExecutionContext`。 |
| `org.a2aproject.sdk.server.tasks.AgentEmitter` | interface | `A2aResultRouter` / `A2aAgentExecutor` 发射 Task 状态和消息。 |
| `org.a2aproject.sdk.server.requesthandlers.RequestHandler` | interface | `A2aJsonRpcController` 调用。 |
| `org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler` | class | `RuntimeAutoConfiguration` 装配。 |
| `org.a2aproject.sdk.server.tasks.TaskStore` | interface | `RuntimeAutoConfiguration` 装配，默认实现为 `InMemoryTaskStore`，FEAT-003 可替换为 Redis-backed TaskStore。 |
| `org.a2aproject.sdk.server.tasks.InMemoryTaskStore` | class | 默认进程内 TaskStore。 |
| `org.a2aproject.sdk.server.events.QueueManager` | interface | `RuntimeAutoConfiguration` 装配，默认实现为 `InMemoryQueueManager`。 |
| `org.a2aproject.sdk.server.events.InMemoryQueueManager` | class | 默认进程内 QueueManager。 |
| `org.a2aproject.sdk.server.events.MainEventBus` | class | 默认进程内事件总线。 |
| `org.a2aproject.sdk.server.events.MainEventBusProcessor` | class | 事件消费和执行调度连接。 |
| `org.a2aproject.sdk.spec.AgentCard` | class | `AgentCardController`、`AgentCardProvider`、`AgentCards`、远端 card cache 使用。 |
| `org.a2aproject.sdk.spec.Message` / `Part` / `TextPart` / `DataPart` | class | 只在 `engine.a2a` 协议桥和 `boot` 接入层内部消费。 |
| `org.a2aproject.sdk.spec.Task` / `Artifact` | class | A2A bridge 内部读取 Task 快照和 artifact 状态。 |

## 8. SPI 纯度约束

可执行权威是 `agent-runtime/src/test/java/com/huawei/ascend/runtime/architecture/RuntimePackageBoundaryTest.java`。摘要如下：

- `engine` 只允许当前子包：根包、`a2a`、`agentscope`、`openjiuwen`、`otel`、`redis`、`versatile`、`spi`。
- `boot` 保持 flat package。
- `app` 不允许子包。
- `engine.spi` 不得依赖 openJiuwen 框架类型。
- `engine.spi` 只能依赖自身、`engine` 根包、`common`、JDK、SLF4J、Spring `org.springframework.util`。
- `common` 只允许依赖 JDK 和自身包。
- `engine` 根包、`engine.spi`、`engine.otel`、`common`、`engine.agentscope`、`engine.openjiuwen` 不得依赖 `org.a2aproject..`。
- `engine.redis` 只有 Redis-backed TaskStore 适配可以依赖 A2A SDK 存储抽象，Runtime Redis SPI 和默认 Redis 连接实现不得把 A2A 类型暴露给 `engine.spi`。
- `engine.agentscope` 和 `engine.openjiuwen` 不得依赖 `engine.a2a`。
- A2A server 机械类型只能被 `engine.a2a` 和 `boot` 依赖。

当文档与上述测试不一致时，以测试为准。
