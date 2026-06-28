---
level: L1-HLD
TAG:
  - process-view
  - runtime-flow
  - concurrency-boundary
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - logical.md
  - development.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-core L1 架构进程视图

## 1. 进程视图定位

本文档描述 `agent-core` 的主要执行流程、执行角色、同步/异步边界、流式片段、中断点表达、错误处理和资源约束。openJiuwen SDK 本地模式可以在单 JVM 内直接执行；平台模式下，服务侧 Task、Session / Context shell 和中间件服务执行由 `agent-runtime` 与 `agent-middleware` 居中。

## 2. 运行时参与者

| 参与者 | 运行职责 |
|---|---|
| Agent | 面向调用方的执行入口，包含 `LlmAgent`、`WorkflowAgent`、`ReActAgent` 等。 |
| Controller / EventHandler | 承接意图识别、任务推进、事件处理和 Agent 控制。 |
| Runner | 执行组件编排、回调、队列、资源管理和流式片段连接。 |
| Local Session helper | SDK 本地模式下保存对话、工作流、节点、流式、checkpoint 和恢复状态；平台模式下不替代 runtime Session。 |
| Workflow / Graph | 组织组件、边、条件、循环和图执行。 |
| Middleware invocation | 生成模型、记忆、检索、工具代理、沙箱和 guardrail 调用意图，并消费返回结果。 |
| Callback / Logging | 处理执行组件观测事件、错误包装和输出管控。 |
| Runtime / Middleware boundary | 平台模式下由 `agent-runtime` 调用 `agent-middleware` 执行 provider 服务。 |

## 3. ReActAgent 执行流程

```text
User input
  -> ReActAgent / LlmAgent
  -> local session helper or runtime-provided context
  -> middleware invocation intent for context / memory
  -> model invocation intent returns reasoning or tool call
  -> tool invocation intent returns observation
  -> observation appended to local state or runtime session projection
  -> loop continues or final answer emitted
```

ReAct 流程的关键边界是：Agent loop 负责组织推理、行动、观察和终止条件；模型服务、工具代理服务和记忆服务的具体执行在平台模式下归 `agent-middleware`，由 `agent-runtime` 受治理调用。

## 4. WorkflowAgent 执行流程

```text
User input
  -> WorkflowAgent
  -> WorkflowController identifies intent / workflow
  -> local workflow session helper or runtime execution projection
  -> Workflow graph schedules component
  -> ComponentExecutable runs
  -> middleware invocation intent when component needs external capability
  -> Output / chunk / state update
  -> next component, interrupt, fail, or complete
```

Workflow 执行支持条件、循环、子工作流、LLM 组件、工具组件、检索组件和记忆组件。组件内部状态保存在本地 helper 或 runtime 投影中；LLM、工具、检索和记忆组件在平台模式下产生中间件调用意图，流式片段通过 Runner/callback 或 runtime 服务流回传。

## 5. 中断与恢复

中断产生于用户输入等待、工具审批、工作流组件暂停、长任务 checkpoint 或远端能力不可用等场景。

```text
component or agent requests interruption
  -> component-local interruption point saved
  -> caller receives prompt or interrupt event
  -> user/tool/system provides resume payload
  -> local helper or runtime restores execution projection
  -> Agent or Workflow continues from saved point
```

agent-core 负责表达和解释组件内部恢复点。SDK 本地模式可以通过本地 Session/checkpoint 保存恢复状态；平台模式下 Task 级中断、恢复入口和服务侧状态归 `agent-runtime`，外部持久化介质和 provider 状态由 runtime/middleware 协作治理。

## 6. 流式输出

流式输出存在于模型响应片段、workflow chunk、runner callback、tool/sysop stream 和候选高阶资产的 message 中。

```text
producer
  -> stream chunk / event
  -> callback or queue
  -> local helper / tracer / runtime stream projection records
  -> caller consumes iterator, stream, or event handler
```

流式输出不改变状态归属。组件内部状态由 Agent/Workflow 推进；服务侧 Task 与 Session 状态由 runtime 推进。

## 7. 异步与并发边界

`agent-core-java` 使用 Java 21、Reactor、JDK 并发工具和外部 SDK 的异步能力。L1 视图中的并发边界包括：

- Workflow 图和组件可并发执行。
- 模型和工具调用意图可通过异步 I/O 或流式处理返回结果，provider 执行归 middleware。
- openJiuwen Agent Teams 候选资产可通过 in-process 或 JeroMQ 通信，但不作为 core 协同 owner。
- Runner/callback/queue 解耦执行与输出消费。
- Agent evolving online 能力可启动 gateway、judge、scheduler 等协作进程或服务；该能力归 `agent-evolve` 候选资产。

具体线程池、背压、重试、超时和队列容量属于 L2 或运维配置范围。

## 8. 错误处理

错误分为四类：

| 错误类别 | 归属 | 处理方式 |
|---|---|---|
| 配置错误 | SDK 初始化 | 抛出配置/校验异常，阻止执行。 |
| 模型/工具错误 | 中间件调用边界 | 包装为 ModelError、ToolError、ExternalServiceError 等，并交由 runtime/middleware 治理。 |
| 工作流/Agent 错误 | 执行组件内核 | 更新组件内部状态并通过 callback 或 runtime projection 输出。 |
| 安全/权限错误 | guardrail/sysop/workspace 边界 | 阻断工具或系统操作，返回风险或拒绝结果；服务执行归 runtime/middleware。 |

agent-core 不负责把错误转换为 A2A JSON-RPC 错误；这属于 agent-runtime 或服务化接入层职责。
