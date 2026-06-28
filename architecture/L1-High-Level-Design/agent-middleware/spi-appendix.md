---
level: L1-HLD
TAG:
  - spi-appendix
  - memory-service
  - extension-point
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
---

# agent-middleware memory service SPI 附录

## 1. SPI 定位

memory service SPI 是 Java 侧稳定扩展面，用于隔离 Agent/runtime 调用方、记忆编排逻辑和具体存储/模型实现。

本附录描述 SPI 语义和边界，不提供最终 Java 方法签名。

## 2. Agent-facing SPI

### 2.1 MemoryProvider

最小 Agent 记忆契约。

```text
MemoryProvider
├── init(scope)
├── search(scope, query, options)
└── save(scope, records, options)
```

语义约束：

- `init` 可 no-op，scope 可 lazy create。
- `search` 默认 fail-open，可返回空结果。
- `save` 同步表示 accepted/queued，不表示长期沉淀完成。
- SPI 不引用 A2A、Spring MVC、具体 Agent 框架或具体数据库类型。

### 2.2 MemoryService

完整服务契约。

```text
MemoryService extends MemoryProvider
├── feedback(scope, outcome)
├── learn(scope, trajectory)
├── redact(scope, target)
└── inspect(scope, query)
```

`inspect` 用于治理、调试或后台管理，不应暴露给普通 Agent 热路径。

## 3. Scope SPI

### 3.1 MemoryScopeResolver

```text
MemoryScopeResolver
├── resolveReadPlan(scope, principal, options)
└── resolveWriteDecision(scope, principal, record)
```

职责：

- 确定读集合：Org、Project、User、Agent、Session/Task。
- 过滤 phase、ownership、visibility。
- 标记必须 fail-closed 的越权场景。

### 3.2 MemoryPolicy

```text
MemoryPolicy
├── classify(record)
├── detectPrivate(record)
├── detectPii(record)
├── canRead(scope, record, principal)
└── canWrite(scope, record, principal)
```

策略实现可替换。企业运行态必须使用强治理策略；个人态可使用轻量策略。

## 4. Recall SPI

### 4.1 IntentAnalyzer

```text
IntentAnalyzer
└── analyze(query, previousResponse, scope)
    -> semanticQueries
    -> exactKeywords
    -> conceptAnchors
    -> pathHints
```

可由规则、LLM 或混合实现。失败时应返回基于原 query 的保守计划。

### 4.2 MemoryRetriever

```text
MemoryRetriever
└── retrieve(readPlan, queryPlan, options)
```

负责组合多后端候选，不负责最终压缩。

### 4.3 MemoryRanker

```text
MemoryRanker
└── rank(candidates, query, readPlan)
```

排序可考虑 relevance、scope weight、recency、energy、stability、source quality 和 conflict。

### 4.4 MemoryCompressor

```text
MemoryCompressor
└── compress(rankedHits, query, options)
```

输出结构化 hits 和可选 prompt-oriented whisper。压缩器必须有长度上限和敏感信息策略。

## 5. Ingest SPI

### 5.1 MemoryWriter

```text
MemoryWriter
├── accept(writeDecision, records)
└── enqueueLearning(events)
```

### 5.2 TraceRefiner

```text
TraceRefiner
└── refine(traceWindow)
    -> facts
    -> concepts
    -> summaries
```

### 5.3 IndexWriter

```text
IndexWriter
└── write(memoryProjection)
    -> timeline
    -> vector
    -> scalar
    -> graph
    -> object
```

IndexWriter 应支持幂等写入和部分投影重试。

## 6. Storage SPI

### 6.1 VectorMemoryIndex

```text
VectorMemoryIndex
├── upsert(documents, metadata, ids)
├── search(query, filter, limit)
└── delete(ids or filter)
```

### 6.2 ScalarMemoryIndex

```text
ScalarMemoryIndex
├── upsertRecord(record)
├── fullTextSearch(keywords, filter, limit)
├── exactMatch(field, value, filter)
└── delete(recordId)
```

### 6.3 ThermodynamicStore

```text
ThermodynamicStore
├── markAccessed(recordIds)
├── updateEnergy(recordIds, delta)
├── updateOutcome(assetId, wins, losses, uses, stability)
└── decay(scope)
```

### 6.4 GraphMemoryIndex

```text
GraphMemoryIndex
├── upsertNode(node)
├── upsertEdges(edges)
├── expand(nodeIds, relationTypes, limit)
└── deleteCascade(nodeId)
```

### 6.5 TimelineMemoryStore

```text
TimelineMemoryStore
├── append(event)
├── fetchWindow(scope, endTime, duration)
└── replay(scope, fromOffset)
```

### 6.6 ObjectMemoryStore

```text
ObjectMemoryStore
├── write(logicalPath, content, metadata)
├── read(logicalPath)
├── list(prefix)
└── delete(logicalPath)
```

## 7. Evolution SPI

### 7.1 EvolutionStore

```text
EvolutionStore
├── add(asset)
├── searchAssets(query, scope, kinds, limit)
├── getAssets(assetIds)
├── reinforce(updates)
└── listAssets(scope, options)
```

### 7.2 EligibilityTraceStore

```text
EligibilityTraceStore
├── record(turnId, assetIds, scope, ttl)
└── pop(turnId)
```

必须是 bounded/TTL。进程内实现可用于本地 profile；生产多实例应考虑 Redis 或持久化短期状态。

### 7.3 AssetDistiller

```text
AssetDistiller
└── distill(trajectory, outcomes, scope)
```

该 SPI 通常依赖 LLM，必须后台执行。

## 8. Observability SPI

```text
MemoryMetrics
MemoryAuditSink
MemoryTraceEmitter
```

这些扩展用于记录 search/save/feedback/learn 的指标、审计和链路信息。审计事件不得泄漏完整敏感内容。

## 9. 纯度约束

- SPI 包不得依赖 HTTP controller、Spring annotations、A2A SDK、Agent 框架 SDK、Chroma、SQLite、PostgreSQL client 或 LLM client。
- Adapter 可以依赖具体技术，但必须只实现 SPI。
- 所有 SPI 方法必须携带或可推导 tenant scope。
- 所有返回对象必须可序列化或可映射为 API DTO。
