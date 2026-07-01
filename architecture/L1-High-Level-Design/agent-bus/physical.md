---
level: L1
module: agent-bus
view: physical
status: active
---

# agent-bus 物理视图

## 1. 部署平面

`agent-bus` 属于 `bus_state` 部署平面。当前分支只包含 SPI、契约和少量基础测试，不包含完整物理 bus 实现。

> 命名说明：本文架构语义（部署平面角色、模块关系）使用 L0 逻辑名 `agent-runtime` / `agent-core`。`agent-runtime` 已落地为同名模块（原 `agent-service` 已重命名为 `agent-runtime`）；`agent-core` 已落地为 `agent-core`。完整映射见 [`README.md`](README.md)「命名说明」。

| 平面 | 模块 | 与 bus 的关系 |
|---|---|---|
| edge | `agent-client` | 通过 ingress 进入内部，通过 S2C 接收客户端能力调用。 |
| compute_control | `agent-runtime` | 拥有 Task 生命周期，消费 ingress/S2C/engine 契约。 |
| compute_control | `agent-core` | 实现或消费 engine SPI。 |
| bus_state | `agent-bus` | 拥有跨边界契约、治理表面和未来 bus runtime 的语义位置。 |

## 2. 当前物理事实

当前代码中的 `agent-bus` 是一个 Maven module。它不依赖 `agent-runtime`、`agent-core`、`agent-client`、`agent-middleware` 或 `agent-evolve` 的生产代码。（Stage 17 引入 `agent-runtime` 的 **test-scope** 依赖做端到端集成验证 —— `C3ForwardingEndToEndIntegrationTest` 启动真实 `LocalA2aRuntimeHost`；Stage 18 延续同 test-scope 依赖做失败路径端到端验证 `C3ForwardingFailurePathIntegrationTest`；Stage 19 重投往返生命周期端到端验证 `C3ForwardingRetryLifecycleIntegrationTest`（仅 embedded-postgres + Flyway，不 boot runtime）；Stage 20 验证回填 `C3ForwardingLeaseReclaimAndBreakerIntegrationTest`（仅 embedded-postgres + Flyway，不 boot runtime；租约过期卡住持有者回收 + 断路器全状态机真实链路端到端）；Stage 21 多 worker 并发验证 `C3ForwardingMultiWorkerConcurrencyIntegrationTest`（仅 embedded-postgres + Flyway，不 boot runtime；并发 claim 无重复 + 共享断路器单例并发一致 OPEN）；Stage 22 时间驱动的终态与续约端到端验证 `C3ForwardingExpiryAndLeaseRenewalIntegrationTest`（仅 embedded-postgres + Flyway，不 boot runtime；EXPIRED 终态 markExpired 落盘 + lease 续约真实触发）；Stage 23 payloadRef 端到端传递验证 `C3ForwardingPayloadRefIntegrationTest` + `A2aForwardingDeliveryPortMockWebServerTest` 扩展（DATA_BEARING payloadRef 真实 PG round-trip 不变 + A2A metadata 传输边界两分支对称；embedded-postgres + Flyway + MockWebServer，不 boot runtime）；无生产代码改动，生产依赖边界不变，`AgentBusDependencyBoundaryTest.bus_does_not_depend_on_agent_runtime` 守卫。）

当前已经存在的物理文件包括：

- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/ingress/**`
- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c/**`
- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/federation/**`
- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/engine/**`
- `agent-bus/src/test/java/**`

## 3. 物理边界

| 边界 | 当前策略 |
|---|---|
| 网络边界 | federation/reflection 仅有 SPI，不选择 broker 或网络协议。 |
| 租户边界 | ingress 与 S2C envelope 均已携带 `tenantId`（S2C 为 Stage 2 迁移）；Agent 注册发现 registry key 强制包含 `tenantId`，禁止跨 tenant fallback（见 [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md)）。 |
| 凭证边界 | 当前没有物理 credential 绑定。 |
| 存储边界 | bus 不拥有 Task state store。 |
| 队列边界 | mailbox/backpressure/tick 仍是设计态。 |
| 转发边界 | 类 MQ 转发底座运行态承载已确认为 **C3（database outbox / inbox），`adopted-c3`**：Stage 7 最小骨架 + Stage 8 持久化准备（record 模型 / claim / lease 端口 / dispatcher worker / 抽象 delivery 端口 / schema 草案，DDL 未执行）+ Stage 9 lease-safe（lease-owner guarded mutation / record 不变量 / failure-code 分类 / claim + state-update SQL contract）+ Stage 10 dispatch-loop runtime（worker lease 异常恢复 / lease 续约 / dispatch loop 骨架）+ Stage 11 runtime-completion（lease 续约改读注入 `EpochClock` / `deliver` 非 lease 异常兜底 skip / `runOnce` fail-fast）+ Stage 12 real persistence（Postgres JDBC adapter + Flyway migration + §7.3 RLS，打破路径 B）已落地；消费 Stage 3 route handle；broker-agnostic（不绑定具体 broker / MQ 产品）；transport 投递模型已在 Stage 13 完成候选评审（T1-T4 × 8 维度，T3 consumer-pull over DB 非裁决推荐），真实投递绑定仍 deferred（待 H2/H3 裁决 push / pull / MQ）；Stage 14 落地 deliver 重投策略先行（`ForwardingRetryPolicy` 端口 + overflow-safe 指数退避 + exhausted→DLQ + 熔断端口 deferred，§6.2 不变）；Stage 15 落地真实投递绑定 PoC（A2A HTTP transport adapter `A2aForwardingDeliveryPort` 消费 agent-runtime `/a2a`，`§6.1` 第 4 项解除、`§6.2` 不变；ArchUnit 把 `org.a2aproject` 圈进 `transport.a2a` 子包）；Stage 16 接入 `ForwardingCircuitBreaker` 到 worker（`RouteCircuitBreaker` 三态机 CLOSED→OPEN→HALF_OPEN + `recordOutcome` 反馈 + `allowsDelivery` 短路，正当性来自 Stage 15 选 T1 push；纯 JDK transport-agnostic，§6.2 不变）；Stage 17 落地首次跨模块端到端集成（`C3ForwardingEndToEndIntegrationTest` 用真实 `LocalA2aRuntimeHost` 替换 Stage 15 MockWebServer，端到端驱动 outbox enqueue → tick → deliver → 真实 /a2a → COMPLETED → ACKED；agent-bus 加 `agent-runtime` test-scope 依赖、生产仍零依赖；两个发现：`LocalA2aRuntimeHost` 对 JDBC-bearing 共享 classpath 敏感 + Spring Boot 4 autoconfigure 重打包；182 tests green；§6.2 不变）；Stage 18 失败路径端到端验证 + `REMOTE_TASK_FAILED` 码收口（`C3ForwardingFailurePathIntegrationTest` 双失败场景：真实 `FailingHandler` FAILED→DLQ + 不可达 route→RETRY；`ForwardingFailureCode.REMOTE_TASK_FAILED` NON_RETRYABLE + 终态映射改 `isFinal()` if-chain；无 DDL/SqlCodec/record 改动；184 tests green；§6.2 不变）；大载荷走 data reference path，不进 event / control channel（见 [`ICD-Agent-Bus-Forwarding`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md)、[`forwarding-persistence`](../../L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。 |
| 注册发现边界 | agent/service/capability 注册发现仍是设计态；租户隔离、registry key、health、contract version 语义已在 ICD 设计态裁决。仍未裁决的是运行态物理实现：持久化存储、写入者、健康检查推/拉模型、region 路由、broker/topic 绑定、一致性策略（见 [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md)）。 |

## 4. S2C tenant 物理影响

S2C envelope 已携带 `tenantId`（Stage 2 契约层迁移，commit `d894f494`），以下物理或部署相关能力因此具备稳定的 tenant scope 基础：

- 跨 service dispatch。
- 跨网络 federation。
- callback audit。
- DLQ / replay。
- client-side authorization。

契约层迁移已完成；这是 pre-GA 内部契约的 breaking change，不升 v1.1/v2。剩余为 runtime-side construction binding / schema validation integration，随后续波次推进。

## 5. 尚未选择的物理实现

以下内容不属于当前 L1 草案的已实现事实：

- Kafka / NATS / 自研 broker。
- control/data/rhythm 三通道的具体 broker 映射。
- mailbox 存储。
- DLQ 和 replay 存储。
- backpressure runtime。
- tick engine runtime。
- agent/service/capability registry runtime。
- 类 MQ 转发底座 runtime 的真实投递 / broker / queue / DLQ / replay 存储（Stage 12 已落地 JDBC adapter + Flyway migration + RLS 持久化层；真实投递绑定 / broker 仍 deferred，transport 拆出独立议）。
- service discovery API。

### 5.1 运行态候选评审（Stage 5）

Stage 5 对类 MQ 转发底座的运行态承载候选（in-memory dispatcher / runtime-local queue / database outbox-inbox / external broker / hybrid）做了 broker-agnostic 的候选评审，**不实现运行态、不绑定 broker 产品**。评审见 [`agent-bus-forwarding-runtime-candidates`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md)。

### 5.2 运行态候选裁决与落地（Stage 6 → Stage 7 → Stage 8 → Stage 9 → Stage 10 → Stage 11 → Stage 12 → Stage 13 → Stage 14 → Stage 15 → Stage 16 → Stage 17 → Stage 18 → Stage 19 → Stage 20 → Stage 21 → Stage 22 → Stage 23）

Stage 6 建立运行态候选裁决记录 [`agent-bus-forwarding-runtime-decision`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)。C3（database outbox / inbox）已最终确认（**`adopted-c3`**，Stage 8 最终确认）：Stage 7 落地最小运行态骨架（领域模型 + 端口 + 状态机 + in-memory 替身 + harness），Stage 8 落地持久化准备（record 模型 + claim / lease 端口 + dispatcher worker skeleton + 抽象 delivery 端口 + schema / migration 草案，DDL 未执行 + in-memory lease harness），Stage 9 落地 lease-safe（lease-owner guarded mutation / lease 生命周期闭环 / record 条件不变量 / failure-code classification / claim + state-update SQL contract；DB 归属未确认 → 路径 B），Stage 10 落地 dispatch-loop runtime（worker lease 异常恢复 + skip / lease 续约 `DispatchLeasePolicy` / `ForwardingDispatchLoop` 骨架 `TickSource` / `IdleStrategy` 注入；DB 归属经人类再确认为路径 B），Stage 11 落地 runtime-completion（lease 续约触发时机改读注入 `EpochClock` / `deliver` 非 lease `RuntimeException` 兜底为 skipped / `runOnce` 仅入参非法 fail-fast；DB / migration / transport 未变 → 路径 B），Stage 12 落地 real persistence（Postgres JDBC adapter + Flyway migration `V1` + §7.3 RLS；打破路径 B，§6.1 解除「不引入 JDBC」，ArchUnit 把 Spring/JDBC 圈进 `persistence.jdbc` 子包；real-SQL 验证 embedded-postgres PG 16.2，17 tests green，153 tests green），Stage 13 完成 transport 投递模型候选评审（[`transport-candidates`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md)，T1-T4 × 8 维度，T3 consumer-pull over DB 非裁决推荐），Stage 14 落地 deliver 重投策略先行（`ForwardingRetryPolicy` 端口 + overflow-safe 指数退避 + exhausted→DLQ + 熔断端口 deferred，§6.2 不变），Stage 15 落地真实投递绑定 PoC（A2A HTTP transport adapter `A2aForwardingDeliveryPort` 消费 agent-runtime `/a2a`，同步等完成 = T1，`§6.1` 第 4 项解除、`§6.2` 不变；ArchUnit 把 `org.a2aproject` 圈进 `transport.a2a` 子包），Stage 16 接入 `ForwardingCircuitBreaker` 到 worker（`RouteCircuitBreaker` 三态机 CLOSED→OPEN→HALF_OPEN + `recordOutcome` 反馈 + `allowsDelivery` 短路，正当性来自 Stage 15 选 T1 push；纯 JDK transport-agnostic，§6.2 不变；179 tests green），Stage 17 落地首次跨模块端到端集成（`C3ForwardingEndToEndIntegrationTest` 用真实 `LocalA2aRuntimeHost` 替换 Stage 15 MockWebServer，端到端驱动 outbox enqueue → tick → deliver → 真实 /a2a → COMPLETED → ACKED；agent-bus 加 `agent-runtime` test-scope 依赖、生产仍零依赖；两个发现：`LocalA2aRuntimeHost` 对 JDBC-bearing 共享 classpath 敏感 + Spring Boot 4 autoconfigure 重打包；182 tests green；§6.2 不变），Stage 18 失败路径端到端验证 + `REMOTE_TASK_FAILED` 码收口（`C3ForwardingFailurePathIntegrationTest` 双失败场景 + 终态映射 `isFinal()` if-chain + `ForwardingFailureCode.REMOTE_TASK_FAILED` NON_RETRYABLE；无 DDL/SqlCodec/record 改动；184 tests green；§6.2 不变），Stage 19 重投往返生命周期端到端验证（`C3ForwardingRetryLifecycleIntegrationTest` 双场景：claimDue 的 RETRY_SCHEDULED 回收子句跨多 tick 触发 → (A) 持续失败重投 3 次→exhausted→DLQ / (B) 间歇恢复重投 2 次→ACKED；`MutableEpochClock` + 协调多 tick `TickSource` 时间控制无需 scheduler；两 context-boot IT 加 `@Isolated` 修并发 flaky；186 tests green；§6.2 不变），Stage 20 验证回填（`C3ForwardingLeaseReclaimAndBreakerIntegrationTest` 双场景：(A) 租约过期卡住持有者回收——模拟 worker 崩溃留 DISPATCHING + lease_until 过期，单 tick `claimDue` 卡住持有者回收子句 `status='DISPATCHING' AND lease_until<=:now` 申领→ACKED；(B) 断路器全状态机真实链路——`RouteCircuitBreaker` CLOSED→OPEN（2 连续 retryable 失败）→冷却→HALF_OPEN 探测→ACKED→CLOSED 与租约回收交织，复用 Stage 19 `MutableEpochClock` + 协调多 tick `TickSource`；两 IT 加 `@Isolated`；188 tests green；§6.2 不变），Stage 21 多 worker 并发验证（`C3ForwardingMultiWorkerConcurrencyIntegrationTest` 双场景：(A) 并发 claim 无重复——M=20 条、N=4 worker 各不同 lease owner 共享 outbox + 原子计数 delivery port，`CountDownLatch` 同时释放各循环 `runOnce` 直到空，断言 delivery 计数 == 20（SKIP LOCKED 直接证据）+ 全 20 ACKED；(B) 共享 breaker 并发一致 OPEN——M=12、N=4 共享 `RouteCircuitBreaker(2, 极大冷却)` + 全 retry delivery，`breaker.stateOf==OPEN` 无 worker 抛异常；`MutableEpochClock` 冻在 t0；仅 embedded-postgres + Flyway 不 boot runtime；`@Isolated`；190 tests green；§6.2 不变），Stage 22 时间驱动的终态与续约端到端验证（`C3ForwardingExpiryAndLeaseRenewalIntegrationTest` 双场景：(A) EXPIRED 终态——fake delivery `expired()` → 单 tick `markExpired` 落盘 `EXPIRED`/`delivery_timeout`/lease NULL，2 ticks 聚合 `claimed=1` 证明终态不回收；(B) lease 续约真实触发——续约检查读注入 `EpochClock`，claim 短 lease 使 `remaining<阈值` 触发 `renewLease` deliver 前 commit，observing port deliver 时读续约后 `lease_until` 作确凿证据 → ACKED；关键发现：EXPIRED 是「SQL 正确但触发源缺失」终态（record 不持久化 deadline、deliver 不检查 → 真实 A2A 从不返回 `expired()`），真实触发源 deferred；仅 embedded-postgres + Flyway 不 boot runtime；`@Isolated`；192 tests green；§6.2 不变），Stage 23 payloadRef 端到端传递验证（`C3ForwardingPayloadRefIntegrationTest` + `A2aForwardingDeliveryPortMockWebServerTest` 扩展：DATA_BEARING payloadRef 在真实 PG 上 enqueue→claim→deliver→ACKED 全程 round-trip 不变（observing port deliver 时断言 `record.payloadRef()` + PG `payload_ref` 列均等于 enqueue 值，ACK 后列存活）+ A2A metadata 传输边界两分支对称（DATA_BEARING 携带 `metadata.payloadRef` / CONTROL_ONLY 省略）；范围修正 Stage 22 预期——agent-bus 是无状态引用路由层，两传递点都不 boot runtime；关键发现：payloadRef 持久化完整（vs Stage 22 EXPIRED 触发源缺失）、`PAYLOAD_REF_INVALID` 失败码有 enum 无触发点 deferred；仅 embedded-postgres + Flyway + MockWebServer 不 boot runtime；`@Isolated`；195 tests green；§6.2 不变），见 [`forwarding-persistence §15/§16/§17/§18/§19/§20/§21/§22/§23/§24`](../../L2-Low-Level-Design/agent-bus/forwarding-persistence.md)。**真实 broker / queue / DLQ / replay store 仍 deferred**（真实投递绑定 PoC 已落地 A2A adapter，但最终 push / pull / MQ 投递模型裁决待 H2/H3；Stage 13 已产出 transport 候选评审 [`transport-candidates`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md)，T1-T4 × 8 维度，T3 consumer-pull over DB 非裁决推荐；选 T2 / T4 需解除 §6.2 引 MQ）；生产代码不引入 concrete broker / MQ（§6.2），Spring/JDBC 限 `persistence.jdbc` 子包、A2A SDK 限 `transport.a2a` 子包。

## 6. Agent 注册发现的物理问题

注册发现进入实现前，至少需要回答：

- 注册表是否持久化。
- 注册信息由谁写入，谁可以删除。
- health / readiness 由 push、pull 还是 lease 表达。
- tenant 隔离如何保证。
- service 与 capability 的版本兼容如何表达。
- region / deployment plane 是否参与路由选择。
- 注册发现是否和 broker topic / route key 绑定。

Stage 3 已在 [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md) 设计态回答了 owner（agent-bus 只拥有 route index）、tenant 隔离（registry key 强制 `tenantId`）、health（lease/TTL）和 contract version 语义。但上表中的持久化实现、写入者细节、region 路由选择、broker topic 绑定仍是 runtime 物理决策，Stage 3 不实现。

这些问题没有回答前，不能把注册发现实现为 production runtime。

任何引入这些内容的实现都需要新的 H2/H3 审核。
