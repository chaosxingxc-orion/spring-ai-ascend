---
level: L1
module: agent-bus
view: development
status: draft
---

# agent-bus L1 Feature Catalog

## 1. Feature 总览

| 编号 | Feature | 逻辑归属 | 当前状态 | 说明 |
|---|---|---|---|---|
| AB-F01 | C2S Ingress Gateway | Gateway | SPI 已存在，测试待补 | 外部 client 到内部 runtime 的入口治理。 |
| AB-F02 | Ingress Envelope / Response | Gateway | Java record 已存在，测试待补 | 请求/确认 envelope，包含 tenant、trace、幂等。 |
| AB-F03 | S2C Callback Transport | Gateway / 真 bus 交界 | SPI 已存在，tenant 已迁移，runtime 构造点待补 | runtime 到 client 的 capability callback。 |
| AB-F04 | S2C Envelope / Response | Gateway / 真 bus 交界 | Java record 已存在，tenant 已迁移，runtime 构造点待补 | 请求/响应 envelope；`tenantId` 已为 required in-band 字段（Stage 2）。 |
| AB-F05 | Federation Gateway | 真 bus | SPI 已存在，runtime 未实现 | 跨部署、跨网络的 runtime 间调用治理。 |
| AB-F06 | Reflection Envelope Router | 真 bus | SPI 已存在，payload 类型待决策 | reflection update 路由。 |
| AB-F07 | Engine Port SPI Home | 中立边界 | SPI 已存在 | runtime-core 边界类型位置。 |
| AB-F08 | Workflow Primitives | 真 bus | 设计态 | mailbox、admission、backpressure、sleep、wakeup、tick。 |
| AB-F09 | Contract Projection | 治理能力 | 草案 | 从 human ICD/YAML 投影 schema、fixture、mock、test。 |
| AB-F10 | Drift Check | 治理能力 | 草案 | 检查模块依赖、契约状态、生成物来源。 |
| AB-F11 | MQ-like Forwarding Substrate | 真 bus | C3 最小骨架（Stage 7） | 类 MQ 的跨 runtime 转发底座，包含队列/主题、ack/retry、correlation、DLQ/replay、ordering/fairness、backpressure；broker-agnostic 转发语义见 [`ICD-Agent-Bus-Forwarding`](../../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md)，C3 运行态契约见 [`ICD-Agent-Bus-Forwarding-Runtime`](../../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)（消费 Stage 3 route handle，不改 lifecycle owner，大载荷走 data reference；Stage 7 已落纯 Java 领域模型 / 端口 / 状态机 + in-memory 测试替身，真实持久化 deferred Stage 8）。 |
| AB-F12 | Agent Registry / Discovery | 真 bus | 设计态 | 运行时路由所需的 agent/service/capability 注册发现索引，不拥有 agent 定义或 Task 状态。 |

## 2. 成熟度定义

| 状态 | 含义 |
|---|---|
| SPI 已存在 | 生产源码中已有接口或 record，但不代表 runtime 已完整实现。 |
| 测试待补 | L1 接受该表面，但 harness 证据不足。 |
| tenant 已迁移 | `tenantId` 已成为 required in-band 字段（Stage 2，Rule R-C.c）；runtime 构造点待后续波次。 |
| runtime 未实现 | 只有 SPI 或契约，不包含 broker/transport/runtime binding。 |
| 设计态 | 只允许文档和评审，不允许自动生成生产实现。 |
| C3 最小骨架 | 纯 Java 领域模型 / 端口 / 状态机 + 非生产 in-memory 测试替身已落地；真实持久化 / broker 绑定 deferred。 |

## 3. Feature 与视图映射

| Feature | 逻辑视图 | 进程视图 | 物理视图 | 开发视图 | 场景视图 |
|---|---|---|---|---|---|
| AB-F01 | Gateway | SC-001 | edge 到 compute_control | `bus.spi.ingress` | SC-001 |
| AB-F02 | Gateway | SC-001 | tenant/trace/idempotency | `IngressEnvelope` / `IngressResponse` | SC-001 |
| AB-F03 | Gateway / 真 bus | SC-002 | runtime 到 client | `S2cCallbackTransport` | SC-002 |
| AB-F04 | Gateway / 真 bus | SC-002 | tenant 目标态 | `S2cCallbackEnvelope` / `S2cCallbackResponse` | SC-002 |
| AB-F05 | 真 bus | SC-004 | 跨网络待定 | `FederationGateway` | SC-004 |
| AB-F06 | 真 bus | SC-005 | cloud 到 edge | `ReflectionEnvelopeRouter` | SC-005 |
| AB-F07 | 中立边界 | SC-003 | compute_control 内部边界 | `bus.spi.engine` | SC-003 |
| AB-F08 | 真 bus | SC-006 | bus_state future runtime | 待定 | SC-006 |
| AB-F11 | 真 bus | SC-012 | bus_state future runtime | 待定 | SC-012 |
| AB-F12 | 真 bus | SC-007..SC-011 | bus_state registry/discovery | 待定 | SC-007..SC-011 |

## 4. 不进入当前实现的能力

当前 L1 不批准自动实现：

- broker binding。
- mailbox runtime。
- backpressure runtime。
- tick engine。
- DLQ / replay store。
- 类 MQ 转发底座（Stage 7 已落 C3 最小骨架：纯 Java 领域模型 / 端口 / 状态机 + 非生产 in-memory 测试替身，真实持久化 / broker 绑定仍 deferred；Stage 4 转发语义见 [`ICD-Agent-Bus-Forwarding`](../../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md)；Stage 7 运行态契约见 [`ICD-Agent-Bus-Forwarding-Runtime`](../../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)；Stage 6 候选裁决见 [`agent-bus-forwarding-runtime-decision`](../../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)，默认采用 C3）。
- agent/service/capability registry runtime。
- service discovery API。
- runtime-side S2C construction binding / schema validation runtime（契约层迁移已完成）。

契约层 S2C tenant 迁移已完成（Stage 2，已通知冲突方）；runtime-side construction binding 仍待后续波次，不进入当前实现。
