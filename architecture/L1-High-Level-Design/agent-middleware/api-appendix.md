---
level: L1-HLD
TAG:
  - api-appendix
  - memory-service
  - contract
status: draft
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - logical.md
  - development.md
  - process.md
  - physical.md
  - spi-appendix.md
---

# agent-middleware memory service API 附录

## 1. API 定位

memory service API 是框架中立的记忆访问面。它可以实现为 Java SPI、HTTP facade、gRPC 或内部 service call，但语义应保持一致。

本附录只定义 L1 API 面，不提供完整 JSON Schema 或 Java 签名。

## 2. API 清单

| API | 热路径 | 作用 |
|---|---|---|
| `init` | 否 | 初始化或检查某 scope/partition 的记忆上下文。 |
| `search` | 是 | 在 Agent 生成前召回记忆。 |
| `save` | 是/准热路径 | 接收对话 turn 或记忆记录，异步沉淀。 |
| `feedback` | 否 | 对某次召回注入的资产进行结果强化。 |
| `learn` | 否 | 从已完成轨迹蒸馏经验资产。 |
| `redact` | 否 | 删除、撤回或遮蔽某条记忆。 |
| `health` | 是 | 健康检查。 |
| `metrics` | 否 | 轻量指标面。 |

## 3. Search API

### 3.1 Request

```json
{
  "query": "what did we decide about the index?",
  "previousResponse": "We compared B-tree vs hash.",
  "topK": 5,
  "scope": {
    "tenantId": "t1",
    "orgId": "o1",
    "projectId": "p1",
    "userId": "u1",
    "agentId": "a1",
    "sessionId": "s1",
    "taskId": "k1",
    "phase": "runtime",
    "ownership": "business"
  },
  "options": {
    "includeWhisper": true,
    "includeAssets": true,
    "failOpen": true
  }
}
```

### 3.2 Response

```json
{
  "hits": [
    {
      "id": "mem-1",
      "content": "User prefers B-tree for range queries.",
      "score": 0.82,
      "kind": "fact",
      "scope": {"tenantId": "t1", "scopeType": "project", "scopeId": "p1"},
      "metadata": {"source": "retriever", "tags": ["db", "index"]},
      "provenance": {"sourceId": "trace-1", "timestamp": "2026-06-01T00:00:00Z"}
    }
  ],
  "whisperXml": "<memory_whisper>...</memory_whisper>",
  "assets": [
    {
      "id": "asset-1",
      "kind": "skill",
      "title": "Index decision recall",
      "body": "When discussing index choice, recall range query requirements first."
    }
  ],
  "turnId": "turn-123",
  "degraded": false,
  "warnings": []
}
```

`turnId` 只在 assets 被注入且 evolution layer 启用时返回。调用方可在任务完成后携带它调用 `feedback`。

## 4. Save API

### 4.1 Request

```json
{
  "scope": {
    "tenantId": "t1",
    "projectId": "p1",
    "userId": "u1",
    "agentId": "a1",
    "sessionId": "s1",
    "phase": "runtime",
    "ownership": "business"
  },
  "records": [
    {
      "id": "m1",
      "role": "user",
      "content": "Let's use B-tree.",
      "metadata": {"visibility": "project"}
    },
    {
      "id": "m2",
      "role": "assistant",
      "content": "Agreed, B-tree it is.",
      "metadata": {}
    }
  ],
  "options": {
    "idempotencyKey": "save-1"
  }
}
```

### 4.2 Response

```json
{
  "accepted": 2,
  "status": "accepted",
  "queued": true
}
```

`accepted` 表示 ingest 接收数量，不表示长期索引完成。

## 5. Feedback API

### 5.1 Request

```json
{
  "scope": {"tenantId": "t1", "userId": "u1"},
  "turnId": "turn-123",
  "reward": 1.0,
  "weight": 0.8,
  "metadata": {
    "source": "task-outcome",
    "reason": "accepted"
  }
}
```

### 5.2 Response

```json
{
  "reinforced": 3,
  "status": "ok",
  "rewardUsed": 1.0
}
```

当 evolution layer 关闭时，返回 `status=evolution-disabled` 和 `reinforced=0`。

## 6. Learn API

### 6.1 Request

```json
{
  "scope": {"tenantId": "t1", "projectId": "p1", "phase": "runtime"},
  "trajectory": [
    {"role": "user", "content": "How should we deploy?"},
    {"role": "assistant", "content": "Use blue-green deployment."}
  ],
  "reward": 1.0,
  "weight": 1.0
}
```

### 6.2 Response

```json
{
  "status": "ok",
  "queued": true
}
```

Learn 是后台蒸馏入口，不保证同步生成资产。

## 7. Redact API

`redact` 用于撤回、删除或遮蔽记忆。

```json
{
  "scope": {"tenantId": "t1", "userId": "u1"},
  "recordId": "mem-1",
  "mode": "delete|mask|tombstone",
  "reason": "user-request"
}
```

Redact 必须传播到 vector、scalar、graph、timeline、object 和 cache 投影。具体补偿策略属于 L2。

## 8. Health 与 Metrics

### 8.1 Health

```json
{
  "status": "ok",
  "service": "agent-middleware-memory",
  "version": "v1",
  "stores": {
    "vector": "ok",
    "scalar": "ok",
    "object": "ok"
  }
}
```

### 8.2 Metrics

指标面至少包含：

```json
{
  "searches": 12,
  "cacheHits": 4,
  "cacheMisses": 8,
  "timeouts": 0,
  "saves": 3,
  "queueDepth": 0,
  "feedbacks": 2,
  "learnQueued": 1
}
```

## 9. 错误语义

| HTTP / SPI 类别 | 错误码 | 含义 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 请求结构或字段非法。 |
| 401/403 | `UNAUTHORIZED_SCOPE` | 调用方无权访问目标 scope。 |
| 404 | `MEMORY_NOT_FOUND` | redact/inspect 目标不存在。 |
| 409 | `IDEMPOTENCY_CONFLICT` | 幂等键冲突。 |
| 429 | `MEMORY_BACKPRESSURE` | 队列或并发上限触发。 |
| 500 | `MEMORY_INTERNAL` | 未分类内部错误。 |
| 503 | `BACKEND_UNAVAILABLE` | 存储或 embedding 后端不可用。 |
| 504 | `MEMORY_TIMEOUT` | search/后端超时。 |

Search 在 `failOpen=true` 时可将 503/504 转换为空 hits + degraded warning。越权错误不得 fail-open。

## 10. 版本策略

API 路径或 SPI capability 应使用显式版本。新增字段默认向后兼容；删除字段、改变字段含义、改变作用域隔离语义、改变错误码或改变 feedback 归因语义必须升级主版本。
