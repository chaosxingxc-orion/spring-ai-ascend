---
level: L2
module: agent-bus
view: development
status: draft
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_l2_runtime: architecture/L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
source_icd_design: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md
target_module: agent-bus
---

# agent-bus L2 技术设计：C3 持久化（outbox / inbox schema、claim / lease、migration 草案）

> 命名说明：本设计架构语义使用 L0 逻辑名 `agent-runtime` / `agent-core`。`agent-runtime` 已落地为同名模块（原 `agent-service` 已重命名）；`agent-core` 已落地为 `agent-core`。

## 1. 目标

把 Stage 7 的 C3「最小骨架」（领域模型 + 端口 + 状态机 + in-memory 替身）推进为「可落真实持久化的运行态底座」（Stage 8 计划 §3）：

- 补齐 `ForwardingOutboxRecord` / `ForwardingInboxRecord` 显式 record 模型，承载 runtime ICD 全部必填字段（MI8-002）。
- 把 due-message 查询从裸 `findRetryable(now)` 升级为 **claim / lease** 端口（`ForwardingOutboxClaimPort`），防多实例重复投递（MI8-001）。
- 提供 outbox / inbox 两张表的 schema 草案 + Postgres DDL 草稿 + migration / rollback 说明。
- 提供 dispatcher worker skeleton（claim → deliver → ACK / RETRY / DLQ / EXPIRED）与抽象 delivery 端口。
- 用 harness 锁住 record 字段一致性、claim / lease 语义、幂等、重试、DLQ、禁止 Task state、禁止 broker 依赖。

## 2. 非目标（Stage 8 §6 护栏）

> **Stage 12 更新（H2/H3 裁决，2026-06）：本节路径 B 护栏已被 Stage 12 打破** —— 数据库产品（Postgres）/ migration·adapter 归属（agent-bus 自有 + Spring JDBC）/ RLS（启用纵深防御）三项已由人类裁决确认，真实 JDBC adapter + Flyway migration 进入许可范围（见 §14 Stage 12 决策表、[`decision §4 / §8`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)）。**transport / 真实投递绑定仍 deferred**（拆出 Stage 12 单独议）。**§6.2 始终不得项不变。** 以下 Stage 8 护栏作为路径 B 历史记录保留。

> 「如果数据库产品或 migration 归属无法确认，则停在 schema 草案 + repository port + in-memory lease harness，不要直接引入生产数据库依赖。」

`agent-bus` 当前 **未接入 Flyway、未声明 JDBC driver / ORM 依赖**（`agent-bus/pom.xml` 仅含 `spring-boot-starter-test` + `archunit-junit5`）。因此 Stage 8 **不引入生产数据库依赖**：

- **不**把 JDBC driver / ORM / Flyway 加进 `agent-bus`。
- **不**把 DDL 当作已执行 migration（§7 的 DDL 是设计草稿，标注「未执行」）。
- **不**接真实 receiver transport（HTTP / gRPC / broker）；delivery 端口保持抽象，fake delivery 仅在 test fixture。
- **不**写 Task execution state；**不**绕过 `routeHandle`；**不**放 payload body。

真实 JDBC adapter、Flyway migration 归属、polling / lease store 的物理实现、真实 delivery 绑定是 **Stage 9+**，需先确认数据库产品 / migration 归属（decision §6.1 / Stage 8 计划 §6）。

## 3. 两张表 schema

两张表的字段与 [`ICD-Agent-Bus-Forwarding-Runtime`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md) 的 record 字段逐一对齐，Java record（`ForwardingOutboxRecord` / `ForwardingInboxRecord`）是同一 schema 的运行态投影。

### 3.1 `agent_bus_forwarding_outbox`

发送端 durable queue。唯一键 `(tenant_id, message_id)`。

| Column | Type | Required | Mutable | 来源（record 字段） | 说明 |
|---|---|---|---|---|---|
| `tenant_id` | varchar | ✓ | ✗ | `tenantId` | Rule R-C.c；非空非 blank；必须等于 routeHandle 的 tenant scope。 |
| `message_id` | varchar | ✓ | ✗ | `messageId.value` | `ForwardingMessageId`；唯一键组成部分。 |
| `source_service_id` | varchar | ✓ | ✗ | `sourceServiceId` | 发起转发的 service instance（gateway 写入，MI8-002）。 |
| `target_service_id` | varchar | ✓ | ✗ | `targetServiceId` | 目标 service instance（discovery 经 routeHandle 投影，非物理 endpoint）。 |
| `route_handle` | varchar | ✓ | ✗ | `routeHandle.value` | opaque；封装 endpoint / topic / routeKey；转发方不暴露物理 endpoint（HD4）。 |
| `payload_ref` | varchar | 条件 | ✗ | `payloadRef` | `DATA_BEARING` 必填，`CONTROL_ONLY` 可空；载荷走 data reference path。 |
| `status` | varchar | ✓ | ✓ | `status`（enum） | `PENDING` / `DISPATCHING` / `ACKED` / `RETRY_SCHEDULED` / `DLQ` / `EXPIRED`；CHECK 约束。 |
| `attempt_count` | integer | ✓ | ✓ | `attemptCount` | 从 0 起，每次 RETRY 递增。 |
| `next_attempt_at` | bigint | 条件 | ✓ | `nextAttemptAtMillisEpoch` | 仅 `RETRY_SCHEDULED` 必填；claim due 选取条件（≤ now）。 |
| `created_at` | bigint | ✓ | ✗ | `createdAtMillisEpoch` | 入队时间（epoch millis）。 |
| `updated_at` | bigint | ✓ | ✓ | `updatedAtMillisEpoch` | 最近状态变更时间。 |
| `last_failure_code` | varchar | ✗ | ✓ | `lastFailureCode`（enum） | `ForwardingFailureCode`；终态 `ACKED` 时为 null。 |
| `lease_owner` | varchar | ✗ | ✓ | `lease.leaseOwner` | **Stage 8 additive**；claim 持有者；null 表示未被 claim。 |
| `lease_until` | bigint | ✗ | ✓ | `lease.leaseUntilMillisEpoch` | **Stage 8 additive**；claim 独占截止时间；过期可被重新 claim。 |

唯一约束：`(tenant_id, message_id)`。
状态 CHECK：`status IN ('PENDING','DISPATCHING','ACKED','RETRY_SCHEDULED','DLQ','EXPIRED')`。

### 3.2 `agent_bus_forwarding_inbox`

接收端去重 / 幂等 / 审计。去重键 `(tenant_id, message_id, consumer_service_id)`。

| Column | Type | Required | Mutable | 来源（record 字段） | 说明 |
|---|---|---|---|---|---|
| `tenant_id` | varchar | ✓ | ✗ | `tenantId` | Rule R-C.c；必须等于 envelope 的 tenantId。 |
| `message_id` | varchar | ✓ | ✗ | `messageId.value` | 与 outbox messageId 对应；去重键组成部分。 |
| `consumer_service_id` | varchar | ✓ | ✗ | `consumerServiceId` | 消费该消息的 service instance；不同 consumer 各自独立去重。 |
| `status` | varchar | ✓ | ✓ | `status`（enum） | `RECEIVED` / `DUPLICATE_SUPPRESSED` / `CONSUMED` / `REJECTED`；CHECK 约束。 |
| `received_at` | bigint | ✓ | ✗ | `receivedAtMillisEpoch` | 首次接收时间。 |
| `consumed_at` | bigint | ✗ | ✓ | `consumedAtMillisEpoch` | 仅 `CONSUMED` 必填。 |
| `failure_code` | varchar | ✗ | ✓ | `failureCode`（enum） | 仅 `REJECTED` / `DUPLICATE_SUPPRESSED` 必填。 |

唯一约束 / 去重键：`(tenant_id, message_id, consumer_service_id)`。
状态 CHECK：`status IN ('RECEIVED','DUPLICATE_SUPPRESSED','CONSUMED','REJECTED')`。

> `idempotency_key`（envelope 字段）**不进** inbox 去重键，降级为审计字段（MI8-004）：去重以 `(tenantId, messageId, consumerServiceId)` 为主。如需保留 `idempotency_key` 供审计，作为 inbox 的可选非键列后续追加，不改去重语义。

## 4. claim / lease 并发语义

替代裸 `findRetryable(now)`（MI8-001）。真实持久化下多个 dispatcher 实例会并发抢同一条消息，必须用 claim / lease 防重复投递：

- **claim tenant-scoped**：`claimDue(tenantId, now, limit, leaseOwner, leaseUntil)` 只在指定 tenant 内选取，跨 tenant 不命中（Rule R-C.c）。
- **单租约**：同一 outbox record 同一时刻最多被一个 `leaseOwner` 持有；在 `lease_until` 之前，其他 owner 的 claim 跳过它。
- **due 条件**：`status = PENDING`（fresh），或 `status = RETRY_SCHEDULED AND next_attempt_at <= now`（retry due），或 `status = DISPATCHING AND lease_until <= now`（stuck holder reclaim）。
- **terminal 不可 claim**：`ACKED` / `DLQ` / `EXPIRED` 永不 claimable。
- **原子性**：claim + 状态迁移（`PENDING`/`RETRY_SCHEDULED → DISPATCHING`，或 reclaim 原地续 lease）必须在同一事务 / 等价 CAS 中完成。JDBC 实现建议 `SELECT ... FOR UPDATE SKIP LOCKED` + `UPDATE ... SET status, lease_owner, lease_until`（Postgres）。
- **续约 / 释放**：`renewLease`（持有人延长）、`releaseLease`（持有人在 terminal 后释放）——只对当前持有人生效，他人持有时 no-op。

`lease_owner` / `lease_until` 是 Stage 8 additive 字段，在 record、schema、ICD 三处一致（§3.1 / [`ICD-Agent-Bus-Forwarding-Runtime`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md) / yaml）。

## 5. dispatcher worker skeleton 与 delivery 端口

`ForwardingDispatcherWorker`（runtime 包，纯 Java）是 claim / deliver / ack / retry 半边生命周期，与 `ForwardingDispatcher`（accept / enqueue 网关角色）分离（MI8-003）。单次同步 tick `runOnce`：

1. `claimDue` 声明到期记录（已原子迁移到 `DISPATCHING` 并 stamped lease）。
2. **Stage 10（MI10-002）/ Stage 11（MI11-001）**：deliver 前按 `DispatchLeasePolicy` 检查剩余 lease TTL——低于阈值则 `claimPort.renewLease(...)` 续约；renew 返回 false（lease 已被 reclaim / 不再 DISPATCHING）则 skip 该 record，不投递。剩余 TTL `remaining = leaseUntilMillisEpoch − clock.epochMillis()` 读注入的 `EpochClock`（真实墙钟），`claimDue` 仍以 tick 起始时刻 `nowMillisEpoch` 为 claim 时刻——真实运行时一个耗时 deliver 接近 lease TTL 时续约能自然触发（Stage 11 修复：此前续约判断用 tick 入参 `nowMillisEpoch`，整个 tick 不变，而自然 dispatch loop 每次 tick 用 `leaseUntil = now + leaseDurationMillis` 构造 → `remaining` 恒定，续约永不触发，只能靠 harness 构造接近过期 leaseUntil 间接覆盖）。
3. 逐条调用抽象 `ForwardingDeliveryPort.deliver(record, clockNow)`，仅消费 `routeHandle`（**不**暴露物理 endpoint）；`clockNow` 取自注入 `EpochClock`（与续约判断同一时刻）。
4. 按 `ForwardingDeliveryResult.outcome()` 路由：`ACKED → markAcked`、`DLQ → moveToDlq`、`EXPIRED → markExpired`；`RETRY_SCHEDULED → 询问注入的 `ForwardingRetryPolicy`（Stage 14，§15）：`exhausted(attemptCount)` 为真则 `moveToDlq`（retryable 码耗尽是合法 DLQ 码），否则 `scheduleRetry`（`nextAttemptAt` 由 policy 计算，`attemptCount` / `lastFailureCode` 一并更新）。
5. **Stage 10（MI10-001）/ Stage 11（MI11-002）**：deliver / mark* 包在 per-record try-catch 中——lease guard 抛 `ForwardingLeaseException`（reclaim / stale / foreign / expired owner），或 `deliver` 违约抛非 lease `RuntimeException`（真实 transport 绑定应把网络 / 超时 / 反序列化异常映射为 `ForwardingDeliveryResult`、不应抛，见 ICD；worker 兜底是防御性）时，均 skip 该 record（留 `DISPATCHING`，lease 过期被 reclaim 重投，**不丢消息**），tick 继续其余 record。`DispatchTickResult(claimed, acked, retried, dlqd, expired, skipped)` 校验 `claimed == acked + retried + dlqd + expired + skipped`，保证计数自洽、可观测。

边界：worker 无线程、无 scheduler、无 registry、无 transport；唯一时间依赖是注入的 `EpochClock`（默认 `System::currentTimeMillis`），用于 lease 续约判断与 deliver 时刻——`claimDue` 仍以 tick 起始时刻为 claim 时刻。真实 polling cadence / threading / backpressure / 具体 delivery 绑定 deferred 后续阶段。worker 不写 Task execution state。异常契约（Stage 11，MI11-003）：`runOnce` 仅在入参非法（`tenantId` / `leaseOwner` blank、`limit <= 0`）时抛 `IllegalArgumentException`（调用方 bug，fail-fast），tick 内 deliver / lease 异常已兜底为 `skipped` 不抛；`ForwardingDispatchLoop.run` 传播 fail-fast 是正确语义（不静默吞调用方 bug，不加 loop 级兜底）。fake delivery（`InMemoryForwardingDelivery`）仅在 test fixture，让 ACK / RETRY / DLQ / EXPIRED 与 lease 续约 / skip / deliver 异常路径在无网络下可被 harness 覆盖。

> **in-memory vs JDBC**：in-memory lease harness 按 owner（不按 `lease_until` 过期）裁决 lease-guarded mutation；故"renew-or-lose-the-ack"（`WHERE lease_until > now`）是 §7.2 的 SQL contract，不在 in-memory 断言。lease 续约的"renew 后超 TTL 仍丢 ack"语义同样由 §7.2 SQL 编码，**真实 adapter 落地后已由 real-SQL 集成测试覆盖**（Stage 12，§14 MI12-004：Zonky embedded-postgres 承载真实 PG 16.2 in-process，见 §7.4）。

### 5.1 dispatch 调度责任（Stage 10，MI10-004）

`runOnce` 是单次 tick；谁驱动循环、idle 退避、并发分片，是调用方（`agent-runtime` 受控路径 / 真实 scheduler）的责任，不进 `agent-bus`。Stage 10 交付纯 Java `ForwardingDispatchLoop` 骨架，把这份责任契约化。（worker 内部 lease 续约用注入 `EpochClock`，见 §5；loop 本身不持 clock，tick 时刻全由 `TickSource` 提供。）

- `TickSource`（注入下一 tick 时刻；`OptionalLong.empty()` 表示停止）——loop 不持有 clock，可被 fixed-rate executor / scheduler bean / 测试驱动。
- `IdleStrategy`（空 tick 退避；`NO_BACKOFF` 为 no-op）——loop 不 sleep，退避策略外部注入。
- 聚合 `DispatchTickResult`（满足与单 tick 相同的自洽不变量）。
- loop 无线程、无 scheduler、无 transport、无 clock——真实 polling cadence / threading / 并发 worker 分片是调用方决策，deferred 后续阶段。claim / lease 已防多 worker 重复投递（§4），分片只减少空 claim。

## 6. MI8 决策（Stage 8 计划 §2 收口）

| MI | 决策 | 落点 |
|---|---|---|
| MI8-001 | due-message 查询升级为 claim / lease 端口，不补裸 `findRetryable(now)` | `ForwardingOutboxClaimPort.claimDue`；L2 §8 已改写；§4 并发语义 |
| MI8-002 | `sourceServiceId` / `targetServiceId` 在 record（gateway 写入），不进 envelope | `ForwardingOutboxRecord`；`enqueue` / `dispatch` 签名带 source/target |
| MI8-003 | 拆清 accept/enqueue（`ForwardingDispatcher`）与 claim/deliver/ack/retry（`ForwardingDispatcherWorker`） | dispatcher javadoc 修正 + worker 新增 |
| MI8-004 | `idempotencyKey` 降级为审计字段；去重键不变 `(tenantId, messageId, consumerServiceId)` | §3.2 注；ICD / L2 §5 同步 |
| MI8-005 | C3 最终确认为 `adopted-c3`；DB 产品 / migration 归属延期 Stage 9+ | decision.md；本文档 §2 护栏 |

## 7. Postgres DDL

> **Stage 12（MI12-003）已执行**：DDL 落地为真实 Flyway migration `agent-bus/src/main/resources/db/migration/V1__create_agent_bus_forwarding_outbox_inbox.sql`（含 MI9-006 全部条件 CHECK + `ix_outbox_claim_due` 部分索引 + §7.3 RLS，迁移顺序遵循 §7.3）。以下代码块是同一 DDL 的设计态镜像——Stage 8-11 为「未执行草稿」，Stage 12 后为已执行 migration 的文档副本；列定义 / CHECK / 索引与 `V1` migration 逐字一致（RLS 段见 §7.3）。

```sql
-- agent_bus_forwarding_outbox —— 发送端 durable queue
CREATE TABLE agent_bus_forwarding_outbox (
    tenant_id         VARCHAR(128)  NOT NULL,
    message_id        VARCHAR(128)  NOT NULL,
    source_service_id VARCHAR(128)  NOT NULL,
    target_service_id VARCHAR(128)  NOT NULL,
    route_handle      VARCHAR(512)  NOT NULL,
    payload_ref       VARCHAR(1024),
    status            VARCHAR(32)   NOT NULL,
    attempt_count     INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at   BIGINT,
    created_at        BIGINT        NOT NULL,
    updated_at        BIGINT        NOT NULL,
    last_failure_code VARCHAR(32),
    lease_owner       VARCHAR(128),
    lease_until       BIGINT,
    CONSTRAINT pk_outbox PRIMARY KEY (tenant_id, message_id),
    CONSTRAINT ck_outbox_status CHECK (
        status IN ('PENDING','DISPATCHING','ACKED','RETRY_SCHEDULED','DLQ','EXPIRED')),
    -- MI9-006: condition-field CHECK constraints mirror the Java record
    -- invariants (MI9-003) at the DB layer, so a buggy adapter cannot persist
    -- an illegal row even if it bypasses the constructor.
    CONSTRAINT ck_outbox_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_lease_paired CHECK (
        (lease_owner IS NULL AND lease_until IS NULL)
        OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL)),
    CONSTRAINT ck_outbox_retry_has_next_attempt CHECK (
        status <> 'RETRY_SCHEDULED' OR next_attempt_at IS NOT NULL),
    CONSTRAINT ck_outbox_failure_code CHECK (
        (status = 'ACKED' AND last_failure_code IS NULL)
        OR (status IN ('DLQ','EXPIRED','RETRY_SCHEDULED') AND last_failure_code IS NOT NULL)
        OR (status IN ('PENDING','DISPATCHING'))),
    CONSTRAINT ck_outbox_lease_status CHECK (
        (status IN ('PENDING','ACKED','RETRY_SCHEDULED','DLQ','EXPIRED') AND lease_owner IS NULL)
        OR (status = 'DISPATCHING' AND lease_owner IS NOT NULL))
);

-- claim due: 非终态 + due + 未被有效 lease 持有；Postgres SKIP LOCKED 防多实例重复投递
CREATE INDEX ix_outbox_claim_due
    ON agent_bus_forwarding_outbox (tenant_id, next_attempt_at)
    WHERE status IN ('PENDING', 'RETRY_SCHEDULED');

-- agent_bus_forwarding_inbox —— 接收端去重 / 审计
CREATE TABLE agent_bus_forwarding_inbox (
    tenant_id          VARCHAR(128) NOT NULL,
    message_id         VARCHAR(128) NOT NULL,
    consumer_service_id VARCHAR(128) NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    received_at        BIGINT       NOT NULL,
    consumed_at        BIGINT,
    failure_code       VARCHAR(32),
    CONSTRAINT pk_inbox PRIMARY KEY (tenant_id, message_id, consumer_service_id),
    CONSTRAINT ck_inbox_status CHECK (
        status IN ('RECEIVED','DUPLICATE_SUPPRESSED','CONSUMED','REJECTED')),
    -- MI9-006: condition-field CHECK constraints mirror the Java record (MI9-003)
    CONSTRAINT ck_inbox_consumed_at CHECK (
        status <> 'CONSUMED' OR consumed_at IS NOT NULL),
    CONSTRAINT ck_inbox_failure_code CHECK (
        (status IN ('REJECTED','DUPLICATE_SUPPRESSED') AND failure_code IS NOT NULL)
        OR (status IN ('RECEIVED','CONSUMED') AND failure_code IS NULL)),
    CONSTRAINT ck_inbox_dup_code CHECK (
        status <> 'DUPLICATE_SUPPRESSED' OR failure_code = 'duplicate_suppressed')
);
```

> 列宽 / 索引策略是草案；真实实现按实际值域与查询模式定。`SKIP LOCKED` 是 Postgres 9.5+ / MySQL 8.0+ 行为；如选用其它产品需等价并发抢占原语（advisory lock / `SELECT ... FOR UPDATE`）。

### 7.1 claim SQL（lease stamping, Postgres SKIP LOCKED）

`claimDue` 的等价 SQL（MI8-001）：原子地选出 due、tenant-scoped、非终态、lease 空闲/过期的记录，迁移到 `DISPATCHING` 并 stamped 独占 lease。`FOR UPDATE SKIP LOCKED` 让并发 dispatcher 跳过已被同伴锁住的行，不阻塞。

```sql
UPDATE agent_bus_forwarding_outbox AS o
SET status      = 'DISPATCHING',
    lease_owner = :leaseOwner,
    lease_until = :leaseUntil,
    updated_at  = :now
WHERE (o.tenant_id, o.message_id) IN (
    SELECT tenant_id, message_id
    FROM agent_bus_forwarding_outbox
    WHERE tenant_id = :tenantId
      AND ( (status = 'PENDING')
            OR (status = 'RETRY_SCHEDULED' AND next_attempt_at <= :now)
            OR (status = 'DISPATCHING' AND lease_until <= :now) )  -- stuck-holder reclaim
    ORDER BY next_attempt_at NULLS FIRST
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
)
RETURNING o.*;
```

### 7.2 lease-owner guarded state-update SQL

`markAcked` / `scheduleRetry` / `moveToDlq` / `markExpired` 的状态变更都必须被 lease-owner 守卫（MI9-001）：只有当前持有有效 `DISPATCHING` lease 的 owner 才能改状态。`WHERE` 子句编码守卫，0 行受影响即代表 stale/foreign/expired owner，adapter 抛 `ForwardingLeaseException`（record 不被改动）。ACK 为例（terminal 清 lease，MI9-002）：

```sql
UPDATE agent_bus_forwarding_outbox
SET status            = 'ACKED',
    last_failure_code = NULL,
    lease_owner       = NULL,     -- MI9-002: terminal clears the lease
    lease_until       = NULL,
    updated_at        = :now
WHERE tenant_id   = :tenantId
  AND message_id  = :messageId
  AND status      = 'DISPATCHING'
  AND lease_owner = :leaseOwner
  AND lease_until > :now;
-- scheduleRetry / moveToDlq / markExpired share the identical WHERE guard.
-- RETRY additionally: next_attempt_at = :nextAttemptAt, attempt_count + 1,
-- last_failure_code = :retryableCode, lease_owner/lease_until = NULL (MI9-002).
```

> **releaseLease 在 JDBC adapter 的过期语义差异（Stage 12 实施发现）**：`ck_outbox_lease_status` 强制 `DISPATCHING` 行携带非空 `lease_owner`。in-memory 替身的 `releaseLease` 清空 lease 字段（对它内部可变行合法），但落到 DB 会违反该 CHECK。故 JDBC adapter 的 `releaseLease` 改为**过期 lease**（`lease_until = -1`，保留 `lease_owner` 使行 CHECK-valid），使其立即落入 §7.1 `lease_until <= now` 的 stuck-holder reclaim 路径。行为契约（release ⇒ 他人可 reclaim；releaser 不再能 mutate）不变，仅存储表示不同；编码于 `JdbcForwardingOutbox.releaseLease`，real-SQL 测试 `release_lease_expires_it_so_another_owner_can_reclaim` 覆盖（§14 MI12-004）。

### 7.3 RLS（Postgres Row-Level Security）

若采用 Postgres 且启用 RLS 做 tenant 行级隔离：

- **何时启用**：在两张表上启用 RLS，policy 按 `tenant_id = current_setting('app.tenant_id')` 过滤；`app.tenant_id` 由应用连接设置。
- **迁移顺序**：先建表 + 数据 → 再 `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` + `CREATE POLICY`；应用层仍带 `tenantId` 过滤（纵深防御，RLS 不替代应用层隔离）。
- **回滚**：`DROP POLICY` + `ALTER TABLE ... DISABLE ROW LEVEL SECURITY`；不丢数据。
- **是否启用**：**Stage 12 已裁决启用且已落地**（Postgres RLS 纵深防御：应用层 `WHERE tenant_id=?` 主路径 + DB 层 RLS 兜底，R-C.c 硬隔离）；迁移顺序遵循本节（先建表 → 再 `ENABLE ROW LEVEL SECURITY` + `CREATE POLICY`），已落地于 `V1` migration 末尾，回滚不丢数据。real-SQL 测试 `rls_policy_filters_rows_by_session_tenant_setting` 覆盖（含 fail-closed：未设 `app.tenant_id` session 不可见任何行）。

### 7.4 real-SQL 验证：embedded-postgres（Stage 12，MI12-004）

§5 / §14 MI12-004 兑现的「真实 adapter 落地后补 real-SQL 集成验证」由 **Zonky embedded-postgres**（`io.zonky.test:embedded-postgres:2.0.7` + `embedded-postgres-binaries-linux-arm64v8:16.2.0`）承载，**非 Testcontainers**：

- **原因（实施发现）**：执行环境的 Docker daemon 对所有 registry（Docker Hub + 内网 SWR 镜像源）走需认证代理（HTTP 407），host 无 sudo 重配 daemon、无本地 PG on 5432——Docker 路径在此环境不可用（计划 §6 预案的 embedded 退路）。
- **等价保真**：embedded-postgres 跑真实 PostgreSQL 16.2 二进制（aarch64）in-process，与 Testcontainers 同一 PG 引擎——`FOR UPDATE SKIP LOCKED`、RLS、部分索引、CHECK、`ON CONFLICT` 行为一致。
- **可下载性**：平台二进制是普通 Maven artifact，经 `~/.m2/settings.xml` 认证代理解析（已验证 14 MB arm64 binary 下载成功）。Zonky 默认仅声明 amd64/darwin/windows，Linux aarch64 需显式声明 `embedded-postgres-binaries-linux-arm64v8`（否则 "Missing embedded postgres binaries"）；x86_64 CI 同理加 `-linux-amd64`。
- **覆盖**：`ForwardingJdbcIntegrationTest`（17 tests）覆盖 6 类 MI12-004 + migration + RLS。生产环境（可达 Docker / 外部 PG）可换回 Testcontainers——adapter 与 migration 不依赖测试载体。

## 8. migration / rollback 说明

- **归属未定**：`agent-bus` 当前无 Flyway。Stage 9+ 确认数据库产品与 migration 归属（agent-bus 自有 vs 共享 schema 模块 vs runtime 受控路径）后，再决定 Flyway / Liquibase / 手工 SQL。
- **版本与命名**：遵循所选 migration 工具约定（如 Flyway `V<n>__create_agent_bus_forwarding_outbox.sql`）；本草案不是最终文件名。
- **向前兼容**：`lease_owner` / `lease_until` 为 nullable additive 列，旧读写不受影响（yaml `compatibility.additive_fields_allowed: true`）。
- **回滚**：`DROP TABLE agent_bus_forwarding_inbox, agent_bus_forwarding_outbox`（回滚后 outbox/inbox 能力消失，回到 in-memory 替身；不影响其它模块）。
- **breaking change**：字段重命名 / 去除必填列 / 改唯一键属 breaking，需 ADR / review packet（yaml `compatibility.breaking_change_requires_adr: true`）。

## 9. 字段一致性表（ICD ↔ schema ↔ Java record ↔ DDL）

| ICD outbox 字段 | yaml key | Java `ForwardingOutboxRecord` | DDL column |
|---|---|---|---|
| tenantId | outbox_record.required.tenantId | tenantId | tenant_id |
| messageId | outbox_record.required.messageId | messageId | message_id |
| sourceServiceId | outbox_record.required.sourceServiceId | sourceServiceId | source_service_id |
| targetServiceId | outbox_record.required.targetServiceId | targetServiceId | target_service_id |
| routeHandle | outbox_record.required.routeHandle | routeHandle | route_handle |
| payloadRef（条件） | outbox_record.conditional.payloadRef | payloadRef | payload_ref |
| status | outbox_record.required.status | status | status |
| attemptCount | outbox_record.required.attemptCount | attemptCount | attempt_count |
| nextAttemptAt（条件） | outbox_record.conditional.nextAttemptAt | nextAttemptAtMillisEpoch | next_attempt_at |
| createdAt | outbox_record.required.createdAt | createdAtMillisEpoch | created_at |
| updatedAt | outbox_record.required.updatedAt | updatedAtMillisEpoch | updated_at |
| lastFailureCode（条件） | outbox_record.conditional.lastFailureCode | lastFailureCode | last_failure_code |
| —（Stage 8 additive） | lease（见 yaml stage8） | lease | lease_owner / lease_until |

inbox 字段同理一一对应（`consumerServiceId → consumer_service_id`、`receivedAt → received_at`、`consumedAt → consumed_at`、`failureCode → failure_code`）。

harness（`AgentBusForwardingRuntimeContractTest`）结构化校验 `ForwardingOutboxRecord` 的 record 组件名，ICD 文本与 yaml 字段由 contract test 镜像，三者漂移即构建失败。

## 10. DoD 自检

- ✓ record 模型补齐（`ForwardingOutboxRecord` / `ForwardingInboxRecord` / `ForwardingLease`），承载 ICD 必填字段（§3）。
- ✓ claim / lease 端口清晰（`ForwardingOutboxClaimPort`），替代 `findRetryable(now)`（§4）。
- ✓ 两张表 schema + DDL 草案 + migration / rollback 说明（§3 / §7 / §8）。
- ✓ dispatcher worker skeleton + 抽象 delivery 端口 + fake delivery 测试（§5）。
- ✓ MI8-001..005 收口（§6）。
- ✓ 无 concrete broker / MQ client；无 JDBC driver；无 Task execution state 写入；不绕 routeHandle；不放 payload body（§2 + harness 强制）。
- ✓ 未引入生产数据库依赖，停在 schema 草案 + repository port + in-memory lease harness（§2 护栏）。

## 11. Stage 9 决策（lease-safe / persistence-ready）

Stage 9（MI9-001..006）在 Stage 8 的持久化准备之上补齐**并发安全**与**约束完整性**，仍**不引入**真实 DB / migration——DB / migration 归属未由人类确认，按 Stage 9 计划 §5「路径 B」执行：交付完整 DDL CHECK 约束 + claim / state-update SQL contract + in-memory lease harness，JDBC / Flyway 不进 `agent-bus`。

| MI | 决策 | 落点 |
|---|---|---|
| MI9-001 | DISPATCHING → terminal/retry 状态变更全部升级为 lease-owner guarded mutation；`markDispatching` 移除（claim 是进入 DISPATCHING 的唯一路径） | `ForwardingOutboxPort` 四方法带 `leaseOwner`；`ForwardingLeaseException`（RECORD_NOT_FOUND/NO_LEASE/OWNER_MISMATCH/NOT_DISPATCHING）；in-mem `leaseGuardedMutate`；§7.2 SQL |
| MI9-002 | lease 生命周期闭环：ACKED/DLQ/EXPIRED/RETRY_SCHEDULED 清 lease；仅 DISPATCHING 持 lease | in-mem `leaseGuardedMutate` 清 lease；record 不变量；DDL `ck_outbox_lease_status` |
| MI9-003 | record 条件不变量固化到 Java 构造器 / schema / DDL CHECK / harness | `ForwardingOutboxRecord.validateStatusInvariants`、`ForwardingInboxRecord.validateStatusInvariants`；§7 CHECK；harness 非法构造测试 |
| MI9-004 | failure-code classification（retryable/non-retryable/dedup）；`retry(...)` 只接 retryable，`dlq(...)` 拒 dedup | `ForwardingFailureCode.Classification`；`ForwardingDeliveryResult` 构造校验；`scheduleRetry`/`moveToDlq` |
| MI9-005 | `targetServiceId` 来自 discovery metadata / gateway audit，不解包 routeHandle | `ForwardingOutboxRecord` javadoc；`enqueue` javadoc；yaml `owner: discovery-via-routeHandle` |
| MI9-006 | DDL 草案补完整条件 CHECK 约束 | §7 DDL CHECK；SQL contract harness 锁定 |

Stage 9 DoD 自检：

- ✓ lease-owner guarded mutation：stale worker（lease 被 reclaim）ACK 失败为 OWNER_MISMATCH（harness 覆盖）。
- ✓ lease 生命周期闭环：terminal + retry 清 lease，仅 DISPATCHING 持 lease（harness 覆盖）。
- ✓ record 条件不变量进入 Java 构造器 + DDL CHECK + harness（非法构造失败测试）。
- ✓ failure-code 分类明确，retry/dlq 路由在构造期被分类约束（harness 覆盖）。
- ✓ DB / migration 归属：路径 B——归属未由人类确认，不引入 JDBC / Flyway；交付完整 DDL CHECK 约束 + claim / state-update SQL contract + in-memory lease harness。
- ✓ 未引入生产数据库依赖；DDL / SQL 仍标注为 contract / draft（§2 护栏不变）。

## 12. Stage 10 决策（dispatch-loop runtime）

Stage 10（MI10-001..005）把 Stage 9 的 lease-safe 底座接入 worker 运行态，使 dispatcher worker 从 skeleton 推进为「正确处理 lease 生命周期」的可运行 dispatch loop；DB / migration 归属经人类再确认为 **路径 B**（不引入 JDBC / Flyway；DDL / SQL 仍 contract / draft）。真实持久化（JDBC adapter / Flyway / 真实投递绑定）deferred 后续阶段，独立批次处理。

| MI | 决策 | 落点 |
|---|---|---|
| MI10-001 | per-record try-catch `ForwardingLeaseException` → skip（兑现 `ForwardingLeaseException` javadoc 的 skip 承诺），tick 不因 lease 竞态中断；`DispatchTickResult` 增 `skipped` 并校验 `claimed == acked + retried + dlqd + expired + skipped` | `ForwardingDispatcherWorker.runOnce`（try-catch + skipped）；`DispatchTickResult` 6 字段 + 自洽校验；`worker_skips_record_when_lease_reclaimed_mid_tick` |
| MI10-002 | lease 续约契约：deliver 前按 `DispatchLeasePolicy`（renewBeforeExpiryMillis / leaseExtensionMillis）检查剩余 TTL，不足则 `renewLease`；renew 返回 false 同 skip；不续约且超 TTL 则 ack 失败为 lease guard 拒绝 | `DispatchLeasePolicy` record；`runOnce` deliver 前续约；`worker_renews_short_lease_before_delivery` / `worker_does_not_renew_when_lease_sufficient` / `worker_skips_when_lease_renew_fails` |
| MI10-004 | dispatch 调度责任定义：`ForwardingDispatchLoop` 骨架（`TickSource` / `IdleStrategy` 注入，无 clock / scheduler / 线程）；`runOnce` 调用契约写入 §5.1 / javadoc | `ForwardingDispatchLoop`；`dispatch_loop_drives_ticks_from_injected_source_until_it_stops` |
| MI10-005 | DB / migration 归属经人类再确认为 **路径 B** | 本文档 §2 / §11；decision.md §8；plan §7；不引入 JDBC / Flyway |

> MI10-003（`DispatchTickResult` 可观测）由 MI10-001 的 `skipped` 字段 + 自洽校验收口，不单列行为测试。

Stage 10 DoD 自检：

- ✓ worker lease 异常恢复：reclaimed record 不中断 tick，计入 skipped（harness 覆盖）。
- ✓ lease 续约：长 deliver 前按阈值 renew；renew 失败 / 不续约超 TTL 明确失败（harness 覆盖）。
- ✓ `DispatchTickResult` 可观测：skipped + 计数自洽（构造校验 + harness）。
- ✓ dispatch 调度责任：`ForwardingDispatchLoop` 无 scheduler / 线程，trigger 注入，harness 覆盖。
- ✓ DB / migration 归属：路径 B 再确认（人类裁决，Stage 10 切片 4）；不引入 JDBC / Flyway；DDL / SQL 仍 contract / draft。

## 13. Stage 11 决策（runtime-completion：lease 续约时机 / deliver 异常兜底 / 异常契约）

Stage 11（MI11-001..003）修复 Stage 10 dispatch-loop runtime 暴露的三个运行态裂缝——它们都是接真实 DB / transport 前的必要前置（若不先修，接了 JDBC adapter / 真实投递绑定也只是把缺陷物理化）。主轴经人类确认为**运行态完善批次**，**保持路径 B**（不引入 JDBC / Flyway / transport）；真实持久化 / 真实投递绑定 / agent-runtime 集成仍 deferred Stage 12+。

| MI | 决策 | 落点 |
|---|---|---|
| MI11-001 | lease 续约触发时机从「tick 入参 `leaseUntil − now`（恒满租期、自然 loop 下永不触发）」改为「基于注入 `EpochClock` 的真实墙钟」：`remaining = leaseUntilMillisEpoch − clock.epochMillis()`；`claimDue` 仍用 tick 起始时刻作 claim 时刻 | `EpochClock`（`forwarding/runtime`，纯 Java 端口，默认 `System::currentTimeMillis`）；worker 五参构造器注入；`runOnce` 续约判断 + deliver 时刻读 clock；§5；改写续约 harness（注入可控时钟替代构造接近过期 leaseUntil） |
| MI11-002 | `deliver` 非 lease `RuntimeException` 兜底为 `skipped`（record 留 `DISPATCHING` 待重投，不丢消息），tick 不中断；契约主路径仍是「`deliver` 不抛非 lease 异常，底层异常由真实适配器映射为 `ForwardingDeliveryResult`（`delivery_timeout` / `receiver_unavailable` 等）」，worker 兜底是防御性 | `runOnce` deliver 内层 try-catch `RuntimeException` → skipped；`ForwardingDeliveryPort.deliver` javadoc + ICD 契约；`worker_skips_record_when_delivery_throws` |
| MI11-003 | `runOnce` 异常契约：仅入参非法（blank tenant / lease owner、`limit <= 0`）抛 `IllegalArgumentException`（调用方 bug fail-fast），tick 内 deliver / lease 异常已兜底为 `skipped` 不抛；`ForwardingDispatchLoop.run` 传播 fail-fast 是正确语义，**不加** loop 级 tick 异常兜底（过度兜底会掩盖调用方 bug） | `runOnce` javadoc `@throws IllegalArgumentException`；§5 边界段异常契约；`run_once_fails_fast_on_blank_tenant_and_loop_propagates` |

Stage 11 DoD 自检：

- ✓ lease 续约时机基于注入 `EpochClock`（真实墙钟），自然 loop 驱动下能基于 deliver 耗时触发；in-memory 注入时钟覆盖 renew / 不 renew / renew 失败（harness 覆盖）。
- ✓ `deliver` 非 lease 异常兜底为 skipped（record 留 DISPATCHING 待重投），tick 不中断；契约 deliver 不抛写入 ICD（harness 覆盖）。
- ✓ `runOnce` 异常契约明确（入参非法 fail-fast），loop 传播是正确语义（harness 覆盖）。
- ✓ 136 tests green（`AgentBusForwardingRuntimeContractTest` 34 → 36，+2 Stage 11 行为测试）；ArchUnit 纯度仍 green（`EpochClock` 用 `System::currentTimeMillis` 不在禁止列表）。
- ✓ 路径 B 不变：不引入 JDBC / Flyway / transport / scheduler；DDL / SQL 仍 contract / draft。

## 14. Stage 12 决策（真实持久化：打破路径 B）

Stage 12（MI12-001/002/003/004/006）打破路径 B，正式启动真实持久化：H2/H3 裁决 4 项选型，落地 Postgres JDBC adapter（Spring JDBC）+ Flyway migration + Testcontainers + RLS。**transport / 真实投递绑定拆出 Stage 12 单独议**（独立 H2/H3 议题，见 [`decision §8 transport 议题段`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)）。

| MI | 决策 | 落点 |
|---|---|---|
| MI12-001 | H2/H3 裁决 4 项：① DB = **Postgres**；② migration·adapter 归属 = **agent-bus 自有 + Spring JDBC**；③ transport = **拆出 Stage 12 单独议**；④ RLS = **启用纵深防御** | [`decision §4`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)（许可范围扩展 JDBC / Flyway / Spring JDBC）/ `§6.1`（「不引入 JDBC」约束解除，加注）/ `§8`（Stage 12 行 + transport 议题段）；本文 §2（护栏更新注）/ §7.3（RLS 已裁决启用）/ §14 |
| MI12-002 | 真实 Postgres JDBC adapter（Spring `JdbcTemplate` + 注入 `DataSource`）：claim 走 §7.1 `FOR UPDATE SKIP LOCKED RETURNING`、状态变更走 §7.2 lease-owner guarded `WHERE`（0 行 → `ForwardingLeaseException` 分类 RECORD_NOT_FOUND / NO_LEASE / OWNER_MISMATCH / NOT_DISPATCHING）、reclaim 走 `lease_until <= now`、renew / release 只对当前持有人；adapter 调 `ForwardingStateMachine` 校验迁移后再 persist；tenant 行级显式 `tenant_id=?` + RLS 纵深防御 | `agent-bus` 新增 JDBC adapter 实现 `ForwardingOutboxPort` / `ForwardingOutboxClaimPort`（+ inbox）；in-memory 替身保留为 fast test double |
| MI12-003 | Flyway migration 落地：`agent-bus/src/main/resources/db/migration/V<n>__create_agent_bus_forwarding_outbox_inbox.sql`，含 MI9-006 全部条件 CHECK + `ix_outbox_claim_due` + RLS（§7.3 顺序） | §7 DDL 草案 → 真实 migration（归属 = agent-bus 自有） |
| MI12-004 | real-SQL 验证（**实际由 embedded-postgres 承载**，非 Testcontainers——Docker daemon 经认证代理不可达，见 §7.4）：并发 claim 无重复（`SKIP LOCKED`）/ lease guard（stale ACK → 0 行 / `ForwardingLeaseException` 分类）/ reclaim（过期被第二 worker 抢）/ renew-or-lose-ack（§7.2）/ releaseLease 过期语义（§7.2）/ CHECK 兜底 / tenant 隔离（含 §7.3 RLS fail-closed）；兑现 §5「真实 adapter 落地后补 real-SQL 集成验证」 | `ForwardingJdbcIntegrationTest`（17 tests green，Zonky embedded-postgres PG 16.2，§7.4） |
| MI12-006 | pom 引入 `spring-boot-starter-jdbc` + Postgres driver + Flyway（production）+ Testcontainers Postgres（test）；ArchUnit 纯度规则**精确化**（允许 JDBC / Flyway / Spring JDBC，仍禁 concrete broker / MQ client + Task execution state）；ContractTest 纯度断言从「禁 `java.sql.`」改为「禁 concrete broker client + Task state」—— 护栏从「禁一切外部依赖」精确化为「禁 broker 绑定 + Task state 越权」，**不放松 §6.2** | `agent-bus/pom.xml`；ArchUnit 规则；`AgentBusForwardingRuntimeContractTest` 纯度断言 |

Stage 12 DoD 自检：

- ✓ 4 项选型裁决落位（Postgres + agent-bus 自有 Spring JDBC + RLS；transport 拆出）。
- ✓ JDBC adapter 实现 outbox / claim（+ inbox），claim / lease-guarded mutation / reclaim / renew / release 走真实 SQL（`JdbcForwardingOutbox` / `JdbcForwardingInbox` / `ForwardingSqlCodec`）。
- ✓ Flyway migration 落地 `V1__create_agent_bus_forwarding_outbox_inbox.sql`（DDL CHECK + 索引 + RLS）。
- ✓ real-SQL 验证 17 tests green（embedded-postgres PG 16.2，§7.4），覆盖 6 类 MI12-004 + migration + RLS（含 §7.2 releaseLease 过期语义）。
- ✓ pom 引入 JDBC / Flyway / embedded-postgres（+ arm64v8 平台二进制）；ArchUnit 护栏精确化（允许 JDBC 限于 `persistence.jdbc` 子包，禁 broker / Task state）。
- ✓ **153 tests green**（Stage 11 的 136 + real-SQL 17），ArchUnit 纯度 green。
- ✓ **不引入** concrete broker / MQ（transport 拆出）；**不写** Task execution state；**不**跨 tenant fallback；**不放** payload body（`§6.2` 始终不得，不变）。
- ✓ Stage 12 实现完成（切片 0 裁决 + 1 依赖 + 2 adapter + 3 migration + 4 real-SQL + 5/6 文档/提交）；transport / 真实投递绑定 / agent-runtime 集成 deferred 独立议题。

## 15. Stage 14 决策（deliver 重投策略先行：retry policy 端口）

Stage 14（无 MI 编号，对应 Stage 13 拆出的「可独立于投递模型先行」子项）把 deliver 异常重投的治理从 `ForwardingDeliveryPort.deliver`（交付动作）中分离，落到独立的 `ForwardingRetryPolicy` 端口（重试治理）。**独立于 Stage 13 未裁决的 transport 模型**（push / pull / MQ 不影响重投时机——retryable 失败无论下一次由 dispatcher push 还是 receiver pull，都排一个更晚的到期时刻）；**不引入** transport / broker / scheduler，**不破 §6.2**。

| 关注点 | Stage 14 前 | Stage 14 |
|---|---|---|
| 交付是否到达 receiver | `ForwardingDeliveryResult` | 不变 |
| 重投时机（when） | `ForwardingDeliveryResult.nextAttemptAtMillisEpoch`（delivery binding 决定） | `ForwardingRetryPolicy.nextAttemptAt(...)`（policy 决定） |
| 重投耗尽（give up） | 隐含在 delivery binding | `ForwardingRetryPolicy.exhausted(attemptCount)` |

### 15.1 `ForwardingRetryPolicy` 端口

纯 Java 接口（`forwarding/runtime`，与 worker 同包，无需 import），两个方法 + 一个默认实现，照 `DispatchLeasePolicy` / `EpochClock` 的注入范式（构造器链式 + DEFAULT 静态实例）：

- `long nextAttemptAt(ForwardingFailureCode code, int attemptCount, long nowMillisEpoch)`——下一次次到期时刻（写入 outbox `nextAttemptAt`，被下一次 `claimDue` 门控）。`code` 保留供未来 per-code 退避（如 `backpressure_rejected` 退避更久），默认实现仅依赖 `attemptCount`。
- `boolean exhausted(int attemptCount)`——重试预算是否耗尽；`true` 则 worker 路由到 DLQ（非 RETRY）。
- `ForwardingRetryPolicy.DEFAULT = new ExponentialBackoff(100ms base, 60s cap, maxAttempts=5, 无 jitter)`。

`ExponentialBackoff` record（不可变值类型；canonical constructor 校验 `baseMillis>0` / `capMillis>=baseMillis` / `maxAttempts>=0` / `jitterMillis` 非空，错误配置在构造期而非首次重投暴露）：

- **退避公式**：`delay = min(cap, base << shift) + jitter`，其中 `shift = min(attemptCount, 62 − highestSetBit(base))`。
- **overflow-safe**：固定 shift 上限 62 对多 bit base 是错的——`100 << 62` 把 100 的所有位（最高位 bit 6）移出 64-bit `long` 得 `0`（非负数），逃过符号检查，被 `min(0, cap)=0`。动态上限 `62 − (63 − Long.numberOfLeadingZeros(baseMillis))` 保证 `base << shift` 恒正；任意 ≥ cap 的值 clamp 到 cap，runaway `attemptCount` 永不产生负 / 零 / 荒谬 delay。不用 `Math.pow`（`double`→`long` 强转会静默溢出）。
- **可测 jitter**：`LongSupplier` 注入——测试传固定值观测精确 `now + delay + jitter`，生产传有界随机源；负 jitter clamp 到 `0`。

### 15.2 worker RETRY 分支接入

`ForwardingDeliveryResult` 移除第三字段 `nextAttemptAtMillisEpoch`（重投时机不再归 delivery binding）；工厂 `retry(code)` 简化为单参（只接 retryable 码，MI9-004 不变）。worker 在 `RETRY_SCHEDULED` 分支：

```text
retryPolicy.exhausted(record.attemptCount()) ?
    outboxPort.moveToDlq(...)            // retryable 耗尽 → DLQ（retryable 码是合法 DLQ 码）
  : outboxPort.scheduleRetry(..., retryPolicy.nextAttemptAt(retryCode, record.attemptCount(), clockNow))
```

`record.attemptCount()` 是已记录重试次数（`scheduleRetry` 内部 +1）；`exhausted(attempt) = attempt >= maxAttempts`；`maxAttempts` = 最大重试次数（不含首次投递），`0` 表示任何失败直接 DLQ。`nextAttemptAt` 用 deliver 时刻 `clockNow`（注入 `EpochClock`，与续约判断同一时刻，MI11-001）。worker 新增第 6 参构造器注入 `retryPolicy`，3/4/5 参构造器委托到 6 参并用 `ForwardingRetryPolicy.DEFAULT`，现有调用不破坏。

> outbox record 的 `nextAttemptAtMillisEpoch`（DB 列 `next_attempt_at`，§3.1）**仍是** persisted 字段——它是 policy 决策的存储；与 delivery result 解耦（重投时机归 policy）是两个独立关注点。`nextAttemptAt` 字段的「条件必填（仅 RETRY_SCHEDULED）」语义不变。

### 15.3 `ForwardingCircuitBreaker` 端口（Stage 14 deferred → Stage 16 已接入，见 §17）

Stage 14 另落一个 deferred 端口 `ForwardingCircuitBreaker`（`allowsDelivery(routeHandle)` + `ALWAYS_CLOSED` no-op），**不接入 worker**：真实熔断需 per-`routeHandle` 失败率状态，且其形态依赖 Stage 13 未裁决的 transport 模型——push（T1/T2）需 breaker 主动短路故障 route；consumer-pull（T3）天然自调速（receiver 停止 claim 即是其 backpressure / break），显式 breaker 大体冗余。接入前会 bake in transport 假设，故 deferred 至 H2/H3 transport 裁决。

> **Stage 16 更新（2026-06）**：该 deferred 已由 Stage 16 解除 —— Stage 15 真实投递绑定 PoC 选了 T1（dispatcher-push over sync RPC），push 模型需 breaker 主动短路故障 route，故「形态依赖 transport」的悬挂前提落地；`ForwardingCircuitBreaker` 已接入 worker（`RouteCircuitBreaker` 三态机），详见 §17。**§6.2 始终不得项不变**（breaker 纯 JDK，无 Task state / payload body / concrete broker client）。

Stage 14 DoD 自检：

- ✓ `ForwardingRetryPolicy` 端口 + `ExponentialBackoff` 默认实现（纯 Java，forwarding 纯度合规）。
- ✓ overflow-safe 指数退避（动态 shift 上限，clamp cap）；可测 jitter（注入 `LongSupplier`，负值 clamp 0）。
- ✓ worker RETRY 分支接入：`exhausted` → DLQ，否则 `nextAttemptAt` → `scheduleRetry`；`ForwardingDeliveryResult.retry(code)` 简化（移除 `nextAttemptAtMillisEpoch` 字段）。
- ✓ 熔断端口 deferred（`ALWAYS_CLOSED` no-op，未接入 worker，待 transport 裁决）。
- ✓ **158 tests green**（Stage 12 的 153 + 5 个 Stage 14 行为测试），ArchUnit 纯度 green。
- ✓ §6.2 不变：不引入 transport / broker / scheduler；不写 Task state；不跨 tenant fallback；不放 payload body。

## 16. Stage 15 决策（真实投递绑定 PoC：A2A transport adapter）

Stage 15（无 MI 编号，对应 Stage 14 评审后用户提出的「确认 C3 转发能否与 main 分支的 agent-runtime 对接」诉求）落地 `ForwardingDeliveryPort.deliver` 的**真实 transport 实现** —— 此前一直用 `InMemoryForwardingDelivery` fake，从未证明 agent-bus 能真把一条消息投递到远程 agent-runtime。PoC 用 A2A HTTP transport adapter 证明对接在技术上可行，**不裁决** Stage 13 的 push / pull / MQ 哲学张力（仍 H2/H3）。**`§6.1` 第 4 项「真实投递绑定 deferred」由 Stage 15 解除**；**`§6.2` 始终不得项不变**（A2A 是 HTTP JSON-RPC，非 concrete broker / MQ）。

### 16.1 `A2aForwardingDeliveryPort`（A2A SDK `JSONRPCTransport`）

落地于 `transport.a2a` 子包（A2A SDK 圈进该子包，与 Stage 12 Spring / JDBC 圈进 `persistence.jdbc` 同范式），照搬 main 分支 `A2aRemoteAgentOutboundAdapter` 的 streaming 模式：

- **per-endpoint `JSONRPCTransport` 缓存**（`ConcurrentHashMap`，`computeIfAbsent`）—— 照 `A2aRemoteAgentOutboundAdapter.obtainTransport`；PoC 不处理 endpoint churn / close-on-rebuild（生产生命周期 deferred）。
- **`sendMessageStreaming(params, eventConsumer, errorConsumer, context)` + `CountDownLatch.await(streamTimeoutMillis)`** 阻塞等终态事件。
- **终态映射**（`terminalStateOf` 提取 `Task` / `TaskStatusUpdateEvent` → `TaskState`，`isFinal() || isInterrupted()` 镜像 `A2aJsonRpcController#isStreamTerminating`）：

  | A2A 远程 Task 状态 | 映射 |
  |---|---|
  | COMPLETED / INPUT_REQUIRED | `acked()`（投递成功 + 远程到达；INPUT_REQUIRED 的精确半完成态语义 deferred） |
  | FAILED / CANCELED / REJECTED（`isFinal && !COMPLETED`） | `dlq(REMOTE_TASK_FAILED)`（Stage 18 MI18-002：远程 agent 终态业务失败，non-retryable → 直达 DLQ；对确定失败的 task 重投同输入只会轰炸下游） |
  | AUTH_REQUIRED（中断） | `retry(RECEIVER_UNAVAILABLE)`（认证可恢复，infra 层，非 task 失败） |
  | 流内 `errorConsumer` 触发（无前置终态） | `retry(RECEIVER_UNAVAILABLE)`（连接级失败） |
  | `await` 超时（未到终态） | `retry(DELIVERY_TIMEOUT)` |
  | `sendMessageStreaming` 同步抛 `RuntimeException`（含 `A2AClientException`） | `retry(RECEIVER_UNAVAILABLE)`（MI11-002 契约：`deliver` 不抛非 lease 异常） |

### 16.2 `ForwardingEndpointResolver`（解开 routeHandle opaque，HD4 不破）

`ForwardingRouteHandle(value, tenantScope)` 完全 opaque，**HD4 硬约束：deliver 不得自行 unwrap**。引入注入端口（与 `ForwardingRetryPolicy` / `DispatchLeasePolicy` / `EpochClock` 同层范式）：

- `Optional<String> resolve(ForwardingRouteHandle handle)` —— 空 = 路由解析失败 → `dlq(ROUTE_NOT_FOUND)`。
- 默认 `MapEndpointResolver`（`routeHandle.value → base URL`，测试里指向 MockWebServer URL）；**生产实现由 Stage 3 registry 提供，deferred**。

### 16.3 同步等完成语义（= Stage 13 T1 dispatcher-push over sync RPC）

Stage 15 范围决策：deliver 等 Task 到终态才 ACKED（镜像 main 的 `A2aRemoteAgentOutboundAdapter` streaming 阻塞模式）。这是 Stage 13 的 T1 dispatcher-push over sync RPC 投递模型的具体落地，**不裁决** T1（同步 push）vs C3 异步转发的哲学张力 —— PoC 只证明对接可行，最终 push / pull / MQ 投递模型裁决仍 H2/H3。

### 16.4 tenant + payload 语义（§6.2 守恒）

- **tenant（R-C.c 跨租户连续性）**：`ClientCallContext` 携带 `X-Tenant-Id` header（`Map.of(properties.tenantHeaderName(), record.tenantId())`），对齐 `A2aJsonRpcController @RequestHeader(name="X-Tenant-Id")`。
- **payload（§6.2 不破）**：`payloadRef` 走 `MessageSendParams.metadata`（A2A 扩展位）；`TextPart` 仅载 routing descriptor（`"agent-bus forwarded message " + messageId`），**绝不放 payload body / token stream**。

### 16.5 MockWebServer 契约验证 + 线上格式对称性（test）

5 场景 harness（`A2aForwardingDeliveryPortMockWebServerTest`）：COMPLETED→ACKED（+ 断言 `X-Tenant-Id` header + body 含 `SendStreamingMessage`）/ 流超时→DELIVERY_TIMEOUT / 远程 FAILED→RECEIVER_UNAVAILABLE / 连接错误（`SocketPolicy.DISCONNECT_AFTER_REQUEST`）→RECEIVER_UNAVAILABLE / resolver 空→ROUTE_NOT_FOUND。

**线上格式对称性**（核心 PoC 价值）：harness 用 A2A SDK 自身序列化器 `JsonUtil.toJson(new SendStreamingMessageResponse(id, event))` 产出 SSE `event:jsonrpc\ndata:<json>` 帧 —— 与 agent-runtime `A2aJsonRpcController` 的 `ServerSentEvent.event("jsonrpc").data(streamingResponseJson(id, evt))` **逐字节一致**。即真实 `JSONRPCTransport` 客户端解析的字节 == 真实 agent-runtime 响应（同 Stage 12 embedded-postgres「用测试载体验证真实协议代码」哲学，非 agent-runtime 自测用的 `RecordingTransport` fake）。

**SDK 行为发现**：HTTP 4xx / 5xx（如 503）**不被当错误** —— A2A SDK 把非 2xx 视为静默空 SSE 流（无事件、连接正常关闭、`errorConsumer` 从不触发）→ deliver 阻塞到 `DELIVERY_TIMEOUT`。真正的 socket 断开才触发 `onFailure → errorConsumer → RECEIVER_UNAVAILABLE`。故「连接错误」场景用 `SocketPolicy.DISCONNECT_AFTER_REQUEST`（真 socket 断开）而非 HTTP 503。

### 16.6 ArchUnit（子包豁免）

`AgentBusForwardingSpiPurityTest` 新增 `forwarding_core_does_not_import_a2a_outside_transport_adapter`：`org.a2aproject` 仅允许在 `transport.a2a` 子包内依赖，转发核心（ports / 状态机 / worker / loop）保持 transport-agnostic。全局规则（netty / jackson / servlet / kafka / nats / hikari / reactor）实测不退化（A2A SDK 公共 API 不暴露这些）。`AgentBusForwardingRuntimeContractTest` 的 §6.2 文本扫描（`readForwardingProductionSources`）同步排除 `transport/a2a` 子树（`TaskStatus` 仅用于解析远程 A2A 事件，从不写进 record）。

Stage 15 DoD 自检：

- ✓ `A2aForwardingDeliveryPort implements ForwardingDeliveryPort`（真实 A2A transport binding，圈进 `transport.a2a`）。
- ✓ `ForwardingEndpointResolver` 注入端口 + `MapEndpointResolver` 默认实现（HD4 不破，routeHandle 保持 opaque）。
- ✓ 同步等完成语义（T1），终态映射覆盖成功 / 超时 / 远程失败 / 连接错误 / 路由解析失败，`deliver` 不抛非 lease 异常（MI11-002）。
- ✓ 线上格式对称性（SDK 自身序列化器产出 agent-runtime 逐字节一致的 SSE 帧）。
- ✓ tenant（R-C.c header）+ payload（metadata 扩展位，无 payload body）。
- ✓ **164 tests green**（Stage 14 的 158 + 5 MockWebServer 场景），ArchUnit 纯度 green（`org.a2aproject` 圈进 `transport.a2a`，§6.2 文本扫描豁免）。
- ✓ §6.2 不变：A2A 是 HTTP JSON-RPC 非 concrete broker / MQ；不写 Task execution state；不跨 tenant fallback；不放 payload body / token stream；不绕 routeHandle。
- ✓ **deferred**：真实 agent-runtime 端到端拉起验证（受 runtime 构建坑阻塞）、`REMOTE_TASK_FAILED` non-retryable 码、registry 集成的 resolver 生产实现、连接池治理、最终 push / pull / MQ 投递模型裁决（H2/H3）。`ForwardingCircuitBreaker` 接入 worker 由 **Stage 16 完成**（§17）。

## 17. Stage 16 决策（`ForwardingCircuitBreaker` 接入 worker：断路器生产化补全）

Stage 16（无 MI 编号，对应 Stage 15 评审后「agent-bus 生产化补全」方向）把 Stage 14 预留的 deferred 端口 `ForwardingCircuitBreaker`（§15.3：端口 + `ALWAYS_CLOSED` no-op，**未接入 worker**）**正式接入 `ForwardingDispatcherWorker`**。正当性来自 Stage 15 真实投递绑定 PoC 的范围决策：选了 **T1（dispatcher-push over sync RPC）** —— push 模型 dispatcher 主动驱动投递，**需要 breaker 在故障 route 上主动短路**（否则连续 retryable 失败会轰炸下游 receiver），故 §15.3「形态依赖 transport 模型」的悬挂前提落地。**不裁决** Stage 13 的 push / pull / MQ 最终模型（仍 H2/H3）。**`§6.2` 始终不得项不变**（breaker 纯 JDK，无 Task state / payload body / concrete broker client）。

### 17.1 端口扩展：`recordOutcome` 结果反馈

`ForwardingCircuitBreaker` 端口加 `recordOutcome(routeHandle, ForwardingDeliveryResult)`（投递后反馈，驱动状态机）；`ALWAYS_CLOSED` 由单方法 lambda 改为实现两方法的匿名类（lambda 无法实现多方法接口）。源码兼容、二进制不兼容 —— `ALWAYS_CLOSED` 仅 agent-bus 内部用，安全。

### 17.2 真实实现：`RouteCircuitBreaker`（三态机，纯 JDK）

`forwarding/runtime/RouteCircuitBreaker.java`（与 `ForwardingRetryPolicy` / `DispatchLeasePolicy` / `EpochClock` 同包同层），照「注入端口 + 构造期验证不变量的实现」范式（同 `ExponentialBackoff`）。注入 `EpochClock`（与 worker 续约判断同源，MI11-001）。构造参数（compact constructor 验证）：`failureThreshold`（CLOSED→OPEN 连续失败阈值，`>= 1`）、`cooldownMillis`（OPEN 冷却时长，`> 0`）、`clock`（非空）。

状态机：`CLOSED --(failureThreshold 连续失败)--> OPEN --(cooldown 到)--> HALF_OPEN`；`HALF_OPEN --(探测成功)--> CLOSED`；`HALF_OPEN --(探测失败)--> OPEN`。每路由状态 `RouteState`（`ConcurrentHashMap<String, RouteState>`，key = `routeHandle.value()`，每个 `RouteState` 用 `synchronized(routeState)` 保护）：`State state` / `int consecutiveFailures` / `long openedAtMillisEpoch` / `boolean probeInFlight`（HALF_OPEN 单探测锁）。

**触发分类在 breaker 内部**（worker 只调一次，不关心怎么分类）：`ACKED`→成功（重置计数 / HALF_OPEN 探测成功→CLOSED）；`RETRY_SCHEDULED`→失败（retryable 码 = route 不健康信号，计数++ / HALF_OPEN 探测失败→OPEN）；`DLQ` / `EXPIRED`→忽略（non-retryable 是配置错误非 route 过载，retryable 耗尽已在之前 RETRY_SCHEDULED 记过，EXPIRED 是消息自身 deadline）。`ForwardingDeliveryResult.retry(code)` 已在 Stage 9 拒绝非 retryable 码，故 RETRY_SCHEDULED 必然 retryable，breaker 无需再查 `failureCode.retryable()`。

### 17.3 worker 接入：接入点顺序与 `probeInFlight` 不变量

worker 7 参构造器（+`ForwardingCircuitBreaker`）；6 参（Stage 14 全参）改为委托 7 参传 `ALWAYS_CLOSED`；3/4/5 参链不变。`runOnce` 三处接入，**顺序是 `probeInFlight` 不泄漏的关键**：

1. 投递前 `allowsDelivery`（**在 lease 续约检查之后、deliver 之前**）：OPEN→短路，复用现有 skip 路径（`skipped++`，留 DISPATCHING 待租约过期回收，**不消耗 attemptCount**）。
2. 投递后 `recordOutcome`（**在 switch 之前**）：使 switch 内 `markAcked` / `scheduleRetry` / `moveToDlq` / `markExpired` 的 lease-guard 异常不影响 breaker 反馈。
3. deliver 抛异常 catch 块补 `recordOutcome(retry(RECEIVER_UNAVAILABLE))`：HALF_OPEN 探测抛异常（永不返回 result）也清掉 `probeInFlight`，不卡死。

`DispatchTickResult` **不变** —— 短路复用现有 skip 路径，自洽不变量 `claimed == acked+retried+dlqd+expired+skipped` 自动保持，无新计数器。

### 17.4 transport-agnostic 不破

breaker 端口只消费 `ForwardingRouteHandle` / `ForwardingDeliveryResult`（HD4 守恒，不 unwrap 物理端点），不碰 broker。即便 H2/H3 最终裁决选 T3 consumer-pull，接入的 breaker 也无害 —— T3 receiver 自调速（停止 claim 即 backpressure），breaker 基本不触发。接入安全，不 bake in 任何 push / pull 假设。

### 17.5 ArchUnit（无需新规则）

`RouteCircuitBreaker` 纯 JDK（依赖 `ForwardingDeliveryResult` / `ForwardingRouteHandle` / `EpochClock` / `java.util.concurrent`），不沾 spring / jdbc / jackson / kafka / nats / netty / a2a 任何规则 —— 现有规则全 green，无需新豁免。worker 新增 `ForwardingCircuitBreaker` 字段同理不破。§6.2 文本扫描不触发（breaker 无 Task state / payload body / broker client）。

Stage 16 DoD 自检：

- ✓ `ForwardingCircuitBreaker` 加 `recordOutcome`；`ALWAYS_CLOSED` 改两方法匿名类。
- ✓ `RouteCircuitBreaker` 三态机（纯 JDK，compact constructor 验证；per-route `ConcurrentHashMap` + `synchronized` + `probeInFlight` 单探测）。
- ✓ worker 7 参构造器 + `runOnce` 三处接入（顺序保证 `probeInFlight` 不泄漏）。
- ✓ 触发分类在 breaker 内部（ACKED / RETRY_SCHEDULED / DLQ·EXPIRED）；`DispatchTickResult` 不变（短路复用 skip 路径）。
- ✓ **179 tests green**（Stage 15 的 164 + 11 `RouteCircuitBreakerTest` + 4 worker 行为测试），ArchUnit 纯度 green（纯 JDK 无需新豁免）。
- ✓ §6.2 不变：breaker 无 Task state / payload body / concrete broker client；不跨 tenant fallback；不绕 routeHandle。
- ✓ **deferred**：per-route breaker 状态非持久化（进程重启回 CLOSED）、`failureThreshold` / `cooldownMillis` 生产调优 / per-route 配置、HALF_OPEN 单探测（当前）、多 worker 实例共享单例 breaker、最终 push / pull / MQ 投递模型裁决（H2/H3）。

相关文档：

- C3 运行态 L2：[`forwarding-outbox-inbox`](forwarding-outbox-inbox.md)（组件 / 状态机 / 失败语义）。
- runtime ICD：[`ICD-Agent-Bus-Forwarding-Runtime`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)。
- machine-readable schema：[`agent-bus-forwarding-runtime.v1.yaml`](../../../docs/architecture/l0/05-contracts/machine-readable/agent-bus-forwarding-runtime.v1.yaml)。
- 裁决：[`decision`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)。
- Stage 8 计划：[`agent-bus-stage7-review-and-stage8-plan`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage7-review-and-stage8-plan.md)。

## 18. Stage 17 决策（首次跨模块集成：真实端到端 IT 验证 C3 转发闭环）

Stage 17（MI17-005..007）是 agent-bus 项目自 Stage 1 以来**首个跨模块集成里程碑**：首次建立 agent-bus → agent-runtime 依赖（test-only），用**真实的 `LocalA2aRuntimeHost`（Spring Boot A2A 服务器）替换 Stage 15 的 MockWebServer**，端到端驱动完整 C3 转发链路到 outbox record ACKED。不裁决 Stage 13 的 push/pull/MQ 最终模型（仍 H2/H3）；IT 复用 Stage 15 已选的 T1 投递绑定 + Stage 10 测试驱动的 `TickSource`（无真实 scheduler，符合 §6.1）。**§6.2 始终不得项不变**（IT 不含 Task 执行状态 / payload body / concrete broker，复用 Stage 12 CONTROL_ONLY envelope）。

### 18.1 链路（无 stub transport / mock server / in-memory outbox）

```
JdbcForwardingOutbox.enqueue(record)            [Stage 12 真实 Postgres 16.2 + Flyway V1 + RLS]
   ↓
ForwardingDispatchLoop.run (测试 TickSource)     [Stage 10 无 scheduler/thread/clock]
   ↓ worker.runOnce: claim (lease-guarded)
A2aForwardingDeliveryPort.deliver               [Stage 15 真实 A2A JSON-RPC client + X-Tenant-Id]
   ↓ HTTP /a2a + SSE
LocalA2aRuntimeHost.port(0) + StubHandler       [Stage 17 真实 Spring Boot MVC + A2A SDK server]
   ↓ Task → COMPLETED (SSE)
mapped → ACKED → outbox record ACKED
```

### 18.2 两个 Stage 17 发现（治理记录）

(a) **agent-runtime 对 JDBC-bearing 共享 classpath 敏感**。`LocalA2aRuntimeHost` 的 A2A server Spring 上下文是纯内存（A2A SDK task store / event queue，无 JDBC），其自身测试从不触发 `DataSourceAutoConfiguration`；但 agent-bus 的 `spring-boot-starter-jdbc` + postgres driver + flyway（Stage 12）一旦泄漏到共享测试 classpath，`DataSourceAutoConfiguration` / `FlywayAutoConfiguration` 对真实 host 上下文 fire 并因缺 `spring.datasource.url` 失败。IT 里用 `System.setProperty("spring.autoconfigure.exclude", …)` 排除 —— `LocalA2aRuntimeHost.port(int)` 工厂未暴露 property hook，system property 是唯一高于其 package-private `defaultProperties` 的属性源。**记录给 agent-runtime**：`LocalA2aRuntimeHost` 生产部署若与 JDBC-bearing 消费方共享 classpath 会撞同样失败，应排除这些 autoconfig（或显式提供 datasource）。

(b) **Spring Boot 4 autoconfigure 重打包**。jdbc autoconfigure 从 `org.springframework.boot.autoconfigure.jdbc`（Spring Boot 3）移到 `org.springframework.boot.jdbc.autoconfigure`（Spring Boot 4），flyway 从 `…autoconfigure.flyway` 移到 `org.springframework.boot.flyway.autoconfigure`。**旧包名排除静默无效** —— 排除 Spring Boot 3 的包名不会报错也不会生效。任何用 `spring.autoconfigure.exclude` 排除 jdbc/flyway 的代码必须用 Spring Boot 4 新包名。

### 18.3 意外强证据：真实 controller 接收并解析 tenant

Stage 15 在 wire level 断言了 `X-Tenant-Id` header 的值（MockWebServer 拦截）。Stage 17 的运行日志显示真实 `A2aAgentExecutor` 收到 `metadata.tenantId=tenant-loop` / `tenant-iso-a` —— 证明 header 不只被发送，更被**真实 agent-runtime controller 接收 / 解析 / 传给 handler**（MockWebServer 不解析 header 语义，这是 Stage 15 无法提供的证据）。端到端租户隔离（Rule R-C.c）的第二个测试进一步证实：tenant A 两条 route 指向**同一**真实 host，dispatch loop 只 ACK tenant A 的 record，tenant B 仍 PENDING（无跨租户回退）。

### 18.4 边界（ArchUnit / production 纯度）

agent-bus 生产代码**仍零依赖** agent-runtime。`agent-bus/pom.xml` 的 `agent-runtime` 依赖是 **test-scope**；`AgentBusDependencyBoundaryTest.bus_does_not_depend_on_agent_runtime` 守卫生产 `com.huawei.ascend.bus..` 不得 reach `com.huawei.ascend.runtime..`（这条守卫的存在正是为了允许 test-only 跨模块依赖而不破生产边界；同时取代 `agent-service → agent-runtime` 重命名 034da8f7 遗留的旧 `service..` 守卫）。`AgentBusForwardingSpiPurityTest` 针对生产源，test-scope 引用不触发。

Stage 17 DoD 自检：

- ✓ `C3ForwardingEndToEndIntegrationTest` 双测试 green（happy path 一条 record 走完到 ACKED + tick 自洽 + 端到端租户隔离）。
- ✓ `agent-bus/pom.xml` 加 test-scope `agent-runtime` 依赖（首次跨模块）。
- ✓ `AgentBusDependencyBoundaryTest.bus_does_not_depend_on_agent_runtime` 守卫加入。
- ✓ **182 tests green**（Stage 16 的 179 + 2 IT），ArchUnit green（新 `runtime..` 守卫 + SpiPurity 不受影响）。
- ✓ §6.2 不变：IT 不含 Task 执行状态 / payload body / concrete broker；复用 CONTROL_ONLY envelope。
- ✓ **deferred**：真实 agent handler（用 StubHandler）、registry 集成的 resolver 生产实现（用 MapEndpointResolver）、连接池治理、`REMOTE_TASK_FAILED` non-retryable 码、真实 scheduler/polling、push/pull/MQ 最终裁决（H2/H3）。

## 19. Stage 18 决策（失败路径端到端验证 + `REMOTE_TASK_FAILED` non-retryable 码收口）

Stage 18（MI18-001..005）闭合 Stage 17 的唯一盲区 —— happy path 之外的失败处理路径（ACK / RETRY / DLQ / EXPIRED、lease-guarded mutation、retry policy、circuit breaker）此前只在 fake-delivery 单元 / 契约测试覆盖，从未在真实端到端链路上跑通；并把三次 deferred（14 → 15 → 17）的 `REMOTE_TASK_FAILED` non-retryable 码一并落地。**§6.2 始终不得项不变**（`remote_task_failed` 是 failure-code enum 值，非 Task 执行状态 / payload body / concrete broker；IT 复用 Stage 12 CONTROL_ONLY envelope）。不裁决 Stage 13 的 push/pull/MQ 最终模型（仍 H2/H3）。

### 19.1 核心论证：业务失败 vs infra 失败

A2A `FAILED` 是远程 agent 的**终态业务失败**（业务层），区别于 infra 层 retryable 失败（`DELIVERY_TIMEOUT` / `RECEIVER_UNAVAILABLE` / `BACKPRESSURE_REJECTED`）。对一条确定失败的 task 重投同一输入只会轰炸下游（bomber 模式）、不改变结果 —— 故 `FAILED` / `CANCELED` / `REJECTED`（`isFinal && !COMPLETED`）应**直达 DLQ**，不消耗 retry 预算。这与 Stage 14 retry policy 正交：retry policy 决定 retryable 失败的重投时机，`REMOTE_TASK_FAILED` 决定的是「根本不进 retry 轨道，直接进 DLQ」。

`AUTH_REQUIRED`（`isInterrupted && !INPUT_REQUIRED`）仍是 `retry(RECEIVER_UNAVAILABLE)`：认证是可恢复的 infra 层问题（补 token 后重投可达），非 task 自身的业务失败。

### 19.2 终态映射精确化（MI18-002）

`A2aForwardingDeliveryPort` 终态映射从 Stage 15 的保守 switch（FAILED/CANCELED/REJECTED/AUTH_REQUIRED 全 → `retry(RECEIVER_UNAVAILABLE)`）改为 `isFinal()` if-chain（见 §16.1 表）。用 `isFinal()`（非枚举 case label）判定，使未来新增 A2A final state 自动正确分类，且规避 CANCELED/CANCELLED 拼写风险。

### 19.3 失败路径端到端 IT（MI18-003）

`C3ForwardingFailurePathIntegrationTest` 双场景，复用 Stage 17 boot recipe（embedded-postgres + Flyway + `spring.autoconfigure.exclude` + 真实 `LocalA2aRuntimeHost`），唯一差异是 handler：

- **场景 1（真实 FAILED → DLQ）**：`FailingHandler.resultAdapter` 把每个 raw 结果映射为 `AgentExecutionResult.failed(...)` → 真实 A2A server Task FAILED → 真实 SSE FAILED 帧 → `A2aForwardingDeliveryPort dlq(REMOTE_TASK_FAILED)` → worker `moveToDlq` → persisted `last_failure_code = remote_task_failed`。
- **场景 2（不可达 route → RETRY）**：`MapEndpointResolver` 指向 `freeUnusedPort()`（bind 后 close 的瞬时空闲端口 → 真实 socket 拒连）→ `A2aForwardingDeliveryPort retry(RECEIVER_UNAVAILABLE)` → worker `scheduleRetry`（Stage 14 policy）→ persisted `last_failure_code = receiver_unavailable` + `attempt_count = 1` + future `next_attempt_at`。场景 2 不经 runtime（socket 拒连在任何 server 之前）。

outbox 端口不暴露 per-record reader（`claimDue` 是租约路径非读路径），IT 用 raw JDBC 投影读 `last_failure_code` / `attempt_count` / `next_attempt_at` 持久化列，对齐 `ForwardingFailureCode.wireCode()` 契约。

### 19.4 最小足迹（MI18-001 classification 连锁）

新增一个 failure-code enum 值，classification / DDL / SqlCodec / record 全部**自动兼容、零改动**：

- **DDL**：`ck_outbox_failure_code` CHECK 只校验 `status` ↔ `last_failure_code` 的 null 配对，不枚举码值 → 加 `remote_task_failed` 无需 migration。
- **`ForwardingSqlCodec.decodeFailureCode`**：遍历 `ForwardingFailureCode.values()` 查 wireCode → 新码自动覆盖。
- **`ForwardingDeliveryResult.dlq(code)` compact constructor**：只拒绝 `dedup()` / null，接受任意 non-retryable 码 → `dlq(REMOTE_TASK_FAILED)` 无需改 record。
- **classification harness**：`AgentBusForwardingRuntimeContractTest` 的 `failure_code_classification_drives_retry_and_dlq_routing` 加 `REMOTE_TASK_FAILED` 断言（non-retryable true、dlq 接受、retry 拒绝）；`forwarding_runtime_failure_codes_cover_l2_semantics` 期望码集从 7 增到 8。

### 19.5 落地清单

- ✓ `ForwardingFailureCode.REMOTE_TASK_FAILED` enum 值 + javadoc。
- ✓ `A2aForwardingDeliveryPort` 终态映射 `isFinal()` if-chain（FAILED/CANCELED/REJECTED → DLQ、AUTH_REQUIRED → retry）。
- ✓ `C3ForwardingFailurePathIntegrationTest` 双失败场景端到端。
- ✓ `A2aForwardingDeliveryPortMockWebServerTest.deliver_dlqs_on_remote_task_failed`（Stage 15 场景 3 从 retry 改 DLQ）。
- ✓ classification harness 加 `REMOTE_TASK_FAILED` 断言。
- ✓ **184 tests green**（Stage 17 的 182 + 2 失败路径 IT），ArchUnit green。
- ✓ §6.2 不变：`remote_task_failed` 是 failure-code enum 值，非 Task 执行状态 / payload body / concrete broker；IT 复用 CONTROL_ONLY envelope。
- ✓ **deferred**：真实 agent handler（仍 FailingHandler/StubHandler 程序化失败/完成）、registry 集成的 resolver 生产实现、连接池治理、push/pull/MQ 最终裁决（仍 H2/H3）。
