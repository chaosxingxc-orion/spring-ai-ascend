---
artifact_type: delivery_projection
version: agent-bus-stage18-review-and-stage19-plan
status: stage-18-completed
source_commit: 0ea371da
stage19_planned: 2026-06-23
source_stage18_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage17-review-and-stage18-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
source_l2: architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md
target_module: agent-bus (+ cross-module agent-runtime, test-only, 沿用 Stage 17/18)
---

# agent-bus Stage 18 评审与 Stage 19 计划（C3 重投闭环端到端验证）

## 0. 结论

提交 `0ea371da` 可以作为 Stage 18 的阶段性成果接受：在 Stage 17 首次跨模块端到端（happy path）之上，**首次端到端验证失败路径** + **收口三次 deferred（14→15→17）的 `REMOTE_TASK_FAILED` non-retryable 码**。`C3ForwardingFailurePathIntegrationTest` 双场景（真实 `FailingHandler` FAILED→DLQ + route 不可达→RETRY）证明「真实 handler 产出 SSE FAILED 帧 → `dlq(REMOTE_TASK_FAILED)` → outbox DLQ」与「真实 socket 拒连 → `retry(RECEIVER_UNAVAILABLE)` → outbox RETRY_SCHEDULED」两条链路在真实 Postgres + 真实 A2A server 下成立。**184 tests green**，ArchUnit green，§6.2 不变，无 DDL/SqlCodec/record 改动（码引入 additive）。**接受**。

但 Stage 18 的最大盲区也很清晰：**重投闭环（retry round-trip lifecycle）从未端到端验证**。Stage 18 场景 2 只验证了 RETRY_SCHEDULED 的**入口**（route 不可达 → retry → `attempt_count=1`），而 `JdbcForwardingOutbox.claimDue` 的 RETRY_SCHEDULED reclaim 路径（SQL `OR (status = 'RETRY_SCHEDULED' AND next_attempt_at <= :now)`）—— 即一条记录失败重投后，等到 `next_attempt_at` 到期，下一个 tick 是否真的把它重新 claim、`attempt_count` 是否真的递增、耗尽后是否真的落到 DLQ、或中途恢复是否真的 ACKED —— **整条生命周期从未在真实链路跑过一次**。这正是 C3（database outbox）模式相对于「同步直投」的核心价值主张：**持久化 + 自动重投到成功或 DLQ**，而它的核心承诺目前只有 fake-delivery 的 unit/contract 覆盖。

Stage 19 = 用户在 Stage 18 收尾后选定的**重投闭环端到端验证**（候选 A）。复用 Stage 17/18 的真实基础设施（embedded-postgres + Flyway + `JdbcForwardingOutbox` + 真实 `LocalA2aRuntimeHost`），**零新增前置工程**；新增的只是测试侧的时间可控性 —— 注入 `MutableEpochClock` + 一个产出多 tick 的协调 `TickSource`，让一次 `loop.run` 在「压缩时间」下跑完整个重投生命周期（schedule → 到期 reclaim → 重投 → `attempt_count++` → exhausted→DLQ / recover→ACKED），无需真实 `sleep`、无需真实 scheduler（§6.1「总线无调度器」不破）。

**核心论点**：Stage 14 落地的 `ForwardingRetryPolicy`（overflow-safe 指数退避 + exhausted→DLQ）和 Stage 9/12 落地的 `claimDue` reclaim 路径，目前各自有 unit/contract 覆盖，但**两者的协同 —— policy 决定的 `nextAttemptAt` 写入 outbox、claimDue 据此到期 reclaim、worker 据 `attemptCount` 判定 exhausted —— 从未在一个真实 Postgres outbox 上端到端跑通**。任何一环的接线错误（`nextAttemptAt` 时区/单位错、claimDue `<=` 写成 `<`、`attemptCount` 传成 ++ 前的值、exhausted 阈值差一）都不会被现有测试发现。Stage 19 用真实持久化 + 可控时间把这条链路打穿。

Stage 19 **不裁决** Stage 13 的 push/pull/MQ 哲学张力（仍 H2/H3）—— 重投闭环是 outbox/worker 层语义，与投递是 push 还是 pull 无关（任何 transport 模型下，失败重投的治理都由 retry policy + claimDue reclaim 承担）。沿用 Stage 15 已选的 T1（同步等完成）+ Stage 10 测试 `TickSource`（无真实 scheduler）。§6.2 不变（重投验证用 `CONTROL_ONLY` envelope，可控时钟/`TickSource`/`RecoveringHandler` 都是 test-scope 纯 JDK 辅助，不引入 concrete broker/MQ、不写 payload 正文/token 流/Task execution state）。

## 1. Stage 18 评审（commit `0ea371da`）

Stage 18 在 Stage 17 真实 runtime 基础设施在手时，一次完成两件互相解锁的事：端到端失败路径 + 收口三次 deferred 的 `REMOTE_TASK_FAILED` 码。两件事天然耦合 —— 要端到端验证失败处理，就得先有精确的失败码（否则 FAILED 终态仍保守重试，IT 只能验证 retry 而非 DLQ）；码一旦引入，Stage 17 的真实 runtime 立刻能证明新映射端到端成立。

**4 个优点：**

1. **失败分层论证严谨（业务层 vs infra 层）**：`FAILED/CANCELED/REJECTED`（远程 agent 主动报告的业务终态失败）→ `NON_RETRYABLE` → DLQ，区别于 infra 层 `RECEIVER_UNAVAILABLE`/`DELIVERY_TIMEOUT`（瞬时，retryable）。「对确定失败的远程 task 反复重投同输入 = bomber（轰炸下游）」的论证成立，且与 Stage 14 retry policy **正交** —— non-retryable 直达 DLQ 不消耗 retry budget、不经退避；retryable 走指数退避耗尽 → DLQ。`AUTH_REQUIRED` 例外（「需认证」非「task 失败」，认证可恢复，保留 retry）保留得有分寸。
2. **最小足迹（关键设计洞察）**：新增一个 enum 值，但 **DDL/SqlCodec/record 全零改动** —— ① outbox `ck_outbox_failure_code` CHECK 只校验 `status↔last_failure_code` null 配对、**不枚举码值**（→ 无 migration）；② `ForwardingSqlCodec.decodeFailureCode` 遍历 `values()` 查 wireCode（→ 自动覆盖新码）；③ `ForwardingDeliveryResult.dlq(code)` compact constructor 只拒 `dedup()`/null、接受任意 non-retryable 码（→ 无 record 改）。这是「码引入 additive、ICD `compatibility.additive_fields_allowed: true`」论点的硬证据，也把 Stage 15 当年回避「Stage 9 classification / DDL / ICD 连锁」的代价降到最低。
3. **`isFinal()` if-chain 而非 case label（面向未来）**：`A2aForwardingDeliveryPort` 终态映射从 `switch(state)` 改成 `COMPLETED/INPUT_REQUIRED→acked`、`isFinal()→dlq(REMOTE_TASK_FAILED)`、`default→retry`。好处有二：(a) 未来新增 A2A final state（如新的拒绝态）自动正确分类为 DLQ；(b) 规避 `CANCELED/CANCELLED` 拼写差异风险（if-chain 用 `isFinal()` 语义而非逐字面量匹配）。
4. **零新增前置工程 + `freeUnusedPort()` 真实拒连**：复用 Stage 17 的 `LocalA2aRuntimeHost` + embedded-postgres boot recipe；`freeUnusedPort()`（bind `ServerSocket(0)` 后 close 的瞬时空闲端口）产出**真实 socket 级 connection refused**，绕开 Stage 15 发现的「SDK 把非 2xx 当静默空流（→ `DELIVERY_TIMEOUT`）、真正 socket 断开才走 `errorConsumer`（→ `RECEIVER_UNAVAILABLE`）」陷阱，确保场景 2 走对分类路径。

**3 个盲区（观察，驱动 Stage 19）：**

1. **重投闭环未端到端验证（最大盲区）**：Stage 18 场景 2（route 不可达 → RETRY）只验证了 RETRY_SCHEDULED 的**入口** —— 一次失败后 `status=RETRY_SCHEDULED`、`attempt_count=1`、`next_attempt_at` 已置。但 `JdbcForwardingOutbox.claimDue` 的 RETRY_SCHEDULED reclaim 路径（SQL `OR (status = 'RETRY_SCHEDULED' AND next_attempt_at <= :now)`，行 219）**从未在真实链路被触发并验证**：那条 `attempt_count=1` 的记录，等到 `next_attempt_at` 到期后，下一个 tick 是否真的被 claimDue 重新 claim、`attempt_count` 是否真的从 1 递增到 2、Stage 14 retry policy 的 `exhausted` 判定在 `attemptCount` 跨多次重投后是否正确、耗尽后是否真的 `moveToDlq` —— 以及最关键的「重投到中途恢复成功是否 ACKED」—— **整条 C3 outbox 的核心生命周期从未端到端跑过**。这是 outbox 模式相对于「同步直投 + 内存重试」的全部存在理由，目前只有 `InMemoryForwardingDelivery`/`FakeDeliveryPort` 的 unit/contract 覆盖。
2. **lease 过期 reclaim 未端到端**：`claimDue` SQL 行 220 的第三类 reclaim（`status='DISPATCHING' AND lease_until <= :now`）—— 一个 worker claim 后 lease 过期、另一个 worker 接手 —— 同样只在 fake/contract 覆盖，真实 Postgres 下未验证（需控制 lease TTL 过期 + 多 worker，复杂度高，Stage 19 不含，见 §5）。
3. **breaker 真实链路未验证**：Stage 16 `RouteCircuitBreaker` 三态机（CLOSED→OPEN→HALF_OPEN）在真实失败链路（连续真实失败触发 OPEN 短路 → 冷却 → HALF_OPEN 探测 → 恢复）从未跑过，只有 `RouteCircuitBreakerTest` 纯状态机单元 + worker 契约覆盖（Stage 19 不含，见 §5）。

Stage 18 DoD：184 tests green，ArchUnit green，§6.2 不变。**接受**。

## 2. Stage 19 范围与设计

### 2.1 为什么（补重投闭环盲区）

C3（database outbox）模式相对「同步直投」的全部价值主张是 **持久化 + 自动重投到成功或 DLQ**。这条主张由三个已落地组件协同实现，但**协同从未端到端验证**：

| 组件 | 落地 Stage | 单独覆盖 | 协同盲区 |
|---|---|---|---|
| `ForwardingRetryPolicy`（`nextAttemptAt` + `exhausted`） | 14 | unit（overflow-safe 退避、exhausted 阈值） | policy 算的 `nextAttemptAt` 写入 outbox 后，claimDue 是否据此到期 reclaim —— **未验证** |
| `JdbcForwardingOutbox.claimDue` RETRY_SCHEDULED reclaim | 9/12 | contract（SQL 语义、SKIP LOCKED） | reclaim 后 worker 拿到的 `attemptCount` 是否正确驱动 `exhausted` 判定 —— **未验证** |
| `ForwardingDispatcherWorker` RETRY_SCHEDULED 分支 | 14 | unit/contract（fake delivery） | `attemptCount` 传给 policy 是「++ 前」还是「++ 后」、耗尽→DLQ、恢复→ACKED —— **未真实跑过** |

Stage 18 场景 2 把第一条记录送进 RETRY_SCHEDULED 后就停了（单 tick）。Stage 19 **继续往后跑**：用可控时间把这条记录推过 `next_attempt_at`，让它被 reclaim、重投、`attempt_count` 递增，直到两个出口之一 —— exhausted→DLQ（恒失败）或 recover→ACKED（中途恢复）。这打穿整条生命周期。

### 2.2 时间可控性：`MutableEpochClock` + 协调多 tick `TickSource`（核心新增）

Stage 17/18 用 `EpochClock.SYSTEM`（真实系统时钟）+ 单 tick `TickSource`（`oneTickThenStop`），因为它们只跑一个 tick。Stage 19 要跑多个 tick 且每个 tick 的「时刻」必须可控地推进到上一 tick 写入的 `next_attempt_at` 之后，否则 claimDue 不会 reclaim。真实 `sleep` 等退避（默认 base 100ms、cap 60s）既慢又 flaky，不可取。

**新增（test-scope，纯 JDK）**：

- **`MutableEpochClock implements EpochClock`**：持一个可变 `long current`，`epochMillis()` 返回它，`advanceTo(long)` / `advanceMillis(long)` 推进。注入 `ForwardingDispatcherWorker`（5/6 参构造器），使 worker 的 `clockNow = clock.epochMillis()`（delivery instant + `nextAttemptAt` 计算基准）可控。
- **协调多 tick `TickSource`**：产出一个递增的 tick instant 序列（每个比上一个 + 一个大于 retry cap 的步长，如 `+61_000ms`，确保恒 > 任何 `next_attempt_at`），并在每次 `nextTickMillisEpoch()` 返回前把 `MutableEpochClock` 推进到同一时刻 —— 这样 `claimDue` 的 `:now`（tick instant）与 worker 的 `clockNow`（`clock.epochMillis()`）始终一致。产出 N 个 tick 后 `OptionalLong.empty()` 停。

**两个时钟协调的必要性**（这是 Stage 19 最易踩的坑）：

- `claimDue` 的 `:now` = `runOnce` 的 `nowMillisEpoch` = `DispatchLoop.run` 从 `TickSource` 拿的 tick instant；
- `nextAttemptAt(code, attemptCount, clockNow)` 的 `clockNow` = worker 内 `clock.epochMillis()`（注入时钟）。

若两者不同源，`nextAttemptAt` 写入的值与 claimDue 比较的 `:now` 会错位（policy 用时钟 A 算 `nextAttemptAt`、claimDue 用时钟 B 的 tick instant 比较），reclaim 行为不可预测。让 `MutableEpochClock` 与 `TickSource` 共享同一可变时刻（TickSource 推进时同步推进 clock）保证两者一致。

**`leaseGuardedUpdate` 用真实 `System.currentTimeMillis()` 的协调约束**（前置分析确认）：`markAcked`/`scheduleRetry`/`moveToDlq` 都走 `leaseGuardedUpdate`，其 lease guard `WHERE ... AND lease_until > :now` 里的 `:now` 是 `System.currentTimeMillis()`（**真实**系统时钟，非注入时钟）。这意味着可控时钟推进到的 tick instant 必须 **单调递增且 ≥ 测试启动时刻 T0**（`T0 = System.currentTimeMillis()` 起步），否则 lease_until（= tick instant + leaseDuration，也在「未来」）会 ≤ 真实 now 导致 guard 误判 lease 过期 → `ForwardingLeaseException` → skipped。本设计 tick instant 序列从 T0 起步单调递增，且每次 +61s 后 lease_until 更在未来，真实 System.currentTimeMillis 永远 < lease_until，guard 不误判。**不碰 `leaseGuardedUpdate` 的真实时钟**（那是生产代码，lease 过期回收的真实语义由它保证，Stage 19 不动生产代码）。

### 2.3 小 retry policy 注入（加速 exhausted→DLQ）

默认 `ForwardingRetryPolicy.DEFAULT`（maxAttempts=5）需 6 次尝试才 exhausted，tick 序列长。Stage 19 注入一个**小 maxAttempts 的 policy**（如 `new ExponentialBackoff(100L, 60_000L, 3, () -> 0L)`，maxAttempts=3）加速：4 个 tick（attempt 0/1/2 失败 retry、attempt 3 exhausted→DLQ）。注入 via worker 6 参构造器。

> 语义不变：小 maxAttempts 只缩短重投轮数，`exhausted(attemptCount) = attemptCount >= maxAttempts` 与 `nextAttemptAt` 的退避数学与生产一致，验证的是同一接线（policy ↔ claimDue ↔ worker），只是更快。

### 2.4 场景 A — 恒失败重投 → exhausted→DLQ（核心场景）

**复用 Stage 18 场景 2 的不可达 route**（`freeUnusedPort()` → 真实 socket 拒连 → `retry(RECEIVER_UNAVAILABLE)`），不需 runtime（deliver 在连真实 server 前就失败）。boot recipe 沿用 Stage 17/18（embedded-postgres + Flyway + `JdbcForwardingOutbox` + `spring.autoconfigure.exclude`），但**不 boot runtime**（场景 A 不需要；若 IT 类统一 boot runtime，场景 A 的 resolver 指 `freeUnusedPort` 也不碰 runtime，无害，见 §2.6）。

链路（maxAttempts=3，4 ticks）：

```
tick 1 (now=T0):            claimDue reclaim PENDING → DISPATCHING
                            → deliver → socket 拒连 → retry(RECEIVER_UNAVAILABLE)
                            → exhausted(0)? no → nextAttemptAt(receiver_unavailable, 0, T0)=T0+100
                            → scheduleRetry → RETRY_SCHEDULED, attempt_count=1, next_attempt_at=T0+100
tick 2 (now=T0+61000):      claimDue reclaim RETRY_SCHEDULED (next_attempt_at=T0+100 <= T0+61000) ✓
                            → DISPATCHING → deliver 拒连 → retry
                            → exhausted(1)? no → scheduleRetry → attempt_count=2, next_attempt_at=T0+61200
tick 3 (now=T0+122000):     reclaim → deliver 拒连 → retry
                            → exhausted(2)? no → scheduleRetry → attempt_count=3, next_attempt_at=T0+122200
tick 4 (now=T0+183000):     reclaim → deliver 拒连 → retry
                            → exhausted(3)? YES (3>=3) → moveToDlq(receiver_unavailable) → DLQ
```

**断言**（聚合 `DispatchTickResult` + raw JDBC `outboxRow` 投影读，复用 Stage 18 helper）：

- `tick.claimed() == 4`（同一条记录被 claim 4 次：1 次 PENDING + 3 次 RETRY_SCHEDULED reclaim）；
- `tick.retried() == 3`（前 3 次失败 retry）；
- `tick.dlqd() == 1`（第 4 次 exhausted→DLQ）；
- `tick.acked() == 0`、`tick.expired() == 0`、`tick.skipped() == 0`；
- 自洽不变量 `claimed == acked+retried+dlqd+expired+skipped`；
- `outboxRow`：`status == DLQ`、`attempt_count == 3`、`last_failure_code == receiver_unavailable`。

**关键证据**：`tick.retried() == 3` + `outboxRow.attempt_count == 3` —— 证明 claimDue **真的 reclaim 了 RETRY_SCHEDULED 记录 3 次**（否则 attempt_count 永远停在 1，retried==1）。这是 Stage 18 盲区 1 的直接闭合。

### 2.5 场景 B — 重投到中途恢复 → ACKED（证明重投自愈）

场景 A 只验证了「失败重投到耗尽 DLQ」一个出口。重投闭环的另一半是「重投到中途恢复成功 → ACKED」—— 证明 outbox 不是无限轰炸，而是**失败重试到成功为止**。这需要 deliver 结果随重投轮次变化（前 N 次失败、第 N+1 次成功）。

**`RecoveringHandler`（test handler，照 Stage 17 `StubHandler`/Stage 18 `FailingHandler` 范式）**：持有 `AtomicInteger` 计数，`resultAdapter` 把前 `failAfter`（如 2）次 execute 映射为 `AgentExecutionResult.failed(...)`、之后映射为 `completed(...)`。boot 真实 `LocalA2aRuntimeHost`（`RuntimeApp.create(new RecoveringHandler(2)).run(LocalA2aRuntimeHost.port(0))`）。

链路（maxAttempts 足够大如 5，3 ticks）：

```
tick 1 (now=T0):       claimDue reclaim PENDING → DISPATCHING → deliver → 真实 /a2a
                       → RecoveringHandler count=1 (<=2 → failed) → SSE FAILED
                       → retry(RECEIVER_UNAVAILABLE)?  ← 注意：见下「终态映射交叉」
```

**终态映射交叉（需在 plan 评审时确认）**：`RecoveringHandler` 走 `AgentExecutionResult.failed` → 真实 SSE FAILED 帧 → Stage 18 的 `A2aForwardingDeliveryPort` 终态映射现在是 **`dlq(REMOTE_TASK_FAILED)`**（Stage 18 把 FAILED 从保守 retry 改成 DLQ）。这意味着场景 B 若用 `failed`，第 1 次就会 `dlq(REMOTE_TASK_FAILED)` 直达 DLQ，**根本不会 retry** —— 与「重投到恢复」目标矛盾。

两条解法（择一，倾向解法 1）：

- **解法 1（推荐）**：`RecoveringHandler` 的前 `failAfter` 次不返回 `failed`，而返回一个**会触发 retryable `retry` 的状态**。但 A2A 协议里 handler 主动返回的终态只有 completed/failed/canceled/rejected/input_required，都是终态（→ DLQ 或 acked），没有「瞬时失败请重试」的 handler 返回值 —— retryable 失败只能是 **transport 层**（超时/拒连/反压），不是 handler 业务返回。所以「重投到恢复」的真实语义是：**前 N 次 transport 失败、第 N+1 次 transport 成功且 handler completed**。场景 B 因此应模拟 **transport 层的间歇性失败**，而非 handler 业务失败。
- **解法 2**：场景 B 用一个**可控的 `ForwardingDeliveryPort` 替身**（fake，注入结果序列 `[retry(RECEIVER_UNAVAILABLE), retry(RECEIVER_UNAVAILABLE), acked()]`），而非真实 runtime + handler。这放弃了「真实 deliver」但保留了真实持久化（claimDue reclaim 真实 SQL）+ 可控时间，且语义正确（transport 间歇性失败 → 恢复）。代价是 deliver 不是真实链路。

**倾向解法 2**：场景 A 已用**真实 transport 失败**（不可达 route，socket 拒连）证明 reclaim + 递增 + DLQ 出口，deliver 的真实性已由场景 A 担保；场景 B 的核心是证明**重投到 ACKED 出口**（claimDue reclaim 后 worker 走 ACKED 分支），deliver 结果序列只是手段，用 fake port 注入 `[retry, retry, ack]` 即可，且语义上更贴合「transport 间歇性失败后恢复」的真实重投场景（handler 业务失败本就该 DLQ 而非 retry，Stage 18 已论证）。fake port 与真实 `JdbcForwardingOutbox` 组合，claimDue reclaim 仍是真实 SQL —— 场景 B 验证的重投闭环持久化层行为是真的，只有 deliver 结果是注入的。

> **plan 评审点**：场景 B 用 fake delivery port（解法 2）还是另想真实 transport 间歇性失败（如 MockWebServer 前两次 503、第三次 200，但 SDK 503 走静默空流→`DELIVERY_TIMEOUT` 而非拒连，需确认能否触发 retry）。倾向解法 2（fake port），明示其「deliver 注入、持久化真实」边界。

**断言**（解法 2，fake port `[retry, retry, ack]`，3 ticks）：

- `tick.claimed() == 3`、`tick.retried() == 2`、`tick.acked() == 1`、`tick.dlqd() == 0`；
- 自洽不变量；
- `outboxRow`：`status == ACKED`、`attempt_count == 2`（两次 retry 后第三次成功）。

### 2.6 统一 boot 还是分离 boot

Stage 18 已建立「统一 boot runtime（`FailingHandler`）+ 部分 @Test 用 `freeUnusedPort` 不碰 runtime」范式（场景 2 不碰 runtime）。Stage 19 照此：

- **场景 A**（不可达 route）：不碰 runtime。无论 IT 类是否 boot runtime 都能跑（resolver 指 `freeUnusedPort`）。
- **场景 B**（解法 2 fake port）：不碰 runtime（fake port 不连真实 server）。**也不需要 boot runtime**。

因此 Stage 19 的 `C3ForwardingRetryLifecycleIntegrationTest` **可不必 boot 真实 runtime**（两场景都不需要）—— 比 Stage 17/18 更轻（省掉 `LocalA2aRuntimeHost` + `spring.autoconfigure.exclude` 的 A2A server boot）。只需 embedded-postgres + Flyway + `JdbcForwardingOutbox` + 场景 A 的 `A2aForwardingDeliveryPort`（指 `freeUnusedPort`）+ 场景 B 的 fake delivery port。这反而**降低了 IT 的启动成本和 flaky 面积**。

> 若评审倾向保留「真实 runtime」一致性（与 Stage 17/18 同构），可仍 boot runtime，两场景不碰它（无害）。但既然两场景都不需要 runtime，**不 boot 更干净**（少一个真实 Spring 上下文）。plan 评审确认。

### 2.7 边界 + ArchUnit + governance

- **§6.2 不变**：重投验证用 `CONTROL_ONLY` envelope；`MutableEpochClock`/多 tick `TickSource`/`RecoveringHandler` 或 fake delivery port 都是 **test-scope 纯 JDK 辅助**，不引入 concrete broker/MQ、不写 payload 正文/token 流/Task execution state、不跨租户回退。
- **ArchUnit**：`AgentBusForwardingSpiPurityTest` 扫**生产源码**；Stage 19 **不动生产代码**（只加测试），无需新 ArchUnit 豁免。worker 6 参构造器注入小 retry policy 是现有 API 用法。
- **decision §8**：加 Stage 19 bullet（正向：重投闭环端到端打穿 claimDue RETRY_SCHEDULED reclaim + 两个出口；反向：§6.2 不变、不动生产代码、不裁决 push/pull/MQ、不 boot runtime）。
- **L2 `forwarding-persistence`**：新增 §20（Stage 19 决策：重投闭环端到端），含时间可控性设计（MutableEpochClock + 协调 TickSource）、两时钟协调约束、两场景链路图。
- **L1 `README`/`physical`**：C3 标题 + 转发边界 + §5.2 加 Stage 19。
- **ICD + yaml**：`stage19_scope`（delivers `retry-round-trip-lifecycle-end-to-end` / `claim-due-reclaims-retry-scheduled-verified` / `attempt-count-increment-end-to-end` / `exhausted-to-dlq-end-to-end` / `recover-to-acked-end-to-end`；not_delivers `lease-expiry-reclaim-end-to-end` / `circuit-breaker-real-link` / `production-real-scheduler`）；顶部 description 追加 Stage 19 句。

## 3. 关键发现（前置分析）

| # | 发现 | 验证 | 结论 |
|---|---|---|---|
| 1 | claimDue 的 RETRY_SCHEDULED reclaim SQL | 读 `JdbcForwardingOutbox.claimDue`（行 210-224） | `WHERE (status='PENDING' OR (status='RETRY_SCHEDULED' AND next_attempt_at <= :now) OR (status='DISPATCHING' AND lease_until <= :now)) ... FOR UPDATE SKIP LOCKED`；`:now` = `runOnce` 的 `nowMillisEpoch` = TickSource tick instant。**RETRY_SCHEDULED reclaim 路径存在且正确，但从未端到端触发验证** |
| 2 | worker RETRY_SCHEDULED 分支的 attemptCount 语义 | 读 `ForwardingDispatcherWorker.runOnce`（行 267-285） | `retryPolicy.exhausted(record.attemptCount())` → DLQ；否则 `nextAttemptAt(code, record.attemptCount(), clockNow)` → `scheduleRetry`（内部 `attemptCount++`）。`record.attemptCount()` 是「已记录重试数」（首次失败=0）。`maxAttempts=5` → 投递1次+重投5次，第6次（attemptCount=5）exhausted→DLQ |
| 3 | claimDue 的 `:now` 与 nextAttemptAt 的 `clockNow` 不同源 | 读 worker L215 `clockNow = clock.epochMillis()` vs claimDue 用 `nowMillisEpoch`（runOnce 参数） | **两时钟必须协调**：TickSource 推进时同步推进 MutableEpochClock，使 tick instant == clockNow。否则 nextAttemptAt（时钟A算）与 claimDue `:now`（时钟B tick instant）错位，reclaim 不可预测 |
| 4 | `leaseGuardedUpdate` 用真实 `System.currentTimeMillis()` | 读 `JdbcForwardingOutbox.leaseGuardedUpdate`（行 293） | markAcked/scheduleRetry/moveToDlq 的 lease guard `lease_until > System.currentTimeMillis()`（真实时钟）。**可控时钟推进的 tick instant 必须 ≥ T0 且单调递增**，否则 lease_until ≤ 真实 now → guard 误判过期 → skipped。本设计 T0 起步 + 单调递增 + 每次 +61s（lease_until 更在未来），不误判 |
| 5 | retry policy 默认值 + exhausted 阈值 | 读 `ForwardingRetryPolicy.DEFAULT`（行 93-94）+ `ExponentialBackoff.exhausted`（行 154） | DEFAULT = `ExponentialBackoff(100ms, 60s, maxAttempts=5, jitter=0)`；`exhausted(attemptCount) = attemptCount >= maxAttempts`。Stage 19 注入 maxAttempts=3 加速（4 ticks → DLQ），语义不变 |
| 6 | 无现成可控 EpochClock 实现 | grep `implements EpochClock` in test | **测试中无可控 clock**（只有 `SYSTEM = System::currentTimeMillis`）。Stage 19 需新建 `MutableEpochClock`（test helper，纯 JDK） |
| 7 | Stage 18 `outboxRow` 投影读 helper 可复用 | 读 `C3ForwardingFailurePathIntegrationTest.outboxRow`（行 282-294） | raw JDBC `SELECT last_failure_code, attempt_count, next_attempt_at`，返回 Map。Stage 19 IT 直接复用验证中间/最终态 |
| 8 | DispatchLoop.run 聚合多 tick | 读 `ForwardingDispatchLoop.run`（行 84-122） | 每个 TickSource instant 跑一个 `runOnce`，聚合 DispatchTickResult。TickSource 产出 N instant 后 empty 停。单次 `loop.run` 即可跑完 N ticks 的重投生命周期，聚合结果天然含 `retried`/`dlqd`/`acked` 计数 |
| 9 | 终态映射交叉（场景 B） | 读 Stage 18 `A2aForwardingDeliveryPort` 终态映射 | handler 返回 `failed` → SSE FAILED → Stage 18 映射 `dlq(REMOTE_TASK_FAILED)`（非 retry）。故场景 B 不能用 handler 业务失败模拟「重投到恢复」（第 1 次就 DLQ）。retryable 失败只能是 transport 层。**场景 B 用 fake delivery port 注入 `[retry, retry, ack]`**（解法 2），语义贴合「transport 间歇性失败后恢复」 |
| 10 | 两场景都不需真实 runtime | 场景 A 用 freeUnusedPort（socket 拒连，deliver 前失败）；场景 B 用 fake port（不连 server） | **Stage 19 IT 可不 boot `LocalA2aRuntimeHost`**，比 Stage 17/18 更轻（省 A2A server boot + `spring.autoconfigure.exclude`）。只需 embedded-postgres + Flyway + JdbcForwardingOutbox |

## 4. 切片 + MI 表

| MI | 切片 | 产出 |
|---|---|---|
| MI19-001 | 1 场景 A IT + 时间可控性基础设施 | 新 `C3ForwardingRetryLifecycleIntegrationTest`：`MutableEpochClock`（test helper，纯 JDK，`advanceTo`/`advanceMillis`）+ 协调多 tick `TickSource`（产出 N 个 +61s 步长 tick instant，同步推进 clock，N 后 empty 停）；场景 A `dispatch_loop_retries_to_exhaustion_then_dlq`：`A2aForwardingDeliveryPort` 指 `freeUnusedPort` → 注入 maxAttempts=3 retry policy → 4 ticks → 断言 `claimed==4, retried==3, dlqd==1` + 自洽 + `outboxRow`（status=DLQ, attempt_count=3, last_failure_code=receiver_unavailable）。boot：embedded-postgres + Flyway + JdbcForwardingOutbox（**不 boot runtime**）。worker 6 参构造器注入 MutableEpochClock + 小 retry policy |
| MI19-002 | 2 场景 B IT（recover→ACKED） | 场景 B `dispatch_loop_recovers_to_acked_after_intermittent_failure`：fake `ForwardingDeliveryPort`（注入结果序列 `[retry(RECEIVER_UNAVAILABLE), retry(RECEIVER_UNAVAILABLE), acked()]`）+ maxAttempts=5（确保不 exhausted）+ 3 ticks → 断言 `claimed==3, retried==2, acked==1` + 自洽 + `outboxRow`（status=ACKED, attempt_count=2）。明示边界：deliver 结果注入、claimDue reclaim 持久化真实 |
| MI19-003 | 3 文档同步 | decision §8 加 Stage 19 bullet（正向 + 反向）；ICD（边界标题加 Stage 19 + Stage 19 边界条 + Open Issues 重投闭环收口）；yaml（`stage19_scope` + 顶部 description）；L2 `forwarding-persistence` 新增 §20（时间可控性设计 + 两时钟协调 + 两场景链路图）；L1 `README` C3 标题 + `§5.2` + `physical` 转发边界 + §2；本双语文档 |
| MI19-004 | 4 构建验证 + 提交 | `mvn -f agent-bus/pom.xml test` green（184 + 2 重投 IT ≈ 186+）；ArchUnit green；§6.2 文本扫描不触发；commit + push（experimental，用户已授权） |

## 5. deferred + 风险（明示边界）

**风险（需关注）：**

- **两时钟协调（最易踩的坑）**：`MutableEpochClock` 必须与多 tick `TickSource` 共享同一可变时刻（TickSource 推进时同步 advanceTo），否则 `nextAttemptAt`（clockNow 算）与 claimDue `:now`（tick instant）错位，reclaim 行为不可预测。IT 里封装一个 `advanceableTickSource(clock, baseInstant, stepMillis, ticks)` 助手集中这个协调，避免每个测试手写。
- **`leaseGuardedUpdate` 真实时钟约束**：tick instant 必须 ≥ T0（`System.currentTimeMillis()` 起步）且单调递增。本设计 T0 起步 + 每次 +61s 满足；但**不可让 tick instant 倒退或停滞**（否则 lease_until ≤ 真实 now → guard 误判 → skipped → 测试假失败）。IT 注释明示此约束。
- **场景 B 终态映射交叉**：handler 业务失败（`AgentExecutionResult.failed`）经 Stage 18 终态映射直达 DLQ（`REMOTE_TASK_FAILED`），**不会 retry**。故场景 B 不能用真实 runtime + 业务失败模拟「重投到恢复」，须用 fake delivery port 注入 transport 层 retryable 序列（解法 2）。这是 Stage 18 精确化终态映射的**有意后果**（业务失败该 DLQ、transport 失败该 retry），非 bug —— 但让「真实 runtime 重投到恢复」无法用 handler 模拟，是 Stage 19 场景 B 用 fake port 的根因，plan 评审需确认接受此边界。
- **场景 A 不可达 route 的 attempt_count 读法**：`outboxRow` 复用 Stage 18 helper 读 `attempt_count`；exhausted→DLQ 时 attempt_count 应 == maxAttempts（scheduleRetry 在第 maxAttempts 次 ++ 后，下一次 exhausted 判定用 ++ 后的值 → moveToDlq，attempt_count 停在 maxAttempts）。需确认 `moveToDlq` 不再 ++ attempt_count（读 `JdbcForwardingOutbox.moveToDlq` 确认 SET 片段不 bump attempt）。

**deferred（明示边界，不在 Stage 19 范围）：**

- **lease 过期 reclaim 端到端（盲区 2）**：claimDue SQL 行 220 第三类 reclaim（`status='DISPATCHING' AND lease_until <= :now`）—— worker claim 后 lease 过期、另一 worker 接手 —— 需控制 lease TTL 过期（让 lease_until < tick instant）+ 多 worker 实例。但 `leaseGuardedUpdate` 用真实 `System.currentTimeMillis()`，要让 lease 真实过期需真实 sleep 或改生产时钟注入点（后者破生产代码）。复杂度高，Stage 19 不含，作独立后续 Stage。
- **breaker 真实链路端到端（盲区 3）**：Stage 16 `RouteCircuitBreaker` 三态机在真实失败链路（连续真实失败→OPEN→冷却→HALF_OPEN→恢复）未验证。复用 Stage 19 场景 A 的连续失败 + 注入 EpochClock 推进冷却可验证，但属另一关注点，Stage 19 不含（可作 Stage 19 之后的独立 Stage，或合并）。
- **真实 scheduler / polling / 多 worker 分片**：Stage 19 用注入 `TickSource` 推进时间，**无真实 scheduler**（§6.1 + H2/H3）。多 worker 并发（SKIP LOCKED 真实竞争）也未验证。
- **`MapEndpointResolver` → registry resolver**：生产 resolver 由 Stage 3 registry 集成实现（registry runtime 物理实现仍 H2/H3）；Stage 19 IT 场景 A 用 `MapEndpointResolver` 指 `freeUnusedPort`，场景 B 用 fake port，均不接 registry。
- **production 依赖 agent-runtime / 真实 agent handler**：Stage 19 不 boot runtime（两场景都不需要），不引入新 production 依赖。
- **`openjiuwen.version` 构建债**（Stage 17 盲区 5，沿用）：同事 `20dc622f` 引入 `com.openjiuwen:agent-core-java` 依赖但 property 未定义。Stage 19 不 boot runtime → 不触发该依赖链，但仍需 `mvn -f agent-bus/pom.xml test` 走 m2 旧 jar 绕过 broken workspace pom。
- **push/pull/MQ 最终裁决**：仍 H2/H3。

## 6. Stage 19 落地总结（**已完成**，2026-06）

Stage 19 按 §2 / §4 计划落地，实际结果与计划一致，另有一个**超出原计划的 flaky 修复**：

- **按计划落地**（无偏差）：`C3ForwardingRetryLifecycleIntegrationTest` 双场景 —— 场景 A（不可达 route `freeUnusedPort()` 死 socket + `maxAttempts=3` 小 policy → 4 ticks 重投到 exhausted→DLQ，`attempt_count=3`）、场景 B（`fakeDeliveryPort` 注入 `[retry, retry, acked]` → 3 ticks 重投到恢复→ACKED，`attempt_count=2`）；`MutableEpochClock` + `advanceableTickSource` 协调多 tick（两时钟协调、真实时钟约束均按 §5 风险条落地）；不 boot runtime（两场景都不需要，比 Stage 17/18 更轻）。§5 的 4 个风险（两时钟协调 / 真实时钟约束 / 场景 B 终态映射交叉用 fake port / 场景 A `attempt_count` 读法）均在 IT 注释 + 断言中显式处理。
- **超出原计划的发现 —— flaky 修复**：构建验证发现 `C3ForwardingEndToEndIntegrationTest` / `C3ForwardingFailurePathIntegrationTest` 两个 context-boot IT 在 parent pom 的 surefire 4 路并发（`mode.classes.default=concurrent`）下 flaky（Spring Boot 4 `SpringApplication.run` 非线程安全 + 两 IT 命名 `*IntegrationTest` 绕过 failsafe 的 `**/*IT.java` 串行 include、落到并发 surefire → `ConcurrentModificationException` + 全局 `spring.autoconfigure.exclude` System property 踩踏）。最小修复：两 IT 加 JUnit5 `@Isolated`（独占执行），连续 3 次完整构建稳定 green（parent pom 注释本已警告 boot-context IT 应走串行 failsafe）。原计划未列此项 —— 它是构建验证阶段的真实发现。
- **结果**：**186 tests green**（Stage 18 的 184 + 2 retry lifecycle IT），ArchUnit green，**无生产代码改动**（纯测试阶段；`@Isolated` + import 是测试代码），§6.2 不变。
- **§4 MI19-003 文档同步**已按计划完成：decision §8 Stage 19 bullet、ICD（边界标题 + Stage 19 边界条 + Open Issues 重投闭环收口）、yaml（description + `stage19_scope`）、L2 `forwarding-persistence` §20、L1（README / physical / scenarios / development / process / logical / ARCHITECTURE）+ L2 `forwarding-outbox-inbox` + 本双语文档 §6。
- **下一步**（未裁决）：Stage 19 是纯验证阶段，无生产代码增量；§5 的 deferred（lease 过期 reclaim 端到端、breaker 真实链路端到端、真实 scheduler、registry resolver、push/pull/MQ 裁决）仍是独立后续 Stage 候选，待用户裁决方向。

## 相关文档

- Stage 18 计划：[`agent-bus-stage17-review-and-stage18-plan`](agent-bus-stage17-review-and-stage18-plan.md)（失败路径端到端 + `REMOTE_TASK_FAILED` 码收口 —— Stage 19 继续其场景 2 的 RETRY_SCHEDULED 入口往后跑完整生命周期）。
- C3 裁决：[`agent-bus-forwarding-runtime-decision`](../review-packets/agent-bus-forwarding-runtime-decision.md)（`adopted-c3`；§8 Stage 19 许可段待加）。
- runtime 契约：[`ICD-Agent-Bus-Forwarding-Runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)（Stage 19 边界条 + Open Issues 重投闭环收口）。
- 持久化 L2：[`forwarding-persistence`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)（Stage 19 决策：重投闭环端到端，§20 待加）。
- 重投机制源头：
  - `agent-bus/src/main/java/com/huawei/ascend/bus/forwarding/runtime/ForwardingRetryPolicy.java`（Stage 14，`nextAttemptAt` + `exhausted` + overflow-safe 退避）；
  - `…/persistence/jdbc/JdbcForwardingOutbox.java` `claimDue`（行 210-224，RETRY_SCHEDULED reclaim SQL 行 219）+ `leaseGuardedUpdate`（行 282-308，真实 `System.currentTimeMillis()` lease guard）；
  - `…/runtime/ForwardingDispatcherWorker.java` `runOnce` RETRY_SCHEDULED 分支（行 267-285，`attemptCount` 传 policy）；
  - `…/runtime/ForwardingDispatchLoop.java` `run`（行 84-122，TickSource 多 tick 聚合）。
- Stage 18 复用点：`C3ForwardingFailurePathIntegrationTest.outboxRow`（行 282-294，raw JDBC 投影读）+ `freeUnusedPort`（行 270，真实 socket 拒连）+ boot recipe（embedded-postgres + Flyway + `spring.autoconfigure.exclude`）。
