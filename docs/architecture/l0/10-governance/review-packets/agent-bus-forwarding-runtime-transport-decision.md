---
artifact_type: a2d_review_packet
version: "agent-bus-forwarding-runtime-transport-decision"
status: adopted-t4
source_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage24-review-and-stage25-plan.md"
source_decision: "docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md"
source_candidates: "docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md"
source_t3_assessment: "docs/architecture/l0/10-governance/review-packets/agent-bus-t3-consumer-pull-landing-assessment.md"
source_icd: "docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md"
target_module: agent-bus
---

# agent-bus 运行态转发 transport / 投递模型最终裁决（Stage 25）：T4 hybrid（outbox + broker）

## 0. 裁决边界与性质

本 packet 是 **Stage 25 transport / 投递模型最终裁决**，回答 Stage 13（[`transport-candidates`](agent-bus-forwarding-runtime-transport-candidates.md)）deferred 给 H2/H3 的核心未决项：**C3 的投递到底用 push 还是 pull、是否引 MQ？** Stage 13 比较了 T1/T2/T3/T4 × 8 维度，核心结论「反压内核=消费方控速，MQ 只是载体之一」，但把最终 push/pull/MQ 裁决明确 deferred 给 H2/H3；Stages 15-24 在 T1 push 临时 PoC 上端到端验证了三条生命线 + 三终态 + payloadRef + RLS（200 tests green），为本次裁决提供了事实基础。

本 packet 是 **裁决阶段，性质同 Stage 5 / 6 / 13 / 24 治理段**：

- **不写生产代码**：broker 接线 / relay adapter / receiver consumer / 状态机扩展是 Stage 26+ 实现，本阶段仅产出决策 + 切片计划。
- **不裁决 broker 产品**：Kafka / RocketMQ / RabbitMQ 等具体产品 final 选型 deferred Stage 26 PoC（真实 DMS broker 端到端 produce/pull/retry/DLQ/租户隔离实测后定），本 packet §5 给出倾向（RocketMQ）与矩阵，不锁定。
- **裁决权属**：本 packet 行使 H2/H3 裁决权，**解除 [`decision §6.1`](agent-bus-forwarding-runtime-decision.md) 第 1 项「引入具体 broker / MQ client」禁令**（详见 §2），同时**守 [`decision §6.2`](agent-bus-forwarding-runtime-decision.md) 第①项精神**（不反向定义 broker-agnostic 语义）及第②③④⑤项全部不变。

建立在已稳定的 C3 底座之上（运行态承载候选 = C3 database outbox/inbox，Stage 5 裁决、Stage 7-24 落地）。本 packet 裁决的是 **C3 之内的投递模型**（deliver 怎么发生），**不是重新评审运行态承载**：C3 outbox/inbox 表 + claim/lease/RLS 全部保留，T4 在其上加 broker 投递通道。

## 1. 裁决项表

| # | 裁决项 | 裁决 | 依据 / 落点 |
|---|---|---|---|
| D1 | 投递模型 | **T4 hybrid（outbox + broker），`adopted-t4`** | §2 论证；§3 数据流 |
| D2 | §6.1 第 1 项「引 concrete broker」禁令 | **解除**（圈进 `transport.broker` 子包） | §2.3；[`decision §6.1 Stage 25 更新块`](agent-bus-forwarding-runtime-decision.md) |
| D3 | §6.2 始终不得项 | **精神不变**（第①项不反向定义语义、第②③④⑤项 payload body/token stream/Task state/跨租户 R-C.c/registry 全部不变） | §2.3；§10 护栏 |
| D4 | broker 产品选型 | **deferred Stage 26 PoC**（倾向 RocketMQ） | §5 |
| D5 | ack 语义 | **模型 B（ack-after-consume，至少一次）** | §3；状态机扩展 Stage 27 |
| D6 | 重投主导权 | **agent-bus retry policy（Stage 14）主导，broker 原生 retry 配 off** | §3；§10 |
| D7 | T1 push PoC | **保留**（`A2aForwardingDeliveryPort`），T4 上线后 routeHandle 级别共存/灰度切换 | §7；Stage 30 |
| D8 | 租户隔离 | **broker 侧 L1-L5 纵深**（topic-per-tenant 首选 + header 校验 + consumer-group 隔离 + inbox RLS + 应用层显式拒绝） | §6 |

## 2. 裁决论证

### 2.1 为何 pull 是目标态（反压内核）

[`transport-candidates §6`](agent-bus-forwarding-runtime-transport-candidates.md) 已论证：**反压诉求的内核是「消费方控速 / 速率解耦」，MQ 只是载体之一**。push 模型（T1/T2）是反应式背压 —— sender 主导投递速率、receiver 被动接受，控速只能靠 receiver 反向施压（拒绝/限流），天生弱反压且易雪崩。pull 模型才是强反压的目标态：receiver 主动按自身容量拉取（控速），sender 只负责排队，速率天然解耦。

### 2.2 为何是 T4 而非 T1 / T2 / T3

| 候选 | 模型 | 结论 | 关键障碍 |
|---|---|---|---|
| T1 push-RPC | push，sender 直连 receiver | **临时 PoC（Stages 15-24 已用）** | 反应式背压，无法升级 receiver 控速；非目标态 |
| T2 push-broker | push，sender→broker→receiver（broker 推） | **不采用** | 仍是 push（receiver 被动），broker 只换载体不换模型，反压内核不变却付了 broker 复杂度 |
| T3 consumer-pull-over-DB | pull，receiver 轮询 outbox/inbox 表 | **内核正确但不采用** | [`t3-assessment`](agent-bus-t3-consumer-pull-landing-assessment.md) 揭示两大落地障碍（见下） |
| T4 C3+broker hybrid | pull，receiver 从 broker 消费 | **采用（adopted-t4）** | MQ 本质是 pull + 一次性解决 T3 两大障碍 |

**T3 的两大落地障碍**（[`t3-assessment`](agent-bus-t3-consumer-pull-landing-assessment.md)）：

1. **生产 scheduler 从无到有**：`ForwardingDispatchLoop.TickSource`（[`Stage 10`](agent-bus-forwarding-runtime-decision.md)）生产零驱动，relay / receiver 的轮询循环需要生产级 scheduler（定时 tick 或事件驱动），从无到有建一个调度器撞 [`decision §6.1`](agent-bus-forwarding-runtime-decision.md) 第 3 项（不引 scheduler）。
2. **模块依赖反转**：receiver 要 pull outbox/inbox 表，意味着 receiver 访问 agent-bus 持久层 —— 撞 `AgentBusDependencyBoundaryTest`（agent-bus 此前完全隔离，[`Stage 17`](agent-bus-forwarding-runtime-decision.md) 才首次跨模块 test-scope 依赖）。

**T4 一次性解决两大障碍**：

- **MQ 本质是 pull**（Kafka consumer `poll()` / RocketMQ pull consumer / broker prefetch = 与 T3 claim+`SKIP LOCKED` 同内核）—— receiver 从 broker pull 即消费方控速，反压内核与 T3 等价，但无需 receiver 访问 agent-bus 表。
- **MQ client 自带 consumer loop** —— relay produce / receiver consume 的循环由 broker client SDK 内置（`poll()` 循环、长连接、rebalance），**不需自建 scheduler**，绕开障碍①、不需解除 §6.1 第 3 项。
- **broker 独立中介** —— receiver 从 broker pull，**完全不碰 agent-bus 表**，绕开障碍②、不撞 `AgentBusDependencyBoundaryTest`。模块依赖在 broker 处解耦：sender 侧（agent-bus）只 produce，receiver 侧（消费方）只 pull，二者经 broker 间接，无直接代码依赖。

### 2.3 §6.2 解除论证（解除什么 / 守什么）

**解除**：[`decision §6.1`](agent-bus-forwarding-runtime-decision.md) 第 1 项「引入具体 broker / MQ client」—— 这是 Stage 7 阶段许可边界（可被后续 Stage 解除），类比 Stage 12 解除第 2 项（JDBC）、Stage 15 解除第 4 项（投递绑定）。concrete broker client 圈进 `com.huawei.ascend.bus.forwarding.runtime.transport.broker` 子包。

**守（§6.2 始终不得项精神不变）**：

- **第①项「不反向定义 broker-agnostic 语义」精神保持** —— broker 的 partition / offset / consumer-group / topic 等产品概念**不得泄漏到 `transport.broker` 子包之外**；envelope / routeHandle / lease / retry policy / DLQ / circuit breaker 治理语义仍由现有 SPI 端口（`ForwardingDeliveryResult` / `ForwardingRetryPolicy` / `ForwardingCircuitBreaker`）表达，[`forwarding-persistence §5`](../../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md) 立柱不动。broker adapter 是新 `ForwardingDeliveryPort` 实现，不是新治理语义来源。
- **第②③④⑤项全部不变**：不放 payload body / token stream（payloadRef 走 broker message header/attribute）；不写 Task execution state；不跨租户 fallback（R-C.c，broker 侧 §6 纵深）；registry 不变 agent 定义仓库。

**ArchUnit + 文本扫描双豁免**（类比 Stage 15 `transport.a2a` 圈 `org.a2aproject`、[`AgentBusForwardingSpiPurityTest`](../../../../../agent-bus/src/test/java/com/huawei/ascend/bus/forwarding/runtime/transport/) 范式）：broker client 圈进 `transport.broker` 子包，新增 ArchUnit 规则 `forwarding_core_does_not_import_broker_client_outside_broker_adapter` + §6.2 文本扫描把 `transport.broker` 子树排除。**由 Stage 26 实施时落地，Stage 25 仅在文档标注授权**。

### 2.4 T4 ≠ §3 拒绝的 C4

[`decision §3`](agent-bus-forwarding-runtime-decision.md) 拒绝的 **C4 external broker** = 抛弃 outbox、纯用 external broker 作承载（broker 即持久层）。**T4 ≠ C4**：T4 **保留 Stage 12 outbox**（事务一致 / durable / 审计 / RLS）作 durable 层，broker **仅作投递通道**（outbox record → broker produce → receiver pull → 反向 ack → outbox markAcked）。C4 的拒绝理由（运维过重、抛弃 C3 收益）对 T4 不成立 —— T4 拿走 broker 的 pull 反压收益，但保留 outbox 的全部持久化收益。

## 3. T4 hybrid 数据流（5 跳）

```
① Sender 业务事务
   enqueue envelope → outbox(PENDING)            [withTenant + RLS, Stage 24]

② Relay worker（sender 侧，agent-bus）
   claimDue → outbox(DISPATCHING, SKIP LOCKED §7.1)
   → produce broker
       topic        = routeHandle → ForwardingEndpointResolver 映射（HD4 opaque）
       message body = 仅 routing descriptor
       message hdr  = payloadRef + tenantId + messageId  （不放 payload body, §6.2②）

③ Receiver consumer（pull，消费方侧）
   poll broker（控速 = 反压内核）
   → inbox.receive(RECEIVED, dedup by tenantId+messageId+consumerServiceId)
   → 业务处理

④ Ack 回路（模型 B: ack-after-consume）
   receiver 处理成功 → 手动 commit offset（enable.auto.commit=false）
   → 反向 ack 通道通知 relay
   → relay markAcked（outbox ACKED, lease-guarded §7.2）   [真至少一次]
   receiver 处理失败 → 不 commit → broker redeliver → relay 侧 retry policy 主导

⑤ 终态
   ACKED（receiver 确认）/ DLQ（retry exhausted / REMOTE_TASK_FAILED）/ EXPIRED（超 deadline）
   翻译为 ForwardingDeliveryResult（与 Stage 15 A2A 终态映射 + Stage 18 同范式）
```

**ack 语义裁决方向 = 模型 B（ack-after-consume）**：relay produce broker 后 outbox 留 DISPATCHING（长 lease 或新增中间态 AWAITING_ACK），receiver 消费完经反向 ack 通道通知 relay markAcked —— ACKED 真反映 receiver 处理完成（至少一次）。**状态机是否加第 7 态 AWAITING_ACK 是 Stage 27 设计决策**（本 packet 仅定方向）。

**避免双重重投**：agent-bus retry policy（Stage 14，transport-agnostic）**主导**重投时机与退避；broker 原生 retry **配 off**（consumer `enable.auto.commit=false`，手动 commit only after receiver 处理成功）。broker 侧的 redelivery（至少一次必然）由 inbox dedup（tenantId+messageId+consumerServiceId）幂等吸收，不触发 agent-bus retry 计数。broker outcome 翻译为 `ForwardingDeliveryResult`（ACKED / RETRY_SCHEDULED / DLQ / EXPIRED），与 Stage 15 `A2aForwardingDeliveryPort` 终态映射 + Stage 18 `REMOTE_TASK_FAILED` 同范式。

## 4. 治理层边界（transport.broker 子包，broker 概念不泄漏）

类比 Stage 15 `transport.a2a`（`A2aForwardingDeliveryPort` 圈 `org.a2aproject`），新增 `com.huawei.ascend.bus.forwarding.runtime.transport.broker`：

| 组件 | 职责 | Stage |
|---|---|---|
| `BrokerForwardingRelayPort`（SPI） | relay: outbox record → broker produce | Stage 26 |
| `BrokerForwardingConsumerPort`（SPI） | receiver: broker poll → inbox | Stage 26 |
| `BrokerClientProperties` | broker 连接配置（产品无关抽象） | Stage 26 |
| `<product>ForwardingRelayPort` | relay 实现（产品 Stage 26 PoC 定） | Stage 26 |
| `<product>ForwardingConsumerPort` | receiver 实现 | Stage 26 |

**核心不变量 —— SPI 端口零改动**：`ForwardingDeliveryPort` / `ForwardingDeliveryResult` / `ForwardingRetryPolicy` / `ForwardingCircuitBreaker` / `ForwardingOutboxRecord` 全部不动。broker adapter 是新 `ForwardingDeliveryPort` 实现（relay 形态），消费 Stage 14 retry policy / Stage 16 circuit breaker / Stage 24 RLS。

**broker 产品概念封装映射（不泄漏到 `transport.broker` 之外）**：

| broker 产品概念 | 封装为（broker-agnostic 治理语义） |
|---|---|
| topic | routeHandle（经 `ForwardingEndpointResolver` 映射，HD4 opaque） |
| partition key | messageId hash |
| consumer-group | consumerServiceId（inbox dedup key 之一） |
| offset | adapter 内部 commit（不暴露） |
| broker retry / DLX | `ForwardingDeliveryResult`（RETRY_SCHEDULED / DLQ） |

## 5. broker 选型矩阵（倾向 RocketMQ，final 选型 deferred Stage 26）

| 维度 | RocketMQ（**倾向**） | Kafka | RabbitMQ |
|---|---|---|---|
| 消息模型 | 原生顺序消息 + pull consumer | partition 内有序 + offset pull | 主要 push（AMQP basic.consume） |
| 反压内核（pull） | ✓ pull consumer 原生 | ✓ poll() 原生 | △ push 为主，pull 支持弱（与 pull 目标态张力） |
| 租户分层 | namespace 租户隔离原生 | topic / ACL | vhost |
| retry / DLQ | 原生 retry topic + DLQ | 无原生（需自建） | DLX 但语义不同 |
| 概念收敛（对 broker-agnostic 治理） | 高（topic/consumer-group/retry 概念与 §4 映射直接） | 高 | 中 |
| 华为云托管 | DMS for RocketMQ | DMS for Kafka | DMS for RabbitMQ |
| 至少一次 + 手动 commit | ✓ | ✓（enable.auto.commit=false） | ✓（手动 ack） |

**倾向 RocketMQ 的理由**：① 原生顺序消息对 §3 双层 ordering 一致性（outbox+broker）最友好；② namespace 租户分层与 §6 L1 topic-per-tenant 自然契合；③ 原生 retry/DLQ 与 Stage 14 retry policy + DLQ 概念收敛；④ 华为云 DMS 托管降低运维（回应 [`decision §3`](agent-bus-forwarding-runtime-decision.md) C4 拒绝理由「运维过重」）；⑤ pull consumer 与 T4 反压内核直接对应。

**RabbitMQ 倾向排除**：主要 push 模型与 pull 目标态有张力（push-to-pull 需额外适配）。

**final 选型 deferred Stage 26 PoC**：真实 DMS broker 端到端 produce/pull/retry/DLQ/租户隔离/顺序性实测后定。若 PoC 推翻 RocketMQ 倾向（如团队已有 Kafka 运维栈、或顺序性实测不达标），回更本 packet §5 + [`decision §8 Stage 25 段`](agent-bus-forwarding-runtime-decision.md)。

## 6. 租户隔离纵深（broker 侧，破 §6.2 后必须补强）

Stage 24 RLS 只护 outbox/inbox 表（DB 侧）。T4 下 receiver 从 broker pull，**broker 侧是新攻击面**（broker 无 RLS）。纵深分层（§6.2 第⑤项 R-C.c 在 broker 侧 = L2 header 校验失败必须显式 reject）：

| 层 | 机制 | 说明 |
|---|---|---|
| **L1 topic-per-tenant（首选）** | routeHandle 映射含 tenantId→topic，receiver 只订阅本租户 topic | 物理隔离，最强 |
| L2 header 校验 | 消息 header 强制带 tenantId，poll 后校验 header==consumer tenant，不符→reject 不 commit | §6.2⑤ 显式失败兜底 |
| L3 consumer-group 隔离 | consumer-group 含 consumerServiceId | 防跨消费组串扰 |
| L4 inbox DB 侧 RLS | 复用 Stage 24 `withTenant` | receiver 写 inbox 时 DB 侧纵深 |
| L5 应用层显式拒绝 | `inbox.receive` 前 tenantId 校验，不符抛 `ForwardingFailureCode`（R-C.c） | 不静默 fallback |

## 7. Stage 12-24 资产命运表

| 资产 | 命运 | 说明 |
|---|---|---|
| outbox 表 + claim/lease/SKIP LOCKED + lease-guarded mutation + RLS(Stage 24) + TransactionTemplate | **保留（零改）** | T4 durable 层，relay claim/终态标记复用全套 |
| inbox 表 | **保留（receiver dedup）** | broker 至少一次下 redelivery 必然，inbox dedup（tenantId+messageId+consumerServiceId）是必需幂等键 |
| retry policy（Stage 14） | **保留（主导）** | broker retry 配 off，避免双重重投 |
| circuit breaker（Stage 16） | **保留（relay 侧）/退化（receiver 侧）** | relay produce 失败短路故障 broker route；receiver pull 天然自调速 |
| `A2aForwardingDeliveryPort`（Stage 15, T1） | **保留（临时 PoC，共存/灰度切换）** | Stage 30 routeHandle 级别路由 |
| `ForwardingDispatcherWorker` + DispatchLoop + TickSource | **保留 + 必须解决生产 TickSource 零驱动** | T4 relay/receiver 都需循环驱动；MQ client 自带 consumer loop 绕开自建 scheduler（§2.2），但 relay 侧 claim 循环仍需驱动，Stage 26/28 同次裁决 |
| `ForwardingDeliveryResult`（4 态）+ StateMachine + SPI 端口 | **保留（零改 / 模型 B 或扩展）** | broker adapter 翻译为 result；模型 B 可能扩展 StateMachine 加 AWAITING_ACK（Stage 27） |
| `ForwardingEndpointResolver` | **保留（扩展 topic 映射）** | routeHandle→broker topic 映射（HD4 不破） |

## 8. rejection criteria（命中即回退 T1 / T3）

| # | 准则 | 命中后果 |
|---|---|---|
| R1 | 团队不承担 broker 运维（无 DMS 托管或人力不足） | 回退 T3（守 §6.2，付 scheduler+依赖代价） |
| R2 | 目标投递量级 T3(DB-poll) 已能满足（T4 双层复杂度超收益） | 回退 T3 |
| R3 | 跨层 ordering（outbox+broker）无法保证 | 回退 T1（单层 push）或接受最终一致 |
| R4 | broker 侧租户隔离无法纵深（§6 L1-L5 不达） | 回退 T3（DB RLS 单层）—— §6.2⑤ 不可妥协 |
| R5 | 生产 scheduler 障碍无法解除（relay claim 循环驱动无解） | 回退 T1（已有 PoC） |

**Stage 26 PoC 必须验证 R1-R5 全部不命中**，方可进入 Stage 27+ 实现。

## 9. 后续 Stage 切片（Stage 26-30）

| Stage | 切片 | 关键产出 |
|---|---|---|
| **Stage 26** | broker PoC + 选型 + ArchUnit 豁免 | 真实 DMS broker（倾向 RocketMQ）端到端 produce/pull/retry/DLQ/租户隔离/顺序性实测；final 选型；`transport.broker` 子包骨架 + ArchUnit 圈进 + §6.2 文本扫描豁免；验证 R1-R5 不命中 |
| **Stage 27** | relay adapter + 模型 B 反向 ack + 状态机扩展 | `BrokerForwardingRelayPort` 实现；AWAITING_ACK 中间态（若采）；反向 ack 通道；outbox DISPATCHING→ACKED 闭环 |
| **Stage 28** | receiver consumer + 生产 TickSource | `BrokerForwardingConsumerPort` 实现；inbox dedup；relay claim 循环驱动（解除 §6.1 第 3 项 scheduler 障碍）；§6 L1-L5 租户纵深接线 |
| **Stage 29** | 端到端 T4 | sender enqueue→relay→broker→receiver→ack→outbox ACKED 全链路；三生命线（retry/lease/breaker）+ 三终态在 T4 上复现 |
| **Stage 30** | T1→T4 切换共存 | routeHandle 级别路由（T1 push / T4 broker 灰度）；`A2aForwardingDeliveryPort` 与 broker adapter 共存 |

## 10. 护栏清单（不变量，Stage 26+ 实现必须守）

- **payloadRef 走 broker message header/attribute，不进 message body**（§6.2②）。
- **agent-bus 不写 Task execution state**：broker message 只载 routing descriptor + payloadRef + tenantId + messageId，不载 `TaskStatus`；Task lifecycle owner 仍归 runtime（§6.2④）。
- **routeHandle opaque（HD4）**：broker topic/partition 由 routeHandle 经 `ForwardingEndpointResolver` 映射，不自行 unwrap。
- **broker retry off，agent-bus retry policy（Stage 14）主导**（避免双层重投）。
- **跨租户 R-C.c 显式 reject**（§6.2⑤）：broker 侧 L2 header 校验失败必须 reject 不 commit，不静默 fallback。
- **broker 产品概念不泄漏 `transport.broker` 之外**（§6.2① 精神）：治理语义仍由 SPI 端口表达。
- **outbox 保留**：broker 不替代 outbox（T4 ≠ C4）。

## 11. deferred / 后续

- **broker 产品 final 选型**：Stage 26 PoC（§5）。
- **broker 接线 / relay adapter / receiver consumer / 状态机扩展 / 生产 TickSource / 端到端 / T1→T4 切换**：Stage 26-30（§9）。
- **§6.1 第 3 项（scheduler）障碍**：T4 relay claim 循环仍需驱动，Stage 26/28 同次裁决解除（MQ client consumer loop 解决 receiver 侧，但 relay 侧 claim 循环待定）。
- **双层 ordering 一致性**：outbox+broker 双层 ordering 是 T4 主要工程负担，Stage 27/29 建立因果关系/全局序号。
- **沿用 Stage 24 deferred**：FORCE/WITH CHECK RLS / app_role 生产部署 / 连接池治理 / PAYLOAD_REF_INVALID 接线 / EXPIRED 真实触发源 / payloadPolicy 持久化 / 真实 handler / registry resolver。

---

相关文档：

- [`decision`](agent-bus-forwarding-runtime-decision.md) §6.1 Stage 25 更新块 / §4 Stage 25 许可段 / §8 Stage 25 裁决段。
- [`transport-candidates`](agent-bus-forwarding-runtime-transport-candidates.md)（Stage 13 评审，本 packet 的输入）。
- [`t3-assessment`](agent-bus-t3-consumer-pull-landing-assessment.md)（T3 落地障碍分析，§2.2 依据）。
- [`ICD-Agent-Bus-Forwarding-Runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)（HD4 契约）。
- [`Stage 25 双语 plan`](../delivery-projections/agent-bus-stage24-review-and-stage25-plan.md)。
