---
level: L1-HLD
TAG:
  - api-appendix
  - public-api
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - logical.md
  - development.md
  - spi-appendix.md
---

# agent-core L1 API 附录

## 1. API 附录定位

本文档汇总 `agent-core` 面向业务开发者和集成方的主要公开 API 面。完整类型页以 openJiuwen 原仓 `documents/zh/2.开发指南/API文档/` 为准。

## 2. Agent 应用 API

| API 面 | 代表类型 | 作用 |
|---|---|---|
| LLM Agent | `LlmAgent`、`LlmController`、`LlmEventHandler` | 面向对话和模型调用的 Agent。 |
| Workflow Agent | `WorkflowAgent`、`WorkflowController`、`WorkflowEventHandler` | 面向结构化任务流程的 Agent。 |
| ReAct Agent | `ReActAgent`、`ReActAgentConfig`、`ReActAgentEvolve` | 面向推理-行动-观察循环的单 Agent。 |
| Agent 配置 | `LlmAgentConfig`、`ReActAgentConfig`、`WorkflowAgentConfig` | 组装执行组件、中间件调用契约和本地约束。 |

## 3. Workflow API

| API 面 | 代表类型 | 作用 |
|---|---|---|
| 工作流主体 | `Workflow`、`BaseWorkflow`、`WorkflowSpec`、`WorkflowConfig` | 定义和配置工作流图。 |
| 组件 | `WorkflowComponent`、`ComponentExecutable`、`ComponentComposable` | 定义可执行和可编排节点。 |
| 条件与边 | `Condition`、`EdgeTopology`、`ConnectionType`、`BranchRouter` | 描述分支、边和路由。 |
| 输出与状态 | `WorkflowOutput`、`WorkflowChunk`、`WorkflowExecutionState` | 表达执行结果和流式片段。 |
| 预置组件 | LLM、Tool、KnowledgeRetrieval、MemoryRead/Write、Loop、SubWorkflow | 降低工作流搭建成本；中间件型组件在平台模式下产生调用意图，不拥有 provider 执行。 |

## 4. 中间件调用 API

| API 面 | 代表类型 | 作用 |
|---|---|---|
| Model intent/schema | `Model`、`BaseModelInfo`、`ModelClientConfig`、消息类型 | 表达模型调用需求、消息结构和结果消费；provider 执行归 middleware。 |
| Tool intent/schema | `Tool`、`ToolCard`、`ToolInfo`、`ToolDefinition`、`ToolCall` | 表达工具声明、参数和调用意图；授权、幂等、审计和代理执行归 runtime/middleware。 |
| Prompt | prompt template 相关类型 | 提示词模板与动态填充，可作为 core 组件输入。 |
| Store/query references | KV、object、vector、graph store 相关类型 | 作为中间件候选 SPI 或引用结构；生产数据治理不归 core。 |

## 5. Session / Runner API

| API 面 | 代表类型 | 作用 |
|---|---|---|
| Local Session helper | `AgentSessionApi`、`WorkflowSessionApi`、`AgentSession`、`WorkflowSession` | SDK 本地执行辅助；服务侧 Session owner 仍是 runtime。 |
| Component State | `WorkflowStateCollection`、`WorkflowCommitState`、agent state 类型 | Task 边界以下的执行状态快照。 |
| Checkpoint seam | `CheckpointerProvider` 等 | 组件内部恢复点接缝；跨重启服务恢复不由 core 单独承诺。 |
| Runner | `RunnerImpl`、callback、queue、resource manager | 执行编排与输出回调。 |

## 6. Memory / Retrieval 调用 API

| API 面 | 代表类型 | 作用 |
|---|---|---|
| Memory invocation | `MemoryProvider`、`MemoryEngineConfig`、`MemorySettings`、`MemoryToolOps` | 表达记忆读写或工具化记忆操作；服务执行归 middleware。 |
| Context helper | `ContextEngine`、`ModelContext`、`ContextProcessor`、`TokenCounter` | 可复用为本地辅助或中间件候选；平台 Context shell 不归 core。 |
| Retrieval invocation | `KnowledgeBase`、embedding、retriever、reranker、indexing processor | 表达检索 pipeline 或候选实现；生产检索服务归 middleware。 |
| Vector store references | Milvus、pgvector、Chroma、in-memory 相关类型 | 候选中间件实现或测试资产，不作为 core 数据 owner。 |

## 7. 高阶能力 API

| API 面 | 包 | 作用 |
|---|---|---|
| Multi-agent assets | `com.openjiuwen.core.multiagent`、`com.openjiuwen.agent_teams` | 候选复用资产；协同 owner 按 runtime/bus/Task tree 裁剪。 |
| Harness assets | `com.openjiuwen.harness`、`com.openjiuwen.deepagents` | 候选复用资产；按执行组件、上下文、工具或 runtime 编排拆分。 |
| Agent evolving assets | `com.openjiuwen.agent_evolving` | `agent-evolve` 候选资产，不属于 core API 主体。 |
| SysOp assets | `com.openjiuwen.core.sysop` | 工具意图或候选实现；受治理执行归 runtime/middleware。 |

## 8. API 边界

这些 API 面向 Java SDK 调用和本地框架集成，不等同于 HTTP/RPC 服务 API。服务化协议、A2A 接入、Agent Card、租户鉴权和跨实例 task API 应由 agent-runtime 或更上层服务模块定义。
