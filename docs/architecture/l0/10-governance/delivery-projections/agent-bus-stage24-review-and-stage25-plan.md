---
artifact_type: delivery_projection
version: agent-bus-stage24-review-and-stage25-plan
status: stage-25-planned
source_commit: d73b6ecd
stage25_planned: 2026-07-02
source_stage24_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage23-review-and-stage24-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_transport_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-decision.md
source_transport_candidates: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
source_icd_forwarding: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md
source_l2: architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md
target_module: agent-bus（**纯文档裁决阶段、无生产代码**：投递模型最终裁决 T4 hybrid；解除 §6.1 第 1 项引 concrete broker、守 §6.2 精神 + 第②③④⑤项不变；broker 物理接线 deferred Stage 26+）
---

# agent-bus Stage 24 评审与 Stage 25 计划

**投递模型最终裁决：T4 hybrid（outbox + broker），解除 §6.2 引 concrete broker 禁令**

本文是 agent-bus 转发运行态验证序列的交付投影（delivery projection）：先评审上一阶段（Stage 24）的实际落点，再规划下一阶段（Stage 25）的范围与设计。**Stage 25 回到「纯文档裁决阶段」范式**（性质同 Stage 5 / 6 / 13）——无生产代码改动，产出 = 决策文档 + 切片计划。它裁决自 Stage 13 起 deferred 的「最终 push / pull / MQ 投递模型」：采用 **T4 hybrid（outbox + broker）**，保留 Stage 12 outbox 事务一致性 + relay worker produce 到 broker + receiver 从 broker pull 消费；**解除 §6.1 第 1 项引 concrete broker 的明确禁令**（类比 Stage 12 解除 §6.1 第 2 项 JDBC），同时**守 §6.2 精神**（broker 产品概念不反向定义 broker-agnostic 治理语义）+ 第②③④⑤项全部不变。

---

## §0 结论

- **Stage 24 接受**（commit `d73b6ecd`，200 tests green，ArchUnit green，未 push 待用户授权）。RLS 接线两场景闭合：受限 `app_role` 下 adapter 业务路径 green（接线反证）+ 裸 SQL RLS 过滤对照（纵深防御可见）。adapter 首次引入 `TransactionTemplate` 事务管理，激活 §7.3 fail-closed RLS，闭合 Stage 12「armed but not wired」纵深防御债务。
- **Stage 25 裁决投递模型 = T4 hybrid（outbox + broker）**，`adopted-t4`。Stages 15–24 在 **T1 push**（`A2aForwardingDeliveryPort` 同步等终态）上端到端验证了三条生命线（retry 往返 / 租约回收 / 断路器）+ 三终态（ACKED / DLQ / EXPIRED）+ payloadRef 传递 + RLS 接线，**200 tests green**。但 T1 push 是反应式背压（sender 主导、receiver 被动），无法升级为 receiver 主动控速 —— **pull 才是目标态**。
- **用户裁决（本会话收敛）**：采用 **T4 hybrid** —— 保留 outbox + relay produce broker + receiver pull。**核心理由（用户独立重新发现 Stage 13 结论）**：**(a) MQ 本质是 pull**（Kafka consumer poll / RocketMQ pull consumer / broker prefetch = 与 Stage 12 `claimDue` + `SKIP LOCKED` 同内核）；**(b) MQ client 自带 consumer loop 一次性解决 scheduler 障碍**（不需自建 `ForwardingDispatchLoop.TickSource` 生产驱动、不需解除 §6.1 第 3 项）；**(c) broker 独立中介解决模块依赖障碍**（receiver 从 broker pull，完全不碰 agent-bus 表，绕开 `AgentBusDependencyBoundaryTest`，与 T3 receiver 访问持久层撞依赖边界的障碍根本不同）。
- **§6.2 解除落位**：解除的是 **§6.1 第 1 项**（引 concrete broker 的明确禁令），broker client 圈进 `forwarding.runtime.transport.broker` 子包（同 Stage 12 `persistence.jdbc` / Stage 15 `transport.a2a` 范式）；同时**守 §6.2 第①项精神**（broker 产品概念——topic / partition / offset / consumer-group——不反向定义 envelope / routeHandle / lease / retry policy / DLQ / breaker 治理语义，这些仍由现有 SPI 端口表达）+ **第②③④⑤项（payload body / token stream / Task state / 跨租户 R-C.c / registry 不变 agent 定义仓库）全部不变**。**T4 ≠ Stage 13 §3 拒绝的 C4**：C4 = 抛弃 outbox 纯 external broker；T4 = outbox 保留 + broker 仅投递通道。
- **broker 产品选型 deferred Stage 26 PoC**（倾向 RocketMQ：原生顺序消息 + namespace 租户分层 + 原生 retry/DLQ + 华为云 DMS 托管 + 概念收敛度对 broker-agnostic 治理最友好）；final 选型靠真实 DMS broker 端到端 produce / pull / retry / DLQ / 租户隔离实测后定。**T1 push PoC 保留**（`A2aForwardingDeliveryPort`），T4 上线后 routeHandle 级别共存 / 灰度切换（Stage 30）。
- **预计**：200 tests 不变（无 Java 改动），ArchUnit 不变（无新代码、Stage 26 实施时才加 broker client ArchUnit 豁免）。产出 = decision §6.1/§6.2/§4/§8 治理落位 + 新建 transport-decision packet + L2 同步 + 7 L1 视图 + ICD + yaml + 双语 plan（本文）。

---

## §1 Stage 24 评审

### 1.1 已落地

`C3ForwardingRlsWiringIntegrationTest`（新 IT，2 场景 + 接线契约测试，`@Isolated`，不 boot runtime）+ adapter 生产代码改动（首次事务管理），200 tests green：

- **场景 A — 受限角色下 adapter 业务路径 green（接线反证）**：`app_role`（受 RLS 约束）专用 DataSource + `JdbcForwardingOutbox(appRoleDs)` → `enqueue(tenant-A)` → `claimDue(tenant-A)` → `markAcked` 全 green。green ⟺ 接线 `set_config('app.tenant_id',…,true)` 在事务内生效（否则 RLS fail-closed 断 `claimDue` 返回空、业务断裂）。Inbox 同路径（`receive` → `markConsumed`）。
- **场景 B — 裸 SQL RLS 过滤对照（纵深防御可见）**：`app_role` 连接 + 手动 `SET app.tenant_id` + 裸 SQL `SELECT count(*)`（绕应用层 WHERE）：设 tenant-A → 1 / 设 tenant-B → 0（A 行被 RLS 过滤）/ 不设 → 0（fail-closed）。证明 RLS policy 在受限角色下真正生效（与场景 A 互补：A 证接线、B 证 RLS 真过滤）。

### 1.2 测试与提交

- 200 tests green（Stage 23 的 195 + 5：场景 A +1 IT、场景 B +1 IT、接线契约 +2、RLS 控制组 +1 范畴；含 +3 RLS IT + 2 接线契约），ArchUnit green（spring 事务管理圈在 `persistence.jdbc` 子包，现有规则不破）。
- commit `d73b6ecd`（experimental，**未 push**，PAT 过期待用户 `! git push origin experimental`；与 Stage 22 `852765c9` / Stage 23 `e827291c` 同批）。

### 1.3 关键发现（首次事务管理 + RLS 接线）

- **`SET LOCAL` 在 auto-commit 下不生效**：adapter 此前纯 auto-commit 单语句，`SET LOCAL` 活不过一条语句 → 接线必须先引入事务边界，**首次引入 `TransactionTemplate` 编程式事务**（不用 `@Transactional`——adapter 是 POJO，测试直接 `new` 不经容器）。
- **`set_config` 返回文本（结果集）**：必须 `queryForObject(..., String.class)` 消费，`update` 会拒绝返回的结果集。
- **不加 FORCE / 不加 WITH CHECK / V1 零改**：FORCE 会破坏 7 个 superuser IT；应用层 `WHERE tenant_id` 已防写越权；最小 footprint。
- **双构造器向后兼容**：旧 `(DataSource)` 委托 `(DataSource, new DataSourceTransactionManager(dataSource))`，现有所有 IT 零改动。

### 1.4 deferred 结转（Stage 24 → 后续）

FORCE RLS（owner 也受约束）/ WITH CHECK（防写越权）/ app_role 生产部署模型 / 连接池治理（事务期间持有连接但事务极短）/ 沿用 Stages 15–23 deferred（PAYLOAD_REF_INVALID 接线 / EXPIRED 真实触发源 / payloadPolicy 持久化 / 真实 agent handler / registry resolver / 真实 scheduler / push-pull-MQ 最终裁决）—— 其中**最后一项（push-pull-MQ 最终裁决）正是 Stage 25 要闭合的**。

### 1.5 验证序列 Stage 17–24 里程碑总结

C3 三条生命线（retry 往返 / 租约回收 / 断路器）+ 三终态（ACKED / DLQ / EXPIRED）+ payloadRef 端到端传递 + RLS 纵深防御，在单 / 多 worker 全端到端验证通过。**T1 push 路径已被充分证明可行**（Stages 15–24 的全部验证资产都建立在 T1 push 之上），但 T1 是 sender 主导的反应式背压——Stage 25 裁决升级到 receiver 主导控速的 pull 目标态。

---

## §2 Stage 25 范围与设计

### 2.1 为什么是 T4 hybrid（裁决分析）

Stage 13（[`transport-candidates`](../review-packets/agent-bus-forwarding-runtime-transport-candidates.md)）比较了 T1 dispatcher-push over sync RPC / T2 dispatcher-push over broker / T3 consumer-pull over DB / T4 C3+broker hybrid 四候选 × 8 维度，核心结论「反压内核 = 消费方控速 / 速率解耦，MQ 只是满足该内核的载体之一」。Stage 15 选 T1 push 作临时 PoC（同步等终态），Stages 15–24 在 T1 上完成全部端到端验证。

但 **T1 push 的本质局限**：sender 主动驱动、receiver 被动接受，背压是反应式的（sender 必须感知 receiver 状态才能减速）。目标态需要 **receiver 主动控速**（receiver 按自身能力 pull）——即 pull 模型。

两个 pull 候选的取舍：

| 候选 | 内核 | 落地障碍 | 是否破 §6.2 |
|---|---|---|---|
| **T3 consumer-pull over DB** | receiver 直接 `SELECT … FOR UPDATE SKIP LOCKED` agent-bus outbox 表 | ① 生产 scheduler 从无到有（`TickSource` 生产零驱动，撞 §6.1 第 3 项）；② **模块依赖反转**（receiver 访问 agent-bus 持久层，撞 `AgentBusDependencyBoundaryTest`） | 不破（Stage 13 非裁决推荐） |
| **T4 C3+broker hybrid** | relay worker（sender 侧）claim outbox → produce broker；receiver 从 broker pull | ① 生产 scheduler（同 T3，但 MQ client 自带 consumer loop 免除）；② broker 独立中介（receiver 不碰 agent-bus 表，无模块依赖反转） | **破**（解除 §6.1 第 1 项引 broker） |

**用户裁决 T4 的核心理由**（独立重新发现 Stage 13 结论）：

1. **MQ 本质是 pull** —— Kafka `consumer.poll()` / RocketMQ pull consumer / broker prefetch buffer，与 T3 `claimDue` + `SKIP LOCKED` 是**同一反压内核**的不同载体。broker 的 consumer 速率 = 消费方控速，正是目标态。
2. **MQ client 自带 consumer loop 一次性解决 scheduler 障碍** —— T3/T4 relay/receiver 都需要生产 `TickSource` 驱动（`ForwardingDispatchLoop.java` 生产零驱动，§6.1 第 3 项障碍）。但 MQ client（如 RocketMQ `DefaultMQPushConsumer` / Kafka `KafkaConsumer.poll()` loop）**自带 consumer 调度循环**，receiver 侧无需自建 scheduler、无需解除 §6.1 第 3 项。sender 侧 relay worker 复用 Stage 12 已落地的 outbox `claimDue` poll（仍是 §6.1 第 3 项的 test TickSource，生产化是 Stage 26/28 议题）。
3. **broker 独立中介解决模块依赖障碍** —— T3 的 receiver 要 `SELECT` agent-bus outbox 表，直接撞 `AgentBusDependencyBoundaryTest`（agent-bus 持久层是 bus 内部资产，receiver 不应直接读）。T4 的 receiver 从 **broker** pull（broker 是独立中介，非 agent-bus 表），完全不碰 agent-bus 持久层，绕开模块依赖边界。broker 在 receiver 视角是一个外部数据源，与 agent-runtime 读自己的 DB 同性质。

**T4 ≠ C4**：Stage 13 §3 拒绝了 C4（抛弃 outbox 纯 external broker），因为 C4 丧失 outbox 的事务一致性（业务事务与投递原子性）。T4 **保留 Stage 12 outbox** —— sender 业务事务内 enqueue outbox（PENDING），relay worker 异步 claim outbox → produce broker，**outbox 仍是 durable 事务一致层**，broker 仅是 outbox 之后的投递通道。

### 2.2 §6.2 解除落位（类比 Stage 12 解除 §6.1 第 2 项 JDBC）

解除的是 **§6.1 第 1 项**（引 concrete broker 的明确禁令），同时**守 §6.2 第①项精神**：

- **解除**：broker client（Stage 26 PoC 定产品）圈进 `forwarding.runtime.transport.broker` 子包（与 Stage 12 Spring/JDBC 圈 `persistence.jdbc`、Stage 15 A2A SDK 圈 `transport.a2a` **同范式**）。ArchUnit 新豁免 `forwarding_core_does_not_import_kafka/nats/rocketmq_outside_broker_adapter`（类比 `AgentBusForwardingSpiPurityTest.java:142-152` a2a 规则；**Stage 26 实施时加，Stage 25 只在文档标注**）+ §6.2 文本扫描双豁免。
- **守 §6.2 第①项精神不变**：broker 产品概念（topic / partition / offset / consumer-group / broker retry / DLX）**不泄漏** transport adapter 之外；envelope / routeHandle / lease / retry policy / DLQ / breaker 治理语义由现有 SPI 端口（`ForwardingDeliveryResult` / `ForwardingRetryPolicy` / `ForwardingCircuitBreaker`）表达，`forwarding-persistence §5` 立柱不动。
- **第②③④⑤项始终不得，全部不变**：payload body / token stream 不进 envelope（payloadRef 条件必填，走 broker header 而非 body）；registry 不变 agent 定义仓库；agent-bus 不写 Task execution state；跨 tenant 显式 reject（R-C.c）。

### 2.3 T4 hybrid 数据流（5 跳）

```
Sender 业务事务: enqueue envelope → outbox(PENDING) [withTenant+RLS, Stage 24]
Relay worker(sender): claimDue→outbox(DISPATCHING,SKIP LOCKED §7.1) → produce broker
    (topic 由 routeHandle→resolver 映射; body=仅 routing descriptor, header=payloadRef+tenantId+messageId)
Receiver consumer(pull): poll broker(控速=反压内核) → inbox.receive(RECEIVED, dedup by tenantId+messageId+consumerServiceId)
    → 业务处理 → markConsumed/markRejected
Ack 回路: receiver commit offset → relay markAcked(outbox ACKED, lease-guarded §7.2)
    [模型 B: ack-after-consume, 真至少一次]
```

- **ack 语义裁决方向 = 模型 B（ack-after-consume）**：relay produce broker 后 outbox 留 DISPATCHING（长 lease 或 AWAITING_ACK 中间态），receiver 消费完经反向 ack 通道通知 relay `markAcked` —— ACKED 真反映 receiver 处理（至少一次）。状态机是否加第 7 态 AWAITING_ACK 是 Stage 27 设计决策。
- **避免双重重投**：agent-bus retry policy（Stage 14，transport-agnostic）**主导**，broker 原生 retry **配 off**（consumer `enable.auto.commit=false`，手动 commit only after receiver 处理成功）。broker outcome 翻译为 `ForwardingDeliveryResult`（ACKED / RETRY_SCHEDULED / DLQ / EXPIRED），与 Stage 15 `A2aForwardingDeliveryPort` 终态映射 + Stage 18 `REMOTE_TASK_FAILED` 同范式。

### 2.4 治理层边界（broker adapter 子包）

类比 Stage 15 `transport.a2a`（`A2aForwardingDeliveryPort`），新增 `com.huawei.ascend.bus.forwarding.runtime.transport.broker`：

- `BrokerForwardingRelayPort`（relay: outbox record → broker produce）
- `BrokerForwardingConsumerPort`（receiver: broker poll → inbox）
- `BrokerClientProperties` / `<product>ForwardingRelayPort` + `<product>ForwardingConsumerPort`（Stage 26 PoC impl）

**核心不变量**：`ForwardingDeliveryPort` / `ForwardingDeliveryResult` / `ForwardingRetryPolicy` / `ForwardingCircuitBreaker` / `ForwardingOutboxRecord` 这些 SPI 端口**零改动** —— broker adapter 是新 `ForwardingDeliveryPort` 实现（relay 形态）。broker 产品概念封装映射（不泄漏）：topic → routeHandle（经 `ForwardingEndpointResolver`）、partition key → messageId hash、consumer-group → consumerServiceId（inbox dedup key）、offset → adapter 内部 commit、broker retry/DLX → `ForwardingDeliveryResult`。

### 2.5 租户隔离纵深（broker 侧，破 §6.2 后必须补强）

Stage 24 RLS（DB 侧）只护 outbox / inbox 表。T4 下 receiver 从 broker pull，**broker 侧是新攻击面**（broker 无 RLS）。纵深分层：

| 层 | 机制 | 作用 |
|---|---|---|
| **L1 topic-per-tenant**（首选） | routeHandle 映射含 tenantId → topic，receiver 只订阅本租户 topic | 物理隔离 |
| **L2 header 校验** | 消息 header 强制带 tenantId，poll 后校验 header == consumer tenant，不符 → reject 不 commit | §6.2 第⑤项 broker 侧体现（显式 reject 不静默） |
| **L3 consumer-group 隔离** | consumer-group 含 consumerServiceId | 接收侧独立消费位 |
| **L4 inbox DB 侧 RLS** | 复用 Stage 24 `withTenant` | DB 侧纵深 |
| **L5 应用层显式拒绝** | `inbox.receive` 前 tenantId 校验，不符抛 `ForwardingFailureCode`（R-C.c 显式失败） | 兜底 |

### 2.6 Stage 12–24 资产命运（关键项）

| 资产 | 命运 | 说明 |
|---|---|---|
| outbox 表 + claim/lease/SKIP LOCKED + lease-guarded mutation + RLS(Stage 24) + TransactionTemplate | **保留（零改）** | T4 durable 层，relay claim / 终态标记复用全套 |
| inbox 表 | **保留（receiver dedup）** | broker 至少一次下 redelivery 必然，inbox dedup 是必需幂等键 |
| retry policy(Stage 14) | **保留（主导）** | broker retry 配 off，避免双重重投 |
| circuit breaker(Stage 16) | **保留（relay 侧）/ 退化（receiver 侧）** | relay produce 失败短路故障 broker route；receiver pull 天然自调速 |
| A2aForwardingDeliveryPort(Stage 15, T1) | **保留（临时 PoC，共存 / 灰度切换）** | Stage 30 routeHandle 级别路由 |
| ForwardingDispatcherWorker + DispatchLoop + TickSource | **保留 + 必须解决生产 TickSource 零驱动** | T4 relay/receiver 都需生产 scheduler（撞 §6.1 第 3 项），Stage 26 / 28 同次裁决解除 |
| ForwardingDeliveryResult(4 态) + StateMachine + SPI 端口 | **保留（零改）** | broker adapter 翻译为 result；模型 B 可能扩展 StateMachine（Stage 27） |

### 2.7 rejection criteria（命中即回退 T1 / T3）

- **R1** 团队不承担 broker 运维。
- **R2** 目标投递量级 T3（DB-poll）已能满足（T4 双层复杂度超收益）。
- **R3** 跨层 ordering（outbox + broker）无法保证。
- **R4** broker 侧租户隔离无法纵深。
- **R5** 生产 scheduler 障碍无法解除。

### 2.8 边界 + ArchUnit + §6.2

| 护栏 | Stage 25 状态 |
|---|---|
| 无生产代码 | ✅ 纯文档裁决阶段（性质同 Stage 5 / 6 / 13）；`git status` 仅 `docs/` + `architecture/` 改动，无 `agent-bus/src/` 改动 |
| §6.2 解除落位 | ✅ 解除 §6.1 第 1 项（引 concrete broker）；守 §6.2 第①项精神（不反向定义语义）+ 第②③④⑤项全部不变 |
| ArchUnit | ✅ 不变（无新代码）；broker client 豁免 deferred Stage 26 实施时加 |
| 现有测试 | ✅ 200 tests 不变（无 Java 改动）；如确认基线 `mvn -f .../agent-bus/pom.xml test` 仍 200 green |
| 4+1 视图回灌 | ✅ 按 [[agent-bus-4plus1-view-rebound]] 清单，7 L1 视图 + 2 L2 + ICD + yaml + decision 全同步 |

---

## §3 关键发现（前置分析）

| # | 发现 | 影响 |
|---|---|---|
| F1 | **MQ 本质是 pull**（Kafka poll / RocketMQ pull / broker prefetch = `claimDue` + `SKIP LOCKED` 同内核） | T4 的 receiver pull 与 T3 同反压内核，broker 非异类 |
| F2 | **MQ client 自带 consumer loop** | 一次性解决 §6.1 第 3 项 scheduler 障碍（receiver 侧无需自建 TickSource） |
| F3 | **broker 独立中介解决模块依赖** | receiver 从 broker pull 不碰 agent-bus 表，绕开 `AgentBusDependencyBoundaryTest`（T3 的真障碍） |
| F4 | **T4 ≠ C4** | 保留 outbox 事务一致性（Stage 12），broker 仅投递通道；C4 抛弃 outbox 已被 Stage 13 §3 拒绝 |
| F5 | **broker retry 必须配 off** | consumer `enable.auto.commit=false`，agent-bus Stage 14 retry policy 主导，避免双重重投 |
| F6 | **broker 侧租户隔离是新攻击面** | broker 无 RLS，须 L1 topic-per-tenant + L2 header 校验 + L3-L5 纵深补强 |
| F7 | **§6.2 解除落位精确** | 解除 §6.1 第 1 项明确禁令（类比 Stage 12 §6.1 第 2 项 JDBC）+ 守 §6.2 第①项精神 + 第②③④⑤项不变 |
| F8 | **SPI 端口零改动** | broker adapter 是新 `ForwardingDeliveryPort` 实现，broker 概念封装映射不泄漏 |
| F9 | **T1 push PoC 充分证明可行** | Stages 15–24 全部验证资产建立在 T1 上，T4 上线后 T1 保留共存 / 灰度切换 |
| F10 | **双层 ordering 是主要工程负担** | outbox + broker 双层 ordering（Stage 13 §2.4），Stage 27 / 29 需建立因果关系 / 全局序号 |

---

## §4 切片 + MI 表（Stage 25 = 纯文档，无生产代码）

| MI | 切片 | 产出 |
|---|---|---|
| MI25-001 | 0 治理落位 | decision.md §6.1 加 Stage 25 更新块（解除 §6.1 第 1 项引 broker）+ §6.2 加 Stage 25 更新块（解除引 concrete broker 禁令、守精神与②③④⑤）+ §4 加 Stage 25 许可 / 禁止段（正向：broker 圈 `transport.broker`、relay / consumer adapter [Stage 26+ 授权]；反向：payload body / token stream / Task state / §6.2②③④⑤）+ §8 加 Stage 25 裁决段（指向 transport-decision packet，`adopted-t4`）。范式见 Stage 12 更新块（:131）/ Stage 15 更新块（:133）。 |
| MI25-002 | 1 裁决 packet | 新文件 `agent-bus-forwarding-runtime-transport-decision.md`（§0-§11：裁决边界 / 裁决项表 / §6.2 解除论证 / T4 数据流 / 治理边界 / broker 选型矩阵 [倾向 RocketMQ deferred Stage 26] / 租户纵深 / 资产命运表 / rejection criteria / 后续 Stage 切片 / 护栏清单），结构对齐 `transport-candidates.md`。 |
| MI25-003 | 2 L2 同步 | `forwarding-persistence.md`（追加 §25 Stage 25 决策）+ `forwarding-outbox-inbox.md`（T4 数据流 + 资产命运 + §6.2 解除标注，文档 only，不改代码契约）。 |
| MI25-004 | 3 4+1 视图回灌 | 按 [[agent-bus-4plus1-view-rebound]]：7 L1 视图（README / physical / logical / process / development / scenarios / ARCHITECTURE）标注「投递模型裁决 T4 hybrid、破 §6.2、broker 选型 deferred Stage 26、T1 PoC 保留」；ICD（边界标题 / 边界条 / Open Issue）+ yaml（`stage25_scope`: delivers=transport-decision-doc, adopted-t4; not_delivers=broker-wiring, relay-adapter, receiver-consumer, broker-product-selection）。 |
| MI25-005 | 4 双语 plan | delivery-projections `agent-bus-stage24-review-and-stage25-plan.md`（Stage 24 评审 + Stage 25 裁决切片计划，frontmatter 对齐 stage23-review-and-stage24-plan.md）。即本文。 |
| — | 5 验证 + 提交 | grep 确认无生产代码改动（纯文档阶段）；现有 200 tests 不跑（无 Java 改动）；ArchUnit 不变（无新代码）；commit（experimental，PAT 过期待用户 push，与 Stage 22 / 23 / 24 同批）。 |

---

## §5 deferred + 风险

### 5.1 deferred（Stage 25 不触及，记录为后续 Stage 26–30）

- **Stage 26 broker PoC + relay adapter**：broker 产品选型实测（倾向 RocketMQ，真实 DMS broker 端到端 produce / pull / retry / DLQ / 租户隔离）；`BrokerForwardingRelayPort` 实现；broker client ArchUnit 豁免；生产 TickSource（§6.1 第 3 项）解除。
- **Stage 27 模型 B 反向 ack + 状态机扩展**：是否加第 7 态 AWAITING_ACK；反向 ack 通道；双层 ordering 因果关系。
- **Stage 28 receiver consumer + 生产 TickSource**：`BrokerForwardingConsumerPort` 实现；receiver poll → inbox；生产 scheduler 落地。
- **Stage 29 端到端 T4**：sender outbox → relay → broker → receiver → ack 全链端到端 IT。
- **Stage 30 T1→T4 切换共存**：routeHandle 级别路由 / 灰度切换；T1 push PoC 退役计划。
- **沿用 Stages 15–24 deferred**：FORCE / WITH CHECK RLS / app_role 生产部署 / 连接池治理 / PAYLOAD_REF_INVALID 接线 / EXPIRED 真实触发源 / payloadPolicy 持久化 / 真实 agent handler / registry resolver —— 均不变。

### 5.2 风险

- **broker 产品选型未裁决**：RocketMQ 倾向有据（原生顺序 / namespace 租户 / retry-DLQ / DMS 托管 / 概念收敛），final 选型 deferred Stage 26 PoC（真实 DMS broker 端到端实测后定）；若 PoC 推翻倾向则回更 transport-decision packet §5。
- **§6.1 第 3 项（scheduler）障碍**：T4 relay / receiver 都需生产 TickSource（生产零驱动），与 T3 同障碍，Stage 26 / 28 必须同次裁决解除（类比 t3-assessment 标注）。MQ client 自带 consumer loop 只免除 receiver 侧，sender 侧 relay worker 仍需生产化。
- **双层 ordering 一致性**：outbox + broker 双层 ordering 是 T4 主要工程负担（Stage 13 §2.4），Stage 27 / 29 需建立因果关系 / 全局序号。
- **broker 侧租户隔离纵深**：破 §6.2 引 broker 后，broker 侧（无 RLS）是新攻击面，L1-L5 纵深必须在 Stage 26-28 落地，否则跨租户泄露风险。
- **§6.2 守恒自证**：解除 §6.1 第 1 项（明确禁令）+ 守 §6.2 第①项精神（不反向定义语义）+ 第②③④⑤项不变；broker 是投递通道非 payload body / token stream / Task state / registry 定义仓库；跨租户 R-C.c 显式 reject（L2 header 校验失败必 reject）。

---

## 相关文档

- Stage 24 计划（本文评审对象）：[`agent-bus-stage23-review-and-stage24-plan`](agent-bus-stage23-review-and-stage24-plan.md)
- 运行态裁决：[`agent-bus-forwarding-runtime-decision`](../review-packets/agent-bus-forwarding-runtime-decision.md) §8
- **Stage 25 投递模型裁决（本次产出）**：[`agent-bus-forwarding-runtime-transport-decision`](../review-packets/agent-bus-forwarding-runtime-transport-decision.md)
- transport 候选评审（Stage 13）：[`agent-bus-forwarding-runtime-transport-candidates`](../review-packets/agent-bus-forwarding-runtime-transport-candidates.md)
- 运行态 ICD：[`ICD-agent-bus-forwarding-runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)
- forwarding ICD（HD4 / payloadRef）：[`ICD-agent-bus-forwarding`](../../05-contracts/human-readable/ICD-agent-bus-forwarding.md)
- L2 持久化：[`forwarding-persistence`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)
- yaml（machine-readable）：[`agent-bus-forwarding-runtime.v1`](../../05-contracts/machine-readable/agent-bus-forwarding-runtime.v1.yaml)
