---
artifact_type: delivery_projection
version: agent-bus-stage20-review-and-stage21-plan
status: stage-21-completed
source_commit: f65105b5
stage21_planned: 2026-06-23
source_stage20_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage19-review-and-stage20-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
source_l2: architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md
target_module: agent-bus (纯测试 / 验证阶段，无生产代码；不 boot runtime)
---

# agent-bus Stage 20 评审与 Stage 21 计划（多 worker 并发验证：并发 claim 无重复 + 共享断路器单例并发一致 OPEN）

## 0. 结论

提交 `f65105b5`（Stage 20）可以作为 Stage 20 的阶段性成果接受：首次端到端打穿 C3 outbox 的**租约过期回收（卡住持有者）** + **断路器全状态机真实链路**，并把二者编织 —— 证明两个 reclaim 子句（`RETRY_SCHEDULED` + `status='DISPATCHING' AND lease_until<=:now`）与三条 skip 路径（lease 续约失败 / breaker OPEN / deliver 异常）在真实 Postgres 上正确组合，`probeInFlight` 不泄漏。**188 tests green**，ArchUnit green，§6.2 不变，无生产代码改动。**接受**。

但 Stage 20（以及 Stage 16/12 的 deferred 注记）明示留下的两个「单线程验证」盲区仍在：

1. **并发 claim 真实竞争未端到端**：Stage 12 的 `ForwardingJdbcIntegrationTest` 在 2 线程单元测试上证明了 `claimDue` 的 `FOR UPDATE SKIP LOCKED` 无重复 *claim*，Stages 17-20 驱动完整 claim→deliver→ack 链路但**每 tick 只有一个 worker**（Stage 20 场景 A 用「同 worker 下一 tick 不同 lease owner」模拟「另一 worker 接手」，从无两 worker 在同一瞬间对同一共享 outbox 竞争）。N 个真实 worker 各跑 `runOnce` loop 对同一共享 outbox 是否**无重复投递**（deliver 被调多次）从未端到端验证。

2. **共享断路器单例并发正确性未端到端**：Stage 16 接入 `RouteCircuitBreaker`、Stage 20 单线程验证全状态机，但 Stage 16 risks / Stage 20 deferred 都说「单实例跨 worker *线程* 共享在 `synchronized(RouteState)` 下安全；但真实并发 `recordOutcome` 下是否真成立（`consecutiveFailures` 的 read-modify-write 是否丢更新、状态字段是否撕裂）留待生产」。Stage 21 回答这个问题。

Stage 21 = **多 worker 并发验证**（用户在 Stage 20 收尾后选定「仅多 worker 并发验证」，断路器跨重启 DB 持久化 deferred —— 理由见 §5）。复用 Stage 20 的全部测试基础设施（`MutableEpochClock` + `fakeDeliveryPort` + `CONTROL_ONLY` envelope + `@Isolated` + boot recipe），**零新增前置工程、零生产代码**，新增 `ExecutorService` + `CountDownLatch` + `AtomicLong` 三个 test-only 纯 JDK 并发原语。两个新场景各闭合一个盲区。**190 tests green**，§6.2 不变。

**核心论点**：`runOnce` 是**无状态**的（所有计数是局部变量），所以多个 worker 线程/实例安全；并发正确性的真正风险全在**共享可变状态**上 —— (a) outbox 行的并发 claim（`FOR UPDATE SKIP LOCKED` + lease guard 双防线）；(b) `RouteCircuitBreaker` 的 `ConcurrentHashMap<String, RouteState>` + `synchronized(RouteState)` + `probeInFlight`。Stage 21 两个场景分别给共享 outbox（场景 A）与共享 breaker（场景 B）施压。场景 A 的 **delivery 计数 == 20** 是 smoking gun：lease guard（`markAcked` 拒绝 owner 变了的行）是第二道防线 —— 即使两 worker 都把行转 DISPATCHING、只有一个 ack 落地、另一个 skip，持久化终态仍可能看起来对（一条 ACKED）；delivery 计数在 guard **之前**，它计每次 `deliver` 调用 —— 计数 > 20 直接证明了一次本应被 SKIP LOCKED 阻止的重复 claim，== 20 直接证明它成立。

Stage 21 **不裁决** Stage 13 的 push/pull/MQ 哲学张力（仍 H2/H3）—— 多 worker 并发是 outbox/worker/breaker 层语义，与投递是 push 还是 pull 无关；§6.1 不受影响（并发用注入 `TickSource` + 线程池，**非真实调度器**）。§6.2 不变（两场景用 `CONTROL_ONLY` envelope，并发原语/timing helper 都是 test-scope 纯 JDK，不引入 concrete broker/MQ、不写 payload 正文/token 流/Task execution state、不跨租户回退）。

## 1. Stage 20 评审（commit `f65105b5`）

Stage 20 在 Stage 19 重投往返生命周期在手时，闭合了 Stage 19 评审明示的两个端到端盲区：**租约过期回收**与**断路器真实链路**，并把二者交织。

**4 个优点：**

1. **闭合 outbox「不丢消息 + 自愈」承诺的另一半**：Stage 19 验证了 retryable 失败的 RETRY_SCHEDULED reclaim（`next_attempt_at <= :now`），Stage 20 验证了 claimDue 的**第三类 reclaim 子句**（`status='DISPATCHING' AND lease_until <= :now`，卡住持有者回收）—— worker claim 后崩溃/卡住、lease 过期、被下一 tick 回收重投，这是「worker 崩溃不丢消息」的物理保证。场景 A 的 `simulateCrashedDispatch`（raw JDBC UPDATE 推 DISPATCHING + 过期 lease，通过 DDL CHECK）+ 单 tick `claimDue` 申领 → ACKED，端到端闭合。
2. **断路器全状态机 + lease 回收交织（最高价值场景 B）**：把 `RouteCircuitBreaker(2, 122s)` 三态机（CLOSED→OPEN→HALF_OPEN→CLOSED）与 `claimDue` 两 reclaim 子句编织在 4-tick 链路里。tick3 breaker OPEN 短路**留 DISPATCHING**（不释放 lease），tick4 卡住持有者 reclaim 子句把 DISPATCHING 拉回 + HALF_OPEN 探测放行 + ACKED + CLOSED —— 证明 breaker 短路留下的 DISPATCHING 不会永久卡死（lease 过期回收），回收后 breaker 已过冷却转 HALF_OPEN 放行探测，`probeInFlight` 不泄漏，`attempt_count` 在 skip 期间不递增（==2）。四个组件协同正确。
3. **时间可控性设计严谨（两时钟协调 + 真实时钟约束）**：复用 Stage 19 的 `MutableEpochClock`（test-only 纯 JDK）+ 协调多 tick `advanceableTickSource`（`+61s` step > 60s lease 且两 tick 越过 122s 冷却），无需真实 sleep/scheduler（§6.1 守恒）。关键：TickSource yield 前 `advanceTo` MutableEpochClock，使 worker `clockNow` 与 claimDue `:now` 一致；`leaseGuardedUpdate` 用真实 `System.currentTimeMillis()` → tick instant 单调递增 → `lease_until = instant+60s` 始终 > 真实时钟 → 租约防护不误判。
4. **`@Isolated` 稳定性基线**：Stage 19 给两个 context-boot IT 加 `@Isolated` 根治 parent pom surefire 4 路并发 + Spring Boot 4 非线程安全的 flaky。Stage 20 两场景虽不 boot runtime（更轻），仍沿用 `@Isolated` 独占执行 —— 这条稳定性基线 Stage 21 直接继承（多 worker IT 引入真实 `ExecutorService` 线程，`@Isolated` 更必要）。

**2 个盲区（观察，驱动 Stage 21）：**

1. **并发 claim 真实竞争未端到端**：Stage 12 单元测试证明 `FOR UPDATE SKIP LOCKED` 在 2 线程无重复 *claim*，Stages 17-20 驱动完整链路但每 tick 单 worker。N 真实 worker 同瞬间对共享 outbox 竞争 claim、`deliver` 是否被对同一条记录调用多次（投递重复）从未端到端验证。lease guard 是第二道防线（持久化终态可能看起来对），故需要 **delivery 计数** 作为 SKIP LOCKED 成立的直接证据。
2. **共享断路器单例并发正确性未端到端**：`RouteCircuitBreaker` 的 `ConcurrentHashMap` + `synchronized(RouteState)` + `probeInFlight` 在单线程下都验证过，但跨 worker 线程**共享单例**、并发 `recordOutcome` 下 `consecutiveFailures` 是否丢更新、状态转换（CLOSED→OPEN）是否撕裂、`probeInFlight` 是否跨线程泄漏 —— 从未在真实并发下验证。Stage 16 risks 明示「跨 worker 线程共享留待生产」。

Stage 20 DoD：188 tests green，ArchUnit green，§6.2 不变。**接受**。

## 2. Stage 21 范围与设计

### 2.1 为什么（补两个并发盲区）

两个盲区都是 C3 outbox「水平扩展（多 worker）」承诺的组成部分，但并发验证缺位：

| 盲区 | 涉及组件 | 已有覆盖 | 并发缺位 |
|---|---|---|---|
| 并发 claim 真实竞争 | `claimDue` `FOR UPDATE SKIP LOCKED` + lease guard | Stage 12 `ForwardingJdbcIntegrationTest` 2 线程单元（无重复 *claim*）+ Stages 17-20 单 worker 端到端 | N 真实 worker 同瞬间对共享 outbox 竞争，`deliver` 是否被对同一条记录多次调用（**投递重复**）未端到端 |
| 共享 breaker 单例并发 | `RouteCircuitBreaker`（Stage 16）`ConcurrentHashMap` + `synchronized(RouteState)` + `probeInFlight` | `RouteCircuitBreakerTest` 纯状态机单元 + Stage 20 单 worker 端到端 | 跨 worker 线程**共享单例**、并发 `recordOutcome` 下 `consecutiveFailures` 丢更新 / 状态撕裂 / `probeInFlight` 跨线程泄漏，未端到端 |

Stage 21 = 多 worker 并发验证，两个场景各闭合一个。复用 Stage 20 全部测试基础设施，新增三个 test-only 纯 JDK 并发原语（`ExecutorService` / `CountDownLatch` / `AtomicLong`），零新增生产代码、零新增前置工程。

### 2.2 场景 A — 并发 claim 无重复（闭合盲区 1，SKIP LOCKED 端到端）

**注入**：M=20 条 PENDING（同 tenant `tenant-concurrent-claim` / route `route-concurrent-claim`，不同 messageId `msg-0..19`）、N=4 worker（各不同 lease owner `worker-claim-0..3`），共享同一 outbox + `ALWAYS_CLOSED` breaker + **原子计数 delivery port**（每次 `deliver` `deliveryCount.incrementAndGet()` 后返回 `ForwardingDeliveryResult.acked()`）。`CountDownLatch` `startGate` 让 4 worker 同时释放；`Executors.newFixedThreadPool(4)`。

**worker loop**（每 worker 独立线程）：

```
setup:  enqueue M=20 PENDING (同 tenant/route，msg-0..19)
        deliveryCount = AtomicLong(0)
        startGate = CountDownLatch(1)
        4 workers (leaseOwner=worker-claim-{0..3}), 各 new ForwardingDispatcherWorker(
            outbox, outbox, countDeliveryPort, DispatchLeasePolicy.DISABLED, clock,
            ForwardingRetryPolicy.DEFAULT, ALWAYS_CLOSED)
submit 4 to ExecutorService, each:
    await startGate
    loop:
        tick = worker.runOnce(tenant, t0, limit=5, leaseOwner, leaseUntil=t0+120_000)
        totalClaimed += tick.claimed(); totalAcked += tick.acked()
        if tick.claimed()==0: break   // 本 worker 的 claimDue 空，PENDING 耗尽
startGate.countDown(); await all futures
```

因为 `deliver` 同步、ack 在同一 `runOnce` 内完成，worker claim 的行在其 `runOnce` 返回时已 ACKED —— 所有 PENDING 消费完后，每 worker 下一轮 `claimDue` 空，loop 退出。

**断言**：

- `deliveryCount.get() == 20`（**每条恰好投递一次** —— smoking gun）；
- `totalAcked == 20`；
- `totalClaimed == 20`；
- 全 20 条记录 raw JDBC 读 == ACKED；
- 自洽不变量（每 tick `claimed == acked+retried+dlqd+expired+skipped`，聚合自洽）。

**关键证据（smoking gun）**：lease guard（`markAcked` 拒绝 owner 变了的行）是第二道防线 —— 即使两 worker 都把同一条行转 DISPATCHING，只有一个的 ack 落地、另一个 skip（`ForwardingLeaseException` → `skipped`），持久化终态仍可能看起来对（一条 ACKED）。**delivery 计数在 guard 之前**：它计每次 `deliver` 调用，不管后续 ack 成不成功。计数 > 20 直接证明了一次本应被 SKIP LOCKED 阻止的重复 claim（输者撞 lease guard skip 但 delivery 已多调）；== 20 直接证明 SKIP LOCKED 端到端成立。这是 Stage 20 盲区 1 的直接闭合。

### 2.3 场景 B — 共享 breaker 单例并发一致 OPEN（闭合盲区 2）

**注入**：M=12 条（tenant `tenant-concurrent-breaker` / route `route-concurrent-breaker`，messageId `msgb-0..11`）、N=4 worker（lease owner `worker-breaker-0..3`），共享 `RouteCircuitBreaker(2, 3_600_000L, clock)`（`failureThreshold=2` 连续失败→OPEN，**冷却刻意极大 = 1 小时**）+ always-retry delivery port（返回 `ForwardingDeliveryResult.retry(RECEIVER_UNAVAILABLE)`）。`MutableEpochClock` **冻在 t0**（无线程推进它）。

**链路（多 worker 并发 `runOnce`）**：

```
并发驱动：前两次 retryable 失败跨阈值 trip CLOSED→OPEN（synchronized(RouteState) 保护
          consecutiveFailures 的 read-modify-write）；之后任何 worker 的 allowsDelivery
          见 OPEN 即短路（skip，留 DISPATCHING，不消耗 attemptCount）。
时钟冻结 + 远冷却：breaker 只能达 OPEN，不能 HALF_OPEN —— 隔离并发 CLOSED→OPEN 转换
                  （唯一的并发状态写，lost-update 风险所在）作为测试目标。
```

**断言**：

- `breaker.stateOf(routeHandle) == OPEN`（一致性 + 可见性：`ConcurrentHashMap` 的 happens-before 把 OPEN 发布到所有 worker 线程）；
- `totalRetried >= 2`（`failureThreshold` 至少被触发一次，跨 worker 的并发失败正确累加到阈值）；
- `totalAcked == 0`（delivery port 不 ack）；
- `totalClaimed == totalAcked + totalRetried + totalDlqd + totalExpired + totalSkipped`（聚合自洽）；
- 无 worker 抛异常（无 `ConcurrentModificationException`、无非法状态转换、`probeInFlight` 不跨线程泄漏）；
- 无 record 达终结态 ACKED/DLQ/EXPIRED（每行 RETRY_SCHEDULED 或 DISPATCHING）。

**关键证据**：并发 CLOSED→OPEN 转换的 lost-update 风险 —— `synchronized(RouteState)` 防止 `consecutiveFailures` 的 read-modify-write 丢失 / 状态字段撕裂；`ConcurrentHashMap` 的 happens-before 把 OPEN 发布到所有 worker 线程。`breaker.stateOf(routeHandle) == OPEN` + 无异常 + 聚合自洽 + `totalRetried >= 2` 共同证明：跨 worker 共享单例的并发 `recordOutcome` 正确累加到阈值并一致地发布 OPEN。这是 Stage 20 盲区 2 的直接闭合。

### 2.4 时间控制：冻时钟（隔离并发状态写）

Stage 21 **刻意冻结** `MutableEpochClock` 在 t0（场景 A/B 都不推进时钟），而非沿用 Stage 19/20 的 `advanceableTickSource`。理由：

- **场景 A**：delivery port 同步返回 acked、ack 在同一 `runOnce` 内完成，无需跨 tick 推进；worker claim 的行在其 `runOnce` 返回时已 ACKED，下一轮 `claimDue` 空，loop 自然终止。时钟冻在 t0 足够。
- **场景 B**：多线程推进共享 `MutableEpochClock` + HALF_OPEN 探测在飞会引入**真实 race**（冷却检查 vs `recordOutcome`），把测试目标（并发 CLOSED→OPEN 的 lost-update）和无关的 timing race 混在一起。**冻时钟 + 远冷却（1 小时）** 使 breaker 只能达 OPEN 不能 HALF_OPEN，**唯一的并发状态写就是 CLOSED→OPEN 转换本身** —— 正是本测试靶向的 lost-update 风险，干净隔离。
- **真实时钟约束**：lease 设 `t0 + 120_000`（2 分钟），测试远在此内完成（几秒），真实墙钟追不上 lease → `leaseGuardedUpdate` 的 `lease_until > System.currentTimeMillis()` guard 在并发下不误判。冻住的 t0 还使场景 B 的 `RETRY_SCHEDULED` 行不被回收（`nextAttemptAt = t0 + 100ms（DEFAULT base） > t0 = now`，claimDue 的 RETRY_SCHEDULED 回收子句不触发），每 worker 的 loop 在 PENDING 耗尽后干净终止。

### 2.5 边界 + ArchUnit + governance

- **§6.2 不变**：两场景用 `CONTROL_ONLY` envelope；`MutableEpochClock`（冻时钟）/ 计数 delivery port / `CountDownLatch` / `ExecutorService` / `RouteCircuitBreaker`（场景 B）/ `AtomicLong` 都是 **test-scope 纯 JDK 辅助**，不引入 concrete broker/MQ、不写 payload 正文/token 流/Task execution state、不跨租户回退。
- **ArchUnit**：`AgentBusForwardingSpiPurityTest` 扫**生产源码**；Stage 21 **不动生产代码**（只加测试），无需新 ArchUnit 豁免。worker 7 参构造器（Stage 16 引入）注入 breaker / `DispatchLeasePolicy.DISABLED` 是现有 API 用法。
- **§6.1 不受影响**：Stage 21 用注入 `TickSource`（场景 A/B 实际是 worker loop 手动驱动 `runOnce`，非 TickSource 推进）+ `ExecutorService` 线程池，**无真实调度器**（§6.1「总线无调度器」+ H2/H3 裁决均不受影响；多 worker 并发 ≠ 调度器，是注入的线程模型）。
- **decision §8**：加 Stage 21 bullet（正向：闭合 Stage 20 两个并发盲区、delivery 计数为 SKIP LOCKED 证据、breaker 并发一致 OPEN；反向：§6.2 不变、不动生产代码、不裁决 push/pull/MQ、不 boot runtime、breaker 跨重启持久化 deferred）。
- **L2 `forwarding-persistence`**：新增 §22（Stage 21 决策：多 worker 并发验证），含两场景链路、冻时钟设计、smoking gun 论证。
- **L1 4+1 视图**：6 视图 + README/ARCHITECTURE 全部纳入 Stage 21 回灌（沿用 `agent-bus-4plus1-view-rebound` 教训）。
- **ICD + yaml**：`stage21_scope`（delivers `multi-worker-concurrent-claim-no-duplicate-delivery` / `shared-circuit-breaker-singleton-concurrent-consistent-open`；not_delivers `real-retry-scheduler-polling-cadence` / `circuit-breaker-state-persistence-across-restart` / `concurrent-worker-sharding` / `production-code-changes`）；顶部 description 追加 Stage 21 句。

## 3. 关键发现（前置分析）

| # | 发现 | 验证 | 结论 |
|---|---|---|---|
| 1 | `runOnce` 无状态 → 多 worker 线程/实例安全 | 读 `ForwardingDispatcherWorker.runOnce` | 所有计数（claimed/acked/retried/dlqd/expired/skipped）是局部变量，返回 `DispatchTickResult`。多线程调同一 worker 实例或不同实例安全。并发风险全在共享可变状态（outbox 行 / breaker） |
| 2 | `leaseUntilMillisEpoch` 为绝对 epoch（非相对） | 读 `runOnce` 签名 + lease 注释 | `runOnce(tenant, nowMillisEpoch, limit, leaseOwner, leaseUntilMillisEpoch)`，leaseUntil「claimed leases are exclusive until this instant」。多 worker IT 传 `t0 + 120_000`（2 分钟），测试几秒内完成 → 真实时钟追不上 lease → lease guard 不在竞争下失效 |
| 3 | lease guard 是第二道防线（delivery 计数才是 smoking gun） | 读 `markAcked` + worker runOnce 顺序 | `markAcked(messageId, tenant, leaseOwner)` 拒绝 owner 变了的行 → `ForwardingLeaseException` → skipped。即使两 worker 都 claim 同行，持久化终态可能对（一条 ACKED）。delivery 计数在 guard **之前**（计每次 deliver 调用），== 20 才是 SKIP LOCKED 直接证据 |
| 4 | DEFAULT 重试 100ms 退避 + 冻时钟 → RETRY 行不被回收 | 读 `ForwardingRetryPolicy.DEFAULT` + claimDue 回收子句 | `ExponentialBackoff(100ms, 60s, maxAttempts=5)`：`nextAttemptAt = now + 100ms`。claimDue 用注入 `now=t0`，`t0+100ms > t0` → RETRY_SCHEDULED 行不被回收 → worker loop 干净终止（第二循环 claimDue 空）。无需推进时钟 |
| 5 | `DispatchLeasePolicy.DISABLED = (0, 1)` 跳过续约 | 读 `DispatchLeasePolicy` | `renewBeforeExpiryMillis=0` → worker `if (leasePolicy.renewBeforeExpiryMillis() > 0)` 不续约。简化多 worker IT（无需协调续约 timing） |
| 6 | `RouteCircuitBreaker` 并发原语构成 | 读 `RouteCircuitBreaker.java` | `ConcurrentHashMap<String, RouteState>`（happens-before 发布）+ `synchronized(RouteState)`（保护 consecutiveFailures / state / probeInFlight 的 read-modify-write）。`stateOf(routeHandle)` 可观测。场景 B 验证并发 CLOSED→OPEN 无 lost-update |
| 7 | 场景 B 冷却刻意极大隔离测试目标 | 设计选择 | `cooldownMillis=3_600_000`（1 小时）+ 冻时钟 t0 → breaker 只能达 OPEN 不能 HALF_OPEN。唯一并发状态写 = CLOSED→OPEN 转换 = lost-update 风险。隔离了无关的 HALF_OPEN 探测 timing race（那已 Stage 20 单线程验） |
| 8 | `RouteCircuitBreaker` 共享单例跨 worker | worker 7 参构造器注入同一 breaker 实例 | N worker 注入**同一个** `breaker` 引用 → `ConcurrentHashMap` 是共享可变状态。场景 B 验证跨线程共享正确（非跨进程/跨重启） |
| 9 | 两场景都不 boot runtime | 场景 A 用计数 delivery port + ALWAYS_CLOSED；场景 B 用 retry delivery port + breaker | **Stage 21 IT 不 boot `LocalA2aRuntimeHost`**（两场景都用 fake delivery port，不连真实 server）。只需 embedded-postgres + Flyway + JdbcForwardingOutbox，比 Stage 17/18 更轻，与 Stage 19/20 同级 |
| 10 | Stage 20 时间控制基础设施可零改动复用 | 读 `C3ForwardingLeaseReclaimAndBreakerIntegrationTest` helper | `MutableEpochClock` + `fakeDeliveryPort` + `CONTROL_ONLY` envelope + `@Isolated` + boot recipe 全部可复用。Stage 21 只把 `MutableEpochClock` 从「可推进」精简为「冻住」（去掉 advanceTo，因场景无需推进），新增 `ExecutorService`/`CountDownLatch`/`AtomicLong` 三个纯 JDK 并发原语 |
| 11 | `@Isolated` 对多 worker IT 更必要 | Stage 19 `@Isolated` 修复 + Stage 21 引入真实线程 | Stage 21 IT 自己用 `ExecutorService` 起 4 线程跑 `runOnce`；若 parent pom surefire 再 4 路并发跑多个 IT class，线程爆炸。`@Isolated` 让本 IT 独占执行（其他 class 串行后），避免线程叠加。沿用 Stage 19/20 基线 |

## 4. 切片 + MI 表

| MI | 切片 | 产出 |
|---|---|---|
| MI21-001 | 1 场景 A IT（并发 claim 无重复） | `C3ForwardingMultiWorkerConcurrencyIntegrationTest` 场景 A `scenario_a_concurrent_claim_skip_locked_no_duplicate_deliveries`：enqueue M=20 PENDING → 原子计数 delivery port（`AtomicLong` incrementAndGet + 返回 acked）+ `CountDownLatch` startGate + `Executors.newFixedThreadPool(4)` 各不同 lease owner 共享 outbox + `ALWAYS_CLOSED` breaker + `DispatchLeasePolicy.DISABLED` + 冻时钟 t0 → 各 worker loop `runOnce(limit=5)` 直到 `claimDue` 空 → 断言 `deliveryCount==20`（smoking gun）+ `totalAcked==20` + `totalClaimed==20` + 全 20 行 ACKED。boot：embedded-postgres + Flyway + JdbcForwardingOutbox（不 boot runtime）；`@Isolated` |
| MI21-002 | 2 场景 B IT（共享 breaker 并发一致 OPEN） | 场景 B `scenario_b_shared_breaker_singleton_concurrent_open`：enqueue M=12 PENDING → 共享 `RouteCircuitBreaker(2, 3_600_000, clock)` + always-retry delivery port（`retry(RECEIVER_UNAVAILABLE)`）+ 4 worker 共享同一 breaker + 冻时钟 t0 → 并发 `runOnce` 直到空 → 断言 `breaker.stateOf(routeHandle)==OPEN` + `totalRetried>=2` + `totalAcked==0` + 聚合自洽 + 无异常 + 无终结态 record |
| MI21-003 | 3 文档同步 | decision §8 加 Stage 21 bullet + 链接；ICD（边界标题加 Stage 21 + Stage 21 边界条 + Open Issues 两盲区标记已验证 + 并发 worker 分片加注）；yaml（`stage21_scope` + 顶部 description）；L2 `forwarding-persistence` 新增 §22（两场景设计 + 冻时钟 + smoking gun 论证）；L2 `forwarding-outbox-inbox` §10；L1 4+1 视图 6 文件（README/physical/scenarios/development/process/logical/ARCHITECTURE）按 `agent-bus-4plus1-view-rebound` 回灌；本双语文档 |
| MI21-004 | 4 构建验证 + 提交 | `mvn -f agent-bus/pom.xml test` green（188 + 2 并发 IT ≈ 190）；ArchUnit green；§6.2 文本扫描不触发；commit + push（experimental，用户已授权自主推进） |

## 5. deferred + 风险（明示边界）

**风险（需关注）：**

- **场景 A 多 worker loop 终止条件**：worker claim 的行在其 `runOnce` 内同步 deliver+ack 完成，故行被 claim 后即 ACKED，下一轮 claimDue 不会再申领同一条。但若 4 worker 在 PENDING 耗尽后仍循环（因某 worker 在另 3 个 worker 把剩余行 ack 光前恰好 claimDue 非空），需 `tick.claimed()==0` 终止 loop。DEFAULT 重试 100ms 退避 + 冻时钟 t0 保证 RETRY_SCHEDULED 行不被回收（`nextAttemptAt > t0`），故 PENDING 耗尽即终止。若实测发现 loop 不终止，检查 delivery port 是否误返回 retry。
- **场景 B 断言的鲁棒性**：`totalRetried >= 2` 而非精确值 —— 多 worker 并发下哪两个失败先到阈值是非确定的（线程调度），但阈值（2）是确定的。`breaker.stateOf==OPEN` 是确定的（无论哪些 worker 触发，只要累计达 2 即 OPEN 且冷却极大不再转 HALF_OPEN）。聚合自洽不变量是结构性的（每 tick 自洽 → 聚合自洽），确定成立。
- **`@Isolated` 与真实线程叠加**：场景 A/B 各自用 `ExecutorService(4)`；若 surefire 并发跑两个 class，线程爆炸。`@Isolated` 让本 IT 独占执行（其他 class 串行后）规避。若实测仍 flaky，确认 failsafe include 规则（`*IntegrationTest` 是否绕过串行 include）。

**deferred（明示边界，不在 Stage 21 范围）：**

- **真实 scheduler / polling cadence**：Stage 21 用 worker loop 手动驱动 `runOnce`（非 TickSource 推进，非真实 scheduler），**无真实调度器**（§6.1 + H2/H3）。多 worker 并发用注入 `ExecutorService`，是线程模型不是调度器。
- **断路器状态持久化（跨重启落 DB）**：`RouteCircuitBreaker` 状态在内存（`ConcurrentHashMap`），进程重启丢失（回 CLOSED）。**Stage 21 验证的是跨 worker *线程* 共享单例的并发正确性，非跨重启**。跨重启持久化 deferred —— **理由：主流熔断器实现不做跨重启持久化**（Resilience4j 默认内存态、Hystrix 内存态、Spring Cloud CircuitBreaker 抽象层均无内置持久化）；进程重启后回到 CLOSED 重新探测是**预期且合理**的行为（重启通常伴随部署/故障恢复，旧 OPEN 状态可能已过时，重新探测比信任陈旧状态更安全；OPEN 状态的目的是在**单进程生命周期内**保护下游，跨进程失效是 feature 非 bug）。若未来确需跨重启保持，是把 breaker 状态投影到 outbox/router 元数据的独立议题，需独立评审 + H2/H3 裁决，不属 Stage 21。
- **并发 worker 分片**：Stage 21 验证的是**并发 claim 正确性**（无重复投递），非**分片策略**（按 tenant/route 分区把不同 worker 钉到不同子集以减少竞争）。分片策略是运维化/生产化议题（减少锁竞争、提升吞吐），deferred。
- **breaker 参数调优 / per-route 配置**：`failureThreshold`/`cooldownMillis` 用测试注入值（2 / 测试值），生产默认值、per-route 配置 deferred。
- **真实 retry 调度**：Stage 21 冻时钟，DEFAULT 重试退避的退避序列（100ms→200ms→...）在真实 scheduler 下的行为未验（沿用 Stage 19/20 deferred）。
- **`MapEndpointResolver` → registry resolver**：生产 resolver 由 Stage 3 registry 集成实现（registry runtime 物理实现仍 H2/H3）；Stage 21 IT 用 `MapEndpointResolver`/fake port。
- **`openjiuwen.version` 构建债**（Stage 17 盲区，沿用）：同事 `20dc622f` 引入 `com.openjiuwen:agent-core-java` 依赖但 property 未定义。Stage 21 不 boot runtime → 不触发该依赖链，但仍需 `mvn -f agent-bus/pom.xml test` 走 m2 旧 jar 绕过 broken workspace pom。
- **push/pull/MQ 最终裁决**：仍 H2/H3。

## 6. Stage 21 落地总结（**已完成**，2026-06）

Stage 21 按 §2 / §4 计划落地，实际结果与计划一致：

- **场景 A（并发 claim 无重复）**：M=20 条、N=4 worker（各不同 lease owner）共享 outbox + 原子计数 delivery port + `ALWAYS_CLOSED` breaker + `DispatchLeasePolicy.DISABLED` + 冻时钟 t0；`CountDownLatch` 同时释放、`ExecutorService(4)` 各 loop `runOnce(limit=5)` 直到 `claimDue` 空。断言 `deliveryCount==20`（**smoking gun**：delivery 计数在 lease guard 之前，== 20 直接证明 SKIP LOCKED 端到端无重复投递）+ `totalAcked==20` + `totalClaimed==20` + 全 20 行 ACKED。闭合 Stage 20 盲区 1。
- **场景 B（共享 breaker 单例并发一致 OPEN）**：M=12、N=4 worker 共享 `RouteCircuitBreaker(2, 极大冷却, clock)` + always-retry delivery；并发 `runOnce`；冻时钟 t0。断言 `breaker.stateOf(routeHandle)==OPEN` + `totalRetried>=2` + `totalAcked==0` + 聚合自洽 + 无 worker 抛异常 + 无终结态 record。证明 `synchronized(RouteState)` 防并发 CLOSED→OPEN lost-update、`ConcurrentHashMap` happens-before 发布 OPEN 到所有线程。冷却刻意极大隔离测试目标（非复验 HALF_OPEN 探测，那已 Stage 20 单线程验）。闭合 Stage 20 盲区 2。
- **结果**：**190 tests green**（Stage 20 的 188 + 2 并发 IT），ArchUnit green，**无生产代码改动**（纯测试阶段），§6.2 不变，§6.1 不受影响（注入 ExecutorService 线程，非真实调度器）。
- **§4 MI21-003 文档同步**已按计划完成：decision §8 Stage 21 bullet + 链接、ICD（边界标题 + Stage 21 边界条 + Open Issues 两盲区收口 + 并发 worker 分片加注）、yaml（description + `stage21_scope`）、L2 `forwarding-persistence` §22、L2 `forwarding-outbox-inbox` §10、L1 4+1 视图 6 文件（README/physical/scenarios/development/process/logical/ARCHITECTURE）+ 本双语文档 §6。grep 验证全部文档含 Stage 21；`188 tests` 残留均为 Stage 20 时点快照（增量审计惯例），无过时引用。
- **下一步**（未裁决）：Stage 21 是纯多 worker 并发验证阶段，无生产代码增量；C3 outbox 的三条端到端生命线（retry 往返 + 租约回收 + 断路器）至此**全部在单 worker（Stages 17-20）与多 worker（Stage 21）下验证通过**。§5 的 deferred（真实 scheduler、breaker 跨重启持久化、并发 worker 分片、registry resolver、push/pull/MQ 裁决）仍是独立后续 Stage 候选，待用户裁决方向。

## 相关文档

- Stage 20 计划：[`agent-bus-stage19-review-and-stage20-plan`](agent-bus-stage19-review-and-stage20-plan.md)（租约过期 reclaim + 断路器真实链路端到端 —— Stage 21 复用其全部测试基础设施，闭合其明示的两个「单线程验证」deferred 盲区）。
- C3 裁决：[`agent-bus-forwarding-runtime-decision`](../review-packets/agent-bus-forwarding-runtime-decision.md)（`adopted-c3`；§8 Stage 21 许可段）。
- runtime 契约：[`ICD-Agent-Bus-Forwarding-Runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)（Stage 21 边界条 + Open Issues 两盲区收口）。
- 持久化 L2：[`forwarding-persistence`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)（Stage 21 决策：多 worker 并发验证，§22）。
- outbox-inbox L2：[`forwarding-outbox-inbox`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md)（§10 Stage 21 要点）。
- 验证机制源头：
  - `agent-bus/src/main/java/com/huawei/ascend/bus/forwarding/runtime/persistence/jdbc/JdbcForwardingOutbox.java` `claimDue`（`FOR UPDATE SKIP LOCKED` + lease guard，并发 claim 无重复的物理保证）；
  - `…/runtime/RouteCircuitBreaker.java`（Stage 16 三态机 `ConcurrentHashMap` + `synchronized(RouteState)` + `stateOf` 可观测，并发 CLOSED→OPEN 的共享单例）；
  - `…/runtime/ForwardingDispatcherWorker.java`（7 参构造器注入 breaker + `runOnce` 无状态 + lease guard 第二道防线）；
  - `agent-bus/src/test/java/com/huawei/ascend/bus/forwarding/runtime/C3ForwardingLeaseReclaimAndBreakerIntegrationTest.java`（Stage 20 helper 复用源：`MutableEpochClock`/`fakeDeliveryPort`/boot recipe）。
