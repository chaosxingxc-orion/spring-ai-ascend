---
level: L1
module: agent-bus
view: scenarios
status: active
---

# agent-bus 场景视图

> 命名说明：本文参与者与所有权使用 L0 逻辑名 `agent-runtime` / `agent-core`。`agent-runtime` 已落地为同名模块（原 `agent-service` 已重命名为 `agent-runtime`）；`agent-core` 已落地为 `agent-core`。完整映射见 [`README.md`](README.md)「命名说明」。

## SC-001：client 创建或操作 Task

| 项目 | 内容 |
|---|---|
| 参与者 | `agent-client`、Gateway、`agent-runtime` |
| 入口 | `IngressGateway.routeClientRequest(...)` |
| 契约 | `ingress-envelope.v1.yaml` |
| 流程 | client 构造 `IngressEnvelope`，Gateway 校验并路由到 service，service 处理 Task 生命周期，Gateway 返回 `IngressResponse`。 |
| 成功结果 | 返回 accepted cursor 或查询结果。 |
| 失败结果 | 返回 rejected reason 或 deferred。 |
| 不变量 | Gateway 不直接写 Task execution state。 |
| 缺口 | ingress 契约测试需要补齐。 |

## SC-002：service 请求客户端能力

| 项目 | 内容 |
|---|---|
| 参与者 | `agent-runtime`、`S2cCallbackTransport`、`agent-client` |
| 入口 | `S2cCallbackTransport.dispatch(...)` |
| 契约 | `s2c-callback.v1.yaml` |
| 流程 | service 构造 `S2cCallbackEnvelope`，通过 transport 发给 client，client 执行本地 capability 后返回 `S2cCallbackResponse`，service 校验并恢复或失败。 |
| 成功结果 | Run 恢复并继续执行。 |
| 失败结果 | timeout、schema invalid、transport failure 等导致 Run 进入失败或对应终态。 |
| 不变量 | service 仍拥有 suspend/resume 状态机。 |
| 缺口 | envelope 已携带 `tenantId`（Stage 2 契约层迁移）；runtime 构造点与 schema validation integration 待后续波次。 |

## SC-003：service 驱动 execution engine

| 项目 | 内容 |
|---|---|
| 参与者 | `agent-runtime`、`EnginePort`、`agent-core` |
| 入口 | `EnginePort.execute(...)` |
| 契约 | `engine-port.v1.yaml` |
| 流程 | service 通过中立端口发起执行，engine 返回 `AgentEvent` stream，service 消费事件并更新状态。 |
| 成功结果 | 收到唯一 terminal event，Task/Run 状态由 service 收敛。 |
| 失败结果 | failed 或 interrupt request 以 terminal event 表达。 |
| 不变量 | bus 提供 SPI home，但不是 engine runtime owner。 |
| 缺口 | terminal event harness 需要补齐。 |

## SC-004：跨部署 federation

| 项目 | 内容 |
|---|---|
| 参与者 | 本地 Gateway、真 bus、`FederationGateway`、远端 `agent-runtime` |
| 入口 | `FederationGateway.routeFederated(...)` |
| 契约 | `federation-envelope.v1.yaml` |
| 流程 | 本地 bus 判断请求需要跨部署转发，通过 federation gateway 发送到远端服务边界。 |
| 成功结果 | 远端 service 返回同步确认，后续结果异步观察。 |
| 失败结果 | routing rejected、network failure、policy denied。 |
| 不变量 | 远端 Task 生命周期仍由远端 service 拥有。 |
| 缺口 | broker、credential、routing policy 未决定。 |

## SC-005：reflection 更新路由

| 项目 | 内容 |
|---|---|
| 参与者 | cloud Slow Track、`ReflectionEnvelopeRouter`、edge Fast Track/session |
| 入口 | `ReflectionEnvelopeRouter.route(...)` |
| 契约 | `reflection-envelope.v1.yaml` |
| 流程 | 云侧产生 reflection envelope，真 bus 负责路由到目标 edge session。 |
| 成功结果 | edge session 接收到 reflection update。 |
| 失败结果 | target not found、schema invalid、delivery failure。 |
| 不变量 | router 只路由 envelope，不拥有 reflection 语义处理。 |
| 缺口 | 当前参数是 `Map<String,Object>`，需要决定 schema validator 或 typed record。 |

## SC-006：未来 workflow primitives

| 项目 | 内容 |
|---|---|
| 参与者 | 真 bus、service、future scheduler/runtime |
| 入口 | 未接受 |
| 契约 | backpressure、control-event、work-item、access-intent 等草案契约 |
| 流程 | 未来可能由 mailbox、admission、backpressure、tick 等机制治理跨服务运行节奏。 |
| 成功结果 | 尚未定义 |
| 失败结果 | 尚未定义 |
| 不变量 | 当前只保留设计态，不进入自动实现范围。 |
| 缺口 | 需要版本意图、状态 owner、失败语义和 harness。 |

## SC-007：agent/service/capability 注册

| 项目 | 内容 |
|---|---|
| 参与者 | service/agent、registry（agent-bus view） |
| 入口 | registry register（设计态） |
| 契约 | [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md) |
| 流程 | service 上线时把可路由能力注册到 registry，entry 含 `tenantId`、`agentId`/`serviceId`、`capability`、`routeKey`、`contractVersion`/`capabilityVersion`、`endpoint`、`lease`。 |
| 成功结果 | entry 可见，持有有效 lease。 |
| 失败结果 | 必填字段缺失（尤其 `tenantId`）→ register rejected。 |
| 不变量 | registry 只拥有 route index，不接管 agent 定义或 Task 状态。 |
| 缺口 | Stage 3 不实现 runtime；lease/TTL 具体值待定。 |

## SC-008：基于 tenant + capability 的服务发现

| 项目 | 内容 |
|---|---|
| 参与者 | gateway/真 bus、registry |
| 入口 | registry discover（设计态） |
| 契约 | [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md) |
| 流程 | gateway/真 bus 用 `tenantId` + `capability`（+ 可选 version/health 过滤）查询 registry，拿到候选 route handle + health + version。 |
| 成功结果 | 返回零或多个候选；调用方据 health/weight 选择，用 route handle 路由。 |
| 失败结果 | `entry_not_found`、`registry_unavailable`。 |
| 不变量 | discovery result 不携带 Task execution state。 |
| 缺口 | 多候选选择责任在调用方，registry 只返回候选集。 |

## SC-009：目标不可用（health / lease）

| 项目 | 内容 |
|---|---|
| 参与者 | gateway/真 bus、registry |
| 入口 | registry discover（设计态） |
| 契约 | [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md) |
| 流程 | 目标 entry 存在但 health degraded/unhealthy，或 lease 已过期。 |
| 成功结果 | unhealthy target 仍可见但显式标注 health 状态；调用方据 health 决策（降级、重试、放弃）。 |
| 失败结果 | `lease_expired`（entry 不可见）、`health_unavailable`（entry unhealthy）。 |
| 不变量 | registry 不替调用方做放弃决策，只表达 health。 |
| 缺口 | health metadata schema 待定。 |

## SC-010：版本不匹配

| 项目 | 内容 |
|---|---|
| 参与者 | gateway/真 bus、registry |
| 入口 | registry discover（设计态） |
| 契约 | [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md) |
| 流程 | 调用方查询要求某 `contractVersion`/`capabilityVersion`，但无匹配 entry。 |
| 成功结果 | 无匹配时显式返回 `version_unavailable`，不静默返回错误版本。 |
| 失败结果 | `version_unavailable`。 |
| 不变量 | 版本由注册方声明；version mismatch 有显式 result 状态。 |
| 缺口 | version downgrade 策略待定。 |

## SC-011：跨 tenant 查询拒绝

| 项目 | 内容 |
|---|---|
| 参与者 | gateway/真 bus、registry |
| 入口 | registry discover（设计态） |
| 契约 | [`ICD-Agent-Registry-Discovery`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md) |
| 流程 | 调用方 tenant 上下文与 query `tenantId` 不一致，或试图发现他 tenant 的 capability。 |
| 成功结果 | 不适用。 |
| 失败结果 | `tenant_isolation_violation`（cross-tenant query rejected），禁止跨 tenant fallback。 |
| 不变量 | registry 不扩大权限；只返回调用方 tenant 范围内的 entry。 |
| 缺口 | runtime enforcement 待后续波次（Stage 3 harness-level）。 |

## SC-012：runtime-to-runtime 异步控制消息转发（设计态）

| 项目 | 内容 |
|---|---|
| 参与者 | 调用方 `agent-runtime`、真 bus（转发底座）、接收方 `agent-runtime` |
| 入口 | forwarding（设计态） |
| 契约 | [`ICD-Agent-Bus-Forwarding`](../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md) |
| 流程 | 调用方据 Stage 3 discovery 返回的 route handle 构造 forwarding envelope（`tenantId`、`traceId`、`correlationId`、`idempotencyKey`、`routeHandle`、`capability`、`deadline`；`payloadRef` 条件必填），转发底座投递，接收方按自身 Task/Run lifecycle 处理。 |
| 成功结果 | 同步 ack（已落队）或异步完成（接收方处理后回传 outcome）。 |
| 失败结果 | `route_not_found`、`tenant_mismatch`、`delivery_timeout`、`receiver_unavailable`、`backpressure_rejected`、`duplicate_suppressed`。 |
| 不变量 | 不改变远端 Task lifecycle owner；envelope 有载荷时只携带 `payloadRef`（条件必填，MI5-003 方案 B）、不携带 payload body / token stream / Task execution state；纯控制消息可省略 `payloadRef`；大载荷走 data reference path。 |
| 缺口 | broker / MQ 产品选择 deferred（Stage 5）；运行态转发底座、queue / DLQ / replay 存储未实现。 |
