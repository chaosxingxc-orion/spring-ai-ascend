---
level: L1-HLD
TAG:
  - overview
  - module-boundary
  - memory-service
status: draft
dependency:
  - README.md
  - scenarios.md
  - logical.md
  - development.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-middleware memory service L1 架构概览

## 目的

本文档给出 `agent-middleware` memory service 的 L1 高阶心智模型，描述模块目标、问题领域、边界形态、非目标和从 doushuaigong 代码包提炼出的设计原则。

本文档只描述 memory service 的高阶设计，不展开类级清单、完整接口签名、数据库 schema、配置项全集或测试矩阵。

## 模块目标

`agent-middleware` memory service 是面向智能体运行时和智能体开发态的 **框架中立记忆服务**。它负责在不绑定某个 Agent 框架、不侵入模型权重、不接管 runtime Task 生命周期的前提下，为 Agent 提供可治理的长期记忆召回、对话沉淀、组织作用域合成和经验资产强化能力。

当前 L1 目标包括：

- 提供稳定的 `init/search/save/feedback/learn` 记忆服务面，使 `agent-runtime`、`agent-service` 或业务 Agent 能够按统一契约接入记忆。
- 将短期对话、语义事实、概念图谱、原文片段、经验资产和组织知识分层建模，避免把 memory 降格为单一向量库。
- 支持混合召回：语义向量、关键词/FTS、图谱锚定、时间线和原文旁路共同参与候选生成、去重、重排和压缩。
- 支持写入沉淀：对话 turn 先进入 ingest 流，再通过滑窗、反思、抽取和旁路索引进入长期记忆。
- 支持作用域治理：tenant、org、project、user、agent、session/task 形成显式隔离与合成顺序。
- 支持 memory-layer evolution：将 skill、playbook、lesson、case 作为记忆资产，通过结果反馈调整能量和成熟度。
- 提供 fail-open 的中间件边界：记忆服务不可用时不阻断 Agent 主执行路径，但必须暴露可观测的降级信号。

## 受众边界

| 受众 | 主要需求 |
|---|---|
| 模块维护者 | 理解 memory service 在 `agent-middleware` 中的职责边界、API/SPI、存储模型和治理约束。 |
| Runtime / Agent 适配开发者 | 理解如何从 `AgentExecutionContext`、会话、用户和任务信息映射到记忆查询与写入。 |
| 平台架构评审者 | 判断 memory service 是否保持框架中立、租户隔离、状态归属清晰，并与 L0 模块边界一致。 |
| 存储与运维开发者 | 理解向量库、标量库、VFS、缓存、后台队列和降级策略的部署归属。 |
| AI agent / 文档维护者 | 以本文建立 memory service 心智模型，再进入各视图和附录定位事实来源。 |

## 问题领域

### 1. 智能体长期记忆不是单一向量检索

doushuaigong 的 Helix 设计将 Chroma 向量、SQLite FTS、VFS 原文、图谱连边、时间线和热力学状态拆为不同存储责任面。memory service 需要保留这种多形态记忆模型：向量负责模糊语义，FTS 负责字面锚定，图谱负责概念关系，VFS/原文负责可追溯证据，热力学负责记忆活性。

### 2. 召回应在 Agent 生成前完成，并以压缩线索注入

doushuaigong 的 `search` 路径通过 `FRAMEWORK_MESSAGE_RECEIVED` 触发 IntentExtractor 和 Retriever，最终返回 `RetrieverWhisperPill` / `<memory_whisper>`。在 `spring-ai-ascend` 中，这应被抽象为 generation-before memory recall：在 Agent 生成前返回有 provenance 的 facts/hits，而不是让 Agent 自己直连底层存储。

### 3. 写入是异步学习，不是同步行存储

`save` 在 doushuaigong 中是 fire-and-forget ingest：每条 turn 作为事件进入中循环，滑窗到阈值后触发 TraceEncoder/Reflector，把原始对话蒸馏为事实、概念和索引。memory service 应将同步 `save` 的职责限定为接收和排队，长期记忆沉淀由异步 pipeline 完成。

### 4. 多租户平台需要比个人 Agent 更强的作用域与治理

OpenClaw 形态可以按 `user_id or session_id` 分区；`spring-ai-ascend` 运行态需要 `tenant -> org/project/user/agent` 的显式作用域、ownership、phase、visibility、ACL、PII/redact 和审计。

### 5. 经验改进属于记忆服务，而不是模型训练前置条件

doushuaigong 的 evolution 代码将 skill/playbook/lesson/case 作为记忆资产，并通过 `feedback` 调整能量、稳定度、使用次数和胜负计数。memory service 可先落地这种 memory-layer evolution，不要求立即进行权重微调或本地 LoRA。

## 模块边界形态

| 边界项 | memory service 负责 | memory service 不负责 | 事实下沉位置 |
|---|---|---|---|
| 记忆 API | 提供 search/save/init/feedback/learn、健康和指标面。 | 不定义 Agent 主执行协议，不接管 A2A Task API。 | `api-appendix.md` |
| 记忆召回 | 生成候选、去重、重排、压缩、返回 hits/provenance/可选 whisper。 | 不直接决定 Agent 最终回答，不绕过 Agent 策略。 | `logical.md`, `process.md` |
| 记忆写入 | 接收 turn/record/lesson/case，进入异步沉淀管线。 | 不把同步 save 声明为长期事实已可召回。 | `process.md` |
| 存储抽象 | 管理向量、标量/FTS、图谱、时间线、VFS、缓存的组合边界。 | 不把某个具体向量库或 SQLite 设为唯一实现。 | `development.md`, `physical.md` |
| 作用域治理 | 管理 tenant/org/project/user/agent/session scope、phase、ownership、visibility。 | 不替代平台 IAM、租户网关、计费和数据出域审批。 | `logical.md`, `physical.md` |
| 经验进化 | 记录 operational assets、eligibility trace、feedback 强化和异步蒸馏。 | 不训练基础模型权重，不承诺 L4/L5 强化学习或 LoRA 已落地。 | `scenarios.md`, `logical.md` |
| Runtime 协作 | 通过 SPI/API 被 `agent-runtime` 或 `agent-service` 调用。 | 不持有 runtime Task/Session 生命周期，不写 A2A TaskStore。 | `spi-appendix.md` |

## 参考来源

本设计参考 `outputs/doushuaigong-main/doushuaigong-main` 中的以下文件：

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/MEMORY_HTTP_CONTRACT.md`
- `docs/ORG_MEMORY_DESIGN.md`
- `core/loops/integration/http_facade.py`
- `core/loops/middle/engine.py`
- `core/loops/daemons/workflow_daemons.py`
- `core/helix/*`
- `core/helix/engines/*`
- `agents/intent_extractor/intent_extractor_agent.py`
- `agents/retriever/retriever_agent.py`
- `core/loops/outer/evolution.py`
- `core/loops/outer/evolution_helix.py`

这些文件作为外部代码包事实来源，不构成 `spring-ai-ascend` 的实现约束。
