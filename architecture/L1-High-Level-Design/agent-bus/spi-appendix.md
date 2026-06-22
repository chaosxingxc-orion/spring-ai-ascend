---
level: L1
module: agent-bus
view: development
status: active
---

# agent-bus SPI 附录

> 命名说明：本文架构语义（职责拆分、所有权）使用 L0 逻辑名 `agent-runtime` / `agent-core`。`agent-runtime` 已落地为同名模块（原 `agent-service` 已重命名为 `agent-runtime`）；`agent-core` 已落地为 `agent-core`。完整映射见 [`README.md`](README.md)「命名说明」。

## 1. 已接受 SPI 清单

| SPI / 类型 | 包 | 职责 | 契约来源 | 状态 |
|---|---|---|---|---|
| `IngressGateway` | `com.huawei.ascend.bus.spi.ingress` | C2S 入口路由 | `ingress-envelope.v1.yaml` | 已纳入 L1 |
| `IngressEnvelope` | `com.huawei.ascend.bus.spi.ingress` | C2S 请求 envelope | `ingress-envelope.v1.yaml` | 已纳入 L1 |
| `IngressResponse` | `com.huawei.ascend.bus.spi.ingress` | C2S 同步确认 envelope | `ingress-envelope.v1.yaml` | 已纳入 L1 |
| `S2cCallbackTransport` | `com.huawei.ascend.bus.spi.s2c` | S2C callback transport | `s2c-callback.v1.yaml` | 已纳入 L1 |
| `S2cCallbackEnvelope` | `com.huawei.ascend.bus.spi.s2c` | S2C 请求 envelope | `s2c-callback.v1.yaml` | 已纳入 L1；tenant 已迁移（Stage 2，Rule R-C.c） |
| `S2cCallbackResponse` | `com.huawei.ascend.bus.spi.s2c` | S2C 响应 envelope | `s2c-callback.v1.yaml` | 已纳入 L1 |
| `ReflectionEnvelopeRouter` | `com.huawei.ascend.bus.spi.s2c` | reflection envelope 路由 | `reflection-envelope.v1.yaml` | 已纳入 L1，payload 类型待决策 |
| `FederationGateway` | `com.huawei.ascend.bus.spi.federation` | 跨部署 federation 路由 | `federation-envelope.v1.yaml` | 已纳入 L1 |
| `EnginePort` | `com.huawei.ascend.bus.spi.engine` | service-engine 中立执行端口 | `engine-port.v1.yaml` | 已纳入 L1 |

## 2. Engine SPI 说明

`bus.spi.engine` 是中立边界 home。它的存在不表示 `agent-bus` 拥有 engine runtime。

职责拆分：

- `agent-runtime` 驱动 `EnginePort`。
- `agent-core` 实现执行能力。
- `agent-bus` 提供中立 SPI 类型位置，避免 service 和 engine 直接形成不合适的模块耦合。

## 3. S2C tenant 迁移说明

迁移状态：`已迁移`（Stage 2，2026-06-14，Rule R-C.c）。

`S2cCallbackEnvelope` 现携带 9 个字段：`callbackId`、`tenantId`、`serverRunId`、`capabilityRef`、`requestPayload`、`traceId`、`idempotencyKey`、`deadline`、`requestAttributes`。

迁移事实：

- `tenantId` 是 required field（compact constructor 校验非 null、非 blank）。
- YAML 契约 `s2c-callback.v1.yaml#request.required_fields`、Java record、`S2cCallbackEnvelopeLibraryTest`、`contract-catalog.md`、治理模板 `contract-catalog.md.j2` 已同步。
- registry 绑定作为兼容路径保留，但不替代 envelope 内 tenant scope（S2C-TENANT-005）。
- runtime 侧构造点（`agent-runtime` / `agent-core` / `agent-client`）尚未落地，随 runtime 实现波次补齐；Stage 2 只迁移契约与 harness，不改 Task lifecycle 所有权（S2C-TENANT-006）。

## 4. SPI 纯度

SPI 包应保持纯 Java：

- 可以依赖 `java.*`。
- 可以依赖同一 SPI 包或 `agent-bus` 内 sibling carrier type。
- 不应依赖 Spring、Reactor、Jackson、HTTP framework 或 broker runtime。
- 不应依赖 sibling module 的生产代码。

## 5. 待补测试

| SPI | 测试缺口 |
|---|---|
| `IngressEnvelope` | required fields、tenant、trace、request type、requestAttributes defensive copy。 |
| `IngressResponse` | rejected reason、accepted cursor、deferred status。 |
| `S2cCallbackEnvelope` | `tenantId` required field、null/blank 拒绝、registry 兼容路径——已在 Stage 2 harness 补齐。 |
| `FederationGateway` | broker-agnostic、只使用 ingress carrier type。 |
| `ReflectionEnvelopeRouter` | map payload schema validation 或 typed record。 |
| `EnginePort` | terminal event 唯一且最后发出。 |
