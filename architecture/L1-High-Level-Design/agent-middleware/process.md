---
level: L1-HLD
TAG:
  - process-view
  - runtime-flow
  - concurrency-boundary
  - memory-service
status: draft
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - logical.md
  - development.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-middleware memory service L1 架构进程视图

## 1. 进程视图定位

本文档描述 memory service 在运行时的请求入口、同步/异步边界、后台学习、缓存、并发控制和错误处理。

本文档只描述 memory service。Agent 执行线程、A2A Task 状态和模型调用主流程不归 memory service 拥有。

## 2. 运行时参与者

| 参与者 | 运行职责 |
|---|---|
| Memory API / SPI caller | 发起 search/save/feedback/learn。 |
| MemoryFacade | 参数校验、超时、错误语义、metrics。 |
| ScopeResolver | 解析读写作用域和 ACL。 |
| SearchCache | TTL/LRU 热路径缓存。 |
| RecallOrchestrator | 召回编排。 |
| HybridRetriever | 调用向量、FTS、图谱、时间线和原文旁路。 |
| IngestGateway | 接收 save 并写入事件流。 |
| LearningWorkers | 微批抽取、反思、索引写入。 |
| EvolutionEngine | asset recall、feedback 强化和 learn 蒸馏。 |
| StorageFabric | 多后端存储。 |

## 3. Search 流程

### 3.1 基本路径

```text
Caller
  -> MemoryFacade.search()
  -> validate request
  -> ScopeResolver.resolveReadPlan()
  -> SearchCache.get(partition, query, topK, previousResponse)
  -> RecallOrchestrator.recall()
       -> IntentAnalyzer.analyze()
       -> HybridRetriever.retrieve()
          -> vector search
          -> FTS search
          -> graph expansion
          -> timeline / VFS / sidecar
       -> ACL filter
       -> deduplicate
       -> rank
       -> compress
  -> optional EvolutionEngine.recallForTurn()
  -> SearchCache.put(base memory result)
  -> MemorySearchResponse
```

### 3.2 缓存边界

缓存 key 应包含至少以下维度：

```text
tenant partition + phase + scope fingerprint + query + topK + previousResponse
```

缓存只保存普通 memory hits / whisper 的 base result，不缓存 evolution 的 `turnId`。每次带资产注入的 search 都应生成新的 `turnId`，避免后续 feedback 误归因。

`save` 成功入队后，应失效同 partition 的 search cache。

### 3.3 部分失败

单个检索后端失败时，HybridRetriever 应继续使用其他后端结果，并在响应 metadata 或指标中标记 partial degradation。

全部检索失败时：

```text
return hits=[]
return degraded=true
record metric memory.search.failed
do not fail agent main execution when caller policy is fail-open
```

## 4. Save 流程

### 4.1 同步接收

```text
Caller
  -> MemoryFacade.save()
  -> validate records
  -> ScopeResolver.resolveWriteDecision()
  -> PII/private policy
  -> EventJournal.append()
  -> LearningQueue.enqueue()
  -> SearchCache.invalidate(partition)
  -> SaveResponse(accepted=N, status=accepted)
```

同步 save 的完成只表示记录已被接收或排队，不表示 facts/concepts/assets 已完成抽取。

### 4.2 异步学习

```text
LearningWorker
  -> read queued records by partition
  -> accumulate sliding window
  -> when threshold reached
       -> TraceRefiner extracts facts/concepts
       -> Reflector extracts lessons or abstractions
       -> IndexWriter writes:
          -> timeline
          -> object/VFS
          -> scalar/FTS
          -> vector
          -> graph edges
       -> publish MemoryLearningCompleted
```

滑窗阈值、批大小和并发数属于 L2 配置。L1 只要求写入沉淀异步、可观测、可重试。

### 4.3 写入失败

写入失败分为三类：

| 类别 | 处理 |
|---|---|
| 入参非法 | 同步返回 4xx 或 SPI validation error。 |
| 入队失败 | 同步返回 `accepted=0` 或错误，调用方可重试。 |
| 后台沉淀失败 | 记录 dead letter / retry / metric，不回滚已接受的 ingest event。 |

## 5. Feedback 流程

### 5.1 基本路径

```text
Caller
  -> MemoryFacade.feedback(turnId, reward, weight)
  -> EvolutionEngine.lookupEligibility(turnId)
  -> AssetStore.getMany(assetIds)
  -> ReinforcementPolicy.update(asset, outcome)
  -> ThermodynamicStore.update(assetIds, energyDelta)
  -> AssetStore.refreshMetadata()
  -> FeedbackResponse(reinforced=N)
```

`turnId` 应有 TTL。过期或不存在时返回 reinforced=0，不应报错阻断调用方。

### 5.2 奖励来源

reward 可以来自：

- 明确用户反馈。
- 任务成功/失败状态。
- 被采纳、被重试、被人工改写等平台轨迹。
- 可选 LLM judge。

奖励映射到 `[-1, 1]`，`weight` 表示信号置信度。

## 6. Learn 流程

```text
Caller
  -> MemoryFacade.learn(trajectory, outcome)
  -> validate trajectory
  -> DistillationQueue.enqueue()
  -> LearnResponse(queued=true)

DistillationWorker
  -> AssetDistiller.distill()
  -> DuplicateGuard.searchSimilar()
  -> AssetStore.add(new assets)
```

蒸馏通常依赖 LLM 或规则抽取，必须在后台运行。该流程失败不影响普通 search/save。

## 7. 并发与背压

### 7.1 热路径并发

Search 是热路径，应有独立超时、并发上限和后端级隔离。

建议边界：

- HTTP/SPI request timeout。
- vector search timeout。
- FTS/SQL timeout。
- graph expansion node/edge 上限。
- compressor prompt/token 上限。
- search cache max size 与 TTL。

### 7.2 写入路径并发

Save 同步段应尽量短，只做校验、路由、journal 和入队。后台学习队列应有：

- 分区级顺序或 session lock，避免同一会话窗口竞态。
- 全局并发上限，避免抽取任务压垮模型或数据库。
- 队列容量上限和 dead-letter。
- 重试次数与退避。

### 7.3 Evolution 并发

EligibilityTrace 需要线程安全并有 TTL/LRU。Feedback 更新应按 asset id 批量、原子或幂等处理，避免并发 outcome 造成丢失更新。

## 8. 错误处理

### 8.1 稳定错误类别

| 错误类别 | 说明 |
|---|---|
| `INVALID_REQUEST` | 请求字段非法。 |
| `UNAUTHORIZED_SCOPE` | 调用方无权读写目标 scope。 |
| `MEMORY_TIMEOUT` | search 或后端超时。 |
| `BACKEND_UNAVAILABLE` | 存储或 embedding 后端不可用。 |
| `PARTIAL_DEGRADED` | 部分后端失败但返回了可用结果。 |
| `INGEST_REJECTED` | save 未入队。 |
| `EVOLUTION_DISABLED` | feedback/learn 调用了未启用能力。 |

### 8.2 Fail-open 与 Fail-closed

Search 默认 fail-open：返回空结果或 partial result。涉及越权、租户泄漏或私有数据读取时必须 fail-closed。

Save 对非法或越权写入 fail-closed；对后台沉淀失败 fail-open 并进入重试/告警。

## 9. 可观测性

memory service 至少暴露：

- search count、latency、timeout、cache hit/miss。
- save accepted、queue depth、dead letter。
- backend latency 和 error。
- hit count、empty result ratio。
- feedback reinforced count。
- learn queued/distilled count。
- scope rejection 和 redaction count。

日志必须脱敏，不输出完整用户输入和完整记忆内容。
