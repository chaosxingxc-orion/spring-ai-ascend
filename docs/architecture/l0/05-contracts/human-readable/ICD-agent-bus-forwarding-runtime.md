---
level: L2
view: process
status: draft
source_icd_design: ICD-agent-bus-forwarding.md
source_l2: architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
---

# ICD-Agent-Bus-Forwarding-Runtime

> 命名说明：本 ICD 架构语义使用 L0 逻辑名 `agent-runtime` / `agent-core`（当前实现 / 兼容落点分别为 `agent-service` / `agent-execution-engine`）；代码路径、Maven artifact、`module-metadata.yaml`、forbidden dependencies 仍保留旧名。转发两端在架构语义上是 runtime-to-runtime；当表达一般服务实例时使用 `service instance` / 「服务实例」。

## 目的

定义 `agent-bus` 类 MQ 转发**运行态承载**（候选 C3，database outbox / inbox）的 record schema、唯一约束、禁止字段、失败码与状态机引用。本 ICD 是 Stage 7 切片 3 的契约产物：

- 把 [`ICD-Agent-Bus-Forwarding`](ICD-agent-bus-forwarding.md)（Stage 4 设计态语义）与 [`L2 forwarding-outbox-inbox`](../../../../architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md)（Stage 7 组件 / 状态机）投影为可被 harness 字段级校验的 schema。
- 明确 outbox / inbox record 的每个字段：owner、是否必填、是否可变、脱敏要求。
- 锁定禁止字段，防止 payload body / token stream / Task execution state / 物理 endpoint 渗入 record。

本 ICD 是 **draft / 契约态**：Stage 7 交付 schema 草案、端口接口、状态机、harness、in-memory test double；**Stage 8 补齐 record 模型、claim / lease 端口、dispatcher worker skeleton 与 schema / migration 草案**（[`forwarding-persistence`](../../../../architecture/docs/L2/agent-bus/forwarding-persistence.md)）；**真实持久化已在 Stage 12 落地**——Postgres JDBC adapter（Spring JDBC）+ Flyway migration + §7.3 RLS（[`forwarding-persistence §7/§14`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)、[`decision §8`](../../10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)）；真实投递绑定 PoC 已在 Stage 15 落地**——A2A HTTP transport adapter（`A2aForwardingDeliveryPort`，消费 agent-runtime 的 A2A JSON-RPC `/a2a` 端点，圈进 `transport.a2a` 子包，§6.1 第 4 项解除、§6.2 不变）；最终 push / pull / MQ 投递模型裁决仍 deferred（独立 H2/H3 议题）。

machine-readable schema 见 [`agent-bus-forwarding-runtime.v1.yaml`](../machine-readable/agent-bus-forwarding-runtime.v1.yaml)。

## 适用读者

`agent-bus` forwarding runtime owner、outbox / inbox 端口实现者、harness 生成器、`agent-runtime` owner、架构评审者。

## outbox record 字段

outbox 是发送端 durable queue，承载一条 forwarding 消息从入队到终态（ACKED / DLQ / EXPIRED）的完整生命周期。

| Field | Required | Mutable | Owner | 脱敏 | 说明 |
|---|---|---|---|---|---|
| `tenantId` | ✓ | ✗ | 调用方 / discovery（tenant scope） | tenant 标识，审计保留，不脱敏 | Rule R-C.c；强制非空非 blank；必须等于 `routeHandle` 的 tenant scope。 |
| `messageId` | ✓ | ✗ | forwarding 底座 | opaque 标识，不脱敏 | `ForwardingMessageId`；outbox 唯一键组成部分。 |
| `sourceServiceId` | ✓ | ✗ | 调用方 | 服务标识，审计保留 | 发起转发的 service instance。 |
| `targetServiceId` | ✓ | ✗ | discovery（经 routeHandle） | 服务标识，审计保留 | 目标 service instance；来自 Stage 3 discovery，非物理 endpoint。 |
| `routeHandle` | ✓ | ✗ | discovery | opaque，不脱敏 | `ForwardingRouteHandle`；opaque 封装 endpoint / topic / routeKey，转发方不暴露物理 endpoint。 |
| `payloadRef` | 条件必填 | ✗ | 调用方 | 引用，不内联正文 | MI5-003 方案 B：`DATA_BEARING` 消息必填，`CONTROL_ONLY` 可省略；一旦出现，载荷走 data reference path。 |
| `status` | ✓ | ✓ | dispatcher / 状态机 | 枚举，不脱敏 | `ForwardingStatus.Outbox`：PENDING / DISPATCHING / ACKED / RETRY_SCHEDULED / DLQ / EXPIRED。 |
| `attemptCount` | ✓ | ✓ | dispatcher | 计数，不脱敏 | 投递尝试次数；从 0 起，每次 RETRY 递增。 |
| `nextAttemptAt` | 条件必填 | ✓ | dispatcher worker（`ForwardingRetryPolicy`） | 时间戳，不脱敏 | 仅 `RETRY_SCHEDULED` 必填；`claimDue(now)` 的 due 选取条件（MI8-001）；重投时刻由 `ForwardingRetryPolicy` 计算（Stage 14，§15），此处是 policy 决策的 persisted 存储。 |
| `createdAt` | ✓ | ✗ | 底座 | 时间戳，不脱敏 | 入队时间。 |
| `updatedAt` | ✓ | ✓ | 底座 | 时间戳，不脱敏 | 最近状态变更时间。 |
| `lastFailureCode` | ✗ | ✓ | dispatcher | 枚举，不脱敏 | 最近失败码；`ForwardingFailureCode`；终态 ACKED 时为 null。 |
| `leaseOwner` | ✗ | ✓ | dispatcher worker（claim） | owner 标识，审计保留 | **Stage 8 additive**（MI8-001）；claim 持有者；null 表示未被 claim。 |
| `leaseUntil` | ✗ | ✓ | dispatcher worker（claim） | 时间戳，不脱敏 | **Stage 8 additive**（MI8-001）；claim 独占截止时间；过期可被重新 claim。 |

唯一约束：`(tenantId, messageId)`。`leaseOwner` / `leaseUntil` 是 Stage 8 additive 字段，运行态投影为 `ForwardingLease`（Java record 嵌套 `lease` 字段）；并发抢占语义见 [`forwarding-persistence §4`](../../../../architecture/docs/L2/agent-bus/forwarding-persistence.md)。

## inbox record 字段

inbox 是接收端去重 / 幂等 / 审计记录。

| Field | Required | Mutable | Owner | 脱敏 | 说明 |
|---|---|---|---|---|---|
| `tenantId` | ✓ | ✗ | 接收方校验 | tenant 标识，审计保留 | Rule R-C.c；必须等于消息 envelope 的 tenantId。 |
| `messageId` | ✓ | ✗ | forwarding 底座 | opaque 标识，不脱敏 | 与 outbox messageId 对应；inbox 去重键组成部分。 |
| `consumerServiceId` | ✓ | ✗ | 接收方 | 服务标识，审计保留 | 消费该消息的 service instance；不同 consumer 各自独立去重。 |
| `status` | ✓ | ✓ | inbox / 状态机 | 枚举，不脱敏 | `ForwardingStatus.Inbox`：RECEIVED / DUPLICATE_SUPPRESSED / CONSUMED / REJECTED。 |
| `receivedAt` | ✓ | ✗ | inbox | 时间戳，不脱敏 | 首次接收时间。 |
| `consumedAt` | ✗ | ✓ | 接收方 | 时间戳，不脱敏 | 仅 `CONSUMED` 必填。 |
| `failureCode` | ✗ | ✓ | inbox | 枚举，不脱敏 | 仅 `REJECTED` / `DUPLICATE_SUPPRESSED` 必填；`ForwardingFailureCode`。 |

唯一约束 / 去重键：`(tenantId, messageId, consumerServiceId)`。

> MI8-004：`idempotencyKey`（envelope 字段）**不进** inbox 去重键，降级为审计字段。去重以 `(tenantId, messageId, consumerServiceId)` 为主，不随 `idempotencyKey` 变化。如需保留 `idempotencyKey` 供审计，作为 inbox 可选非键列后续追加，不改去重语义。

## 禁止字段

outbox / inbox record **始终不得**包含：

- **payload body**：大载荷走 `payloadRef` data reference path，不进 record 正文（ICD-Agent-Bus-Forwarding Forbidden Payload）。
- **token stream**：token / 凭证流不进 record（归对应 owner / 走引用）。
- **Task execution state**：`agent-bus` 不写 Task execution state，Task lifecycle owner 不变（HD4 / registry 边界）。
- **物理 endpoint**：转发方只持 opaque `routeHandle`，不直接暴露或操作物理 endpoint / topic / IP（HD4）。

## 失败码

`ForwardingFailureCode`（wire 名见 [`L2 §7`](../../../../architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md)）按分类（Stage 9，MI9-004）驱动 RETRY / DLQ / REJECT 路由：

- **non-retryable**（`nonRetryable()`）：`route_not_found`（→ DLQ）、`tenant_mismatch`（拒绝）、`payload_ref_invalid`（拒绝）。
- **retryable**（`retryable()`）：`delivery_timeout`、`receiver_unavailable`、`backpressure_rejected`（→ RETRY_SCHEDULED，耗尽 → DLQ / EXPIRED）。
- **dedup**（`dedup()`）：`duplicate_suppressed`（inbox 去重命中；不是投递失败，不驱动 RETRY / DLQ）。

`ForwardingDeliveryResult.retry(...)` 只接 retryable；`dlq(...)` 拒 dedup（接受 non-retryable 或 retryable 耗尽）。分类由 `ForwardingFailureCode.Classification` 在 Java 枚举上固化，schema / DDL / harness 一致。

## 状态机引用

outbox / inbox 状态机的完整迁移表（含触发条件、终态、失败码）见 [`L2 forwarding-outbox-inbox §4`](../../../../architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md)。本 ICD 不重复迁移表，只锁定 record schema；状态机由 `ForwardingStateMachine`（runtime 包，纯函数）裁决，端口实现调用状态机后持久化。

## 边界（Stage 7 / Stage 8 / Stage 9 / Stage 10 / Stage 11 / Stage 12 / Stage 13 / Stage 14 / Stage 15 / Stage 16 / Stage 17 / Stage 18 / Stage 19）

- Stage 7：schema 草案、端口接口、状态机、harness、in-memory test double。
- Stage 8：record 模型、claim / lease 端口、dispatcher worker skeleton、schema / migration 草案（DDL 草稿，**未执行**）、in-memory lease harness。
- Stage 9：lease-owner guarded mutation、lease 生命周期闭环、record 条件不变量（Java 构造器 + DDL CHECK + harness）、failure-code classification、claim / state-update SQL contract、in-memory lease-guard harness。
- Stage 10：worker lease 异常恢复（catch `ForwardingLeaseException` + skip，`DispatchTickResult.skipped` 计数自洽可观测）、lease 续约契约（`DispatchLeasePolicy`，deliver 前按阈值 renew，失败同 skip）、dispatch 调度责任（`ForwardingDispatchLoop` 骨架，`TickSource` / `IdleStrategy` 注入，无 scheduler / 线程 / 时钟）。
- Stage 11：runtime-completion——lease 续约触发时机改读注入 `EpochClock`（真实墙钟，使自然 loop 下耗时 deliver 接近 lease TTL 时续约能触发，MI11-001）、`deliver` 非 lease `RuntimeException` 兜底为 `skipped`（record 留 DISPATCHING 待重投，不丢消息；契约：真实 transport 绑定应把网络 / 超时 / 反序列化异常映射为 `ForwardingDeliveryResult`，**不应抛**非 lease 异常，MI11-002）、`runOnce` 异常契约（仅入参非法 fail-fast，`ForwardingDispatchLoop` 传播，MI11-003）。worker 通过注入 `EpochClock` 获取时间；loop 本身仍无 clock。
- Stage 12：real persistence —— **打破路径 B**：Postgres JDBC adapter（Spring JDBC，`JdbcForwardingOutbox` / `JdbcForwardingInbox` / `ForwardingSqlCodec`；claim §7.1 `FOR UPDATE SKIP LOCKED RETURNING` / lease-guarded §7.2 `WHERE` + 0 行分类 / reclaim / renew / release 过期语义）+ Flyway migration `V1__create_agent_bus_forwarding_outbox_inbox.sql`（MI9-006 全部 CHECK + `ix_outbox_claim_due` 索引 + §7.3 RLS fail-closed）+ real-SQL 验证（embedded-postgres PG 16.2 in-process，§7.4，17 tests green）。§6.1「不引入 JDBC driver」由 Stage 12 H2/H3 裁决**解除**；adapter + migration 归属 = agent-bus 自有，agent-bus 从纯 Java 模块变为 Spring 模块（ArchUnit 把 Spring/JDBC 限制在 `persistence.jdbc` 子包）。**transport 投递模型已在 Stage 13 完成候选评审**（见下 Stage 13 条），真实投递绑定仍 deferred（待 H2/H3 最终裁决 push / pull / MQ）。
- Stage 13：transport / 投递模型候选评审（裁决阶段、无生产代码）—— 比较 T1 dispatcher-push over sync RPC / T2 dispatcher-push over broker / T3 consumer-pull over DB / T4 C3+broker hybrid 四候选 × 8 维度（[`transport-candidates`](../../10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md)）；核心结论「反压诉求内核 = 消费方控速 / 速率解耦，MQ 只是满足该内核的载体之一」，T3 consumer-pull over DB 是唯一不破 `§6.2` 的强反压候选（复用 Stage 12 claim / lease / `SKIP LOCKED` / RLS），作为非裁决推荐进入 H2/H3。deliver 异常重投策略（退避 / attemptCount 上限 / 熔断）拆为可独立先行的子项。**最终 push / pull / MQ 裁决由 H2/H3 做**；选 T2 / T4 需解除 `§6.2` 引 MQ。
- Stage 14：deliver 重投策略先行（独立于 Stage 13 未裁决的 transport 模型，`§6.2` 不变）—— `ForwardingRetryPolicy` 端口（`ExponentialBackoff` 默认实现，overflow-safe 指数退避 `min(cap, base<<shift)` + 注入 `LongSupplier` jitter + `exhausted(attemptCount)`→DLQ，与 `DispatchLeasePolicy` 同层注入 worker）+ worker `RETRY_SCHEDULED` 分支接入；`ForwardingDeliveryResult` **移除** `nextAttemptAtMillisEpoch` 字段、`retry(code)` 简化为单参（重投时机归 policy，交付动作与重试治理分离关注点；outbox record 的 `nextAttemptAt` 仍是 policy 决策的 persisted 字段）；`ForwardingCircuitBreaker` 端口 + `ALWAYS_CLOSED` no-op **deferred 未接入**（形态依赖 transport 模型；**Stage 15 选 T1 push 后阻塞解除，Stage 16 已接入 worker**）。158 tests green（[`forwarding-persistence §15`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。
- Stage 15：真实投递绑定 PoC（A2A HTTP transport adapter，`§6.1` 第 4 项解除、`§6.2` 不变）—— `A2aForwardingDeliveryPort implements ForwardingDeliveryPort`（per-endpoint `JSONRPCTransport` 缓存 + `sendMessageStreaming` 阻塞 + 终态映射：COMPLETED / INPUT_REQUIRED→`acked`、`isFinal && !COMPLETED` / `AUTH_REQUIRED`→`retry(RECEIVER_UNAVAILABLE)`、超时→`retry(DELIVERY_TIMEOUT)`、连接级错误→`retry(RECEIVER_UNAVAILABLE)`、resolver 空→`dlq(ROUTE_NOT_FOUND)`），消费 agent-runtime 的 A2A JSON-RPC `/a2a` 端点；`ForwardingEndpointResolver` 注入端口解开 routeHandle opaque（HD4 不破，默认 `MapEndpointResolver`，生产由 Stage 3 registry 实现）；同步等完成语义（= Stage 13 的 T1 dispatcher-push over sync RPC，镜像 main 的 `A2aRemoteAgentOutboundAdapter`）。**线上格式对称性**：harness 用 A2A SDK 自身序列化器产出的 SSE 帧与 agent-runtime `A2aJsonRpcController` 逐字节一致（同 Stage 12 embedded-postgres「用测试载体验证真实协议代码」哲学）。**SDK 行为发现**：HTTP 4xx / 5xx 不被当错误（SDK 视非 2xx 为静默空 SSE 流 → `DELIVERY_TIMEOUT`），真正 socket 断开才触发 `errorConsumer`。tenant（R-C.c）走 `ClientCallContext` 的 `X-Tenant-Id` header；`payloadRef` 走 `MessageSendParams.metadata` 扩展位（`TextPart` 仅载 routing descriptor）。**164 tests green**（158 + 5 MockWebServer 场景），ArchUnit 把 `org.a2aproject` 圈进 `transport.a2a` 子包。**deferred**：真实 agent-runtime 端到端验证、`REMOTE_TASK_FAILED` non-retryable 码、registry 集成的 resolver 生产实现、连接池治理、最终 push / pull / MQ 裁决（[`decision §8`](../../10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)、[`forwarding-persistence §16`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。**熔断接入由 Stage 16 完成**；`REMOTE_TASK_FAILED` non-retryable 码 + 失败路径端到端验证由 **Stage 18 完成**（见下）。
- Stage 16：`ForwardingCircuitBreaker` 接入 worker（`§6.2` 不变）—— Stage 14 预留的 breaker seam（端口 + `ALWAYS_CLOSED` no-op）由 Stage 16 填上（正当性来自 Stage 15 PoC 选 T1 push：push 需主动短路故障 route）。`ForwardingCircuitBreaker` 加 `recordOutcome(routeHandle, result)`；真实实现 `RouteCircuitBreaker`（纯 JDK 三态机 CLOSED→OPEN→HALF_OPEN，`failureThreshold` / `cooldownMillis` / 注入 `EpochClock`，per-route `ConcurrentHashMap` + `synchronized` + `probeInFlight` 单探测锁）；worker 7 参构造器注入，`runOnce` 三处接入（投递前 `allowsDelivery` 短路复用 skip 路径留 DISPATCHING 不消耗 attemptCount、投递后 `recordOutcome`、deliver 抛异常 catch 补 `recordOutcome(retry(RECEIVER_UNAVAILABLE))`）。触发分类在 breaker 内部：ACKED→成功、RETRY_SCHEDULED→失败、DLQ / EXPIRED→忽略。`DispatchTickResult` 不变。**transport-agnostic 不破**（端口只消费 routeHandle / delivery result，HD4 守恒；即便最终选 T3 也无害）。**179 tests green**（164 + 11 `RouteCircuitBreakerTest` + 4 worker 行为测试），ArchUnit 纯度 green（纯 JDK 无需新豁免），§6.2 不变（[`decision §8`](../../10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)、[`forwarding-persistence §17`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。**deferred**：per-route 状态非持久化、breaker 参数生产调优 / per-route 配置、HALF_OPEN 单探测、多 worker 共享单例、最终 push / pull / MQ 裁决。
- Stage 17：首次跨模块集成 + 真实端到端验证闭环（`§6.2` 不变）—— agent-bus ↔ agent-runtime 的**第一个依赖**（仅 test scope，两模块此前完全隔离构建）：`C3ForwardingEndToEndIntegrationTest` 用**真实的 `LocalA2aRuntimeHost`**（Spring Boot A2A 服务器）替换 Stage 15 的 MockWebServer，端到端驱动完整链路 `JdbcForwardingOutbox enqueue → ForwardingDispatchLoop tick → worker claim/lease-guarded → A2aForwardingDeliveryPort deliver → 真实 /a2a JSON-RPC+SSE → StubHandler → COMPLETED → worker markAcked → outbox record ACKED`（复用 Stage 12 真实 Postgres + Stage 15 A2A adapter + Stage 10 测试 TickSource 无 scheduler）；双测试 green（happy path + 端到端租户隔离 R-C.c，tenant A 两条 route 指向同一真实 host、loop 只 ACK tenant A）。`agent-bus/pom.xml` 加 test-scope `agent-runtime` 依赖；`AgentBusDependencyBoundaryTest` 加 `bus_does_not_depend_on_agent_runtime` 守卫（生产仍零依赖 runtime，取代 034da8f7 重命名遗留的旧 `service..` 守卫）。**两个发现**：(a) `LocalA2aRuntimeHost` 的 A2A server Spring 上下文纯内存但对 JDBC-bearing 共享 classpath 敏感 —— agent-bus 的 jdbc starter + flyway（Stage 12）泄漏后触发 `DataSourceAutoConfiguration` / `FlywayAutoConfiguration` 对真实 host 上下文 fire，IT 用 `System.setProperty("spring.autoconfigure.exclude", …)` 排除；(b) **Spring Boot 4 autoconfigure 重打包** —— jdbc 从 `…autoconfigure.jdbc` 移到 `org.springframework.boot.jdbc.autoconfigure`、flyway 移到 `org.springframework.boot.flyway.autoconfigure`，旧包名排除静默无效。**意外强证据**：真实 `A2aAgentExecutor` 收到 `metadata.tenantId`（证明 `X-Tenant-Id` header 不只发送、更被真实 controller 接收/解析）。**182 tests green**（179 + 2 IT），ArchUnit green，§6.2 不变（IT 无 Task state / payload body / concrete broker）。**deferred**：真实 agent handler（用 StubHandler）、registry 集成的 resolver 生产实现、连接池治理、`REMOTE_TASK_FAILED` non-retryable 码、push/pull/MQ 最终裁决（[`decision §8`](../../10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)、[`forwarding-persistence §18`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。
- Stage 18：失败路径端到端验证 + `REMOTE_TASK_FAILED` non-retryable 码收口（`§6.2` 不变）—— 闭合 Stage 17 happy-path-only 盲区（Stages 7-16 的失败处理路径此前只在 fake-delivery 单元 / 契约测试覆盖）：`ForwardingFailureCode.REMOTE_TASK_FAILED` NON_RETRYABLE 码（远程 agent 终态业务失败 FAILED/CANCELED/REJECTED —— 对确定失败的 task 重投同输入只会轰炸下游 → 直达 DLQ，正交于 Stage 14 retry policy；AUTH_REQUIRED 仍 retry 可恢复）+ `A2aForwardingDeliveryPort` 终态映射从保守 switch 改 `isFinal()` if-chain；`C3ForwardingFailurePathIntegrationTest` 双失败场景端到端（真实 `FailingHandler` FAILED→DLQ persisted `last_failure_code=remote_task_failed`；不可达 route `freeUnusedPort()` socket 拒连→RETRY persisted `attempt_count=1` + future `next_attempt_at`）；无 DDL / SqlCodec / record 改动（outbox CHECK 不枚举码值、`decodeFailureCode` 遍历 `values()`、DLQ compact constructor 接受任意 non-retryable 码）；IT 复用 Stage 17 boot recipe（唯一差异 FailingHandler）。**184 tests green**（182 + 2 失败路径 IT），ArchUnit green，§6.2 不变（[`decision §8`](../../10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)、[`forwarding-persistence §19`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。**deferred**：真实 agent handler（仍 FailingHandler/StubHandler）、registry 集成的 resolver 生产实现、连接池治理、push/pull/MQ 最终裁决。
- Stage 19：重投往返生命周期端到端验证（`§6.2` 不变）—— 闭合 Stage 18 最大盲区：Stage 18 把 record 推进到 `RETRY_SCHEDULED` 就停（单 tick，只证明重投路径入口），`claimDue` 的 `RETRY_SCHEDULED` 回收子句 `next_attempt_at <= :now`（outbox 价值主张核心：持久化 + 自动重驱动到成功或 DLQ）**从未端到端触发**。Stage 19 在真实持久化上驱动同一条 record 越过 `next_attempt_at` 跨多 tick，看 `claimDue` 回收、重投、`attempt_count` 攀升到两出口。两场景：`C3ForwardingRetryLifecycleIntegrationTest` (A) 持续失败→重投 3 次→exhausted(`maxAttempts=3`)→DLQ（`attempt_count=3`，`moveToDlq` 不递增）/ (B) 间歇恢复→重投 2 次→ACKED（fake port 注入 `[retry,retry,acked]`，`attempt_count=2`，`markAcked` 不递增）。时间控制：`MutableEpochClock`（test-only 纯 JDK）注入 worker + 协调多 tick `TickSource`（`+61s` step > 任何 backoff），无需 sleep/scheduler（§6.1 守恒）；两时钟协调（tick source yield 前 `advanceTo` clock，worker `clockNow` == `claimDue :now`）；真实时钟约束（`leaseGuardedUpdate` 用真实 `System.currentTimeMillis`，tick instant 从 `T0` 单调递增，`leaseUntil = instant+60s` 始终 > 真实时钟）。flaky 修复：parent pom surefire 4 路并发跑测试类但 Spring Boot 4 `SpringApplication.run` 非线程安全，两个 context-boot `*IntegrationTest`（命名绕过 failsafe `*IT` include、落并发 surefire）撞 `ConcurrentModificationException` + 全局 `spring.autoconfigure.exclude` 踩踏；两 IT 加 JUnit5 `@Isolated` 独占执行根治。**无生产代码改动**（纯测试 / 验证阶段）。**186 tests green**（184 + 2 retry lifecycle IT），ArchUnit green，§6.2 不变（两场景用 CONTROL_ONLY envelope；MutableEpochClock/tick source/fake port 是 test-only 纯 JDK，无 concrete broker / payload body / token stream / Task 执行状态）。**deferred**：真实 retry 调度（`ForwardingDispatchLoop` 接真实 scheduler/polling cadence）、真实 agent handler（仍 StubHandler/FailingHandler/fake port）、registry 集成的 resolver 生产实现、连接池治理、push/pull/MQ 最终裁决（[`decision §8`](../../10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)、[`forwarding-persistence §20`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。
- **不引入** broker / MQ client（broker-agnostic；C4 是后续阶段可选上层；A2A 是 HTTP JSON-RPC 非 broker/MQ）。
- 不改 Task lifecycle owner；不绕 routeHandle；不放 payload body / token stream；不暴露物理 endpoint。

## Contract Tests（harness 镜像）

- `forwarding_runtime_outbox_record_has_required_fields`
- `forwarding_runtime_inbox_record_has_required_fields`
- `forwarding_runtime_outbox_unique_key_is_tenant_and_message_id`
- `forwarding_runtime_inbox_dedup_key_includes_consumer`
- `forwarding_runtime_forbids_payload_body_token_stream_task_state_endpoint`
- `forwarding_runtime_status_values_match_l2_state_machine`
- `forwarding_runtime_failure_codes_cover_l2_semantics`
- `forwarding_runtime_outbox_record_carries_source_and_target_service_id`（Stage 8，MI8-002）
- `claim_due_returns_only_tenant_scoped_due_non_terminal_records`（Stage 8，MI8-001）
- `claim_due_grants_exclusive_lease_one_owner_at_a_time`（Stage 8，MI8-001）
- `expired_lease_can_be_reclaimed`（Stage 8，MI8-001）
- `terminal_outbox_record_is_not_claimable`（Stage 8，MI8-001）
- `dispatcher_worker_routes_ack_retry_dlq_expired_via_fake_delivery`（Stage 8，MI8-003）
- `forwarding_persistence_does_not_write_task_state_nor_introduce_broker`（Stage 8，决策 §6.1）
- `stale_worker_acks_after_lease_reclaimed_by_another_owner_fails`（Stage 9，MI9-001）
- `lease_guarded_mutation_reports_record_not_found_and_no_lease`（Stage 9，MI9-001）
- `terminal_and_retry_states_clear_lease_only_dispatching_holds_it`（Stage 9，MI9-002）
- `outbox_record_rejects_invalid_status_invariants`（Stage 9，MI9-003）
- `inbox_record_rejects_invalid_status_invariants`（Stage 9，MI9-003）
- `failure_code_classification_drives_retry_and_dlq_routing`（Stage 9，MI9-004）
- `forwarding_persistence_ddl_enforces_record_invariants`（Stage 9，MI9-006）
- `worker_skips_record_when_lease_reclaimed_mid_tick`（Stage 10，MI10-001）
- `worker_renews_short_lease_before_delivery`（Stage 10，MI10-002）
- `worker_does_not_renew_when_lease_sufficient`（Stage 10，MI10-002）
- `worker_skips_when_lease_renew_fails`（Stage 10，MI10-002）
- `dispatch_loop_drives_ticks_from_injected_source_until_it_stops`（Stage 10，MI10-004）

harness 方法名逐字镜像本节，防 ICD / harness 漂移（同 Stage 4 约定）。

## Open Issues

- ~~真实 JDBC adapter + Flyway migration 归属~~ —— **Stage 12 已裁决**：agent-bus 自有 + Spring JDBC + Postgres + 启用 RLS（[`decision §4/§8`](../../10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)）；adapter / migration 已落地，real-SQL 验证 17 tests green（§7.4）。
- ~~数据库产品确认 + 是否启用 Postgres RLS~~ —— **Stage 12 已裁决**：Postgres + 启用 RLS 纵深防御（§7.3，含 fail-closed）。
- transport / 投递模型（push vs pull / 是否引 MQ / C3+broker hybrid）—— **Stage 13 已产出候选评审**（[`transport-candidates`](../../10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md)，T1-T4 × 8 维度，T3 consumer-pull over DB 作为非裁决推荐）；**Stage 15 已落地真实投递绑定 PoC**（A2A HTTP transport adapter，`A2aForwardingDeliveryPort`，同步等完成 = T1 dispatcher-push over sync RPC，证明 agent-bus 可消费 agent-runtime 的 `/a2a`，§6.1 第 4 项解除、§6.2 不变）；最终 push / pull / MQ 投递模型裁决仍待 H2/H3（选 T2 / T4 需解除 `§6.2` 引 MQ）。
- ~~deliver 异常重投策略（退避 / attemptCount 上限 / 熔断）可独立先行~~ —— **Stage 14 已落地先行子项**：`ForwardingRetryPolicy` 端口 + overflow-safe 指数退避 + `exhausted`→DLQ + worker RETRY 接入（`ForwardingDeliveryResult` 移除 `nextAttemptAtMillisEpoch`）；**熔断**（`ForwardingCircuitBreaker`）端口预留 `ALWAYS_CLOSED` no-op，~~**deferred 未接入**~~ —— **Stage 16 已接入 worker**（`RouteCircuitBreaker` 三态机 + `recordOutcome` 反馈 + `allowsDelivery` 短路，正当性来自 Stage 15 选 T1 push；§6.2 不变）。179 tests green（§17）。
- lease TTL 默认值、polling cadence、并发 worker 分片、backpressure 阈值、`ForwardingDispatchLoop` 接真实 scheduler —— 运维化 / 生产化阶段。
- ~~真实 agent-runtime 端到端验证~~ —— **Stage 17 已落地端到端 IT**（`C3ForwardingEndToEndIntegrationTest` 用真实 `LocalA2aRuntimeHost` 替换 Stage 15 MockWebServer，驱动 outbox → tick → deliver → 真实 /a2a → COMPLETED → ACKED 全链；用 StubHandler 验证转发链路，真实 agent handler 仍 deferred）；agent-bus 加 `agent-runtime` test-scope 依赖（生产仍零依赖，`AgentBusDependencyBoundaryTest.bus_does_not_depend_on_agent_runtime` 守卫），两个发现：`LocalA2aRuntimeHost` 对 JDBC-bearing 共享 classpath 敏感（agent-bus jdbc starter + flyway 泄漏触发 `DataSourceAutoConfiguration`，IT 用 `spring.autoconfigure.exclude` 排除）+ Spring Boot 4 autoconfigure 重打包（jdbc→`org.springframework.boot.jdbc.autoconfigure`、flyway→`org.springframework.boot.flyway.autoconfigure`，旧包名排除静默无效）。182 tests green（[`forwarding-persistence §18`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。
- ~~`REMOTE_TASK_FAILED` non-retryable 码（远程 task 失败的精确分类）+ 失败路径端到端验证~~ —— **Stage 18 已收口**：`ForwardingFailureCode.REMOTE_TASK_FAILED` NON_RETRYABLE 码（远程 agent 终态业务失败 FAILED/CANCELED/REJECTED → `dlq(REMOTE_TASK_FAILED)` 直达 DLQ，区别 infra 层 retryable；对确定失败的 task 重投同输入只会轰炸下游，正交于 Stage 14 retry policy；AUTH_REQUIRED 仍 retry 可恢复）+ `A2aForwardingDeliveryPort` 终态映射从保守 switch 改 `isFinal()` if-chain；`C3ForwardingFailurePathIntegrationTest` 双失败场景端到端（真实 `FailingHandler` FAILED→DLQ persisted `last_failure_code=remote_task_failed`；不可达 route `freeUnusedPort()` socket 拒连→RETRY persisted `attempt_count=1` + future `next_attempt_at`）；无 DDL / SqlCodec / record 改动（outbox CHECK 不枚举码值、`decodeFailureCode` 遍历 `values()`、DLQ compact constructor 接受任意 non-retryable 码）；IT 复用 Stage 17 boot recipe（唯一差异 FailingHandler）。**184 tests green**（182 + 2 失败路径 IT），ArchUnit green，§6.2 不变（[`forwarding-persistence §19`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。
- ~~重投往返生命周期端到端验证（claimDue 的 `RETRY_SCHEDULED` 回收子句跨多 tick 触发、`attempt_count` 攀升、exhausted/恢复两出口闭环）~~ —— **Stage 19 已验证**：`C3ForwardingRetryLifecycleIntegrationTest` 双场景端到端（持续失败重投 3 次→exhausted→DLQ `attempt_count=3` / 间歇恢复重投 2 次→ACKED `attempt_count=2`）；`MutableEpochClock` + 协调多 tick `TickSource` 时间控制无需 scheduler；`@Isolated` 修 context-boot IT 并发 flaky（§6.2 不变）。**186 tests green**（[`forwarding-persistence §20`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。
