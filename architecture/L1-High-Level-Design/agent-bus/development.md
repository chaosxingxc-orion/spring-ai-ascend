---
level: L1
module: agent-bus
view: development
status: active
---

# agent-bus 开发视图

## 1. 代码结构

当前 `agent-bus` 的开发结构以 SPI 包为中心：

```text
agent-bus/
  module-metadata.yaml
  pom.xml
  src/main/java/com/huawei/ascend/bus/
    forwarding/
      spi/        # C3 转发运行态领域模型 + 端口 + record 模型 + claim/lease + delivery（Stage 7 + Stage 8，纯 Java）
      runtime/    # C3 转发状态机 + dispatcher worker + dispatch loop + retry policy + circuit breaker + EpochClock（Stage 7 + Stage 8 + Stage 10 + Stage 11 + Stage 14 + Stage 16，纯 Java）
        persistence/jdbc/  # Stage 12: Postgres JDBC adapter（Spring JDBC）；唯一允许 Spring/JDBC 的子包，ArchUnit 豁免
        transport/         # Stage 15: 真实投递绑定（A2aForwardingDeliveryPort + ForwardingEndpointResolver）；transport 抽象 + a2a 具体实现
          a2a/             # A2A SDK 限定子包（ArchUnit 豁免 org.a2aproject）
    spi/
      engine/
      federation/
      ingress/
      s2c/
  src/main/resources/
    db/migration/  # Stage 12 Flyway: V1__create_agent_bus_forwarding_outbox_inbox.sql（MI9-006 CHECK + 索引 + RLS）
  src/test/java/com/huawei/ascend/bus/
    forwarding/
      test/       # in-memory 测试替身（non-production）
    architecture/  # contract + purity harness
    spi/
      engine/
      s2c/
```

> 命名说明：本文架构语义（所有权）使用 L0 逻辑名 `agent-runtime`（已落地为同名模块，原 `agent-service` 已重命名）；forbidden dependencies 列表与 runtime 构造点引用使用当前 artifact 名。完整映射见 [`README.md`](README.md)「命名说明」。

## 2. 包职责

| 包 | 职责 | 成熟度 |
|---|---|---|
| `bus.spi.ingress` | C2S 入口 envelope、response、gateway | SPI 已存在，测试待补 |
| `bus.spi.s2c` | S2C callback envelope、response、transport、reflection router | SPI 已存在，S2C tenant 已迁移，runtime 构造点待后续波次 |
| `bus.spi.federation` | 跨网络 federation gateway | SPI 已存在，运行时实现待定 |
| `bus.spi.engine` | service-engine 中立执行边界和相关基础类型 | SPI 已存在，被 engine/service 消费 |
| `bus.forwarding.spi` | C3 转发运行态领域模型（envelope / route handle / status / failure code / receipt）+ outbox / inbox / dispatcher 端口；Stage 8 增 record 模型（outbox / inbox / lease）+ claim / lease 端口 + delivery 端口 | 纯 Java 已落地（Stage 7 + Stage 8）；Stage 12 真实 JDBC adapter 落地于 sibling 子包 `persistence.jdbc` |
| `bus.forwarding.runtime` | C3 转发状态机（outbox / inbox 转换表）+ Stage 8 dispatcher worker（claim / deliver / ack / retry）+ Stage 10 dispatch loop（lease 异常恢复 / 续约 / 调度责任，`TickSource` / `IdleStrategy` 注入）+ Stage 14 retry policy（overflow-safe 退避）+ Stage 16 circuit breaker（`RouteCircuitBreaker` 三态机接入 worker） | 纯 Java 状态机 + worker + dispatch loop + retry + breaker 已落地 |
| `bus.forwarding.runtime.transport` | Stage 15 真实投递绑定：`A2aForwardingDeliveryPort`（消费 agent-runtime `/a2a`，A2A SSE 终态映射为 `ForwardingDeliveryResult`）+ `ForwardingEndpointResolver` / `MapEndpointResolver` | transport 抽象纯 Java 已落地；`transport.a2a` 子包限定 A2A SDK（ArchUnit 豁免 `org.a2aproject`） |
| `bus.forwarding.runtime.persistence.jdbc` | C3 真实持久化：`JdbcForwardingOutbox`（含 claim / lease）/ `JdbcForwardingInbox` / `ForwardingSqlCodec`（Stage 12，Spring JDBC） | 已落地（Stage 12）；Spring / JDBC / Flyway / Postgres driver 仅限本子包 |

## 3. 依赖规则

`agent-bus` 生产代码允许：

- Java 标准库。
- `agent-bus` 内部 sibling SPI 类型。

`agent-bus` 生产代码禁止：

- 依赖 `agent-runtime`。
- 依赖 `agent-core`。
- 依赖 `agent-client`。
- 依赖 `agent-middleware`。
- 依赖 `agent-evolve`。
- 在 SPI 包中引入 Spring、Reactor、Jackson、HTTP framework 或 broker runtime。

## 4. 测试现状

| 测试 | 覆盖 | 缺口 |
|---|---|---|
| `S2cCallbackEnvelopeLibraryTest` | S2C envelope 基础字段和 trace 校验 | tenantId required-field harness 已补齐 |
| `SuspendSignalTest` / engine 相关测试 | engine/suspend 基础语义 | 需要确认 terminal event harness |
| ingress 测试 | 暂缺 | 需要补 required fields、trace、tenant、response status |
| federation 测试 | 暂缺 | 需要补 broker-agnostic 和 ingress carrier type |
| reflection 测试 | 暂缺 | 需要决定 map validator 或 typed record |
| `AgentBusForwardingRuntimeContractTest` | C3 outbox / inbox 记录字段、唯一键、去重键、禁止字段、状态机、失败码；Stage 8 增 record source/target、claim / lease 语义、dispatcher worker、persistence 纯度；Stage 9 增 lease-owner guarded mutation、record 不变量、failure-code 分类、SQL contract；Stage 10 增 worker lease 异常恢复 skip、lease 续约、dispatch loop；Stage 11 增 deliver 异常兜底 skip、`runOnce` fail-fast / loop 传播、续约 EpochClock 重写；Stage 14 增 retry policy overflow-safe 退避 + exhausted→DLQ；Stage 16 增断路器接入 worker（连续失败触发短路 / HALF_OPEN 探测放行 / `probeInFlight` 不泄漏） | 7 契约（方法名镜像 ICD）+ Stage 7 / Stage 8 / Stage 9 / Stage 10 / Stage 11 / Stage 14 / Stage 16 行为，41+ tests 已 green（Stage 12 real-SQL 由独立 `ForwardingJdbcIntegrationTest` 覆盖） |
| `AgentBusForwardingSpiPurityTest` | forwarding 生产代码纯 Java（无 Spring / JDBC / broker client）；**Stage 12 精确化**：Spring/JDBC 限于 `persistence.jdbc` 子包豁免，`bus.forwarding..` 主体仍纯 | 11 纯度 + 1 活跃度守卫，已 green（Stage 12 把 Spring/JDBC 圈进 `persistence.jdbc`；hikari/jackson/reactor/kafka/nats/servlet/netty 仍全局禁） |
| `ForwardingJdbcIntegrationTest` | Stage 12 real-SQL：Flyway migration / enqueue-claim-ack round-trip / 并发 claim 无重复（`SKIP LOCKED`）/ lease guard 分类 / stuck-holder reclaim / renew-or-lose-ack / release 过期语义 / CHECK 兜底 / tenant 隔离 / §7.3 RLS fail-closed / inbox | embedded-postgres PG 16.2 in-process（Docker 不可达环境），17 tests 已 green |
| `A2aForwardingDeliveryPortTest` | Stage 15 真实投递绑定 PoC：A2A transport adapter MockWebServer 场景（正常 SSE 完成流 / 503 不当错误静默空流 / 真实 socket 断开 errorConsumer / 终态映射 FAILED→dlq / per-endpoint transport 缓存） | MockWebServer 5 场景，已 green（ArchUnit 双豁免 `org.a2aproject` 圈 `transport.a2a`） |
| `C3ForwardingEndToEndIntegrationTest` / `C3ForwardingFailurePathIntegrationTest` | Stage 17 / Stage 18 跨模块端到端：embedded-postgres + Flyway + 真实 `LocalA2aRuntimeHost`，outbox enqueue → tick → deliver → 真实 /a2a JSON-RPC + SSE；happy-path（StubHandler COMPLETED→ACKED）+ 失败路径（FailingHandler FAILED→DLQ `remote_task_failed` / 不可达 route→RETRY）；agent-bus 加 `agent-runtime` test-scope 依赖 | 4 tests（happy-path 2 + 失败路径 2），已 green（总 184 tests） |
| `C3ForwardingRetryLifecycleIntegrationTest` | Stage 19 重投往返生命周期端到端：embedded-postgres + Flyway + `JdbcForwardingOutbox`（无 runtime boot）；`claimDue` 的 `RETRY_SCHEDULED` 回收子句跨多 tick 触发 → (A) 持续失败重投 3 次→exhausted→DLQ / (B) 间歇恢复重投 2 次→ACKED；`MutableEpochClock` + 协调多 tick `TickSource` 时间控制无需 scheduler；两 context-boot IT 加 `@Isolated` 修并发 flaky | 2 tests（retry lifecycle A/B），已 green（总 186 tests） |
| `C3ForwardingLeaseReclaimAndBreakerIntegrationTest` | Stage 20 验证回填端到端：embedded-postgres + Flyway + `JdbcForwardingOutbox`（无 runtime boot）；(A) 租约过期卡住持有者回收——模拟 worker 崩溃留 DISPATCHING + lease_until 过期，单 tick `claimDue` 卡住持有者回收子句 `status='DISPATCHING' AND lease_until<=:now` 申领→ACKED；(B) 断路器全状态机真实链路——`RouteCircuitBreaker` CLOSED→OPEN（2 连续 retryable 失败）→冷却→HALF_OPEN 探测→ACKED→CLOSED 与租约回收交织，复用 Stage 19 `MutableEpochClock` + 协调多 tick `TickSource`；两 IT 加 `@Isolated` | 2 tests（lease-reclaim / breaker），已 green（总 188 tests） |
| `C3ForwardingMultiWorkerConcurrencyIntegrationTest` | Stage 21 多 worker 并发验证端到端：embedded-postgres + Flyway + `JdbcForwardingOutbox`（无 runtime boot）；(A) 并发 claim 无重复——M=20 条、N=4 worker 各不同 lease owner 共享 outbox + 原子计数 delivery port，`CountDownLatch` 同时释放各循环 `runOnce` 直到空，断言 delivery 计数 == 20（SKIP LOCKED 直接证据）+ 全 20 ACKED；(B) 共享断路器单例并发一致 OPEN——M=12、N=4 共享 `RouteCircuitBreaker(2, 极大冷却)` + 全 retry delivery，`breaker.stateOf==OPEN` 无 worker 抛异常；`MutableEpochClock` 冻在 t0 隔离并发状态写；`@Isolated` | 2 tests（concurrent-claim / shared-breaker），已 green（总 190 tests） |
| `C3ForwardingExpiryAndLeaseRenewalIntegrationTest` | Stage 22 时间驱动的终态与续约验证端到端：embedded-postgres + Flyway + `JdbcForwardingOutbox`（无 runtime boot）；(A) EXPIRED 终态——fake delivery `expired()` → 单 tick `markExpired` 落盘 `EXPIRED`/`delivery_timeout`/lease NULL，2 ticks 聚合 `claimed=1` 证明终态不回收；(B) lease 续约真实触发——`MutableEpochClock(t0)` + `DispatchLeasePolicy(renewBefore=50s, extend=60s)` + claim lease 30s → 续约检查 `remaining=30s<50s` 触发 `renewLease` deliver 前 commit，observing port deliver 时读续约后 `lease_until=t0+90s` 作确凿证据 → ACKED；关键发现：EXPIRED 是「SQL 正确但触发源缺失」终态（record 不持久化 deadline、deliver 不检查），真实触发源 deferred；`@Isolated` | 2 tests（expired-terminal / lease-renewal），已 green（总 192 tests） |
| `C3ForwardingPayloadRefIntegrationTest` + `A2aForwardingDeliveryPortMockWebServerTest` 扩展 | Stage 23 payloadRef 端到端传递验证：embedded-postgres + Flyway + `JdbcForwardingOutbox`（无 runtime boot）+ MockWebServer；(A) DATA_BEARING payloadRef outbox 持久化端到端——DATA_BEARING envelope + observing port deliver 时断言 `record.payloadRef()`（SqlCodec decode）+ live PG `payload_ref` 列（raw JDBC）均等于 enqueue 值（round-trip 确凿证据）+ ACK 后列存活（markAcked 不清）；(B) payloadRef → A2A metadata 传输边界——`deliver_carries_payload_ref` 断言 body 含 `payloadRef` / `deliver_omits_payload_ref` 断言 CONTROL_ONLY 省略（两分支对称即 §6.2 data-reference 契约）；范围修正 Stage 22 预期（agent-bus 是无状态引用路由层，两传递点都不 boot runtime）；关键发现：payloadRef 持久化完整（vs Stage 22 EXPIRED 触发源缺失）/ `PAYLOAD_REF_INVALID` 失败码有 enum 无触发点 deferred；`@Isolated` | 3 tests（payload-ref round-trip 1 + transport 2），已 green（总 195 tests） |
| `C3ForwardingRlsWiringIntegrationTest` | Stage 24 RLS 接线端到端：embedded-postgres + Flyway（superuser）+ `app_role` 受限角色专用 DataSource（每连接 `SET ROLE app_role` + GRANT SELECT/INSERT/UPDATE）；(A) app_role 下 adapter 业务路径 `enqueue→claimDue→markAcked` 全 green 反证接线（否则 RLS fail-closed 断 claimDue 返回空）+ Inbox 同；(B) app_role + 裸 SQL `count(*)` RLS 过滤对照：设 tenant→可见 / 他 tenant→0 / 不设→0（fail-closed）；adapter 首次引入 `TransactionTemplate` 事务管理 + `withTenant` helper 事务内 `set_config('app.tenant_id',…,true)` ≡ SET LOCAL，不加 FORCE/WITH CHECK、V1 零改、双构造器向后兼容 | 2 tests（adapter 业务路径反证 + 裸 SQL 过滤），已 green（总 200 tests） |

## 5. 生成物边界

允许生成：

- L1 graph model。
- schema fixture。
- contract test skeleton。
- drift check manifest。

禁止自动生成：

- 运行时 broker 实现。
- 修改 production dependency graph 的代码。
- 未经 owner 裁决的 breaking 契约变更（如 S2C v1 这种 pre-GA 内部契约的字段增删，MI-005 方案 A）。
- 将 W2 workflow primitives 直接生成为运行时代码。

## 6. S2C tenant 迁移结果与剩余影响

Stage 2 已完成的迁移（commit `d894f494`）：

- `docs/contracts/s2c-callback.v1.yaml`：`tenant_id` 加入 request required fields（第七个必填字段，Rule R-C.c）。
- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c/S2cCallbackEnvelope.java`：新增 `tenantId` 组件，compact constructor 校验非 null、非 blank。
- `agent-bus/src/test/java/com/huawei/ascend/bus/spi/s2c/S2cCallbackEnvelopeLibraryTest.java`：补齐 null/blank `tenantId` 负向用例与既有构造点更新。
- `contract-catalog.md` / `contract-catalog.md.j2` / `spi-appendix.md`：preferred fix 升级为 migrated fact。

仍待后续波次补齐（不改 Task lifecycle 所有权，S2C-TENANT-006）：

- 构造 `S2cCallbackEnvelope` 的 runtime 侧构造点（`agent-runtime`）。
- runtime-side schema validation integration。
- downstream 文档与治理模板的剩余同步。

本迁移已通知所有冲突方（CN-001..CN-007）；不改变 `agent-runtime` 对 Task lifecycle 的所有权。

## 7. C3 转发运行态（Stage 7 → Stage 25）

C3（database outbox / inbox）已最终确认为类 MQ 转发的生产候选路径（裁决见 [`agent-bus-forwarding-runtime-decision`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)，`adopted-c3`）。Stage 7–23 按裁决递进落地：Stage 7 最小骨架 → Stage 8 持久化准备 → Stage 9 lease-safe → Stage 10 dispatch-loop runtime → Stage 11 runtime-completion → Stage 12 real persistence（打破路径 B）→ Stage 13 transport 候选评审 → Stage 14 deliver 重投策略 → Stage 15 真实 A2A 投递绑定 PoC → Stage 16 断路器接入 worker → Stage 17/18 跨模块端到端验证 → Stage 19 重投往返生命周期端到端验证 → Stage 20 验证回填 → Stage 21 多 worker 并发验证 → Stage 22 时间驱动的终态与续约端到端验证 → Stage 23 payloadRef 端到端传递验证 → Stage 24 RLS 接线闭合跨租户纵深防御 → Stage 25 投递模型最终裁决 T4 hybrid（outbox + broker，`adopted-t4`，无生产代码，见 [`transport-decision`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-decision.md)）（[`forwarding-persistence`](../../L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。

**已落地（生产代码，纯 Java）：**

- `bus.forwarding.spi`：领域模型 `ForwardingEnvelope`（强制 tenant 隔离 + `payloadRef` 条件必填）、`ForwardingRouteHandle`、`ForwardingMessageId`、`ForwardingStatus`（outbox / inbox 终态枚举）、`ForwardingFailureCode`（**8 个 wire 失败码**，含 Stage 18 `remote_task_failed`）、`ForwardingReceipt`；端口 `ForwardingOutboxPort`（enqueue + mark* + statusOf）、`ForwardingInboxPort`、`ForwardingDispatcher`（accept / enqueue 网关角色，MI8-003）。
- `bus.forwarding.spi`（Stage 8 增补）：record 模型 `ForwardingOutboxRecord` / `ForwardingInboxRecord`（承载 runtime ICD 必填字段，含 source / target，MI8-002）、`ForwardingLease`；端口 `ForwardingOutboxClaimPort`（claim / lease，`claimDue` 取代 `findRetryable`，MI8-001）、`ForwardingDeliveryPort` + `ForwardingDeliveryResult`（抽象投递，4 种 outcome）。
- `bus.forwarding.runtime`：纯状态机 `ForwardingStateMachine`（outbox `ENQUEUE → PENDING → DISPATCHING → {ACKED | RETRY_SCHEDULED → DISPATCHING | DLQ | EXPIRED}`、inbox `ARRIVE_NEW → RECEIVED → {CONSUMED | REJECTED}`，非法迁移抛 `IllegalStateTransitionException`）；Stage 8 增 `ForwardingDispatcherWorker`（claim / deliver / ack / retry 半边，单次 `runOnce` tick：claimDue → deliver → markAcked / scheduleRetry / moveToDlq / markExpired）；Stage 14 增 `ForwardingRetryPolicy`（overflow-safe 指数退避 + exhausted→DLQ）；Stage 16 增 `ForwardingCircuitBreaker` 端口（`recordOutcome` 反馈）+ `RouteCircuitBreaker` 三态机（CLOSED→OPEN→HALF_OPEN）接入 worker（投递前 `allowsDelivery` 短路 + 投递后 `recordOutcome`）。
- `bus.forwarding.runtime.transport`（Stage 15 新增）：真实投递绑定 PoC `A2aForwardingDeliveryPort`（per-endpoint JSON-RPC transport 缓存 + 阻塞 sendMessageStreaming + A2A SSE 终态映射为 `ForwardingDeliveryResult`：COMPLETED/INPUT_REQUIRED→acked、`isFinal()` 终态→dlq(`remote_task_failed`)、AUTH_REQUIRED→retry）+ `ForwardingEndpointResolver` / `MapEndpointResolver`（注入投递端口，解开 routeHandle opaque，不破 HD4）；`transport.a2a` 子包限定 A2A SDK（ArchUnit 豁免 `org.a2aproject`），transport 抽象层仍纯 Java。

**仅测试夹具（non-production）：**

- `bus.forwarding.test`：`InMemoryForwardingOutbox`（同时实现 outbox + claim / lease 端口，验证并发抢占语义）/ `InMemoryForwardingInbox` / `InMemoryForwardingDispatcher` / `InMemoryForwardingDelivery`（fake delivery port），HashMap 支撑。明确标注 non-production（Stage 12 真实 JDBC adapter 落地后仍保留为 fast test double）。

**边界（Stage 7 / Stage 8，与 Stage 4 / 6 一致，强化）：**

- 生产代码不引入 concrete broker / MQ（由 `AgentBusForwardingSpiPurityTest` ArchUnit 强制）。**Stage 12 精确化**：Spring JDBC / Flyway / Postgres driver 已引入，ArchUnit 把它们圈在 `bus.forwarding.runtime.persistence.jdbc` 子包；`bus.forwarding..` 主体仍纯 Java。
- 不改变远端 Task lifecycle owner；不写 Task execution state；不携带 payload body / token stream / physical endpoint。
- **Stage 12 已落地真实持久化**：Postgres JDBC adapter（Spring JDBC）+ Flyway migration `V1` + §7.3 RLS（real-SQL 验证 embedded-postgres PG 16.2，17 tests green）；`§6.1`「不引入 JDBC」解除，`§6.2` 不变。**Stage 15 已落地真实投递绑定 PoC**：`A2aForwardingDeliveryPort` 消费 agent-runtime `/a2a`（同步等完成 = T1 push，Stage 13 候选评审）；ArchUnit 把 A2A SDK 圈在 `transport.a2a` 子包。**Stage 16 接入断路器**（`RouteCircuitBreaker` 三态机，正当性来自 Stage 15 选 T1 push）。**Stage 17/18 跨模块端到端验证**（真实 `LocalA2aRuntimeHost`，happy-path + 失败路径，184 tests green）；**Stage 19 重投往返生命周期端到端验证**（claimDue 的 `RETRY_SCHEDULED` 回收子句跨多 tick 触发 → exhausted/DLQ 或恢复/ACKED，186 tests green）+ **Stage 20 验证回填**（租约过期卡住持有者回收子句 `status='DISPATCHING' AND lease_until<=:now` 端到端 + 断路器全状态机真实链路端到端，188 tests green；复用 Stage 19 `MutableEpochClock` + 协调多 tick `TickSource`；两 IT 加 `@Isolated`）+ Stage 21 多 worker 并发验证（并发 claim 无重复投递端到端——delivery 计数 == 20 为 SKIP LOCKED 直接证据——+ 共享断路器单例并发一致 OPEN，190 tests green；`MutableEpochClock` 冻在 t0；两 IT 加 `@Isolated`）+ Stage 22 时间驱动的终态与续约端到端验证（EXPIRED 终态 markExpired 落盘 + lease 续约真实触发，192 tests green；复用 Stage 19 `MutableEpochClock`；两 IT 加 `@Isolated`）+ Stage 23 payloadRef 端到端传递验证（DATA_BEARING payloadRef 在真实 PG 上 enqueue→claim→deliver→ACKED 全程 round-trip 不变 + A2A metadata 传输边界两分支对称，闭合 Stages 17-22 全 CONTROL_ONLY 盲区）+ Stage 24 RLS 接线闭合跨租户纵深防御（adapter 首次引入 `TransactionTemplate` 事务管理，`withTenant` helper 在每端口方法事务内 `set_config('app.tenant_id',…,true)` ≡ SET LOCAL 激活 §7.3 fail-closed RLS，闭合 Stage 12「armed but not wired」；**有生产代码改动**（与 Stages 17-23 纯测试不同）、双构造器向后兼容、不加 FORCE/WITH CHECK、V1 零改、受限 `app_role` 下 adapter 业务路径 green 作接线 smoking gun；200 tests green；`@Isolated`）；agent-bus 加 `agent-runtime` test-scope 依赖、生产仍零依赖（`AgentBusDependencyBoundaryTest` 守卫）。**投递模型已 Stage 25 裁决为 T4 hybrid（outbox + broker，`adopted-t4`，无生产代码，见 [`transport-decision`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-decision.md)）**：保留 outbox + relay produce broker + receiver pull（pull = 反压内核，MQ 本质是 pull），解除 §6.1 第 1 项引 broker（圈 `transport.broker` 子包）、守 §6.2 精神；broker 选型 deferred Stage 26 PoC 倾向 RocketMQ；T1 push PoC 保留共存。**仍 deferred**：真实 broker 物理接线（relay adapter / receiver consumer / broker 产品选型 Stage 26+ PoC）、registry 集成的 resolver 生产实现、连接池治理、polling cadence、并发 worker 分片、backpressure 参数、`ForwardingDispatchLoop` 接真实 scheduler（§6.1 第 3 项：T4 relay/receiver 都需生产 TickSource 解除）。
