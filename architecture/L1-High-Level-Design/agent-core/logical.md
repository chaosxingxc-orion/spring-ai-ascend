---
level: L1-HLD
TAG:
  - logical-view
  - domain-model
  - architecture-fact
  - module-boundary
status: active
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - development.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-core L1 架构逻辑视图

## 1. 逻辑视图定位

`agent-core` 是面向 Java 应用的智能体开发框架和执行组件边界。逻辑视图描述该模块内部的领域对象、状态归属、职责分层和依赖方向，并说明 openJiuwen 实现基线中超出 core 逻辑边界的能力如何映射到其他 L0 模块。

## 2. 领域对象模型

### 2.1 Agent 与 Controller

Agent 是面向调用方的主要应用对象。当前实现包含：

- `LlmAgent`：LLM 对话 Agent。
- `WorkflowAgent`：封装 workflow 执行的任务型 Agent。
- `ReActAgent`：遵循 ReAct 循环的单 Agent。
- `ControllerAgent` / controller：承接意图识别、任务推进和事件处理。

Agent 不直接拥有服务端 Task 生命周期状态。它通过 workflow、runner 辅助、组件内部状态和中间件调用意图完成执行；模型、工具、记忆、检索、沙箱和安全护栏的 provider 执行由 `agent-middleware` 提供，并由 `agent-runtime` 在服务侧执行契约中协调。

### 2.2 Workflow 与 Component

Workflow 是结构化任务图，由组件、边、条件、循环和子工作流组成。

```text
Workflow
  -> WorkflowComponent
  -> ComponentExecutable
  -> ComponentComposable
  -> WorkflowExecutionState
  -> WorkflowOutput / WorkflowChunk
```

组件类型包括开始、结束、分支、循环、LLM、工具、知识检索、记忆读写和子工作流。Workflow 负责结构化流程表达；其中 LLM、工具、知识检索和记忆读写组件在平台模式下应产生中间件调用意图，由 `agent-runtime` 调用 `agent-middleware` 服务并把结果返回给执行组件继续推进。

### 2.3 Runner / Session / Checkpoint

Runner 是执行组件编排入口，SDK 本地 Session 是本地执行辅助边界，checkpoint 是组件内部恢复点与状态保存接缝。

```text
Runner
  -> callback / queue / resource manager
  -> AgentSession / WorkflowSession
  -> state / tracer / stream / checkpointer
```

SDK 本地 Session 状态只属于 `agent-core` 的本地执行模式。进入平台运行路径后，服务侧 Session / Context shell、Task 级中断恢复入口和生命周期状态归属 `agent-runtime`；`agent-core` 仅保留 workflow node、ReAct loop、component local state 等 Task 边界以下的细粒度执行状态。

### 2.4 中间件调用意图

模型、工具、存储、记忆和检索在 openJiuwen 实现基线中存在大量本地 API 与实现。L1 归属上，它们分为两层：

- `agent-core` 拥有：组件对模型、记忆、检索、工具代理、沙箱和 guardrail 的调用意图、调用参数、恢复点和结果消费逻辑。
- `agent-middleware` 拥有：模型服务、记忆服务、知识检索服务、工具代理服务、沙箱服务和安全护栏服务的具体执行、provider 适配、容量、幂等、审计和安全治理。

该分层允许 openJiuwen 仓内实现被复用，但复用时必须按 L0 边界拆分 owner。

## 3. 逻辑分层

```text
agent-core
  SDK facade
    Agent / WorkflowAgent / ReActAgent / examples-facing builders
  application and controller
    LLM agent / workflow agent / intent / event handler
  execution kernel
    workflow / graph / runner helper / local session helper / callback / stream chunk
  middleware invocation
    model intent / memory intent / retrieval intent / tool intent / sandbox intent / guardrail intent
  optional implementation assets
    openJiuwen foundation / memory / retrieval / sysop / harness / teams / evolving
```

该分层不是严格目录等价关系，而是围绕 Agent 执行职责形成的逻辑责任面。

## 4. 状态模型

### 4.1 Agent 状态

Agent 状态包括对话历史引用、当前执行组件位置、工具调用上下文、记忆引用、工作流执行位置和中断信息。`agent-core` 只拥有 Task 边界以下的执行状态；服务侧 Task、Session / Context shell、Agent definition 注册和恢复入口归属 `agent-runtime`。

### 4.2 Workflow 状态

Workflow 状态围绕组件执行推进：

| 状态对象 | 归属 | 含义 |
|---|---|---|
| `WorkflowExecutionState` | workflow 执行 | 工作流整体执行阶段。 |
| `NodeStatus` | component / tracer helper | 节点/组件执行状态。 |
| `WorkflowStateCollection` | local workflow helper | 工作流状态快照集合；平台模式下不能替代 runtime Session。 |
| `WorkflowChunk` | stream output | 工作流流式输出片段。 |

### 4.3 Task / Team 状态

任务状态在不同上下文中有不同归属：

- `core.common.task_manager.TaskStatus` 与 `core.controller.schema.TaskStatus` 服务单 Agent/控制器任务。
- `agent_teams.schema.task.TaskStatus` 与 `agent_teams.schema.status.*` 服务团队任务和成员状态。

L1 约束是：这些状态只能作为 openJiuwen 本地 SDK 或候选资产事实。平台级 Task 生命周期、Task tree、成员实体生命周期和跨节点协同状态不得由 `agent-core` 单独定义为真相源。若多个智能体实体天然存在，其协同应由 `agent-runtime` 居中；若任务中只是为上下文拆分而派生无状态子执行，则应建模为 Task tree / 子 Task 协作，而不是 core 内部多智能体 owner。

### 4.4 Memory / Retrieval 状态

Memory、Retrieval 和 Store 的 provider 状态归属 `agent-middleware`、外部 provider 或客户系统。`agent-core` 负责生成索引、查询、注入上下文或保存请求的调用意图，并消费返回结果，不负责企业级数据生命周期治理。

## 5. 依赖方向

### 5.1 核心执行依赖能力供给

```text
Agent / Workflow / Runner helper
  -> middleware invocation intent
  -> agent-runtime governed execution contract
  -> agent-middleware services
```

执行组件依赖的是能力调用契约和结果语义，不应直接绑定 provider 内部状态或绕过 runtime/middleware 治理。

### 5.2 高阶能力依赖核心 SDK

```text
openJiuwen optional assets
  -> com.openjiuwen.core.*
  -> mapped to runtime / middleware / evolve when adopted
```

高阶能力可以组合核心 SDK，但不应把训练平台、团队生命周期、跨边界协同或工作区策略写入核心 Agent/Workflow 基础抽象。

### 5.3 与 agent-runtime 的关系

`agent-runtime` 可将 agent-core 的 Agent 能力作为底层框架适配对象，通过中立执行 SPI 调用 ReActAgent、WorkflowAgent 或 Runner。agent-core 本身不承担 A2A northbound、Agent Card 或 runtime TaskStore 语义。
