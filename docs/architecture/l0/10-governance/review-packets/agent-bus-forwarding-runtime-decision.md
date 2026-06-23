---
artifact_type: a2d_review_packet
version: "agent-bus-forwarding-runtime-decision"
status: "adopted-c3"
source_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage6-review-and-stage7-plan.md"
source_stage6_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage5-review-and-stage6-plan.md"
source_candidates: "docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md"
source_icd: "docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md"
source_l1: "architecture/docs/L1/agent-bus/README.md"
target_module: agent-bus
---

# agent-bus 运行态转发候选方案裁决（Stage 6 H2/H3 → Stage 7 落位 → Stage 8 最终确认 → Stage 9 lease-safe）

## 0. 文档状态与裁决前提

**当前状态：已采用 C3（database outbox / inbox）。H2/H3 在 Stage 7 → Stage 8 推进期间未提出反对，C3 进入最终确认状态（`adopted-c3`）；后续对 C3 的变更需新的 ADR / review packet。**

本文档是 Stage 6（运行态候选裁决）的裁决记录，并被 Stage 7 计划（[`stage6-review-and-stage7-plan`](../delivery-projections/agent-bus-stage6-review-and-stage7-plan.md) §0）正式推进落位：Stage 6 的 draft 作为裁决入口已被接受为收口输入；Stage 7 不再生成「等待裁决」文档，而是按 Stage 5 推荐方向默认采用 **C3 database outbox / inbox** 作为生产候选路径，并解锁最小运行态实现。Stage 8 计划（[`stage7-review-and-stage8-plan`](../delivery-projections/agent-bus-stage7-review-and-stage8-plan.md) §0/§3）在此基础上完成 C3 最终确认、补齐 record 模型与 claim / lease 端口、提供 schema / migration 草案与 dispatcher worker skeleton。

裁决权威方为 **H2/H3**（架构治理 / 人类决策）。Stage 7 计划 §0 的默认裁决（「若人类没有反对，默认按 Stage 5 推荐采用 C3」）在 Stage 7 → Stage 8 推进期间未被 H2/H3 否决，C3 进入最终确认状态（`adopted-c3`）。**后续对 C3 的变更（撤回、改候选、扩大 / 收紧代码许可范围）不再以「待确认」悬置，而需新的 ADR / review packet**；不再以「H2/H3 反对则撤回代码」作为默认回退路径。Stage 7 / Stage 8 生产代码受 §4 许可范围与 §6 禁止范围双向约束。

回应「Stage 6 是否应该写代码」（Stage 6 计划 MI6-003）：Stage 6 本身是裁决关卡、不写代码（已收口）；Stage 7 在 C3 默认裁决下开始铺最小运行态实现路径，代码最早在此出现。

## 1. 裁决项

| 裁决项 | 裁决 | 说明 |
|---|---|---|
| 采用候选 | **C3 database outbox / inbox（已采用，`adopted-c3`）** | Stage 7 计划 §0 默认裁决，Stage 8 最终确认；durable、审计强、tenant 行级隔离清楚、可能复用现有 DB、运维轻（见 [`candidates §6.2`](agent-bus-forwarding-runtime-candidates.md)）。 |
| 是否允许写生产代码 | **是（仅 C3 最小范围）** | 允许最小领域模型、端口接口、状态机、schema 草案、harness；不允许完整调度器接入真实服务调用链（见 §4）。 |
| 最小实现范围 | C3 最小骨架（Stage 7 切片 2-5） | L2 技术设计、outbox/inbox schema 草案、`com.huawei.ascend.bus.forwarding{,.spi,.runtime}` 骨架、状态机与契约 harness、L1 同步。 |
| 验收标准 | Stage 7 计划 §4 | C3 决策不再阻塞；L2 可投影 schema/接口/测试；最小骨架有单测/harness 覆盖关键规则；不引入 broker/MQ 依赖；不改 Task ownership；不绕 routeHandle；不放 payload body。 |

**本裁决不再是「待裁决导致不得推进」的阻塞状态**：C3 已最终确认（`adopted-c3`），解锁 Stage 7 最小骨架与 Stage 8 持久化准备。

## 2. 采用候选

**采用 C3 — database outbox / inbox（已最终确认的生产候选路径）。**

理由（与 Stage 5 [`candidates §6.2`](agent-bus-forwarding-runtime-candidates.md) 推荐一致）：

- **durable**：outbox/inbox 表提供跨进程可靠投递与重启不丢，满足生产转发底座最低要求。
- **审计强**：行级 record（tenantId + messageId + status + attemptCount + lastFailureCode）天然可审计、可 replay。
- **tenant 隔离清楚**：registry key 强制 `tenantId`（Stage 3）的隔离在 outbox/inbox 上延续为行级 `tenantId` + 唯一约束，跨 tenant fallback 在查询/投递层显式失败。
- **可能复用现有 DB**：不强制引入新基础设施（broker 运维），与当前阶段「最小切片、最小新增生产依赖」一致。
- **broker-agnostic**：outbox/inbox 是承载语义，不绑定具体 broker/MQ 产品；未来若需要 external broker（C4），可在 outbox dispatcher 与 broker adapter 之间加一层，不推翻 C3 骨架。

C1 in-memory dispatcher 仅保留为**本地非 durable 替身 / 测试替身**，不作生产底座。

## 3. 不采用候选及原因

| 候选 | 不采用原因（命中 rejection criteria） |
|---|---|
| C1 in-memory dispatcher | 不满足跨进程可靠投递、重启不丢消息、生产审计；仅作为本地非持久化实验 / 测试替身保留，不作生产底座。 |
| C2 runtime-local queue | 不满足跨实例一致路由与 durable replay；runtime-local 状态在实例失效后丢失，不满足转发底座最低 durable 要求。 |
| C4 external broker（Kafka / NATS / RocketMQ / RabbitMQ） | 引入 broker 运维与产品绑定，当前阶段过重；C3 outbox/inbox 已满足最小切片收益，broker adapter 可作为 Stage 8+ 的可选上层，不进 Stage 7 最小骨架。 |
| C5 hybrid | 复杂度超过最小切片收益；当前阶段不需要在 outbox 与 broker 之间做混合编排。 |

## 4. 是否允许写生产代码

**裁决：是 —— 仅 C3 最小范围。**

Stage 7 允许写生产代码的范围（正向许可）：

- `agent-bus` 内的 forwarding runtime 领域模型、端口接口、状态机。
- outbox / inbox schema 草案（machine-readable yaml）与 L2 技术设计。
- in-memory test double（**仅**测试替身 / 本地非持久化实验，放在 test source set 或明确标注 non-production）。
- 状态机与契约 harness / 单元测试。

Stage 7 **不允许**写生产代码的范围（反向禁止）：

- **不引入**具体 broker / MQ client（Kafka / RabbitMQ / RocketMQ / NATS）。
- **不引入** JDBC driver / 具体 ORM / 真实数据库实现（C3 持久化实现是 Stage 8；Stage 7 若发现 C3 需真实 DB 才能表达，停在端口接口与状态机，见 Stage 7 计划 §5）。
- **不接入**完整调度器到真实服务调用链。
- **不写** Task execution state。
- **不绕过** Stage 3 discovery 的 `routeHandle`。
- **不放** payload body / token stream 进 forwarding envelope（`payloadRef` 条件必填，MI5-003 方案 B）。

Stage 8 允许写生产代码的范围（正向许可，[`Stage 8 计划 §3`](../delivery-projections/agent-bus-stage7-review-and-stage8-plan.md)）：

- outbox / inbox **record 模型**（`ForwardingOutboxRecord` / `ForwardingInboxRecord` / `ForwardingLease`），承载 runtime ICD 必填字段（MI8-002）。
- **claim / lease 端口**（`ForwardingOutboxClaimPort`）与并发抢占语义，取代裸 `findRetryable(now)`（MI8-001）。
- **dispatcher worker skeleton**（`ForwardingDispatcherWorker`）与抽象 delivery 端口（`ForwardingDeliveryPort` / `ForwardingDeliveryResult`），分离 accept / enqueue 与 claim / deliver（MI8-003）。
- outbox / inbox **schema / migration 草案**（DDL 草稿，**未执行**）与持久化 L2（[`forwarding-persistence`](../../../../architecture/docs/L2/agent-bus/forwarding-persistence.md)）。
- in-memory lease harness + fake delivery（test source set，non-production）。

Stage 12 允许写生产代码的范围（正向许可，[`Stage 12 计划 §4`](../delivery-projections/agent-bus-stage11-review-and-stage12-plan.md)）—— **正式启动真实持久化，打破路径 B**（`§6.1`「不引入 JDBC driver」约束由 Stage 12 H2/H3 裁决**解除**）：

- **Postgres JDBC adapter（Spring JDBC）**：`ForwardingOutboxPort` / `ForwardingOutboxClaimPort`（+ inbox）的真实实现，`spring-boot-starter-jdbc` + `JdbcTemplate` + 注入 `DataSource`；claim 走 §7.1 `FOR UPDATE SKIP LOCKED RETURNING`，状态变更走 §7.2 lease-owner guarded `WHERE`（0 行 → `ForwardingLeaseException`）。**adapter + migration 归属 = agent-bus 自有**（agent-bus 从纯 Java 模块变为 Spring 模块）。
- **Flyway migration**：`agent-bus/src/main/resources/db/migration/V<n>__create_agent_bus_forwarding_outbox_inbox.sql`，含 MI9-006 全部条件 CHECK + `ix_outbox_claim_due` 索引 + RLS。
- **Postgres RLS 纵深防御**：应用层 `WHERE tenant_id=?` 主路径 + DB 层 RLS 兜底（R-C.c 硬隔离）。
- **Testcontainers（test）**：真实 SQL 行为验证（并发 claim / lease guard / reclaim / renew-or-lose-ack / CHECK 兜底 / tenant 隔离）。
- in-memory 替身保留为 fast test double。

Stage 12 **不允许**写生产代码的范围（反向禁止，不变）：

- **不引入** concrete broker / MQ client（Kafka / RabbitMQ / RocketMQ / NATS）—— transport **拆出 Stage 12 单独议**（投递模型 push vs pull / 是否引 MQ 触及 C3/C4 根本裁决，可能需 review packet 复议；`§2` 预留 C3+broker hybrid 路径）。
- **不接** 真实 receiver transport / 投递绑定（dispatcher worker → receiver 的 HTTP/gRPC/MQ 投递 deferred，独立议题）。
- **不写** Task execution state；**不绕过** `routeHandle`；**不放** payload body（`§6.2` 始终不得）。

Stage 15 允许写生产代码的范围（正向许可，[`Stage 14 评审与 Stage 15 计划`](../delivery-projections/agent-bus-stage14-review-and-stage15-plan.md)）—— **正式启动真实投递绑定 PoC**（`§6.1` 第 4 项「真实投递绑定 deferred」由 Stage 15 解除）：

- **A2A HTTP transport adapter（A2A SDK `JSONRPCTransport`）**：`ForwardingDeliveryPort` 的真实实现 `A2aForwardingDeliveryPort`，per-endpoint transport 缓存 + `sendMessageStreaming` 阻塞 + 远程 Task 终态映射，消费 agent-runtime 的 A2A JSON-RPC `/a2a` 端点；落地于 `transport.a2a` 子包（A2A SDK 圈进该子包，与 Stage 12 Spring / JDBC 圈进 `persistence.jdbc` 同范式）。
- **`ForwardingEndpointResolver` 注入端口**（解开 routeHandle opaque，HD4 不破）：默认 `MapEndpointResolver`，生产由 Stage 3 registry 实现；resolver 空 → `dlq(ROUTE_NOT_FOUND)`。
- **`A2aForwardingProperties`**（`streamTimeoutMillis` + `tenantHeaderName`，注入式，便于测试用短超时）。
- **MockWebServer 契约验证（test）**：5 场景（COMPLETED / 超时 / 远程 FAILED / 连接错误 / resolver 空），线上格式对称性 —— harness 用 A2A SDK 自身序列化器产出的 SSE 帧与 agent-runtime 逐字节一致。

Stage 15 **不允许**写生产代码的范围（反向禁止，不变）：

- **不引入** concrete broker / MQ client（Kafka / RabbitMQ / RocketMQ / NATS）—— A2A 是 HTTP JSON-RPC，**非 broker / MQ**（`§6.2` 不变）。
- **不写** Task execution state（`transport.a2a` 只读远程 Task 事件映射为 `ForwardingDeliveryResult`，不把 `TaskStatus` 存进 outbox / inbox record）；**不绕过** `routeHandle`（经注入 resolver 解开，不自行 unwrap）；**不放** payload body / token stream（`payloadRef` 走 `MessageSendParams.metadata` 扩展位，`TextPart` 仅载 routing descriptor）。

允许范围与禁止范围同时存在，且均可被 review（§6 与 Stage 7 / Stage 8 / Stage 12 / Stage 15 计划 §3/§4）。

## 5. 最小实现范围（C3 已激活）

C3 分支已由本裁决激活。Stage 7 的最小实现切片（见 Stage 7 计划 §3）：

- **切片 2 L2 技术设计**：`architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md` —— 组件边界、状态机、幂等键、租户隔离、失败语义。
- **切片 3 契约与 schema**：`ICD-agent-bus-forwarding-runtime.md` + `machine-readable/agent-bus-forwarding-runtime.v1.yaml` —— outbox/inbox record 字段、唯一约束、禁止字段。
- **切片 4 代码骨架**：`com.huawei.ascend.bus.forwarding{,.spi,.runtime}` —— `ForwardingEnvelope` / `ForwardingMessageId` / `ForwardingStatus` / `ForwardingFailureCode` / `ForwardingReceipt` / `ForwardingDispatcher` / `ForwardingOutboxPort` / `ForwardingInboxPort` / `ForwardingStateMachine` / `ForwardingRouteHandle`。
- **切片 5 harness 与测试**：tenant mismatch / routeHandle missing / payload body rejected / payloadRef missing / duplicate suppressed / 正常路径 / 重试路径 / DLQ / 不写 Task state / SPI 无 DB 依赖。
- **切片 6 L1 同步**：README / logical / process / development / features。

真实持久化实现（JDBC / 具体 DB schema migration / polling / lease / 并发抢占）**不在 Stage 7**，作为 **Stage 8** 处理（Stage 7 计划 §6）。

## 6. 禁止范围

### 6.1 Stage 7 生产代码许可的边界（C3 裁决下适用）

> **Stage 12 更新（H2/H3 裁决，2026-06）**：本节「不得」第 2 项「引入 JDBC driver / 真实数据库实现（停在端口接口 + 状态机）」已被 Stage 12 裁决**解除** —— 真实 Postgres JDBC adapter（Spring JDBC）+ Flyway migration + RLS 已进入许可范围（见 §4 Stage 12 段、§8）。第 4 项「真实投递绑定」**仍 deferred**（transport 拆出 Stage 12 单独议；**已于 Stage 15 解除，见下**）。**§6.2 始终不得项不变。** 本节其余约束作为 Stage 7 历史边界保留。

> **Stage 15 更新（2026-06）**：本节「不得」第 4 项的「真实投递绑定 deferred」**已解除** —— A2A HTTP transport adapter（`JSONRPCTransport` client + per-endpoint 缓存 + `sendMessageStreaming` 阻塞 + 远程 Task 终态映射）+ MockWebServer 契约验证已进入许可范围，落地于 `transport.a2a` 子包（见 §4 Stage 15 段、§8）。**§6.2 始终不得项不变**（A2A 是 HTTP JSON-RPC over `application/json` + `text/event-stream`，**非 concrete broker / MQ client**，不撞 Kafka / RabbitMQ / NATS 禁令；不内联 payload body / token stream，`payloadRef` 走 A2A `MessageSendParams.metadata` 扩展位）。与 Stage 12 把 Spring / JDBC 圈进 `persistence.jdbc` 同范式，A2A SDK 圈进 `transport.a2a` 子包：ArchUnit（`forwarding_core_does_not_import_a2a_outside_transport_adapter`）+ `AgentBusForwardingRuntimeContractTest` 的 §6.2 文本扫描（`readForwardingProductionSources` walk 排除 `runtime/transport/a2a` 子树）**双豁免**。豁免合理：`transport.a2a` 是线上格式解析器（读远程 A2A Task 事件映射为 `ForwardingDeliveryResult`，**从不把 Task execution state 写进 outbox / inbox record** —— record 无 `TaskStatus` 字段），转发核心的 §6.2 精神不变。

即使已裁决 C3，Stage 7 仍**不得**：

- 引入具体 broker / MQ client。
- 引入 JDBC driver / 真实数据库实现（停在端口接口 + 状态机）。
- 让完整调度器接入真实服务调用链。
- 写生产 runtime dispatcher 的真实投递（dispatcher 接口 + 状态机允许；真实投递绑定是 Stage 8）。
- 改 Task lifecycle owner / 让 `agent-bus` 写 Task execution state。
- 绕过 `routeHandle`。

### 6.2 始终不得（即使后续 Stage 裁决也不变）

- 用某个产品能力反向修改 Stage 4 的 broker-agnostic 语义。
- 把大对象正文或 token stream 放入 forwarding envelope（`payloadRef` 条件必填，MI5-003 方案 B）。
- 把注册发现变成 agent 定义仓库。
- 让 `agent-bus` 写 Task execution state。
- 跨 tenant fallback（R-C.c）。

## 7. 需要通知的 owner

| Owner | 通知事项 |
|---|---|
| H2/H3 | 本文档已默认采用 C3（Stage 7 计划 §0 落位），待最终确认；若反对 C3，回退状态并撤回 Stage 7 生产代码。 |
| agent-bus 模块负责人 | C3 已解锁最小运行态实现；Stage 7 按 §5 切片执行；真实持久化是 Stage 8。 |
| `agent-runtime`（当前实现落点：`agent-service`）owner | 转发底座不改变 Task lifecycle owner；outbox owner 落点（若 Stage 8 接真实 DB）需协调，但 Task execution state 仍归 runtime。 |
| `agent-core`（当前实现落点：`agent-execution-engine`）owner | 转发底座不触及 engine 执行边界，无 owner 变更。 |

## 8. 后续

- ~~H2/H3 反对 C3 则回退 draft、撤回代码~~ —— 此悬置路径已随 Stage 8 最终确认关闭；C3 = `adopted-c3`。
- Stage 8（已完成）：record 模型、claim / lease 端口、dispatcher worker skeleton、抽象 delivery 端口、schema / migration 草案（DDL 草稿未执行）、in-memory lease harness（[`forwarding-persistence`](../../../../architecture/docs/L2/agent-bus/forwarding-persistence.md)）。
- Stage 9（已完成，**路径 B**）：lease-safe / persistence-ready —— lease-owner guarded mutation（`markAcked` / `scheduleRetry` / `moveToDlq` / `markExpired` 带 `leaseOwner`，`markDispatching` 移除）、lease 生命周期闭环（terminal + retry 清 lease）、record 条件不变量（Java 构造器 + DDL CHECK + harness）、failure-code classification（retryable / non-retryable / dedup）、claim / state-update SQL contract、in-memory lease-guard harness；收口 MI9-001..006（[`forwarding-persistence §11`](../../../../architecture/docs/L2/agent-bus/forwarding-persistence.md)）。**DB / migration 归属未由人类确认 → 路径 B：不引入 JDBC / Flyway；DDL / SQL 仍为 contract / draft。**
- Stage 10（已完成，**路径 B**）：dispatch-loop runtime —— worker lease 异常恢复（per-record catch `ForwardingLeaseException` + skip，`DispatchTickResult.skipped` 计数自洽可观测，MI10-001）、lease 续约契约（`DispatchLeasePolicy`：deliver 前按阈值 `renewLease`，renew 失败同 skip，MI10-002）、dispatch 调度责任（`ForwardingDispatchLoop` 骨架：`TickSource` / `IdleStrategy` 注入，无 clock / scheduler / 线程，MI10-004）；收口 MI10-001..005（[`forwarding-persistence §12`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)、[`forwarding-outbox-inbox §8/§10`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md)）。**DB / migration 归属经人类再确认为路径 B**（Stage 10 切片 4 裁决）：不引入 JDBC / Flyway；DDL / SQL 仍为 contract / draft；真实持久化（JDBC adapter / Flyway / 真实投递绑定）deferred 后续阶段，独立批次。
- Stage 11（已完成，**路径 B**）：runtime-completion —— lease 续约触发时机改读注入 `EpochClock`（`ForwardingDispatcherWorker` 注入 `EpochClock`，`remaining = leaseUntilMillisEpoch − clock.epochMillis()`；自然 dispatch loop 下耗时 deliver 接近 lease TTL 时续约能真正触发，MI11-001）、`deliver` 非 lease `RuntimeException` 兜底为 `skipped`（record 留 DISPATCHING，lease 过期重投，不丢消息；契约：真实 transport 绑定应把网络 / 超时 / 反序列化异常映射为 `ForwardingDeliveryResult`，**不应抛**非 lease 异常，MI11-002）、`runOnce` 异常契约（仅入参非法 fail-fast 抛 `IllegalArgumentException`，`ForwardingDispatchLoop` 传播，无循环级吞没，MI11-003）；收口 MI11-001/002/003（[`forwarding-persistence §13`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)、[`forwarding-outbox-inbox §8/§10`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md)）。**DB / migration / transport 归属未变（路径 B）**：不引入 JDBC / Flyway / 真实投递绑定。
- Stage 12（**已完成，打破路径 B**，2026-06）：真实持久化 —— H2/H3 裁决 4 项选型（① DB = **Postgres**；② migration·adapter 归属 = **agent-bus 自有 + Spring JDBC**（`NamedParameterJdbcTemplate` + 注入 `DataSource`）；③ transport = **拆出 Stage 12 单独议**；④ RLS = **启用纵深防御**）。落地 Postgres JDBC adapter（`JdbcForwardingOutbox` / `JdbcForwardingInbox` / `ForwardingSqlCodec`，claim §7.1 `FOR UPDATE SKIP LOCKED RETURNING` / lease-guarded §7.2 / reclaim / renew / release 过期语义）、Flyway migration `V1__create_agent_bus_forwarding_outbox_inbox.sql`（MI9-006 全部 CHECK + `ix_outbox_claim_due` 索引 + §7.3 RLS）、real-SQL 验证（[`forwarding-persistence §7.4/§14`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）。**real-SQL 载体用 Zonky embedded-postgres（PG 16.2 in-process），非 Testcontainers**——执行环境 Docker daemon 经认证代理（407）不可达、host 无 sudo、无本地 PG（§7.4）；adapter / migration 不依赖测试载体，生产可换回 Testcontainers。**153 tests green**（Stage 11 的 136 + real-SQL 17），ArchUnit 纯度 green（Spring/JDBC 限制在 `persistence.jdbc` 子包）。**`§6.1`「不引入 JDBC driver」约束由 Stage 12 裁决解除**（真实 JDBC adapter / Flyway / Spring JDBC 进入许可范围）；**`§6.2` 始终不得项不变**。
- 后续 deferred（持久化剩余项）：lease store 物理实现 / polling cadence / 并发 worker 分片 / backpressure 参数 / `ForwardingDispatchLoop` 接真实 scheduler / 接入 agent-runtime 受控调用路径。
- **transport / 投递模型议题（独立 H2/H3，不进 Stage 12 → Stage 13 已完成候选评审，待 H2/H3 裁决）**：人类提出「基于 MQ 以获反压 / 降低接收方压力」，但 MQ 撞 §3 对 C4 的拒绝 + §6.2 禁 broker；且诉求触及 C3 投递模型根本裁决 —— dispatcher-push（投递速率由发送方控制）无消费方控速能力，真正的反压需 consumer-pull / MQ（投递/消费速率解耦）。**Stage 13（2026-06，裁决阶段、无生产代码）已产出候选评审 review packet**（[`agent-bus-forwarding-runtime-transport-candidates`](agent-bus-forwarding-runtime-transport-candidates.md)）：比较 T1 dispatcher-push over sync RPC / T2 dispatcher-push over broker / T3 consumer-pull over DB / T4 C3+broker hybrid 四个投递模型变体 × 8 维度，核心结论是「反压诉求内核 = 消费方控速 / 速率解耦，而 MQ 只是满足该内核的载体之一」—— **T3 consumer-pull over DB 是唯一同时满足「强反压」+「不破 §6.2」+「低复杂度」的候选**（复用 Stage 12 claim / lease / `SKIP LOCKED` / RLS，代价是 dispatcher 归属从 sender 转到 receiver，不破坏持久层）；作为非裁决推荐进入 H2/H3。最终 push / pull / MQ 裁决仍由 H2/H3 在 review packet 后做；若选 T2 / T4 需 H2/H3 解除 `§6.2` 引 MQ + broker 产品裁决。deliver 异常重投策略（attemptCount 递增 / 退避 / 熔断）经 Stage 13 拆分为**可独立于投递模型先行**的子项，**Stage 14 已落地**（[`forwarding-persistence §15`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)）：`ForwardingRetryPolicy` 端口（`ExponentialBackoff` 默认实现，overflow-safe 指数退避 `min(cap, base<<shift)` + 注入 `LongSupplier` jitter + `exhausted(attemptCount)`→DLQ，与 `DispatchLeasePolicy` 同层注入 worker）+ worker RETRY 分支接入；`ForwardingDeliveryResult` 移除 `nextAttemptAtMillisEpoch` 字段、`retry(code)` 简化为单参（重投时机归 policy，交付动作与重试治理分离关注点；outbox `nextAttemptAt` 仍是 policy 决策的 persisted 字段）；`ForwardingCircuitBreaker` 端口 + `ALWAYS_CLOSED` no-op **deferred 未接入 worker**（形态依赖 transport 模型：push 需主动短路、consumer-pull 天然自调速；**Stage 15 PoC 选 T1 push 后该阻塞解除，Stage 16 已接入 worker，见 §8 Stage 16 段**）。**158 tests green**（Stage 12 的 153 + 5），ArchUnit 纯度 green，§6.2 不变。续约真实耗时验证 deferred 依赖投递模型裁决 + 真实 deliver 落地。不阻塞 Stage 12 持久化。
- Stage 15（**已完成**，2026-06）：真实投递绑定 PoC —— A2A HTTP transport adapter 落地，证明 agent-bus 的 C3 forwarding 可消费 agent-runtime 的 A2A JSON-RPC `/a2a` 端点（此前 `ForwardingDeliveryPort.deliver` 一直用 `InMemoryForwardingDelivery` fake，从未证明真实对接）。`A2aForwardingDeliveryPort implements ForwardingDeliveryPort`（per-endpoint `JSONRPCTransport` 缓存 + `sendMessageStreaming` 阻塞 `CountDownLatch.await(streamTimeoutMillis)` + 终态映射：COMPLETED / INPUT_REQUIRED→`acked`、`isFinal && !COMPLETED` / `AUTH_REQUIRED`→`retry(RECEIVER_UNAVAILABLE)`、超时→`retry(DELIVERY_TIMEOUT)`、连接级错误→`retry(RECEIVER_UNAVAILABLE)`、resolver 空→`dlq(ROUTE_NOT_FOUND)`、`deliver` 不抛非 lease 异常 = MI11-002 契约）；`ForwardingEndpointResolver` 注入端口解开 routeHandle opaque（HD4 不破，默认 `MapEndpointResolver`，生产由 Stage 3 registry 实现）；`A2aForwardingProperties`（`streamTimeoutMillis` + `tenantHeaderName`）。**同步等完成语义**（Stage 15 范围决策，镜像 main 的 `A2aRemoteAgentOutboundAdapter` streaming 模式 = Stage 13 的 T1 dispatcher-push over sync RPC）：deliver 等 Task 到终态才 ACKED，**不裁决** T1 vs C3 异步的哲学张力（仍 H2/H3）。**线上格式对称性发现**：harness 用 A2A SDK 自身序列化器 `JsonUtil.toJson(new SendStreamingMessageResponse(id, event))` 产出 SSE `event:jsonrpc\ndata:<json>` 帧，与 agent-runtime `A2aJsonRpcController` 逐字节一致 —— 真实 `JSONRPCTransport` 解析的字节 == 真实 agent-runtime 响应（同 Stage 12 embedded-postgres「用测试载体验证真实协议代码」哲学，非 agent-runtime 自测用的 `RecordingTransport` fake）。**SDK 行为发现**：HTTP 4xx / 5xx（如 503）不被当错误 —— SDK 把非 2xx 视为静默空 SSE 流 → deliver 阻塞到 `DELIVERY_TIMEOUT`；真正的 socket 断开（`SocketPolicy.DISCONNECT_AFTER_REQUEST`）才触发 `errorConsumer` → `RECEIVER_UNAVAILABLE`。tenant 连续性（R-C.c）走 `ClientCallContext` 的 `X-Tenant-Id` header（对齐 `A2aJsonRpcController @RequestHeader`）；`payloadRef` 走 `MessageSendParams.metadata` 扩展位（§6.2 不破，`TextPart` 仅载 routing descriptor）。**164 tests green**（Stage 14 的 158 + 5 MockWebServer 场景），ArchUnit 纯度 green（`org.a2aproject` 圈进 `transport.a2a`，全局 netty / jackson / servlet 规则不退化），§6.2 不变（文本扫描豁免见 §6.1 Stage 15 段）。**`§6.1` 第 4 项「真实投递绑定 deferred」由 Stage 15 解除**（A2A transport adapter 进入许可范围）；**`§6.2` 始终不得项不变**。**deferred**：真实 agent-runtime 端到端拉起验证（受 runtime 构建坑阻塞）、`REMOTE_TASK_FAILED` non-retryable 码（远程 task 失败的精确分类）、registry 集成的 resolver 生产实现、连接池治理、push / pull / MQ 最终模型裁决（仍 H2/H3）。`ForwardingCircuitBreaker` 接入 worker 由 **Stage 16 完成**；真实 agent-runtime 端到端拉起由 **Stage 17 完成**；`REMOTE_TASK_FAILED` non-retryable 码 + 失败路径端到端验证由 **Stage 18 完成**（见下）。
- Stage 16（**已完成**，2026-06）：`ForwardingCircuitBreaker` 接入 `ForwardingDispatcherWorker` —— Stage 14 预留的 seam（端口 + `ALWAYS_CLOSED` no-op，**deferred 未接入**）由 Stage 16 填上。正当性来自 Stage 15 真实投递绑定 PoC 选了 **T1（dispatcher-push over sync RPC）**：push 模型 dispatcher 主动驱动投递，需 breaker 在故障 route 上主动短路（否则连续失败轰炸下游），故「breaker 形态依赖 transport」的悬挂前提落地。落地：`ForwardingCircuitBreaker` 加 `recordOutcome(routeHandle, result)`（投递后反馈驱动状态机，`ALWAYS_CLOSED` 由单方法 lambda 改两方法匿名类）；真实实现 `RouteCircuitBreaker`（纯 JDK 三态机 CLOSED→OPEN→HALF_OPEN，`failureThreshold` 连续 retryable 失败 trip、`cooldownMillis` 冷却、注入 `EpochClock` 与 worker 续约同源；per-route `ConcurrentHashMap<String, RouteState>` + `synchronized(RouteState)` + `probeInFlight` 单探测锁）；worker 7 参构造器注入，`runOnce` 三处接入 —— 投递前 `allowsDelivery`（OPEN 短路，复用现有 skip 路径：留 DISPATCHING 待租约过期回收、不消耗 attemptCount）、投递后 `recordOutcome`（switch 前，使 switch 内 lease mutation 异常不影响反馈）、deliver 抛异常 catch 块补 `recordOutcome(retry(RECEIVER_UNAVAILABLE))`（HALF_OPEN 探测抛异常也不泄漏 `probeInFlight`）。触发分类在 breaker 内部：ACKED→成功重置、RETRY_SCHEDULED（retryable 码）→失败计数、DLQ / EXPIRED→忽略（`retry(code)` 已在 Stage 9 拒绝非 retryable 码，breaker 无需再查）。`DispatchTickResult` 不变（短路复用 skip 路径，自洽不变量自动保持，无新计数器）。**transport-agnostic 不破**：breaker 端口只消费 `ForwardingRouteHandle` / `ForwardingDeliveryResult`（HD4 守恒），即便 H2/H3 最终裁决选 T3 consumer-pull 也无害（receiver 自调速，breaker 基本不触发）。**179 tests green**（Stage 15 的 164 + 11 `RouteCircuitBreakerTest` + 4 worker 行为测试），ArchUnit 纯度 green（`RouteCircuitBreaker` 纯 JDK，无需新豁免），**§6.2 始终不得项不变**（breaker 无 Task state / payload body / concrete broker client）。**deferred**：per-route breaker 状态非持久化（进程重启回 CLOSED）、`failureThreshold` / `cooldownMillis` 生产调优 / per-route 配置、HALF_OPEN 单探测（当前）、多 worker 实例共享单例 breaker、push / pull / MQ 最终模型裁决（仍 H2/H3）。

- Stage 17（**已完成**，2026-06）：**首次跨模块集成** —— agent-bus ↔ agent-runtime 的第一个依赖（仅 test scope，两模块此前完全隔离构建）。第 15 阶段用 MockWebServer 证明了 `A2aForwardingDeliveryPort` 发出字节级正确的请求并把远程 Task 生命周期映射到 `ForwardingDeliveryResult`；第 17 阶段用**真实的 `LocalA2aRuntimeHost`（Spring Boot A2A 服务器）替换 MockWebServer**，端到端驱动完整链路：`JdbcForwardingOutbox enqueue → ForwardingDispatchLoop tick → ForwardingDispatcherWorker claim/lease-guarded → A2aForwardingDeliveryPort deliver → 真实 /a2a JSON-RPC+SSE → StubHandler → COMPLETED → worker markAcked → outbox record ACKED`。正当性：验证第 12 阶段真实持久化 + 第 10 阶段 dispatch loop + 第 15 阶段真实投递绑定 + 第 16 阶段 worker 在一条真实链路下闭环自洽，且端到端租户隔离（Rule R-C.c，无跨租户回退）。落地：`C3ForwardingEndToEndIntegrationTest` 双测试 —— happy path（一条 record 走完到 ACKED，tick 自洽 `claimed == acked+…`）+ 端到端租户隔离（tenant A 两条 route 指向**同一**真实 host，loop 只 ACK tenant A 的 record、tenant B 仍 PENDING）；`agent-bus/pom.xml` 加 `agent-runtime` test-scope 依赖（首次跨模块依赖）；`AgentBusDependencyBoundaryTest` 加 `bus_does_not_depend_on_agent_runtime` 守卫（生产 `com.huawei.ascend.bus..` 仍不得 reach `com.huawei.ascend.runtime..`，test-only 依赖正是这条守卫存在的理由；同时取代 `agent-service → agent-runtime` 重命名 034da8f7 遗留的旧 `service..` 守卫）。**两个第 17 阶段发现**：(a) **agent-runtime 对 JDBC-bearing 共享 classpath 敏感** —— `LocalA2aRuntimeHost` 的 A2A server Spring 上下文是纯内存（A2A SDK task store / event queue，无 JDBC），其自身测试从不触发 `DataSourceAutoConfiguration`；但 agent-bus 的 `spring-boot-starter-jdbc` + postgres driver + flyway（第 12 阶段）泄漏到共享测试 classpath 后，`DataSourceAutoConfiguration` / `FlywayAutoConfiguration` 对真实 host 上下文 fire 并因缺 `spring.datasource.url` 失败。IT 里用 `System.setProperty("spring.autoconfigure.exclude", …)` 排除（`LocalA2aRuntimeHost.port(int)` 工厂未暴露 property hook，system property 是唯一高于其 package-private defaultProperties 的属性源）。记录给 agent-runtime：co-deployed 消费方带 jdbc starter 会撞同样失败，应排除。(b) **Spring Boot 4 autoconfigure 重打包** —— jdbc autoconfigure 从 `org.springframework.boot.autoconfigure.jdbc`（Spring Boot 3）移到 `org.springframework.boot.jdbc.autoconfigure`（Spring Boot 4），flyway 从 `…autoconfigure.flyway` 移到 `org.springframework.boot.flyway.autoconfigure`；旧包名排除静默无效。**意外强证据**：运行日志显示真实 `A2aAgentExecutor` 收到 `metadata.tenantId=tenant-loop` / `tenant-iso-a` —— 证明 `X-Tenant-Id` header 不只发送（第 15 阶段 wire-level 断言），更被真实 agent-runtime controller 接收/解析/传给 handler。**182 tests green**（第 16 阶段 179 + 2 IT），ArchUnit green（新 `runtime..` 守卫 + SpiPurity 不受影响；跨模块依赖仅 test-scope，不进生产 classpath），**§6.2 始终不得项不变**（IT 不含 Task 执行状态 / payload body / concrete broker，复用第 12 阶段 CONTROL_ONLY envelope）。**deferred**：真实 agent handler（用 StubHandler）、registry 集成的 resolver 生产实现（用 MapEndpointResolver）、连接池治理、`REMOTE_TASK_FAILED` non-retryable 码（由 **Stage 18 完成**，见下）、push/pull/MQ 最终裁决（仍 H2/H3）。

- Stage 18（**已完成**，2026-06）：失败路径端到端验证 + `REMOTE_TASK_FAILED` non-retryable 码收口 —— Stage 17 首次跨模块集成只验证了 happy path（StubHandler → COMPLETED → ACKED），Stages 7–16 落地的所有失败处理路径（ACK / RETRY / DLQ / EXPIRED、lease-guarded mutation、retry policy、circuit breaker）此前只在 fake-delivery 单元 / 契约测试覆盖，从未在真实端到端链路上跑通。Stage 18 闭合这个盲区，并把三次 deferred（14 → 15 → 17）的 `REMOTE_TASK_FAILED` non-retryable 码一并落地，让远程 agent 终态业务失败被精确分类。**核心论证**：A2A `FAILED` 是远程 agent 的**终态业务失败**（业务层），区别于 infra 层 retryable 失败（超时 / receiver 不可达）—— 对确定失败的 task 重投同一输入只会轰炸下游，故 `FAILED` / `CANCELED` / `REJECTED`（`isFinal && !COMPLETED`）→ `dlq(REMOTE_TASK_FAILED)` 直达 DLQ（不消耗 retry 预算，正交于 Stage 14 retry policy）；`AUTH_REQUIRED`（中断）仍是 `retry(RECEIVER_UNAVAILABLE)`（认证可恢复，非 task 失败）。落地：(a) `ForwardingFailureCode.REMOTE_TASK_FAILED("remote_task_failed", NON_RETRYABLE)` enum 值 + javadoc；(b) `A2aForwardingDeliveryPort` 终态映射从保守 switch 改 `isFinal()` if-chain（FAILED/CANCELED/REJECTED → DLQ，未来新增 A2A final state 自动正确分类；避免 CANCELED/CANCELLED 拼写风险）；(c) `C3ForwardingFailurePathIntegrationTest` 双失败场景端到端 —— 真实 `FailingHandler`（`resultAdapter` 映射 `AgentExecutionResult.failed(...)` → 真实 A2A server Task FAILED → 真实 SSE FAILED 帧）→ `dlq(REMOTE_TASK_FAILED)` → persisted `last_failure_code = remote_task_failed`；不可达 route（`freeUnusedPort()` → 真实 socket 拒连）→ `retry(RECEIVER_UNAVAILABLE)` → RETRY_SCHEDULED + `attempt_count = 1` + future `next_attempt_at`；(d) classification harness 加 `REMOTE_TASK_FAILED` 断言。**最小足迹**：无 DDL 改动（outbox CHECK 只校验 status↔null 配对、不枚举码值）、无 SqlCodec 改动（`decodeFailureCode` 遍历 `values()` 自动覆盖）、无 record 改动（DLQ compact constructor 接受任意 non-retryable 码）。IT 复用 Stage 17 boot recipe（embedded-postgres + Flyway + `spring.autoconfigure.exclude` + 真实 `LocalA2aRuntimeHost`），唯一差异是 `FailingHandler`（失败而非完成）。**184 tests green**（Stage 17 的 182 + 2 失败路径 IT），ArchUnit green，**§6.2 始终不得项不变**（`remote_task_failed` 是 failure-code enum 值，非 Task 执行状态 / payload body / concrete broker；IT 复用 CONTROL_ONLY envelope）。**deferred**：真实 agent handler（仍 FailingHandler/StubHandler 程序化失败/完成）、registry 集成的 resolver 生产实现、连接池治理、push/pull/MQ 最终裁决（仍 H2/H3）。重投往返生命周期端到端验证（claimDue 的 RETRY_SCHEDULED 回收子句跨多 tick 触发、attempt_count 攀升、exhausted/恢复两出口）由 **Stage 19 完成**（见下）。

- Stage 19（**已完成**，2026-06）：重投往返生命周期端到端验证 —— Stage 18 把 record 推进到 `RETRY_SCHEDULED` 就停（单 tick），只证明重投路径的**入口**（不可达 route → `retry(RECEIVER_UNAVAILABLE)` → `attempt_count=1` + future `next_attempt_at`）；但 `claimDue` 的 `RETRY_SCHEDULED` 回收子句 `next_attempt_at <= :now`（outbox 价值主张的核心：持久化 + 自动重驱动到成功或 DLQ）**从未端到端触发 / 验证** —— 回收是否真发生、`attempt_count` 是否真在重投间攀升、retry policy 的 `exhausted` gate 是否在正确计数触发、两出口（exhausted→DLQ / 恢复→ACKED）是否真能闭环，此前只在 fake-delivery 单元 / 契约层覆盖。Stage 19 在真实持久化（embedded-postgres + Flyway + `JdbcForwardingOutbox`）上驱动**同一条 record 越过 `next_attempt_at` 跨多个 tick**，看 `claimDue` 回收它、重投、把 `attempt_count` 推到两个出口之一。两场景：(A) 持续失败 → 重投 3 次 → `exhausted(maxAttempts)` → DLQ（不可达 route 真实 socket 拒连 → 每次 `retry(RECEIVER_UNAVAILABLE)`；`maxAttempts=3` 在 4 ticks 耗尽；outbox 终态 DLQ、`attempt_count=3`、`moveToDlq` 不递增）；(B) 间歇失败恢复 → 重投 2 次 → ACKED（fake delivery port 注入 `[retry, retry, acked]`；第 3 次恢复；outbox 终态 ACKED、`attempt_count=2`、`markAcked` 不递增）。**时间控制（核心新增）**：`MutableEpochClock`（test-only 纯 JDK）注入 worker + 协调多 tick `TickSource`（`+61s` step > 任何 backoff），无需 sleep / scheduler（§6.1「总线无调度器」守恒）；**两时钟协调** —— tick source 在 yield 前 `advanceTo` clock，使 worker `clockNow`（`nextAttemptAt` 基准）与 `claimDue :now`（tick instant）同源（这条链最易错的接线点）；**真实时钟约束** —— `leaseGuardedUpdate` 用真实 `System.currentTimeMillis()`，故 tick instant 从 `T0` 单调递增、`leaseUntil = instant + 60s` 始终 > 真实时钟，租约防护不误判过期。**flaky 修复**：parent pom 的 surefire 配 JUnit5 测试类 4 路并发（`mode.classes.default=concurrent`），但 Spring Boot 4 `SpringApplication.run` 非线程安全 —— 两个 boot-context 的 `*IntegrationTest`（命名绕过 failsafe 的 `**/*IT.java` include、落到并发 surefire）在同一复用 JVM 撞 `ConcurrentModificationException` + 全局 `spring.autoconfigure.exclude` System property 互相踩踏；Stage 19 给两个 context-boot IT（`C3ForwardingEndToEndIntegrationTest` / `C3ForwardingFailurePathIntegrationTest`）加 JUnit5 `@Isolated`（独占执行、不与其他类并发），根治 flaky（parent pom 注释本已警告 boot-context IT 应走串行 failsafe；理想改名 `*IT` 留给 IT owner）。**无生产代码改动**（Stage 19 是纯测试 / 验证阶段；`@Isolated` + import 也是测试代码）。**186 tests green**（Stage 18 的 184 + 2 retry lifecycle IT），ArchUnit green，**§6.2 始终不得项不变**（两场景均用 CONTROL_ONLY envelope；`MutableEpochClock` / tick source / fake port 是 test-only 纯 JDK helper，无 concrete broker / payload body / token stream / Task 执行状态 / 跨租户回退）。**deferred**：真实 retry 调度（`ForwardingDispatchLoop` 接真实 scheduler / polling cadence）、真实 agent handler（仍 StubHandler / FailingHandler / fake port）、registry 集成的 resolver 生产实现、连接池治理、push/pull/MQ 最终裁决（仍 H2/H3）。

相关文档：

- Stage 5 候选评审：[`agent-bus-forwarding-runtime-candidates`](agent-bus-forwarding-runtime-candidates.md)。
- Stage 13 transport 投递模型候选评审：[`agent-bus-forwarding-runtime-transport-candidates`](agent-bus-forwarding-runtime-transport-candidates.md)。
- Stage 6 计划：[`agent-bus-stage5-review-and-stage6-plan`](../delivery-projections/agent-bus-stage5-review-and-stage6-plan.md)。
- Stage 7 计划：[`agent-bus-stage6-review-and-stage7-plan`](../delivery-projections/agent-bus-stage6-review-and-stage7-plan.md)。
- Stage 8 计划：[`agent-bus-stage7-review-and-stage8-plan`](../delivery-projections/agent-bus-stage7-review-and-stage8-plan.md)。
- Stage 9 计划：[`agent-bus-stage8-review-and-stage9-plan`](../delivery-projections/agent-bus-stage8-review-and-stage9-plan.md)。
- Stage 12 计划：[`agent-bus-stage11-review-and-stage12-plan`](../delivery-projections/agent-bus-stage11-review-and-stage12-plan.md)。
- Stage 13 计划：[`agent-bus-stage12-review-and-stage13-plan`](../delivery-projections/agent-bus-stage12-review-and-stage13-plan.md)。
- Stage 14 评审与 Stage 15 计划：[`agent-bus-stage14-review-and-stage15-plan`](../delivery-projections/agent-bus-stage14-review-and-stage15-plan.md)。
- Stage 15 评审与 Stage 16 计划：[`agent-bus-stage15-review-and-stage16-plan`](../delivery-projections/agent-bus-stage15-review-and-stage16-plan.md)。
- Stage 16 评审与 Stage 17 计划：[`agent-bus-stage16-review-and-stage17-plan`](../delivery-projections/agent-bus-stage16-review-and-stage17-plan.md)。
- Stage 17 评审与 Stage 18 计划：[`agent-bus-stage17-review-and-stage18-plan`](../delivery-projections/agent-bus-stage17-review-and-stage18-plan.md)。
- Stage 18 评审与 Stage 19 计划：[`agent-bus-stage18-review-and-stage19-plan`](../delivery-projections/agent-bus-stage18-review-and-stage19-plan.md)。
- Stage 7 L2 设计：[`forwarding-outbox-inbox`](../../../../architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md)。
- Stage 8 持久化 L2：[`forwarding-persistence`](../../../../architecture/docs/L2/agent-bus/forwarding-persistence.md)。
- Stage 7 runtime 契约：[`ICD-Agent-Bus-Forwarding-Runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)。
- 设计态契约：[`ICD-Agent-Bus-Forwarding`](../../05-contracts/human-readable/ICD-agent-bus-forwarding.md)（HD4）。
- L1 入口：[`agent-bus L1 README`](../../../../architecture/docs/L1/agent-bus/README.md)。
