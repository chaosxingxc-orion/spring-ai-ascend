---
level: L1
module: agent-bus
status: active
source_review_packet: docs/architecture/l0/10-governance/review-packets/agent-bus-architecture-review-draft.md
covers_views: [logical, process, development, physical, scenarios]
---

# agent-bus L1 架构文档索引

## 当前状态

本目录是 `agent-bus` 的正式 L1 架构文档入口。它根据 H2 决策创建，当前状态为草案。本文档集以 H2 评审包为事实输入，并把当前分支代码、契约、模块元数据和 L0 边界作为校验来源。

当前评审包：

- `docs/architecture/l0/10-governance/review-packets/agent-bus-architecture-review-draft.md`

## 命名说明（L0 逻辑模块与当前实现）

本文档集引用模块时区分两类名称（L0 commit `544391d8` 收敛后的逻辑边界）：

| L0 逻辑模块 | 当前实现 / 兼容落点 |
|---|---|
| `agent-runtime` | `agent-runtime/` 目录、Maven artifact `agent-runtime`（原 `agent-service` 已重命名并入） |
| `agent-core` | `agent-core/` 目录、Maven artifact `agent-core` |

- 架构语义（生命周期 owner、参与者、状态归属、跨模块关系）优先使用 L0 逻辑名 `agent-runtime` / `agent-core`。
- `agent-runtime` 已落地为同名模块（原 `agent-service` 已重命名为 `agent-runtime`）；`agent-core` 已落地为 `agent-core/`。

## 视图文件

| 文件 | 内容 | 状态 |
|---|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 模块总览、边界、当前事实、风险 | 草案 |
| [`logical.md`](logical.md) | 逻辑视图：Gateway、真 bus、SPI 表面、状态所有权 | 草案 |
| [`process.md`](process.md) | 进程视图：ingress、S2C、engine port、federation/reflection 流程 | 草案 |
| [`physical.md`](physical.md) | 物理视图：部署平面、网络边界、tenant 边界 | 草案 |
| [`development.md`](development.md) | 开发视图：代码结构、依赖规则、生成物边界 | 草案 |
| [`scenarios.md`](scenarios.md) | 场景视图：关键业务/系统场景 | 草案 |
| [`spi-appendix.md`](spi-appendix.md) | SPI 附录：当前已接受的 SPI 清单和迁移状态 | 草案 |
| [`features/README.md`](features/README.md) | L1 feature catalog：能力清单和成熟度 | 草案 |

## 已接受的边界

- `agent-runtime` 保持 Task 生命周期、Task 状态、Task 层级关系、suspend/resume 的所有权。
- `agent-bus` 不直接拥有或写入 Task execution state。
- `agent-bus` 逻辑上拆分为两大块：
  - Gateway：负责外部到内部的转发、入口治理和调度。
  - 真 bus：负责 service 与 service 之间的相互调用、跨服务路由和跨服务治理。
- W2 workflow primitives 继续保持设计态，直到具体版本意图定义 mailbox、admission、backpressure、tick 语义。
- S2C envelope 已增加 `tenantId`（Stage 2，Rule R-C.c）；runtime-side construction binding / schema validation integration 仍待后续波次补齐。
- main 分支中的历史 L1 文档只作为结构参考，不作为当前分支事实源。

## 第二阶段后续同步事项

- 契约层 S2C `tenantId` 迁移已完成（Stage 2，已通知冲突方）；剩余事项为 runtime-side construction binding、schema validation integration 与 downstream 文档/模板同步，均在后续波次推进，不进入当前实现。

## Agent 注册发现契约（Stage 3 设计态）

Agent / Service / Capability 注册与发现的完整设计态契约见 [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md)。Stage 3 边界（HD3-001..007）：

- `agent-bus` 只拥有 runtime route index，不拥有 agent 定义或 Task 状态。
- registry key 强制包含 `tenantId`，禁止跨 tenant fallback。
- discovery 返回 opaque route handle，不携带 Task execution state。
- Stage 3 只定义接口和 harness 断言，不实现 runtime registry（持久化选择 deferred）。

## 类 MQ 转发契约（Stage 4 设计态）

类 MQ 转发底座的完整设计态契约见 [`ICD-Agent-Bus-Forwarding`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md)。Stage 4 边界（HD4）：

- 转发语义 broker-agnostic：不绑定具体 broker / MQ 产品，产品选择 deferred 到 Stage 5。
- forwarding envelope 通过 `routeHandle` 消费 Stage 3 discovery result，不直接暴露或绕过物理 endpoint。
- forwarding envelope 有载荷时只携带 `payloadRef`（条件必填，MI5-003 方案 B；纯控制消息可省略）、不携带 payload body / token stream / Task execution state；大载荷走 data reference path。
- runtime-to-runtime 消息不改变远端 Task lifecycle owner；`agent-bus` 不写 Task execution state。
- Stage 4 只定义转发语义和 harness 断言，不实现运行态转发底座、不新增 mailbox / queue / DLQ / replay 运行态存储。

## C3 转发运行态（Stage 7 最小骨架 → Stage 8 持久化准备 → Stage 9 lease-safe → Stage 10 dispatch-loop runtime → Stage 11 runtime-completion → Stage 12 real persistence → Stage 13 transport 候选评审 → Stage 14 deliver 重投策略先行 → Stage 15 真实投递绑定 PoC → Stage 16 断路器接入 worker）

Stage 7 按 Stage 6 裁决采用 **C3（database outbox / inbox）** 作为类 MQ 转发的生产候选路径，交付 C3 的最小可测运行态骨架（非完整持久化实现）。运行态契约见 [`ICD-Agent-Bus-Forwarding-Runtime`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)，L2 技术设计见 [`forwarding-outbox-inbox.md`](../../L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md)。Stage 7 边界：

- 落地纯 Java 领域模型与端口：`ForwardingEnvelope` / `ForwardingRouteHandle` / `ForwardingMessageId` / `ForwardingStatus` / `ForwardingFailureCode` / `ForwardingReceipt`，以及 `ForwardingOutboxPort` / `ForwardingInboxPort` / `ForwardingDispatcher` 三个端口和纯状态机 `ForwardingStateMachine`。
- envelope 强制 tenant 隔离（`tenantId` 必须等于 `routeHandle.tenantScope`，违例报 `tenant_mismatch`）与 MI5-003 方案 B 的 `payloadRef` 条件必填（DATA_BEARING 必填、CONTROL_ONLY 可选）；envelope / receipt 不携带 payload body / token stream / Task execution state / physical endpoint。
- 生产代码（`com.huawei.ascend.bus.forwarding{,.spi,.runtime}`）保持纯 Java：不依赖 Spring / JDBC / broker client / HTTP / 序列化框架（由 `AgentBusForwardingSpiPurityTest` ArchUnit 强制）。
- 持久化 / 交付绑定的真实实现（JDBC 驱动、migration、polling、lease、broker transport）deferred 到 Stage 8；Stage 7 仅提供非生产 in-memory 测试替身验证端口语义。
- 不改变远端 Task lifecycle owner；`agent-bus` 不写 Task execution state。

### Stage 8：C3 持久化准备（record 模型 / claim / lease / worker / schema 草案）

Stage 8 在 Stage 7 最小骨架上完成 C3 最终确认（`adopted-c3`）并推进为可落真实持久化的运行态底座（[`forwarding-persistence.md`](../../L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。组件边界拆清：

- **ForwardingDispatcher**（accept / enqueue 网关角色）：接受 envelope，写 outbox，返回同步 ack；不驱动投递（MI8-003）。
- **ForwardingOutboxPort**（写入 + 状态迁移 + 状态查询）：enqueue / mark* / statusOf；`sourceServiceId` / `targetServiceId` 写入 record（MI8-002）。
- **ForwardingOutboxClaimPort**（claim / lease，Stage 8 MI8-001）：`claimDue` 原子声明到期记录并 stamped lease，取代裸 `findRetryable(now)`。
- **ForwardingDispatcherWorker**（claim / deliver / ack / retry 半边）：claimDue → deliver → ACK / RETRY / DLQ / EXPIRED；与网关角色分离（MI8-003）。
- **ForwardingDeliveryPort**（抽象投递）：worker 仅消费 `routeHandle`，**不**暴露物理 endpoint；真实 HTTP / gRPC / broker 绑定 deferred Stage 9+。

Stage 8 边界：补齐 record 模型（`ForwardingOutboxRecord` / `ForwardingInboxRecord` / `ForwardingLease`）、claim / lease 端口、worker skeleton、schema / migration 草案（DDL 草稿**未执行**）、in-memory lease harness；收口 MI8-001..005。真实 receiver transport **仍未落地**；**不引入** JDBC driver / Flyway / 生产数据库依赖（§6 护栏：数据库产品 / migration 归属未确认前停在此处）。

### Stage 9：C3 lease-safe / persistence-ready（lease-owner guarded mutation / record 不变量 / failure-code 分类）

Stage 9 在 Stage 8 持久化准备上补齐**并发安全**与**约束完整性**（[`forwarding-persistence.md §11`](../../L2-Low-Level-Design/agent-bus/forwarding-persistence.md)），收口 MI9-001..006：

- **MI9-001 lease-owner guarded mutation**：`markAcked` / `scheduleRetry` / `moveToDlq` / `markExpired` 全部带 `leaseOwner`；`markDispatching` 移除（DISPATCHING 只经 `claimDue` 进入）；stale / foreign / expired owner 改状态抛 `ForwardingLeaseException`（RECORD_NOT_FOUND / NO_LEASE / OWNER_MISMATCH / NOT_DISPATCHING）。
- **MI9-002 lease 生命周期闭环**：ACKED / DLQ / EXPIRED / RETRY_SCHEDULED 清 lease；仅 DISPATCHING 持 lease。
- **MI9-003 record 条件不变量**：`ForwardingOutboxRecord` / `ForwardingInboxRecord` compact constructor 固化 per-status 字段规则，并进入 DDL CHECK 与 harness。
- **MI9-004 failure-code classification**：`ForwardingFailureCode.Classification`（retryable / non-retryable / dedup）；`retry(...)` 只接 retryable，`dlq(...)` 拒 dedup。
- **MI9-006 DDL 条件 CHECK + claim / state-update SQL contract**：DDL 草案补完整 CHECK（attempt_count、lease 成对、retry→next_attempt、terminal / failureCode、lease / status），交付 claim（`SKIP LOCKED`）+ lease-owner guarded state-update SQL contract（harness 锁定）。

DB / migration 归属未由人类确认 → **路径 B**：不引入 JDBC / Flyway；DDL / SQL 仍为 contract / draft，in-memory lease-guard harness 作为行为替身。真实 JDBC adapter / Flyway migration / 真实投递绑定仍 deferred 后续阶段。

## 阶段记录

- Stage 1 harness 计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-harness.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-harness.md)。
- Stage 1 评审与 Stage 2 计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-review-and-stage2-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-review-and-stage2-plan.md)。
- Stage 2 评审与 Stage 3 计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage2-review-and-stage3-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage2-review-and-stage3-plan.md)。
- Stage 1 follow-up 评审与 Stage 3 执行计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-followups-review-and-stage3-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-followups-review-and-stage3-plan.md)。
- Stage 3 评审与后续收口计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage3-review-and-followup-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage3-review-and-followup-plan.md)。
- Stage 3 收口评审与 Stage 4 计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage3-close-review-and-stage4-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage3-close-review-and-stage4-plan.md)。
- Stage 4 评审与 Stage 5 计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage4-review-and-stage5-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage4-review-and-stage5-plan.md)。
- Stage 5 评审与 Stage 6 计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage5-review-and-stage6-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage5-review-and-stage6-plan.md)。
- Stage 6 评审与 Stage 7 大批次计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage6-review-and-stage7-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage6-review-and-stage7-plan.md)。
- Stage 7 评审与 Stage 8 计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage7-review-and-stage8-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage7-review-and-stage8-plan.md)。
- Stage 8 评审与 Stage 9 计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage8-review-and-stage9-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage8-review-and-stage9-plan.md)。
- Stage 9 评审与 Stage 10 计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage9-review-and-stage10-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage9-review-and-stage10-plan.md)。
- Stage 10 评审与 Stage 11 计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage10-review-and-stage11-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage10-review-and-stage11-plan.md)。
- Stage 11 评审与 Stage 12 计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage11-review-and-stage12-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage11-review-and-stage12-plan.md)。
- Stage 12 评审与 Stage 13 计划：[`../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage12-review-and-stage13-plan.md`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage12-review-and-stage13-plan.md)。

## 后续工作

- 补齐 S2C tenant 迁移后的 runtime-side construction binding / schema validation / downstream 文档同步。
- 为 ingress、federation、reflection 增加契约测试计划。
- 为本目录生成 graphify 输入和漂移检查 manifest。
- Stage 5 运行态候选方案评审：见 [`../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md)（候选评审，不绑定产品；Stage 4 设计态契约见上方「类 MQ 转发契约」章节）。
- Stage 6 运行态候选裁决：见 [`../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)（已采用 C3，`adopted-c3`；Stage 8 最终确认，后续变更需 ADR / review packet）。
- Stage 7 C3 转发运行态最小骨架已落地（领域模型 + 端口 + 状态机 + in-memory 测试替身 + harness）；115 tests green。
- Stage 8 C3 持久化准备已落地：record 模型 + claim / lease 端口 + dispatcher worker skeleton + 抽象 delivery 端口 + schema / migration 草案（DDL 草稿未执行）+ in-memory lease harness；收口 MI8-001..005；122 tests green。真实 JDBC adapter / Flyway migration / 真实投递绑定 deferred Stage 9+（§6 护栏：数据库产品 / migration 归属未确认前不引入生产数据库依赖）。计划见上方「阶段记录」。
- Stage 9 C3 lease-safe / persistence-ready 已落地（MI9-001..006）：lease-owner guarded mutation、lease 生命周期闭环、record 条件不变量（Java + DDL CHECK + harness）、failure-code classification、claim / state-update SQL contract；DB / migration 归属未确认 → 路径 B（不引入 JDBC / Flyway）；129 tests green。计划见上方「阶段记录」。
- Stage 10 dispatch-loop runtime 已落地（MI10-001..005）：worker lease 异常恢复（catch `ForwardingLeaseException` + skip）、lease 续约契约（`DispatchLeasePolicy`）、`DispatchTickResult` 可观测（skipped + 计数自洽）、dispatch 调度责任定义（`ForwardingDispatchLoop` 骨架，`TickSource` / `IdleStrategy` 注入，无 scheduler / 线程）；DB / migration 归属经人类再确认为路径 B（不引入 JDBC / Flyway）；134 tests green。计划见 [`stage9-review-and-stage10-plan`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage9-review-and-stage10-plan.md)。
- Stage 11 runtime-completion 已落地（MI11-001/002/003）：lease 续约触发时机改读注入 `EpochClock`（`ForwardingDispatcherWorker` 注入 `EpochClock`，使自然 dispatch loop 下耗时 deliver 接近 lease TTL 时续约能真正触发）、`deliver` 非 lease `RuntimeException` 兜底为 `skipped`（record 留 DISPATCHING，lease 过期重投，不丢消息）、`runOnce` 异常契约（仅入参非法 fail-fast，`ForwardingDispatchLoop` 传播，无循环级吞没）；DB / migration / transport 归属未变（路径 B，不引入 JDBC / Flyway / 真实投递绑定）；136 tests green。计划见 [`stage10-review-and-stage11-plan`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage10-review-and-stage11-plan.md)。
- Stage 12 real persistence 已落地（**打破路径 B**）：H2/H3 裁决 4 项选型（Postgres / agent-bus 自有 Spring JDBC / transport 拆出 / 启用 RLS）；落地 Postgres JDBC adapter（`JdbcForwardingOutbox` / `JdbcForwardingInbox` / `ForwardingSqlCodec`，claim `FOR UPDATE SKIP LOCKED RETURNING` / lease-guarded / reclaim / renew / release 过期语义）+ Flyway migration `V1__create_agent_bus_forwarding_outbox_inbox.sql`（MI9-006 CHECK + 索引 + RLS）+ real-SQL 验证（embedded-postgres PG 16.2，§7.4，17 tests green）；`§6.1`「不引入 JDBC」解除，ArchUnit 把 Spring/JDBC 圈进 `persistence.jdbc` 子包；`§6.2` 不变；153 tests green。transport / 真实投递绑定仍 deferred（独立议题）。计划见 [`stage11-review-and-stage12-plan`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage11-review-and-stage12-plan.md)。
- Stage 13 transport 投递模型候选评审已完成（裁决阶段、无生产代码）：比较 T1 dispatcher-push over sync RPC / T2 dispatcher-push over broker / T3 consumer-pull over DB / T4 C3+broker hybrid 四候选 × 8 维度；核心结论「反压诉求内核 = 消费方控速 / 速率解耦，MQ 只是满足该内核的载体之一」，T3 consumer-pull over DB 作为非裁决推荐（唯一不破 §6.2 的强反压候选，复用 Stage 12 claim / lease / `SKIP LOCKED` / RLS）；deliver 异常重投策略（退避 / attemptCount 上限 / 熔断）拆为可独立先行子项；**最终 push / pull / MQ 裁决待 H2/H3**（选 T2 / T4 需解除 §6.2 引 MQ）。评审见 [`agent-bus-forwarding-runtime-transport-candidates`](../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md)；计划见 [`stage12-review-and-stage13-plan`](../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage12-review-and-stage13-plan.md)。
- Stage 14 deliver 重投策略先行已落地（独立于 Stage 13 未裁决的 transport 模型，§6.2 不变）：`ForwardingRetryPolicy` 端口（`ExponentialBackoff` 默认实现，overflow-safe 指数退避 `min(cap, base<<shift)` + 注入 `LongSupplier` jitter + `exhausted(attemptCount)`→DLQ）+ worker RETRY 分支接入（与 `DispatchLeasePolicy` / `EpochClock` 同层注入）；`ForwardingDeliveryResult` 移除 `nextAttemptAtMillisEpoch`、`retry(code)` 简化为单参（重投时机归 policy，交付与重试治理分离关注点；outbox `nextAttemptAt` 仍是 persisted 字段）；`ForwardingCircuitBreaker` 端口 + `ALWAYS_CLOSED` no-op（形态依赖 transport 裁决，Stage 15 选 T1 push 后阻塞解除，**Stage 16 已接入 worker**，见 §17）；158 tests green。设计见 [`forwarding-persistence §15`](../../L2-Low-Level-Design/agent-bus/forwarding-persistence.md)。
- Stage 15 真实投递绑定 PoC 已落地（**打破 deferred**：`§6.1` 第 4 项「真实投递绑定 deferred」由 Stage 15 解除，§6.2 不变）：A2A HTTP transport adapter `A2aForwardingDeliveryPort implements ForwardingDeliveryPort`（per-endpoint `JSONRPCTransport` 缓存 + `sendMessageStreaming` 阻塞 + 终态映射：COMPLETED / INPUT_REQUIRED→`acked`、`isFinal && !COMPLETED` / `AUTH_REQUIRED`→`retry(RECEIVER_UNAVAILABLE)`、超时→`retry(DELIVERY_TIMEOUT)`、连接级错误→`retry(RECEIVER_UNAVAILABLE)`、resolver 空→`dlq(ROUTE_NOT_FOUND)`），消费 agent-runtime 的 A2A JSON-RPC `/a2a` 端点；`ForwardingEndpointResolver` 注入端口解开 routeHandle opaque（HD4 不破，默认 `MapEndpointResolver`，生产由 Stage 3 registry 实现）；同步等完成语义（= Stage 13 的 T1 dispatcher-push over sync RPC，镜像 main 的 `A2aRemoteAgentOutboundAdapter`），不裁决 T1 vs C3 异步的哲学张力（仍 H2/H3）。**线上格式对称性**：harness 用 A2A SDK 自身序列化器产出的 SSE 帧与 agent-runtime `A2aJsonRpcController` 逐字节一致（同 Stage 12 embedded-postgres「用测试载体验证真实协议代码」哲学）。**SDK 行为发现**：HTTP 4xx / 5xx 不被当错误（SDK 视非 2xx 为静默空 SSE 流 → `DELIVERY_TIMEOUT`），真正 socket 断开才触发 `errorConsumer`。tenant（R-C.c）走 `ClientCallContext` 的 `X-Tenant-Id` header；`payloadRef` 走 `MessageSendParams.metadata` 扩展位。**164 tests green**（158 + 5 MockWebServer 场景），ArchUnit 把 `org.a2aproject` 圈进 `transport.a2a` 子包。设计见 [`forwarding-persistence §16`](../../L2-Low-Level-Design/agent-bus/forwarding-persistence.md)。
- Stage 16 断路器接入 worker 已落地（**生产化补全**：把 Stage 14 预留的 deferred 端口 `ForwardingCircuitBreaker` 正式接入 `ForwardingDispatcherWorker`；正当性来自 Stage 15 PoC 选 T1 push —— push 模型 dispatcher 主动驱动投递，需 breaker 在故障 route 主动短路）。真实实现 `RouteCircuitBreaker` 三态机（CLOSED→OPEN→HALF_OPEN，纯 JDK，注入 `EpochClock`，per-route `ConcurrentHashMap` + `probeInFlight` 单探测）；端口加 `recordOutcome` 反馈驱动状态机，`ALWAYS_CLOSED` 由 lambda 改两方法匿名类。worker 7 参构造器 + `runOnce` 三处接入（顺序保证 `probeInFlight` 不泄漏：`allowsDelivery` 在 lease 续约后 deliver 前 / `recordOutcome` 在 deliver 后 switch 前 / catch 块补 `retry(RECEIVER_UNAVAILABLE)`）。触发分类在 breaker 内（ACKED→成功 / RETRY_SCHEDULED→失败 / DLQ·EXPIRED→忽略）；`DispatchTickResult` 不变（短路复用 skip 路径，无新计数器）。**179 tests green**（164 + 11 `RouteCircuitBreakerTest` + 4 worker 行为测试），ArchUnit 纯度 green（纯 JDK 无需新豁免），§6.2 不变（breaker 无 Task state / payload body / concrete broker client）。deferred：per-route 状态非持久化（进程重启回 CLOSED）、参数生产调优 / per-route 配置、多 worker 共享单例 breaker、push / pull / MQ 最终裁决（H2/H3）。设计见 [`forwarding-persistence §17`](../../L2-Low-Level-Design/agent-bus/forwarding-persistence.md)。
