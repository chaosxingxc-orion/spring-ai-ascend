---
level: L1-HLD
TAG:
  - scenarios-view
  - use-case
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - logical.md
  - development.md
  - process.md
  - physical.md
---

# agent-core L1 架构场景视图

## 1. 场景视图定位

本文档描述 `agent-core` 的关键用户和系统场景，用于连接架构视图、代码组织和后续验证范围。场景视图只保留模块级流程，不展开具体模型参数、工具 JSON schema 或示例输出全集。

## 2. 核心场景清单

| 场景 | 入口 | 涉及能力 | 验证重点 |
|---|---|---|---|
| 创建 ReAct Agent | `ReActAgent`、`LlmAgent`、tool/model 配置 | 推理-行动-观察循环、工具调用、模型输出解析、Session | Agent 循环、工具选择、异常与中断 |
| 执行 Workflow Agent | `WorkflowAgent`、`Workflow`、组件 | 工作流图、组件执行、条件、循环、子工作流、状态恢复 | 图执行、组件输入输出、用户交互 |
| 接入模型与提示词 | 模型调用组件、prompt template | 模型调用意图、消息 schema、模板填充、结果消费 | 调用意图与 provider 执行边界 |
| 注册与调用工具 | `Tool`、tool schema、tool intent | 工具声明、调用参数、结果消费、中断点 | 工具意图与受治理执行边界 |
| 使用本地 Session 与 checkpoint | `AgentSession`、`WorkflowSession`、checkpointer | SDK 本地执行辅助、流式片段、中断恢复接缝 | 本地状态与 runtime Session owner 边界 |
| 记忆和检索增强 | memory/retrieval 调用组件 | 查询意图、上下文注入、返回结果消费 | middleware provider 状态不归 core |
| 同边界子执行与任务拆分 | workflow / planner / context split | 子任务规划、上下文隔离、组件并行 | 区分 Task tree 与多智能体协同 |
| DeepAgent harness | `harness`、`deepagents` | workspace、rails、skills、progressive tools、配置加载 | 任务规划、上下文工程、工具护栏 |
| Agent evolving 资产评估 | `agent_evolving` | evaluator、trainer、trajectory、online/offline RL | 是否可拆为 agent-evolve 微服务候选 |

## 3. 典型用户旅程

### 3.1 从 WorkflowAgent 开始构建业务流程

业务开发者先定义 workflow 组件与输入输出，再通过 `WorkflowAgent` 封装为可对话 Agent。用户输入进入 Agent 后，由 controller 识别意图并选择或推进 workflow；workflow 执行中遇到需要补充信息的组件时，Session 保存中断状态并等待用户继续输入。

该场景要求：

- Agent 入口不暴露底层图执行细节。
- Workflow 组件输入输出保持结构化。
- 中断恢复依赖 Session/checkpoint，而不是业务调用方自行维护执行栈。
- 流式或分段输出由 Runner/Session 回调统一回传。

### 3.2 使用 ReActAgent 调用工具完成开放任务

业务开发者注册模型和工具调用契约，构建 `ReActAgent`。Agent 在每轮中根据对话上下文生成思考、行动和工具调用意图；平台模式下，模型服务和工具代理服务由 `agent-runtime` 通过受治理契约调用 `agent-middleware` 执行，结果再作为观察输入下一轮，直到输出最终答案或触发中断/失败。

该场景要求：

- 模型和工具调用契约可以替换。
- 工具调用需要 schema、参数校验、治理上下文和错误包装。
- Agent loop 应能够访问 Session、Memory、Context 和 callback。
- 工具执行失败不应破坏基础运行时状态，且不可绕过 runtime/middleware 治理直接产生不可逆副作用。

### 3.3 用检索和记忆增强 Agent 上下文

开发者通过中间件侧 retrieval pipeline 建立知识库索引，使用 vector store、embedding、reranker 和 query rewrite；Agent 执行组件只产生 memory/retrieval 查询或写入意图，并消费返回结果完成上下文注入。

该场景要求：

- 检索和记忆作为中间件能力接入 Agent，而不是替代 Agent controller。
- 外部存储状态归属 store/retrieval/memory 后端或客户系统。
- 服务侧 Context shell 归 runtime/middleware 协作，core 只消费上下文结果或本地辅助对象。

### 3.4 子执行、任务拆分与多智能体边界

在当前架构下，多智能体协作分为两类。若智能体实体天然存在，例如多个已注册 Agent、多个 runtime 实例或多个部门智能体，则无论共节点还是跨节点，协同生命周期、成员状态和 Task tree 都应由 `agent-runtime` 居中完成，跨边界时进入 `agent-bus` 治理。若任务过程中只是为了上下文分拆隔离而派生子执行，它本质是无状态智能体实体的多 Task 协作，应建模为 Task tree / 子 Task，而不是 `agent-core` 内部多智能体 owner。

该场景要求：

- `agent-core` 可以复用 context split、子任务规划或同边界算法，但不拥有团队生命周期。
- `agent_teams` 中 task/message/member/worktree 能力需要逐项映射到 runtime、bus、middleware 或工具链。
- workspace/worktree 操作必须受 runtime/middleware/client 边界治理。

### 3.5 DeepAgent 与演化训练

DeepAgent harness 组合配置、workspace、skills、rails、上下文工程和任务规划；这些能力需要按执行组件、上下文、工具和 runtime 编排拆分归属。Agent evolving 消费 trajectory、dataset、evaluator、trainer 和 online/offline RL gateway，归属 `agent-evolve` 候选资产。

该场景要求：

- 高阶能力可以依赖核心执行组件，但不能改变核心 API 的基础语义。
- 训练、评测、gateway、judge、vLLM、采样运行时和业务环境仿真属于演进平面候选微服务，不属于核心 SDK 单进程保证。

## 4. 负向场景

`agent-core` 不应承担以下职责：

- 平台级 A2A northbound 协议接入和 task-owning runtime 服务化外观。
- 跨实例任务总线、租户治理、统一鉴权网关和企业数据权限中心。
- 模型服务、记忆服务、知识检索服务、工具代理服务、沙箱服务、安全护栏服务的 provider 执行和生产治理。
- 多智能体实体生命周期、团队成员状态、跨边界协同路由和演进平面训练/评测微服务。
- 本地旧 `agent-core` 模块中引擎契约类型的兼容维护。
