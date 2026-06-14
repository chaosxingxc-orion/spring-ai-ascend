---
level: L2
view: process
status: draft
---

# ICD-Agent-Bus-Forwarding

> 命名说明：本 ICD 架构语义（参与模块、所有权、边界、转发角色）使用 L0 逻辑名 `agent-runtime` / `agent-core`（当前实现/兼容落点分别为 `agent-service` / `agent-execution-engine`）；当前代码路径、Maven artifact、`module-metadata.yaml`、forbidden dependencies 仍保留旧名。转发两端在架构语义上是 runtime-to-runtime；当表达一般服务实例时使用 `service instance` / 「服务实例」，不写成模块名。

## 目的

定义 `agent-bus` 拥有的类消息队列（MQ-like）转发语义，回答 Stage 4 的核心问题「怎么发」：

- Gateway 如何将外部请求分发到 runtime。
- 真 bus 如何承载 runtime-to-runtime 的异步控制消息。
- ack、retry、timeout、DLQ、ordering、backpressure、correlation 如何表达。
- route handle 如何被转发语义消费。
- 哪些语义属于 broker 无关契约，哪些是未来运行态实现选择。

本 ICD 是 **设计态 / 契约态** 定义：Stage 4 只定义转发语义和 harness 断言，**不实现运行态转发底座、不绑定 broker / MQ 产品**（产品选择 deferred 到 Stage 5 运行态候选方案评审）。

本 ICD 显式消费 Stage 3 的 [`ICD-Agent-Registry-Discovery`](ICD-agent-registry-discovery.md)：转发依据 discovery 返回的 opaque route handle 选择目标，**不直接暴露或绕过物理 endpoint**。

## 适用读者

`agent-bus` forwarding view owner、gateway 与真 bus 的实现者、`agent-runtime`（当前实现落点：`agent-service`）owner、`agent-core`（当前实现落点：`agent-execution-engine`）owner、`agent-client` / edge owner、`agent-middleware` owner、harness 生成器、架构评审者。

## 维护规则

- 本 ICD 是 draft，正式 wire contract 需要与 ADR-0050（Bus & State Hub）、ADR-0089（Edge-Plane Ingress Gateway）、ADR-0101（Federation）、`ICD-Agent-Registry-Discovery`（route handle 来源）、`ICD-cs-capability-placement.md` 对齐。
- 转发语义 **broker-agnostic**：本 ICD 不绑定具体 broker / MQ 产品；产品选择 deferred 到 Stage 5。
- forwarding envelope 必须携带 `tenantId`（延续 registry key 强制 tenantId，HD3-003 / Rule R-C.c）。
- forwarding envelope 通过 `routeHandle` 消费 Stage 3 discovery result；**禁止绕过 route handle 直接使用物理 endpoint**。
- forwarding envelope **只携带 `payloadRef`，不携带 payload body**：大载荷走 data reference path，event / control channel 不承载大对象正文或 token stream。
- runtime-to-runtime 消息 **不改变远端 Task lifecycle owner**；`agent-bus` 不写 Task execution state（与 registry / discovery 边界一致）。
- Stage 4 不新增 mailbox / queue / DLQ / replay 运行态存储、不实现 service discovery API runtime、不改 Maven module 名或目录名。

| Field | Value |
|---|---|
| ICD ID | ICD-Agent-Bus-Forwarding |
| Participating Modules | `agent-bus`（forwarding view owner）；`agent-runtime`、`agent-core`、`agent-client` / edge、`agent-middleware`（转发参与者 / 消费者）；gateway + 真 bus（分发与转发执行点）。 |
| Interaction Purpose | 为 gateway 入口分发与真 bus runtime-to-runtime 异步控制消息提供 tenant-scoped、broker-agnostic、route-handle-driven 的转发语义；明确 ack / retry / timeout / DLQ / ordering / backpressure / correlation 的契约表达，把产品选择留给 Stage 5。 |
| Forwarding Envelope Required Fields | `tenantId`、`traceId`、`correlationId`、`idempotencyKey`、`routeHandle`、`capability`、`payloadRef`、`deadline`。`tenantId` 强制；`routeHandle` 来自 Stage 3 discovery。 |
| Route Handle (HD4) | `routeHandle` 来自 `ICD-Agent-Registry-Discovery` 的 discovery result，内部封装 endpoint / topic / serviceId / routeKey。转发方只持 route handle，**不直接暴露或操作物理 endpoint**；route handle 是转发与发现的唯一关联点（discovery result 与 forwarding envelope 通过 route handle 关联）。 |
| Delivery Model | 区分 **同步 ack**（转发底座确认已接收并落队，不等处理完成）与 **异步完成**（接收方处理后回传 outcome）。两者用不同 envelope / response 状态表达，不混用。 |
| Retry | 只有转发底座或明确授权的重试者允许重试；重试依据是 failure mode + `idempotencyKey`；接收方据 `idempotencyKey` 抑制重复（`duplicate_suppressed`）。业务层自发重试不算转发语义。 |
| Timeout | 区分三类：**request deadline**（envelope 携带的 `deadline`，整个请求有效期）、**delivery timeout**（投递到接收方的超时）、**processing timeout**（接收方处理超时）。三者独立，分别映射到不同 failure mode。 |
| DLQ / Replay | 投递失败且重试耗尽、不可恢复错误进入 DLQ；replay 保留 `tenantId`、`traceId`、`payloadRef`、`correlationId`，不重建 payload body（payload 走 data reference path）。DLQ 是设计态语义，运行态存储选择 deferred。 |
| Ordering | 默认 **无全局 ordering**。可选 per-tenant / per-route / per-correlation 的局部 ordering，由 route 维度表达；ordering 是运行态实现选择，不进 broker-agnostic 必选契约。 |
| Backpressure | 接收方不可用、队列压力、tenant quota 超限用显式状态表达：接收方拒绝 / 延迟接收，转发方据此降速或失败。backpressure 不静默丢消息。 |
| Failure Modes | `route_not_found`（route handle 无法解析）；`tenant_mismatch`（envelope tenantId 与 route / 接收方 tenant 不一致）；`delivery_timeout`（投递超时）；`receiver_unavailable`（接收方不可用）；`backpressure_rejected`（因 backpressure 被拒绝）；`duplicate_suppressed`（幂等键命中重复）。 |
| Payload Reference Path | 大载荷通过 `payloadRef`（data reference）传递，**不进 event / control channel**；event / control channel 只携带控制语义与引用，不承载大对象正文或 token stream。 |
| Forbidden Payload | forwarding envelope **不携带 payload body、不携带 token stream、不携带 Task execution state**；这三类内容一律走引用或归对应 owner。 |
| Lifecycle Ownership | runtime-to-runtime 消息只携带控制与引用，**不改变远端 Task lifecycle owner**；`agent-bus` 不写 Task execution state。 |
| Broker Posture (HD4) | broker-agnostic：本 ICD 不绑定具体 broker / MQ 产品；in-memory dispatcher、外部 broker、数据库 outbox / inbox、runtime-local queue、混合方案等运行态选择 deferred 到 Stage 5。 |
| Stage 4 Boundary | 不实现运行态转发底座；不新增 mailbox / queue / DLQ / replay 运行态存储；不绑定 broker / MQ 产品；不实现 service discovery API runtime；不修改 Task lifecycle 所有权；不改 Maven module 名或目录名。 |
| Contract Tests (design-level, 切片 3) | `forwarding_icd_exists_and_l1_readme_backlinks`；`forwarding_envelope_requires_tenant_id`；`forwarding_envelope_requires_route_handle`；`forwarding_envelope_carries_payloadref_not_body`；`forwarding_failure_modes_cover_backpressure_timeout_tenant`；`stage4_adds_no_broker_runtime_package`；`discovery_result_and_forwarding_envelope_linked_by_route_handle`。 |
| Open Issues | 同步 ack 与异步完成的具体 envelope / response schema；delivery / processing timeout 的默认值；DLQ 运行态存储选择（deferred）；局部 ordering 的 route 维度定义；backpressure 的具体状态编码；broker 产品选择（Stage 5）。 |

## Stage 4 与 Stage 5 的边界

Stage 4 只稳定 broker-agnostic 转发语义；Stage 5 才比较 in-memory dispatcher、外部 broker 产品、数据库 outbox / inbox、runtime-local queue、混合方案。Stage 5 必须建立在 Stage 4 已稳定的 broker-agnostic 语义上，不能用某个产品能力反向定义架构语义。
