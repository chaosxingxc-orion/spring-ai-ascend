---
artifact_type: delivery_projection
version: agent-bus-stage12-review-and-stage13-plan
status: draft
source_commit: 2f5cc328
source_stage12_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage11-review-and-stage12-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_candidates: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
target_module: agent-bus
---

# agent-bus Stage 12 评审与 Stage 13 计划

## 0. 结论

最新提交 `2f5cc328` 可以作为 Stage 12 的阶段性成果接受：4 项 H2/H3 选型裁决（Postgres / agent-bus 自有 Spring JDBC / transport 拆出 / 启用 RLS）全部落位，Postgres JDBC adapter（`JdbcForwardingOutbox` + `JdbcForwardingInbox` + `ForwardingSqlCodec`）、Flyway migration `V1`（MI9-006 全部 CHECK + `ix_outbox_claim_due` 部分索引 + §7.3 RLS）、embedded-postgres real-SQL 验证（17 tests）全部落地，**153 tests green**，并正式打破路径 B（`§6.1`「不引入 JDBC」解除，Spring/JDBC 圈进 `persistence.jdbc` 子包，`§6.2` 始终不得项不变）。Stage 12 把 C3 的**持久化层**做完整了：outbox / inbox / claim / lease / dispatch-loop 现在都有真实 Postgres 实现，不再是 in-memory 替身。

但诚实评审暴露了 Stage 12 范围之外、由「transport 拆出」明确 deferred 的四个未决项——它们都不是 Stage 12 的 bug（Stage 12 范围是持久化，这些是设计内的 deferred scope），而是 C3 从「可持久化」走向「可真正投递」前的必要输入：

- **MI13-T transport / 投递绑定完全缺失**：`ForwardingDeliveryPort.deliver(record, nowMillisEpoch)` 是纯抽象，唯一实现是 test-scope 的 `InMemoryForwardingDelivery`；C3 能 claim / lease / dispatch-loop，但 deliver 不知道发去哪——持久化层是闭环的，投递半边是断的。
- **MI13-R inbox receiver 端完全缺失**：`ForwardingInboxPort` 有 JDBC 实现，但 `markConsumed` / `markRejected` 没有任何调用方；CONSUMED / REJECTED 状态从未被驱动，接收半边没闭环。
- **MI13-O 调度运维化**：`ForwardingDispatchLoop` 骨架在（`TickSource` / `IdleStrategy` 注入），但无真实 scheduler / polling cadence / 并发 worker 分片，未接入 agent-runtime 受控调用。
- **MI13-D deliver 异常重投策略**：`attemptCount` 递增已有，但退避（backoff）/ 重试上限 / 熔断策略未定（§8 明确随 transport 议题 deferred）。

简短判断：

- Stage 12 方向正确，持久化层（adapter + migration + RLS + real-SQL）完整且经真实 PG 16.2 验证，路径 B 在 H2/H3 裁决下正确打破。
- 上述四个未决项中，**transport（MI13-T）是核心阻塞**——它触及 C3 投递模型的根本裁决（push vs pull / 是否引 MQ），decision §8 已预告「可能需新的 review packet 复议 C3 dispatcher-push 模型」。receiver（MI13-R）是投递的对端（push 的被动接收方，或 pull 的主动拉取方），其补齐方式取决于投递模型裁决。ops（MI13-O）与 deliver 重投策略（MI13-D）可独立推进或随裁决。
- Stage 13 主轴经人类确认为 **Transport / 投递模型裁决**（候选评审 + 复议 C3 dispatcher-push + 反压分析），**无生产代码**（裁决阶段，性质同 Stage 5 / Stage 6）：产出 transport 候选评审 review packet + 推荐方向 + rejection criteria，最终 push / pull / MQ 裁决由 H2/H3 在 review packet 后做，代码最早 Stage 14+。

## 1. 本次提交审查

### 1.1 完成情况

本次提交（`2f5cc328`，experimental，fast-forward 推送）完成：

- **MI12-001 4 项选型裁决**：DB = Postgres；migration·adapter 归属 = agent-bus 自有 + Spring JDBC；transport = 拆出 Stage 12 单独议；RLS = 启用纵深防御。落位于 [`decision §4 / §6.1 / §8`](../review-packets/agent-bus-forwarding-runtime-decision.md)。
- **MI12-002 JDBC adapter**（`com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc`）：`JdbcForwardingOutbox`（实现 `ForwardingOutboxPort` + `ForwardingOutboxClaimPort`，enqueue / markAcked / scheduleRetry / moveToDlq / markExpired / statusOf + claimDue / renewLease / releaseLease）、`JdbcForwardingInbox`（receive / markConsumed / markRejected / statusOf）、`ForwardingSqlCodec`（状态存 enum 名、失败码存 snake_case wire code、未知码抛 `IllegalStateException`）。claim §7.1 `FOR UPDATE SKIP LOCKED RETURNING`；状态变更 §7.2 lease-owner guarded `WHERE`（0 行 → `ForwardingLeaseException` 分类 RECORD_NOT_FOUND / NO_LEASE / OWNER_MISMATCH / NOT_DISPATCHING）；reclaim / renew / release（release 过期语义 `lease_until = -1`，保留 lease_owner 满足 `ck_outbox_lease_status`）。
- **MI12-003 Flyway migration**：`V1__create_agent_bus_forwarding_outbox_inbox.sql`——两表 + 7 个 MI9-006 条件 CHECK（`ck_outbox_*` / `ck_inbox_*`）+ `ix_outbox_claim_due` 部分索引 + 2 个 RLS policy（`current_setting('app.tenant_id', true)` fail-closed）。
- **MI12-004 real-SQL 验证**：`ForwardingJdbcIntegrationTest`（17 tests，Zonky embedded-postgres PG 16.2 in-process）——flyway migration / enqueue-claim-ack round-trip / 并发 claim 无重复（`SKIP LOCKED`）/ stale ACK 分类 / 过期 lease reclaim / renew / release 过期语义 / CHECK 兜底 / cross-tenant / RLS fail-closed / inbox 去重+拒绝+CHECK。
- **MI12-006 pom + ArchUnit 精确化**：`spring-boot-starter-jdbc` + `flyway-core` + `flyway-database-postgresql`(runtime) + `postgresql`(runtime) + `embedded-postgres`(test) + `embedded-postgres-binaries-linux-arm64v8`(test)；`AgentBusForwardingSpiPurityTest` 把 Spring / JDBC / `javax.sql` 圈进 `persistence.jdbc` 子包，`bus.forwarding..` 主体仍纯 Java，hikari / jackson / reactor / kafka / nats / servlet / netty 全局禁止（无豁免）。
- 文档同步：L2 `forwarding-persistence`（§5 / §7 / §7.4 / §14）、ICD（边界 Stage 12 项 + open issues）、yaml（`stage12_scope`）、decision（§4 / §6.1 / §8）、L1 × 6（README / development / process / physical / ARCHITECTURE / logical）。

### 1.2 验收判断

- 4 项选型收口，3 个 adapter + V1 migration + 17 real-SQL tests + ArchUnit 精确化全部落地，**153 tests green**（Stage 11 的 136 + real-SQL 17）。
- `§6.2` 始终不得项不变（禁 concrete broker / MQ、Task execution state、跨 tenant fallback、payload body）；ArchUnit 纯度 green（Spring/JDBC 限 `persistence.jdbc` 子包）。
- real-SQL 载体用 Zonky embedded-postgres（执行环境 Docker daemon 经认证代理 407 不可达、host 无 sudo、无本地 PG；adapter / migration 不依赖测试载体，生产可换回 Testcontainers）——诚实且可迁移。
- **但 transport / 真实投递绑定 / receiver 端 / 调度运维化是 Stage 12 范围外的 deferred scope**，Stage 12 评审不把它们视为缺陷，而视为 Stage 13 的输入。

## 2. 当前修改意见

| 编号 | 问题 | 严重度 | 证据 | 修改意见 |
|---|---|---|---|---|
| MI13-T | transport / 投递绑定完全缺失：`ForwardingDeliveryPort.deliver(record, nowMillisEpoch)` 纯抽象，唯一实现 `InMemoryForwardingDelivery`（test scope）；C3 持久化层闭环但 deliver 不知道发去哪 | 高 | `ForwardingDeliveryPort.java`（spi，纯接口）；`InMemoryForwardingDelivery.java`（test）；`ForwardingDispatcherWorker.runOnce` 调 `deliveryPort.deliver(record, clockNow)` 后据 `outcome` 走 markAcked / scheduleRetry / moveToDlq / markExpired | Stage 13 主轴：transport 投递模型候选评审 + 复议 C3 dispatcher-push（push vs pull / 是否引 MQ / C3+broker hybrid），覆盖 §8 全部 deferred；无生产代码，代码 deferred Stage 14+ |
| MI13-R | inbox receiver 端缺失：`ForwardingInboxPort` 有 JDBC 实现，但 `markConsumed` / `markRejected` 无任何调用方；CONSUMED / REJECTED 从未被驱动，接收半边未闭环 | 中 | `JdbcForwardingInbox.markConsumed / markRejected` 无生产调用方（仅 harness）；receiver 消费逻辑不存在 | Stage 13 评审中评估：receiver 是 push 模型的被动接收方（暴露 HTTP/gRPC 端点）还是 pull 模型的主动拉取方——补齐方式取决于 MI13-T 裁决 |
| MI13-O | 调度运维化：`ForwardingDispatchLoop` 骨架在（`TickSource` / `IdleStrategy` 注入），无真实 scheduler / polling cadence / 并发 worker 分片，未接 agent-runtime 受控调用 | 低-中 | `ForwardingDispatchLoop`（纯 Java 骨架，无 scheduler / 线程）；§8 deferred 项 | deferred 生产化 / 运维化阶段（不阻塞 transport 裁决）；polling cadence / worker 分片 / scheduler 集成独立议题 |
| MI13-D | deliver 异常重投策略未定：`attemptCount` 递增已有，退避（backoff）/ 重试上限 / 熔断策略未定；§8 明确随 transport 议题 deferred | 中 | `ForwardingDispatcherWorker` scheduleRetry 只递增 attemptCount + 写 nextAttemptAt，无 backoff / 熔断策略 | Stage 13 评审子项：deliver 重投策略（指数退避 / attemptCount 上限 / 熔断）可独立于投递模型裁决，建议独立子项；续约真实耗时验证随接真实 deliver（Stage 14+） |

## 3. Stage 13 目标

Stage 13 的目标是把 Stage 12 暴露的 transport 议题（MI13-T）做一次完整的**投递模型候选评审 + 复议 C3 dispatcher-push**，为 H2/H3 的 push / pull / MQ 裁决提供结构化输入。Stage 13 主轴经人类确认为 **Transport / 投递模型裁决**，性质同 Stage 5（候选评审）/ Stage 6（裁决准备），**无生产代码**：

> 产出 transport 投递模型候选评审 review packet（T1-T4）+ 反压根本分析（push 无消费方控速 vs pull / MQ 天然反压）+ 非裁决性质推荐方向 + rejection criteria + deliver 重投策略子项；最终 push / pull / MQ 裁决由 H2/H3 在 review packet 后做；不预设方向；代码（真实 transport 实现）deferred Stage 14+，依赖 H2/H3 裁决。

核心张力（decision §8 预告）：人类曾提出「基于 MQ 以获反压 / 降低接收方压力」，但 MQ 撞 §3 对 C4 的拒绝 + §6.2 禁 broker；且诉求触及 C3 投递模型根本裁决——**dispatcher-push（投递速率由发送方控制）无消费方控速能力，真正的反压需 consumer-pull / MQ（投递 / 消费速率解耦）**。Stage 13 必须诚实回答：**反压诉求是否必须引 MQ（解除 §6.2），还是 consumer-pull over DB（复用 Stage 12 的 claim / lease / SKIP LOCKED 语义）就能满足而不破 §6.2**。

## 4. Stage 13 开发切片

### 切片 0：范围确认 + 议题框定

- 确认 Stage 13 = transport 投递模型评审 + 复议 C3 dispatcher-push（反压），无生产代码。
- 更新 [`decision §8`](../review-packets/agent-bus-forwarding-runtime-decision.md) transport 议题段 + [`ICD open issues`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)：transport 项从「deferred」标注为「Stage 13 评审中，待 H2/H3 裁决」。

DoD：decision §8 + ICD open issues 标注 Stage 13 进行中；明确本阶段不写生产代码。

### 切片 1：transport 投递模型候选评审 review packet

新增 `docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md`（格式参照 Stage 5 [`candidates`](../review-packets/agent-bus-forwarding-runtime-candidates.md)）。候选（投递模型层面，区分于 Stage 5 的运行态承载候选 C1-C5）：

| 候选 | 形态 | 是否引 MQ / 破 §6.2 |
|---|---|---|
| T1 | dispatcher-push over sync RPC（HTTP / gRPC）：outbox dispatcher claim 后主动同步调用 receiver 端点 | 否（不引 MQ） |
| T2 | dispatcher-push over broker：dispatcher claim 后发 broker topic，broker push consumer | 是（需解除 §6.2） |
| T3 | consumer-pull（DB-backed）：receiver 主动从 outbox / 共享 inbox 拉取，claim 后处理；复用 Stage 12 `SKIP LOCKED` + lease | 否（DB poll，不引 MQ） |
| T4 | C3 + broker hybrid（pull over broker）：outbox 持久 + broker 承载投递 + consumer 拉 broker（lag 流控） | 是（需解除 §6.2） |

评审维度（聚焦 transport / 投递模型，8 项）：①反压能力（消费方控速，核心）②投递 / 消费速率解耦 ③是否引 MQ / 是否需解除 §6.2 ④复杂度 / 新生产依赖 ⑤与 Stage 12 JDBC adapter + claim / lease 语义的契合（push 不动持久层；pull 复用 / 扩展 claim）⑥receiver 端补齐方式（push：暴露端点；pull：主动拉）⑦durable / ordering / tenant isolation 与已落地 C3 层的契合 ⑧deliver 异常重投策略（退避 / 上限 / 熔断）的承载层。

DoD：4 候选 × 8 维度评估完整；评分矩阵暴露 trade-off；**不预设方向**。

### 切片 2：复议 C3 dispatcher-push（反压根本分析）

- push vs pull 反压根本分析：T1（push）dispatcher 控投递速率，receiver 被动，压力大时只能 deliver 返回 `backpressure_rejected` → `scheduleRetry` 退避（发送方降速，非消费方控速）；T3（pull）receiver 控拉取速率，天然反压；T2 / T4（broker）broker lag / quota 流控。
- 关键张力：反压诉求是否必须引 MQ（T2 / T4，破 §6.2），还是 consumer-pull over DB（T3，不破 §6.2）即满足。
- T3 与 Stage 12 claim / lease / `SKIP LOCKED` 的契合度重点分析（可能复用持久化层，receiver 共享 outbox claim 或扩展 inbox 拉取语义）。
- 若评审倾向引 MQ（T2 / T4）：**必须明确标注「需 H2/H3 解除 §6.2 + 新增 broker 运维」，不得自行解除、不得引入 broker client 依赖**。

DoD：反压分析清楚，push / pull / MQ 的控速机理分明；§6.2 边界明确（解除需 H2/H3 裁决，本阶段不解除）。

### 切片 3：推荐方向 + rejection criteria + deliver 重投策略子项

- 非裁决性质推荐（给 H2/H3 快速聚焦，性质同 candidates §6.2，非最终裁决）。
- rejection criteria（每候选不可接受条件，避免弱候选长期保留）。
- **deliver 异常重投策略子项**（独立于投递模型，可独立裁决）：指数退避（base + jitter）、attemptCount 上限（耗尽 → DLQ / EXPIRED）、熔断（receiver 持续 `receiver_unavailable` 的 circuit break）；落点建议（`DispatchLeasePolicy` 同层的 retry policy 端口，或状态机参数化）。
- 续约真实耗时验证（MI11-001 的端到端）：接真实 deliver（Stage 14+）后验证，标注 deferred。

DoD：推荐 + rejection criteria + 重投策略建议完整；最终 push / pull / MQ 裁决标记为 H2/H3 在 review packet 后做。

### 切片 4：文档同步

- [`decision`](../review-packets/agent-bus-forwarding-runtime-decision.md)：§8 transport 段更新（评审完成 + 推荐方向 + 待 H2/H3 裁决）；§4 / §6.2 若评审不倾向引 MQ 则维持，若倾向引 MQ 则标注待解除。
- [`ICD`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)：open issues transport 项更新（评审完成，待裁决）。
- yaml `agent-bus-forwarding-runtime.v1.yaml`：`stage13_scope`（transport-candidates-review）。
- L1（README / physical）：transport 评审引用。

DoD：文档同步，不宣称 transport 已落地（仍是评审态）。

### 切片 5：构建验证 + 提交

- `mvn -f agent-bus/pom.xml test -s ~/.m2/settings.xml`：无生产 Java 改动，153 保持 green（裁决阶段）。
- 提交（transport review packet + 本 plan + decision / ICD / yaml / L1 同步）。

DoD：153 green；commit；无 Java 生产代码改动。

## 5. Stage 13 可接受结果

可以接受：

- transport candidates review packet 完整（4 候选 × 8 维度 + 评分矩阵），暴露 trade-off 而不下定论。
- 反压根本分析清楚（push 无消费方控速 vs pull / MQ 天然反压），关键张力（反压是否必须引 MQ）有结构化结论。
- 非裁决性质推荐方向 + rejection criteria；deliver 重投策略子项（退避 / 上限 / 熔断）建议完整。
- decision §8 + ICD open issues + yaml + L1 同步，标注评审完成、待 H2/H3 裁决。
- 153 tests 保持 green；无生产代码改动。

不能接受：

- 预设某个 transport 实现（HTTP / broker client）并写生产代码——本阶段是裁决阶段。
- 自行解除 §6.2 引入 concrete broker / MQ client 依赖（需 H2/H3 裁决）。
- 评审不覆盖 §8 全部 deferred（push / pull / MQ / hybrid + 反压 + deliver 重投策略 + 续约耗时）。
- 让 `agent-bus` 写 Task execution state；绕过 `routeHandle`；放 payload body / token stream（§6.2 始终不得）。

## 6. 给施工智能体的提示

- 本阶段是**裁决阶段**，无生产代码（性质同 Stage 5 / Stage 6），产出是 review packet + 文档同步。
- transport candidates review packet 必须覆盖 decision §8 的全部 deferred：push vs pull / 是否引 MQ / C3+broker hybrid + 反压 + deliver 异常重投策略 + 续约真实耗时验证。
- **不预设方向**：推荐是非裁决性质（给 H2/H3 聚焦），最终 push / pull / MQ 裁决由 H2/H3 在 review packet 后做。
- **§6.2 边界**：若评审倾向引 MQ（T2 / T4），必须标注「需 H2/H3 解除 §6.2 + 承担 broker 运维」，**不得自行解除、不得引入 broker client 依赖**（kafka / nats / rocketmq / amqp 等）。ArchUnit 仍全局禁 broker client。
- T3（consumer-pull over DB）是关键候选：可能满足反压而不破 §6.2，重点分析它与 Stage 12 `claimDue` / lease / `SKIP LOCKED` 的契合（receiver 是否能共享 / 扩展 outbox claim，或 inbox 是否需拉取语义）。
- deliver 重投策略（退避 / attemptCount 上限 / 熔断）可独立于投递模型裁决，建议作为独立子项，不与 push / pull 裁决耦合。
- 测试基线：当前 153 tests green；Stage 13 无 Java 改动，应保持 green。构建命令 `mvn -f agent-bus/pom.xml test -s ~/.m2/settings.xml`（system mvn 3.6.3 + Red Hat JDK 21，见 build-env-maven-via-settings-xml）。
- 范围外（本阶段不动）：receiver 端代码补齐、真实 transport 实现、调度运维化（polling / scheduler / worker 分片）、agent-runtime 集成——均 deferred Stage 14+，依赖 H2/H3 裁决。

## 7. 执行记录

（计划阶段；Stage 13 执行后回填：切片 0-5 完成情况、transport review packet 落位、H2/H3 裁决状态、153 tests 保持 green、commit。预期无 Java 生产代码改动。）
