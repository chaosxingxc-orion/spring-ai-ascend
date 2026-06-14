---
level: L2
module: agent-bus
view: development
status: draft
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_icd_design: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
source_candidates: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md
target_module: agent-bus
---

# agent-bus L2 技术设计：类 MQ 转发 outbox / inbox（C3 运行态承载）

> 命名说明：本设计架构语义（参与模块、所有权、边界）使用 L0 逻辑名 `agent-runtime` / `agent-core`（当前实现 / 兼容落点分别为 `agent-service` / `agent-execution-engine`）；代码路径、Maven artifact、`module-metadata.yaml`、forbidden dependencies 仍保留旧名。

## 1. 目标

为 `agent-bus` 的 runtime-to-runtime 类 MQ 转发提供一个 **durable、可审计、tenant-scoped、broker-agnostic** 的最小底座（候选 C3，database outbox / inbox，见 [`decision`](../../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)）。

本 L2 把 Stage 4 的设计态转发语义（[`ICD-Agent-Bus-Forwarding`](../../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md)）投影为：

- 可直接落地的组件边界与端口接口。
- outbox / inbox 两套状态机（含触发条件、终态、失败码）。
- 幂等键、租户隔离、失败语义的精确化定义。
- 可被 harness 字段级校验的 schema 草案（见 runtime ICD + machine-readable yaml）。

Stage 7 只交付**最小骨架**：领域模型、端口接口、状态机、schema 草案、harness、in-memory test double。真实持久化实现（JDBC / migration / polling / lease / 并发抢占 / 真实投递绑定）是 **Stage 8**。

## 2. 非目标

- **不**定义 Task 生命周期、不拥有 Run / Task 状态（Task execution state 仍归 `agent-runtime`）。
- **不**拥有 agent / service / capability 定义（注册发现只消费 Stage 3 route index）。
- **不**绑定具体 broker / MQ 产品（broker-agnostic；broker adapter 是 Stage 8+ 可选上层）。
- **不**实现 payload 存储（大载荷走 `payloadRef` data reference path，不进 outbox / inbox 正文）。
- **不**在 Stage 7 接真实数据库（停在端口接口 + 状态机 + in-memory test double）。

## 3. 组件边界

```
  调用方 service                          接收方 service
        |                                       ^
        v                                       |
  ForwardingGateway                        ForwardingInbox
  (校验 / 接收 / 写 outbox)               (去重 / 幂等 / 审计)
        |                                       ^
        v                                       |
  ForwardingOutbox  <-- ForwardingDispatcher --> (routeHandle 投递)
  (durable queue, PENDING..DLQ)            (基于 routeHandle,不暴露物理 endpoint)
        |                                       |
        +-- ForwardingRegistryPort --+----------+
            (消费 Stage 3 discovery,   |
             不拥有 registry)          v
                                  routeHandle 来源 = ICD-Agent-Registry-Discovery
```

| 组件 | 职责 | 边界（不做什么） |
|---|---|---|
| `ForwardingGateway` | runtime-to-runtime 转发入口：校验 envelope、接收、写入 outbox、返回同步 ack receipt。 | 不投递（投递归 dispatcher）；不写 Task state；不解析 routeHandle 内部。 |
| `ForwardingOutbox` | 待发送消息的 durable queue；维护 outbox 状态机、attemptCount、nextAttemptAt、lastFailureCode。 | 不持有接收方状态；不直接调用接收方 transport。 |
| `ForwardingDispatcher` | 从 outbox 取消息，基于 routeHandle 投递；驱动 outbox 状态迁移（PENDING→DISPATCHING→ACKED/RETRY/DLQ）。 | 不暴露 / 不绕过物理 endpoint；不写 Task state；Stage 7 不接真实 transport。 |
| `ForwardingInbox` | 接收端去重（幂等键）、幂等消费、审计；驱动 inbox 状态机。 | 不持有发送方状态；不回写 outbox（ack 经独立 receipt 回路）。 |
| `ForwardingRegistryPort` | 消费 Stage 3 discovery 结果（opaque routeHandle）；不拥有 registry / agent 定义。 | 不写 registry；不缓存物理 endpoint；不跨 tenant fallback。 |

> 代码骨架（Stage 7 切片 4）实现最小可测试子集：`ForwardingEnvelope` / `ForwardingMessageId` / `ForwardingRouteHandle` / `ForwardingStatus` / `ForwardingFailureCode` / `ForwardingReceipt` / `ForwardingOutboxPort` / `ForwardingInboxPort` / `ForwardingDispatcher` / `ForwardingStateMachine`。`ForwardingGateway` 的「校验 + 接收 + 写 outbox」角色由 dispatcher + outbox port 承担；`ForwardingRegistryPort` 的「消费 discovery」由 `ForwardingRouteHandle`（opaque，来自 discovery）表达。完整 Gateway / RegistryPort 物理实现随 Stage 8 真实持久化落地。

## 4. 状态机

所有状态迁移由 `ForwardingStateMachine`（runtime 包，纯函数，无 IO）裁决；端口实现调用状态机计算新状态后再持久化。非法迁移抛 `IllegalStateTransitionException`。

### 4.1 outbox 状态机

| From | Event | To | 触发条件 | 失败码 | 终态 |
|---|---|---|---|---|---|
| `(new)` | `ENQUEUE` | `PENDING` | envelope 校验通过（tenant 一致、payloadRef 条件满足、无 payload body），写入 outbox | —（校验失败在入队前即拒） | |
| `PENDING` | `BEGIN_DISPATCH` | `DISPATCHING` | dispatcher 取出消息并开始投递 | | |
| `DISPATCHING` | `ACK` | `ACKED` | 接收方同步 ack（ICD Delivery Model：同步 ack） | | ✓ |
| `DISPATCHING` | `RETRY` | `RETRY_SCHEDULED` | retryable 失败且 attemptCount < 上限 | `delivery_timeout` / `receiver_unavailable` / `backpressure_rejected` | |
| `DISPATCHING` | `EXHAUST_RETRIES` | `DLQ` | 不可恢复失败，或 attemptCount 达上限 | `route_not_found`（不可恢复）/ retryable 已耗尽 | ✓ |
| `RETRY_SCHEDULED` | `BEGIN_DISPATCH` | `DISPATCHING` | nextAttemptAt 到达，重新投递 | | |
| `RETRY_SCHEDULED` | `EXHAUST_RETRIES` | `DLQ` | 重试上限耗尽 | retryable 已耗尽 | ✓ |
| `PENDING` / `DISPATCHING` / `RETRY_SCHEDULED` | `EXPIRE` | `EXPIRED` | envelope `deadline` 超过（ICD request deadline） | `delivery_timeout` | ✓ |
| 任意非终态 | （非法迁移） | — | — | — | 抛 `IllegalStateTransitionException` |

终态：`ACKED`（成功）、`DLQ`（不可恢复 / 重试耗尽）、`EXPIRED`（超时）。终态不可再迁移。

### 4.2 inbox 状态机

| From | Event | To | 触发条件 | 失败码 | 终态 |
|---|---|---|---|---|---|
| `(new)` | `ARRIVE_NEW` | `RECEIVED` | 幂等键未命中（首次） | | |
| `(new)` | `ARRIVE_DUPLICATE` | `DUPLICATE_SUPPRESSED` | 幂等键命中（重复） | `duplicate_suppressed` | ✓ |
| `RECEIVED` | `CONSUME` | `CONSUMED` | 接收方处理完成 | | ✓ |
| `RECEIVED` | `REJECT` | `REJECTED` | 接收方拒绝（tenant mismatch / payloadRef invalid / schema invalid） | `tenant_mismatch` / `payload_ref_invalid` | ✓ |
| 任意非终态 | （非法迁移） | — | — | — | 抛 `IllegalStateTransitionException` |

终态：`DUPLICATE_SUPPRESSED`、`CONSUMED`、`REJECTED`。

## 5. 幂等键

| 维度 | 组成 | 用途 |
|---|---|---|
| outbox 唯一键 | `(tenantId, messageId)` | 同 tenant 同消息只入队一次；重复 enqueue 返回「已存在」receipt（不重复写、不重新投递）。 |
| inbox 去重键 | `(tenantId, messageId, consumerServiceId)` | 接收方对同消息去重；不同 consumer 各自独立消费。 |
| route 稳定维度 | `routeHandle.tenantScope`（== envelope.tenantId） | routeHandle 来自 Stage 3 discovery，opaque；幂等键含 tenantId 保证跨 tenant 不混淆、不可跨 tenant fallback。 |

`messageId` 是 `ForwardingMessageId`（opaque 稳定值）。`idempotencyKey`（envelope 字段）与 `messageId` 的关系：`idempotencyKey` 是调用方提供的业务幂等键，`messageId` 是转发底座分配的转发级唯一键；去重以 `(tenantId, messageId, consumerServiceId)` 为主，`idempotencyKey` 作为辅助校验（`duplicate_suppressed` 可由任一命中触发）。

## 6. 租户隔离

- outbox / inbox **所有 record 强制 `tenantId` 非 null 非 blank**（compact constructor 校验，对齐 Rule R-C.c）。
- 所有查询 / 投递 / 去重 / 审计方法签名**带 `tenantId` 参数**；端口实现必须按 `tenantId` 过滤，禁止跨 tenant 读取。
- `routeHandle.tenantScope` **必须等于** `envelope.tenantId`（envelope 构造时校验）；不匹配 → `tenant_mismatch`，拒绝入队 / 拒绝接收。
- 跨 tenant 查询显式失败（返回空集 / 抛 `tenant_isolation_violation`），**禁止跨 tenant fallback**（延续 Stage 3 registry 隔离）。

## 7. 失败语义

| FailureCode（wire） | 场景 | 触发点 | 处理 |
|---|---|---|---|
| `route_not_found` | routeHandle 无法解析（Stage 3 discovery 无此 route / 已注销） | dispatch 前 | 不可恢复 → `DLQ` |
| `tenant_mismatch` | `envelope.tenantId != routeHandle.tenantScope` | envelope 构造 / inbox 接收 | 拒绝（不入队 / inbox `REJECTED`） |
| `delivery_timeout` | 投递超时（ICD delivery timeout）或 deadline 超过 | dispatch 中 / `EXPIRE` | retryable → `RETRY_SCHEDULED`，耗尽 `DLQ` / `EXPIRED` |
| `receiver_unavailable` | 接收方不可用 | dispatch 中 | retryable → `RETRY_SCHEDULED` |
| `backpressure_rejected` | 接收方 / 队列压力拒绝 | dispatch / inbox | retryable → `RETRY_SCHEDULED`（不静默丢消息） |
| `duplicate_suppressed` | inbox 幂等键命中 | inbox `ARRIVE_DUPLICATE` | `DUPLICATE_SUPPRESSED` 终态 |
| `payload_ref_invalid` | `DATA_BEARING` 消息缺 `payloadRef` / `payloadRef` blank / 不可解析 | envelope 构造 / inbox 接收 | 拒绝（不入队 / inbox `REJECTED`） |

retryable vs 不可恢复：`route_not_found`、`tenant_mismatch`、`payload_ref_invalid` 不可恢复（直接 DLQ / REJECT）；`delivery_timeout` / `receiver_unavailable` / `backpressure_rejected` 可重试（受 attemptCount 上限 + deadline 约束）。

## 8. 端口接口投影（Stage 7 骨架）

```
ForwardingOutboxPort (spi)
  enqueue(envelope, now)           -> ForwardingReceipt     // 同步 ack；重复 -> 已存在 receipt
  markDispatching(id, tenantId)    -> Outbox status          // PENDING -> DISPATCHING
  markAcked(id, tenantId)          -> Outbox status          // DISPATCHING -> ACKED
  scheduleRetry(id, tenantId, code, nextAttemptAt) -> status // -> RETRY_SCHEDULED
  moveToDlq(id, tenantId, code)    -> Outbox status          // -> DLQ
  markExpired(id, tenantId)        -> Outbox status          // -> EXPIRED
  statusOf(id, tenantId)           -> Outbox status
  findRetryable(now)               -> List<id> (tenant-scoped, nextAttemptAt <= now)

ForwardingInboxPort (spi)
  receive(envelope, consumerServiceId, now) -> InboxReceipt  // 去重判定 -> RECEIVED / DUPLICATE_SUPPRESSED
  markConsumed(id, tenantId, consumer)      -> Inbox status
  markRejected(id, tenantId, consumer, code)-> Inbox status
  statusOf(id, tenantId, consumer)          -> Inbox status

ForwardingDispatcher (spi)
  dispatch(envelope)               -> ForwardingReceipt       // 编排 enqueue -> DISPATCHING -> ACK/RETRY/DLQ

ForwardingStateMachine (runtime, 纯函数)
  transitOutbox(current, event)    -> Outbox status           // 非法迁移抛异常
  transitInbox(current, event)     -> Inbox status
```

端口实现（JDBC / 真实 DB）是 Stage 8；Stage 7 提供 in-memory test double（test source set）验证端口语义与状态机。

## 9. 与 Stage 3 / Stage 4 的消费关系

- **消费 Stage 3 discovery**：`routeHandle` 是 `ICD-Agent-Registry-Discovery` discovery result 的 opaque 封装；转发只持 routeHandle，**不直接暴露或绕过物理 endpoint**（延续 HD4）。
- **承载 Stage 4 语义**：outbox/inbox 是 Stage 4 broker-agnostic 转发语义（ack / retry / timeout / DLQ / correlation / backpressure / tenant-aware routing）的运行态承载；本 L2 不修改 Stage 4 语义，只投影为状态机与端口。
- **不改变 Task ownership**：runtime-to-runtime 消息只携带控制与 `payloadRef`，**不改变远端 Task lifecycle owner**；`agent-bus` 不写 Task execution state（延续 HD4 / 与 registry 边界一致）。

## 10. Stage 8 deferred（不在 Stage 7）

- JDBC / 真实 DB 实现；outbox / inbox 物理表 schema migration / rollback 策略。
- polling / lease / 并发抢占（CAS / `SELECT ... FOR UPDATE` / advisory lock）。
- backpressure 参数（队列阈值、降速策略、tenant quota）。
- 真实投递绑定（dispatcher → 接收方 transport；HTTP / gRPC / 内部 RPC）。
- 是否独立 adapter module；接入 `agent-runtime` 受控调用路径。
- ordering / fairness 的具体实现（per-tenant / per-route 局部 ordering 是运行态选择，不进 Stage 7 必选）。

## 11. DoD 自检

- ✓ L2 能直接投影出 schema（runtime ICD + machine-readable yaml）、接口（§8）、测试计划（Stage 7 切片 5）。
- ✓ 所有状态迁移都有触发条件、终态和失败码（§4）。
- ✓ 幂等键、租户隔离、失败语义精确化（§5 / §6 / §7）。
- ✓ 组件边界与 Stage 7 代码骨架子集对应（§3 注）。
- ✓ 不引入 broker / MQ 依赖；不改 Task ownership；不绕 routeHandle；不放 payload body（§2 非目标 + decision §4/§6）。
