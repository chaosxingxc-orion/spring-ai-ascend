---
level: L1-HLD
TAG:
  - logical-view
  - domain-model
  - memory-service
status: draft
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

# agent-middleware memory service L1 架构逻辑视图

## 1. 逻辑视图定位

memory service 是 `agent-middleware` 中负责智能体记忆能力的逻辑子系统。它对上提供框架中立记忆契约，对下组合多种存储与学习机制，对侧与 runtime/service 协作但不拥有 runtime Task 生命周期。

本视图回答以下问题：

- memory service 内部有哪些领域对象。
- search/save/feedback/learn 分别落到哪些逻辑责任面。
- 长期记忆、短期缓存、经验资产、业务事实和平台轨迹如何分域。
- 作用域、ownership、phase、visibility 如何参与写入和召回。
- 混合检索和记忆进化如何在 L1 层表达。

## 2. 领域对象模型

### 2.1 MemoryScope

`MemoryScope` 表示一次记忆读写的身份、隔离和合成范围。

```text
MemoryScope
├── tenantId      硬隔离键
├── orgId         组织级共享范围
├── projectId     项目级共享范围
├── userId        用户私有或跨项目跟随范围
├── agentId       Agent 专属记忆范围
├── sessionId     当前会话范围
├── taskId        当前任务范围
├── phase         dev | runtime
└── ownership     business | platform
```

Tenant 是硬隔离边界。Org、Project、User、Agent 是长期记忆作用域。Session/Task 是短期工作记忆或当前 turn 归因范围，不默认成为长期共享边界。

### 2.2 MemoryRecord

`MemoryRecord` 是 memory service 的长期记录抽象。

```text
MemoryRecord
├── id
├── scope
├── visibility       private | project | org
├── kind             fact | concept | raw | summary | skill | playbook | lesson | case
├── content          可召回文本或结构化 payload
├── provenance       source, timestamps, sessionId, taskId, trace ids
├── thermodynamics   energy, stability, decayRate, accessCount
├── embeddingRef     向量索引引用
├── graphRefs        概念或事实关系
└── metadata         扩展字段
```

`MemoryRecord` 不要求所有字段都存在于同一物理表中。向量库、标量库、图谱边表、VFS 原文和事件日志可以分别保存记录的不同投影。

### 2.3 MemoryHit

`MemoryHit` 是 search 返回给调用方的候选记忆。

```text
MemoryHit
├── id
├── content
├── score
├── scope
├── kind
├── provenance
├── metadata
└── degradedReason?
```

`score` 是服务内排序参考，不保证跨后端、跨请求、跨租户可比较。调用方不能把 score 当作全局置信度。

### 2.4 MemoryWhisper

`MemoryWhisper` 是面向 Agent prompt 注入的压缩上下文载体，来源于 doushuaigong 的 `RetrieverWhisperPill`。

```text
MemoryWhisper
├── paths   原始证据路径或记录 ID
├── facts   压缩后的事实线索
├── tags    主题标签
└── assets  可选 skill/playbook/lesson
```

在 `spring-ai-ascend` 中，`MemoryWhisper` 是可选输出形态。标准 API 仍以结构化 `MemoryHit` 为主，避免调用方只能消费 XML 字符串。

### 2.5 OperationalAsset

`OperationalAsset` 是经验进化类记忆。

```text
OperationalAsset
├── id
├── kind        skill | playbook | lesson | case
├── title
├── trigger
├── body
├── scope
├── energy
├── stability
├── uses
├── wins
└── losses
```

OperationalAsset 与 fact/concept 共用记忆治理和召回能力，但可以使用独立 collection 或 namespace，避免操作性建议污染普通事实召回。

### 2.6 EligibilityTrace

`EligibilityTrace` 记录某次 search 给某个 turn 注入了哪些资产。

```text
EligibilityTrace
├── turnId
├── assetIds
├── scope
├── createdAt
└── expiresAt
```

它是 `feedback` 精确归因的最小状态，不是长期业务记忆。它应有 TTL 和容量上限。

## 3. 六个逻辑责任面

### 3.1 分层总览

```text
┌──────────────────────────────────────────────────────────────┐
│           agent-middleware memory service                     │
│                                                              │
│  memory-api                                                   │
│  search / save / feedback / learn / health / metrics          │
│        │                                                     │
│        ▼                                                     │
│  scope-governance                                             │
│  tenant isolation, ACL, phase, ownership, visibility          │
│        │                                                     │
│        ▼                                                     │
│  recall-orchestrator                                          │
│  intent analysis, hybrid retrieval, rank, compress            │
│        │                                                     │
│        ▼                                                     │
│  ingest-and-learning                                          │
│  save intake, event journal, windows, reflection, indexing    │
│        │                                                     │
│        ▼                                                     │
│  evolution-layer                                              │
│  asset recall, eligibility, feedback, distillation            │
│        │                                                     │
│        ▼                                                     │
│  storage-fabric                                               │
│  vector, scalar/FTS, graph, timeline, VFS, cache              │
└──────────────────────────────────────────────────────────────┘
```

这些是逻辑责任面，不要求一一对应代码包或进程。

### 3.2 memory-api

`memory-api` 是 memory service 的 northbound 和 SPI 边界，承接 `init/search/save/feedback/learn`、健康和指标。

该层负责：

- 校验输入与版本。
- 转换 runtime/service 上下文为 `MemoryScope`。
- 执行超时、幂等、错误语义和 fail-open 策略。
- 保持 HTTP/API/SPI 与内部存储实现解耦。

### 3.3 scope-governance

`scope-governance` 管理作用域、权限、归属和阶段。

该层负责：

- Tenant 硬隔离。
- `Org -> Project -> User -> Agent` 召回合成顺序。
- 写入路由：个人优先、项目共享、组织策展、无 project 兜底 User。
- `business/platform` ownership 标记。
- `dev/runtime` phase 隔离。
- PII、private escape、redact 和审计入口。

### 3.4 recall-orchestrator

`recall-orchestrator` 管理热路径召回。

该层负责：

- 将 query 分解为 semantic queries、exact keywords、concept anchors 和 optional path hints。
- 并行或分阶段调用多后端召回。
- 对候选进行 ACL 过滤、去重、重排和上限裁剪。
- 将候选压缩为 hits、provenance 和可选 whisper。
- 更新访问热度和召回指标。

### 3.5 ingest-and-learning

`ingest-and-learning` 管理写入和长期沉淀。

该层负责：

- 接收 user/assistant/tool/system records。
- 将同步写入转为 event journal 和 learning queue。
- 通过滑窗或批处理触发事实、概念和摘要抽取。
- 将抽取结果写入 storage-fabric 的多个投影。
- 保留原始证据和可追溯关系。

### 3.6 evolution-layer

`evolution-layer` 管理经验资产。

该层负责：

- 按 query 检索 skill/playbook/lesson。
- 根据 relevance、energy、maturity、win/loss 选择注入资产。
- 记录 eligibility trace。
- 消费 feedback 并强化资产。
- 后台从 resolved trajectory 蒸馏新资产。

该层是可选能力。未启用时，普通 memory search/save 不受影响。

### 3.7 storage-fabric

`storage-fabric` 是多后端存储组合边界。

| 存储面 | 逻辑职责 |
|---|---|
| Vector store | 语义 KNN 召回，保存文本向量投影和 metadata。 |
| Scalar / FTS store | 字面匹配、热力学、计数、稳定度、状态更新和关系索引。 |
| Graph store | 概念、事实、资产和证据之间的关系。 |
| Timeline store | 按时间组织 turn、fact、trace 和事件。 |
| VFS / object store | 原文、长文、附件、概念文件和可追溯证据。 |
| Hot cache | search TTL/LRU、最近上下文、eligibility trace 和短期工作记忆。 |

## 4. 写入路由模型

### 4.1 路由优先级

写入路由遵循“协作优先 + 用户严格私有”的默认规则。

| 输入类型 | 默认目标 |
|---|---|
| 个人偏好、身份、私事、PII 或不确定个人内容 | User private |
| 项目上下文、技术决策、协作事实 | Project shared |
| 组织政策、合规、全员知识 | Org curated |
| Agent 人设、专属技能、Agent 级经验 | Agent scope |
| 临时任务状态、黑板、scratchpad | Session/Task ephemeral |

### 4.2 共享晋升

User -> Project -> Org 的晋升需要策略控制。Project 共享可由成员操作触发；Org 级写入默认需要管理员或自动流程的强治理确认。

### 4.3 归属与阶段

`ownership=business` 表示业务事实、领域知识、用户偏好和客户数据；`ownership=platform` 表示平台轨迹、指标、运行经验、token、模型遥测和服务质量信号。

`phase=dev` 表示开发态 agent 资产、项目规范、prompt/skill 选择和设计决策；`phase=runtime` 表示终端用户业务事实和生产运行轨迹。二者默认隔离，只有经过治理的 project/org 知识可跨 phase 投影。

## 5. 混合召回模型

### 5.1 候选生成

```text
SearchRequest
  -> ScopeResolver
  -> IntentAnalyzer
       ├── semanticQueries
       ├── exactKeywords
       ├── conceptAnchors
       └── pathHints
  -> HybridRetriever
       ├── vector KNN
       ├── FTS / exact match
       ├── graph expansion
       ├── timeline window
       └── verbatim sidecar
```

### 5.2 过滤与排序

候选排序至少考虑：

- tenant/ACL/visibility 是否可读。
- query relevance。
- scope weight：Org/Project/User/Agent 按场景加权。
- recency 与 phase。
- energy、stability、access count。
- source quality：fact/concept/raw/asset。
- duplicate / conflict。

### 5.3 压缩输出

memory service 不应把大量原文直接塞给 Agent。输出应分层：

- `hits`：结构化候选，给程序消费。
- `whisper`：可选压缩文本，给 prompt 注入。
- `provenance`：路径、record id、source、timestamp、scope。
- `debug`：仅在调试或授权模式暴露。

## 6. 状态归属

| 状态 | 归属 | Memory service 职责 |
|---|---|---|
| Runtime Task / Session 状态 | `agent-runtime` | 只读取 scope，不写 TaskStore。 |
| Memory records | memory service | 管理长期记忆、索引和治理属性。 |
| Event journal | memory service | 保存 ingest、learn、feedback 的审计与重放基础。 |
| Eligibility trace | memory service hot state | TTL 状态，用于 feedback 归因。 |
| Agent checkpoint | Agent 框架或 runtime 适配器 | 不接管，仅可保存摘要或事实投影。 |
| Business data source | 业务系统 | 不复制原始业务库，除非明确写入 memory。 |
| Platform telemetry | 平台观测系统 / memory service 投影 | 可将经验信号投影为 platform ownership 记忆。 |

## 7. 逻辑依赖方向

memory service 可被 `agent-runtime`、`agent-service` 或业务 Agent 调用，但不依赖这些模块的实现类型。

```text
agent-runtime / agent-service / business agent
    ↓ calls API/SPI
agent-middleware memory-api
    ↓ uses
scope-governance + recall + ingest + evolution
    ↓ uses
storage-fabric
```

Memory SPI 不依赖 A2A wire 类型、具体 Agent 框架类型或具体向量库类型。具体 HTTP adapter、database adapter、vector adapter、embedding adapter 属于 implementation boundary。
