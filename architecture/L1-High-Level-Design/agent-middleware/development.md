---
level: L1-HLD
TAG:
  - development-view
  - code-organization
  - dependency-boundary
  - memory-service
status: draft
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - logical.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-middleware memory service L1 架构开发视图

## 1. 开发视图定位

本文档描述 memory service 在 `agent-middleware` 中的建议代码组织、依赖方向、扩展面、适配器和架构约束。

本文档不是对当前 `agent-middleware` 代码的反向说明，而是基于 doushuaigong MemOpt/Helix 记忆系统抽取出的 L1 设计。实际实现可分阶段落入 Java 模块，但应保持本文定义的边界。

## 2. 模块与构建形态

### 2.1 Maven 模块身份

memory service 属于根工程一级 Maven 模块 `agent-middleware` 的一个逻辑子域。它不应拆成依赖 `agent-runtime` 的子模块，也不应把 runtime Task 生命周期或 A2A 协议对象引入自身 SPI。

建议 artifact 边界：

```text
agent-middleware
  -> memory service SPI
  -> memory service implementation adapters
  -> optional Spring Boot auto-configuration
  -> optional HTTP facade
```

### 2.2 依赖原则

memory service 的核心 SPI 应只依赖 JDK、基础响应式/异步抽象和本模块中立类型。

可选实现依赖按 adapter 下沉：

| 依赖类型 | 允许位置 |
|---|---|
| Spring Boot Web / Actuator | `memory.boot` 或 facade adapter。 |
| Vector database client | `memory.store.vector.*` adapter。 |
| JDBC / JPA / jOOQ | `memory.store.scalar.*` adapter。 |
| Object storage / filesystem | `memory.store.object.*` adapter。 |
| Embedding model client | `memory.embedding` adapter。 |
| LLM distiller / judge | `memory.evolution` adapter。 |

核心 SPI 不依赖 Chroma、SQLite、NATS、OpenClaw、Python、A2A SDK 或具体 Agent 框架。

## 3. 建议包结构

命名空间根建议为：

```text
com.huawei.ascend.middleware.memory
```

建议包结构：

```text
agent-middleware/src/main/java/com/huawei/ascend/middleware/memory/
├── api
├── spi
├── scope
├── recall
├── ingest
├── evolution
├── store
│   ├── vector
│   ├── scalar
│   ├── graph
│   ├── timeline
│   ├── object
│   └── cache
├── adapter
│   ├── http
│   ├── runtime
│   └── spring
└── boot
```

### 3.1 api

`api` 包承载 northbound DTO 和错误语义。

代表性类型：

| 类型 | 职责 |
|---|---|
| `MemorySearchRequest` | search 请求。 |
| `MemorySearchResponse` | search 响应。 |
| `MemorySaveRequest` | save 请求。 |
| `MemoryFeedbackRequest` | feedback 请求。 |
| `MemoryLearnRequest` | learn 请求。 |
| `MemoryError` | 稳定错误码与降级原因。 |

### 3.2 spi

`spi` 包承载框架中立扩展面。

代表性类型：

| 类型 | 职责 |
|---|---|
| `MemoryProvider` | 对 Agent/runtime 暴露 init/search/save 的最小契约。 |
| `MemoryService` | 完整 memory service 能力，包括 feedback/learn。 |
| `MemoryStore` | 长期记忆读写抽象。 |
| `MemoryRetriever` | 召回抽象。 |
| `MemoryWriter` | 写入抽象。 |
| `MemoryScopeResolver` | 作用域解析和合成。 |
| `MemoryPolicy` | private/PII/ownership/phase 策略。 |
| `EvolutionStore` | operational assets 和强化抽象。 |

### 3.3 scope

`scope` 包承载作用域、ACL、ownership、phase 和 visibility。

该包不连接具体数据库。它只输出可执行的读写计划：

```text
MemoryScope + callerPrincipal
  -> ReadScopePlan
  -> WriteScopeDecision
```

### 3.4 recall

`recall` 包承载热路径召回编排。

职责包括：

- Intent/query analysis。
- 向量、FTS、图谱、时间线和原文旁路的候选生成。
- 候选合并、去重、重排。
- 上下文压缩和 provenance 输出。
- 部分失败降级。

### 3.5 ingest

`ingest` 包承载写入接收和异步学习。

职责包括：

- `save` 记录校验。
- 原始事件 journal。
- 滑窗与微批。
- facts/concepts/raw summary 抽取任务编排。
- 多索引写入。

抽取器可以是规则实现、LLM 实现或外部服务实现，但应通过接口接入。

### 3.6 evolution

`evolution` 包承载经验资产能力。

职责包括：

- OperationalAsset 模型。
- AssetSelector。
- EligibilityTrace。
- ReinforcementPolicy。
- AssetDistiller。
- Feedback/Learn 编排。

该包可独立关闭；关闭时普通 memory search/save 仍可工作。

### 3.7 store

`store` 是 storage-fabric 的 adapter 根包。

| 子包 | 职责 |
|---|---|
| `vector` | Embedding 文本写入、KNN 查询、metadata filter。 |
| `scalar` | FTS、热力学、状态计数、关系索引或 JDBC 查询。 |
| `graph` | 概念/事实/资产关系扩展。 |
| `timeline` | 事件、turn、fact 的时间线查询。 |
| `object` | 原文、长文、附件和 VFS/object storage。 |
| `cache` | TTL/LRU search cache、eligibility trace 和 hot memory。 |

### 3.8 adapter 与 boot

`adapter.http` 可提供 HTTP facade；`adapter.runtime` 可把 `agent-runtime` 的上下文映射为 `MemoryScope`；`boot` 提供 Spring Boot 自动配置、健康检查、指标和条件化 bean。

## 4. SPI 与扩展面

### 4.1 最小 Agent 记忆 SPI

面向 Agent/runtime 的最小 SPI 应接近 doushuaigong HTTP contract 中的映射：

```text
init(scope)        -> session / partition lazy init
search(scope, query, options) -> hits + optional whisper
save(scope, records)          -> accepted
```

这是热路径必须稳定的契约。

### 4.2 完整 service SPI

完整 service SPI 在最小 SPI 上增加：

```text
feedback(scope, turnId, outcome)
learn(scope, trajectory, outcome)
inspectSkills(scope, options)
redact(scope, recordId)
```

这些能力可按治理策略和部署 profile 启用。

### 4.3 存储 SPI

存储 SPI 应保留 doushuaigong 中 Helix 的可替换性，但避免绑定 Python 抽象名。

```text
VectorMemoryIndex
ScalarMemoryIndex
GraphMemoryIndex
TimelineMemoryStore
ObjectMemoryStore
ThermodynamicStore
```

其中 `ThermodynamicStore` 可与 scalar store 合并实现，但逻辑上表示 energy、stability、accessCount、wins/losses 等高频状态更新。

## 5. 自动配置边界

Spring Boot 自动配置应按能力分组：

| 配置组 | 说明 |
|---|---|
| `MemoryCoreAutoConfiguration` | MemoryService、ScopeResolver、RecallOrchestrator、IngestGateway。 |
| `MemoryStoreAutoConfiguration` | 默认 store adapter 和缓存。 |
| `MemoryHttpFacadeAutoConfiguration` | 可选 HTTP API。 |
| `MemoryEvolutionAutoConfiguration` | 可选 evolution layer。 |
| `MemoryObservabilityAutoConfiguration` | metrics、health、trace。 |

所有自动配置都应允许业务方以同类型 bean 覆盖默认实现。

## 6. 架构边界测试

建议用 ArchUnit 或等价测试固化以下约束：

- `memory.spi` 不依赖 Spring Web、A2A SDK、具体向量库、具体数据库、OpenClaw、NATS 或 Agent 框架。
- `memory.recall` 不直接调用 HTTP controller。
- `memory.store.*` adapter 不反向依赖 `agent-runtime`。
- `memory.evolution` 可关闭，普通 `MemoryProvider` 测试不需要 evolution 依赖。
- HTTP DTO 不泄漏底层 store client 类型。
- Tenant scope 是所有 store query 的必填过滤条件，除非 profile 明确为 single-user local。

## 7. 编码约束

### 7.1 不可变上下文

`MemoryScope`、`MemorySearchRequest`、`MemoryHit`、`OperationalAsset` 等值对象应优先不可变，避免异步 pipeline 中被共享修改。

### 7.2 幂等与去重

写入记录应支持 caller-provided id 或幂等键。异步抽取生成的 fact/concept/asset 应支持内容稳定 ID 或去重策略，避免重复 turn 导致长期记忆膨胀。

### 7.3 敏感信息处理

日志、metrics 和错误响应不得输出完整 prompt、完整 memory content 或 PII。调试输出必须有显式开关和脱敏。

### 7.4 Fail-open

memory search 超时、部分后端失败、evolution 失败、distillation 失败时，应返回 degraded result 或空结果，而不是抛出阻断 Agent 主流程的异常。写入路径可返回 `accepted=false` 或 `queued=false`，并暴露明确错误码。
