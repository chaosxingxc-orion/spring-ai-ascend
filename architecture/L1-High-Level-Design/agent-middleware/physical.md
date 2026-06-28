---
level: L1-HLD
TAG:
  - physical-view
  - deployment-boundary
  - memory-service
status: draft
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - logical.md
  - development.md
  - process.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-middleware memory service L1 架构物理视图

## 1. 物理视图定位

本文档描述 memory service 的部署归属、进程组合、存储拓扑、缓存、资源模型和多租户隔离。

本文档只定义 L1 物理形态，不指定具体数据库选型或容量参数。具体 pgvector、Milvus、Elasticsearch、PostgreSQL schema、对象存储 bucket 和 RLS 策略应在 L2 中展开。

## 2. 部署形态

### 2.1 嵌入式 library 形态

memory service 可作为 `agent-middleware` library 嵌入宿主 Spring Boot 应用。

```text
Host JVM
  -> agent-service or agent-runtime host
  -> agent-middleware memory service library
  -> local adapters / remote storage clients
```

适合开发态、本地测试、小规模单体部署。宿主进程负责 HTTP server、鉴权入口、线程池、配置和生命周期。

### 2.2 独立 memory service 形态

memory service 可由专门 Spring Boot host 独立部署，对外提供 HTTP/gRPC/API。

```text
Agent Host JVM
  -> Memory client
  -> Network
Memory Service JVM
  -> Memory API
  -> storage-fabric
```

适合多 runtime 实例共享记忆、集中审计、多租户运行态和独立扩缩容。

### 2.3 Sidecar / 本地 profile 形态

参考 doushuaigong 的 HTTP facade 和 MemPalace sidecar，memory service 可提供轻量 sidecar profile：本地进程、文件/SQLite/向量库持久化、单用户或单租户隔离。

该形态用于开发态或个人 Agent，不承诺企业级租户隔离。

## 3. 单实例拓扑

```text
+---------------------------------------------------+
| Memory Service Process / Host JVM                 |
|                                                   |
|  Memory API / SPI                                 |
|      |                                            |
|  Scope Governance                                 |
|      |                                            |
|  Recall Orchestrator                              |
|      |                                            |
|      +--> Search Cache (TTL/LRU)                  |
|      +--> Vector Index Client                     |
|      +--> Scalar / FTS Client                     |
|      +--> Graph Index Client                      |
|      +--> Timeline Store Client                   |
|      +--> Object / VFS Store Client               |
|                                                   |
|  Ingest Gateway                                   |
|      +--> Event Journal                           |
|      +--> Learning Queue                          |
|              +--> Learning Workers                |
|                                                   |
|  Evolution Layer                                  |
|      +--> Eligibility Trace Cache                 |
|      +--> Asset Store                             |
+---------------------------------------------------+
```

## 4. 存储物理模型

### 4.1 逻辑到物理映射

| 逻辑存储面 | 本地/开发态候选 | 企业/运行态候选 | 说明 |
|---|---|---|---|
| Vector store | Chroma / local vector index | pgvector / Milvus / OpenSearch vector | 语义召回。 |
| Scalar / FTS | SQLite FTS5 | PostgreSQL FTS / OpenSearch / Elasticsearch | 字面匹配、热度和状态。 |
| Graph store | SQLite edge table / files | PostgreSQL graph tables / graph db | 概念和事实关系。 |
| Timeline store | JSONL / SQLite | PostgreSQL partitioned tables / object log | turn、event、fact 时间线。 |
| Object / VFS | local filesystem | S3/OBS/NAS/object store | 原文、附件、长内容证据。 |
| Hot cache | in-process Caffeine | Redis / in-process + Redis | search cache、eligibility trace。 |

### 4.2 多投影一致性

长期记忆通常以多投影存在：

```text
raw turn / trace
  -> event journal
  -> timeline
  -> fact / concept record
  -> scalar / FTS index
  -> vector index
  -> graph edges
  -> object evidence
```

这些投影不要求强事务一致，但必须有可重放的 source event、幂等 ID 和修复任务，确保索引损坏或部分失败时可以重建。

### 4.3 热力学状态

Energy、stability、accessCount、wins、losses、lastAccessedAt 是高频更新状态，适合放在标量存储或专门状态表中。向量 metadata 可保存读取投影，但不能作为唯一权威状态。

## 5. 多租户隔离

### 5.1 Tenant 硬隔离

企业运行态下，所有物理存储查询都必须包含 tenant 过滤或使用租户级隔离容器。

候选隔离方式：

- 单库共享表 + tenant_id + RLS。
- 租户级 schema。
- 租户级数据库。
- 租户级 collection / index。

L1 不指定唯一方案，但要求跨租户读取在 API、store adapter 和物理查询三层都不可绕过。

### 5.2 作用域过滤

除 tenant 外，记录还应保存 scope_type、scope_id、phase、ownership、visibility、owner_user_id、project_id、agent_id 等治理字段。Vector index 的 metadata filter 必须与 scalar/ACL filter 一致。

### 5.3 个人态 profile

个人态或开发态可使用简化 partition，例如 `user_id or session_id`。该 profile 不应被误标为企业运行态能力。

## 6. 资源模型

### 6.1 CPU 与线程

热路径 search 消耗：

- embedding query 生成或向量库 query。
- 多后端并行查询。
- 排序、去重和压缩。
- 可选 LLM compressor。

写入后台消耗：

- 滑窗抽取。
- LLM 反思/蒸馏。
- 向量写入。
- FTS/graph/object 写入。

这些线程池应分离，避免 learn/distill 影响 search。

### 6.2 内存

主要内存来源：

- TTL/LRU search cache。
- EligibilityTrace cache。
- 后台队列。
- 批处理窗口。
- 查询候选集。
- compressor prompt 构造。

每个维度都应有上限。候选数量和 prompt 长度必须在 recall-orchestrator 中裁剪。

### 6.3 存储容量

容量由以下因素决定：

- 原始 turn 和 trace 保留期。
- fact/concept 抽取密度。
- 向量维度和记录数。
- 原文/VFS 附件大小。
- operational assets 数量。
- 多租户和多 phase 的索引副本。

保留期、归档和删除策略属于治理配置，L2 应与合规要求绑定。

## 7. 高可用与恢复

### 7.1 Search 高可用

Search 可通过以下方式提升可用性：

- 多后端 partial result。
- Search cache。
- 读副本。
- vector/FTS 超时隔离。
- 空结果 fail-open。

### 7.2 Write 恢复

写入路径应以 event journal 为恢复基础。后台索引失败后可从 journal 重放，重新生成 timeline、vector、FTS、graph 和 object 投影。

### 7.3 Evolution 恢复

EligibilityTrace 是短期状态，丢失后只影响未反馈 turn 的强化，不影响普通记忆。OperationalAsset 的 durable 状态必须写入长期存储，避免进程重启后丢失 energy/win/loss。

## 8. 部署约束

- 独立 memory service 形态下，Agent host 与 memory service 的网络超时必须短于 Agent 主执行超时预算。
- 生产运行态不得使用无 tenant 过滤的单 collection 全局向量检索。
- 本地文件/SQLite profile 不应用于多租户生产。
- LLM distillation 和 judge 应异步或低优先级运行，不抢占 search 热路径资源。
- 所有 debug API 必须受鉴权和脱敏策略保护。
