---
artifact_type: delivery_projection
version: agent-bus-stage21-review-and-stage22-plan
status: stage-22-planned
source_commit: feaacac1
stage22_planned: 2026-07-01
source_stage21_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage20-review-and-stage21-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
source_l2: architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md
target_module: agent-bus (纯测试 / 验证阶段，无生产代码；不 boot runtime)
---

# agent-bus Stage 21 评审与 Stage 22 计划（时间驱动的终态与续约端到端验证：EXPIRED 终态 + lease 续约真实触发）

## 0. 结论

提交 `feaacac1`（Stage 21）可以作为 Stage 21 的阶段性成果接受：首次在真实并发下端到端验证 C3 outbox 的**多 worker claim 无重复投递**（delivery 计数 == 20 是 `FOR UPDATE SKIP LOCKED` 的 smoking gun）+ **共享 `RouteCircuitBreaker` 单例并发一致 OPEN**（`synchronized(RouteState)` 防 lost-update）。C3 outbox 的三条端到端生命线（retry 往返 + 租约回收 + 断路器）至此在单 worker（Stages 17–20）与多 worker（Stage 21）下**全部验证通过**。**190 tests green**，ArchUnit green，§6.2 不变，无生产代码改动。**接受**。

但 Stages 17–21 的端到端覆盖面仍有三个高价值盲区——**EXPIRED 终态、lease 续约真实触发、payloadRef 大载荷路径**——三者的共同点是**都是 Stage 15（T1 真实 deliver 落地）之前因投递模型未定而 deferred 的验证项**，现在阻塞解除了，却从未端到端跑通。盘点后选其中**两个时间维度盲区**作 Stage 22：

1. **EXPIRED 终态从未端到端触发**：outbox 状态机有第三类终态 EXPIRED（`markExpired`，与 ACKED/DLQ 并列），worker `case EXPIRED → outboxPort.markExpired(...)` 是 Stage 8 落地的生产代码，`ForwardingDeliveryResult.expired()` 工厂也在。但 Stages 17–21 所有端到端测试里 `tick.expired()` 始终为 0——从未有一条 record 从 PENDING 端到端走到 EXPIRED。三大终态里 ACKED（Stage 17）/ DLQ（Stage 18）都端到端验过，唯独 EXPIRED 缺位。

2. **lease 续约从未端到端触发**：MI11-001 把续约检查从 tick-start instant 改成「每条 record 读注入 `EpochClock`」，正是为了让「deliver 真实耗时 → lease 临近 TTL → `renewLease` 续约」在自然 dispatch loop 下能触发。但 Stages 17–21 所有端到端测试**全用 `DispatchLeasePolicy.DISABLED`**（`renewBeforeExpiryMillis=0` → 跳过续约），续约的 `claimPort.renewLease(...)` SQL 路径在真实持久化上**从未被驱动过**。这是 `agent-bus-forwarding-runtime-decision §8` 明确列出的 deferred 项「续约真实耗时验证 deferred 依赖投递模型裁决 + 真实 deliver 落地」——Stage 15 已裁决 T1 且 deliver 已落地，阻塞解除，可验证。

Stage 22 = **时间驱动的终态与续约端到端验证**。复用 Stage 20 的全部测试基础设施（`MutableEpochClock` + `fakeDeliveryPort` + `CONTROL_ONLY` envelope + `@Isolated` + 不 boot runtime 的轻量 boot recipe），**零新增前置工程、零生产代码**。两个新场景各闭合一个盲区。预计 **192 tests green**，§6.2 不变。

**核心论点**：EXPIRED 与续约都是 worker.runOnce 里**已落地的生产分支**（`case EXPIRED` / lease-renew check），只是端到端测试从未驱动它们。Stage 22 用 fake delivery port 注入 `expired()` / 用 `DispatchLeasePolicy(50s, 60s)` + MutableEpochClock 构造续约触发条件，在真实 PG 上验证这两条分支的 SQL 路径正确闭合。这与 Stages 18–21「用 fake delivery 驱动真实 PG 上的状态转换」范式完全对称。

**一个关键发现（Stage 22 必须记录）**：EXPIRED 是「SQL 路径正确但缺真实触发源」的终态——`ForwardingOutboxRecord` **不持久化 `deadlineMillisEpoch`**（envelope 有此字段但 record 无），`A2aForwardingDeliveryPort.deliver` **不检查 deadline**，所以真实 A2A 链路下 `expired()` 永不被返回、EXPIRED 永不触发；且 `markExpired` 硬编码 `last_failure_code = 'delivery_timeout'`（一个 retryable 码）填充 EXPIRED 终态，语义混淆（EXPIRED 是消息 deadline 超时，不是投递超时）。Stage 22 验证现有 `markExpired` 的 PG 路径正确（lease-guarded UPDATE + DDL CHECK 接受 EXPIRED + 终态不被回收），**并把「EXPIRED 缺真实触发源 + 码语义混淆」作为发现留作后续 / H2/H3 议题**——不在 Stage 22 补 deadline 持久化（那需要 record 加字段 + DDL 加列 + deliver 检查，是 schema 改动级议题，超出纯验证 stage）。

Stage 22 **不裁决** Stage 13 的 push/pull/MQ 哲学张力（仍 H2/H3）—— EXPIRED 与续约是 worker / 持久化层语义，与投递是 push 还是 pull 无关；§6.1 不受影响（时间控制用注入 `MutableEpochClock`，**非真实调度器**）。§6.2 不变（两场景用 CONTROL_ONLY envelope，时间控制 / fake port 是 test-scope 纯 JDK，无 concrete broker / payload body / token stream / Task execution state / 跨租户回退）。

## 1. Stage 21 评审（commit `feaacac1`）

Stage 21 在 Stages 17–20 三条生命线单 worker 全验证在手时，闭合了 Stage 20 明示的两个「单线程验证」并发盲区：**并发 claim 真实竞争**与**共享断路器单例并发正确性**。

**4 个优点：**

1. **delivery 计数 == 20 是 SKIP LOCKED 的 smoking gun**：lease guard（`markAcked` 拒绝 owner 变了的行）是第二道防线 —— 即使两 worker 都把同一条行转 DISPATCHING，只有一个 ack 落地、另一个 skip，持久化终态仍可能看起来对（一条 ACKED）。delivery 计数在 guard **之前**（计每次 `deliver` 调用），== 20 直接证明无重复投递，> 20 直接证明 SKIP LOCKED 失效。这是并发 claim 正确性的最直接证据。
2. **共享 breaker 并发一致 OPEN**：`RouteCircuitBreaker` 的 `ConcurrentHashMap<String, RouteState>` + `synchronized(RouteState)` 在跨 worker 线程共享单例、并发 `recordOutcome` 下，防住了 `consecutiveFailures` 的 read-modify-write 丢更新与状态撕裂，`ConcurrentHashMap` 的 happens-before 把 OPEN 一致发布到所有线程。场景 B 用冻时钟 + 极大冷却隔离了唯一的并发状态写（CLOSED→OPEN 转换 = lost-update 风险所在），干净靶向。
3. **冻时钟设计严谨**：不沿用 Stage 19/20 的 `advanceableTickSource`，而是把 `MutableEpochClock` 冻在 t0。场景 A 同步 deliver+ack 无需跨 tick；场景 B 冻时钟 + 远冷却使 breaker 只能达 OPEN 不能 HALF_OPEN，避免多线程推进共享时钟与 HALF_OPEN 探测在飞的真实 race，隔离测试目标。
4. **`@Isolated` 稳定性基线延续**：多 worker IT 引入真实 `ExecutorService(4)` 线程，`@Isolated` 让本 IT 独占执行避免线程叠加，沿用 Stage 19/20 基线。

**1 个盲区（观察，驱动 Stage 22）：**

Stages 17–21 的端到端覆盖面**只覆盖了 worker 在「正常投递 + retryable 失败 + non-retryable 失败 + 租约回收 + 断路器 + 多 worker 并发」下的行为**，但 worker.runOnce 里还有两个**已落地的生产分支从未被端到端驱动**：(a) `case EXPIRED` —— EXPIRED 终态从未端到端触发（`tick.expired()` 始终为 0）；(b) lease-renew check —— Stages 17–21 全 `DispatchLeasePolicy.DISABLED`，续约 SQL 路径从未在真实 PG 上跑过。这两个都是「生产代码有、端到端测试没覆盖」的真盲区，且都是 Stage 15 之前因 deliver 模型未定而 deferred 的验证项，现在可验证。Stage 22 闭合它们。

（注：第三个高价值盲区 payloadRef 大载荷路径 —— Stages 17–21 全用 CONTROL_ONLY envelope，DATA_BEARING + payloadRef 端到端从未跑通 —— 留作 Stage 23 候选。它需 boot runtime + 一个能断言「收到 payloadRef metadata」的 handler，比 EXPIRED/续约的「不 boot runtime + fake delivery」重，单独 stage 更合适。）

Stage 21 DoD：190 tests green，ArchUnit green，§6.2 不变。**接受**。

## 2. Stage 22 范围与设计

### 2.1 为什么（补两个时间维度盲区）

两个盲区都是 worker.runOnce 里已落地但端到端未驱动的生产分支：

| 盲区 | 涉及组件 | 已有覆盖 | 端到端缺位 |
|---|---|---|---|
| EXPIRED 终态 | worker `case EXPIRED` + `markExpired`（JdbcForwardingOutbox:168）+ `ForwardingDeliveryResult.expired()` | `AgentBusForwardingRuntimeContractTest` 单元级（**内存 outbox**，fake delivery 返回 expired() → 内存状态 EXPIRED） | 真实 PG 上 `markExpired` 的 lease-guarded UPDATE（硬编码 `last_failure_code='delivery_timeout'` + 清 lease + DDL CHECK 接受 EXPIRED）从未端到端验证；EXPIRED 是否真终态（claimDue 不回收）从未端到端验证 |
| lease 续约 | worker lease-renew check（ForwardingDispatcherWorker:220-229）+ `renewLease`（JdbcForwardingOutbox:235） | `ForwardingJdbcIntegrationTest` 单元级（`renewLease` 方法存在 + 调用）+ `AgentBusForwardingRuntimeContractTest` 单元级 | Stages 17–21 全 `DispatchLeasePolicy.DISABLED`；续约检查「remaining < renewBeforeExpiry → renewLease」在真实 PG 上从未端到端触发，`renewLease` 的 owner-guarded UPDATE 从未被 worker 驱动 |

Stage 22 = 时间驱动的终态与续约端到端验证，两个场景各闭合一个。复用 Stage 20 全部测试基础设施，新增 `DispatchLeasePolicy(非 DISABLED)` + 一个 observing delivery port（deliver 时读 PG `lease_until` 断言续约已生效），零新增生产代码、零新增前置工程。

### 2.2 场景 A — EXPIRED 终态端到端（闭合盲区 1，三大终态完整）

**注入**：单条 CONTROL_ONLY envelope（tenant `tenant-expired` / route `route-expired` / messageId `msg-expired`，`deadlineMillisEpoch = t0 - 1000` 反映「已超时」语义——虽 record 不持久化 deadline、fake delivery 决定结果，但语义上表明这条消息的 deadline 已过）。`MutableEpochClock(t0)`，`t0 = System.currentTimeMillis()`。

**链路（worker.runOnce 单 tick）**：

```
enqueue PENDING (deadline=t0-1000, CONTROL_ONLY)
claimDue(t0) → row 转 DISPATCHING，lease_owner=worker-exp，lease_until=t0+60_000
worker.runOnce:
  clockNow = t0
  lease-renew check: DispatchLeasePolicy.DISABLED → 跳过
  breaker.allowsDelivery → ALWAYS_CLOSED → true
  deliver(record, t0) → fakeDeliveryPort 返回 ForwardingDeliveryResult.expired()
  breaker.recordOutcome(expired) → EXPIRED 被忽略（DLQ/EXPIRED 不驱动 breaker）
  switch EXPIRED → outboxPort.markExpired(messageId, tenant, leaseOwner)
    → leaseGuardedUpdate(EXPIRE):
        WHERE status='DISPATCHING' AND lease_owner=worker-exp AND lease_until > now(real)
        SET status='EXPIRED', last_failure_code='delivery_timeout',
            lease_owner=NULL, lease_until=NULL
  expired++ → tick.expired()==1
```

**断言**：

- `tick.expired()==1`，`acked/retried/dlqd/skipped==0`，自洽（`claimed == acked+retried+dlqd+expired+skipped` → 1==0+0+0+1+0）；
- PG row（raw JDBC 投影读）：`status==EXPIRED`，`last_failure_code==delivery_timeout`，`attempt_count==0`（EXPIRED 不递增），`lease_owner IS NULL`，`lease_until IS NULL`（终态清 lease，MI9-002）；
- **EXPIRED 是终态**：再 `runOnce` 一次（同 tenant / t0）→ `claimDue` 候选集是 PENDING/RETRY_SCHEDULED/DISPATCHING-stuck，**不含 EXPIRED** → `tick2.claimed()==0`（EXPIRED 行不会被回收重投）。

**关键证据**：`markExpired` 的 lease-guarded UPDATE 在真实 PG 上首次端到端执行——证明 (a) DDL CHECK `ck_outbox_status` 接受 EXPIRED 终态（DISPATCHING+lease → EXPIRED+清 lease 不违反 `ck_outbox_lease_status`，因为 EXPIRED 不要求 lease_owner）；(b) 硬编码 `last_failure_code='delivery_timeout'` 满足 record 不变量（EXPIRED 要求 `lastFailureCode != null`）；(c) `ForwardingFailureCode` 的 wire code `delivery_timeout` 能被 `decodeFailureCode` 正确解码回 `DELIVERY_TIMEOUT`（虽然码语义混淆，见 §3 发现 1）。第二段的 `tick2.claimed()==0` 证明 EXPIRED 是真终态（与 ACKED/DLQ 一样不被 claimDue 回收）。

### 2.3 场景 B — lease 续约真实触发端到端（闭合盲区 2，§8 deferred 收口）

**注入**：单条 CONTROL_ONLY envelope（tenant `tenant-renew` / route `route-renew` / messageId `msg-renew`）。`MutableEpochClock(t0)`，`t0 = System.currentTimeMillis()`。**关键：`DispatchLeasePolicy(renewBeforeExpiryMillis=50_000, leaseExtensionMillis=60_000)`**（非 DISABLED）。claim lease 故意短：`claimLeaseUntil = t0 + 30_000`（30s）。

**链路（worker.runOnce 单 tick）**：

```
enqueue PENDING
worker.runOnce(tenant, t0, limit=5, "worker-renew", claimLeaseUntil=t0+30_000):
  claimDue(t0, leaseUntil=t0+30_000) → row 转 DISPATCHING
      PG lease_until = t0+30_000（claimDue SET lease_until = :leaseUntil）
  clockNow = clock.epochMillis() = t0
  lease-renew check: renewBeforeExpiryMillis=50_000 > 0
      remaining = leaseUntilMillisEpoch - clockNow = (t0+30_000) - t0 = 30_000
      30_000 < 50_000 → 触发续约
      extendedUntil = leaseUntilMillisEpoch + leaseExtensionMillis = (t0+30_000)+60_000 = t0+90_000
      claimPort.renewLease(messageId, tenant, "worker-renew", t0+90_000)
        → UPDATE lease_until=t0+90_000 WHERE status='DISPATCHING' AND lease_owner='worker-renew'
        → 1 row（caller 是 DISPATCHING owner）→ true（不 skip）
  breaker.allowsDelivery → ALWAYS_CLOSED → true
  deliver(record, t0) → observingDeliveryPort:
      读 PG lease_until 断言 == t0+90_000（续约已生效；claim 时写的是 t0+30_000）
      读 PG lease_owner 断言 == 'worker-renew'
      renewalObserved = true
      返回 ForwardingDeliveryResult.acked()
  breaker.recordOutcome(acked) → 成功
  switch ACKED → markAcked(messageId, tenant, leaseOwner)
      → leaseGuardedUpdate(ACK): WHERE status='DISPATCHING' AND lease_owner='worker-renew'
                                  AND lease_until > now(real)
        lease_until=t0+90_000 >> System.currentTimeMillis()（测试远小于 90s）→ guard 通过
        SET status='ACKED', last_failure_code=NULL, lease_owner=NULL, lease_until=NULL
  acked++ → tick.acked()==1
```

**断言**：

- `tick.acked()==1`，`skipped==0`（续约成功，没走 renew-false-skip 路径）；
- `renewalObserved==true`（deliver 被调用，且读到续约后的 lease_until）；
- **续约 smoking gun**：observing delivery 在 deliver 时读 PG `lease_until == t0+90_000`——claim 时写的是 `claimLeaseUntil = t0+30_000`，**只有 `renewLease` 能把它改成 `t0+90_000`**（extendedUntil），故读到 `t0+90_000` 直接证明 `renewLease` 的 owner-guarded UPDATE 在真实 PG 上生效；
- PG row（ACKED 后）：`status==ACKED`，`attempt_count==0`，`lease_owner/lease_until IS NULL`（ACKED 清 lease）。

**real-clock 约束**：`leaseGuardedUpdate`（markAcked）的 WHERE 子句有 `lease_until > :now`，`:now = System.currentTimeMillis()`（真实墙钟，非注入时钟）。续约后 `lease_until = t0+90_000`，`t0` 是测试起点的真实墙钟，测试运行远小于 90s，故 `t0+90_000` 始终 > 真实时钟 → lease guard 不误判 → markAcked 通过。这与 Stage 19/20 的 real-clock 约束处理一致（tick instant 从真实 `T0` 单调起，lease_until 总超实时）。

**关键证据**：续约的 `claimPort.renewLease(...)` SQL（`UPDATE SET lease_until WHERE status='DISPATCHING' AND lease_owner`）在真实 PG 上**首次被 worker 驱动**——证明 (a) worker 续约检查的算术（`remaining = leaseUntilMillisEpoch - clockNow < renewBeforeExpiryMillis`）在真实时钟下正确触发；(b) `renewLease` 的 owner guard（不检查 lease_until > now，只检查 caller 是 DISPATCHING owner——见 JdbcForwardingOutbox:235 注释「renew succeeds iff the caller is the current DISPATCHING owner; does NOT require the lease to be unexpired」）让 holder 能延长一个即将过期的 lease；(c) 续约后 lease_until 延长到 `extendedUntil`，使后续 markAcked 的 real-clock guard 通过。这是 decision §8「续约真实耗时验证 deferred」的直接收口。

### 2.4 时间控制：MutableEpochClock + 真实时钟约束（复用 Stage 19/20）

- **场景 A**：`MutableEpochClock(t0)` 冻在 t0（单 tick，EXPIRED 是终态，无需跨 tick 推进；第二段验证不回收也是单 tick）。
- **场景 B**：`MutableEpochClock(t0)` 冻在 t0。续约触发完全由算术决定（`remaining = claimLeaseUntil - t0 = 30_000 < renewBeforeExpiry=50_000`），**不需要真实耗时 sleep**——这是 MI11-001 把续约检查改成「读注入 EpochClock」的设计红利：续约触发条件是「处理这条 record 时的时钟」减「lease_until」，用 MutableEpochClock 冻 t0 + claimLeaseUntil 接近 t0 即可纯算术触发，CI 不会 flaky。真实耗时（deliver sleep）只是续约设计意图的来源，不是验证续约逻辑的必要条件。
- **真实时钟约束（两场景共享）**：`leaseGuardedUpdate` 用真实 `System.currentTimeMillis()`，故 `t0 = System.currentTimeMillis()`（真实起点），lease_until 总设在 t0 之后足够远（场景 A lease_until=t0+60_000 由 claimDue 写入但 markExpired 清空；场景 B 续约后 t0+90_000），测试运行远小于这些值 → real-clock guard 不误判。

### 2.5 边界 + ArchUnit + governance

- **§6.2 不变**：两场景用 CONTROL_ONLY envelope（payloadRef=null）；`MutableEpochClock` / `fakeDeliveryPort` / observing delivery port / `DispatchLeasePolicy` 都是 **test-scope 纯 JDK 辅助**（`DispatchLeasePolicy` 是 worker 的 public record，测试用现有 API），不引入 concrete broker/MQ、不写 payload 正文/token 流/Task execution state、不跨租户回退。EXPIRED 用的 `delivery_timeout` 是现有 failure code enum 值（非新码、非 Task state、非 payload）；续约用的 `lease_until` 是现有 schema 字段。
- **ArchUnit**：`AgentBusForwardingSpiPurityTest` 扫**生产源码**；Stage 22 **不动生产代码**（只加测试），无需新 ArchUnit 豁免。
- **§6.1 不受影响**：Stage 22 用注入 `MutableEpochClock`（冻时钟），**无真实调度器 / polling cadence**（§6.1「总线无调度器」+ H2/H3 裁决均不受影响）。
- **decision §8**：加 Stage 22 bullet（正向：闭合 EXPIRED 终态 + 续约两个盲区、三大终态完整、续约 SQL 端到端首驱；反向：§6.2 不变、不动生产代码、不裁决 push/pull/MQ、不 boot runtime；**发现**：EXPIRED 缺真实触发源 + delivery_timeout 码语义混淆，留后续）。
- **L2 `forwarding-persistence`**：新增 §23（Stage 22 决策：EXPIRED + 续约端到端验证），含两场景链路、续约算术触发、real-clock 约束、EXPIRED 触发源缺口发现。
- **L1 4+1 视图**：6 视图 + README/ARCHITECTURE 全部纳入 Stage 22 回灌（沿用 `agent-bus-4plus1-view-rebound` 教训）。
- **ICD + yaml**：`stage22_scope`（delivers `expired-terminal-end-to-end` / `lease-renewal-triggered-end-to-end` / `mark-expired-lease-guarded-update-on-real-pg` / `renew-lease-owner-guarded-update-on-real-pg` / `expired-is-terminal-not-reclaimed`；not_delivers `expired-real-trigger-source` / `envelope-deadline-persisted-on-record` / `real-retry-scheduler-polling-cadence` / `production-code-changes`）；顶部 description 追加 Stage 22 句。

## 3. 关键发现（前置分析）

| # | 发现 | 验证 | 结论 |
|---|---|---|---|
| 1 | **EXPIRED 缺真实触发源** | 读 `ForwardingOutboxRecord`（无 `deadlineMillisEpoch` 字段）+ `A2aForwardingDeliveryPort.deliver`（不检查 deadline）+ `ForwardingFailureCode`（无 expire/deadline 码） | envelope 有 `deadlineMillisEpoch`（ForwardingEnvelope:35）但 **record 不持久化它**；真实 A2A delivery 不检查 deadline → `expired()` 永不返回 → EXPIRED 在真实链路下永不触发。`markExpired` 是「SQL 正确但缺触发源」的终态。Stage 22 用 fake delivery 返回 `expired()` 验证 `markExpired` 的 PG 路径（与 Stage 18 fake delivery 驱动 DLQ 对称），真实触发源缺口作为发现留后续（补它需 record 加 deadline 字段 + DDL 加列 + deliver 检查 = schema 改动级议题） |
| 2 | **markExpired 硬编码 `delivery_timeout` 码** | 读 `JdbcForwardingOutbox.markExpired`（行 171: `last_failure_code = 'delivery_timeout'`） | EXPIRED 终态用 `delivery_timeout`（retryable 码）填充 `last_failure_code`，满足 record 不变量（EXPIRED 要求 `lastFailureCode != null`）但**语义混淆**（EXPIRED 是消息 deadline 超时，非投递超时；retryable 码填终态也无实际意义——终态不 retry）。Stage 22 验证它满足不变量 + 可解码，码语义混淆作为发现留后续（是否需专门 `envelope_expired` 码是 H2/H3 或独立议题） |
| 3 | **续约触发是纯算术，不需真实耗时** | 读 worker lease-renew check（行 220-229）+ MI11-001 设计 | 续约检查 `remaining = leaseUntilMillisEpoch - clockNow < renewBeforeExpiryMillis`。MI11-001 把检查从 tick-start instant 改成「每条 record 读注入 EpochClock」，故用 MutableEpochClock 冻 t0 + claimLeaseUntil 接近 t0 即可纯算术触发（30s lease < 50s threshold）。真实耗时 sleep 只是续约设计意图来源，非验证必要条件。CI 不 flaky |
| 4 | **renewLease 不检查 lease_until > now** | 读 `JdbcForwardingOutbox.renewLease`（行 235-250）+ 注释 | `renewLease` 的 WHERE 是 `status='DISPATCHING' AND lease_owner=:leaseOwner`，**只验 caller 是 DISPATCHING owner，不要求 lease 未过期**。故 holder 能延长即将过期的 lease（设计意图：holder 未被回收前可续）。Stage 22 场景 B claimLeaseUntil=t0+30s（在未来），续约成功 |
| 5 | **续约后 lease_until 必须 > 真实时钟** | 读 `leaseGuardedUpdate`（行 302-305: `lease_until > :now`，`:now = System.currentTimeMillis()`） | markAcked/scheduleRetry/moveToDlq/markExpired 的 lease guard 用**真实墙钟**非注入时钟。续约后 lease_until = t0+90_000，t0 是真实起点，测试远小于 90s → guard 通过。故 leaseExtensionMillis=60_000 足够大，续约后 lease_until 远超实时钟 |
| 6 | **EXPIRED 是终态（claimDue 不回收）** | 读 `claimDue` 候选集（行 218-220: PENDING / RETRY_SCHEDULED+next_attempt_at<=now / DISPATCHING+lease_until<=now） | EXPIRED 不在 claimDue 候选集（与 ACKED/DLQ 同）→ EXPIRED 行不会被回收重投。场景 A 第二段 `tick2.claimed()==0` 证明之 |
| 7 | **两场景都不 boot runtime** | 场景 A 用 fake delivery（expired()）+ ALWAYS_CLOSED；场景 B 用 observing delivery（acked）+ ALWAYS_CLOSED | Stage 22 IT **不 boot `LocalA2aRuntimeHost`**（两场景都用 fake/observing delivery port，不连真实 server）。只需 embedded-postgres + Flyway + JdbcForwardingOutbox，与 Stage 19/20/21 同级（比 Stage 17/18 轻） |
| 8 | **Stage 20 测试基础设施零改动复用** | 读 `C3ForwardingLeaseReclaimAndBreakerIntegrationTest` helper | `MutableEpochClock` + `fakeDeliveryPort` + `CONTROL_ONLY` envelope + `outboxRow` 投影读 + `@Isolated` + boot recipe 全部可复用。Stage 22 新增：(a) observing delivery port（deliver 时读 PG lease_until）；(b) `outboxLeaseRow` 投影读（lease_until/lease_owner/status）；(c) `DispatchLeasePolicy(50_000, 60_000)`（场景 B） |
| 9 | **`@Isolated` 沿用** | Stage 19 `@Isolated` 修复基线 | Stage 22 IT 不 boot runtime（更轻），但仍沿用 `@Isolated` 独占执行（与 Stage 20/21 一致），防 parent pom surefire 4 路并发叠加 |

## 4. 切片 + MI 表

| MI | 切片 | 产出 |
|---|---|---|
| MI22-001 | 1 场景 A IT（EXPIRED 终态端到端） | `C3ForwardingExpiryAndLeaseRenewalIntegrationTest` 场景 A `scenario_a_expired_terminal_end_to_end`：enqueue PENDING（deadline=t0-1000, CONTROL_ONLY）→ `MutableEpochClock(t0)` + fake delivery 返回 `expired()` + `ALWAYS_CLOSED` breaker + `DispatchLeasePolicy.DISABLED` → 单 tick `runOnce` → 断言 `tick.expired()==1` + PG row `status=EXPIRED` / `last_failure_code=delivery_timeout` / `attempt_count=0` / `lease_owner NULL` / `lease_until NULL` + 再 `runOnce` 断言 `tick2.claimed()==0`（EXPIRED 是终态不回收）。boot：embedded-postgres + Flyway + JdbcForwardingOutbox（不 boot runtime）；`@Isolated` |
| MI22-002 | 2 场景 B IT（lease 续约端到端） | 场景 B `scenario_b_lease_renewal_triggered_end_to_end`：enqueue PENDING → `MutableEpochClock(t0)` + `DispatchLeasePolicy(50_000, 60_000)` + observing delivery port（deliver 时读 PG `lease_until==t0+90_000` + `lease_owner==worker-renew`，置 `renewalObserved=true`，返回 acked）+ `ALWAYS_CLOSED` → 单 tick `runOnce(t0, 5, "worker-renew", claimLeaseUntil=t0+30_000)` → 断言 `tick.acked()==1` + `skipped==0` + `renewalObserved==true` + PG row `status=ACKED` / `attempt_count=0` |
| MI22-003 | 3 文档同步 | decision §8 加 Stage 22 bullet + 链接 + EXPIRED 触发源缺口 / 码语义混淆两条发现；ICD（边界标题加 Stage 22 + Stage 22 边界条 + Open Issues 加 EXPIRED 触发源缺口 + 续约验证收口）；yaml（`stage22_scope` + 顶部 description）；L2 `forwarding-persistence` 新增 §23（两场景设计 + 续约算术触发 + real-clock 约束 + EXPIRED 触发源缺口发现）；L1 4+1 视图 6 文件（README/physical/scenarios/development/process/logical/ARCHITECTURE）按 `agent-bus-4plus1-view-rebound` 回灌；本双语文档 |
| MI22-004 | 4 构建验证 + 提交 | `mvn -f agent-bus/pom.xml test -s ~/.m2/settings.xml -B` green（190 + 2 IT ≈ 192）；ArchUnit green；§6.2 文本扫描不触发；commit + push（experimental，用户已授权自主推进） |

## 5. deferred + 风险（明示边界）

**风险（需关注）：**

- **场景 B observing delivery 的 lease_until 读取时机**：observing delivery 在 deliver 时读 PG lease_until，此时续约已发生（续约在 deliver 之前）。读到 `t0+90_000` 证明续约 UPDATE 生效。若读到 `t0+30_000`（claim 值）说明续约未触发——需检查 `DispatchLeasePolicy` 是否正确注入（非 DISABLED）+ 算术（remaining < renewBeforeExpiry）。这是验证续约的 smoking gun，失败时给出明确诊断。
- **场景 B real-clock guard 边界**：若测试环境极慢（运行 > 90s），`t0+90_000` 可能被真实时钟追上 → markAcked 撞 lease guard → ForwardingLeaseException → skipped → `tick.acked()==0`。embedded-postgres + 单 tick 测试正常 < 5s，此风险极低；若实测命中，加大 leaseExtensionMillis。

**deferred（明示边界，不在 Stage 22 范围）：**

- **EXPIRED 真实触发源**（核心发现）：`ForwardingOutboxRecord` 不持久化 `deadlineMillisEpoch`，`A2aForwardingDeliveryPort.deliver` 不检查 deadline → 真实链路下 EXPIRED 永不触发。补它需 record 加 deadline 字段 + DDL 加列 + enqueue 持久化 deadline + SqlCodec 读 deadline + deliver 检查 `record.deadline() < clockNow → expired()`，是 schema 改动级议题（影响 ArchUnit/ICD/record 不变量），需独立评审 + 可能 H2/H3 裁决。Stage 22 只验证现有 `markExpired` PG 路径 + 记录缺口。
- **EXPIRED 的 `delivery_timeout` 码语义混淆**：是否引入专门 `envelope_expired` non-retryable 码（区分消息 deadline 超时 vs 投递超时）是码表设计议题，deferred。
- **payloadRef 大载荷路径端到端**：Stages 17–21 全 CONTROL_ONLY，DATA_BEARING + payloadRef 从未端到端跑通。留 Stage 23 候选（需 boot runtime + 断言收到 payloadRef metadata 的 handler）。
- **续约失败路径（renewLease false → skipped）**：renewLease false 需 caller 不是 DISPATCHING owner（被别人 reclaim），单 worker 测试不可达（claim 刚把 lease 给 caller）。该路径由 `AgentBusForwardingRuntimeContractTest` 单元覆盖 + Stage 21 并发 reclaim 场景隐含覆盖。Stage 22 只验证续约成功路径。
- **真实 scheduler / polling cadence**：Stage 22 用冻时钟 MutableEpochClock，**无真实调度器**（§6.1 + H2/H3）。
- **真实 agent handler / registry resolver / 连接池治理 / breaker 跨重启持久化 / 并发 worker 分片 / push/pull/MQ 最终裁决**：沿用 Stages 19–21 deferred。

## 相关文档

- Stage 21 计划：[`agent-bus-stage20-review-and-stage21-plan`](agent-bus-stage20-review-and-stage21-plan.md)（多 worker 并发验证 —— Stage 22 复用其全部测试基础设施的源头 Stage 20 helper，闭合 Stages 17–21 留下的 EXPIRED + 续约两个「生产分支端到端未驱动」盲区）。
- C3 裁决：[`agent-bus-forwarding-runtime-decision`](../review-packets/agent-bus-forwarding-runtime-decision.md)（`adopted-c3`；§8 Stage 22 许可段 + EXPIRED 触发源缺口发现）。
- runtime 契约：[`ICD-Agent-Bus-Forwarding-Runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)（Stage 22 边界条 + Open Issues EXPIRED 触发源缺口 / 续约验证收口）。
- 持久化 L2：[`forwarding-persistence`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)（Stage 22 决策：EXPIRED + 续约端到端验证，§23）。
- 验证机制源头：
  - `agent-bus/src/main/java/com/huawei/ascend/bus/forwarding/runtime/ForwardingDispatcherWorker.java`（`case EXPIRED` 行 291-294 + lease-renew check 行 220-229 —— Stage 22 两条被驱动的生产分支）；
  - `…/runtime/persistence/jdbc/JdbcForwardingOutbox.java`（`markExpired` 行 168-173 硬编码 `delivery_timeout` + `renewLease` 行 235-250 owner-guarded UPDATE —— Stage 22 真实 PG 上首驱的两条 SQL 路径）；
  - `…/forwarding/spi/ForwardingDeliveryResult.java`（`expired()` 工厂行 107 + `Outcome.EXPIRED` 不带 failureCode 行 68-72）；
  - `agent-bus/src/test/java/com/huawei/ascend/bus/forwarding/runtime/C3ForwardingLeaseReclaimAndBreakerIntegrationTest.java`（Stage 20 helper 复用源：`MutableEpochClock`/`fakeDeliveryPort`/`outboxRow` 投影读/boot recipe）。
