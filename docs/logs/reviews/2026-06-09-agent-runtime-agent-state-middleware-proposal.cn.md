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

本提案落地第一版 Agent State 中间件：runtime 统一加载和保存状态，具体 Agent Adapter 只通过可选 Provider 把框架内部状态导入/导出到 `AgentExecutionContext`。Provider 不持有 Store，Store 也不理解具体 Agent 框架。

## 2. Scope Statement

本次变更主视图是 `development`，影响 `agent-runtime` 的 engine / engine.spi / engine.service 代码组织。它不改变 A2A 协议、不改变 Session 语义、不引入 Mem。

## 3. Root Cause / Strongest Interpretation

1. Agent 执行中断后，runtime 缺少一个统一位置保存和恢复框架无关的执行状态。
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

### 4.2 AgentExecutionContext

`AgentExecutionContext` 增加：

- `getAgentStateKey()` / `setAgentStateKey(...)`：暴露业务指定的状态 key。
- `getAgentState()` / `setAgentState(...)`：读取或替换 runtime 预加载状态。
- `replaceAgentState(...)`：由 Provider 或 Adapter 写回新的状态 map。

这里不暴露 `AgentStateStore`，避免 Agent Adapter 直接绑定存储后端。

### 4.3 Dispatcher Load / Save

`EngineDispatcher` 在执行 handler 前：

1. 从 `AgentExecutionContext` 解析 state key。
2. 通过 `AgentStateStore.load(key)` 加载状态。
3. 把状态放入 `AgentExecutionContext`。
4. 使用 `AgentRuntimeProviders.execute(handler, context)` 执行 handler 与 Provider 链。
5. 在 stream close 后读取 context 状态，并通过 `AgentStateStore.save(key, state)` 保存。

`save` 必须发生在 try-with-resources 之后，因为 Provider 的 `afterExecute(context)` 挂在 stream close 语义上；提前保存会保存旧状态或空状态。

### 4.4 Provider Composition

为避免深继承树，`AgentRuntimeHandler` 提供默认 `providers()`：

- 普通 handler 默认返回空列表。
- handler 可以只实现 `AgentRuntimeHandler`；如果需要自定义 A2A Agent Card，再额外提供可选的 `AgentCardProvider` Bean。
- 继承 `AbstractAgentRuntimeHandler` 的 handler 可以在构造阶段通过 `addRuntimeProvider(...)` 注入多个能力；直接实现接口的 handler 可以返回自己的 `providers()` 列表。
- `AgentRuntimeProviders.execute(...)` 统一执行 Provider 链。
- Provider 的 `beforeExecute(context)` 按注册顺序执行，`afterExecute(context)` 按反向顺序执行。
- 如果某个 `beforeExecute(context)` 失败，只反向清理已经成功进入的 Provider，不执行 handler 本体。

当前公开 Provider：

- `AgentRuntimeProvider`：通用生命周期 Provider。
- `StateProvider`：状态恢复/导出 Provider 标记，给 OpenJiuwen、未来 Mem 桥接和其他 Agent 框架打样。
- `AgentCardProvider`：可选 Agent Card 声明 Provider。它不属于执行职责，OpenJiuwen handler 当前不强制实现它。

本轮删除 `AbstractStatefulAgentRuntimeHandler`。需要状态的框架直接实现 `AgentRuntimeHandler`，或选择继承 `AbstractAgentRuntimeHandler`，然后注册自己的 `StateProvider`。

### 4.5 OpenJiuwen State Bridge

OpenJiuwen 自身已经提供 `AgentSessionApi.updateState(...)` 和 `dumpState()`。因此 runtime 只做适配：

- 执行前：`OpenJiuwenAgentRuntimeHandler.openJiuwenSession(...)` 从 context state 中读取 OpenJiuwen 状态，并调用 `session.updateState(...)`。
- 执行后：内部 `OpenJiuwenStateProvider` 从当前 session 调用 `dumpState()`，再写回 `context.replaceAgentState(...)`。
- OpenJiuwen handler 直接实现 `AgentRuntimeHandler`，不继承 runtime 基类，也不再继承额外 stateful 基类。

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
| Dispatcher load/save | Implemented | Engine 统一拥有状态加载与保存时机 |
| Composable runtime providers | Implemented | `AgentRuntimeProvider` + `AgentRuntimeProviders` |
| Optional Agent Card provider | Implemented | `AgentCardProvider` 是可选能力；handler 不必强制实现 |
| State provider marker | Implemented | `StateProvider` |
| Store-free handler | Implemented | handler 只读写 `AgentExecutionContext` |
| OpenJiuwen state bridge | Implemented | 使用 `AgentSessionApi.dumpState()/updateState(...)` |
| Snapshot/revision | Deferred | 不在当前最小版本实现 |
| Mem integration | Deferred | 后续作为独立 Provider 或 middleware 扩展 |

## 7. Open/Closed And Dependency Inversion Audit

- 新存储后端通过实现 `AgentStateStore` 注入，不修改 `EngineDispatcher`。
- 新 Agent 框架通过实现 `AgentRuntimeHandler` 接入执行面；如需自定义 A2A Agent Card，再额外提供 `AgentCardProvider`。
- 新能力通过 `AgentRuntimeProvider` / `StateProvider` 注入，不新增层层叠加的抽象基类。
- handler 依赖 `AgentExecutionContext` 这个抽象 carrier，不依赖具体 Store。
- Store 不理解 OpenJiuwen、Mem、Sandbox 或业务状态结构。

## 8. Mem Extension Plan

Mem 不应复用 `AgentStateStore` 存正文记忆。后续建议：

- Agent State 只保存 `memoryRef`、`checkpointRef`、`cursor` 等小对象。
- Mem 的 compact、budget、vector retrieval、长期检索由 Mem backend 负责。
- Mem 可以通过新的 Provider 读取/写入 context，不需要新增 `AbstractMemoryAgentRuntimeHandler`。

## 9. Verification Plan

建议执行：

```bash
wsl -d Ubuntu-24.04 -- bash -lc 'cd /mnt/d/repo/spring-ai-ascend && ./mvnw -pl agent-runtime -Dtest=EngineDispatcherTest,InMemoryAgentStateStoreTest,AgentRuntimeProviderTest,OpenJiuwenAgentRuntimeHandlerTest test'
```

覆盖点：

- dispatcher 能按业务 state key 加载/保存 state。
- dispatcher 能兼容 `stateKey` 旧别名，并在未提供业务 key 时 fallback 到 `taskId`。
- Provider 可 restore/export state。
- Provider before 失败时只清理已进入 Provider。
- state load 失败 fail closed。
- OpenJiuwen session state 可跨同一 state key 恢复。

## 10. Self-Audit

Open findings:

- `AgentStateStore.save` 当前没有 CAS/fencing，后续 durable backend 必须补齐。
- W1 save 失败只记录日志；生产态需要告警和补偿。
- Mem 未实现，需要单独 proposal/PR。

No ship-blocking finding for the W1 in-memory Agent State capability.
