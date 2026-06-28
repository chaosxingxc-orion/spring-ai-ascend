---
level: L1-HLD
TAG:
  - scenarios
  - memory-service
  - technical-scenario
status: draft
dependency:
  - README.md
  - overview.md
  - logical.md
  - development.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-middleware memory service L1 架构场景视图

## 目的

本文档从 memory service 的目标架构反推技术场景，用于把架构概览、逻辑视图、开发视图、进程视图和物理视图连接到可验证的运行路径。

本文档只覆盖 `agent-middleware` 的 memory service，不描述其他中间件能力。

## 场景边界

memory service 的场景围绕 Agent 生成前召回、生成后写入、长期沉淀、作用域合成、经验资产强化和降级治理展开。场景中的 Agent 执行、A2A Task 生命周期、模型调用和工具执行不是 memory service 的生命周期 owner。

## MS-01 生成前记忆召回

### 场景目标

Agent 在生成回复前调用 memory service，传入当前用户输入、上一轮回复、身份 scope 和召回上限，memory service 返回可注入的 memory hits、provenance 和可选压缩上下文。

### 参与组件

| 组件 | 角色 |
|---|---|
| Memory client / SPI adapter | 从 runtime 或 service 侧发起 `search`。 |
| ScopeResolver | 解析 tenant/org/project/user/agent/session 作用域和 ACL。 |
| Intent analyzer | 将当前 query 拆成语义查询、关键词、图谱锚点和可选路径线索。 |
| HybridRetriever | 融合向量、FTS、图谱、时间线和原文旁路召回。 |
| MemoryRanker | 去重、重排、热度加权和 relevance filtering。 |
| ContextCompressor | 将候选压缩为 hits / memory whisper / provenance。 |

### 基本路径

1. Agent 调用 `search(query, scope, topK, previousResponse)`。
2. ScopeResolver 生成候选作用域集合：Org、Project、User、Agent 和可选 Session/Task。
3. Intent analyzer 生成 semantic queries、exact keywords、concept anchors 和 tracing hints。
4. HybridRetriever 分别调用向量库、FTS/标量库、图谱/VFS 和原文旁路。
5. MemoryRanker 过滤 ACL、去重、重排，并更新访问热度。
6. ContextCompressor 返回 `MemorySearchResult`，包含 hits、provenance 和可选 `whisperXml`。

### 验证关注点

- 召回不会跨 tenant 泄漏。
- User 私有记忆不被 Project/Org 消费者误读。
- 查询失败可降级为空 hits，不阻断 Agent 主执行。
- 返回结果有来源、作用域、时间和可信度标记。

## MS-02 生成后对话写入与异步沉淀

### 场景目标

Agent 完成一轮交互后将 user/assistant turn 写入 memory service。同步调用只表示 ingest 已接收；长期事实、概念、经验资产的生成由异步学习管线完成。

### 参与组件

| 组件 | 角色 |
|---|---|
| Memory client / SPI adapter | 发起 `save`。 |
| IngestGateway | 校验记录、归一化角色、补齐 scope 和 metadata。 |
| WriteRouter | 判断 private/project/org/agent 目标作用域和 ownership。 |
| EventJournal | 保存原始 ingest 事件和审计线索。 |
| LearningQueue | 异步滑窗、批处理和背压边界。 |
| TraceRefiner / Reflector | 抽取 facts、concepts、lessons 或 playbooks。 |
| IndexWriter | 写入向量、FTS、图谱、时间线和原文旁路索引。 |

### 基本路径

1. Agent 调用 `save(scope, records)`。
2. IngestGateway 过滤空内容、规范化 role、记录幂等键和来源。
3. WriteRouter 根据 scope、metadata、PII 规则和显式 private 标记确定写入目标。
4. 原始记录进入 EventJournal 和 LearningQueue。
5. 同步响应返回 `accepted`。
6. 后台滑窗到阈值后触发抽取与索引。
7. facts/concepts/assets 写入对应存储后，后续 search 可召回。

### 验证关注点

- `save` 的同步成功不等同于长期记忆立即可召回。
- 个人隐私和 PII 默认走 User private 或拒绝共享。
- 写入后台失败可观测，可重试，不污染 Agent 主流程。
- 原始 turn 与蒸馏事实之间保留 provenance。

## MS-03 组织/项目/用户/Agent 作用域合成

### 场景目标

多租户运行态或开发态 Agent 在召回时需要合成多个作用域的记忆：组织政策、项目决策、用户偏好和当前 Agent 专属经验。

### 参与组件

| 组件 | 角色 |
|---|---|
| ScopeResolver | 解析 active scopes 与读取顺序。 |
| ACL filter | 按 tenant、visibility、owner、role 和 phase 过滤。 |
| ConflictPolicy | 对冲突记忆做保留、降权或并列返回。 |
| MemoryRanker | 按 relevance、scope weight、recency、energy、stability 排序。 |

### 基本路径

1. 请求进入时携带 tenant、project、user、agent、phase。
2. ScopeResolver 生成召回集合：`Org -> Project -> User -> Agent`。
3. ACL filter 删除不可见或跨租户记录。
4. MemoryRanker 按作用域权重和相关性重排。
5. 冲突事实并列返回并标记 provenance，让 Agent 或上层策略自裁。

### 验证关注点

- Tenant 是硬隔离边界。
- Org 记忆不自动由普通用户写入。
- User/Agent 记忆可跨项目跟随，但不能泄漏给其他用户。
- dev/runtime phase 不混写。

## MS-04 经验资产召回与结果反馈强化

### 场景目标

memory service 将 skill、playbook、lesson 和 case 视为可召回的 operational assets。一次 search 注入的资产被记录到 eligibility trace；任务结果回来后通过 feedback 精确强化或削弱这些资产。

### 参与组件

| 组件 | 角色 |
|---|---|
| AssetStore | 存储 operational assets。 |
| AssetSelector | 按 relevance、energy、maturity、win/loss 选择资产。 |
| EligibilityTrace | 记录某 turn 注入了哪些 asset id。 |
| ReinforcementPolicy | 将 outcome 映射为能量和稳定度更新。 |
| EvolutionSink | 将强化结果写回标量和向量 metadata。 |

### 基本路径

1. `search` 在普通 facts 之外检索 operational assets。
2. AssetSelector 选择适配当前 query 的 skill/playbook/lesson。
3. memory service 返回 assets，并生成 `turnId`。
4. Agent 或平台在任务完成后调用 `feedback(turnId, reward, weight)`。
5. EligibilityTrace 找到本轮注入的 asset ids。
6. ReinforcementPolicy 更新 energy、stability、uses、wins、losses。
7. AssetStore 写回热力学状态和向量 metadata。

### 验证关注点

- 未被注入的资产不会被误强化。
- 负反馈能降低资产权重。
- 成熟资产对单次噪声结果更保守。
- 经验资产默认不跨 tenant 共享。

## MS-05 异步经验蒸馏

### 场景目标

平台把已完成轨迹和结果发送给 memory service，后台从成功/失败经历中蒸馏新的 skill、playbook 或 lesson。

### 参与组件

| 组件 | 角色 |
|---|---|
| Learn API | 接收 trajectory 和 outcome。 |
| DistillationQueue | 将蒸馏放到后台，避免阻塞热路径。 |
| AssetDistiller | 从轨迹中抽取操作性资产。 |
| DuplicateGuard | 语义去重，避免资产膨胀。 |
| AssetStore | 保存蒸馏结果。 |

### 基本路径

1. 平台调用 `learn(scope, trajectory, reward)`。
2. Learn API 校验输入并入队。
3. AssetDistiller 生成候选资产。
4. DuplicateGuard 过滤相似资产。
5. AssetStore 写入新资产，并初始化 energy/stability。

### 验证关注点

- 蒸馏是后台任务，不阻断 Agent 响应。
- 蒸馏失败不影响普通 search/save。
- 生成资产带来源轨迹和 outcome provenance。
- 资产晋升到 project/org 作用域需要治理策略。

## MS-06 记忆服务不可用时的降级

### 场景目标

memory service 不可用、超时、索引损坏或下游向量库失败时，Agent 主执行路径应继续运行，并暴露明确降级信号。

### 参与组件

| 组件 | 角色 |
|---|---|
| Memory client | 设置超时、重试和 fail-open 策略。 |
| Search API | 对部分后端失败做 partial result。 |
| Metrics | 记录 timeout、cache hit/miss、backend error 和 fallback。 |
| Health endpoint | 暴露服务和后端状态。 |

### 基本路径

1. search 请求进入 memory service。
2. 某个检索后端超时或失败。
3. HybridRetriever 使用剩余后端结果继续。
4. 全部失败时返回空 hits 和 degraded 标记。
5. Agent 主执行继续，只是不带记忆上下文。

### 验证关注点

- memory failure 不导致 Agent 主流程失败。
- 降级对调用方和运维可见。
- save 入队失败与后台沉淀失败区分清楚。
