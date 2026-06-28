---
level: L1-HLD
TAG:
  - spi-appendix
  - extension-surface
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - logical.md
  - development.md
  - api-appendix.md
---

# agent-core L1 SPI 附录

## 1. SPI 附录定位

本文档汇总 `agent-core` 中适合被业务方、集成方或后续本工程适配层扩展的公共扩展面。对 openJiuwen 仓内属于中间件、运行时或演进平面的 SPI，本文只记录其候选复用和调用边界，完整方法签名和参数以源码与 API 文档为准。

## 2. 模型调用扩展面

| 扩展面 | 代表类型 | 约束 |
|---|---|---|
| 模型调用契约 | `Model`、`BaseModelClient`、`ModelClientConfig` | core 可复用调用参数和结果语义；provider 执行归 middleware。 |
| 服务发现 | `META-INF/services/com.openjiuwen.core.foundation.llm.Model$ModelClientFactory` | 可作为 middleware provider 加载机制候选，不作为 core 必选机制。 |
| 消息 schema | `BaseMessage`、`ToolCall`、`AssistantMessageChunk` | 执行组件和 middleware 之间可共享的消息结构。 |

## 3. 工具调用扩展面

| 扩展面 | 代表类型 | 约束 |
|---|---|---|
| 工具声明 | `Tool`、`ToolDefinition`、`ToolCard` | 工具应声明 schema、参数和输出。 |
| 工具调用 | `ToolExecutor`、`ToolRegistry`、`ToolCallOperator` | 可作为本地 SDK 能力或 tool intent 生成逻辑；平台执行必须经 runtime/middleware 治理。 |
| MCP 工具 | MCP client/server 相关类型 | 外部工具协议接入候选，服务执行和权限治理归 middleware/runtime。 |
| 系统操作 | `sysop`、sandbox、file/cmd/code operation | 只能作为候选实现或工具意图；必须经过权限、路径、安全、审计和重复保护。 |

## 4. 存储和查询候选扩展面

| 扩展面 | 代表类型 | 约束 |
|---|---|---|
| KV store | `BaseKVStore`、`KVStorePipeline` | middleware 候选实现或测试资产；core 不拥有生产状态。 |
| Object store | `BaseObjectStorageClient` | middleware 候选实现或引用结构；core 不拥有对象数据生命周期。 |
| Vector store | `BaseVectorStore`、`CollectionSchema`、`FieldSchema` | retrieval/memory 服务候选实现；core 仅消费检索结果。 |
| Query expression | `QueryExpr` 及其实现 | 可复用查询表达，但执行和数据治理归 provider/middleware。 |

## 5. Memory / Retrieval 扩展面

| 扩展面 | 代表类型 | 约束 |
|---|---|---|
| Memory provider | `MemoryProvider`、memory lite 相关类型 | middleware 候选 provider；core 产生读写意图并消费结果。 |
| Retrieval pipeline | parser、chunker、embedding、reranker、retriever | middleware 候选 pipeline；core 不拥有知识索引状态。 |
| Context processor | compressor、offloader、token counter | 可作为本地辅助或 middleware 候选；服务侧 Context shell 归 runtime/middleware。 |

## 6. Runner / Session 扩展面

| 扩展面 | 代表类型 | 约束 |
|---|---|---|
| Callback | runner callback events | 观测执行过程、流式输出和状态变化。 |
| Checkpointer | `CheckpointerProvider` | 保存组件内部恢复点；服务侧跨重启恢复不由 core 单独承诺。 |
| Stream | workflow chunk、operator stream、tool stream | 生产者应保证终止、错误和资源关闭语义。 |

## 7. Guardrail / Harness / Team 扩展面

| 扩展面 | 代表类型 | 约束 |
|---|---|---|
| Guardrail | `GuardrailBackend`、`UserInputGuardrail`、risk model | 安全护栏服务候选实现；执行和审计归 middleware/runtime。 |
| Harness rails | `harness` rails、skills、workspace | 候选资产，按执行组件、上下文、工具治理或 runtime 编排拆分。 |
| Agent Teams | messager、backend、workspace、team tools | 候选资产；不作为 core 协同 owner。实体协同归 runtime，跨边界归 bus。 |

## 8. SPI 纯度约束

- SPI 应以 Java 接口、抽象类、schema 对象或 Java SPI service 为主。
- SPI 不应强制依赖 agent-runtime、A2A 协议对象或本地旧 `agent-core` 模块。
- 外部服务客户端应被封装在 middleware provider/adapter 后面，避免进入 Agent 核心状态模型。
- 高阶能力扩展面可以依赖核心 API，但不能改变核心 Agent/Workflow/Runner 的基础契约，也不能把团队生命周期或演进训练路径变成 core 必选职责。
