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
---

# agent-core L1 术语表

| 术语 | 定义 |
|---|---|
| agent-core | L0 定义的智能体开发框架和执行组件边界，主流 Java 实现基线为 openJiuwen Core Java。 |
| agent-core-java | 远端仓库 artifact 名，Maven 坐标为 `com.openjiuwen:agent-core-java:0.1.12`；它是实现基线，不替代 L0 逻辑模块名。 |
| Agent | 面向调用方的智能体开发对象，组合执行组件、中间件调用意图和本地执行辅助。平台 Agent definition 归 `agent-runtime`。 |
| LlmAgent | 面向模型对话的 Agent。 |
| ReActAgent | 采用 Reasoning + Action + Observation 循环的 Agent。 |
| WorkflowAgent | 封装 workflow 执行的任务型 Agent。 |
| Workflow | 由组件、边、条件、循环和子工作流构成的结构化任务图。 |
| WorkflowComponent | Workflow 中可编排、可执行的节点抽象。 |
| Runner | 负责执行组件编排、回调、队列、资源管理和流式片段连接的运行辅助。 |
| Local Session helper | openJiuwen SDK 本地模式下承载对话、工作流、节点、状态、流式和 checkpoint 的辅助对象；平台 Session owner 仍是 `agent-runtime`。 |
| Checkpoint | 组件内部恢复点或本地执行快照接缝；跨重启服务恢复不由 core 单独承诺。 |
| Middleware invocation | `agent-core` 执行组件对模型、记忆、检索、工具代理、沙箱和 guardrail 的调用意图、参数、恢复点和结果消费逻辑。 |
| Context Engine | 管理模型上下文窗口、token、压缩和 offload 的能力；平台 Context shell 由 `agent-runtime` 与 `agent-middleware` 协作。 |
| Memory | 记忆能力或候选 provider 实现，逻辑归属为 `agent-middleware`；core 只产生读写意图并消费结果。 |
| Retrieval | 知识检索 pipeline，包括解析、切分、embedding、检索、重写和重排；生产服务归 `agent-middleware`。 |
| Tool | Agent 可声明或请求调用的本地、API、MCP 或系统操作能力；受治理执行归 runtime/middleware。 |
| MCP | Model Context Protocol，用于接入外部工具和资源。 |
| SysOp | 文件、命令、代码执行和 sandbox 等系统操作候选能力；平台执行必须受治理。 |
| Guardrail | 输入、工具、系统操作或输出的安全风险控制服务，逻辑归属为 `agent-middleware`。 |
| Agent Teams | openJiuwen 仓内团队协作候选资产。天然存在的智能体实体协同归 `agent-runtime`，跨边界归 `agent-bus`，上下文拆分型子执行归 Task tree。 |
| DeepAgent harness | 面向复杂任务执行的高阶 harness 候选资产，需按执行组件、上下文、工具和 runtime 编排拆分归属。 |
| Agent evolving | Agent 自优化、评测、轨迹采样、训练和 RL online/offline 候选资产，逻辑归属为 `agent-evolve`。 |
| Rail | harness 或 Agent 执行中的约束、增强或处理管线。 |
| Stream chunk | 模型、workflow、tool 或 runner 在执行过程中的流式输出片段。 |
| A2A | Agent-to-Agent 协议；在本架构中属于 agent-runtime 或服务化接入层，不属于 agent-core 核心职责。 |
