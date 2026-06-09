---
affects_level: L1
affects_view: development
proposal_status: review
authors: ["EuphoriaYan", "Codex"]
related_adrs: []
related_rules: []
affects_artefact: ["agent-runtime/src/main/java/com/huawei/ascend/runtime/engine"]
---

# agent-runtime Agent State 中间件实现提案

> **Date:** 2026-06-09
> **Status:** Pending Review
> **Affects:** L1 / development，次要影响 logical view

## 1. Background

`agent-runtime` 已经通过 `AgentRuntimeHandler` 统一承载不同 Agent 框架，但此前 `AgentExecutionContext` 只有 `scope + input`，没有框架无关的执行状态恢复入口。

本提案落地第一版 Agent State 中间件：runtime 提供统一 `AgentStateStore` 与可选 Provider 生命周期。对 OpenJiuwen 这类已经提供原生 checkpointer 的框架，runtime 不再重写其持久化后端，只负责传入稳定 `conversation_id`，并在 sample 中直接配置 OpenJiuwen 自带 checkpointer。对缺少原生 checkpoint 的框架，才通过可选 Provider 把框架内部状态导入/导出到 `AgentExecutionContext`。Provider 不持有 Store，Store 也不理解具体 Agent 框架。

## 2. Scope Statement

本次变更主视图是 `development`，影响 `agent-runtime` 的 engine / engine.spi / engine.service 代码组织。它不改变 A2A 协议、不改变 Session 语义、不引入 Mem。

## 3. Root Cause / Strongest Interpretation

1. Agent 执行中断后，runtime 缺少一个统一位置承载框架 checkpoint 或框架无关执行状态。
2. 如果让每个 handler 自己持有 Store，会把状态存储细节泄漏到具体 Agent Adapter，破坏依赖倒置。
3. 如果每加一个能力就新增 `AbstractXxxAgentRuntimeHandler`，后续 State、Mem、Sandbox、Tool Override 会形成深继承树。
4. Agent Card 是 runtime 对外的协议元数据声明，不应强制每个业务 handler 通过继承基类或实现额外接口来获得。

## 4. Implemented Design

### 4.1 Agent State API

`com.huawei.ascend.runtime.engine.service` 提供：

- `AgentStateStore`：状态存储 API，提供 `load(String key)`、`save(String key, Map<String,Object>)`、`delete(String key)`。
- `InMemoryAgentStateStore`：默认内存实现，依赖 JDK `ConcurrentHashMap`，不引入额外库。
- `NoopAgentStateStore`：兼容未接入状态存储的手工 Dispatcher 构造路径。

状态 key 由业务侧在 `EngineInput.variables` 中指定：

- 首选 `agentStateKey`。
- 兼容 `stateKey`。
- 未指定时退回 `taskId`，保证现有最小链路仍可运行。

不再由 runtime 固定拼接 `tenantId + userId + sessionId + taskId + agentId`，也不再引入 `AgentStateSnapshot` / revision。并发 fencing、CAS、分布式一致性留给未来 durable backend 设计。

本轮明确区分两类 key：

- 业务自定义 key：`agentStateKey` / `stateKey`，用于决定 `AgentStateStore` 中这份状态的存取位置。业务可按订单、会话、流程实例或其他业务维度指定。
- Adapter 内部 key：由具体 Agent 框架自行定义。对 OpenJiuwen 来说，本轮改为优先使用其原生 `conversation_id + Checkpointer` 机制，不再由 runtime 手工定义 `openjiuwen.sessionId` / `openjiuwen.state` envelope。

业务状态字段应由具体 Agent 框架在自己的 session state / checkpoint 中读写；runtime 只负责提供稳定的业务状态 key。OpenJiuwen 这类具备原生 checkpoint 的框架直接使用自身 `InMemoryCheckpointer` / `RedisCheckpointer` 等实现；框架没有原生 checkpoint 时才考虑通过 Provider 做轻量桥接。

OpenJiuwen 当前推荐形状：

```text
EngineInput.variables
  agentStateKey = 业务自定义 key，例如 order-123 / conversation-456

OpenJiuwenMessageAdapter
  query = 最新用户输入
  conversation_id = context.getAgentStateKey()

OpenJiuwen Runner / Checkpointer
  sessionId = conversation_id
  state backend = OpenJiuwen native Checkpointer, such as InMemoryCheckpointer / RedisCheckpointer
  state value = OpenJiuwen 自己的 agent / workflow / graph checkpoint
```

也就是说，业务方只需要保证 `agentStateKey` 稳定；OpenJiuwen 内部保存哪些字段、如何序列化、何时恢复，仍交给 OpenJiuwen 的 `Runner` / `Checkpointer` 处理。runtime 不再提供 `BaseKVStore` adapter；需要持久化时由业务按 OpenJiuwen 标准方式选择 Redis 或其他 checkpointer。

### 4.2 AgentExecutionContext

`AgentExecutionContext` 增加：

- `getAgentStateKey()` / `setAgentStateKey(...)`：暴露业务指定的状态 key。
- `getAgentState()` / `setAgentState(...)`：读取或替换 runtime 预加载状态。
- `replaceAgentState(...)`：由 Provider 或 Adapter 写回新的状态 map。

这里不暴露 `AgentStateStore`，避免 Agent Adapter 直接绑定存储后端。

### 4.3 Dispatcher Load / Save

`EngineDispatcher` 的 load/save 只面向 `AgentExecutionContext` 里的框架无关状态 map，主要服务手工桥接型 Provider。它不是 OpenJiuwen checkpoint 的主存取路径；OpenJiuwen 的主路径是 `Runner` / OpenJiuwen 自带 `Checkpointer`。

对需要手工桥接状态的框架，`EngineDispatcher` 在执行 handler 前：

1. 从 `AgentExecutionContext` 解析 state key。
2. 通过 `AgentStateStore.load(key)` 加载状态。
3. 把状态放入 `AgentExecutionContext`。
4. 使用 `AgentRuntimeProviders.execute(handler, context)` 执行 handler 与 Provider 链。
5. 在 stream close 后读取 context 状态，并通过 `AgentStateStore.save(key, state)` 保存。

`save` 必须发生在 try-with-resources 之后，因为 Provider 的 `afterExecute(context)` 挂在 stream close 语义上；提前保存会保存旧状态或空状态。

如果 handler / Provider 没有调用 `context.replaceAgentState(...)` 或 `setAgentState(...)`，dispatcher 不会额外保存 OpenJiuwen checkpoint。OpenJiuwen checkpoint 由其原生 checkpointer 在执行过程中自行保存到 runtime 提供的 KV 后端。

### 4.4 Provider Composition

为避免深继承树，`AgentRuntimeHandler` 提供默认 `providers()`：

- 普通 handler 默认返回空列表。
- handler 可以只实现 `AgentRuntimeHandler`；如果需要自定义 A2A Agent Card，再额外提供可选的 `AgentCardProvider` Bean。
- 需要能力扩展的 handler 直接重写 `providers()`，返回自己的 Provider 列表；生产侧不再提供共享抽象基类，避免为了便利方法形成新的继承入口。
- `AgentRuntimeProviders.execute(...)` 统一执行 Provider 链。
- Provider 的 `beforeExecute(context)` 按注册顺序执行，`afterExecute(context)` 按反向顺序执行。
- 如果某个 `beforeExecute(context)` 失败，只反向清理已经成功进入的 Provider，不执行 handler 本体。

核心点：**能力用组合，不用继承树**。

### 4.4.1 Agent Card Metadata Split

本轮主动把 Agent Card 声明从具体 handler 实现中摘出来。原因是：

- `AgentRuntimeHandler` 的职责是执行一个业务 Agent。
- `AgentCardProvider` 的职责是声明这个 runtime 对外暴露的 A2A 元数据。
- 执行职责和协议元数据职责可以同时由一个类承担，但不应通过执行基类强制绑定。
- OpenJiuwen handler 当前只实现 `AgentRuntimeHandler`，保持 adapter 关注执行和状态桥接。
- Access 层优先使用可选 `AgentCardProvider` Bean；如果没有 provider，就使用默认 Agent Card。

这个拆分给后续框架接入留出两条路径：简单业务方只实现 `AgentRuntimeHandler`，并在 `providers()` 中声明需要的状态、沙箱、工具覆盖等 Provider；需要自定义 Agent Card 时，再额外提供 `AgentCardProvider` Bean 或直接实现该接口。复杂业务方可以把 handler、card provider、state provider 分别作为独立 Bean 组合起来。

当前公开 Provider：

- `AgentRuntimeProvider`：通用生命周期 Provider。
- `StateProvider`：状态恢复/导出 Provider 标记，给缺少原生 checkpoint 的 Agent 框架或未来 Mem 辅助上下文打样；OpenJiuwen checkpoint 主路径不依赖它。
- `AgentCardProvider`：可选 Agent Card 声明 Provider。它不属于执行职责，OpenJiuwen handler 当前不强制实现它。

本轮删除 `AbstractStatefulAgentRuntimeHandler`，并进一步删除只被测试使用的 `AbstractAgentRuntimeHandler`。需要手工桥接状态的框架直接实现 `AgentRuntimeHandler`，然后通过 `providers()` 注册自己的 `StateProvider`；具备原生 checkpoint 的框架优先接入自己的 checkpointer 后端。

### 4.5 OpenJiuwen Native Checkpointer Configuration

调研 OpenJiuwen 0.1.7 / 0.1.12 后，本轮修正为：OpenJiuwen adapter 不再手工调用 `AgentSessionApi.updateState(...)` 和 `dumpState()` 搬运状态，也不再维护 runtime 自己的 OpenJiuwen KV 后端适配层，而是使用 OpenJiuwen 原生 Runner / Checkpointer 生命周期。

OpenJiuwen 文档与源码主线是：

- `Runner.runAgent(...)` 会准备 `AgentSessionApi`。
- `AgentSessionApi.preRun(inputs)` 会调用 `CheckpointerFactory.getCheckpointer().preAgentExecute(...)` 恢复 agent state。
- `AgentSessionApi.postRun()` 会调用 `postAgentExecute(...)` 保存 agent state。
- `RunnerImpl` 会优先从输入 map 中读取 `conversation_id` 作为 session id；没有时才回退到 `default_session`。

因此 runtime 的 OpenJiuwen adapter 只做三件事：

1. 把 `context.getAgentStateKey()` 写入 OpenJiuwen input 的 `conversation_id`。
2. 对外提供 `openJiuwenConversationId(context)`，让子类调用 `Runner.runAgent(agent, input, conversationId, null)`。
3. 不在每次执行 finally 中调用 `Runner.release(...)`；release 只代表会话结束或业务显式清理，否则会破坏多轮恢复。

当前 sample 在配置阶段同时实例化 `InMemoryCheckpointer` 和 `RedisCheckpointer` 两个 OpenJiuwen 原生 checkpointer 候选，默认通过 `CheckpointerFactory.setDefaultCheckpointer(...)` 选择 `InMemoryCheckpointer`，便于本地 E2E。需要演示持久化路径时，通过 `sample.openjiuwen.checkpointer=redis` / `SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER=redis` 和 `sample.openjiuwen.redis-url` / `SAA_SAMPLE_OPENJIUWEN_REDIS_URL` 切换到 `RedisCheckpointer`；OpenJiuwen adapter 不需要变化。

## 5. Failure Semantics

- state load 失败：fail closed，不调用 handler，通过控制面返回 `AGENT_STATE_LOAD_FAILED`。
- handler 执行失败：转换成 control-plane `FAILED`，保持原有单出口语义。
- Provider `afterExecute` 失败：记录 warn，不把已经完成的任务反转成失败，避免双终态。
- state save 失败：记录 warn，不覆盖业务执行结果；生产态后续需要告警、重试或补偿队列。

## 6. Feature Checklist

| Feature | Status | Notes |
|---|---|---|
| Business-supplied state key | Implemented | `agentStateKey` / `stateKey`，fallback `taskId` |
| Replaceable state store | Implemented | `AgentStateStore` + `InMemoryAgentStateStore` |
| Dispatcher load/save | Implemented | 面向手工桥接型 Provider 的框架无关 state map；OpenJiuwen checkpoint 不走这条主路径 |
| Composable runtime providers | Implemented | `AgentRuntimeProvider` + `AgentRuntimeProviders` |
| Optional Agent Card provider | Implemented | `AgentCardProvider` 是可选能力；handler 不必强制实现 |
| State provider marker | Implemented | `StateProvider`，用于可选生命周期桥接，不强制所有框架使用 |
| Store-free handler | Implemented | handler 只读写 `AgentExecutionContext` |
| OpenJiuwen native checkpointer configuration | Implemented | 使用稳定 `conversation_id` 接入 OpenJiuwen `Runner` / `Checkpointer` |
| OpenJiuwen checkpointer direct setup | Implemented | sample 同时实例化 InMemory / Redis 候选，默认 set InMemory，可配置切换 Redis |
| Snapshot/revision | Deferred | 不在当前最小版本实现 |
| Mem integration | Deferred | 后续作为独立 Provider 或 middleware 扩展 |

## 7. Open/Closed And Dependency Inversion Audit

- 新存储后端通过实现 `AgentStateStore` 注入，不修改 `EngineDispatcher`。
- 新 Agent 框架通过实现 `AgentRuntimeHandler` 接入执行面；如需自定义 A2A Agent Card，再额外提供 `AgentCardProvider`。
- 新能力通过 `AgentRuntimeProvider` / `StateProvider` 注入，不新增层层叠加的抽象基类；具备原生 checkpoint 的框架优先使用自己的 checkpointer 配置。
- handler 依赖 `AgentExecutionContext` 这个抽象 carrier，不依赖具体 Store。
- Store 不理解 OpenJiuwen、Mem、Sandbox 或业务状态结构。

## 8. Mem Extension Plan

Mem 不应复用 `AgentStateStore` 存正文记忆。后续建议：

- Agent State 只保存 `memoryRef`、`checkpointRef`、`cursor` 等小对象。
- Mem 的 compact、budget、vector retrieval、长期检索由 Mem backend 负责。
- Mem 可以通过新的 Provider 读取/写入 context，不需要新增 `AbstractMemoryAgentRuntimeHandler`；如果未来 Mem 有自己的持久化后端，也应优先桥接后端而不是强迫所有 handler 手工搬运状态。

## 9. Verification Plan

建议执行：

```bash
wsl -d Ubuntu-24.04 -- bash -lc 'cd /mnt/d/repo/spring-ai-ascend && ./mvnw -pl agent-runtime -Dtest=EngineDispatcherTest,InMemoryAgentStateStoreTest,AgentRuntimeProviderTest,OpenJiuwenAgentRuntimeHandlerTest test'
```

覆盖点：

- dispatcher 能按业务 state key 加载/保存 state。
- dispatcher 能兼容 `stateKey` 旧别名，并在未提供业务 key 时 fallback 到 `taskId`。
- Provider 可 restore/export state，且该能力是可选手工桥接路径。
- Provider before 失败时只清理已进入 Provider。
- state load 失败 fail closed。
- OpenJiuwen session state 可跨同一 `conversation_id` / `agentStateKey` 恢复，且依赖 OpenJiuwen 自带 checkpointer，不依赖 runtime 手工 `dumpState/updateState`。

## 10. Self-Audit

Open findings:

- `AgentStateStore.save` 当前没有 CAS/fencing，后续 durable backend 必须补齐。
- W1 save 失败只记录日志；生产态需要告警和补偿。
- Mem 未实现，需要单独 proposal/PR。
- OpenJiuwen sample 默认使用 `InMemoryCheckpointer` 打样，同时保留 `RedisCheckpointer` 配置分支；生产态仍需业务提供真实 Redis 服务、连接安全与运维策略。

No ship-blocking finding for the W1 in-memory Agent State capability.
