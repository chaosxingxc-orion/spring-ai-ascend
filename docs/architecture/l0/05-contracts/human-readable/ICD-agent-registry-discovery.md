---
level: L2
view: process
status: draft
revision_note: |
  2026-07-08 / REQ-2026-004 / ADR-0161: `capability` 字段全局移除（registry key /
  entry required fields / discovery query / audit / observability）；
  `agentType` → `frameworkType`（FrameworkType enum: JIUWEN/AGENTSCOPE/
  VERSATILE/PROXY_SERVICE）；discovery 由 dual-method（discoverBestAgents A/B）
  收敛为 `searchByAgentId` 单值精确点查。ADR-0160 decision 2 被 ADR-0161
  supersede。HD3-004 visibility window scope 收窄（discovery 不再用，仅
  health-probe scheduler 用）。
---

# ICD-Agent-Registry-Discovery

> 命名说明：本 ICD 架构语义（参与模块、所有权、边界）使用 L0 逻辑名 `agent-runtime` / `agent-core`（当前实现/兼容落点分别为 `agent-service` / `agent-execution-engine`）；当前代码路径、Maven artifact、`module-metadata.yaml`、forbidden dependencies 仍保留旧名。

## 目的

定义 `agent-bus` 拥有的 Agent / Service / Capability 注册与发现（registry / discovery）接口语义，支撑两类路由：

- **Gateway 入口路由**：外部 client 请求进入时，gateway 需要知道该请求应路由到哪个内部 agent / service / route。
- **真 bus service-to-service 路由**：service 与 service 之间相互调用时，调用方需要发现对端能力、选择目标、拿到 route handle。

本 ICD 是 **设计态 / 契约态** 定义：Stage 3 只定义接口语义和 harness 断言，**不实现运行态注册表**，不选择 memory / durable / 外部 discovery 系统（HD3-007）。

## 适用读者

`agent-bus` registry/discovery view owner、gateway 与真 bus 的实现者、`agent-runtime`（当前实现落点：`agent-service`）owner、`agent-client` / edge owner、`agent-core`（当前实现落点：`agent-execution-engine`）owner、`agent-middleware` owner、harness 生成器、架构评审者。

## 维护规则

- 本 ICD 是 draft，正式 wire contract 需要与 ADR-0050（Bus & State Hub）、ADR-0089（Edge-Plane Ingress Gateway）、ADR-0074（S2C Callback）、ADR-0101（Federation）、`ICD-cs-capability-placement.md` 对齐。
- `agent-bus` **只拥有 runtime route index / discovery view**（HD3-001）。agent 业务定义归 agent 定义来源；Task lifecycle / Task execution state 归 `agent-runtime`（当前实现落点：`agent-service`）。
- **registry key 必须包含 `tenantId`**（HD3-003）。`tenantId` 是注册和查询的强制维度；跨 tenant fallback 必须显式失败，不得静默。
- **discovery result 不得携带 Task execution state**（Run 状态、Task 状态、suspend/resume 证据）。registry 只返回 routing 视图。
- Stage 3 不新增运行态注册表生产类、不绑定 broker / MQ、不修改 Task 生命周期所有权。

| Field | Value |
|---|---|
| ICD ID | ICD-Agent-Registry-Discovery |
| Participating Modules | `agent-bus`（registry / discovery view owner）；`agent-runtime`、`agent-client` / edge、`agent-core`、`agent-middleware`（消费者 / 注册方）；gateway + 真 bus（discovery 查询点）。 |
| Interaction Purpose | 为 gateway ingress 路由与真 bus service-to-service 调用提供 tenant-scoped、version-aware、health-aware 的 route discovery；agent-bus 只拥有 route / discovery 视图，不拥有 agent 定义或 Task 状态。 |
| Ownership Boundary (HD3-001) | `agent-bus` 只拥有 runtime route index。agent 业务定义归 agent 定义来源；Task lifecycle / execution state 归 `agent-runtime`（当前实现落点：`agent-service`）。discovery result 不得携带 Task execution state。 |
| Registry Subject (HD3-002) | 注册主体 = service instance + agent / capability + endpoint / route key。注册的是「可路由能力（routable capability）」，不是 agent 源码定义、不是 Task 状态。 |
| Registry Key (HD3-003) | registry key = `(tenantId, agentId)`。`tenantId` 是 key 的强制组成部分。（REQ-2026-004 / ADR-0161: `capability` 从 key 移除——A2A AgentCard 无 capability 概念，pull 模式无法推导。） |
| Tenant Isolation | `tenantId` 是注册和查询的强制维度。跨 tenant 查询必须显式失败（`tenant_isolation_violation`），**禁止跨 tenant fallback**。调用方 tenant 上下文必须与 query tenantId 一致。 |
| Registry Entry Required Fields | `tenantId`、`agentId`、`frameworkType`（FrameworkType enum: JIUWEN/AGENTSCOPE/VERSATILE/PROXY_SERVICE）、`agentName`、`routeKey`、`contractVersion`、`capabilityVersion`（实现版本，保留）、`endpointUrl`、`a2aAgentCardJson`（原始 A2A AgentCard JSON，pull 模式透传）。 |
| Registry Entry Optional Fields | `health`（readiness / degraded / drain metadata）、`region`、`weight`（用于多候选选择提示）。 |
| Health & Lease (HD3-004) | lease / TTL 到期即从可见集移除（`lease_expired`）；`health` 为 optional metadata。unhealthy / degraded target 仍可见，但必须显式标注 health 状态，由调用方据 health 决策。（REQ-2026-004 / ADR-0161: 15 秒 visibility window scope 收窄——discovery 不再用，仅 health-probe scheduler `scanDueForProbe` 用。） |
| Version (HD3-005) | 每个 entry 携带 `contractVersion`（A2A 契约版本）与 `capabilityVersion`（实现版本）。version mismatch 必须有显式 discovery result 状态（`version_unavailable`），不得静默返回错误版本。 |
| Route Result (HD3-006) | discovery 返回 **opaque route handle**，内部封装 endpoint / topic / serviceId / routeKey。调用方不直接操作物理 endpoint；route handle 是路由的稳定引用。 |
| Discovery Query | query = `(tenantId, agentId)`——单值精确点查（`searchByAgentId`），命中即返回 1 条 AgentCardDto，未命中返回空 Optional。（REQ-2026-004 / ADR-0161: dual-method `discoverBestAgents` A/B 删除——free-text search_tsv 基础设施移除 + `capability` 字段移除，dual-method 失去基础。多值/ranking discovery 如未来需要，需新 ADR。） |
| Discovery Result Fields | `agentId`、`agentName`、`frameworkType`、`routeHandle`、`endpointUrl`、`routeKey`、`contractVersion`、`capabilityVersion`、`health`、`region`、`weight`。**不返回** Task execution state、Run 状态。单值结果（0 或 1 条），无排序/选择责任。 |
| Direction | `register`（push）：runtime → registry POST `/api/registry/register`；`pull-register`：registry → runtime GET `/.well-known/agent-card.json`（ApplicationReadyEvent 触发，串行，单 runtime 失败跳过）；`renew-lease` / heartbeat：owner → registry；`deregister`：owner → registry；`discover`：gateway / 真 bus → registry；route result：registry → caller。 |
| Failure Modes | `entry_not_found`；`tenant_isolation_violation`（cross-tenant query rejected）；`lease_expired`（entry invisible）；`version_unavailable`（无 entry 匹配 version 约束）；`health_unavailable`（entry 存在但 unhealthy / degraded）；`registry_unavailable`。 |
| Persistence Posture (HD3-007) | Stage 3 只定义接口语义和 harness 断言；memory / durable / 外部 discovery 系统的选择 deferred 到后续波次。不绑定具体 runtime，不引入 broker / MQ 依赖。 |
| Security / Permission Semantics | route discovery 不扩大权限；registry 只返回调用方 tenant 范围内的 entry（tenant-scoped）；endpoint 物理细节封装在 route handle 内，不泄露给无权调用方。 |
| Audit Semantics | 记录 register / pull-register / deregister / renew-lease / discover 事件、`tenantId`、`agentId`、`frameworkType`、`contractVersion`、`capabilityVersion`、`health`、`caller`、`traceId`、`outcome`。（REQ-2026-004: audit log 10→9 字段，`capability` 移除。） |
| Observability Fields | `tenantId`、`traceId`、`registryOp`、`agentId`、`frameworkType`、`contractVersion`、`capabilityVersion`、`health`、`routeHandleId`、`outcome`、`latency`。（REQ-2026-004: `capability` 移除，`frameworkType` 加入。） |
| Versioning Strategy | registry entry 字段 additive；改变 route handle 编码、registry key 语义或 tenant 隔离规则属于 breaking change，需 ADR / CR。`contractVersion` / `capabilityVersion` 由注册方声明。 |
| Contract Tests (design-level, 切片 3) | `registry_entry_requires_tenant_id`；`discovery_query_requires_tenant_id`；`discovery_result_has_no_task_execution_state`；`unhealthy_target_has_explicit_health_state`；`version_mismatch_has_explicit_result`；`cross_tenant_query_rejected`；`no_runtime_registry_production_class_added_in_stage3`；`registry_entry_has_framework_type`（REQ-2026-004）；`discovery_uses_search_by_agent_id`（REQ-2026-004）；`audit_log_has_no_capability_field`（REQ-2026-004）。 |
| Stage 3 Boundary | 不实现 runtime registry；不绑定 broker / MQ；不修改 Task lifecycle 所有权；不跨 tenant fallback；不把 `agent-runtime` 的 Task state 字段复制进 discovery result；不新增运行态注册表生产类。 |
| Open Issues | route handle 编码格式；lease / TTL 具体值；health metadata schema；durable vs memory 持久化选择（Stage 3 不选）；与 `ICD-cs-capability-placement.md` 的 local capability registry 关系待对齐；version downgrade 策略；multi-value/ranking discovery 如未来需要需新 ADR（REQ-2026-004 移除 dual-method 后）。 |
