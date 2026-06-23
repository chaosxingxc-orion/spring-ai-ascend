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

> 命名说明：本设计架构语义（参与模块、所有权、边界）使用 L0 逻辑名 `agent-runtime` / `agent-core`。`agent-runtime` 已落地为同名模块（原 `agent-service` 已重命名）；`agent-core` 已落地为 `agent-core`。

## 1. 目标

为 `agent-bus` 的 runtime-to-runtime 类 MQ 转发提供一个 **durable、可审计、tenant-scoped、broker-agnostic** 的最小底座（候选 C3，database outbox / inbox，见 [`decision`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)）。

本 L2 把 Stage 4 的设计态转发语义（[`ICD-Agent-Bus-Forwarding`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md)）投影为：

- 可直接落地的组件边界与端口接口。
- outbox / inbox 两套状态机（含触发条件、终态、失败码）。
- 幂等键、租户隔离、失败语义的精确化定义。
- 可被 harness 字段级校验的 schema 草案（见 runtime ICD + machine-readable yaml）。

Stage 7 交付**最小骨架**：领域模型、端口接口、状态机、schema 草案、harness、in-memory test double。**Stage 8** 补齐 record 模型（`ForwardingOutboxRecord` / `ForwardingInboxRecord`）、claim / lease 端口、dispatcher worker skeleton、schema / migration 草案（[`forwarding-persistence`](forwarding-persistence.md)）。真实持久化（Stage 12 JDBC adapter + Flyway + RLS）与真实投递绑定（Stage 15 A2A transport adapter）已落地，跨模块端到端验证 Stage 17/18 通过（184 tests green）；细节见 [`forwarding-persistence`](forwarding-persistence.md) 与本文 §10。

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

`messageId` 是 `ForwardingMessageId`（opaque 稳定值）。`idempotencyKey`（envelope 字段）与 `messageId` 的关系（MI8-004 收口）：`idempotencyKey` 是调用方提供的业务幂等键，**降级为审计字段**；去重以 `(tenantId, messageId, consumerServiceId)` 为唯一依据，**不**随 `idempotencyKey` 命中触发 `duplicate_suppressed`。如需保留 `idempotencyKey` 供审计，作为 inbox 可选非键列后续追加，不改去重语义。

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
| `remote_task_failed` | 远程 agent 终态业务失败（A2A `FAILED` / `CANCELED` / `REJECTED`，`isFinal && !COMPLETED`） | transport 终态映射（Stage 15 / Stage 18） | 不可恢复 → `DLQ`（不消耗 retry 预算，正交于 retry policy） |

retryable vs 不可恢复：`route_not_found`、`tenant_mismatch`、`payload_ref_invalid`、`remote_task_failed` 不可恢复（直接 DLQ / REJECT；`remote_task_failed` 不消耗 retry 预算，正交于 `ForwardingRetryPolicy`）；`delivery_timeout` / `receiver_unavailable` / `backpressure_rejected` 可重试（受 `ForwardingRetryPolicy` 的 attemptCount 上限 + deadline 约束）。

## 8. 端口接口投影（Stage 7 骨架 + Stage 8 补齐）

```
ForwardingOutboxPort (spi)                          // gateway / worker 共用：写入 + lease-owner guarded 状态迁移 + 状态查询
  enqueue(envelope, sourceServiceId, targetServiceId, now) -> ForwardingReceipt
                                                    // 同步 ack；source/target 写入 record（MI8-002）；重复 -> 已存在 receipt
  markAcked(id, tenantId, leaseOwner)            -> Outbox status  // DISPATCHING -> ACKED（terminal，清 lease；MI9-001/002）
  scheduleRetry(id, tenantId, leaseOwner, code, nextAttemptAt) -> status   // code 须 retryable；-> RETRY_SCHEDULED（attemptCount + 1，清 lease）
  moveToDlq(id, tenantId, leaseOwner, code)      -> Outbox status  // -> DLQ（清 lease）
  markExpired(id, tenantId, leaseOwner)          -> Outbox status  // -> EXPIRED（清 lease）
  statusOf(id, tenantId)                         -> Outbox status
                                                    // markDispatching 已移除：DISPATCHING 只经 claimDue 进入（MI9-001）
                                                    // 状态变更 lease-owner guarded：stale/foreign/expired owner 抛 ForwardingLeaseException

ForwardingOutboxClaimPort (spi)                     // Stage 8（MI8-001）：claim / lease，替代 findRetryable(now)
  claimDue(tenantId, now, limit, leaseOwner, leaseUntil) -> List<ForwardingOutboxRecord>
                                                    // 原子声明到期记录（PENDING / due RETRY_SCHEDULED / 过期 lease 的 DISPATCHING）
                                                    // -> DISPATCHING + stamped lease；tenant-scoped；terminal 不可 claim
  renewLease(id, tenantId, owner, until)  -> boolean
  releaseLease(id, tenantId, owner)       -> boolean

ForwardingInboxPort (spi)
  receive(envelope, consumerServiceId, now) -> Inbox status  // 去重判定 -> RECEIVED / DUPLICATE_SUPPRESSED
  markConsumed(id, tenantId, consumer)      -> Inbox status
  markRejected(id, tenantId, consumer, code)-> Inbox status
  statusOf(id, tenantId, consumer)          -> Inbox status

ForwardingDispatcher (spi)                          // accept / enqueue 网关角色（MI8-003）
  dispatch(envelope, sourceServiceId, targetServiceId, now) -> ForwardingReceipt

ForwardingDeliveryPort (spi)                        // Stage 8：抽象投递，worker 消费 routeHandle
  deliver(record, now)              -> ForwardingDeliveryResult  // outcome: ACKED / RETRY_SCHEDULED / DLQ / EXPIRED
                                                    // 契约（Stage 11，MI11-002）：真实 transport 绑定应把网络 / 超时 / 反序列化异常
                                                    //   映射为 ForwardingDeliveryResult，不应抛非 lease RuntimeException；worker 兜底为 skipped（防御性）

ForwardingDispatcherWorker (runtime)                // Stage 8：claim / deliver / ack / retry 半边（MI8-003）；Stage 10 增 lease 续约 + skip；Stage 11 完善运行态
  runOnce(tenantId, now, limit, leaseOwner, leaseUntil) -> DispatchTickResult
                                                    // claimDue -> deliver -> markAcked / scheduleRetry / moveToDlq / markExpired
                                                    // Stage 10（MI10-002）/ Stage 11（MI11-001）：DispatchLeasePolicy（构造注入）决定 deliver 前是否 renew；
                                                    //   剩余 TTL 读注入 EpochClock（真实墙钟），claimDue 仍以 tick 起始 now 为 claim 时刻
                                                    // Stage 10（MI10-001）/ Stage 11（MI11-002）：ForwardingLeaseException / renew=false / deliver 抛非 lease 异常 -> skip（skipped），tick 继续
                                                    // Stage 11（MI11-003）：仅入参非法（blank tenant/owner、limit<=0）抛 IllegalArgumentException（fail-fast），loop 传播

ForwardingDispatchLoop (runtime)                    // Stage 10（MI10-004）：调度责任骨架（纯 Java，无 scheduler）
  run(tenantId, limit, leaseOwner, leaseDurationMillis) -> DispatchTickResult  // 聚合多 tick
                                                    // TickSource（注入下一 tick 时刻）/ IdleStrategy（空 tick 退避）注入；无 clock / 线程

ForwardingStateMachine (runtime, 纯函数)
  transitOutbox(current, event)    -> Outbox status  // 非法迁移抛异常
  transitInbox(current, event)     -> Inbox status
```

> Stage 8 用 `claimDue` 取代裸 `findRetryable(now)`（MI8-001）：真实持久化下多实例并发抢同一条消息，必须用 claim / lease 防重复投递（并发抢占语义见 [`forwarding-persistence §4`](forwarding-persistence.md)）。端口实现（JDBC adapter）已于 Stage 12 落地（`JdbcForwardingOutbox` 含 `SKIP LOCKED` claim + lease guard + §7.3 RLS）；Stage 7 / Stage 8 的 in-memory test double + in-memory lease harness 仍保留为 fast test double。

## 9. 与 Stage 3 / Stage 4 的消费关系

- **消费 Stage 3 discovery**：`routeHandle` 是 `ICD-Agent-Registry-Discovery` discovery result 的 opaque 封装；转发只持 routeHandle，**不直接暴露或绕过物理 endpoint**（延续 HD4）。
- **承载 Stage 4 语义**：outbox/inbox 是 Stage 4 broker-agnostic 转发语义（ack / retry / timeout / DLQ / correlation / backpressure / tenant-aware routing）的运行态承载；本 L2 不修改 Stage 4 语义，只投影为状态机与端口。
- **不改变 Task ownership**：runtime-to-runtime 消息只携带控制与 `payloadRef`，**不改变远端 Task lifecycle owner**；`agent-bus` 不写 Task execution state（延续 HD4 / 与 registry 边界一致）。

## 10. Stage 8 → Stage 19 已交付 / 后续 deferred

C3 运行态按 [`decision`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md) 分阶段递进落地，截至 Stage 19（186 tests green）：

- **Stage 8**（持久化准备）：record 模型、claim / lease 端口（`claimDue` 取代 `findRetryable`）、dispatcher worker skeleton、抽象 delivery 端口、schema / migration 草案（DDL 草稿，未执行）、in-memory lease harness。
- **Stage 9**（lease-safe / persistence-ready）：lease-owner guarded mutation（`markAcked` / `scheduleRetry` / `moveToDlq` / `markExpired` 带 `leaseOwner`，`markDispatching` 移除）、lease 生命周期闭环、record 条件不变量（Java 构造器 + DDL CHECK + harness）、failure-code classification（retryable / non-retryable / dedup）、claim / state-update SQL contract。路径 B。
- **Stage 10**（dispatch-loop runtime）：worker lease 异常恢复（catch `ForwardingLeaseException` + skip，`DispatchTickResult.skipped` 自洽）、lease 续约（`DispatchLeasePolicy`）、dispatch 调度责任（`ForwardingDispatchLoop` 骨架：`TickSource` / `IdleStrategy` 注入）。路径 B。
- **Stage 11**（runtime-completion）：lease 续约触发时机改读注入 `EpochClock`、`deliver` 非 lease `RuntimeException` 兜底 `skipped`、`runOnce` 仅入参非法 fail-fast。路径 B。
- **Stage 12**（real persistence，**打破路径 B**）：Postgres JDBC adapter（Spring JDBC）+ Flyway migration `V1` + §7.3 RLS；real-SQL 验证 embedded-postgres PG 16.2（17 tests）。ArchUnit 把 Spring/JDBC 圈进 `persistence.jdbc` 子包。真实持久化已从 deferred 解除。
- **Stage 13**（transport 候选评审）：T1-T4 × 8 维度候选评审（[`transport-candidates`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md)），T3 consumer-pull over DB 非裁决推荐；不实现生产代码，最终 push / pull / MQ 投递模型裁决仍 deferred。
- **Stage 14**（deliver 重投策略先行）：`ForwardingRetryPolicy` 端口 + overflow-safe 指数退避 + exhausted→DLQ（DEFAULT：base 100ms / cap 60s / maxAttempts 5 / jitter 0）；worker RETRY_SCHEDULED 分支接入。熔断端口 deferred。§6.2 不变。
- **Stage 15**（真实投递绑定 PoC）：A2A HTTP transport adapter `A2aForwardingDeliveryPort` 消费 agent-runtime `/a2a`（同步等完成 = T1 push）+ `ForwardingEndpointResolver` / `MapEndpointResolver`。`§6.1` 第 4 项「真实投递绑定 deferred」解除、`§6.2` 不变。ArchUnit 把 `org.a2aproject` 圈进 `transport.a2a` 子包。真实投递绑定已从 deferred 解除。
- **Stage 16**（断路器接入 worker）：`ForwardingCircuitBreaker` 端口加 `recordOutcome` 反馈 + `RouteCircuitBreaker` 三态机（CLOSED→OPEN→HALF_OPEN）接入 worker（投递前 `allowsDelivery` 短路 + 投递后 `recordOutcome`）；正当性来自 Stage 15 选 T1 push。纯 JDK transport-agnostic，§6.2 不变。
- **Stage 17**（首次跨模块端到端集成）：`C3ForwardingEndToEndIntegrationTest` 用真实 `LocalA2aRuntimeHost`（替换 Stage 15 MockWebServer）端到端驱动 outbox enqueue → tick → deliver → 真实 /a2a → COMPLETED → ACKED；agent-bus 加 `agent-runtime` test-scope 依赖、生产零依赖。182 tests green。
- **Stage 18**（失败路径端到端 + `REMOTE_TASK_FAILED`）：`C3ForwardingFailurePathIntegrationTest` 双场景（真实 FAILED→DLQ `remote_task_failed` / 不可达 route→RETRY）+ `ForwardingFailureCode.REMOTE_TASK_FAILED` NON_RETRYABLE + 终态映射改 `isFinal()` if-chain；无 DDL / SqlCodec / record 改动（outbox CHECK 不枚举码值）。184 tests green。
- **Stage 19**（重投往返生命周期端到端）：`C3ForwardingRetryLifecycleIntegrationTest` 双场景端到端 —— (A) 持续失败重投 3 次→exhausted(`maxAttempts=3`)→DLQ（`attempt_count=3`，`moveToDlq` 不递增）/ (B) 间歇恢复重投 2 次→ACKED（fake port 注入 `[retry,retry,acked]`，`attempt_count=2`，`markAcked` 不递增）；闭合 Stage 18 盲区（claimDue 的 `RETRY_SCHEDULED` 回收子句 `next_attempt_at <= :now` 此前从未端到端触发）；`MutableEpochClock` + 协调多 tick `TickSource` 时间控制无需 scheduler；两 context-boot IT 加 `@Isolated` 修并发 flaky。无生产代码改动。186 tests green。

后续 deferred：

- 真实 broker / queue / replay store 物理实现；push vs pull / 是否引 MQ 的最终投递模型裁决（H2/H3；选 T2 / T4 需解除 §6.2 引 MQ）。
- registry 集成的 resolver 生产实现（Stage 15 用 `MapEndpointResolver` 替身）。
- 连接池治理 / 熔断参数调优 / breaker 状态持久化。
- polling cadence；并发 worker 分片；backpressure 参数（队列阈值、降速策略、tenant quota）。
- `ForwardingDispatchLoop` 接真实 scheduler。
- ordering / fairness 的具体实现（per-tenant / per-route 局部 ordering 是运行态选择）。

## 11. DoD 自检

- ✓ L2 能直接投影出 schema（runtime ICD + machine-readable yaml）、接口（§8）、测试计划（Stage 7 切片 5）。
- ✓ 所有状态迁移都有触发条件、终态和失败码（§4）。
- ✓ 幂等键、租户隔离、失败语义精确化（§5 / §6 / §7）。
- ✓ 组件边界与 Stage 7 代码骨架子集对应（§3 注）。
- ✓ 不引入 broker / MQ 依赖；不改 Task ownership；不绕 routeHandle；不放 payload body（§2 非目标 + decision §4/§6）。
