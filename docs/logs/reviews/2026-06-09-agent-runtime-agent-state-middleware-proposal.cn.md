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

`agent-runtime` 已经通过 `AgentRuntimeHandler` 统一承载不同 Agent 框架，但此前 `AgentExecutionContext`
只携带 `scope + input`，没有框架无关的执行状态恢复入口。同类开源组件的状态模块经验提示我们：
Agent 执行状态需要 checkpoint/save/restore，但组件不应该直接持有 Session 或 Store，否则会把存储职责散落到
每个 Agent Adapter 内部。

本提案落地第一版 Agent State 中间件：runtime 统一加载和保存状态，具体 Agent Adapter 只通过可选钩子把
框架内部状态导入/导出到 `AgentExecutionContext`。

## 2. Scope statement

本次变更主视图是 `development`，影响 `agent-runtime` 的 engine / engine.spi / engine.service 代码组织。
逻辑上新增一个 runtime-owned middleware：Agent State。它不改变 A2A 协议、不改变 Session 语义、不引入 Mem。

## 3. Root cause / strongest interpretation

1. **Observed failure / motivation**: Agent 执行中断后，runtime 缺少一个统一位置保存和恢复框架无关的执行状态。
2. **Execution path**: `EngineDispatcher.runHandler(...) -> AgentRuntimeHandler.execute(context) -> resultAdapter().adapt(...) -> TaskControlClient`。
3. **Root cause**: `AgentExecutionContext` 原先只有 `scope` 和 `input`，handler 没有标准 state carrier；如果让 handler 自己持有 store，又会违反依赖倒置，把状态存储细节泄漏到具体 Agent Adapter。
4. **Evidence**: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/AgentExecutionContext.java`、`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/EngineDispatcher.java`。

## 4. Proposed change

### 4.1 Runtime-owned Agent State API

新增 `com.huawei.ascend.runtime.engine.service`：

- `AgentStateKey`：以 `tenantId + userId + sessionId + taskId + agentId` 标识一个 task-scoped state。
- `AgentStateSnapshot`：不可变状态快照，包含 `revision`、`values`、`updatedAt`。
- `AgentStateStore`：状态存储抽象，提供 `load/save/delete`。
- `InMemoryAgentStateStore`：W1 默认内存实现。
- `NoopAgentStateStore`：兼容手工构造旧 dispatcher 的无状态实现。

### 4.2 ExecutionContext 作为状态 carrier

`AgentExecutionContext` 增加：

- `getAgentState()`：读取 dispatcher 预加载的 state。
- `setAgentState(...)`：由 adapter 显式替换 state。
- `replaceAgentState(...)`：替换 payload 并递增 revision。

这里不暴露 `AgentStateStore`，避免 Agent Adapter 直接绑定存储后端。

### 4.3 Dispatcher 统一 load / save

`EngineDispatcher` 在运行 handler 前执行：

1. 根据 `EngineExecutionScope` 生成 `AgentStateKey`。
2. 从 `AgentStateStore` 加载 state。
3. 构造带 state 的 `AgentExecutionContext`。
4. 执行 handler。
5. 在 stream close 之后保存 context 中的新 state。

`save` 必须发生在 try-with-resources 之后。原因是 `AbstractStatefulAgentRuntimeHandler` 的
state extension `afterExecute(context)` 挂在 `Stream.onClose()` 上，只有 dispatcher 关闭 stream 之后，Adapter 才完成状态导出。
如果在 try 块内部保存，会保存到旧 state 或空 state。

### 4.4 可组合 Runtime Extension

为避免后续每增加一个能力就新增一层抽象基类，`AgentRuntimeHandler` 增加默认 `extensions()`：

- 普通 handler 默认返回空列表，不受影响。
- 继承 `AbstractAgentRuntimeHandler` 的 handler 可以在构造阶段通过 `addRuntimeExtension(...)` set 进多个能力。
- `EngineDispatcher` 通过 `AgentRuntimeExtensions.execute(handler, context)` 统一执行扩展链，保证能力顺序、流关闭和异常隔离一致。
- extension 的 `beforeExecute(context)` 按注册顺序执行，`afterExecute(context)` 按反向顺序执行。
- 如果某个 `beforeExecute(context)` 失败，运行时只反向清理已经成功进入的 extension，不执行 handler 本体，也不调用未进入 extension 的 `afterExecute(context)`。

本轮新增的 `AbstractStatefulAgentRuntimeHandler` 不再把状态能力写死在继承链里，而是作为兼容和示例基类：它在构造时注册一个 Agent State extension，并继续暴露：

- `beforeExecute(context)`：把 `context.getAgentState()` 恢复到具体 Agent 框架。
- `doExecute(context)`：执行具体 Agent 框架。
- `afterExecute(context)`：把具体 Agent 框架状态导出到 `context.replaceAgentState(...)`。

该基类不持有 `AgentStateStore`，也不调用 `load/save`。普通 handler 仍可继续实现 `AgentRuntimeHandler` 或继承
`AbstractAgentRuntimeHandler`。后续工具调用 override、沙箱 override、trace、限流等能力应优先作为新的 `AgentRuntimeExtension` 注入，而不是继续叠加 `AbstractXxxAgentRuntimeHandler`。

### 4.5 Failure semantics

- state load 失败：fail closed，不调用 handler，通过控制面返回 `AGENT_STATE_LOAD_FAILED`。
- handler 执行失败：仍转换为 control-plane `FAILED`，保持原有单出口语义。
- state export hook 失败：只记录 warn，不把已完成任务反转成失败，避免双终态。
- state save 失败：只记录 warn，不覆盖业务执行结果。后续 durable backend 可扩展为告警、重试或补偿队列。

## 5. Implemented feature points

| Feature | Status | Notes |
|---|---|---|
| Agent State key model | Implemented | task-scoped，包含 tenant/user/session/task/agent |
| Immutable state snapshot | Implemented | 带 revision，后续可扩展 CAS/fencing |
| Replaceable state store | Implemented | `AgentStateStore` 抽象 + `InMemoryAgentStateStore` 默认实现 |
| Dispatcher load/save | Implemented | engine 统一拥有状态加载与保存时机 |
| Composable runtime extensions | Implemented | `AgentRuntimeExtension` + `AgentRuntimeExtensions` 统一编排 |
| Optional restore/export hooks | Implemented | `AbstractStatefulAgentRuntimeHandler` 通过 state extension 提供兼容样例 |
| Store-free handler base class | Implemented | handler 基类不持有 store |
| Extension enter/cleanup discipline | Implemented | before 失败时清理已进入能力，避免后续沙箱/工具能力泄漏资源 |
| OpenJiuwen state bridge | Implemented | `OpenJiuwenAgentRuntimeHandler` 通过 `AgentSessionApi.dumpState()/updateState(...)` 在 runtime Agent State 中保存/恢复 OpenJiuwen 状态 |
| State load fail closed | Implemented | 不执行 handler |
| Export/save failure isolation | Implemented | 不制造双终态 |
| Mem integration | Deferred | 不在本轮实现，后续作为独立 middleware |

## 6. Open/Closed and dependency inversion audit

本实现满足开闭原则：

- 新存储后端通过实现 `AgentStateStore` 注入，不修改 `EngineDispatcher`。
- 新 Agent 框架可通过继承 `AbstractAgentRuntimeHandler` 并 set runtime extension 增加能力，不修改 engine core。
- 需要状态的 Agent 框架可暂时继承 `AbstractStatefulAgentRuntimeHandler`，也可直接注册自己的 `AgentRuntimeExtension`。
- 不需要状态的 Agent 不受影响。

本实现满足依赖倒置：

- engine core 依赖 `AgentStateStore` 抽象，不依赖 Redis/JDBC/InMemory 细节。
- handler 依赖 `AgentExecutionContext` 抽象 carrier，不依赖 store。
- `AgentRuntimeExtension` 依赖 `AgentExecutionContext`，不依赖具体 handler 实现。
- `AbstractStatefulAgentRuntimeHandler` 只负责把 Agent State 转换注册为一个可选能力。

## 7. Mem extension plan

Mem 不应复用 `AgentStateStore` 存储正文记忆。后续建议新增独立 middleware：

- `MemoryStore` 或等价接口：负责短期/长期记忆。
- `AgentExecutionContext` 可携带 memory handle 或 memory service 引用。
- Agent State 可以只保存 `memoryRef`、`checkpointRef`、`cursor` 等小对象。
- Mem 的 compact、budget、vector retrieval、长期检索等能力由 Mem backend 负责。

这样 Mem 可以独立扩展，不破坏 Agent State 的 task checkpoint 职责。

## 8. Alternatives considered

| Alternative | Why rejected |
|---|---|
| Handler 直接持有 `AgentStateStore` | 会把存储职责泄漏给 Adapter，破坏依赖倒置 |
| 把 state 存进 Runtime Session | Session 只管理外部会话和当前输入，不应承载 Agent 框架内部状态 |
| 把 Mem 和 Agent State 合并 | Memory 与执行 checkpoint 生命周期不同，后续 compact/vector 检索会污染 state 模型 |
| 强制所有 handler 继承 stateful 基类 | 不需要状态的 handler 会被迫接受无关语义 |

## 9. Verification plan

已执行：

```bash
wsl -d Ubuntu-24.04 -- bash -lc 'cd /mnt/d/repo/spring-ai-ascend && ./mvnw -pl agent-runtime -Dtest=EngineDispatcherTest,InMemoryAgentStateStoreTest,AbstractAgentRuntimeHandlerTest,AbstractStatefulAgentRuntimeHandlerTest,OpenJiuwenAgentRuntimeHandlerTest test'
```

覆盖点：

- dispatcher 可跨同一 task 加载/保存 state。
- stateful hook 可 restore/export state。
- state export hook 失败不制造双终态。
- state load 失败 fail closed。
- 普通 `AbstractAgentRuntimeHandler` 不受影响；OpenJiuwen handler 已接入 stateful 基类，并验证 OpenJiuwen session state 可跨同一 task 恢复。

## 10. Rollout

- Wave: W1
- 默认后端：`InMemoryAgentStateStore`
- 生产态后续演进：Redis/JDBC/其他 durable backend + CAS/fencing + save failure retry/alert
- Freeze impact: no frozen L0/L1 artifact is modified in this proposal.

## 11. Self-audit

Open findings:

- `AgentStateStore.save` 目前没有 CAS/fencing，后续 durable backend 必须补齐。
- W1 save 失败只记录日志；生产态需要告警和补偿。
- Mem 仍未实现，需单独 proposal/PR。

No ship-blocking finding for the W1 in-memory Agent State capability.
