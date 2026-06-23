---
level: L1
module: agent-bus
view: logical
status: active
---

# agent-bus 逻辑视图

## 1. 逻辑模型

`agent-bus` 的逻辑模型由两块组成：

| 逻辑块 | 面向对象 | 核心问题 |
|---|---|---|
| Gateway | 外部调用方进入内部平台 | 请求能否进入、进入哪里、如何返回同步确认、如何保持幂等和 trace。 |
| 真 bus | 内部 service 与 service 之间的相互调用 | 服务之间如何路由、如何关联、如何治理跨服务 envelope、类 MQ 转发底座、agent 注册发现和 future control/rhythm 流量。 |

Gateway 和真 bus 共享一个原则：它们治理跨边界流量，但不拥有业务 Task 生命周期。

> 命名说明：本文架构语义（所有权、参与者、状态归属、跨模块关系）使用 L0 逻辑名 `agent-runtime` / `agent-core`。`agent-runtime` 已落地为同名模块（原 `agent-service` 已重命名为 `agent-runtime`）；`agent-core` 已落地为 `agent-core`。完整映射见 [`README.md`](README.md)「命名说明」。

## 2. 逻辑元素

| 元素 | 类型 | 职责 | 所有者 |
|---|---|---|---|
| `agent-bus` | 模块 | 跨平面契约与治理中心 | bus 模块负责人 |
| Gateway | 逻辑子模块 | 外部到内部的入口治理、转发和调度 | bus 模块负责人 |
| 真 bus | 逻辑子模块 | service 与 service 之间的相互调用和跨服务治理 | bus 模块负责人 |
| 类 MQ 转发底座 | 目标态能力 | 跨 service 异步转发、队列/主题抽象、ack/retry、correlation、DLQ/replay、ordering/fairness、backpressure | bus 模块负责人 / 运维负责人 |
| Agent 注册与发现 | 目标态能力 | 提供运行时路由所需的 agent/service/capability 注册、发现、健康和路由元数据 | bus 模块负责人 |
| `IngressGateway` | SPI | C2S 入口表面 | bus 模块负责人 |
| `S2cCallbackTransport` | SPI | service 到 client 的能力回调表面 | bus 模块负责人 |
| `FederationGateway` | SPI | 跨网络/跨部署 ingress 转发 | bus 模块负责人 |
| `ReflectionEnvelopeRouter` | SPI | reflection envelope 路由 | bus 模块负责人 |
| `EnginePort` | SPI | service 与 execution engine 的中立边界 | bus/engine 共同负责 |
| Task execution state | 状态 | Task 生命周期持久化状态 | `agent-runtime` |

## 3. 逻辑关系

| 从 | 到 | 关系 | 说明 |
|---|---|---|---|
| `agent-client` | Gateway | 调用 | client 通过 `IngressGateway` 发起 C2S 请求。 |
| Gateway | `agent-runtime` | 转发/调度 | Gateway 把请求路由到生命周期所有者。 |
| `agent-runtime` | `S2cCallbackTransport` | 调用 | service 请求客户端托管能力。 |
| `S2cCallbackTransport` | `agent-client` | 派发 | callback 通过具体 transport 到达 client。 |
| `agent-runtime` | `EnginePort` | 驱动 | service 驱动 engine 执行。 |
| `agent-core` | `EnginePort` | 实现 | execution engine 提供具体执行能力。 |
| 真 bus | 远端 `agent-runtime` | 跨服务调用治理 | federation/reflection/control envelope 进入跨服务流。 |
| 真 bus | 类 MQ 转发底座 | 使用 | 真 bus 目标态通过转发底座承载跨 service 异步消息。 |
| 真 bus | Agent 注册与发现 | 查询/维护 | 真 bus 目标态基于注册发现结果选择目标 service、agent capability 或 endpoint。 |
| Agent 注册与发现 | `agent-runtime` | 读取/注册 | service 可以注册运行时可调用 agent/capability 元数据，但仍拥有 Task 生命周期。 |
| `agent-bus` | Task execution state | 禁止写入 | Task 状态所有权不属于 bus。 |

## 4. 状态所有权

`agent-bus` 不拥有下面这些状态：

- Task execution state。
- Task hierarchy。
- Task sleep / suspend / resume 的最终状态机。
- 单个 service instance 内部的多 agent 协同状态。

`agent-bus` 可以拥有或治理下面这些跨边界信息：

- callback correlation。
- request id / idempotency key。
- trace id。
- tenant scope。
- future mailbox/backpressure/tick 的 envelope 和治理规则。
- service / agent / capability 的运行时注册发现索引。

`agent-bus` 不应拥有下面这些注册发现之外的业务事实：

- agent 的业务定义源。
- agent 的代码包或工具实现。
- Task / Run 的执行状态。
- 单个 service 内部的 agent 编排细节。

## 5. S2C tenant 迁移结果

Stage 2 已完成 `S2cCallbackEnvelope` 的 tenant 迁移（commit `d894f494`）：`tenantId` 成为 envelope 的第七个必填字段（Rule R-C.c）。registry 绑定保留为 COMPATIBILITY ONLY 路径，不替代 in-band tenant scope。

迁移原因（目标态，现已实现）：

- S2C 是跨边界 envelope，应当自包含 tenant scope。
- 真 bus 和 federation 场景不能依赖单个 service runtime 的隐式 registry。
- 审计、重放、DLQ 和跨服务排查需要从 envelope 本身看到 tenant。

剩余影响：`agent-runtime` 侧的构造点和 runtime-side schema validation integration 仍待后续波次（不改变 Task lifecycle 所有权，见 `development.md` §6）。Agent 注册发现的 tenant 隔离语义在 [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md) 中规定：registry key 必须包含 `tenantId`，跨 tenant fallback 显式失败。

## 6. 逻辑风险

| 风险 | 说明 | 处理方式 |
|---|---|---|
| Gateway 被误认为 service API owner | Gateway 只负责入口治理和转发，不创建或持久化 Task。 | 文档和测试中重复 service-owned Task invariant。 |
| 真 bus 被误认为运行时 broker 已存在 | 当前只有 SPI 和契约事实，物理 bus 未实现。 | 在物理视图和 feature catalog 标注成熟度。 |
| Agent 注册发现被误认为 agent 定义所有权 | 真 bus 需要发现能力，但不应接管 agent 定义、实现和 Task 状态。 | 把注册发现限定为运行时路由索引。 |
| S2C tenant 事实不一致 | 契约层 `tenantId` 已迁移（Stage 2）；runtime 构造点待后续波次。 | Stage 2 契约层迁移完成（commit `d894f494`）；runtime-side construction binding 随后续波次。 |

## 7. Agent 注册发现契约

Agent 注册与发现的完整设计态契约见 [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md)。核心裁决（HD3-001..007）：

- `agent-bus` 只拥有 runtime route index，不拥有 agent 定义或 Task 状态。
- registry key = `(tenantId, agentId|serviceId, capability)`，`tenantId` 是强制维度，禁止跨 tenant fallback。
- discovery 返回 opaque route handle，调用方不直接操作物理 endpoint。
- discovery result 不携带 Task execution state。
- 每个 entry 携带 `contractVersion` 与 `capabilityVersion`；version mismatch 有显式 result 状态。
- lease/TTL + optional health metadata；unhealthy target 仍可见但显式标注。
- Stage 3 只定义接口和 harness 断言，不实现 runtime registry（durable/memory/外部 discovery 系统的选择 deferred）。

## 8. 类 MQ 转发契约（Stage 4 设计态）

类 MQ 转发底座的完整设计态契约见 [`ICD-Agent-Bus-Forwarding`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md)。核心边界（HD4）：

- 转发语义 broker-agnostic：不绑定具体 broker / MQ 产品，产品选择 deferred 到 Stage 5。
- forwarding envelope 通过 `routeHandle` 消费 Stage 3 的 discovery result，不直接暴露或绕过物理 endpoint。
- forwarding envelope 有载荷时只携带 `payloadRef`（条件必填，MI5-003 方案 B；纯控制消息可省略）、不携带 payload body；大载荷走 data reference path，不进 event / control channel。
- runtime-to-runtime 消息不改变远端 Task lifecycle owner；`agent-bus` 不写 Task execution state。
- Stage 4 只定义转发语义和 harness 断言，不实现运行态转发底座、不新增 mailbox / queue / DLQ / replay 运行态存储。

## 9. C3 转发运行态（Stage 7 → Stage 19）

C3（database outbox / inbox）按 Stage 6 裁决落地，`adopted-c3`。运行态契约见 [`ICD-Agent-Bus-Forwarding-Runtime`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)，L2 技术设计见 [`forwarding-outbox-inbox.md`](../../L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md)。逻辑落点：

- 发送方持久 outbox：唯一键 `(tenantId, messageId)`，幂等 enqueue，状态机驱动 `PENDING → DISPATCHING → {ACKED | RETRY_SCHEDULED → DISPATCHING | DLQ | EXPIRED}`。
- 接收方持久 inbox：去重键 `(tenantId, messageId, consumerServiceId)`，重复到达返回 `DUPLICATE_SUPPRESSED` 不变更，`RECEIVED → {CONSUMED | REJECTED}`。
- envelope 强制 tenant 隔离（`tenantId == routeHandle.tenantScope`，违例 `tenant_mismatch`）与 `payloadRef` 条件必填（MI5-003 方案 B：DATA_BEARING 必填、CONTROL_ONLY 可选）。
- outbox / inbox / dispatcher 以端口接口表达，broker-agnostic；真实持久化 Stage 12 落地（Postgres JDBC adapter + Flyway + RLS，打破路径 B）。
- 真实投递绑定 Stage 15 落地 PoC：`A2aForwardingDeliveryPort`（`transport.a2a` 子包）消费 agent-runtime `/a2a`，把 A2A SSE 终态映射为 `ForwardingDeliveryResult`（同步等完成 = T1 push，Stage 13 候选评审）；`ForwardingEndpointResolver` 注入投递端口，解开 routeHandle opaque（不破 HD4）。
- 重投策略（Stage 14）：`ForwardingRetryPolicy` overflow-safe 指数退避 + exhausted→DLQ；断路器（Stage 16）：`ForwardingCircuitBreaker` 接入 worker，故障 route 经 `RouteCircuitBreaker` 三态机短路（CLOSED→OPEN→HALF_OPEN），正当性来自 Stage 15 选 T1 push。
- 失败码 8 个，含 Stage 18 `remote_task_failed`（远程 agent 终态业务失败 FAILED/CANCELED/REJECTED，non-retryable → 直达 DLQ，不消耗 retry 预算，正交于 retry policy）。
- Stage 17 happy-path / Stage 18 失败路径端到端验证（真实 `LocalA2aRuntimeHost`）+ Stage 19 重投往返生命周期端到端验证（真实 `JdbcForwardingOutbox`，claimDue 的 RETRY_SCHEDULED 回收子句跨多 tick 触发 → exhausted/DLQ 或恢复/ACKED），共 186 tests green；agent-bus 加 `agent-runtime` test-scope 依赖、生产仍零依赖。
- 非生产 in-memory 测试替身仍保留为 fast test double。
- 不改变远端 Task lifecycle owner；`agent-bus` 不写 Task execution state。真实 broker / queue / replay store + push-pull-MQ 最终投递模型裁决仍 deferred（H2/H3）。
