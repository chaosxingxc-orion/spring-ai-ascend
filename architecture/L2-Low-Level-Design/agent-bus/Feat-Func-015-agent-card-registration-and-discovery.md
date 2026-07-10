---
level: L2
module: agent-bus
feature: Feat-Func-015
feature_name: Agent Card 注册与发现
status: draft
updated: 2026-07-10
authority:
  - ../../../version-scope/Feat-015-agent-card-registration-and-discovery.md
  - ../L0-Top-Level-Design/boundaries.md
  - ../L1-High-Level-Design/agent-bus/logical.md
  - ../L1-High-Level-Design/agent-bus/process.md
  - ./registry-discovery-runtime-design.cn.md
implementation_ref:
  - /home/zhongyiwei/git/agent-solution/common/agent-rdc
covers_contract: ICD-Agent-Registry-Discovery
---

# Agent Card 注册与发现 — L2 低层设计

> 本文档把 `version-scope/Feat-015-agent-card-registration-and-discovery.md` 的框架级能力要求落地为 `agent-bus` registry-discovery-center 单元的低层设计。MVP 实现基线参考 `agent-solution/common/agent-rdc`（下文简称 **agent-rdc**），运行态技术基线见 [registry-discovery-runtime-design.cn.md](./registry-discovery-runtime-design.cn.md)。本文不重复该文档已固化的 SQL / 心跳 / RLS 细节，只聚焦 Feat-015 特性视角下的字段映射、接口契约、查询语义、失败码与阶段边界。

## 1. 概述

### 1.1 特性定位

- **是什么**：`agent-bus` registry-discovery-center 单元对外提供框架级 Agent Card 注册与发现能力。下游 AgentLoop / workflow / 异构 Agent / adapter 通过 Agent Card 声明身份、能力、调用契约、治理属性与可发现元数据；注册中心负责接收注册并按租户、调用方、能力类型、标签、描述与执行约束查询可执行能力集合。
- **解决什么**：before——下游能力没有统一注册入口，调用方只能硬编码目标地址或私有能力目录；after——所有可执行能力经统一 Agent Card 注册后可被按多维度查询，并返回 opaque `route_handle` 供转发层寻址，调用方不接触物理地址。
- **适用场景**：框架级能力目录建设、意图识别场景下的动态能力匹配（作为基础能力的一个使用场景）、异构 Agent 框架统一接入。

### 1.2 核心设计原则

| 原则 | 理由 |
|------|------|
| **注册中心只拥有 runtime route index / discovery view，不拥有 agent 业务定义、不写 Task 状态** | L0 边界约束（HD3-001）；避免 registry 成为业务事实源。 |
| **Agent Card 是注册中心的视图，不是 A2A 标准 AgentCard 的复制品** | A2A `/.well-known/agent-card.json` 是 runtime 对外卡片；registry card 增补路由 / 契约 / 治理字段，两者字段重叠但不等价。参考实现已用 `AgentRegistryEntry` vs `a2aAgentCard` 字段分离体现这一原则。 |
| **`route_handle` opaque，物理地址只对转发层可见** | HD3-006；让 route handle 编码格式可演进（`v1:` 5 字段）而不破坏跨模块消费者。 |
| **租户强制、禁止跨 tenant fallback** | HD3-003；RLS 作为防御深度，应用层 `WHERE tenant_id` 为主路径。 |
| **两阶段演进：MVP 纯 PostgreSQL + SQL 过滤排序 → 生产引入 pgvector 语义检索** | 阶段一快速跑通；阶段二迁移对 Agent 服务侧零改动、对上层 Orchestrator 零改动。 |

### 1.3 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| Agent Card 注册 | 接收、校验、upsert Agent Card | `AgentRegistryEntry`、`POST /api/registry/register`、`ServiceIdCodec` | ✅ MVP |
| Agent Card 更新与失效 | 重注册 upsert、deregister、DRAINING 保留 | `DELETE /api/registry/deregister/...`、`ON CONFLICT` 保留 DRAINING | ✅ MVP |
| Agent Card 查询（已知目标） | 按 `tenantId + agentId` 列举可路由实例 | `AgentDiscoveryService#searchInstancesByAgentId` | ✅ MVP |
| Agent Card 查询（能力维度） | 按 `capability_type` / `tags` / `domain` / `query_text` / 执行约束查询 | （scope §3-§4） | ⬜ 阶段二 |
| 推荐首选与证据 | 返回 `recommended_agent_card` / `score` / `evidence` | （scope §4） | ⬜ 阶段二 |
| 意图识别动态能力匹配 | `IntentRouteRequest` + `route_query` + `runtime_facts` 驱动查询 | （scope §6） | ⬜ 阶段二 |
| 健康与版本可用性过滤 | `status` / `contractVersion` 过滤不可调用能力 | discovery SQL `status IN ('ONLINE','DEGRADED')` | ✅ MVP |

> 状态对齐 [registry-discovery-runtime-design.cn.md](./registry-discovery-runtime-design.cn.md) 两阶段计划：阶段一纯 PG + SQL（当前），阶段二引入 pgvector 语义检索 + 推荐排序。scope §11 已显式将语义检索、学习排序、历史效果反馈列为「开发设计待细化事项」。

## 2. 功能规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| Agent Card 注册（push） | ✅ | `POST /api/registry/register` upsert `AgentRegistryEntry`；必填字段校验 + `serviceId` 服务端派生。 |
| Agent Card 注册（pull） | ✅ | `rdc.pull-registration.enabled=true` 时 `PullRegistrationBootstrap` 拉取 `/.well-known/agent-card.json` 构造 entry upsert；单实例失败跳过，不阻塞启动。 |
| Agent Card 校验 | ✅ | registry key（`tenantId + agentId`）必填、`frameworkType` 枚举非空、`endpointUrl` 可派生 `serviceId`；缺字段 400 `invalid_request`。 |
| Agent Card 更新 / 下线 | ✅ | 重注册 upsert（DRAINING 保留）、`deregister/{tenantId}/{agentId}` 批量删、`deregister/{tenantId}/{agentId}/{serviceId}` 单实例删。 |
| 租户与调用方边界 | ✅ | `tenantId` 强制；RLS + 应用层 `WHERE tenant_id`；跨 tenant 抛 `TenantIsolationViolationException`。 |
| 已知目标查询 | ✅ | `searchInstancesByAgentId(tenantId, agentId) → List<AgentCardDto>`，返回 ONLINE/DEGRADED 实例。 |
| 能力维度查询 | ⬜ | 按 `capability_type` / `tags` / `domain` / `query_text` / `execution_constraints` 查询——MVP 已移除 `capability` 字段（REQ-2026-004），阶段二以 pgvector 语义检索重建。 |
| 可用性过滤 | ✅ | discovery SQL `status IN ('ONLINE','DEGRADED')` + `contractVersion` / `capabilityVersion` 在 DTO 中返回，调用方自行做版本兼容判断。 |
| 推荐首选 + 证据 | ⬜ | `recommended_agent_card` / `score` / `evidence` 阶段二引入。 |
| opaque route handle | ✅ | `v1:` + base64(JSON{tenantId, agentId, serviceId, routeKey, contractVersion})；DTO 不带物理地址。 |
| 无能力结果 | ✅ | `searchInstancesByAgentId` 返回空 List = agent_not_found；`resolveRouteHandle` 找不到 entry 抛 `NoSuchElementException` → 404 `entry_not_found`。 |
| 可查询扩展元数据 | ⚠️ | MVP 保留 `a2a_agent_card` JSONB 列存原始 A2A 卡片，但未建立按 `intent_match_metadata` / `tags` / `description` 的查询索引——阶段二 pgvector 重建。 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| Agent 业务定义源 | 属于 agent-runtime / agent-core 职责 | registry 只存路由 / 契约 / 治理字段 + A2A 卡片 JSONB 元数据 |
| Task execution state | L0 边界——bus 不拥有 Task 生命周期 | 见 L1 logical §4 |
| 候选发现面向 agent/client | Feat-016 明确：agent/client 只看路由可用性投影，不看候选 | 见 [Feat-Func-016-runtime-instance-route-query.md](./Feat-Func-016-runtime-instance-route-query.md) |
| 语义检索 / 学习排序 | scope §11 列为后续增强 | 阶段二 pgvector |
| 自然语言意图库 / 文档召回 | scope §1 显式排除——注册中心返回可执行能力集合，不是知识检索 | 业务侧自建 |

### 2.3 接口契约（Logical View）

#### 2.3.1 SPI 声明

注册中心对外暴露的核心 SPI（agent-rdc `com.openjiuwen.rdc.spi.registry`）：

```java
/**
 * agent-bus 拥有的运行时路由索引查询入口。上层 Orchestrator / Gateway 仅依赖此接口；
 * 持久化形态（MVP 单 PG，阶段二 Consul + pgvector）对调用方不可见。
 *
 * 当前版本只覆盖「已知目标查询」语义；能力维度查询 / 推荐首选 / 证据保留
 * 属于 Feat-015 阶段二目标，尚未在此 SPI 上提供方法。
 */
public interface AgentDiscoveryService {

    /**
     * 列出给定 (tenantId, agentId) 下所有 ONLINE/DEGRADED 运行时实例。
     * 每个实例携带独立的 opaque routeHandle（编码 serviceId）。
     * 调用方（Orchestrator / Gateway）自行选择实例并经
     * {@link #resolveRouteHandle} 解析。
     *
     * 状态过滤：只返回 ONLINE / DEGRADED；DRAINING / OFFLINE 排除。
     * 排序：weight DESC, last_heartbeat DESC。
     *
     * @param tenantId registry key 强制维度；与 TenantContext 不一致抛
     *                 TenantIsolationViolationException
     * @param agentId  registry key 强制维度
     * @return 不可变列表，无匹配返回空 List（永不 null）
     */
    List<AgentCardDto> searchInstancesByAgentId(String tenantId, String agentId);

    /**
     * 解析 opaque routeHandle 为转发层可用的 RouteResolution。
     * Orchestrator 业务逻辑永不调用此方法——只有转发层调用（HD3-006）。
     *
     * @throws IllegalArgumentException           handle 畸形（含旧 4 字段格式）
     * @throws TenantIsolationViolationException  tenantId 与 handle 内编码不一致
     * @throws NoSuchElementException              handle 指向的 entry 不存在
     */
    RouteResolution resolveRouteHandle(String routeHandle, String tenantId);
}
```

> **阶段二扩展预留**：当 pgvector 语义检索落地后，本 SPI 将新增 `searchByCapability(CapabilityQuery query)` 方法，返回 `DiscoveryResult`（含 `agent_cards` / `recommended_agent_card` / `score` / `evidence`）。该方法的方法签名、分页、排序规则在阶段二 ADR 中固化，不在本文预先钉死。

#### 2.3.2 数据类型

##### AgentRegistryEntry（注册请求体 / 表行 ORM）

| 字段 | scope §3 对应字段 | 必填 | 含义 / 约束 |
|------|------------------|------|------------|
| `tenantId` | `tenant_scope` | 是 | 租户边界；registry key 维度；跨 tenant fallback 禁止。 |
| `agentId` | `agent_id` | 是 | 下游 Agent 逻辑标识；registry key 维度。 |
| `serviceId` | `service_id` | 服务端派生 | 从 `endpointUrl` 经 `ServiceIdCodec.derive` 派生（`host-port`）；setter 包级私有，HTTP 调用方不可伪造。 |
| `agentName` | `capability_name`（近似） | 是 | 展示名；A2A 卡片的 `name` 字段。 |
| `frameworkType` | `framework_type` | 是 | 枚举 `JIUWEN` / `AGENTSCOPE` / `VERSATLE` / `PROXY_SERVICE`；替代 scope §3 的 `framework_type` 字符串。 |
| `routeKey` | （路由索引源） | 是 | 逻辑路由键，封装进 route handle。 |
| `contractVersion` | `contract_version` | 是 | HD3-005 版本约束。 |
| `capabilityVersion` | `capability_version` | 是 | 能力版本。 |
| `endpointUrl` | （路由索引源） | 是 | 逻辑目标；不直接暴露给 discover 调用方，封装进 route handle。 |
| `maxConcurrency` | （selectionHint） | 否 | 默认 10；NOT NULL 列，缺省由 controller `applyDefaults` 物化。 |
| `weight` | `priority`（近似） | 否 | 默认 100；NOT NULL 列；排序提示。 |
| `region` | （selectionHint） | 否 | 部署区域提示。 |
| `a2aAgentCard` | （扩展元数据） | 否 | A2A 标准 `AgentCard` 对象，序列化为 JSONB 存 `a2a_agent_card` 列。 |

##### scope §3 字段在 MVP 的覆盖情况

| scope §3 字段 | MVP 覆盖 | 说明 |
|---------------|---------|------|
| `tenant_scope` | ✅ `tenantId` | 单租户边界，不支持多租户可见性声明。 |
| `agent_id` / `service_id` | ✅ | `serviceId` 服务端派生。 |
| `capability_id` / `capability_name` / `capability_type` | ⚠️ | MVP 已移除 `capability` 字段（REQ-2026-004）；`agentName` 部分覆盖展示名；`capability_type`（agent_loop / workflow / external_agent / external_workflow）未实现，阶段二随 `searchByCapability` 一并引入。 |
| `description` / `intent_match_metadata` / `tags` / `domain` | ⚠️ | A2A 卡片 JSONB 保留原始信息，但未建立查询索引；阶段二 pgvector 重建。 |
| `framework_type` | ✅ | 枚举化（替代字符串）。 |
| `contract_version` / `capability_version` | ✅ | |
| `route_handle` | ✅ | 输出字段，由 `RouteHandleCodec` 生成。 |
| `health_status` | ✅ | `status` 列 `ONLINE` / `DEGRADED` / `DRAINING` / `OFFLINE`。 |
| `supports_streaming` / `supports_hitl` / `requires_idempotency_key` | ⬜ | 未实现；阶段二随执行约束查询引入。 |
| `priority` | ✅ `weight` | 近似映射。 |
| `risk_hint` / `cancelable_hint` | ⬜ | 未实现。 |

##### AgentCardDto（discovery 结果）

| 字段 | 可空 | 含义 |
|------|------|------|
| `routeHandle` | 否 | opaque 路由引用（HD3-006） |
| `health` | 否 | `status` 字符串 |
| `contractVersion` / `capabilityVersion` | 否 | 版本信息 |
| `weight` / `region` / `maxConcurrency` | 否 | selectionHint，调用方做加权负载均衡 |
| `agentName` / `frameworkType` | 是 | 业务定义字段，`searchInstancesByAgentId` 填充 |

##### RouteResolution（转发层专用）

| 字段 | 含义 |
|------|------|
| `endpointUrl` | 物理端点——仅转发层可见 |
| `routeKey` | 逻辑路由键 |
| `contractVersion` | 注册时钉定的契约版本 |

#### 2.3.3 行为承诺

- **必须**：注册时校验 `tenantId + agentId` registry key；`serviceId` 由服务端从 `endpointUrl` 派生，HTTP 调用方传入值被 `ServiceIdCodec.applyTo` 覆写。
- **必须**：discovery 返回的 `AgentCardDto` 不携带 `endpointUrl` / `routeKey` / `serviceId` 明文——只有 opaque `routeHandle`。
- **必须**：`resolveRouteHandle` 仅由转发层调用；`RouteHandleCodec` 不离开 `registry.runtime.discovery` 包。
- **必须**：所有写操作（upsert / delete / updateStatus）在事务内 `set_config('app.tenant_id', :tenantId, true)`，RLS 作为防御深度。
- **必须**：重注册时若原状态为 `DRAINING`（运维发起的优雅下线），新状态保持 `DRAINING` 而非重置为 `ONLINE`——避免重启把流量重新打到正在下线的实例（PR #389 #7）。
- **禁止**：跨 tenant 查询、缓存复用、降级或 fallback。
- **禁止**：向 discover 调用方返回 `endpointUrl` / `routeKey` / `serviceId` 明文。
- **允许**：MVP 阶段不实现能力维度查询、推荐首选、语义检索——这些属于阶段二目标。

## 3. 模块结构（Development View）

### 3.1 包结构

```
com.openjiuwen.rdc/
├── AgentRdcApplication.java              # @SpringBootApplication 入口
├── spi/registry/                         # 纯 Java SPI（ADR-0160 decision 1：无 Spring/JDBC/Jackson）
│   ├── AgentDiscoveryService.java        # 发现 SPI（searchInstancesByAgentId / resolveRouteHandle）
│   ├── AgentRegistryEntry.java           # 注册请求体 / 表行 ORM
│   ├── AgentCardDto.java                 # discovery 结果 DTO
│   ├── RouteResolution.java              # route handle 解析结果（转发层专用）
│   ├── ServiceIdCodec.java               # serviceId 派生（host-port）
│   ├── FrameworkType.java                # 枚举：JIUWEN/AGENTSCOPE/VERSATLE/PROXY_SERVICE
│   ├── TenantContext.java                # 租户上下文接口
│   ├── TenantIsolationViolationException.java
│   └── Nullable.java                     # 注解，标记可空字段
├── registry/runtime/
│   ├── api/MvpRegistryController.java    # HTTP 入口（Spring Web，5 个端点）
│   ├── discovery/
│   │   ├── PgMvpDiscoveryServiceImpl.java # AgentDiscoveryService 实现（@Primary @Service）
│   │   └── RouteHandleCodec.java          # v1: 5 字段编解码（包内私有）
│   ├── persistence/jdbc/
│   │   ├── AgentRegistryRepository.java   # 仓储端口接口
│   │   └── JdbcAgentRegistryRepository.java # 唯一允许 import java.sql 的类
│   ├── health/MvpHealthProbeScheduler.java # 5s pull 探活
│   ├── pull/PullRegistrationBootstrap.java # pull 模式注册（ApplicationReadyEvent）
│   ├── tenant/ThreadLocalTenantContext.java # 后台调度租户绑定
│   ├── RegistryRuntimeBeanConfig.java
│   ├── RegistryObservabilityConfig.java   # 审计 / 指标
│   └── RegistrySchedulingConfig.java      # 探活线程池隔离
└── resources/db/migration/
    ├── V2__create_agent_registry_mvp.sql   # 建表（含 RLS）
    ├── V3__refactor_agent_registry_mvp_drop_legacy_fields.sql
    ├── V4__refactor_agent_registry_drop_capability_search_tsv_rename_framework_type.sql
    └── V5__multi_instance_service_id_pk.sql # PK → (tenant_id, agent_id, service_id)
```

### 3.2 核心类静态关系

```
«interface»                      «concrete»
AgentDiscoveryService            PgMvpDiscoveryServiceImpl
     ▲                                 │ implements (@Primary)
     │                                 │
     │ delegates                       │ uses (package-private)
     │                                 ▼
     │                          RouteHandleCodec ──encode/decode──► v1: base64 JSON
     │                                 │
     │                                 │ delegates
     │                                 ▼
«interface»                   «concrete»
AgentRegistryRepository ───implements──► JdbcAgentRegistryRepository
                                          │ (only class with java.sql imports)
                                          ▼
                                     agent_registry_mvp (PG, RLS)

«POJO»          «DTO»            «record»
AgentRegistryEntry  AgentCardDto     RouteResolution
   │                  │                  ▲
   │ ServiceIdCodec   │                  │
   └────applyTo──────►│                  │
                                        │ returned by
                                        │
                              AgentDiscoveryService.resolveRouteHandle
```

## 4. 核心设计（Logical + Process View）

### 4.1 Agent Card 注册（push）

#### 4.1.1 关键处理流程

```
Agent 服务                MvpRegistryController            ServiceIdCodec          JdbcAgentRegistryRepository
   │                            │                                │                          │
   │── POST /api/registry/register ──────────────────────────────►│                          │
   │   body: AgentRegistryEntry  │                                │                          │
   │   header: traceparent?      │                                │                          │
   │                            │── hasRegistryKey()? ── no ──► 400 invalid_request          │
   │                            │── applyDefaults (maxConcurrency=10, weight=100)             │
   │                            │── ServiceIdCodec.applyTo(card) ─► derive host-port from      │
   │                            │                                endpointUrl, overwrite        │
   │                            │                                serviceId (包级私有 setter)   │
   │                            │── serializeA2aCard → JSON                                   │
   │                            │── repository.upsert(card, a2aJson) ────────────────────────►│
   │                            │                                │                          │── BEGIN TX
   │                            │                                │                          │── set_config('app.tenant_id')
   │                            │                                │                          │── INSERT ... ON CONFLICT
   │                            │                                │                          │   (tenant_id,agent_id,service_id)
   │                            │                                │                          │   DO UPDATE; preserve DRAINING
   │                            │                                │                          │── COMMIT
   │                            │── observeRegister (audit/metrics)                          │
   │◄── 200 OK ─────────────────│                                │                          │
```

#### 4.1.2 边界条件

| 条件 | 行为 |
|------|------|
| body 缺 `tenantId` 或 `agentId` | 400 `invalid_request` |
| `endpointUrl` 无 host / 畸形 | `ServiceIdCodec.derive` 抛 `IllegalArgumentException` → 400 |
| `frameworkType` 为 null | 400（`AgentRdcRegistrySpiPurityTest` 断言） |
| 原 entry 状态为 `DRAINING` | upsert 保留 `DRAINING`，不重置为 `ONLINE` |
| 原 entry 状态为 `ONLINE`/`DEGRADED`/`OFFLINE` | upsert 重置为 `ONLINE`，刷新 `last_heartbeat` |

### 4.2 Agent Card 注册（pull）

`PullRegistrationBootstrap` 监听 `ApplicationReadyEvent`，串行 HTTP GET 每个 `rdc.pull-registration.runtimes[]` 配置的 runtime 的 `/.well-known/agent-card.json`，构造 `AgentRegistryEntry` 并 upsert。

- **激活**：`rdc.pull-registration.enabled=true`（默认 false）。
- **失败隔离**：单 runtime 失败记录 WARN 日志并跳过，不阻塞启动。
- **bootstrap-only**：无定时重拉、无刷新；agent 重启时由 push 重注册或运维重触发。
- **operator-pinned 字段**：`tenantId` / `agentId` / `frameworkType` / `routeKey` / `contractVersion` / `capabilityVersion` 由配置钉定（A2A AgentCard 无这些字段）；`agentName` 从卡片 `name` 字段提取；`a2aAgentCard` 留 null，原始 JSON 直接作为 `a2aAgentCardJson` 入库（避免 17 字段 record 反序列化）。

### 4.3 Agent Card 更新与失效

| 触发 | 路径 | 行为 |
|------|------|------|
| 重注册 | `POST /register` | upsert，DRAINING 保留 |
| 批量下线 | `DELETE /deregister/{tenantId}/{agentId}` | 删除该 pair 下所有实例（REQ-2026-006 语义泛化） |
| 单实例下线 | `DELETE /deregister/{tenantId}/{agentId}/{serviceId}` | 滚动发布单副本下线不影响其他副本 |
| 心跳过期 | `MvpHealthProbeScheduler` | ONLINE/DEGRADED 且 `last_heartbeat < stale` 被探活；长期 stale 的 ONLINE 降级为 DEGRADED |
| 优雅下线 | 运维置 `DRAINING` | discovery SQL `status IN ('ONLINE','DEGRADED')` 自动排除；重注册不复活 |

### 4.4 已知目标查询（`searchInstancesByAgentId`）

```
Gateway / Orchestrator       PgMvpDiscoveryServiceImpl          JdbcAgentRegistryRepository
   │                                │                                  │
   │── searchInstancesByAgentId ───►│                                  │
   │   (tenantId, agentId)          │                                  │
   │                                │── verifyTenant (可选交叉校验)    │
   │                                │── repository.listByAgentId ─────►│
   │                                │                                  │── BEGIN TX
   │                                │                                  │── set_config('app.tenant_id')
   │                                │                                  │── SELECT ... WHERE tenant_id
   │                                │                                  │   AND agent_id
   │                                │                                  │   AND status IN ('ONLINE','DEGRADED')
   │                                │                                  │   ORDER BY weight DESC, last_heartbeat DESC
   │                                │                                  │── COMMIT
   │                                │◄── List<RegistryRow> ────────────│
   │                                │── toDto: RouteHandleCodec.encode │
   │                                │   (tenantId, agentId, serviceId, │
   │                                │    routeKey, contractVersion)    │
   │                                │   → opaque v1: handle            │
   │◄── List<AgentCardDto> ─────────│                                  │
```

- **空结果**：返回空 List（`agent_not_found` 语义），不抛异常。
- **排序**：`weight DESC, last_heartbeat DESC`——调用方 naive pick-first 落在高权重、新鲜心跳实例。
- **DTO 不含物理地址**：`endpointUrl` / `routeKey` / `serviceId` 明文不进 DTO；只有 opaque `routeHandle`。

### 4.5 route handle 解析（`resolveRouteHandle`）

```
转发层                    PgMvpDiscoveryServiceImpl        RouteHandleCodec        Repository
   │                            │                                │                      │
   │── resolveRouteHandle ─────►│                                │                      │
   │   (handle, tenantId)       │                                │                      │
   │                            │── decode(handle) ─────────────►│                      │
   │                            │                                │── stripPrefix v1:    │
   │                            │                                │── base64 decode       │
   │                            │                                │── JSON parse 5 fields │
   │                            │◄── DecodedHandle ──────────────│                      │
   │                            │── decoded.tenantId != tenantId?                       │
   │                            │   yes ──► TenantIsolationViolationException (400)     │
   │                            │── verifyTenant                                        │
   │                            │── repository.findEndpoint ───────────────────────────►│
   │                            │   (tenantId, agentId, serviceId)                      │
   │                            │◄── Optional<EndpointEntry> ───────────────────────────│
   │                            │── empty? ──► NoSuchElementException (404 entry_not_found)
   │◄── RouteResolution ────────│                                                      │
   │   (endpointUrl, routeKey, contractVersion)                                         │
```

失败码映射见 §7。

### 4.6 健康探活状态机

| 当前状态 | 事件 | 下一状态 | 附加行为 |
|---------|------|---------|---------|
| ONLINE | 探活 2xx | ONLINE | `updateStatus(refreshHeartbeat=true)` |
| ONLINE | 探活 5xx / 超时 / 连接异常 | DEGRADED | `updateStatus(refreshHeartbeat=false)` |
| DEGRADED | 探活 2xx | ONLINE | 恢复（PR #389 #4） |
| DEGRADED | 探活 5xx / 超时 | DEGRADED | 心跳不刷新；discovery SQL 15s 可见性窗口最终过滤 |
| DRAINING | （任何） | DRAINING | 运维发起；discovery 排除；重注册不复活 |
| OFFLINE | （手动） | OFFLINE | 终态；需重注册回 ONLINE |
| ONLINE | `last_heartbeat` 长期 stale 且未被探活调度扫到 | DEGRADED | 调度器兜底降级 |

## 5. 配置模型（Physical View）

### 5.1 完整配置示例

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/agent_rdc}
    username: ${SPRING_DATASOURCE_USERNAME:agent_rdc}
    password: ${SPRING_DATASOURCE_PASSWORD:agent_rdc}
    driver-class-name: org.postgresql.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8092

# pull 模式注册（opt-in）
rdc:
  pull-registration:
    enabled: false
    runtimes: []
      # - base-url: http://localhost:8090
      #   tenant-id: tenant-A
      #   agent-id: demo-runtime-8090
      #   framework-type: JIUWEN
      #   card-path: /.well-known/agent-card.json
      #   route-key: /v1/query
      #   region: cn-east-1
      #   max-concurrency: 10
      #   weight: 100
      #   headers:
      #     Authorization: Bearer ${RUNTIME_TOKEN:placeholder}

# 健康探活调度
agent-bus:
  registry:
    mvp:
      probe-interval-ms: 5000
      probe-stale-before-ms: 5000
      probe-scan-limit: 200
      probe-connect-timeout-ms: 2000
      probe-read-timeout-ms: 2000
```

### 5.2 配置属性表

| 属性路径 | 类型 | 默认值 | 必填 | 说明 |
|---------|------|--------|------|------|
| `server.port` | int | 8092 | 否 | agent-rdc HTTP 端口；避开 8080 与其他模块冲突。 |
| `spring.datasource.url` | String | `jdbc:postgresql://localhost:5432/agent_rdc` | 是 | PG 连接串；生产用环境变量覆盖。 |
| `spring.flyway.enabled` | bool | true | 否 | 启动时跑 V2..V5 migration 建表。 |
| `rdc.pull-registration.enabled` | bool | false | 否 | pull 模式开关；关闭时不实例化 `PullRegistrationBootstrap`。 |
| `rdc.pull-registration.runtimes[]` | list | `[]` | 否 | pull 模式 runtime 列表；每项需 `base-url` / `tenant-id` / `agent-id` / `framework-type`。 |
| `agent-bus.registry.mvp.probe-interval-ms` | long | 5000 | 否 | 探活调度固定延迟。 |
| `agent-bus.registry.mvp.probe-stale-before-ms` | long | 5000 | 否 | 心跳过期阈值。 |
| `agent-bus.registry.mvp.probe-scan-limit` | int | 200 | 否 | 单次扫描上限。 |
| `agent-bus.registry.mvp.probe-connect-timeout-ms` | int | 2000 | 否 | 探活连接超时。 |
| `agent-bus.registry.mvp.probe-read-timeout-ms` | int | 2000 | 否 | 探活读超时。 |

## 6. 对外呈现 / 用户场景（Scenario View）

### 6.1 外部接口

| 端点 | 方法 | 说明 |
|------|------|------|
| `POST /api/registry/register` | HTTP POST | push 模式注册 / 重注册 |
| `DELETE /api/registry/deregister/{tenantId}/{agentId}` | HTTP DELETE | 批量下线该 pair 下所有实例 |
| `DELETE /api/registry/deregister/{tenantId}/{agentId}/{serviceId}` | HTTP DELETE | 单实例下线 |
| `GET /api/registry/instances/{tenantId}/{agentId}` | HTTP GET | 列举 ONLINE/DEGRADED 实例（携带 opaque routeHandle） |
| `POST /api/registry/route-handle/resolve` | HTTP POST | 转发层解析 routeHandle 为物理端点 |

请求 / 响应遵循 `traceparent`（W3C）/ `X-Trace-Id` header 透传，缺失时生成 UUID。

### 6.2 用户示例

#### 6.2.1 注册一个 Agent 能力

```bash
curl -s -X POST http://localhost:8092/api/registry/register \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: req-001" \
  -d '{
    "tenantId": "tenant-wealth-01",
    "agentId": "wealth-expert-01",
    "agentName": "理财专家智能体",
    "frameworkType": "JIUWEN",
    "routeKey": "wealth/v1",
    "contractVersion": "1",
    "capabilityVersion": "1.0.0",
    "endpointUrl": "http://192.168.1.50:8000",
    "maxConcurrency": 10,
    "weight": 100,
    "region": "cn-east-1"
  }'
# 预期：200 OK；serviceId 服务端派生为 192.168.1.50-8000
```

#### 6.2.2 查询已知 agent 的可路由实例

```bash
curl -s http://localhost:8092/api/registry/instances/tenant-wealth-01/wealth-expert-01 \
  -H "X-Trace-Id: req-002"
# 预期：
# [
#   {
#     "routeHandle": "v1:eyJ0ZW5hbnRJZCI6...==",
#     "health": "ONLINE",
#     "contractVersion": "1",
#     "capabilityVersion": "1.0.0",
#     "weight": 100,
#     "region": "cn-east-1",
#     "maxConcurrency": 10,
#     "agentName": "理财专家智能体",
#     "frameworkType": "JIUWEN"
#   }
# ]
# 注意：响应不含 endpointUrl / routeKey / serviceId 明文
```

#### 6.2.3 转发层解析 route handle

```bash
curl -s -X POST http://localhost:8092/api/registry/route-handle/resolve \
  -H "Content-Type: application/json" \
  -d '{"routeHandle": "v1:eyJ0ZW5hbnRJZCI6...==", "tenantId": "tenant-wealth-01"}'
# 预期：
# {"endpointUrl": "http://192.168.1.50:8000", "routeKey": "wealth/v1", "contractVersion": "1"}
```

### 6.3 E2E 流程

```
Agent 服务启动          registry-center              健康探活调度器        转发层（Orchestrator）
   │                        │                            │                       │
   │── POST /register ─────►│                            │                       │
   │                        │── upsert (ONLINE)          │                       │
   │◄── 200 ────────────────│                            │                       │
   │                        │                            │                       │
   │                        │◄── scanDueForProbe ────────│                       │
   │                        │    (5s 周期)               │                       │
   │◄── GET /health ────────────────────────────────────│                       │
   │── 200 ────────────────────────────────────────────►│                       │
   │                        │◄── updateStatus(ONLINE) ───│                       │
   │                        │                            │                       │
   │                        │◄── GET /instances ─────────────────────────────────│
   │                        │── List<AgentCardDto> ─────────────────────────────►│
   │                        │                            │                       │
   │                        │◄── POST /route-handle/resolve ─────────────────────│
   │                        │── RouteResolution ────────────────────────────────►│
   │                        │                            │                       │
   │◄── 实际业务调用 ─────────────────────────────────────────────────────────────│
```

## 7. 错误处理（Process View）

| 错误场景 | 触发条件 | HTTP | error code | 行为 |
|---------|---------|------|-----------|------|
| registry key 缺失 | body 缺 `tenantId` / `agentId` | 400 | `invalid_request` | fail-fast |
| `endpointUrl` 畸形 | 无 host / 不可解析 | 400 | `invalid_request` | `ServiceIdCodec.derive` 抛 IAE |
| `frameworkType` 为 null | push body 或 pull config 缺失 | 400 | `invalid_request` | |
| route handle 畸形 | 缺 `v1:` 前缀 / base64 损坏 / JSON 缺字段 / 旧 4 字段格式 | 400 | `malformed_handle` | baseline-breaking，不兼容旧格式 |
| 跨 tenant 解析 | `resolveRouteHandle` 调用方 tenant 与 handle 内 tenant 不一致 | 400 | `tenant_isolation_violation` | |
| TenantContext 交叉校验失败 | 后台调度路径 bound tenant 与参数不一致 | 400 | `tenant_isolation_violation` | |
| entry 不存在 | handle 指向的 `(tenantId, agentId, serviceId)` 无记录 | 404 | `entry_not_found` | |
| 已知目标无实例 | `searchInstancesByAgentId` 无 ONLINE/DEGRADED 行 | 200 | （空 List） | 空数组语义 = `agent_not_found` |

scope §10 失败语义映射：

| scope 失败语义 | MVP 实现 |
|---------------|---------|
| `NO_CAPABILITY_MATCH` | 阶段二（能力维度查询未实现） |
| `NO_AVAILABLE_AGENT` | `searchInstancesByAgentId` 返回空 List |
| `CONTRACT_VERSION_MISMATCH` | DTO 返回 `contractVersion`，调用方自行判断；MVP 不在 discovery 层强制过滤 |
| `TENANT_SCOPE_DENIED` | `TenantIsolationViolationException` → 400 |
| `AGENT_CARD_INVALID` | 400 `invalid_request` |
| `ROUTE_CONFIDENCE_LOW` | 阶段二（推荐分未实现） |

## 8. 限制与待补

| 限制 | 影响范围 | 临时方案 / 阶段规划 |
|------|---------|-------------------|
| 仅支持按 `agentId` 查询，不支持按 `capability_type` / `tags` / `domain` / `query_text` 查询 | 意图识别动态能力匹配场景无法直接使用 registry | 阶段二引入 pgvector + `searchByCapability` SPI 方法 |
| 无推荐首选 / 分数 / 证据 | 自动调度场景需调用方自排 | 阶段二 |
| `supports_streaming` / `supports_hitl` / `requires_idempotency_key` 等执行约束字段未实现 | 执行约束查询无法下推到 registry | 阶段二随 Agent Card 字段扩展 |
| pull 注册无定时刷新 | agent 卡片变更需重启或重触发 | bootstrap-only 设计；刷新需求由阶段二定时拉取或 push 模式覆盖 |
| route handle 旧 4 字段格式不兼容 | REQ-2026-006 升级后旧 handle 立即失效 | handle 生命周期短（一个探活周期），迁移后调用方重新 `GET /instances` |
| `contractVersion` 不匹配不在 discovery 层强制过滤 | 调用方可能拿到版本不兼容候选 | DTO 携带版本字段，调用方自行判断；阶段二可在查询输入加 `contractVersion` 过滤 |
| 单 PG 实例，无 Consul / pgvector | 检索能力与可用性受限 | 阶段二演进，见 [registry-discovery-runtime-design.cn.md](./registry-discovery-runtime-design.cn.md) |

## 9. 与 scope 文档的对齐矩阵

| scope 要求 | L2 落地 | 备注 |
|-----------|---------|------|
| §2 Agent Card 注册 MUST | §4.1 / §4.2 | push + pull 双模式 |
| §2 Agent Card 校验 MUST | §4.1.2 / §7 | registry key + frameworkType + endpointUrl 派生 |
| §2 更新与失效 MUST | §4.3 | upsert + deregister(pair/triple) + DRAINING 保留 |
| §2 租户与调用方边界 MUST | §2.3.3 / §4.4 / §7 | RLS + 应用层 WHERE + TenantContext 交叉校验 |
| §2 Agent Card 查询 MUST | §4.4 | MVP 仅 agentId 维度；能力维度阶段二 |
| §2 可用性过滤 MUST | §4.4 / §4.6 | status + 版本字段在 DTO |
| §2 推荐首选 SHOULD | §8 | 阶段二 |
| §2 候选与证据保留 SHOULD | §8 | 阶段二 |
| §2 opaque route handle MUST | §4.4 / §4.5 | v1: 5 字段 base64 |
| §2 无能力结果 MUST | §7 | 空 List / 404 |
| §2 可查询扩展元数据 SHOULD | §2.3.2 | A2A JSONB 保留，索引阶段二 |
| §3 Agent Card 最小字段 | §2.3.2 | 覆盖矩阵见 §2.3.2 |
| §4 查询输入与输出 | §2.3.1 / §2.3.2 | MVP 子集；完整查询输入阶段二 |
| §6 意图识别场景协作边界 | §2.2 / §8 | 阶段二；MVP 不实现动态能力匹配 |
| §10 失败语义 | §7 | 部分实现，部分阶段二 |
| §11 开发设计待细化事项 | §8 | 对齐——均标记为阶段二 |
