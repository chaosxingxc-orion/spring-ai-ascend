---
level: L1
module: agent-bus
view: architecture
status: active
source_review_packet: docs/architecture/l0/10-governance/review-packets/agent-bus-architecture-review-draft.md
---

# agent-bus L1 架构总览

## 1. 模块定位

`agent-bus` 是平台的跨平面通信与治理模块。它负责定义和承载跨边界 envelope、SPI 和治理规则，使外部请求、服务间调用、客户端能力回调、federation、reflection 等流量不直接穿透模块边界。

它不是 Task 生命周期中心。Task 创建、状态持久化、suspend/resume、Task hierarchy 和 service API 仍由 `agent-runtime` 拥有。`agent-bus` 负责的是“流量如何跨边界进入、离开、转发、关联和治理”。

> 命名说明：本文架构语义（所有权、参与者、状态归属）使用 L0 逻辑名 `agent-runtime` / `agent-core`。`agent-runtime` 已落地为同名模块（原 `agent-service` 已重命名为 `agent-runtime`）；`agent-core` 已落地为 `agent-core`。完整映射见 [`README.md`](README.md)「命名说明」。

## 2. 两块逻辑职责

H2 已接受 `agent-bus` 内部分为两个逻辑子模块：

| 逻辑子模块 | 职责 | 当前代码对应 |
|---|---|---|
| Gateway | 外部到内部的入口治理、转发和调度。典型流量是 edge/client 到 compute_control 的 C2S ingress。 | `com.huawei.ascend.bus.spi.ingress` |
| 真 bus | service 与 service 之间的相互调用、跨服务路由和跨服务治理。典型流量包括 federation、reflection、未来 control/rhythm 通道。 | `com.huawei.ascend.bus.spi.federation`、`com.huawei.ascend.bus.spi.s2c`、`com.huawei.ascend.bus.spi.engine` 的跨服务边界事实 |

这个拆分是 L1 逻辑架构拆分，不表示当前仓库已经拆成两个 Maven module。

真 bus 还需要进一步包含两个目标态能力：

| 目标态能力 | 职责 | 当前状态 |
|---|---|---|
| 类 MQ 转发底座 | 提供跨 runtime 的异步转发、队列/主题抽象、correlation、ack/retry、backpressure、DLQ/replay、ordering/fairness 等运行时语义。 | Stage 4 broker-agnostic 转发语义 ICD（[`ICD-Agent-Bus-Forwarding`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md)）+ Stage 7 C3 运行态契约（[`ICD-Agent-Bus-Forwarding-Runtime`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)）；Stage 7–18 已落：纯 Java 领域模型 / 端口 / 状态机（Stage 7）+ record / claim / lease（Stage 8）+ lease-safe（Stage 9）+ dispatch-loop（Stage 10）+ runtime-completion（Stage 11）+ 真实持久化 Postgres JDBC adapter + Flyway + RLS（Stage 12，打破路径 B）+ transport 候选评审（Stage 13）+ deliver 重投策略 overflow-safe 退避 + exhausted→DLQ（Stage 14）+ 真实 A2A HTTP 投递绑定 PoC（Stage 15，`A2aForwardingDeliveryPort` 消费 agent-runtime `/a2a`）+ 断路器接入 worker 三态机（Stage 16）+ 首次跨模块端到端集成（Stage 17，真实 `LocalA2aRuntimeHost`，happy-path）+ 失败路径端到端 + `REMOTE_TASK_FAILED` 码收口（Stage 18，真实 FAILED→DLQ / 不可达 route→RETRY）+ 重投往返生命周期端到端验证（Stage 19，claimDue 的 RETRY_SCHEDULED 回收子句跨多 tick 触发 → exhausted/DLQ 或恢复/ACKED）+ 验证回填（Stage 20，租约过期卡住持有者回收端到端 + 断路器全状态机真实链路端到端）+ 多 worker 并发验证（Stage 21，并发 claim 无重复投递 + 共享断路器单例并发一致 OPEN）+ 时间驱动的终态与续约端到端验证（Stage 22，EXPIRED 终态 markExpired 落盘 + lease 续约真实触发）；192 tests green（含 real-SQL / transport / 跨模块 IT / retry lifecycle / lease-reclaim-and-breaker / multi-worker-concurrency / expiry-and-lease-renewal）；真实 broker / queue / replay store 物理实现 + push-pull-MQ 最终投递模型裁决仍 deferred（H2/H3）。 |
| Agent 注册与发现 | 维护运行时路由所需的 agent/service/capability 注册发现索引，包括实例、租户、能力、版本、region、endpoint、health、负载等路由元数据。 | 设计态；当前没有注册表实现或发现 API。 |

这里的注册发现只服务于运行时路由和治理，不拥有 agent 的业务定义、Task 生命周期或执行状态。

## 3. 当前已接受的 SPI 范围

首批 L1 范围覆盖当前代码中已存在的 SPI 包：

- `com.huawei.ascend.bus.spi.ingress`
- `com.huawei.ascend.bus.spi.s2c`
- `com.huawei.ascend.bus.spi.federation`
- `com.huawei.ascend.bus.spi.engine`

W2 workflow primitives 只保留设计态，不进入自动实现范围。它们包括 mailbox、admission、backpressure、sleep、wakeup、tick 等运行时治理能力。

## 4. 关键边界

| 边界 | 规则 |
|---|---|
| Task 生命周期 | `agent-runtime` 拥有，`agent-bus` 不直接写 Task execution state。 |
| Runtime 与 Core | `EnginePort` 是中立边界；`agent-runtime` 驱动，`agent-core` 实现，bus 提供 SPI home。 |
| Client 到 Runtime | `agent-client` 不直接依赖 compute_control 内部模块；通过 `IngressGateway` 进入。 |
| Runtime 到 Client | 通过 `S2cCallbackTransport` 派发 S2C callback；envelope 必须显式携带 `tenantId`（Stage 2 契约层已迁移）。 |
| Runtime 到 Runtime | 由真 bus 负责跨服务调用治理；当前以 federation/reflection 等 SPI 和契约事实表达；类 MQ 转发语义（Stage 4 设计态）消费 Stage 3 route handle，不改远端 Task lifecycle owner，大载荷走 data reference path（见 [`ICD-Agent-Bus-Forwarding`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md)）。 |
| 物理 bus | broker、ordering、DLQ、mailbox fairness 等运行时实现未进入当前切片。 |
| 注册发现 | 真 bus 目标态需要 agent/service/capability 注册发现；已在 Stage 3 形成设计态 ICD 与 harness；仍不进入 runtime 实现。 |

## 5. 当前事实来源

| 类型 | 来源 |
|---|---|
| 模块元数据 | `agent-bus/module-metadata.yaml` |
| 构建定义 | `agent-bus/pom.xml` |
| 契约目录 | `docs/contracts/contract-catalog.md` |
| 具体契约 | `docs/contracts/ingress-envelope.v1.yaml`、`s2c-callback.v1.yaml`、`engine-port.v1.yaml`、`federation-envelope.v1.yaml`、`reflection-envelope.v1.yaml` |
| L0 边界 | `architecture/L0-Top-Level-Design/boundaries.md`、`architecture/L0-Top-Level-Design/views.md` |
| 相关 L1 | `architecture/L1-High-Level-Design/agent-runtime/**` |
| 当前代码 | `agent-bus/src/main/java/com/huawei/ascend/bus/spi/**` |
| 当前测试 | `agent-bus/src/test/java/**` |

## 6. S2C tenant 迁移状态

S2C `tenantId` 契约层迁移已完成（Stage 2，commit `d894f494`，Rule R-C.c）：`S2cCallbackEnvelope` 的 `tenantId` 已成为第七个必填 in-band 字段，`docs/contracts/s2c-callback.v1.yaml`、Java record 与 `contract-catalog.md` 已同步。这是 pre-GA 内部契约的 breaking change，当前不升 v1.1/v2。

剩余事项为 runtime 侧，随后续波次推进（不改 Task lifecycle 所有权，见 `development.md` §6）：

- `agent-runtime` 与 `agent-core`（当前实现模块：`agent-core`）的 envelope 构造点绑定。
- runtime-side schema validation integration。
- downstream 文档与治理模板的剩余同步。

## 7. 自动化边界

自动化可以基于本 L1 文档生成图、schema fixture、测试骨架和漂移检查清单。自动化不得直接执行以下变更：

- 改变 Task 生命周期所有权。
- 给 `agent-bus` 增加到 sibling module 的生产依赖。
- 实现 broker、mailbox、backpressure、tick、DLQ、ordering 等运行时语义。
- 未完成冲突通知就修改 S2C `tenantId` 契约。

## 8. 风险

| 风险 | 说明 | 缓解 |
|---|---|---|
| 职责漂移 | bus 容易被误写成 Task lifecycle owner。 | 在每个视图重复 service-owned Task invariant。 |
| 文档超前 | 契约层 `S2cCallbackEnvelope.tenant_id` 已迁移（Stage 2）；runtime 构造点待后续波次。 | 标记为已迁移契约 + runtime 待补，后续波次补齐构造点。 |
| 运行时夸大 | 当前是 SPI 和契约脚手架，不是完整物理 bus。 | 对每个能力标注成熟度。 |
| 注册发现所有权漂移 | agent registry 容易被误写成 agent 定义仓库或 Task 状态仓库。 | 限定为运行时路由发现索引，不拥有 agent 定义和 Task 状态。 |
| 自动生成反客为主 | Swagger/schema/stub 可能被误当语义事实源。 | 生成物必须引用 human ICD 和 source fact。 |
