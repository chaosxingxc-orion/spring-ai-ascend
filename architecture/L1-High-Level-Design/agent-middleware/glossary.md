---
level: L1-HLD
TAG:
  - glossary
  - memory-service
status: draft
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - logical.md
  - development.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-middleware memory service 术语表

## Agent Memory

智能体在执行中可召回、可写入、可演化的上下文资产集合。它包括事实、概念、原文片段、用户偏好、项目决策、组织知识、经验技能和案例，不等同于单一向量库。

## Memory Service

`agent-middleware` 中负责记忆 API/SPI、作用域治理、召回编排、写入沉淀、经验进化和存储组合的逻辑子系统。

## MemoryScope

一次记忆读写的身份与治理范围，包含 tenant、org、project、user、agent、session、task、phase、ownership 等维度。

## Tenant

企业运行态的硬隔离边界。跨 tenant 记忆读取必须被禁止。

## Scope

记忆可见性和合成的范围。本文使用 Org、Project、User、Agent、Session/Task 作为主要层级。

## Phase

记忆所属阶段。`dev` 表示开发态，`runtime` 表示运行态。

## Ownership

记忆归属标签。`business` 表示客户业务事实、领域知识和用户偏好；`platform` 表示平台轨迹、运行指标、经验信号和服务状态投影。

## Visibility

记忆可见性。典型值包括 `private`、`project`、`org`。

## MemoryRecord

长期记忆记录的逻辑抽象。可以被投影到向量库、FTS、图谱、时间线、对象存储和热力学状态表。

## MemoryHit

一次 search 返回给调用方的候选记忆，包含 content、score、kind、scope、metadata 和 provenance。

## MemoryWhisper

面向 Agent prompt 注入的压缩记忆载体。来源于 doushuaigong 的 `<memory_whisper>` 模式，但在本设计中是可选输出，不替代结构化 hits。

## Hybrid Retrieval

融合向量语义、全文检索、精确匹配、图谱扩展、时间线窗口和原文旁路的召回模式。

## Vector Store

保存文本 embedding 并支持 KNN 查询的存储面。用于模糊语义召回。

## Scalar / FTS Store

保存可查询字段、全文索引、热力学状态和高频计数的存储面。用于关键词锚定、状态更新和治理过滤。

## Graph Store

保存事实、概念、资产和证据关系的存储面。

## Timeline Store

保存 turn、event、fact 和 trace 的时间序列视图。

## VFS / Object Store

保存原文、长内容、附件、概念文件和可追溯证据的对象或文件系统存储面。

## Hot Cache

低延迟短期缓存，包括 search TTL/LRU、recent context 和 eligibility trace。Hot cache 不是长期记忆事实源。

## Ingest

memory service 接收对话 turn 或记录的同步入口。Ingest 成功不等同于长期知识已沉淀完成。

## Learning Pipeline

save 之后的异步沉淀流程，包括滑窗、抽取、反思、索引写入和审计。

## Operational Asset

可被 Agent 召回并影响后续行为的操作性经验资产，包括 skill、playbook、lesson 和 case。

## Skill

可复用的操作方法。

## Playbook

面向一类任务的多步骤策略。

## Lesson

失败教训或反模式，用于避免重复错误。

## Case

带结果标签的已解决情境，用于 case-based recall。

## Eligibility Trace

记录某个 turn 注入了哪些 operational assets 的短期状态，用于 feedback 精确归因。

## Feedback

平台或用户对某次记忆注入结果的反馈信号。它用于强化或削弱注入过的 assets。

## Reward

feedback 中的数值结果，通常映射到 `[-1, 1]`。

## Thermodynamics

记忆活性状态模型，包括 energy、stability、decayRate、accessCount、wins、losses 等。它用于访问热度、遗忘、强化和成熟度计算。

## Energy

记忆或资产的活性/强化强度。成功使用提升 energy，失败或衰减降低 energy。

## Stability

记忆或资产对单次反馈的抗噪能力。成熟资产稳定度更高，更新更保守。

## Decay

按时间或使用结果降低记忆活性的过程。

## ScopeResolver

将调用方 scope 和 principal 转换为可读集合或写入决策的组件。

## ScopeRouter

写入侧根据内容和策略选择 User、Project、Org、Agent 或 Session/Task 的组件。

## Provenance

记忆来源，包括原始 turn、trace、文件路径、时间戳、任务、用户和抽取过程信息。

## Fail-open

记忆服务失败时返回空结果或降级结果，让 Agent 主执行继续。

## Fail-closed

涉及越权、租户泄漏、隐私或合规风险时拒绝请求，不允许降级绕过。
