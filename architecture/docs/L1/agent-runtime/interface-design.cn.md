# agent-runtime 接口与状态中间件设计

本文合并 `agent-runtime` 接口设计说明与 2026-06-09 Agent State 中间件提案，作为当前 L1 设计入口。生成事实仍以 `architecture/facts/generated/*.json` 为准；本文只解释当前代码边界、模块交互和后续扩展原则。

## 1. 设计目标

`agent-runtime` 的职责是把一个业务 Agent 包装成单 Agent runtime，并通过 A2A 协议暴露执行能力。当前设计遵循四条边界：

1. A2A 层只做协议桥接、上下文构造、结果发射和任务状态映射。
2. `engine.spi` 只保留跨 Agent 框架稳定成立的窄接口。
3. 具体 Agent 框架的执行、装饰、状态恢复和结果转换留在对应 adapter 内部。
4. 框架已有原生 checkpoint / session / callback 机制时，runtime 优先桥接原生机制，不重复实现状态后端。

这意味着 runtime 不提供全局 provider chain，也不要求所有 Agent 框架继承同一个抽象基类。新能力优先在具体 adapter 内组合；只有跨框架语义稳定后，才提升为公共 SPI。

## 2. 核心接口

### 2.1 `AgentRuntimeHandler`

`AgentRuntimeHandler` 是执行 SPI。每个 handler 表示一个 runtime 实例承载的一个业务 Agent。

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

`AgentRuntimeHandler` 不承载通用 before/after provider、状态存储、沙箱或工具覆盖逻辑。这些能力在不同 Agent 框架中通常有原生扩展点，强行统一会让 runtime 反向依赖具体框架语义。

### 2.2 `StreamAdapter`

`StreamAdapter` 是结果转换 SPI。

```java
@FunctionalInterface
public interface StreamAdapter {
    Stream<AgentExecutionResult> adapt(Stream<?> rawResults);
}
```

A2A 层只消费 `AgentExecutionResult`，不理解 OpenJiuwen 或其他框架的内部结果结构。

### 2.3 `AgentCardProvider`

`AgentCardProvider` 是可选的 A2A Agent Card 元数据 provider。

```java
public interface AgentCardProvider {
    AgentCard agentCard();
}
```

它与 `AgentRuntimeHandler` 分离：

- `AgentRuntimeHandler` 负责执行 Agent。
- `AgentCardProvider` 负责声明对外暴露的 A2A 元数据。

业务方可以只提供 handler 使用默认 Agent Card，也可以额外提供独立 `AgentCardProvider` Bean。简单 Agent 不需要为了自定义执行而继承 Agent Card 基类。

### 2.4 `AgentExecutionContext`

`AgentExecutionContext` 是 A2A bridge 与框架 adapter 之间的轻量 carrier，包含：

- `RuntimeIdentity scope`：`tenantId`、`userId`、`sessionId`、`taskId`、`agentId`。
- `inputType` 与 A2A message 列表。
- `variables`：调用侧传入的轻量变量。
- `agentStateKey`：业务可控的稳定状态 key。
- 可选 `agentState` map：给没有原生 checkpoint 的框架做轻量状态桥接。

`agentStateKey` 解析顺序：

```text
variables["agentStateKey"]
  -> variables["stateKey"]
  -> fallback taskId
```

fallback 到 `taskId` 是有意设计：当用户跳出原任务并产生新 task 时，新 task 天然隔离状态；如果业务要跨多轮复用同一份状态，应显式传入稳定 `agentStateKey`。

### 2.5 `MemoryProvider`

`MemoryProvider` 是预留的记忆初始化、检索与写回窄 SPI。

```java
public interface MemoryProvider {
    default void init(AgentExecutionContext context) {
    }

    List<MemoryHit> search(AgentExecutionContext context, String query, int limit);

    default void save(AgentExecutionContext context, List<MemoryRecord> records) {
    }

    record MemoryHit(String id, String content, Double score, Map<String, Object> metadata) {
    }
}
```

它只定义 `init` / `search` / `save` 三个基础语义，不负责 compact、budget、向量索引、长期记忆治理或具体后端。后续 Mem 中间件可以在该接口基础上扩展，也可以由具体框架 adapter 直接组合自己的 Mem 能力。

`MemoryHit.score` 是可选字段。Mem0 / OpenJiuwen 这类后端可以返回分数，AgentScope 风格或规则型检索后端也可以不返回分数；`search(...)` 的返回顺序才是 runtime 注入上下文时的主要相关性顺序。公共 SPI 不要求不同 memory 后端的分数可比较，也不要求没有分数的后端伪造 `0.0`。

对 OpenJiuwen，优先复用 OpenJiuwen 自己的 memory 机制。runtime 公共层只保留 `MemoryProvider` 这组窄 SPI；OpenJiuwen 原生 memory 的具体接入应留在 `runtime.engine.openjiuwen` 包或业务 wiring 中，等 OpenJiuwen 0.1.12 依赖升级完成后再单独实现对应 adapter，避免在公共 SPI 中绑定 OpenJiuwen memory 包名。

`MemoryRecord` 是 runtime 中立的 message-like 记录：

```java
record MemoryRecord(String id, String role, String content, Map<String, Object> metadata) {
}
```

OpenJiuwen adapter 会在自己的包内把 OpenJiuwen `BaseMessage` 转换成 `MemoryRecord`。转换规则不放入公共 SPI，避免 AgentScope、OpenJiuwen 和后续框架互相污染。

## 3. A2A 执行链路

`A2aAgentExecutor` 是 A2A SDK 的 `AgentExecutor` 实现，负责把 A2A 请求桥接到 runtime handler。

```text
A2A RequestContext
  -> A2aAgentExecutor.execute(...)
  -> AgentExecutionContext
  -> AgentRuntimeHandler.execute(context)
  -> StreamAdapter.adapt(rawResults)
  -> AgentEmitter task state / message output
```

`A2aAgentExecutor` 做：

- 从 A2A `RequestContext` 提取 `taskId`、`contextId`、消息文本与 metadata。
- 构造 `AgentExecutionContext`。
- 调用 `handler.execute(context)`。
- 使用 `handler.resultAdapter()` 转换结果。
- 将 `OUTPUT`、`COMPLETED`、`FAILED`、`INTERRUPTED` 映射为 A2A emitter 行为。

`A2aAgentExecutor` 不做：

- 不创建具体 Agent。
- 不安装 OpenJiuwen Rail。
- 不理解 OpenJiuwen checkpointer。
- 不持有状态存储。
- 不承载通用 provider chain。

这样可以避免 A2A 协议桥变成所有框架能力的集中点。

## 4. OpenJiuwen adapter

OpenJiuwen 的框架适配收敛在 `runtime.engine.openjiuwen` 包内。

### 4.1 `OpenJiuwenAgentRuntimeHandler`

`OpenJiuwenAgentRuntimeHandler` 实现 `AgentRuntimeHandler`，固定 OpenJiuwen 执行主流程：

```text
AgentExecutionContext
  -> createOpenJiuwenAgent(context)
  -> openJiuwenRails(context)
  -> BaseAgent.registerRail(...)
  -> toOpenJiuwenInput(context)
  -> Runner.runAgent(agent, input, conversationId, null)
  -> OpenJiuwenStreamAdapter
```

子类只需要实现具体 Agent 创建：

```java
protected abstract BaseAgent createOpenJiuwenAgent(AgentExecutionContext context);
```

业务侧负责“如何创建和配置 OpenJiuwen 的 `BaseAgent`”；runtime adapter 负责执行协议、Rail 安装、输入转换、结果转换和错误映射。

### 4.2 OpenJiuwen Rail 扩展点

OpenJiuwen adapter 使用 OpenJiuwen 0.1.12 的 `BaseAgent.registerRail(...)` 与 `AgentRail` 作为框架本地扩展点。默认不安装 Rail；需要接入 Mem、工具治理或沙箱时，由子类覆盖 `openJiuwenRails(context)`，返回需要注册到 OpenJiuwen Agent 的 Rail。

OpenJiuwen 原生 memory 接入优先使用 OpenJiuwen 0.1.12 的 external memory rail。`OpenJiuwenAgentRuntimeHandler.openJiuwenExternalMemoryRail(...)` 会把 runtime 中立 `MemoryProvider` 适配成 OpenJiuwen 的 external memory provider，再交给 `ExternalMemoryRail` 处理 prefetch、工具声明和执行后同步。这个适配器只放在 `runtime.engine.openjiuwen` 包内；如果 OpenJiuwen 后续把 memory 模块拆成独立仓，只需要替换该包内适配代码，公共 `engine.spi.MemoryProvider` 不绑定 OpenJiuwen memory 包名。

同时，当前还保留同包 `MemoryRuntimeRail` 作为 OpenJiuwen 本地兼容桥，并通过 `OpenJiuwenAgentRuntimeHandler#memoryRuntimeRail(...)` 暴露给子类使用。它不是公共 runtime SPI，主要用于普通 ReActAgent 或不完整支持 harness external-memory rail 的路径：

- `beforeInvoke(...)` 调用 runtime 中立 `MemoryProvider.init(context)`。
- `beforeInvoke(...)` 使用最新用户输入调用 `MemoryProvider.search(context, query, limit)`，并把检索结果作为自然语言 system note 合并进 OpenJiuwen `ModelContext` 的 system prompt；如果当前上下文已经有 `SystemMessage`，只合并内容，不额外新增第二个 system prompt。
- `afterInvoke(...)` 从 OpenJiuwen callback context 中取出 `BaseMessage` 列表，但只把干净的 `user` / `assistant` turn 转换成 `MemoryProvider.MemoryRecord` 并调用 `MemoryProvider.save(context, records)`。业务已有的 system prompt、runtime 注入的 memory note、tool 消息都不进入长期记忆写回；长期记忆的抽取、压缩、去重仍由具体 `MemoryProvider` 负责。
- OpenJiuwen `BaseMessage` 与 `MemoryRecord` 的转换由 `OpenJiuwenMemoryMessageAdapter` 负责，留在 `runtime.engine.openjiuwen` 包内。
- `InMemoryMemoryProvider` 是 examples 内的轻量实现，按 `agentStateKey` 做最小隔离；生产 memory 后端、向量召回、压缩和跨租户治理仍由具体 `MemoryProvider` 实现负责。

兼容桥的 system prompt 处理只是兜底路径。优先路径仍是 OpenJiuwen 0.1.12 原生 external memory rail：OpenJiuwen 自己用 provider 的 `systemPromptBlock()`、`prefetch(...)` 与 `syncTurn(...)` 管理 memory prompt、检索片段和执行后同步，runtime 只提供窄 `MemoryProvider` 适配，不把 OpenJiuwen 的 prompt template / memory 包名提升到公共 SPI。

适合放入 Rail 的能力：

- 模型调用前后的 trace、耗时采样和异常观测。
- 工具调用前后的管控、沙箱校验和审计。
- Mem 检索增强与执行后写回。
- OpenJiuwen 原生 callback context 的轻量增强。

不建议放入 Rail 的能力：

- Agent 构造：仍由 `createOpenJiuwenAgent(context)` 负责。
- `Runner.runAgent(...)` 调用：仍由 handler 负责。
- `conversation_id / agentStateKey` 决策：仍由 handler / message adapter 负责。
- Agent Card：属于启动期元数据，不是执行期生命周期 hook。
- Checkpointer 配置：属于 OpenJiuwen runtime / sample wiring，不应在执行期 Rail 中切换全局状态后端。

> 版本约束：本文按 OpenJiuwen `agent-core-java:0.1.12` 的 API 设计。0.1.12 仍提供 `BaseAgent.registerRail(...)`、`AgentRail`、`Runner.runAgent(...)` 与 `CheckpointerFactory`，因此当前 adapter 以这些 API 为边界；不要把其他分支上的新运行时模型当成本文依据。
>
> Sample 约束：如果某个 examples 模块仍显式依赖 OpenJiuwen `agent-core-java:0.1.7`，则它与本文定义的 OpenJiuwen 0.1.12 native memory rail 不是同一条验证路径。该 sample 可以继续验证基础 A2A / AgentScope / OpenJiuwen ReAct 兼容路径，但不能用来证明 `openJiuwenExternalMemoryRail(...)` 可运行；启用该 rail 前应先把 sample 依赖升级到 0.1.12 或提供等价的 OpenJiuwen memory 包。

### 4.3 `OpenJiuwenMessageAdapter`

`OpenJiuwenMessageAdapter` 把 `AgentExecutionContext` 转成 OpenJiuwen input。关键点是：

```text
query = 最新用户输入
conversation_id = context.getAgentStateKey()
```

OpenJiuwen 自身通过 `conversation_id` 与原生 checkpointer 完成 session 保存和恢复。runtime 不在每次调用后手工 `dumpState()` / `updateState(...)` 搬运 OpenJiuwen 内部状态。

### 4.4 OpenJiuwen native checkpointer

OpenJiuwen 的状态主路径是原生 Runner / Checkpointer：

```text
AgentExecutionContext.getAgentStateKey()
  -> OpenJiuwen input["conversation_id"]
  -> Runner.runAgent(..., conversationId, ...)
  -> OpenJiuwen Checkpointer restore/save
```

业务方只需要保证 `agentStateKey` 稳定。OpenJiuwen 内部保存哪些字段、如何序列化、何时恢复，交给 OpenJiuwen `Runner` / `Checkpointer` 处理。

当前 OpenJiuwen adapter 提供 `OpenJiuwenCheckpointerConfigurer` 作为启动期配置入口：

- `setDefault(Checkpointer)`：业务 wiring 自行创建 OpenJiuwen 原生 checkpointer，并交给 runtime.engine.openjiuwen 包统一设置到 `CheckpointerFactory`。
- `setInMemoryDefault()`：本地开发和轻量 E2E 使用的默认路径。

该入口只属于 OpenJiuwen adapter，不进入公共 `engine.spi`。Redis、持久化或业务自定义 checkpointer 都应在业务 / examples wiring 中实例化，然后通过 `setDefault(...)` 注册；不要在每次 `execute(...)` 或每个请求里切换全局 checkpointer。

### 4.5 `OpenJiuwenStreamAdapter`

`OpenJiuwenStreamAdapter` 把 OpenJiuwen 返回的 map 结构转换为 `AgentExecutionResult`：

- answer / output -> `OUTPUT` 或 `COMPLETED`
- error -> `FAILED`
- interrupt / input required -> `INTERRUPTED`

A2A 层只处理转换后的 `AgentExecutionResult`，不直接理解 OpenJiuwen 原始结果。

## 5. 状态与记忆原则

当前状态设计分为两类：

1. 框架原生 checkpoint：优先使用，例如 OpenJiuwen。
2. runtime 预留窄 SPI：给没有原生 checkpoint 或需要 runtime 辅助的框架使用。

不要把正文记忆、大 payload 或完整业务状态都塞进 `AgentExecutionContext`。`AgentExecutionContext` 更适合承载执行身份、输入消息、metadata、状态 key 或小型状态引用。完整状态后端、记忆压缩和检索策略应由对应中间件或框架后端负责。

Mem 后续接入建议：

- Mem 不复用 Agent State 后端存正文记忆。
- Agent State 后端只保存 `memoryRef`、`checkpointRef`、`cursor` 等小对象；当前代码不再发布单独的 Agent 状态存储公共接口。
- Mem 的 compact、budget、vector retrieval、长期检索由 Mem backend 负责。
- OpenJiuwen 可优先通过 Rail 或具体 `createOpenJiuwenAgent(context)` 的 agent 配置接入 Mem。

## 6. 模块职责

| 模块 / 包 | 当前职责 | 不承担的职责 |
|---|---|---|
| `runtime.engine.a2a` | A2A 请求接入、上下文构造、结果映射到 emitter | 不创建具体 Agent，不安装框架装饰，不管理状态存储 |
| `runtime.engine.spi` | 定义跨 Agent 框架稳定成立的窄 SPI | 不放具体框架实现，不承载 provider chain |
| `runtime.engine.openjiuwen` | OpenJiuwen adapter、Agent 创建入口、Rail 安装、Runner 调用、输入/输出转换 | 不要求其他框架复用 OpenJiuwen 机制 |
| `runtime.engine.service` | 状态存储抽象和默认实现 | 不理解某个 Agent 框架的内部状态结构 |
| `examples/*` | 提供具体业务 Agent 示例、配置和样例 provider 实现 | 不定义 runtime 核心执行边界 |

## 7. 接入新 Agent 框架

新增 Agent 框架时，优先按以下顺序判断：

1. 框架是否已有原生 checkpoint / session / state 机制。
2. 框架是否已有 rail、middleware、callback、interceptor 等原生装饰机制。
3. 框架结果如何映射到 `AgentExecutionResult`。
4. 是否需要自定义 Agent Card。
5. 是否需要使用 `MemoryProvider` 这类 runtime 预留窄 SPI。

推荐形态：

- 实现新的 `AgentRuntimeHandler`。
- 提供对应 `StreamAdapter`。
- 如有框架原生装饰机制，装饰逻辑留在该框架 adapter 内部。
- 如需自定义 A2A 元数据，额外提供 `AgentCardProvider`。
- 只有当某个能力跨多个 Agent 框架稳定成立时，才考虑提升为新的 runtime SPI。

## 8. 失败语义

- state load 失败：由具体 Provider / checkpointer fail closed，不调用 handler 或让框架返回明确失败。
- handler 执行失败：转换成 `FAILED`，保持 A2A 单出口语义。
- 执行后辅助写入失败：记录 warn，不把已经完成的任务反转成失败，避免双终态。
- state save 失败：不覆盖业务执行结果；生产态后续需要告警、重试或补偿队列。

## 9. 当前特性清单

| Feature | Status | Notes |
|---|---|---|
| `AgentRuntimeHandler` 执行 SPI | Implemented | 单 Agent runtime 执行入口 |
| `StreamAdapter` 结果转换 SPI | Implemented | 框架原生结果转 `AgentExecutionResult` |
| `AgentCardProvider` 可选元数据 provider | Implemented | 执行职责和 Agent Card 声明分离 |
| 业务自定义 state key | Implemented | `agentStateKey` / `stateKey`，fallback `taskId` |
| `MemoryProvider` 预留 SPI | Implemented | 定义 `init` / `search` / `save` 基础语义 |
| OpenJiuwen native checkpointer 桥接 | Implemented | 使用稳定 `conversation_id` |
| OpenJiuwen Rail 扩展点 | Implemented | 默认不安装 Rail；可选 `MemoryRuntimeRail` 支持 memory search 注入与执行后写回 |
| Snapshot / revision / fencing | Deferred | durable backend 时补齐 |
| Mem 正式集成 | Deferred | 后续单独设计和实现 |

## 10. 验证

当前接口边界主要由以下测试覆盖：

- `OpenJiuwenAgentRuntimeHandlerTest`：验证 OpenJiuwen handler 默认不安装 Rail、子类可安装 `MemoryRuntimeRail`、使用稳定 `agentStateKey` 作为 conversation id、OpenJiuwen message 与 `MemoryRecord` 转换，以及异常结果映射。
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
