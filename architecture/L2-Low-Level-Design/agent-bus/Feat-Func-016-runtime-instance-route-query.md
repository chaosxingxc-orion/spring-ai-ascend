---
level: L2
module: agent-bus
feature: FEAT-016
feature_name: 运行时实例路由查询
status: draft
updated: 2026-07-10
authority:
  - ../../../version-scope/FEAT-016-runtime-instance-route-query.md
  - ../L0-Top-Level-Design/boundaries.md
  - ../L0-Top-Level-Design/glossary.md
  - ../L1-High-Level-Design/agent-bus/logical.md
  - ../L1-High-Level-Design/agent-bus/process.md
  - ../L1-High-Level-Design/agent-bus/scenarios.md
  - ./registry-discovery-runtime-design.cn.md
  - ./Feat-Func-015-agent-card-registration-and-discovery.md
implementation_ref:
  - agent-solution/common/registry-discovery-center
covers_contract: ICD-Agent-Registry-Discovery
---

# 运行时实例路由查询 — L2 低层设计

> 本文档把 `version-scope/FEAT-016-runtime-instance-route-query.md` 的事实要求落地为 `agent-bus` registry-discovery-center 单元（及其与 gateway / `agent-runtime` 协作边界）的低层设计。MVP 实现基线参考 `agent-solution/common/registry-discovery-center`，运行态技术基线见 [registry-discovery-runtime-design.cn.md](./registry-discovery-runtime-design.cn.md)，Agent Card 注册侧设计见 [Feat-Func-015](./Feat-Func-015-agent-card-registration-and-discovery.md)。本文聚焦 FEAT-016 特性视角：已知目标路由查询语义、视图分层与脱敏、可用性状态、中心不可用降级、反枚举保护，以及 registry 单元与 gateway / `agent-runtime` 的职责边界。

## 1. 概述

### 1.1 特性定位

- **是什么**：registry-discovery-center 单元支持运行时实例路由查询——当 gateway 或 `agent-runtime` 已知目标 `agentId` / `serviceId` / `capability` 时，查询该目标当前由哪些运行时实例承载，并返回可用于通信分发的 opaque 路由引用。
- **解决什么**：before——调用方把 `agentId` 误当物理地址，或把 Agent Card 发现结果当路由表；after——`agentId` 是对象标识，实际通信由 gateway / `agent-runtime` 经路由查询 + route handle 解析完成，agent / client 不感知物理 endpoint / topic / 数据库 key / registry 内部结构。
- **与 Feat-015 的边界**：Feat-015 回答「有哪些智能体、服务和能力存在，它们会做什么」；FEAT-016 回答「某个已知目标当前由哪些运行时实例可路由承载」。FEAT-016 不提供面向 agent / client 的候选发现、能力搜索或语义画像检索。
- **适用场景**：gateway 直连路由、`agent-runtime` 代理 agent 发起下游任务、多实例承载同一 agent / service 的候选集合表达。

### 1.2 核心设计原则

| 原则 | 理由 |
|------|------|
| **已知目标查询，不是候选发现** | scope §1；避免 FEAT-016 越界成为 Agent Card 语义发现，后者属 Feat-015。 |
| **`agentId` 是对象标识，不是物理地址** | scope §5.1.1；agent 可以记住 `agentId`，但通信必须经 gateway / `agent-runtime` 完成路由。 |
| **多实例候选是正常形态** | scope §2 / §5.1.1；`serviceId` 是逻辑服务标识，可被多实例共享；`instanceId`（server-derived `host-port`）区分具体实例；registry PK `(tenant_id, agent_id, service_id, instance_id)` 支持同 `agentId` / 同 `serviceId` 多实例。 |
| **route handle opaque，对 agent / client 不透明** | scope §2 / §5.1.4；物理 endpoint / topic / 实例地址只在转发层经 `resolveRouteHandle` 恢复。 |
| **视图分层：系统路由视图 vs 路由可用性投影** | scope §5.1.4；gateway / `agent-runtime` 看系统路由视图，agent / client 只看脱敏投影。 |
| **租户强制、禁止跨 tenant fallback、反枚举** | scope §2 / §5.1.7；无权限 / 跨租户 / 不可见目标查询不返回「存在但不可访问」暗示。 |
| **registry 不直连 agent** | scope §5.1.2；agent 只能通过 `agent-runtime` 注入工具表达查询或目标选择。 |

### 1.3 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| 已知目标路由查询（by agentId） | 按 `tenantId + agentId` 列举可路由实例 | `searchInstancesByAgentId` | ✅ MVP |
| 已知目标路由查询（by serviceId / capability） | 按 `serviceId` / `capability` 查询 | （scope §2） | ⬜ 阶段一 |
| route handle 解析 | opaque handle → 物理端点（仅转发层） | `resolveRouteHandle` | ✅ MVP |
| 多实例候选表达 | 同 `agentId` / 同 `serviceId` 多实例各自独立 handle | `List<AgentCardDto>` + `(tenant_id, agent_id, service_id, instance_id)` PK | ✅ MVP |
| 系统路由视图 | gateway / `agent-runtime` 看到的完整路由信息 | `AgentCardDto` | ✅ MVP |
| 路由可用性投影 | agent / client 看到的脱敏视图 | （投影层在 `agent-runtime`） | ⬜ 不在 registry 单元 |
| 可用性状态语义 | 可用 / 可能不可用 / 有限可用 / 暂不可用 / 版本不匹配 | `status` + 版本字段 | ⚠️ MVP 子集 |
| 中心短时不可用降级 | 本地已知路由信息维持调用 | （本地缓存） | ⬜ 阶段一 |
| 反枚举保护 | 不可见目标不返回存在性暗示 | 空结果语义 | ✅ MVP |
| 租户隔离 | 强制 `tenantId`，禁止跨 tenant | RLS + 应用层 WHERE + TenantContext | ✅ MVP |
| 版本约束 | `contractVersion` / `capabilityVersion` 可用性 | DTO 字段 | ✅ MVP（调用方判断） |
| Task 状态隔离 | 路由结果不携带 Task state | DTO 不含 Task 字段 | ✅ MVP |

## 2. 功能规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| 已知目标路由查询（agentId） | ✅ | `searchInstancesByAgentId(tenantId, agentId) → List<AgentCardDto>`，返回 ONLINE/DEGRADED 实例。 |
| 已知目标路由查询（serviceId / capability） | ⬜ 阶段一 | MVP 仅支持 agentId 维度；serviceId / capability 维度阶段一引入（capability 字段已在 REQ-2026-004 移除，阶段一重建）。 |
| 统一查询语义 | ✅ | gateway 直连与 `agent-runtime` 代理共享 `AgentDiscoveryService` 同一 Service 接口；差异在调用方 / 权限上下文 / 结果呈现，不在 registry 接口。 |
| 多实例候选 | ✅ | `List<AgentCardDto>` 表达候选集合；每实例独立 `routeHandle`。 |
| 运行时实例标识 | ✅ | `serviceId`（逻辑服务标识，注册方传入，多实例共享）+ `instanceId`（server-derived `host-port`，区分具体实例）；`instanceId` 进 route handle，不进 DTO 明文。 |
| 路由引用 | ✅ | opaque `routeHandle`（`v2:` + base64 JSON 6 字段）；对 agent / client 不透明。 |
| 路由可用性投影 | ⬜（不在 registry） | 投影层属 `agent-runtime` 对 agent 表面；registry 只提供系统路由视图。 |
| 租户隔离 | ✅ | 强制 `tenantId`；RLS + 应用层 WHERE；跨 tenant 抛 `TenantIsolationViolationException`。 |
| 版本约束 | ✅ | DTO 携带 `contractVersion` / `capabilityVersion`；MVP 不在 discovery 层强制过滤，调用方判断。 |
| 健康与可用性 | ⚠️ 阶段一 | MVP 提供 `ONLINE` / `DEGRADED` / `DRAINING` / `OFFLINE` 四态；scope §5.1.5 的「可能不可用 / 有限可用」细分状态阶段一补齐。 |
| 中心短时不可用降级 | ⬜ 阶段一 | 本地路由缓存 + 有效性窗口——阶段一；MVP 中心不可用时显式失败。 |
| 反枚举保护 | ✅ | 无权限 / 跨租户 / 不可见目标返回空结果或显式失败，不返回「存在但不可访问」暗示。 |
| Task 状态隔离 | ✅ | DTO 不含 Task execution state / hierarchy / 进度 / 编排状态。 |
| 物理细节透明 | ✅ | DTO 不含 endpoint / topic / 实例地址 / DB key / 探活实现；agent / client 不依赖 Consul / PG / PGVector / broker 等物理实现细节。 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| Agent Card 候选发现 / 能力搜索 / 语义画像 | 属 Feat-015 | 见 [Feat-Func-015](./Feat-Func-015-agent-card-registration-and-discovery.md) |
| event-bus 查询 registry-discovery-center | scope §5.2；当前版本不纳入 | event-bus 路径按 FEAT-013 / FEAT-014 事件转发语义 |
| agent 直连 registry | scope §5.1.2 / §5.2 | agent 经 `agent-runtime` 注入工具表达查询 |
| client 获得物理地址 | scope §5.2 | client 只看路由可用性投影（由 `agent-runtime` / gateway 呈现） |
| 复杂全局调度（资源水位 / 容量 / 全局限流 / 跨区域成本） | scope §5.2 | 后续特性 |
| 具体缓存结构 / 淘汰算法 / 本地路由策略 | scope §5.2 | 阶段一降级能力一并设计 |
| 跨租户 fallback | scope §5.2 | 永不承诺 |
| Task 状态查询 | scope §5.2 | Task 属 `agent-runtime` |
| token / payload 通道 | scope §5.2 | 由转发层 / event-bus 承载 |
| 无界离线工作 | scope §5.2 | 短时自治只在受控有效性窗口内 |

### 2.3 接口契约（Logical View）

#### 2.3.1 Service 接口声明

registry-discovery-center 对外暴露的路由查询 Service 接口（`com.openjiuwen.rdc.service`）：

```java
/**
 * agent-bus 拥有的运行时路由索引查询入口（FEAT-016）。
 *
 * 上层 gateway 直连路由与 agent-runtime 代理查询共享同一 Service 接口；差异只在
 * 调用方、权限上下文与结果呈现方式（系统路由视图 vs 路由可用性投影）。
 *
 * 持久化形态（MVP 单 PG，阶段二 Consul + pgvector）对调用方不可见。
 */
public interface AgentDiscoveryService {

    /**
     * 已知目标路由查询（by agentId）：列出 (tenantId, agentId) 下所有
     * ONLINE/DEGRADED 运行时实例候选。每实例携带独立 opaque routeHandle。
     *
     * FEAT-016 §2「已知目标路由查询」「多实例候选」「运行时实例标识」MUST 项
     * 由本方法承载。gateway 直连与 agent-runtime 代理均调用此方法。
     *
     * 状态过滤：只返回 ONLINE / DEGRADED；DRAINING / OFFLINE 排除
     * （treated as not-discoverable from the caller's perspective）。
     * 排序：weight DESC, last_heartbeat DESC——调用方 naive pick-first
     * 落在高权重、新鲜心跳实例。
     *
     * 反枚举（FEAT-016 §2「反枚举保护」）：无权限 / 跨租户 / 不可见目标
     * 返回空 List，不返回「存在但不可访问」暗示。跨租户查询抛
     * TenantIsolationViolationException。
     *
     * @param tenantId registry key 强制维度；缺失或与 TenantContext 不一致
     *                 抛 TenantIsolationViolationException
     * @param agentId  已知目标 agent 标识
     * @return 候选列表，无匹配返回空 List（永不 null）
     */
    List<AgentCardDto> searchInstancesByAgentId(String tenantId, String agentId);

    /**
     * 解析 opaque routeHandle 为转发层可用的 RouteResolution。
     *
     * FEAT-016 §2「路由引用」MUST 项：route handle 对 agent / client 不透明，
     * 只有 gateway / agent-runtime 的转发层调用本方法恢复物理端点。
     *
     * @throws IllegalArgumentException           handle 畸形（含旧 4 字段及 `v1:` 5 字段格式）
     * @throws TenantIsolationViolationException  调用方 tenant 与 handle 内 tenant 不一致
     * @throws NoSuchElementException              handle 指向的 entry 不存在
     */
    RouteResolution resolveRouteHandle(String routeHandle, String tenantId);
}
```

> **阶段一扩展**：`searchByServiceId(tenantId, serviceId)` 与 `searchByCapability(...)` 在阶段一随 `capability` 字段重建一并引入；当前不预先钉死方法签名。

#### 2.3.2 数据类型

##### AgentCardDto（系统路由视图——gateway / agent-runtime 可见）

| 字段 | 可空 | scope §5.1.4 对应 | 含义 |
|------|------|------------------|------|
| `routeHandle` | 否 | 路由引用 | opaque handle（`v2:` 6 字段），转发层经 `resolveRouteHandle` 解析 |
| `serviceId` | 否 | 运行时实例标识 | 逻辑服务标识，多实例共享；agent / client 投影层可见 |
| `health` | 否 | 可用性状态 | `ONLINE` / `DEGRADED`（DRAINING / OFFLINE 已在 SQL 过滤） |
| `contractVersion` | 否 | 版本约束 | 调用方判断兼容性 |
| `capabilityVersion` | 否 | 版本约束 | 调用方判断兼容性 |
| `weight` | 否 | selectionHint | 调用方加权负载均衡 |
| `region` | 否 | selectionHint | 区域亲和 |
| `maxConcurrency` | 否 | selectionHint | 调用方按容量加权 |
| `agentName` | 是 | 业务定义 | 展示用 |
| `frameworkType` | 是 | 业务定义 | `JIUWEN` / `AGENTSCOPE` / `VERSATILE` / `PROXY_SERVICE` |

> **系统路由视图不可见内容**（scope §5.1.4）：Task execution state、agent 内部编排状态、`instanceId` 明文、`endpointUrl` / `routeKey` 明文。DTO 设计上不含任何 Task 字段；`instanceId` 仅编码在 route handle 内，转发层解析时恢复。
>
> **路由可用性投影不可见内容**（scope §5.1.4）：候选发现结果、Agent Card 语义画像、物理 endpoint、topic、实例地址、数据库 key、探活实现、`instanceId` 明文、`routeKey` 明文。MVP 的 `AgentCardDto` 已满足：不含 `endpointUrl` / `routeKey` / `instanceId` 明文；`serviceId` 作为逻辑服务标识在投影层可见（FEAT-016 §5.1.4 "可选 serviceId"）。投影层（剥离 `weight` / `maxConcurrency` / `frameworkType` 等，只留 `agentId` / 能力 / 可选 `serviceId` / 简化健康 / 版本可用性）在 `agent-runtime` 对 agent 表面实现，不在 registry 单元。

##### RouteResolution（转发层专用——agent / client 永不可见）

| 字段 | 含义 |
|------|------|
| `instanceId` | 运行时实例标识（host-port），从 route handle 恢复 |
| `endpointUrl` | 物理端点——仅 gateway / `agent-runtime` 转发层可见 |
| `routeKey` | 逻辑路由键 |
| `contractVersion` | 注册时钉定的契约版本 |

#### 2.3.3 行为承诺

- **必须**：`searchInstancesByAgentId` 返回的 DTO 不携带 `endpointUrl` / `routeKey` / `instanceId` 明文——只有 opaque `routeHandle`；`serviceId` 作为逻辑服务标识可在 DTO 中返回。
- **必须**：route handle 编码为 `v2:` + base64(JSON{tenantId, agentId, serviceId, instanceId, routeKey, contractVersion}) 6 字段；旧 `v1:` 5 字段格式不兼容。
- **必须**：`resolveRouteHandle` 仅由转发层调用；`RouteHandleCodec` 不离开 `registry.runtime.discovery` 包。
- **必须**：查询参数缺 `tenantId` 时 fail-fast（400 `invalid_request`），不进默认租户、不跨租户搜索。
- **必须**：跨 tenant 查询 / 缓存复用 / 降级 / fallback 全部禁止——抛 `TenantIsolationViolationException`（400 `tenant_isolation_violation`）。
- **必须**：无权限 / 不可见目标返回空结果，不返回「存在但不可访问」暗示（反枚举）。
- **必须**：所有写操作（含 discovery 读路径的 RLS 设置）在事务内 `set_config('app.tenant_id', :tenantId, true)`。
- **必须**：路由结果不携带 Task execution state / hierarchy / 执行进度 / agent 内部编排状态。
- **禁止**：agent 直接调用 registry-discovery-center 原始接口——必须经 `agent-runtime` 注入工具或代理表面。
- **禁止**：向 client 暴露 endpoint / topic / 运行时实例地址 / 数据库 key / 探活实现细节。
- **允许**：MVP 不实现中心不可用降级——中心不可用且无本地信息时显式失败（`discovery_unavailable` 语义由调用方包装）。
- **允许**：MVP 不实现 `serviceId` / `capability` 维度查询——当前仅 `agentId` 维度。

## 3. 模块结构（Development View）

### 3.1 registry-discovery-center 单元在 agent-bus 中的位置

```
agent-bus 逻辑域
├── gateway（逻辑子模块）           ── 直连路由时调用 AgentDiscoveryService
├── event-bus（逻辑子模块）          ── 不查询 registry（FEAT-016 §5.2 排除）
└── registry-discovery-center（单元）── 本特性核心
      │
      ├── controller/         ── MVC Controller 层，HTTP 入口
      ├── service/            ── 业务逻辑层（AgentDiscoveryService / RouteHandleCodec）
      ├── repository/         ── 数据访问层（唯一 JDBC 出口）
      ├── model/              ── 数据模型层（AgentCardDto / RouteResolution / Exception）
      ├── health/             ── 健康探活调度
      └── tenant/             ── 后台调度租户绑定

外部协作模块（不属于 agent-bus 逻辑域）
├── agent-runtime ── 代理 agent 调用 AgentDiscoveryService；承载路由可用性投影层
└── agent-core    ── 实现的 agent 经 agent-runtime 注入工具表达查询
```

### 3.2 调用方与 registry 的边界

```
                ┌─────────────────────────────────────────────────┐
                │           agent-bus 逻辑域                       │
                │                                                 │
  client ──►   gateway ──────────────────────────┐               │
                │                                │               │
                │                          registry-discovery-    │
                │                           center               │
                │                          (AgentDiscovery       │
                │                           Service)              │
                │                                ▲               │
                └────────────────────────────────┼───────────────┘
                                                 │
                            ┌────────────────────┴────────────────┐
                            │      agent-runtime（外部协作）        │
                            │                                      │
  agent ──►  agent-runtime ──┤  注入工具 ──►  agent 表达已知目标     │
            （代理查询）      │  代理调用 ──►  AgentDiscoveryService │
                            │  投影层   ──►  路由可用性投影 → agent │
                            │  转发层   ──►  resolveRouteHandle     │
                            └──────────────────────────────────────┘
```

- **gateway 直连路径**：client → gateway → `AgentDiscoveryService.searchInstancesByAgentId` → `resolveRouteHandle`（转发层）→ agent。
- **agent-runtime 代理路径**：agent → `agent-runtime` 注入工具 → `AgentDiscoveryService.searchInstancesByAgentId` → 投影层脱敏 → agent 看到「可用 / 可能不可用 / 版本不匹配」→ agent 决策 → `agent-runtime` 转发层 `resolveRouteHandle` → 远端 agent。
- **统一查询语义**：两条路径共享 `AgentDiscoveryService` 同一 Service 接口；差异在调用方、权限上下文、结果呈现（系统路由视图 vs 投影）。

## 4. 核心设计（Logical + Process View）

### 4.1 gateway 直连路由查询

```
client                    gateway（agent-bus）          registry-discovery-center
   │                            │                                │
   │── 调用已知 agentId ───────►│                                │
   │                            │── searchInstancesByAgentId ───►│
   │                            │   (tenantId, agentId)          │
   │                            │                                │── RLS set_config
   │                            │                                │── SELECT ... status IN
   │                            │                                │   ('ONLINE','DEGRADED')
   │                            │                                │   ORDER BY weight DESC,
   │                            │                                │   last_heartbeat DESC
   │                            │◄── List<AgentCardDto> ─────────│
   │                            │                                │
   │                            │── 选择实例（pick-first / 加权）  │
   │                            │── resolveRouteHandle ─────────►│
   │                            │   (handle, tenantId)           │
   │                            │◄── RouteResolution ────────────│
   │                            │    (endpointUrl, routeKey,     │
   │                            │     contractVersion)           │
   │                            │                                │
   │                            │── 转发层投递到 endpointUrl      │
   │◄── 同步结果 / 进度 ────────│                                │
```

- **gateway 对 client**：不暴露物理 endpoint / topic / 实例地址 / registry 内部结构。
- **无可用路由**：空 List → gateway 返回可编程失败（`no_route_candidates`），不猜测物理目标。
- **中心不可用**：MVP 显式失败；阶段一引入本地缓存后返回 `degraded / cached route`。

### 4.2 agent-runtime 代理查询

```
agent              agent-runtime                     registry-discovery-center
  │                     │                                    │
  │── 工具调用：          │                                    │
  │   表达已知 agentId ──►│                                    │
  │   或下游任务目标      │                                    │
  │                     │── searchInstancesByAgentId ───────►│
  │                     │   (tenantId, agentId)              │
  │                     │◄── List<AgentCardDto> ─────────────│
  │                     │                                    │
  │                     │── 投影层脱敏：                       │
  │                     │   剥离 weight / maxConcurrency /    │
  │                     │   frameworkType / routeHandle；     │
  │                     │   保留 agentId / 能力 / 简化健康 /  │
  │                     │   版本可用性                         │
  │                     │                                    │
  │◄── 路由可用性投影 ────│                                    │
  │   （可用 / 可能不可用 /                                     │
  │    版本不匹配）                                             │
  │                     │                                    │
  │── 决策：选择目标 ───►│                                    │
  │   （用 agentId 表达）│                                    │
  │                     │── 转发层 resolveRouteHandle ───────►│
  │                     │◄── RouteResolution ─────────────────│
  │                     │── 转发层投递到 endpointUrl           │
  │◄── 任务结果 ─────────│                                    │
```

- **agent 不直连 registry**：所有查询经 `agent-runtime` 注入工具。
- **投影层在 `agent-runtime`**：registry 只提供系统路由视图（`AgentCardDto`）；投影层把视图脱敏为 agent 可见的可用性表达。
- **agent 用 `agentId` 表达目标**：`agentId` 是对象标识，不是物理地址；通信分发由 `agent-runtime` 完成。
- **agent 看到的状态**：可用 / 可能不可用 / 有限可用 / 暂不可用 / 版本不匹配（scope §5.1.5）。MVP 映射：`ONLINE` → 可用；`DEGRADED` → 可能不可用；版本不匹配由 `agent-runtime` 比对 `contractVersion` 判断。

### 4.3 多实例候选表达

```
registry-discovery-center
   │
   │  表：agent_registry_mvp
   │  PK: (tenant_id, agent_id, service_id, instance_id)
   │
   │  tenant-A / wealth-expert-01 / wealth-svc / 10.0.0.1-8080   ONLINE   weight=100
   │  tenant-A / wealth-expert-01 / wealth-svc / 10.0.0.2-8080   ONLINE   weight=100
   │  tenant-A / wealth-expert-01 / wealth-svc / 10.0.0.3-8080   DEGRADED weight=80
   │
   │  searchInstancesByAgentId("tenant-A", "wealth-expert-01")
   │   → List<
   │       AgentCardDto{routeHandle=v2:...10.0.0.1..., serviceId=wealth-svc, health=ONLINE, weight=100, ...},
   │       AgentCardDto{routeHandle=v2:...10.0.0.2..., serviceId=wealth-svc, health=ONLINE, weight=100, ...},
   │       AgentCardDto{routeHandle=v2:...10.0.0.3..., serviceId=wealth-svc, health=DEGRADED, weight=80, ...}
   │     >
   │  排序：weight DESC, last_heartbeat DESC
```

- **同 `agentId` 多实例**：正常形态；实现不要求 `agentId` 在实例维度唯一。
- **同 `serviceId` 多实例**：正常形态；`serviceId` 是逻辑服务标识，多实例共享（FEAT-016 §2 多实例候选 MUST）。
- **`instanceId` 区分实例**：server-derived `host-port`，进 route handle，不进 DTO 明文。
- **每实例独立 handle**：转发层解析具体实例，支持滚动发布单副本上下线。

### 4.4 可用性状态语义

scope §5.1.5 定义五种脱敏状态。MVP 映射：

| scope 脱敏状态 | MVP 实现 | 行为 |
|---------------|---------|------|
| 可用 | `ONLINE` | 可作为新调用候选；discovery SQL 返回 |
| 可能不可用 | `DEGRADED` | 目标存在但健康或新鲜度有风险；discovery SQL 仍返回，调用方按策略降级或重试 |
| 有限可用 | （未实现） | MVP 无此细分；DRAINING 在 SQL 层排除 |
| 暂不可用 | `OFFLINE` / `DRAINING` | discovery SQL `status IN ('ONLINE','DEGRADED')` 排除——不作为候选返回 |
| 版本不匹配 | DTO 携带 `contractVersion` / `capabilityVersion` | 调用方比对；MVP 不在 discovery 层强制过滤，阶段一可在查询输入加版本约束 |

> **状态机**：`ONLINE` ↔ `DEGRADED`（探活驱动）；`ONLINE`/`DEGRADED` → `DRAINING`（运维发起）；`DRAINING` → `OFFLINE`（终态）。重注册时 `DRAINING` 保留（PR #389 #7），其他状态重置为 `ONLINE`。

### 4.5 中心不可用与恢复

| 场景 | MVP 行为 | 阶段一目标 |
|------|---------|-----------|
| 中心短时不可用 + 本地信息仍有效 | 显式失败（调用方包装 `discovery_unavailable`） | 调用方本地缓存 + 有效性窗口内维持已知目标调用，返回 `degraded / cached route` |
| 中心不可用 + 本地信息缺失 | 显式失败 `discovery_unavailable / no_route` | 同上——无本地信息时仍显式失败 |
| 中心恢复 | 后续查询回到正常路径 | 重新获得权威路由结果 |

- **降级窗口**：阶段一设计，受控有效性约束（如 handle TTL = 一个探活周期 5s），不承诺长期离线工作。
- **不跨租户 fallback**：降级只在同 tenant 的本地缓存内成立。

### 4.6 反枚举保护

| 查询场景 | 行为 | 信息泄漏 |
|---------|------|---------|
| 无权限 caller 查询目标 | 空结果或显式权限失败 | 不返回「存在但不可访问」 |
| 跨 tenant 查询 | `TenantIsolationViolationException`（400） | 不泄漏其他租户存在性 |
| 不可见目标 | 空结果 | 与「目标不存在」不可区分 |
| 目标不存在 | 空 List | 与「不可见目标」不可区分 |

- **实现机制**：discovery SQL `WHERE tenant_id = :tenantId AND agent_id = :agentId AND status IN ('ONLINE','DEGRADED')`；RLS `tenant_id = current_setting('app.tenant_id', true)` 作为防御深度。无匹配一律空 List，不在响应中区分「不存在」与「不可见」。

## 5. 配置模型（Physical View）

FEAT-016 复用 [Feat-Func-015 §5](./Feat-Func-015-agent-card-registration-and-discovery.md) 的配置模型，不引入独立配置项。关键配置对 FEAT-016 的影响：

| 属性路径 | FEAT-016 影响 |
|---------|--------------|
| `agent-bus.registry.mvp.probe-interval-ms`（默认 5000） | 决定 `DEGRADED` 状态新鲜度，间接影响「可能不可用」投影窗口 |
| `agent-bus.registry.mvp.probe-stale-before-ms`（默认 5000） | 心跳过期阈值；超过则 discovery SQL 15s 可见性窗口最终过滤 |
| `agent-bus.registry.mvp.probe-connect-timeout-ms` / `probe-read-timeout-ms`（默认 2000） | 探活超时；影响 `DEGRADED` 判定速度 |

> 阶段一降级能力引入后，将新增 `agent-bus.registry.mvp.local-cache.*` 配置组（TTL / 最大条目 / 淘汰策略），届时在本文 §5 扩展。

## 6. 对外呈现 / 用户场景（Scenario View）

### 6.1 外部接口

registry-discovery-center 对 FEAT-016 暴露的接口（与 Feat-015 共享）：

| 端点 / API | 方法 | FEAT-016 用途 |
|-----------|------|--------------|
| `GET /api/registry/instances/{tenantId}/{agentId}` | HTTP GET | 已知目标路由查询（gateway 直连 / agent-runtime 代理共用） |
| `POST /api/registry/route-handle/resolve` | HTTP POST | 转发层解析 route handle 为物理端点 |

> `POST /register` / `DELETE /deregister/...` 属 Feat-015 注册侧，FEAT-016 不直接使用，但注册产出的 `status` / `serviceId` / `routeKey` / `contractVersion` 是路由查询的输入事实。

### 6.2 用户示例

#### 6.2.1 gateway 直连查询已知 agent

```bash
# 前置：wealth-expert-01 已注册，至少一个实例 ONLINE
curl -s http://localhost:8092/api/registry/instances/tenant-wealth-01/wealth-expert-01 \
  -H "X-Trace-Id: gw-req-001"
# 预期：JSON 数组，每项含 opaque routeHandle；不含 endpointUrl / routeKey / instanceId 明文；serviceId 作为逻辑标识可见
```

#### 6.2.2 转发层解析 route handle

```bash
# 前置：上一步返回的 routeHandle
curl -s -X POST http://localhost:8092/api/registry/route-handle/resolve \
  -H "Content-Type: application/json" \
  -d '{"routeHandle": "v2:eyJ0ZW5hbnRJZCI6...==", "tenantId": "tenant-wealth-01"}'
# 预期：{"instanceId": "192.168.1.50-8000", "endpointUrl": "http://192.168.1.50:8000", "routeKey": "wealth/v1", "contractVersion": "1"}
# 该结果只对 gateway / agent-runtime 转发层可见，不返回给 client / agent
```

#### 6.2.3 无可用路由（反枚举）

```bash
# 目标不存在 / 不可见 / 全部 OFFLINE
curl -s http://localhost:8092/api/registry/instances/tenant-wealth-01/unknown-agent \
  -H "X-Trace-Id: gw-req-002"
# 预期：[]   （空数组——与「目标不存在」「不可见」不可区分）
```

#### 6.2.4 跨租户查询被拒

```bash
curl -s -X POST http://localhost:8092/api/registry/route-handle/resolve \
  -H "Content-Type: application/json" \
  -d '{"routeHandle": "v2:eyJ0ZW5hbnRJZCI6InRlbmFudC1BIn0==", "tenantId": "tenant-B"}'
# 预期：400 {"error": "tenant_isolation_violation", "message": "..."}
```

### 6.3 E2E 流程

#### 6.3.1 gateway 直连调用已知 agent

```
client                   gateway                  registry-center           agent 服务
  │                        │                          │                        │
  │── 调用 wealth-expert ─►│                          │                        │
  │   (携带 agentId)        │                          │                        │
  │                        │── GET /instances ───────►│                        │
  │                        │◄── List<AgentCardDto> ───│                        │
  │                        │                          │                        │
  │                        │── POST /resolve ────────►│                        │
  │                        │◄── RouteResolution ──────│                        │
  │                        │                          │                        │
  │                        │── 转发到 endpointUrl ────────────────────────────►│
  │                        │◄── 业务结果 ──────────────────────────────────────│
  │◄── 结果 ───────────────│                          │                        │
```

#### 6.3.2 agent 经 agent-runtime 代理查询

```
agent                  agent-runtime              registry-center           远端 agent
  │                        │                          │                        │
  │── 工具：查询            │                          │                        │
  │   wealth-expert 可用性 ►│                          │                        │
  │                        │── GET /instances ───────►│                        │
  │                        │◄── List<AgentCardDto> ───│                        │
  │                        │                          │                        │
  │                        │── 投影层脱敏              │                        │
  │◄── 可用性投影 ──────────│   （可用 / 版本）         │                        │
  │                        │                          │                        │
  │── 决策：选 wealth-expert►│                          │                        │
  │   （用 agentId）        │                          │                        │
  │                        │── POST /resolve ────────►│                        │
  │                        │◄── RouteResolution ──────│                        │
  │                        │── 转发到 endpointUrl ────────────────────────────►│
  │                        │◄── 任务结果 ──────────────────────────────────────│
  │◄── 任务结果 ────────────│                          │                        │
```

## 7. 错误处理（Process View）

| 错误场景 | 触发条件 | HTTP | error code | 行为 |
|---------|---------|------|-----------|------|
| 查询参数缺 `tenantId` | path variable 为空 | 400 | `invalid_request` | fail-fast，不进默认租户 |
| 查询参数缺 `agentId` | path variable 为空 | 400 | `invalid_request` | |
| 跨 tenant 解析 | `resolveRouteHandle` 调用方 tenant 与 handle 内 tenant 不一致 | 400 | `tenant_isolation_violation` | 不跨 tenant fallback |
| TenantContext 交叉校验失败 | 后台调度路径 bound tenant 与参数不一致 | 400 | `tenant_isolation_violation` | |
| route handle 畸形 | 缺 `v2:` 前缀 / base64 损坏 / JSON 缺字段 / 旧 4 字段或 `v1:` 5 字段格式 | 400 | `malformed_handle` | baseline-breaking |
| entry 不存在 | handle 指向的 `(tenantId, agentId, serviceId, instanceId)` 无记录 | 404 | `entry_not_found` | |
| 目标不存在 / 不可见 / 无可用实例 | `searchInstancesByAgentId` 无 ONLINE/DEGRADED 行 | 200 | （空 List） | 反枚举：与「不存在」不可区分 |
| 中心不可用（MVP） | PG 连接失败 / 超时 | 5xx | （由 Spring 默认映射） | 显式失败；调用方包装 `discovery_unavailable` |
| 中心不可用 + 本地信息可用（阶段一） | 同上 + 本地缓存命中 | 200 | （`degraded` 标记） | 阶段一实现 |
| 版本不匹配 | DTO `contractVersion` 不满足调用方要求 | 200 | （DTO 含版本字段） | 调用方判断并排除；阶段一可在查询输入加版本过滤 |
| 路由失败 | 转发层投递失败 | （转发层语义） | `route_unavailable` | 转发层包装，不向 agent / client 暴露物理失败细节 |

scope §5.1.7 错误语义映射：

| scope 错误语义 | MVP 实现 |
|---------------|---------|
| 查询参数缺少 `tenantId` | 400 `invalid_request` |
| tenant 不匹配 | 400 `tenant_isolation_violation` |
| 无权限目标 | 空 List（反枚举） |
| 目标不存在 | 空 List |
| 中心不可用且本地信息可用 | 阶段一（`degraded / cached route`） |
| 中心不可用且本地信息缺失 | 调用方包装 `discovery_unavailable / no route` |
| 版本不匹配 | DTO 携带版本字段，调用方判断 |
| 路由失败 | 转发层 `route_unavailable` |

## 8. 限制与待补

| 限制 | 影响范围 | 临时方案 / 阶段规划 |
|------|---------|-------------------|
| 仅支持 by `agentId` 查询，不支持 by `serviceId` / `capability` | `serviceId` / `capability` 维度的已知目标查询无法直接使用 | 阶段一随 `capability` 字段重建引入 `searchByServiceId` / `searchByCapability` |
| 无中心不可用降级 | 中心短时不可用时显式失败，无法维持已知目标调用 | 阶段一引入本地缓存 + 有效性窗口（handle TTL = 一个探活周期） |
| 可用性状态细分不足 | scope §5.1.5 的「有限可用」未实现；`DRAINING` 在 SQL 层直接排除而非呈现为「有限可用」 | 阶段一评估是否把 `DRAINING` 纳入「有限可用」投影 |
| 版本不匹配不在 discovery 层强制过滤 | 调用方需自行比对 `contractVersion` | DTO 携带版本字段；阶段一可在查询输入加 `contractVersion` 过滤 |
| 路由可用性投影层不在 registry 单元 | registry 只输出系统路由视图；投影实现是 `agent-runtime` 的职责 | 设计如此——投影层属 `agent-runtime` 对 agent 表面 |
| gateway / agent-runtime 集成未在 registry-discovery-center 实现 | FEAT-016 的调用方在 gateway / agent-runtime 模块，不在 registry 单元 | 各模块自行设计；本文只约束 Service 接口契约与视图分层 |
| route handle 旧 4 字段及 `v1:` 5 字段格式不兼容 | REQ-2026-006 + serviceId/instanceId 分离升级后旧 handle 立即失效 | handle 生命周期短（一个探活周期）；迁移后调用方重新 `GET /instances` |

## 9. 与 scope 文档的对齐矩阵

| scope 要求 | L2 落地 | 备注 |
|-----------|---------|------|
| §2 已知目标路由查询 MUST | §4.1 / §4.2 / §2.3.1 | MVP by agentId；by serviceId / capability 阶段一 |
| §2 统一查询语义 MUST | §3.2 / §4.1 / §4.2 | gateway 与 agent-runtime 共享 `AgentDiscoveryService` Service 接口 |
| §2 agent-runtime 代理查询 MUST | §3.2 / §4.2 | agent 不直连 registry；经 agent-runtime 注入工具 |
| §2 多实例候选 MUST | §4.3 | `List<AgentCardDto>` + `(tenant_id, agent_id, service_id, instance_id)` PK；同 `agentId` / 同 `serviceId` 多实例 |
| §2 运行时实例标识 MUST | §4.3 / §2.3.2 | `serviceId` 逻辑服务标识（多实例共享）+ `instanceId` 实例标识（host-port，进 handle 不进 DTO 明文） |
| §2 路由引用 MUST | §2.3.2 / §4.1 / §4.2 | opaque `routeHandle`（`v2:` 6 字段） |
| §2 路由可用性投影 MUST | §2.3.2 / §4.2 | 投影层在 agent-runtime；registry 输出系统路由视图 |
| §2 租户隔离 MUST | §2.3.3 / §4.6 / §7 | RLS + 应用层 WHERE + TenantContext |
| §2 版本约束 MUST | §2.3.2 / §4.4 | DTO 携带版本字段；阶段一加强制过滤 |
| §2 健康与可用性 MUST | §4.4 | MVP 子集；「有限可用」阶段一补齐 |
| §2 中心短时不可用降级 MUST | §4.5 / §8 | 阶段一 |
| §2 反枚举保护 MUST | §4.6 | 空结果语义 |
| §2 Task 状态隔离 MUST | §2.3.2 | DTO 不含 Task 字段 |
| §2 物理细节透明 MUST | §2.3.2 | DTO 不含物理地址；agent / client 不依赖 Consul / PG / broker |
| §5.1.1 已知目标路由语义 | §1.1 / §4.1 / §4.2 | |
| §5.1.2 agent-runtime 代理语义 | §3.2 / §4.2 | |
| §5.1.3 gateway 直连语义 | §4.1 | |
| §5.1.4 视图分层与脱敏 | §2.3.2 / §4.2 | 系统路由视图 vs 路由可用性投影 |
| §5.1.5 可用性状态语义 | §4.4 | 五态映射 |
| §5.1.6 中心不可用与恢复 | §4.5 | MVP 显式失败；阶段一降级 |
| §5.1.7 错误语义 | §7 | 映射表 |
| §5.2 显式边界与不承诺项 | §2.2 / §8 | 对齐 |
| §6 下游设计约束 | §2.3.3 / §3.2 / §4.2 | Service 接口契约 + 视图分层 + agent 不直连 registry |
