---
scope: version
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-003
status: active
dependency:
  - agent-runtime-release-features.cn.md
  - ../architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-003-agent-runtime-core-interface.cn.md
---

# agent-runtime 核心接口与状态边界 — 黑盒行为说明

## 1. 特性定位

本文合并 `agent-runtime` 接口设计说明与 2026-06-09 Agent State 中间件提案，作为当前 L1 设计入口。生成事实仍以 `architecture/facts/generated/*.json` 为准；本文只解释当前代码边界、模块交互和后续扩展原则。

## 2. 对外能力边界

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

## 3. 接入新 Agent 框架

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
