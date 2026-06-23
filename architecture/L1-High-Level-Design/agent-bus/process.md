---
level: L1
module: agent-bus
view: process
status: active
---

# agent-bus 进程视图

## 1. 进程视图原则

`agent-bus` 的进程视图只描述跨边界流量如何流动，不把 Task 生命周期执行权放入 bus。

进程视图中的所有流程都遵守两条规则：

- 进入或跨越边界的请求必须带有可追踪的 envelope。
- 状态机最终决策仍回到对应 owner，例如 Task 状态回到 `agent-runtime`。

> 命名说明：本文架构语义（参与者、所有权、流程角色）使用 L0 逻辑名 `agent-runtime` / `agent-core`。`agent-runtime` 已落地为同名模块（原 `agent-service` 已重命名为 `agent-runtime`）；`agent-core` 已落地为 `agent-core`。完整映射见 [`README.md`](README.md)「命名说明」。

## 2. C2S ingress 流程

| 步骤 | 参与者 | 动作 |
|---|---|---|
| 1 | `agent-client` | 构造 `IngressEnvelope`，包含 `requestId`、`tenantId`、`idempotencyKey`、`requestType`、`payload`、`traceId`。 |
| 2 | Gateway / `IngressGateway` | 校验 envelope 的基础字段、trace、幂等和入口规则。 |
| 3 | Gateway | 将请求路由到 `agent-runtime`。 |
| 4 | `agent-runtime` | 按 Task 生命周期规则创建、查询、取消或恢复 Task。 |
| 5 | Gateway | 返回 `IngressResponse`，表达 `ACCEPTED`、`REJECTED` 或 `DEFERRED`。 |

关键约束：

- Gateway 可以返回 cursor / ack / rejection。
- Gateway 不直接写 Task execution state。
- 运行结果通过后续查询、SSE、webhook 或 callback 观察，不阻塞 ingress 调用。

## 3. S2C callback 流程

| 步骤 | 参与者 | 动作 |
|---|---|---|
| 1 | `agent-runtime` / runtime | 某个 Run 需要客户端能力，进入等待客户端 callback 的流程。 |
| 2 | runtime | 构造 `S2cCallbackEnvelope`，通过 `SuspendSignal.forClientCallback(...)` 承载。 |
| 3 | `S2cCallbackTransport` | 将请求派发到 client/edge。 |
| 4 | `agent-client` | 执行本地 capability，返回 `S2cCallbackResponse`。 |
| 5 | `agent-runtime` | 校验 response schema，并决定 Run 恢复、失败或超时。 |

当前事实：

- `S2cCallbackEnvelope` 已携带 `tenantId`（Stage 2 契约层迁移，commit `d894f494`，Rule R-C.c）。
- runtime 侧构造点（envelope construction binding）与 schema validation integration 仍待后续波次，不改 Task lifecycle 所有权。

失败路径：

- client 超时：service 将 Run 转入失败或对应终态。
- response schema invalid：service 拒绝恢复，并记录失败原因。
- transport failure：应通过 returned stage 异常完成，而不是在 transport 中同步抛出。

## 4. Runtime 与 Core 流程

| 步骤 | 参与者 | 动作 |
|---|---|---|
| 1 | `agent-runtime` | 通过 `EnginePort.execute(...)` 驱动执行。 |
| 2 | `agent-core` | 实现 `EnginePort` 并返回 `AgentEvent` stream。 |
| 3 | `EnginePort` | 要求最后发出唯一 terminal event。 |
| 4 | `agent-runtime` | 根据 event 更新 Task/Run 状态。 |

`agent-bus` 在这里提供中立 SPI home。它不因此成为 execution owner，也不拥有 Run aggregate。

## 5. Federation / reflection 流程

Federation 和 reflection 属于真 bus 范围：

- `FederationGateway` 表达跨网络、跨部署的 ingress 转发。
- `ReflectionEnvelopeRouter` 表达从云侧 Slow Track 到 edge Fast Track 的 reflection route。

当前状态：

- SPI 已存在。
- broker、credential、routing policy、delivery guarantee 仍未落地。
- 运行时实现需要单独 H2/H3 决策。

## 6. Future workflow primitives

Mailbox、admission、backpressure、sleep、wakeup、tick 当前只保留设计态。它们会影响运行时进程语义，因此不能由自动化直接生成实现。

进入实现前必须先明确：

- 谁拥有队列状态。
- 谁做 admission decision。
- backpressure 如何传播。
- tick/rhythm 的时间源是谁。
- 失败、重试、DLQ 的 owner 是谁。

## 7. 真 bus 目标态流程：类 MQ 转发

真 bus 的 runtime-to-runtime 调用目标态应包含一个类似 MQ 的转发底座，但 L1 不绑定具体产品。

目标态流程：

| 步骤 | 参与者 | 动作 |
|---|---|---|
| 1 | 调用方 service | 构造跨服务 envelope，携带 tenant、trace、correlation、idempotency 和目标能力。 |
| 2 | 真 bus | 查询 agent/service/capability 注册发现索引，选择目标 endpoint 或 topic。 |
| 3 | 类 MQ 转发底座 | 将 envelope 投递到队列、主题或等价通道。 |
| 4 | 目标 service | 消费 envelope，按自身 Task/Run 生命周期处理。 |
| 5 | 真 bus | 记录 ack/retry、timeout、DLQ/replay、correlation 和审计事实。 |

目标态能力：

- 队列/主题抽象。
- ack/retry。
- correlation。
- backpressure。
- DLQ/replay。
- ordering/fairness。
- tenant-aware routing。

当前状态：

- 「目标态流程 / 能力」是抽象语义清单：其中 ack / retry / DLQ / correlation / tenant-aware routing 已由 C3 运行态承载落地（见下条）；backpressure / ordering / fairness / replay store 的运行态实现仍 deferred。
- 类 MQ 转发底座的运行态承载已最终确认为 **C3（database outbox / inbox），`adopted-c3`**；broker 产品仍 broker-agnostic，不绑定具体 broker / MQ 产品。Stage 4 起草 broker-agnostic 转发语义 ICD 与设计态 harness（见 [`ICD-agent-bus-forwarding`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md)）。Stage 5 做候选评审（见 [`agent-bus-forwarding-runtime-candidates`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md)）。Stage 6 / Stage 7 / Stage 8 / Stage 9 / Stage 10 按 [`agent-bus-forwarding-runtime-decision`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md) 落地：Stage 7 最小骨架（领域模型 + 端口 + 状态机 + in-memory 替身 + harness），Stage 8 持久化准备（record 模型 + claim / lease 端口 + dispatcher worker skeleton + 抽象 delivery 端口 + schema / migration 草案，DDL 未执行 + in-memory lease harness，见 [`forwarding-persistence`](../../L2-Low-Level-Design/agent-bus/forwarding-persistence.md)），Stage 9 lease-safe / persistence-ready（lease-owner guarded mutation、lease 生命周期闭环、record 条件不变量、failure-code classification、claim / state-update SQL contract；DB / migration 归属未确认 → 路径 B，不引入 JDBC），Stage 10 dispatch-loop runtime（worker lease 异常恢复 catch `ForwardingLeaseException` + skip、lease 续约 `DispatchLeasePolicy`、`DispatchTickResult` skipped 计数自洽、`ForwardingDispatchLoop` 骨架 `TickSource` / `IdleStrategy` 注入；DB / migration 归属经人类再确认 → 路径 B），Stage 11 runtime-completion（lease 续约触发时机改读注入 `EpochClock`、`deliver` 非 lease `RuntimeException` 兜底为 skipped、`runOnce` 仅入参非法 fail-fast 且 `ForwardingDispatchLoop` 传播；DB / migration / transport 未变 → 路径 B），Stage 12 real persistence（**打破路径 B**：Postgres JDBC adapter Spring JDBC + Flyway migration `V1` + §7.3 RLS；real-SQL 验证 embedded-postgres PG 16.2，17 tests green；§6.1「不引入 JDBC」解除，ArchUnit 把 Spring/JDBC 圈进 `persistence.jdbc` 子包；§6.2 不变），Stage 13 transport 投递模型候选评审（T1-T4 × 8 维度，T3 consumer-pull over DB 非裁决推荐），Stage 14 deliver 重投策略先行（`ForwardingRetryPolicy` overflow-safe 指数退避 + exhausted→DLQ，熔断端口 deferred；§6.2 不变），Stage 15 真实投递绑定 PoC（A2A HTTP transport adapter `A2aForwardingDeliveryPort` 消费 agent-runtime `/a2a`，同步等完成 = T1 push；`§6.1` 第 4 项解除、`§6.2` 不变；ArchUnit 把 `org.a2aproject` 圈进 `transport.a2a` 子包），Stage 16 断路器接入 worker（`RouteCircuitBreaker` 三态机 CLOSED→OPEN→HALF_OPEN，正当性来自 Stage 15 选 T1；纯 JDK transport-agnostic，§6.2 不变），Stage 17 首次跨模块端到端集成（真实 `LocalA2aRuntimeHost`，happy-path：outbox enqueue → tick → deliver → 真实 /a2a → COMPLETED → ACKED；agent-bus 加 `agent-runtime` test-scope 依赖、生产零依赖；182 tests green），Stage 18 失败路径端到端 + `REMOTE_TASK_FAILED` 码收口（真实 FAILED→DLQ / 不可达 route→RETRY；终态映射改 `isFinal()` if-chain；无 DDL/SqlCodec/record 改动；184 tests green；§6.2 不变）。
- 真实持久化（Stage 12 JDBC adapter + Flyway + RLS）与真实投递绑定（Stage 15 A2A transport adapter）已落地，Stage 17/18 端到端验证通过 + Stage 19 重投往返生命周期端到端验证通过（186 tests green）；**仍 deferred**：真实 queue / replay store 物理实现、push vs pull / 是否引 MQ 的最终投递模型裁决（独立 H2/H3 议题）、registry 集成的 resolver 生产实现、连接池治理。
- forwarding envelope 有载荷时只携带 `payloadRef`（条件必填，MI5-003 方案 B；纯控制消息可省略；不携带 payload body / token stream / Task execution state），通过 `routeHandle` 消费 Stage 3 discovery，不改变远端 Task lifecycle owner（见 forwarding ICD）。

## 8. 真 bus 目标态流程：Agent 注册与发现

Agent 注册与发现是 service-to-service 路由的前置能力。它回答“某个 tenant 下，哪个 service/agent/capability 可以处理这个 envelope，应路由到哪里”。完整设计态契约见 [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md)。

目标态流程（HD3 裁决方向）：

| 步骤 | 操作 | 参与者 | 说明 |
|---|---|---|---|
| 1 | register | service/agent → registry | 注册 `(tenantId, agentId|serviceId, capability, routeKey, contractVersion, capabilityVersion, endpoint, lease)`。`tenantId` 强制。 |
| 2 | renew-lease / heartbeat | owner → registry | 续 lease/TTL；到期则 entry 从可见集移除。 |
| 3 | discover | gateway/真 bus → registry | query 必须携带 `tenantId` + `capability`（+ 可选 version/health 过滤）。跨 tenant 查询显式失败。 |
| 4 | route result | registry → caller | 返回 opaque route handle + health + contractVersion/capabilityVersion；不携带 Task execution state。 |
| 5 | deregister | owner → registry | owner 主动注销（可选）。 |

注册 entry 必填字段（与 ICD 对齐）：

- `tenantId`、`agentId`/`serviceId`、`capability`、`routeKey`
- `contractVersion`、`capabilityVersion`
- `endpoint`/`routeTarget`（logical）、`leaseId`/`expiryEpoch`

可选字段：`health`、`region`/`deploymentVariant`、`weight`。

边界：

- 注册发现只拥有运行时路由索引，不拥有 agent 业务定义或 Task/Run 状态。
- discovery result 不得携带 Task execution state。
- 跨 tenant 查询必须显式失败（`tenant_isolation_violation`），禁止跨 tenant fallback。
- version mismatch、unhealthy target、lease expired 都必须有显式 result 状态。
- Stage 3 只定义接口和 harness 断言，不实现 runtime registry（durable/memory 选择 deferred）。

## 9. 进程断言

| 断言 | 证据 |
|---|---|
| C2S 必须经过 `IngressGateway`。 | client 模块依赖规则和 ingress SPI。 |
| S2C 必须经过 `S2cCallbackTransport`。 | runtime L1 场景和 S2C SPI。 |
| Task 状态只由 runtime 生命周期层更新。 | L0 boundaries 状态矩阵。 |
| Engine terminal event 必须唯一且最后发出。 | `EnginePort` 契约和后续 harness。 |
| S2C envelope 必须携带 `tenantId`（契约层已迁移，Stage 2）。 | `s2c-callback.v1.yaml` required fields 与 Stage 2 迁移记录。 |
| 真 bus 转发底座已采用 C3（`adopted-c3`）；ack / retry / DLQ / 失败语义已在 Stage 4 / 7 定义，claim / lease 并发抢占 + dispatcher worker 在 Stage 8 落地骨架，lease-owner guarded mutation + record 不变量 + failure-code 分类在 Stage 9 落地，worker lease 异常恢复 + 续约 + dispatch loop 在 Stage 10 落地，lease 续约触发时机（注入 `EpochClock`）+ deliver 异常兜底 + `runOnce` fail-fast 在 Stage 11 落地，Stage 12 落地真实 Postgres JDBC adapter + Flyway migration + RLS（打破路径 B），Stage 13 transport 候选评审，Stage 14 deliver 重投策略（overflow-safe 退避 + exhausted→DLQ），Stage 15 真实 A2A 投递绑定 PoC（`A2aForwardingDeliveryPort`），Stage 16 断路器接入 worker（三态机），Stage 17 首次跨模块 happy-path 端到端，Stage 18 失败路径端到端 + `REMOTE_TASK_FAILED` 码收口（184 tests green）+ Stage 19 重投往返生命周期端到端验证（claimDue RETRY_SCHEDULED 回收子句跨多 tick 触发，186 tests green）；真实 broker / queue / replay store + push-pull-MQ 最终投递模型仍 deferred；ordering / fairness / backpressure 参数 deferred 后续阶段。 | [`agent-bus-forwarding-runtime-decision`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md) + [`forwarding-persistence`](../../L2-Low-Level-Design/agent-bus/forwarding-persistence.md)。 |
| Agent 注册发现的 owner、租户隔离、health 和 contract version 语义已在 [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md) 设计态定义。 | runtime 物理实现仍待后续波次；Stage 3 只定义接口和 harness 断言。 |
