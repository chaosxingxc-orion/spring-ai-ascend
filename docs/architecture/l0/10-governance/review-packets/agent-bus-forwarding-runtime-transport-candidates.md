---
artifact_type: a2d_review_packet
version: "agent-bus-forwarding-runtime-transport-candidates"
status: draft
source_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage12-review-and-stage13-plan.md"
source_decision: "docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md"
source_candidates: "docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md"
source_icd: "docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md"
target_module: agent-bus
---

# agent-bus 运行态转发 transport / 投递模型候选方案评审（Stage 13）

## 0. 评审边界与禁止范围

本评审是 **Stage 13 transport / 投递模型候选评审**，回答 Stage 12 暴露的核心未决项（[`stage12-review-and-stage13-plan §2 MI13-T`](../delivery-projections/agent-bus-stage12-review-and-stage13-plan.md)）：**C3 的持久化层已落地（Stage 12，Postgres JDBC adapter + Flyway + RLS），但 `ForwardingDeliveryPort.deliver` 是纯抽象、唯一实现是 in-memory fake——消息从 outbox claim 之后到底怎么到达 receiver？** 它比较**投递模型候选**，形成 H2/H3 级别的选型输入，**不实现 transport、不绑定 broker / MQ 产品、不引入生产依赖、不写生产代码**。

本评审建立在已稳定的 C3 底座之上：

- 运行态承载候选已在 Stage 5（[`candidates`](agent-bus-forwarding-runtime-candidates.md)）裁决为 **C3 database outbox / inbox**，Stage 7-12 逐步落地（领域模型 / 端口 / 状态机 / claim / lease / dispatch-loop / 真实持久化）。
- 本评审回答的是 C3 之内的**投递模型**问题（push vs pull / 是否引 MQ），不是重新评审运行态承载（C1-C5）。投递模型候选 T1-T4 是 C3 内部的「deliver 怎么发生」变体，不是与 C3 平级的承载候选。

权威输入：

- [`decision §8 transport 议题段`](agent-bus-forwarding-runtime-decision.md)：人类提出「基于 MQ 以获反压 / 降低接收方压力」，但 MQ 撞 §3 对 C4 的拒绝 + §6.2 禁 broker；且诉求触及 C3 投递模型根本裁决。
- [`Stage 13 计划 §3`](../delivery-projections/agent-bus-stage12-review-and-stage13-plan.md)：核心张力——dispatcher-push 无消费方控速能力，真正反压需 consumer-pull / MQ。
- [`L2 forwarding-persistence`](../../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)：Stage 12 已落地的 claim / lease / `SKIP LOCKED` 语义。
- [`ICD-Agent-Bus-Forwarding`](../../05-contracts/human-readable/ICD-agent-bus-forwarding.md)（HD4）：同步 ack / 异步完成、failure modes、payload reference path、broker-agnostic 契约。

**禁止范围（本评审不得突破）**：

- 不用产品能力反向定义架构语义（产品名仅示例）。
- 不绑定具体 broker / MQ 产品（Kafka / NATS / RocketMQ / RabbitMQ 一律示例）。
- 不在本评审内解除 [`decision §6.2`](agent-bus-forwarding-runtime-decision.md)（始终不得项）；若某候选需引 MQ，标注「需 H2/H3 解除 §6.2」，不自行解除。
- 不实现运行态 transport、不写生产代码（裁决阶段，性质同 Stage 5）。

## 1. 评审问题

本评审必须回答的五个核心问题：

1. C3 outbox 里一条 `DISPATCHING` 的消息，**deliver 这一跳**应该由谁主动触发——sender 的 dispatcher（push），还是 receiver 自己（pull），还是经 broker 中转？
2. 人类「反压 / 降低接收方压力」的诉求，**push 模型能否满足**，还是必须 pull / MQ 才有真正的消费方控速？
3. 引入 MQ（T2 / T4）带来的反压收益，是否值得解除 §6.2 + 承担 broker 运维；还是 consumer-pull over DB（T3，复用 Stage 12 claim / lease / `SKIP LOCKED`）就能满足而不破 §6.2？
4. 各候选下，**receiver 端（Stage 12 review MI13-R 暴露的缺失）** 应该如何补齐——被动暴露端点，还是主动拉取？
5. `deliver` 异常重投策略（退避 / attemptCount 上限 / 熔断，§8 deferred）在哪个候选下承载最自然？是否可独立于投递模型裁决？

本评审 **暴露 trade-off，不下选型定论**。§2 给候选、§3 给维度、§4 给投递模型边界、§5 给评分矩阵、§6 给反压根本分析（复议 C3 dispatcher-push）、§7 给小结与非裁决推荐 + deliver 重投策略子项。

## 2. 候选方案（投递模型变体）

| 候选 | 形态 | 一句话定位 | 引 MQ | 破 §6.2 |
|---|---|---|---|---|
| T1 | dispatcher-push over sync RPC | sender dispatcher claim 后主动 HTTP / gRPC 调 receiver 端点 | 否 | 否 |
| T2 | dispatcher-push over broker | sender dispatcher claim 后发 broker topic，broker push consumer | 是 | 是（需解除） |
| T3 | consumer-pull（DB-backed） | receiver 主动从 outbox / inbox 拉取，claim 后处理 | 否 | 否 |
| T4 | C3 + broker hybrid（pull over broker） | outbox 持久 + broker 承载投递 + consumer 拉 broker（lag 流控） | 是 | 是（需解除） |

> 产品名（HTTP / gRPC / Kafka / NATS 等）仅为刻画形态的示例，不代表选型。broker 产品选择 deferred 到 H2/H3。

### 2.1 T1：dispatcher-push over sync RPC

**形态：** `ForwardingDispatcherWorker`（sender 侧，Stage 10/11 已落地）claim outbox 记录后，`ForwardingDeliveryPort.deliver(record, now)` 的真实实现发起同步 RPC（HTTP / gRPC）调用 receiver 暴露的接收端点；receiver 同步返回 outcome，worker 据之 `markAcked` / `scheduleRetry` / `moveToDlq`。这是当前 C3 骨架（sender-side dispatcher + 抽象 delivery port）的最直接落地。

| 维度 | 评估 |
|---|---|
| 投递触发 | sender dispatcher 主动（push）；投递速率由 dispatcher 控制。 |
| 反压能力 | **弱**：receiver 被动接收，压力大时只能让 RPC 超时 / 主动拒绝（HTTP 429 / gRPC `RESOURCE_EXHAUSTED`），由 dispatcher 映射为 `backpressure_rejected` / `receiver_unavailable` → `scheduleRetry` 退避。这是**发送方降速**，不是消费方控速；退避是全局策略，难 per-receiver 精细节流。 |
| 速率解耦 | **弱**：投递 / 消费同步耦合在 RPC 调用上，receiver 处理慢直接拖慢 dispatcher 的 deliver（受 `delivery_timeout` / lease TTL 约束）。 |
| 引 MQ / §6.2 | 否；不破 §6.2。 |
| 复杂度 / 新依赖 | **低**：只新增 HTTP / gRPC client 依赖 + delivery port 的 RPC 实现；receiver 暴露端点。无新基础设施。 |
| 与 Stage 12 契合 | **高**：deliver 实现填进现有 `ForwardingDeliveryPort` 抽象，持久层（outbox / claim / lease）完全不动；`ForwardingDispatcherWorker` 不改。 |
| receiver 补齐 | receiver 暴露 HTTP / gRPC 接收端点，收到 envelope 后写 inbox（`receive` → 业务处理 → `markConsumed` / `markRejected`）。 |
| durable / ordering / tenant | 复用 C3 DB 行级（Stage 12 RLS + `tenant_id`）；ordering 维持 §4 per-route index；无 broker 分区问题。 |
| deliver 重投承载 | 自然：`scheduleRetry` + attemptCount + 退避（§7 子项）；熔断需额外组件（per-routeHandle 状态）。 |

### 2.2 T2：dispatcher-push over broker

**形态：** sender dispatcher claim outbox 后，deliver 实现把消息发到 broker topic（routeHandle 映射 topic / partition key），broker 异步 push 给 consumer；consumer（receiver）处理完回写 outcome（经反向通道或 outbox 的 `markAcked`）。

| 维度 | 评估 |
|---|---|
| 投递触发 | sender dispatcher push 到 broker；broker push consumer。投递由 dispatcher + broker 共同驱动。 |
| 反压能力 | **中-强**：broker lag（consumer 落后 offset）/ quota / credit 流控，consumer 按能力消费，天然表达接收方压力。 |
| 速率解耦 | **强**：broker 缓冲，producer / consumer 速率解耦。 |
| 引 MQ / §6.2 | **是**：需 concrete broker client + 集群运维；**需 H2/H3 解除 §6.2**（本评审不自行解除）。 |
| 复杂度 / 新依赖 | **高**：broker client（kafka-clients / nats / amqp 等）+ adapter 子包 + broker 运维（集群 / 容量 / 监控 / 灾备）。 |
| 与 Stage 12 契合 | **中**：outbox 仍持久，但 claim 后的 deliver 改为 produce broker；需新增 broker adapter；ack 回写仍可用 lease-guarded `markAcked`（经 reverse 通道或 consumer 侧）。 |
| receiver 补齐 | receiver 作 broker consumer（订阅 topic / consumer-group），消费后写 inbox + 回 outcome。 |
| durable / ordering / tenant | broker 复制持久；per-partition ordering 强；tenant 隔离走 per-tenant topic / partition / namespace（强但需 broker 配合）。 |
| deliver 重投承载 | broker 侧重试（redelivery）+ outbox `scheduleRetry` 双层；退避 / 熔断可在 broker 配置或 client。 |

### 2.3 T3：consumer-pull（DB-backed）—— 反压关键候选

**形态：** receiver 侧主动拉取：receiver 作为另一个 `leaseOwner` 调 `claimDue`（或等价的 inbox 拉取），claim 到待处理记录后本地处理，完成回写 outcome。投递由 **receiver 的拉取节奏**驱动，不是 sender 的 push。有两个子形态，取决于 receiver 拉的是 outbox 还是 inbox：

- **T3a（receiver 共享 outbox claim）**：receiver 直接 claim sender 写入的 outbox 表（跨 service）。outbox 成为跨 service 共享队列，sender 的 `ForwardingDispatcherWorker` 退化为「只入队」（enqueue + 接收 ack 回写），deliver / mark 由 receiver 侧 worker 承担。**复用 Stage 12 的 `claimDue` + `SKIP LOCKED` + lease 语义零改动**，但改变了 outbox 的「发送方专有」语义。
- **T3b（sender push 到 inbox，receiver pull inbox）**：sender dispatcher 把 outbox 记录「投递」为 receiver 的 inbox 行（`receive`），receiver 侧 worker 从 inbox claim 待消费记录（需 inbox 增 claim / lease 语义，当前 inbox 仅有 `receive` / `markConsumed` / `markRejected`）。outbox 保持发送方语义，inbox 升级为可拉取队列。

| 维度 | 评估 |
|---|---|
| 投递触发 | receiver 主动（pull）；投递速率由 receiver 控制。 |
| 反压能力 | **强**：receiver 按自身处理能力决定拉取节奏，压力大就拉得慢 / 不拉，天然 per-receiver 节流，无需额外组件。 |
| 速率解耦 | **强**：outbox 持久缓冲，sender 入队 / receiver 消费速率解耦（同 broker 的解耦效果，但承载物是已落地的 Postgres）。 |
| 引 MQ / §6.2 | **否**：DB poll + `SKIP LOCKED` 承载，不引 MQ，不破 §6.2。 |
| 复杂度 / 新依赖 | **低-中**：T3a 几乎零新增（复用 Stage 12 claim）；T3b 需给 inbox 加 claim / lease 语义（仿 outbox §7.1/§7.2，已在 Stage 9/12 验证过的模式）。无新基础设施。 |
| 与 Stage 12 契合 | **高**：T3a 直接复用 outbox claim / lease / `SKIP LOCKED` / RLS；T3b 把同一套 lease-safe 模式（Stage 9）移植到 inbox。持久层范式一致。 |
| receiver 补齐 | receiver 内置 pull worker（`ForwardingDispatchLoop` + `TickSource` / `IdleStrategy` 骨架可直接复用），claim → 本地处理 → `markConsumed` / `markAcked`。 |
| durable / ordering / tenant | 复用 C3 DB 行级（Stage 12）；T3a 的跨 service 共享 outbox 需明确 ordering / claim 边界（避免多 receiver 抢同一 route 乱序）；tenant 隔离不变（RLS）。 |
| deliver 重投承载 | 自然：`scheduleRetry` + attemptCount + 退避（与 push 同）；receiver claim 失败 / 处理失败留 DISPATCHING 待 reclaim（Stage 9/11 已验证）。 |

> T3 的关键 trade-off：**改变了「谁是 dispatcher」**。当前 C3 骨架假设 sender-side dispatcher（claim → deliver → mark）。T3 把投递驱动力从 sender 转移到 receiver。这不破坏 C3 的持久化层（outbox / inbox / lease 都在），但 `ForwardingDispatcherWorker` 的归属和部署位置需重新界定（sender 侧「入队 worker」+ receiver 侧「消费 worker」）。这是 H2/H3 在 T3 路径上要裁决的架构调整点。

### 2.4 T4：C3 + broker hybrid（pull over broker）

**形态：** outbox 仍由 sender 持久化（durable / 审计 / replay），但投递通道是 broker；consumer 从 broker pull（而非 broker push），利用 broker lag 流控 + C3 的 durable 审计双层。是最强反压 + 最强 durable 的组合，也是最复杂。

| 维度 | 评估 |
|---|---|
| 投递触发 | sender produce broker；receiver pull broker。 |
| 反压能力 | **强**：broker lag 流控 + receiver 按能力 pull。 |
| 速率解耦 | **强**：outbox 持久 + broker 缓冲双层解耦。 |
| 引 MQ / §6.2 | **是**：需 broker + outbox 双 store；**需 H2/H3 解除 §6.2**。 |
| 复杂度 / 新依赖 | **最高**：outbox store + broker client + 跨层 ordering / ack 协调（Stage 5 candidates §2.5 已评 C5 hybrid 复杂度最高）。 |
| 与 Stage 12 契合 | **中**：outbox 持久层保留，但 deliver 改为 broker produce，consumer 侧 pull；跨层一致性 / ordering 是主要工程负担。 |
| receiver 补齐 | receiver 作 broker consumer（pull 模式）+ 写 inbox + 回 outcome。 |
| durable / ordering / tenant | 最强 durable（outbox + broker）；跨层 ordering 难（需因果关系 / 全局序号）；tenant 需跨层一致。 |
| deliver 重投承载 | 双层重投（broker + outbox），强但复杂。 |

## 3. 评审维度（8 项）

下列 8 维度是 Stage 13 计划 §4 切片 1 的评审口径，每个候选在 §2 已逐项评估：

1. 投递触发方（push by sender / pull by receiver / 经 broker）。
2. 反压能力（消费方控速）——**核心维度**，直接回应人类「降低接收方压力」诉求。
3. 投递 / 消费速率解耦。
4. 是否引 MQ / 是否需解除 §6.2（始终不得项的边界）。
5. 复杂度 / 新生产依赖 / 新基础设施运维。
6. 与 Stage 12 已落地持久层（claim / lease / `SKIP LOCKED` / RLS）的契合度。
7. receiver 端（MI13-R 缺失）的补齐方式。
8. `deliver` 异常重投策略（退避 / attemptCount 上限 / 熔断）的承载自然度。

## 4. 投递模型边界定义

不选产品前，先定义投递模型边界（Stage 5 candidates §4 的对应物，但聚焦 deliver 这一跳）。sender / receiver / backpressure 的语义对所有候选一致（由 Stage 4 契约固定），差异集中在 **dispatcher 归属与投递触发方**：

| 边界 | T1 push RPC | T2 push broker | T3 pull DB | T4 hybrid |
|---|---|---|---|---|
| sender | 一致：构造 envelope，写 outbox | 同 | 同 | 同 |
| dispatcher 归属 | sender 侧（claim → deliver → mark） | sender 侧（claim → produce broker） | **receiver 侧**（pull claim → 处理 → mark）；sender 侧退化为入队 | sender 侧（produce broker）+ receiver 侧（pull broker） |
| 投递触发 | sender dispatcher | sender dispatcher + broker | receiver pull worker | broker + receiver pull |
| store | outbox（Stage 12） | outbox + broker | outbox（T3a 共享 / T3b 发送方）+ inbox（T3b 可拉取） | outbox + broker |
| receiver | 被动暴露 RPC 端点 | broker consumer | 内置 pull worker | broker consumer（pull） |
| backpressure 表达 | RPC 拒绝 / 超时 → `backpressure_rejected` | broker lag / quota | receiver 拉取节奏（隐式） | broker lag + receiver 节奏 |

边界小结：**所有候选共享 Stage 4 的 sender / receiver / backpressure 契约语义**；投递模型差异集中在 **dispatcher 归属（sender vs receiver）** 和 **是否经 broker**。这意味着投递模型选择不破坏架构语义，只改变「deliver 怎么发生」与「谁驱动」。

## 5. 候选方案评分矩阵

评分用 **强 / 中 / 弱** 暴露 trade-off，**不定论**；权重标注 Stage 13 计划给定的高 / 中。反压（高权重，直接回应人类诉求）是核心分水岭。

| 维度 | 权重 | T1 push RPC | T2 push broker | T3 pull DB | T4 hybrid |
|---|:---:|:---:|:---:|:---:|:---:|
| 反压能力（消费方控速） | 高 | 弱（被动拒绝 + 发送方降速） | 中-强（broker lag 流控） | **强（receiver 控拉取节奏）** | 强（lag + pull） |
| 速率解耦 | 高 | 弱（RPC 同步耦合） | 强（broker 缓冲） | **强（DB 持久缓冲）** | 强（双层） |
| 引 MQ / 破 §6.2 | 高 | 否 | 是（需解除） | **否** | 是（需解除） |
| 复杂度 / 新依赖 | 高 | 低 | 高（broker 运维） | **低-中（T3a 零新增 / T3b 仿 outbox）** | 最高 |
| 与 Stage 12 契合 | 中 | 高（填 delivery port） | 中（加 broker adapter） | **高（复用 claim / lease / SKIP LOCKED）** | 中 |
| durable / ordering / tenant | 中 | 复用 DB | 强（broker 分区） | 复用 DB（T3a 共享需定 ordering 边界） | 最强但跨层难 |
| receiver 补齐自然度 | 中 | 暴露端点 | broker consumer | **内置 pull worker（复用 dispatch loop 骨架）** | broker consumer |
| deliver 重投承载 | 中 | 自然 | 双层 | 自然（与 push 同） | 双层 |

矩阵读法：

- **反压是核心分水岭**：T1（弱）与其余三者（强）之间是质的差距——人类「降低接收方压力」诉求在 T1 下只能靠发送方退避，无法真正让 receiver 控速。
- **反压与不破 §6.2 的交集只有 T3**：T2 / T4 反压强但需引 MQ（解除 §6.2）；T1 不破 §6.2 但反压弱；**T3 是唯一同时满足「强反压」+「不破 §6.2」+「低复杂度」的候选**。
- **与 Stage 12 契合**：T1 / T3 都高（T1 填 delivery port，T3 复用 claim / lease）；T2 / T4 需新增 broker adapter 层。
- **T3 的代价是 dispatcher 归属转移**（§2.3 / §4）：C3 骨架假设 sender-side dispatcher，T3 把驱动力移到 receiver——这是 H2/H3 在 T3 路径上要裁决的架构调整，不破坏持久化层。

## 6. 反压根本分析（复议 C3 dispatcher-push）

这是 decision §8 预告的「复议 C3 dispatcher-push 模型」核心。三类模型的控速机理：

### 6.1 push 模型（T1）的反压缺陷

dispatcher-push 的本质：**投递速率由发送方控制**。receiver 是被动的，没有主动节流入口：

- receiver 压力大时，唯一信号是 RPC 超时 / 主动拒绝 → dispatcher 映射为 `backpressure_rejected` / `receiver_unavailable` → `scheduleRetry` 退避重试。
- 这是**发送方降速**（dispatcher 退避后少投），不是**消费方控速**（receiver 无法主动说「我现在只能处理 N 条/秒」）。
- 退避是全局策略（`DispatchLeasePolicy` / retry policy），难做到 per-receiver、per-routeHandle 的精细节流。
- 在同步 RPC 下，receiver 处理慢直接拖慢 dispatcher 的 deliver（受 `delivery_timeout` / lease TTL 约束），dispatcher worker 吞吐被 receiver 速度硬性拉低。

**结论**：push 模型对「降低接收方压力」的回应是间接的（靠 receiver 拒绝 + sender 退避），不满足「消费方主动控速」的反压诉求。若人类诉求是硬需求，T1 不可接受。

### 6.2 pull 模型（T3）的反压优势

consumer-pull 的本质：**投递速率由接收方控制**：

- receiver 按自身处理能力决定 claim 节奏（`claimDue(limit)` 的 `limit` + poll cadence 即天然节流阀）。
- receiver 压力大就拉得慢 / 不拉，无需任何额外组件，per-receiver 精节流。
- outbox 持久缓冲（Stage 12 已落地），sender 入队与 receiver 消费速率解耦——**与 broker 的解耦效果等价，但承载物是已落地的 Postgres，不引 MQ**。
- 复用 Stage 12 的 `claimDue` + `SKIP LOCKED` + lease：receiver 就是另一个 `leaseOwner`，多 receiver 实例并发抢不重复（`SKIP LOCKED` 已验证）。

**结论**：pull 模型满足「消费方主动控速」，且复用已验证的持久化层，不破 §6.2。这是对人类反压诉求的最直接回应。

### 6.3 broker 模型（T2 / T4）的反压与代价

broker 的反压来自 broker lag（consumer 落后 producer 的 offset 差）/ quota / credit——consumer 按能力消费，强且成熟。但代价：

- 引入 concrete broker client（kafka-clients / nats / amqp 等）+ 集群运维（容量 / 监控 / 灾备）。
- **需 H2/H3 解除 §6.2**（始终不得项的「不引入 broker / MQ」），并在 §3（decision §3 拒 C4）与 §6.2 双重护栏下重新裁决——这不是工程决定，是架构治理决定。
- Stage 5 已评 C4 / C5 的运维复杂度为「弱」（production ops）。

### 6.4 关键张力结论

人类「基于 MQ 获反压」的诉求，**经本评审拆解后，诉求内核是「消费方控速 / 速率解耦」，而 MQ 只是满足该内核的载体之一**。consumer-pull over DB（T3）以不破 §6.2、低复杂度、复用 Stage 12 的方式同样满足该内核：

- **若反压是硬需求**：T3（pull over DB，不破 §6.2）应作为首选；T2 / T4（broker）仅在 T3 的 DB-poll 吞吐 / 延迟无法满足、且团队明确承担 broker 运维、且 H2/H3 解除 §6.2 后才考虑。
- **若反压非硬需求**（投递量可控、receiver 处理稳定）：T1（push RPC）最简单，填进现有 `ForwardingDeliveryPort` 即可，持久层零改动。

这是 Stage 13 提交给 H2/H3 的核心判断：**反压诉求不必等价于引 MQ**。

## 7. 评审小结与非裁决推荐

### 7.1 关键 trade-off

- **反压 ↔ §6.2 边界**：强反压的「不破 §6.2」路径只有 T3（pull over DB）；broker 路径（T2 / T4）反压更强但需解除 §6.2。
- **反压 ↔ dispatcher 归属**：T1 / T2 保持 sender-side dispatcher（与当前骨架一致）；T3 / T4 把驱动力移到 receiver（需架构调整，不破坏持久层）。
- **复杂度**：T1 最低；T3 低-中（T3a 零新增 / T3b 仿 outbox）；T2 高；T4 最高。
- **与 Stage 12 契合**：T1 / T3 高；T2 / T4 中（需 broker adapter 层）。

### 7.2 推荐进入 H2/H3 的候选（非裁决性质）

本评审用强 / 中 / 弱暴露 trade-off，补一个 **非裁决性质** 的推荐，帮助 H2/H3 快速聚焦（性质同 candidates §6.2，非最终裁决）：

| 推荐 | 候选 | 说明 |
|---|---|---|
| 默认推荐候选（反压优先） | **T3 consumer-pull over DB** | 满足人类「消费方控速」反压内核，且不破 §6.2、低复杂度、复用 Stage 12 claim / lease / `SKIP LOCKED` / RLS。代价是 dispatcher 归属从 sender 转到 receiver（架构调整，不破坏持久层）。 |
| 简化备选（低反压需求） | T1 dispatcher-push over sync RPC | 最小落地，填进现有 `ForwardingDeliveryPort`，持久层零改动；反压弱（仅发送方退避），仅适合投递量可控 / receiver 处理稳定的场景。 |
| 暂不推荐（需解除 §6.2） | T2 / T4（broker） | 反压强但引 MQ + broker 运维 + 需 H2/H3 解除 §6.2；仅在 T3 的 DB-poll 吞吐 / 延迟不满足且团队承担 broker 运维时由 H2/H3 重启评审。 |

**这不是架构裁决**，只是帮助 H2/H3 聚焦；最终 push / pull / MQ 裁决由 H2/H3 在本 review packet 后做。施工智能体在裁决前不得写 transport 生产代码（见 §0 禁止范围、decision §6.2）。

### 7.3 rejection criteria（不可接受条件）

| 候选 | 不可接受条件（命中即不应选该候选） |
|---|---|
| T1 push RPC | 反压 / 降低接收方压力是硬需求（push 无消费方控速）。 |
| T2 push broker | 团队不承担 broker 运维，或当前阶段禁止解除 §6.2 引 MQ。 |
| T3 pull DB | DB-poll 的吞吐 / 延迟无法满足目标投递量级（需量化）。 |
| T4 hybrid | 处于最小切片阶段，复杂度（双层 store + 跨层 ordering）超过收益。 |

### 7.4 deliver 异常重投策略子项（独立于投递模型）

§8 deferred 的「deliver 异常重投策略（attemptCount 递增 / 退避 / 熔断）」**可独立于投递模型裁决**——无论 push / pull / MQ，都需要这套策略。建议作为独立子项先行（不阻塞 push/pull 裁决）：

- **指数退避**：`nextAttemptAt = now + base * 2^attempt + jitter`（base + jitter 防惊群）；落点建议与 `DispatchLeasePolicy` 同层的 retry policy 端口，或状态机参数化。
- **attemptCount 上限**：达到上限 → DLQ 或 EXPIRED（`ForwardingFailureCode` 分类已就绪，retryable 耗尽走 DLQ，Stage 9 MI9-004）。
- **熔断**：某 `routeHandle` 持续 `receiver_unavailable` → circuit break，暂停该 route 的投递（需 per-route 状态，T1 push 下尤其需要；T3 pull 下 receiver 不拉即天然熔断）。
- **续约真实耗时验证**（MI11-001 端到端）：接真实 deliver（Stage 14+）后验证耗时驱动续约，deferred 依赖投递模型裁决 + 真实 deliver 落地。

落点：retry policy 端口（`forwarding.spi` 或 `forwarding.runtime`，纯 Java，仿 `DispatchLeasePolicy`），由 dispatcher worker（T1 / T2）或 receiver worker（T3）注入消费。

### 7.5 本评审不下定论的决策（deferred 到 H2/H3）

- 最终投递模型裁决（T1 / T2 / T3 / T4 之间，含 T3 的 T3a / T3b 子形态）。
- 若选 T3：dispatcher 归属转移的架构调整（sender 入队 worker + receiver 消费 worker 的部署 / 边界）。
- 若选 T2 / T4：broker 产品选择（Kafka / NATS / RocketMQ / RabbitMQ）+ §6.2 / §3 解除裁决。
- DB-poll（T3）的吞吐 / 延迟量化（是否满足目标投递量级，决定 T3 是否被 rejection criteria 命中）。
- 具体退避 base / jitter、attemptCount 上限、熔断阈值的参数化（运维化阶段）。

### 7.6 边界护栏（无论选哪个候选都必须守住）

- forwarding envelope 始终通过 `routeHandle` 绑定投递目标，不绕过 Stage 3 discovery 直接用物理 endpoint。
- envelope 不携带 payload body / token stream / Task execution state（`payloadRef` 条件必填，MI5-003 方案 B）。
- `agent-bus` 不写 Task execution state，不改变远端 Task lifecycle owner。
- backpressure / timeout / tenant mismatch 用 Stage 4 显式 failure mode 表达，不静默丢消息（`backpressure_rejected` / `receiver_unavailable` / `delivery_timeout`）。
- tenant isolation 延续 R-C.c，禁止跨 tenant fallback（Stage 12 RLS 纵深防御已落地）。
- **§6.2 始终不得项**：本评审不自行解除；引 MQ 需 H2/H3 裁决。

## 8. 后续

本评审是 Stage 13 的裁决输入。最终 push / pull / MQ 裁决由 H2/H3 在本 review packet 后做：

- **若裁决 T1 / T3（不破 §6.2）**：Stage 14 落地真实 transport 实现（T1 填 `ForwardingDeliveryPort` RPC 实现 + receiver 端点；T3 落 receiver pull worker + 可能的 inbox claim 语义）+ deliver 重投策略子项 + receiver 端（MI13-R）补齐。不解除 §6.2。
- **若裁决 T2 / T4（引 MQ）**：需先 H2/H3 解除 §6.2 + broker 产品裁决，再 Stage 14+ 落 broker adapter；ArchUnit 纯度规则需重新精确化（broker client 圈进 transport 子包，仿 Stage 12 对 Spring/JDBC 的处理）。
- **deliver 重投策略子项**（§7.4）可独立先行，不阻塞投递模型裁决。
- receiver 端（MI13-R）/ 调度运维化（MI13-O）随所选投递模型在 Stage 14+ 补齐。

相关文档：

- Stage 13 计划：[`agent-bus-stage12-review-and-stage13-plan`](../delivery-projections/agent-bus-stage12-review-and-stage13-plan.md)。
- 运行态承载裁决：[`agent-bus-forwarding-runtime-decision`](agent-bus-forwarding-runtime-decision.md)（C3 = `adopted-c3`；§8 transport 议题段）。
- 运行态承载候选评审：[`agent-bus-forwarding-runtime-candidates`](agent-bus-forwarding-runtime-candidates.md)（Stage 5，C1-C5）。
- 持久化 L2：[`forwarding-persistence`](../../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)（Stage 8-12）。
- 运行态 ICD：[`ICD-Agent-Bus-Forwarding-Runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)。
- 设计态契约：[`ICD-Agent-Bus-Forwarding`](../../05-contracts/human-readable/ICD-agent-bus-forwarding.md)（HD4）。
- L1 入口：[`agent-bus L1 README`](../../../../../architecture/L1-High-Level-Design/agent-bus/README.md)。
