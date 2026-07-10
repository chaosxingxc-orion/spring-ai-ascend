---
level: L1-HLD
TAG:
  - glossary
  - terminology
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - logical.md
  - process.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-runtime L1 术语表

## 目的

本文档定义 `agent-runtime` L1 设计中容易混淆的模块内术语。全局术语仍以 `architecture/L0-Top-Level-Design/glossary.md` 为准；本文只补充 runtime 模块内部语义。

## 术语

| 术语 | 当前含义 |
|---|---|
| task-owning runtime SDK | `agent-runtime` 对 runtime Task 生命周期负责：接收 A2A 请求、创建或推进 Task、调用本地 Agent handler，并把结果折叠回 A2A Task 状态。历史或代码中的 run 命名只能理解为 invocation/trajectory 兼容语义，不引入第二个生命周期 owner。 |
| 本地 Agent handler | 当前 JVM 内唯一注册的 `AgentRuntimeHandler` bean。当前版本 host 只允许一个本地 handler；多个本地 handler / 多本地 Agent 路由属于未来版本提案范围。 |
| 框架适配器 | 位于 `engine.openjiuwen`、`engine.agentscope` 等包中的适配实现，负责把中立 `AgentExecutionContext` 转换为具体框架输入，并把框架原生输出转换为 `AgentExecutionResult`。 |
| A2A 协议桥 | 位于 `engine.a2a` 和 `boot` 边界内的协议相关实现，包括 A2A JSON-RPC controller、Agent Card、A2A executor、远端 Agent card cache、outbound adapter 和 remote invocation orchestration。 |
| runtime Task | A2A SDK 管理的 runtime 层执行状态单元。它承载 submitted、working、input-required、completed、failed、canceled、rejected 等生命周期状态。 |
| Session / context | A2A context 或 runtime 会话范围，用于把多次消息和 Task 关联到同一交互上下文；它不等同于业务 Agent checkpoint。 |
| Agent checkpoint | 具体 Agent 框架或外部状态能力管理的业务执行状态。`agent-runtime` 只传递状态键、上下文和可选状态快照；FEAT-003 可提供 Redis cache 桥接，但不解释或接管业务 checkpoint 语义。 |
| AgentExecutionContext | 传给 handler 的协议中立执行上下文，包含身份、输入类型、消息、变量、Agent 状态键和可选状态快照。A2A wire 类型不穿透到该模型。 |
| AgentExecutionResult | handler 结果适配后的中立结果语义，包含 `OUTPUT`、`COMPLETED`、`FAILED`、`INTERRUPTED`。它不是 A2A wire response，也不是具体 Agent 框架原生输出。 |
| UserInputInterrupt | `AgentExecutionResult.INTERRUPTED` 的人工输入子语义，路由到 `emitter.requiresInput(...)`，使 Task 进入 `INPUT_REQUIRED`。 |
| RemoteAgentInterrupt | `AgentExecutionResult.INTERRUPTED` 的远端 Agent 调用子语义，由 `A2aRemoteInvocationOrchestrator` 执行 outbound 调用；远端完成后以 `REMOTE_RESUME` 重新进入本地 handler。 |
| REMOTE_RESUME | `AgentExecutionContext.inputType` 的一种，表示本地 handler 正在接收远端 Agent 调用结果回灌，而不是普通用户消息。 |
| RemoteAgentToolSpec | 协议中立的远端 Agent 工具规格，由远端 Agent Card 投影得到，供框架适配器作为工具消费。 |
| `agent-bus` 对齐 | 当前仅表示 `agent-runtime` 可在架构语义上对齐或映射 `agent-bus` 的中立执行词汇；当前 POM 不形成对 `agent-bus` 的编译依赖。 |
