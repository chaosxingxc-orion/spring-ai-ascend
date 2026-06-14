---
artifact_type: a2d_review_packet
version: "agent-bus-forwarding-runtime-decision"
status: "adopted-c3-pending-final-confirmation"
source_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage6-review-and-stage7-plan.md"
source_stage6_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage5-review-and-stage6-plan.md"
source_candidates: "docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md"
source_icd: "docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md"
source_l1: "architecture/docs/L1/agent-bus/README.md"
target_module: agent-bus
---

# agent-bus 运行态转发候选方案裁决（Stage 6 H2/H3 → Stage 7 落位）

## 0. 文档状态与裁决前提

**当前状态：默认采用 C3（database outbox / inbox），待 H2/H3 最终确认。**

本文档是 Stage 6（运行态候选裁决）的裁决记录，并被 Stage 7 计划（[`stage6-review-and-stage7-plan`](../delivery-projections/agent-bus-stage6-review-and-stage7-plan.md) §0）正式推进落位：Stage 6 的 draft 作为裁决入口已被接受为收口输入；Stage 7 不再生成「等待裁决」文档，而是按 Stage 5 推荐方向默认采用 **C3 database outbox / inbox** 作为生产候选路径，并解锁最小运行态实现。

裁决权威方仍为 **H2/H3**（架构治理 / 人类决策）。Stage 7 计划 §0 的落位逻辑是「若人类没有反对，默认按 Stage 5 推荐采用 C3」—— 这是默认裁决，不是绕过 H2/H3：若 H2/H3 在最终确认时反对 C3，则回退本文档状态、撤回 Stage 7 生产代码、回到分支式计划态。在 H2/H3 最终确认前，Stage 7 生产代码受 §4 许可范围与 §6 禁止范围双向约束。

回应「Stage 6 是否应该写代码」（Stage 6 计划 MI6-003）：Stage 6 本身是裁决关卡、不写代码（已收口）；Stage 7 在 C3 默认裁决下开始铺最小运行态实现路径，代码最早在此出现。

## 1. 裁决项

| 裁决项 | 裁决 | 说明 |
|---|---|---|
| 采用候选 | **C3 database outbox / inbox（默认）** | Stage 7 计划 §0 落位；durable、审计强、tenant 行级隔离清楚、可能复用现有 DB、运维轻（见 [`candidates §6.2`](agent-bus-forwarding-runtime-candidates.md)）。 |
| 是否允许写生产代码 | **是（仅 C3 最小范围）** | 允许最小领域模型、端口接口、状态机、schema 草案、harness；不允许完整调度器接入真实服务调用链（见 §4）。 |
| 最小实现范围 | C3 最小骨架（Stage 7 切片 2-5） | L2 技术设计、outbox/inbox schema 草案、`com.huawei.ascend.bus.forwarding{,.spi,.runtime}` 骨架、状态机与契约 harness、L1 同步。 |
| 验收标准 | Stage 7 计划 §4 | C3 决策不再阻塞；L2 可投影 schema/接口/测试；最小骨架有单测/harness 覆盖关键规则；不引入 broker/MQ 依赖；不改 Task ownership；不绕 routeHandle；不放 payload body。 |

**本裁决不再是「待裁决导致不得推进」的阻塞状态**：默认采用 C3 已解锁 Stage 7 最小实现路径。

## 2. 采用候选

**采用 C3 — database outbox / inbox（默认生产候选路径）。**

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

允许范围与禁止范围同时存在，且均可被 review（§6 与 Stage 7 计划 §3/§4）。

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

- H2/H3 最终确认 C3：本文档状态升为 `adopted-c3`，Stage 7 生产代码保留。
- H2/H3 反对 C3：本文档回退 draft，撤回 Stage 7 生产代码，回到 §5 分支式计划态。
- Stage 8：真实持久化实现（JDBC / migration / polling / lease / 并发抢占 / backpressure 参数 / 是否独立 adapter module / 接入 agent-runtime 受控调用路径）。

相关文档：

- Stage 5 候选评审：[`agent-bus-forwarding-runtime-candidates`](agent-bus-forwarding-runtime-candidates.md)。
- Stage 6 计划：[`agent-bus-stage5-review-and-stage6-plan`](../delivery-projections/agent-bus-stage5-review-and-stage6-plan.md)。
- Stage 7 计划：[`agent-bus-stage6-review-and-stage7-plan`](../delivery-projections/agent-bus-stage6-review-and-stage7-plan.md)。
- Stage 7 L2 设计：[`forwarding-outbox-inbox`](../../../../architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md)。
- Stage 7 runtime 契约：[`ICD-Agent-Bus-Forwarding-Runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)。
- 设计态契约：[`ICD-Agent-Bus-Forwarding`](../../05-contracts/human-readable/ICD-agent-bus-forwarding.md)（HD4）。
- L1 入口：[`agent-bus L1 README`](../../../../architecture/docs/L1/agent-bus/README.md)。
