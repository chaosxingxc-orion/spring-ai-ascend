# agent-runtime 接口设计说明

本文说明 `agent-runtime` 当前涉及的接口边界、SPI 语义，以及 A2A、OpenJiuwen adapter、状态与记忆预留点之间的模块交互。本文只解释当前代码形态，不替代 `ARCHITECTURE.md`、`docs/contracts/contract-catalog.md` 或生成事实文件。

## 1. 设计边界

`agent-runtime` 的核心职责是把一个业务 Agent 包装成可执行的 runtime 实例，并通过 A2A 协议暴露出去。当前设计遵循三条边界：

1. A2A 层只做协议转换、执行上下文构造、结果路由和任务状态映射。
2. `engine.spi` 只保留跨 Agent 框架稳定成立的窄接口。
3. 具体 Agent 框架的装饰逻辑留在对应 adapter 内部，例如 OpenJiuwen 的 `Rail` 安装、`Runner.runAgent(...)` 调用和 checkpointer 会话语义。

因此，runtime 不提供通用的全局 provider chain，也不要求所有 Agent 框架继承同一个状态基类。

## 2. 核心 SPI

### 2.1 `AgentRuntimeHandler`

`AgentRuntimeHandler` 是最核心的执行 SPI。每个 handler 表示一个 runtime 实例中承载的一个业务 Agent。

```java
public interface AgentRuntimeHandler {
    String agentId();

    boolean isHealthy();

    Stream<?> execute(AgentExecutionContext context);

    StreamAdapter resultAdapter();
}
```

语义：

- `agentId()` 返回该 handler 服务的业务 Agent 标识。
- `isHealthy()` 表示当前 handler 是否可接流量。
- `execute(context)` 执行一次 Agent 调用，返回框架原生结果流。
- `resultAdapter()` 把框架原生结果流转换成 runtime 中立的 `AgentExecutionResult`。

`AgentRuntimeHandler` 不包含 provider 注册、状态存储、沙箱、工具覆盖等通用生命周期钩子。原因是这些能力在不同 Agent 框架里的原生扩展方式差异很大，强行统一会让 runtime 反向依赖具体框架语义。

### 2.2 `StreamAdapter`

`StreamAdapter` 是结果转换 SPI。

```java
@FunctionalInterface
public interface StreamAdapter {
    Stream<AgentExecutionResult> adapt(Stream<?> rawResults);
}
```

它的输入是框架原生结果，输出是 `AgentExecutionResult`。A2A 层只消费 `AgentExecutionResult`，不会理解 OpenJiuwen、AgentScope 或其他框架的内部结果结构。

### 2.3 `AgentCardProvider`

`AgentCardProvider` 是可选的 A2A Agent Card 元数据提供接口。

```java
public interface AgentCardProvider {
    AgentCard agentCard();
}
```

它与 `AgentRuntimeHandler` 分离，表示“执行 Agent”和“描述 Agent 的 A2A 元数据”是两种能力。业务方可以：

- 只提供 `AgentRuntimeHandler`，使用默认 Agent Card；
- 额外提供一个独立 `AgentCardProvider` Bean；
- 或让同一个类同时实现 `AgentRuntimeHandler` 与 `AgentCardProvider`。

### 2.4 `SetState`

`SetState` 是预留的显式状态写入窄 SPI。

```java
@FunctionalInterface
public interface SetState {
    void setState(AgentExecutionContext context, Map<String, Object> values);
}
```

它不是 OpenJiuwen 主路径。OpenJiuwen 优先使用自身 checkpointer 机制。`SetState` 面向没有原生 checkpoint 的 Agent 框架，用于在 adapter 内部把状态写回 runtime 上下文或调用方管理的状态存储。

### 2.5 `MemoryProvider`

`MemoryProvider` 是预留的记忆初始化与检索窄 SPI。

```java
public interface MemoryProvider {
    default void init(AgentExecutionContext context) {
    }

    List<MemoryHit> search(AgentExecutionContext context, String query, int limit);
}
```

当前只定义两个基础语义：

- `init(context)`：一次执行前初始化必要的记忆资源。
- `search(context, query, limit)`：按当前执行上下文检索相关记忆片段。

它不是完整的 memory 产品契约，不负责 compact、budget、向量索引、长期记忆治理等复杂能力。后续 Mem 中间件可以在这个窄接口上扩展，而不要求 runtime 依赖某个具体后端。

## 3. A2A 层交互

`A2aAgentExecutor` 是 A2A SDK 的 `AgentExecutor` 实现，负责把 A2A 请求桥接到 runtime handler。

当前调用链如下：

```text
A2A RequestContext
  -> A2aAgentExecutor.execute(...)
  -> AgentExecutionContext
  -> AgentRuntimeHandler.execute(context)
  -> StreamAdapter.adapt(rawResults)
  -> AgentEmitter task state / message output
```

`A2aAgentExecutor` 做的事情：

- 从 A2A `RequestContext` 提取 `taskId`、`contextId`、消息文本与 metadata。
- 构造 `AgentExecutionContext`，其中 `RuntimeIdentity` 携带 `tenantId`、`userId`、`sessionId`、`taskId`、`agentId`。
- 调用 `handler.execute(context)`。
- 使用 `handler.resultAdapter()` 转换结果。
- 将 `OUTPUT`、`COMPLETED`、`FAILED`、`INTERRUPTED` 映射为 A2A emitter 行为。

`A2aAgentExecutor` 不做的事情：

- 不创建具体 Agent。
- 不安装 OpenJiuwen Rail。
- 不理解 OpenJiuwen checkpointer。
- 不持有状态存储。
- 不承载通用 provider chain。

这样可以避免 A2A 协议桥变成所有框架能力的集中点。

## 4. OpenJiuwen Adapter 交互

OpenJiuwen 的框架适配收敛在 `runtime.engine.openjiuwen` 包内。

### 4.1 `OpenJiuwenAgentRuntimeHandler`

`OpenJiuwenAgentRuntimeHandler` 实现 `AgentRuntimeHandler`，并把 OpenJiuwen 执行主流程固定下来：

```text
AgentExecutionContext
  -> createOpenJiuwenAgent(context)
  -> runtimeRails(context)
  -> BaseAgent.registerRail(...)
  -> toOpenJiuwenInput(context)
  -> Runner.runAgent(agent, input, conversationId, null)
  -> OpenJiuwenStreamAdapter
```

子类只需要实现：

```java
protected abstract BaseAgent createOpenJiuwenAgent(AgentExecutionContext context);
```

也就是说，业务示例负责创建具体 `BaseAgent`，OpenJiuwen adapter 负责执行协议、rail 安装、输入转换、结果转换和错误映射。

### 4.2 `OpenJiuwenRuntimeRail`

`OpenJiuwenRuntimeRail` 是 runtime 自己安装到 OpenJiuwen Agent 上的固定 Rail。Wave 1 中它保持轻量，用作稳定扩展点。后续如果需要 runtime 统一注入状态辅助、工具覆盖、远端 A2A 调用观测或沙箱上下文，可以通过 OpenJiuwen 的 rail 机制扩展，而不是把这些逻辑放回 A2A 层。

### 4.3 `OpenJiuwenMessageAdapter`

`OpenJiuwenMessageAdapter` 把 `AgentExecutionContext` 转成 OpenJiuwen input。关键点是 `conversation_id` 使用 `context.getAgentStateKey()`。

语义：

- `agentStateKey` 是业务可控的稳定状态 key。
- 如果业务未显式传入 `agentStateKey` / `stateKey`，runtime fallback 到 `taskId`。
- 多轮对话需要复用同一个状态时，应由调用侧传入稳定 `agentStateKey`。
- 用户跳出原任务、产生新 task 时，可以使用新的 state key，从而避免错误复用旧任务上下文。

OpenJiuwen 自身通过 `conversation_id` 与原生 checkpointer 完成 session 保存和恢复。runtime 不在每次调用后手工 dump/load OpenJiuwen 内部状态。

### 4.4 `OpenJiuwenStreamAdapter`

`OpenJiuwenStreamAdapter` 把 OpenJiuwen 返回的 map 结构转换成 `AgentExecutionResult`：

- answer / output -> `OUTPUT` 或 `COMPLETED`
- error -> `FAILED`
- interrupt / input required -> `INTERRUPTED`

A2A 层只处理转换后的 `AgentExecutionResult`，不直接理解 OpenJiuwen 原始结果。

### 4.5 构建一个 OpenJiuwen Runtime Handler 时发生什么

业务侧通常在 Spring configuration 中声明一个 `OpenJiuwenAgentRuntimeHandler` Bean：

```java
@Bean
OpenJiuwenAgentRuntimeHandler openJiuwenReactAgentHandler(...) {
    return new SampleOpenJiuwenReactAgentHandler(...);
}
```

`SampleOpenJiuwenReactAgentHandler` 继承 `OpenJiuwenAgentRuntimeHandler`，只实现具体 Agent 的创建逻辑：

```java
@Override
protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
    ReActAgent agent = new ReActAgent(card);
    agent.configure(config);
    return agent;
}
```

因此，业务侧只负责“如何创建和配置 OpenJiuwen 的 `BaseAgent`”。执行、输入转换、状态 key 传递、rail 安装和结果转换都由 runtime adapter 统一处理。

完整链路如下：

```text
Spring Configuration
  -> new SampleOpenJiuwenReactAgentHandler(...)
  -> OpenJiuwenAgentRuntimeHandler(agentId)
  -> A2A runtime wiring 注册为 AgentRuntimeHandler Bean
  -> A2aAgentExecutor.execute(...)
  -> handler.execute(context)
  -> createOpenJiuwenAgent(context)
  -> registerRail(...)
  -> Runner.runAgent(...)
```

#### execute 注入

`A2aAgentExecutor` 收到 A2A 请求后，会构造 `AgentExecutionContext`，然后直接调用：

```java
handler.execute(context);
```

对 OpenJiuwen 来说，`execute(context)` 在 `OpenJiuwenAgentRuntimeHandler` 中是固定流程：

```text
createOpenJiuwenAgent(context)
  -> runtimeRails(context)
  -> agent.registerRail(rail)
  -> toOpenJiuwenInput(context)
  -> runOpenJiuwenAgent(agent, input, openJiuwenConversationId(context))
  -> Runner.runAgent(agent, input, conversationId, null)
```

这保证 A2A bridge 不需要知道 OpenJiuwen 的 `Runner`、`Rail` 或 checkpointer。

#### state 注入

OpenJiuwen 当前不通过 runtime 自己的 `AgentStateStore` 手工搬运状态，而是走 OpenJiuwen 原生 checkpointer：

```text
AgentExecutionContext.getAgentStateKey()
  -> OpenJiuwenMessageAdapter 写入 input["conversation_id"]
  -> OpenJiuwenAgentRuntimeHandler 传给 Runner.runAgent(..., conversationId, ...)
  -> OpenJiuwen Runner / Checkpointer 自行恢复和保存
```

`agentStateKey` 的解析顺序是：

```text
variables["agentStateKey"]
  -> variables["stateKey"]
  -> fallback taskId
```

如果业务需要跨多轮复用状态，应显式传入稳定 `agentStateKey`。如果用户跳出原任务并创建新 task，fallback 到新 `taskId` 会自然隔离状态。

#### mem 注入

当前 OpenJiuwen adapter 还没有强制接入 Mem。`MemoryProvider` 只是 runtime 预留的窄 SPI：

```java
public interface MemoryProvider {
    default void init(AgentExecutionContext context) {
    }

    List<MemoryHit> search(AgentExecutionContext context, String query, int limit);
}
```

后续如果要把 Mem 接入 OpenJiuwen，推荐两种方式：

1. 在 OpenJiuwen adapter 内部的 `runtimeRails(context)` 中注册一个带 Mem 能力的 Rail。
2. 在具体 `createOpenJiuwenAgent(context)` 中，把 Mem 检索结果注入 OpenJiuwen Agent 的 prompt、tool 或 context 配置。

Mem 不应放进 `A2aAgentExecutor`，也不应恢复成全局 provider chain。它应该作为 OpenJiuwen adapter 或具体业务 Agent 创建逻辑的一部分组合进去。

## 5. 状态与记忆接口的使用原则

当前状态设计分成两类：

1. 框架原生 checkpoint：优先使用，例如 OpenJiuwen。
2. runtime 预留窄 SPI：给没有原生 checkpoint 或需要 runtime 辅助的框架使用。

OpenJiuwen 当前推荐路径：

```text
agentStateKey
  -> OpenJiuwen conversation_id
  -> OpenJiuwen Checkpointer
  -> OpenJiuwen native restore/save
```

`SetState` 与 `MemoryProvider` 当前不强制 OpenJiuwen 使用。它们的存在是为了后续接入其他 Agent 框架时有一个足够小的公共扩展点。

不要把正文记忆、大 payload 或完整业务状态都塞进 `AgentExecutionContext`。`AgentExecutionContext` 更适合承载执行身份、输入消息、metadata、状态 key 或引用。完整的状态后端、记忆压缩和检索策略应由对应中间件或框架后端负责。

## 6. 模块间职责

| 模块 / 包 | 当前职责 | 不承担的职责 |
|---|---|---|
| `runtime.engine.a2a` | A2A 请求接入、上下文构造、结果映射到 emitter | 不创建具体 Agent，不安装框架装饰，不管理状态存储 |
| `runtime.engine.spi` | 定义跨 Agent 框架稳定成立的窄 SPI | 不放具体框架实现，不承载 provider chain |
| `runtime.engine.openjiuwen` | OpenJiuwen adapter、Agent 创建入口、Rail 安装、Runner 调用、输入/输出转换 | 不要求其他框架复用 OpenJiuwen 机制 |
| `runtime.engine.service` | 状态存储抽象和默认实现 | 不理解某个 Agent 框架的内部状态结构 |
| `examples/*` | 提供具体业务 Agent 示例和配置 | 不定义 runtime 核心执行边界 |

## 7. 接入新 Agent 框架的建议

新增一个 Agent 框架时，优先按以下顺序判断：

1. 框架是否已有原生 checkpoint / session / state 机制。
2. 框架是否已有类似 rail、middleware、callback、interceptor 的原生装饰机制。
3. 框架结果如何映射到 `AgentExecutionResult`。
4. 是否需要自定义 Agent Card。
5. 是否需要使用 `SetState` 或 `MemoryProvider` 这种 runtime 预留窄 SPI。

推荐形态：

- 实现一个新的 `AgentRuntimeHandler`。
- 提供一个对应的 `StreamAdapter`。
- 如有框架原生装饰机制，装饰逻辑留在该框架 adapter 内部。
- 如需自定义 A2A 元数据，额外提供 `AgentCardProvider`。
- 只有当某个能力跨多个 Agent 框架稳定成立时，才考虑提升为新的 runtime SPI。

## 8. 当前验证覆盖

当前接口边界由以下测试覆盖：

- `OpenJiuwenAgentRuntimeHandlerTest`：验证 OpenJiuwen handler 会安装 runtime rail、使用稳定 `agentStateKey` 作为 conversation id，并能把 OpenJiuwen 异常映射为错误结果。
- `A2aJsonRpcControllerTest`：验证 A2A JSON-RPC 接入路径。
- `RuntimeAppTest`：验证 runtime app 基础启动路径。

推荐最小验证命令：

```bash
wsl -d Ubuntu-24.04 -- bash -lc 'cd /mnt/d/repo/spring-ai-ascend && ./mvnw -pl agent-runtime -Dtest=OpenJiuwenAgentRuntimeHandlerTest,A2aJsonRpcControllerTest,RuntimeAppTest test'
```

如果接口、契约或 architecture facts 发生变化，还需要运行：

```bash
wsl -d Ubuntu-24.04 -- bash -lc 'cd /mnt/d/repo/spring-ai-ascend && ./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts'
```
