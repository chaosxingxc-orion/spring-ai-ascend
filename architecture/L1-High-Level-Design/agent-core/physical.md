---
level: L1-HLD
TAG:
  - physical-view
  - deployment
  - resource-model
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - logical.md
  - development.md
  - process.md
---

# agent-core L1 架构物理视图

## 1. 物理视图定位

本文档描述 `agent-core` 的部署形态、进程边界、外部依赖、资源模型和存储拓扑，并区分 openJiuwen SDK 本地模式与平台受治理模式。

## 2. 部署模式

### 2.1 嵌入式 SDK

SDK 本地模式下，业务 Java 应用引入 `agent-core-java` jar，在同一 JVM 内创建 Agent、Workflow、Runner、本地 Session helper、Tool/Model/Store 候选实现等对象。

```text
Business JVM
  -> agent-core-java
      -> Agent / Workflow / Runner helper / local Session helper
      -> optional local Model / Tool / Memory / Retrieval implementations
```

该模式不要求独立服务进程，但不代表平台模式下这些本地实现拥有 provider 治理、服务侧 Session 或 Task 生命周期。

### 2.2 Runtime 适配模式

在本工程未来演进中，`agent-runtime` 可把 agent-core 作为底层 Agent 框架适配对象：

```text
agent-runtime
  -> neutral runtime SPI
  -> agent-core ReActAgent / WorkflowAgent / Runner
  -> middleware invocation intent
  -> agent-middleware services
```

此时 A2A northbound、TaskStore、Agent Card、runtime 健康面、服务侧 Session / Context shell 和 Task 生命周期仍归属 agent-runtime；模型、记忆、检索、工具代理、沙箱和 guardrail 服务执行归 agent-middleware。

### 2.3 高阶协作/训练模式

OpenJiuwen 仓内 Agent Teams、DeepAgent harness 和 Agent evolving 候选资产可能启动或访问额外资源：

- in-process 或 ZeroMQ 进程通信。
- workspace/worktree 本地目录。
- sandbox 或系统操作执行环境。
- online RL gateway、judge、scheduler、vLLM、训练服务。

这些资源属于候选高阶能力部署依赖，不是 `agent-core` 的最小运行条件。Agent Teams 的实体协同应进入 runtime/bus 边界；Agent evolving 的 gateway、judge、scheduler 和训练服务应进入 `agent-evolve` 平面。

## 3. 外部服务依赖

| 依赖 | 用途 | 是否核心必需 |
|---|---|---|
| LLM/Embedding/Reranker 服务 | 模型推理、向量化、重排 | middleware 执行依赖，core 只生成调用意图 |
| Milvus / PostgreSQL pgvector / Chroma | 向量检索 | middleware 检索服务依赖 |
| PostgreSQL / H2 / KV store | KV、本地状态或候选团队状态 | 按 runtime/middleware/evolve 后端选择 |
| S3 兼容对象存储 | 对象存储 | middleware 数据路径依赖 |
| MCP server | 外部工具协议 | middleware 工具代理依赖 |
| Sandbox / local shell | 系统操作 | middleware/runtime 治理依赖 |
| ZeroMQ peer | Agent Teams 候选通信 | runtime/bus 候选实现依赖 |
| Training / judge / gateway | Agent evolving online | agent-evolve 依赖 |

## 4. 本地资源模型

| 资源 | 使用者 | 说明 |
|---|---|---|
| JVM heap | Agent、Workflow、local helper、Graph | 保存执行组件对象、组件内部状态、上下文引用和临时缓存。 |
| Thread / async executor | Runner helper、Workflow、middleware client | 承担组件并发、调用意图等待和流式片段处理。 |
| File system | workspace、sysop 候选、retrieval parser、examples | 工作区、文档解析、技能和临时文件；平台执行需受治理。 |
| Network | middleware services、MCP、store、gateway | 调用模型、工具、存储和训练/评测服务；归属对应模块治理。 |
| Local resources | PDF/Word/image/parser | 文档和多模态样本处理。 |

## 5. 存储拓扑

```text
Agent / Workflow / Runner
  -> local helper / component checkpoint
  -> middleware invocation intent
  -> agent-runtime governed call
  -> agent-middleware / agent-evolve storage or provider
      -> in-memory
      -> PostgreSQL / pgvector
      -> Milvus / Chroma
      -> S3 / local object store
      -> graph store
```

L1 约束是：openJiuwen 仓内 store 接口和部分实现可以作为候选资产复用，但生产级数据生命周期、备份、权限、跨区域和多租户治理不归属 agent-core。

## 6. 发布与测试物理事实

`v0.1.12-jdk21` 是 Java 21 tag，提交为 `dbd58ef84b122130f631372e3bf6bb960933f5e4`。远端仓库文档标明主代码编译成功，但测试编译尚未完全收敛；迁入本工程时应先固定依赖、CI profile 和测试分组，再扩大运行验证。
