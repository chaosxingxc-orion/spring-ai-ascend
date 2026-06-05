---
affects_level: L1
affects_view: logical
proposal_status: review
authors: ["EuphoriaYan", "Codex"]
related_adrs: [ADR-0016]
archived_adrs: [ADR-0159]
related_rules: [R-C, R-D, R-F, R-G, R-I.1, R-J, R-K, R-M]
affects_artefact: []
---

# Proposal: AgentExamples A2A Multi-Runtime Registry / Gateway Facade Sample

> **Date:** 2026-06-04
> **Status:** Pending Review / 非 ADR / 非已批准设计
> **Affects:** `agent-examples` sample layer first; `agent-service` remains a future formal serviceization module candidate, not the implementation target of this wave.

## 0. 2026-06-04 Updated Naming Decision

本轮实现不把 Gateway facade 原型放进正式 `agent-service` 模块。架构师组最新口径是：

- `agent-runtime`：单业务 Agent runtime SDK。每个业务 Agent 以 JavaBean 形式继承 runtime 抽象 handler 基类，并由 runtime 的 A2A access 暴露单张 Agent Card。
- `agent-examples`：承载业务 handler 样例，也承载“多 runtime 注册发现 / Gateway facade”打样能力。这个层可以理解为管理 examples 的样例平台，方便客户学习如何组织多个 runtime。
- `agent-service`：暂时保持服务化 skeleton，不在本轮承载 registry / gateway 代码。未来如果要成为正式 Gateway facade service，需要单独 ADR / L1 authority / contract / DFX wave。

因此，本文中的 registry、route resolve、A2A forward、SLA / DFX 设计，当前均按 `agent-examples` 样例能力理解；它们不是正式 `agent-service` 的生产承诺。

## 1. Background

ADR-0159 已归档，本轮不把它当作实现前置约束；只从当前 A2A 协议和代码事实出发：runtime A2A access 采用标准 `/.well-known/agent-card.json` 暴露单张 `AgentCard`，这与 A2A discovery 的单卡入口一致；如果一个 runtime 下挂多个业务 Agent，就无法通过同一个 well-known path 把多个独立 A2A Agent Card 暴露给外部 client。

架构师组讨论后的新方向是：**每个 `agent-runtime` 只承载一个业务 Agent，并暴露一张 A2A Agent Card；多个业务 Agent 由多个 runtime 实例承载；`agent-examples` 中的 Gateway facade sample 负责收集这些 runtime 的 A2A endpoint / AgentCard，并向外提供统一发现、路由、治理和可观测门面。**

本提案先定义设计，并在同一轮补充一个最小 HTTP 验证门面，用于证明注册、发现、路由解析、多 Agent 与多轮上下文透传链路可以端到端跑通；它不是最终生产网关实现，也不提交 ADR。若评审通过，应再进入 ADR / L1 authority surface / contract / DFX / production implementation wave。

## 2. Scope Statement

- **主要层级**：L1。
- **主要视图**：Logical。
- **次级视图**：Process（注册、续租、路由、流式返回）、Development（API / DTO / adapter 分层）、Physical（多 runtime 部署与灾备）、Scenarios（单 Agent、双 Agent、故障重路由、冷启动、超时）。

### 2.1 本轮最小验证门面

本轮允许在 `agent-examples` 样例模块内增加一个最小 HTTP Controller，仅用于验证 service facade 是否可以作为多个 runtime 的注册发现入口：

- runtime 自注册：`POST /v1/runtime-registrations`。
- runtime 续租：`PUT /v1/runtime-registrations/{runtimeInstanceId}/lease`。
- runtime 注销：`DELETE /v1/runtime-registrations/{runtimeInstanceId}`。
- AgentCard 精确查询：`GET /v1/agents/{agentId}/card?tenantId=...`。
- Agent 列表：`GET /v1/agents?tenantId=...`。
- 路由解析：`POST /v1/agents/{agentId}/routes/resolve?tenantId=...`。
- A2A Gateway 打样：`POST /v1/agents/{agentId}/a2a?tenantId=...`。

该 Controller 暴露注册、发现、路由解析能力，并额外提供一个最小 A2A JSON-RPC 转发入口，用于给客户展示 Gateway facade 的可插拔打样形态。它不持有 runtime 执行态，但本轮会先实现最小 DFX 骨架：TTL / lease、过期实例 `UNREACHABLE`、健康状态路由剔除、明确错误码、转发耗时 trace。它仍不实现生产级鉴权、限流、熔断、跨 AZ 灾备或注册表持久化，这些能力必须在后续 production wave 中补齐。

当前最小 HTTP Controller 是验证切片；后续目标形态更倾向 **Gateway facade service**：样例 Gateway facade 对外提供统一入口并按 `tenantId`、`agentId`、SLA 与健康状态选择 runtime，再把请求转发给目标 runtime 的 `/a2a` endpoint。这样可以让平台侧统一承接权限、流量管控、审计、SLA 观测与灰度治理；如果客户已有自己的 gateway，也可以把本门面作为可插拔注册发现与路由决策组件集成进去。

### 2.2 命名决议：业务 handler 样例层叫 agent-examples

架构师组最新决议是：业务 Agent 的具体 handler 样例不再被描述为新的 service 层，而是先命名为 `agent-examples`。它的职责是沉淀一组可运行、可替换、可复制的 handler 示例，说明客户如何把自己的 Agent 框架接入 `agent-runtime` 的 `AgentHandler` SPI。

因此第一阶段命名边界是：

- `agent-runtime`：单 Agent runtime SDK / bootable runtime，承载一个业务 Agent。
- `agent-examples`：业务 handler、客户接入样例与 Gateway facade 打样集合，不承担生产平台网关职责。每个业务样例应体现为一个继承 runtime 抽象 handler 基类的 JavaBean，例如 `AbstractRuntimeAgentHandler` 子类；该子类同时满足 runtime 执行 SPI 和 A2A AgentCard 暴露要求。
- `agent-service`：本轮不承载 gateway 代码；未来如需成为正式 Gateway facade service，需要单独 ADR / L1 authority / contract / DFX wave。

本轮不直接重命名 Maven module。原因是 module id 改名会牵动 root reactor、module metadata、DFX、architecture facts 与样例启动脚本；当前先在样例层和代码语义上落地 `agent-examples`，后续若要新增或重命名 module，应作为单独 module wave 处理。
- **模块边界**：
  - `agent-runtime`：继续拥有业务执行、TaskControl、Session、Internal Event Queue、A2A 单 Agent endpoint。
- `examples/agent-runtime-a2a-llm-e2e` Gateway facade sample：维护多 runtime 派生视图，用于样例验证，不持有业务执行状态。
  - `agent-bus`：本提案不强行移动职责；未来若 registration/discovery SPI 进入 bus，需要单独 ADR 裁决。

## 3. Root Cause / Strongest Interpretation

1. **Observed failure / motivation**：单 runtime 多 Agent 会遇到 A2A well-known 单 Agent Card 暴露限制，外部 client 无法按标准 discovery 发现多个独立 Agent。
2. **Execution path**：client 读取 `/.well-known/agent-card.json` → 得到一张 `AgentCard` → 按 card 中 `/a2a` endpoint 发送 JSON-RPC → runtime access 转换为 `AgentRequest` 并交给内部 dispatch。
3. **Root cause**：`A2aWellKnownAgentCardController` 固定在 well-known path 返回一个注入的 `AgentCard`，`AccessLayerConfiguration` 默认也只创建一个 `AgentCard`，因此 runtime access 层天然是单 Agent Card 模型。
4. **Evidence**：`agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/A2aWellKnownAgentCardController.java:18`；`agent-runtime/src/main/java/com/huawei/ascend/runtime/access/config/AccessLayerConfiguration.java:43`；`docs/adr/0016-a2a-federation-strategic-deferral.yaml:56`。

**Strongest interpretation**：不要让单 runtime 聚合多个业务 Agent；把 runtime 当作单 Agent A2A server，把 `agent-examples` Gateway facade sample 建成多 runtime 的服务发现、路由和治理打样入口。

## 4. Final Position For Review

### 4.1 每个 runtime 只承载一个业务 Agent

- 每个业务 Agent 实现 `agent-runtime` 中既有 `AgentHandler` SPI，并由 Spring 注册为 Bean。
- 每个 runtime 只绑定一个业务 Agent identity，暴露一张标准 A2A `AgentCard`。
- runtime 自己暴露：
  - `GET /.well-known/agent-card.json`
  - `POST /a2a`
- runtime 不再尝试在单张 `AgentCard` 中代表多个独立业务 Agent。

### 4.2 agent-service 成为多 runtime A2A 门面

`agent-service` 维护多个 runtime 的派生视图：

| 维度 | 内容 |
|---|---|
| 能力 | `AgentCard`、skills、input/output modes、版本、协议接口 |
| 位置 | runtime A2A endpoint、health endpoint、runtimeInstanceId |
| 状态 | READY、COLD、UNREACHABLE、DRAINING、AT_CAPACITY |
| SLA | 首 token 分阶段耗时、错误率、最近健康状态、重路由统计 |
| 治理 | tenant / agent 授权、注册身份校验、审计、限流、熔断 |

`agent-service` 不做：

- 不持有 Task / Session / Internal Event Queue 业务执行状态。
- 不接管 runtime 的 TaskControl。
- 不重写 A2A 协议。
- 不把多个 runtime 的内部状态暴露成平台内部 API。

## 5. Registration And Discovery Model

### 5.1 第一版：runtime 自注册优先

runtime 启动并完成预热后，向 `agent-examples` Gateway facade sample 注册：

- `runtimeInstanceId`
- `tenantId`
- `agentId`
- `AgentCard`
- `a2aEndpoint`
- `healthEndpoint`
- `version`
- `ttl`
- `metadata`

注册记录带租约。runtime 周期续租；续租超时后，`agent-examples` Gateway facade sample 将该实例标记为 `UNREACHABLE`，并停止路由新请求。

自注册失败时：

- runtime 可以继续本地启动，便于开发调试或单体部署。
- 未注册成功的 runtime 不进入 `agent-examples` Gateway facade sample 路由视图。
- `agent-examples` Gateway facade sample 重启后，内存注册视图会丢失；runtime 必须支持周期性重注册 / 续租失败后重新注册。
- runtime 自身升级、离线或注册失败恢复时，不要求重启 Gateway facade sample；runtime 重新 `register` 即可恢复路由视图。
- 如果注册失败原因是鉴权失败、tenant/agentId 不匹配、AgentCard 不合法或版本策略拒绝，则重启 Gateway facade sample 无法修复，必须修正注册请求或治理配置。

### 5.2 可选适配：K8S discovery

K8S discovery 作为后续 adapter，不阻塞第一版：

- informer 观察 Pod/Service/EndpointSlice readiness。
- 对 Ready endpoint 拉取 `/.well-known/agent-card.json`。
- 将 K8S readiness 与 AgentCard 合成为同一 `RuntimeRoute` 视图。

K8S adapter 与 self-registration adapter 产出同一种派生视图，上层 routing 不因 adapter 改变。

### 5.3 注册真相源

第一版可以使用 `agent-examples` Gateway facade sample 内存注册表作为设计验证；生产态必须满足至少一种：

- runtime 定期自注册 + 多副本 service 共享可恢复后端；
- K8S / registry 作为真相源，Gateway facade sample 只持可重建缓存；
- 二者组合：self-registration 提供业务元数据，K8S 提供存活真相。

## 6. Routing And SLA

### 6.1 路由输入

路由输入至少包含：

- `tenantId`
- `agentId`
- `sessionId` / correlation id
- 用户消息或 A2A JSON-RPC body
- idempotency key
- client streaming preference

目标 Gateway facade service 的处理顺序：

1. 鉴权与 tenant 隔离。
2. 校验 `agentId` 是否在该 tenant 下可用。
3. 选择健康且已预热 runtime。
4. 将请求转发到对应 runtime A2A endpoint。
5. 将 runtime streaming response 转发给 client，并记录首 token 分阶段 trace。

当前最小 HTTP Controller 已覆盖注册、发现、租约续租、route resolve 与一个 A2A 转发打样入口；它证明 Gateway facade 的端到端形态，但尚未实现生产级鉴权、限流、审计、SSE backpressure、连接池治理或故障重试策略。

### 6.1.1 Gateway facade service 功能打样

Gateway facade service 的目标不是重写 A2A 协议，而是在平台边界提供一层可插拔、可替换、可治理的入口。第一版打样功能如下：

| 功能 | 第一版打样 | 生产态增强 |
|---|---|---|
| 统一入口 | `POST /v1/agents/{agentId}/a2a?tenantId=...` | 可扩展为 `/a2a` + header/body 中解析 agent identity，或与客户 gateway path 约定对齐。 |
| 路由决策 | 调用 `AgentDiscoveryApi.resolveRoute`，只选择 READY runtime。 | 增加权重、灰度、区域亲和、租户策略、熔断、限流和负载反馈。 |
| 协议转发 | 原样转发 JSON-RPC body、content-type 和必要 header。 | 支持 SSE / streaming backpressure、连接池、超时预算、重试预算和半开熔断。 |
| 可观测 | 注入 `X-Agent-Examples-Runtime-Instance`、`X-Agent-Examples-Route-Resolve-Ms`、`X-Agent-Examples-First-Byte-Ms`、`X-Agent-Examples-Forward-Ms` 便于链路排查。 | 增加 trace id、tenant/agent/runtime 维度指标、首 token 分段耗时和审计事件。 |
| 安全治理 | 本轮仅保留接口形态。 | 接入客户 IAM / gateway 鉴权、tenant-agent 授权、签名校验、敏感 header 过滤。 |
| 客户集成 | 客户已有 gateway 时，可把本模块作为 route resolver + forwarding sample。 | 支持 discovery-only、gateway-proxy、sidecar/proxy adapter 三种部署方式。 |

本轮最小实现的意图是给客户打样：客户可以照着 `agent-examples` Gateway facade sample 的 registry + route resolve + A2A forward 组合，把权限、流量管控和审计接到自己的平台网关里。

### 6.2 端到端首 token < 0.5s

用户确认的 SLA 口径是**端到端首 token < 0.5s**，不是只要求门面首包。因此设计必须拆分时间预算：

| 阶段 | 目标预算 | 说明 |
|---|---:|---|
| `service.auth_and_route` | ≤ 30 ms | 鉴权缓存、agentId 解析、路由选择、熔断判断。 |
| `service.forward` | ≤ 30 ms | 建连复用、请求转发、header / correlation 注入。 |
| `runtime.admission` | ≤ 70 ms | runtime 入站解析、session/task 接入、dispatch admission。 |
| `runtime.model_first_token` | ≤ 350 ms | 已预热模型 / adapter 的首 token 返回。 |
| buffer | ≤ 20 ms | 网络抖动与观测开销。 |

如果使用长冷启动模型、本地慢模型或跨地域 runtime，0.5s 端到端首 token 不应被声明为满足；必须通过错误码和 DFX trace 暴露真实瓶颈。

### 6.3 SLA 保护动作

- **runtime 预热状态**：COLD runtime 不进入可路由池。
- **快速失败**：不可达、租约过期、AgentCard 过期、tenant 不匹配直接 fail closed。
- **快速重路由**：READY 副本失败时，尝试同 agentId 的其他 READY 副本。
- **streaming path 优先**：首 token SLA 只对 streaming path 做强约束；非 streaming 仅保证最终响应。
- **可观测错误码**：
  - `AGENT_NOT_FOUND`
  - `RUNTIME_UNREACHABLE`
  - `RUNTIME_COLD`
  - `RUNTIME_AT_CAPACITY`
  - `AGENT_CARD_EXPIRED`
  - `TENANT_AGENT_FORBIDDEN`
  - `FIRST_TOKEN_TIMEOUT`

## 7. DFX And Disaster Recovery

### 7.0 DFX Scope Boundary

`agent-examples` 不宣称自身达到 5 个 9。它只提供一个“5 个 9 友好”的 Gateway facade 参考骨架，帮助客户理解哪些机制必须先出现在接口和代码形态里。

| 范围 | 本轮样例做到 | 生产必须补齐 |
|---|---|---|
| 注册视图 | 内存注册表、TTL / lease、续租、注销 | 可恢复 registry backend 或 K8S / Nacos / Consul / etcd 真相源 |
| 健康路由 | `READY` 才可路由；`COLD` / `AT_CAPACITY` / `DRAINING` / `UNREACHABLE` fail closed | 主动健康探测、熔断、限流、隔离舱、权重与灰度 |
| 多副本 | 多 runtime 副本共享同一 route view，按健康状态选择 | 多 AZ、多地域、同城双活、异地灾备 |
| A2A 转发 | 最小 JSON-RPC 转发入口，失败快速返回 | SSE backpressure、连接池、重试预算、超时预算、半开熔断 |
| 可观测 | route resolve / first byte / forward trace headers | SLA/SLO 报表、错误预算、审计链路、跨中心 failover 观测 |
| 安全 | 接口形态预留 | runtime identity、tenant-agent 授权、注册签名、防伪注册 |

### 7.1 Availability

- 同一 `tenantId + agentId` 支持多个 runtime 副本。
- TTL 续租超时自动剔除。
- 健康副本优先，DRAINING 副本不接新流量。
- 未来正式 Gateway facade 多副本部署时，注册视图必须可恢复或可重建。
- 面客金融场景建议把未来正式 Gateway facade 门面自身按 **99.999% 年可用性目标**设计；这意味着年度累计不可用窗口约 5.26 分钟，不能依赖单机、单 AZ 或单内存注册表。
- runtime route view 的生产态目标建议为 **RTO ≤ 1 分钟、RPO ≤ 1 秒**；若机构只要求较低灾备等级，可在 ADR 中降级，但 proposal 默认按面客关键系统上限设计。

### 7.2 Resilience

- 注册失败不阻塞 runtime 本地启动，但该 runtime 不被门面发现。
- `agent-examples` Gateway facade sample 重启后，内存注册视图会丢失，runtime 通过周期性重注册恢复视图。
- runtime 自身升级、离线恢复或注册失败恢复时，不要求重启 Gateway facade sample；只要重新 `register` 即可恢复可路由状态。
- runtime 失败时，未开始输出的请求可以重路由；已经输出 token 的请求不得静默迁移，应显式返回中断/失败事件。
- 对 5 个 9 目标，必须设计跨实例、跨可用区、跨注册视图恢复；单内存注册表只能作为开发态。

### 7.3 Disaster Recovery Topology

金融面客场景需要把灾备拆成三层，而不是只写"多副本"：

| 层级 | 目标 | agent-service 设计含义 |
|---|---|---|
| 同城高可用 | 单机 / 单 AZ / 单机房故障不影响新请求 | `agent-service` 多副本 active-active；runtime 同 agent 多副本 active-active；注册视图跨 AZ 复制或可由 runtime 快速重建。 |
| 同城灾备 | 主机房不可用时，同城备中心接管 | 同城备中心必须有可用 `agent-service`、runtime endpoint、注册视图恢复能力；流量入口支持自动或集中切换。 |
| 异地灾备 | 城市级灾害后恢复核心服务 | 异地中心至少 warm standby；保留 AgentCard、runtime endpoint 元数据、tenant-agent 授权关系、审计和配置快照；按 RTO/RPO 要求恢复路由。 |
| 异地多活目标态 | 区域级故障下保持关键 agent 服务连续 | 需要跨地域路由、租户数据隔离、凭证引用可解析、模型供应商就近可用；这是生产强化阶段，不是第一版实现。 |

建议默认拓扑：

1. **开发态**：单 `agent-examples` Gateway facade sample + 内存注册表 + 单 runtime；只验证接口语义。
2. **生产最小态**：同城双 AZ active-active Gateway facade；registry backend 可恢复；runtime 自注册 + TTL；每个关键 Agent 至少 2 个 READY 副本。
3. **金融面客态**：两地三中心或同等级拓扑；同城 active-active，异地 warm standby / active-standby；注册视图、AgentCard、tenant-agent 授权关系、审计链路具备异地恢复能力。
4. **五个 9 目标态**：跨中心健康剔除、自动重路由、演练可证明；SLA 统计按用户可见不可用时间计算，而不是按单组件 uptime 计算。

灾备边界：

- Gateway facade 可以恢复发现与路由，不负责恢复 runtime 内部 Session / Task / Internal Event Queue。
- 已开始流式输出的请求遇到 runtime 故障，不做静默跨 runtime 续流；应返回可观测中断事件，由 client 或上层流程重新发起。
- route view 的 RPO 只覆盖 AgentCard / endpoint / health / authorization metadata，不覆盖业务对话上下文。

### 7.4 Observability

每次路由必须记录：

- `tenantId`
- `agentId`
- `runtimeInstanceId`
- `sessionId`
- `correlationId`
- `latency_stage`
- `route_decision`
- `failure_reason`

首 token trace 至少拆出：

- `service.auth_and_route`
- `service.forward`
- `runtime.admission`
- `runtime.model_first_token`

同时新增灾备可观测字段：

- `dr_region`
- `dr_site`
- `route_failover_count`
- `registry_source`
- `registry_lag_ms`
- `runtime_lease_age_ms`
- `first_token_sla_breach`

### 7.5 Security

- 注册接口必须校验 runtime identity。
- `AgentCard` 只是能力声明，不是授权依据。
- `tenantId + agentId` 绑定由 Gateway facade 治理面校验。
- runtime 不得通过伪造 AgentCard 获得跨 tenant 访问权。
- deregister / renew 必须只允许同一个 runtime identity 操作自身注册记录。

### 7.6 Releasability

- 第一版先支持 self-registration adapter。
- K8S discovery adapter 后续加入时，不改变 `AgentDiscoveryApi` / route view。
- AgentCard schema 变更需要兼容旧 runtime；不兼容字段进入 version gate。
- service 可以灰度启用某个 runtime 版本，按 tenant / agentId / traffic percentage 做路由选择。

## 8. Proposed Interfaces

> 本节定义接口语义；本轮只实现最小内存注册表和 HTTP 验证门面，不实现生产级 registry backend、K8S adapter 或 A2A 反向代理。

### 8.1 RuntimeRegistrationApi

```text
RuntimeRegistrationApi
  register(RuntimeAgentRegistration registration) -> RuntimeRegistrationResult
  renew(RuntimeLeaseRenewal renewal) -> RuntimeLeaseResult
  deregister(RuntimeInstanceId runtimeInstanceId) -> RuntimeDeregisterResult
```

语义：

- `register`：创建或覆盖同 identity 的 runtime 注册记录；注册时校验 runtime identity、tenant scope、AgentCard 基本合法性。
- `renew`：刷新租约和健康摘要；不能改变 `tenantId + agentId` 主身份。
- `deregister`：主动下线；进入 DRAINING 或直接移除，由后续实现按部署态裁决。

### 8.2 AgentDiscoveryApi

```text
AgentDiscoveryApi
  getAgentCard(agentId, tenantId) -> AgentCard
  listAgents(tenantId) -> List<AgentCardSummary>
  resolveRoute(agentId, tenantId, RoutingContext routingContext) -> RuntimeRoute
```

语义：

- `getAgentCard`：按 `tenantId + agentId` 精确返回单个 AgentCard，不返回不可授权 agent。
- `listAgents`：按 `tenantId` 返回该 tenant 可见的 Agent 摘要列表，不泄漏其他 tenant。这里不需要传 `agentId`，因为 `agentId` 已经是单个 Agent 的精确标识。
- `resolveRoute`：返回当前最适合承载请求的 runtime endpoint；不得返回 COLD / UNREACHABLE / DRAINING 实例。

### 8.3 RuntimeAgentRegistration

字段：

- `runtimeInstanceId`
- `tenantId`
- `agentId`
- `agentCard`
- `a2aEndpoint`
- `healthEndpoint`
- `version`
- `ttl`
- `metadata`

### 8.4 RuntimeRoute

字段：

- `agentId`
- `runtimeInstanceId`
- `a2aEndpoint`
- `state`
- `lastHeartbeatAt`
- `slaSnapshot`

### 8.5 RuntimeState

建议状态：

| 状态 | 含义 |
|---|---|
| `REGISTERING` | 注册中，尚不可接流量。 |
| `COLD` | 已注册但未预热，不可接首 token SLA 流量。 |
| `READY` | 可接流量。 |
| `AT_CAPACITY` | 本地准入拒绝或容量满，不接新流量。 |
| `DRAINING` | 下线中，不接新流量。 |
| `UNREACHABLE` | 租约超时或健康失败。 |
| `DEREGISTERED` | 已注销。 |

## 9. End-To-End Scenarios

### 9.1 一个 runtime 注册一个 Agent

1. runtime 启动，业务 AgentHandler Bean 就绪。
2. runtime 暴露一张 AgentCard。
3. runtime 向 service 注册 `tenantId + agentId + endpoint + AgentCard`。
4. client 可先按 `tenantId` 查询可见 Agent 列表，再用 `tenantId + agentId` 获取该 agent 的 AgentCard 或 route view。
5. 当前验证门面既可返回 route view，也提供一个最小 A2A Gateway 转发入口；目标 Gateway facade service 会在此基础上补齐鉴权、限流、审计、流式背压和生产级故障治理。

### 9.2 两个 runtime 暴露两个 Agent

1. runtime-A 注册 `weather-agent`。
2. runtime-B 注册 `ticket-agent`。
3. client 请求 `weather-agent`，service 路由到 runtime-A。
4. client 请求 `ticket-agent`，service 路由到 runtime-B。
5. 两个 runtime 的 TaskControl / Session / Internal Event Queue 互不共享。

### 9.3 runtime 心跳超时

1. runtime-A 续租停止。
2. service 将 runtime-A 标记为 `UNREACHABLE`。
3. 若存在 runtime-A2 副本，service 重路由。
4. 若不存在副本，返回 `RUNTIME_UNREACHABLE`。

### 9.4 cold runtime

1. runtime 已注册但模型未预热。
2. runtime 状态为 `COLD`。
3. service 不将请求路由给该 runtime。
4. 若无 READY 副本，返回 `RUNTIME_COLD` 或延迟策略；不得假装满足 0.5s SLA。

## 10. Alternatives Considered

| Alternative | Decision | Why |
|---|---|---|
| 单 runtime 下挂多个业务 Agent，用一张 AgentCard 汇总 skills | Rejected | 只能暴露一个 A2A identity，无法让外部按标准发现多个独立 Agent Card。 |
| 单端口多 path 暴露多张 AgentCard | Deferred | 可以工程实现，但会偏离标准 well-known discovery；更适合作为 service 聚合门面的内部扩展。 |
| 多域名/虚拟主机，每个 host 一张 AgentCard | Deferred | 标准性强，但依赖 ingress / DNS / 证书治理，适合作为生产部署增强。 |
| runtime 自注册优先 | Accepted for v1 | 最快支持多 runtime，多 Agent 可见；需要 runtime identity 和租约保护。 |
| K8S informer 优先 | Optional adapter | 企业生产更稳，但首版实现成本高；可作为 self-registration 之后的真相源增强。 |

## 11. Verification Plan

文档检查：

- [ ] `git diff --check -- docs/logs/reviews/2026-06-04-agent-examples-a2a-runtime-registry-facade-proposal.cn.md`
- [ ] `rg -n "AgentCard|自注册|K8S|首 token|0.5s|5 个 9|RuntimeRegistrationApi|AgentDiscoveryApi" docs/logs/reviews/2026-06-04-agent-examples-a2a-runtime-registry-facade-proposal.cn.md`

后续代码实现验收场景：

- [ ] 一个 runtime 注册一个 Agent，service 能在该 `tenantId` 下发现并返回对应 AgentCard。
- [ ] 两个 runtime 注册两个 Agent，service 能按 `tenantId` 列表发现，并按 `tenantId + agentId` 路由到不同 A2A endpoint。
- [ ] Gateway facade 打样入口能按 `tenantId + agentId` 解析 runtime，并把 A2A JSON-RPC body 转发到目标 runtime `/a2a`。
- [ ] runtime 心跳超时后，service 不再路由到该实例。
- [ ] runtime 注册多个副本时，service 能选择健康副本。
- [ ] 端到端 streaming trace 能拆出 service routing、runtime admission、first token 三段耗时。
- [ ] AgentCard 过期、runtime 不可达、agentId 不存在、tenant 不匹配均 fail closed。

## 12. Rollout

- **W1 Design**：本 proposal 评审，确认“每 runtime 单 Agent + agent-examples Gateway facade sample 聚合多 runtime A2A”的方向。
- **W2 ADR**：若评审通过，新增 ADR，正式裁决注册发现职责、SPI home、DFX、dependency allowlist。
- **W3 Contract**：补注册发现契约、AgentCard schema 要求、错误码、trace fields。
- **W4 Implementation**：实现 self-registration adapter、in-memory route view、最小 HTTP 验证门面、A2A Gateway 转发打样、基础白盒与 E2E。
- **W5 Gateway Facade**：增强为生产级 A2A proxy/routing、流式转发、鉴权、限流、审计、灰度与客户 gateway 集成点。
- **W6 Production Hardening**：K8S discovery adapter、可恢复 registry backend、multi-AZ、SLA dashboards、chaos/failover tests。

## 13. Self-Audit

| Finding | Severity | Status | Note |
|---|---|---|---|
| 端到端首 token < 0.5s 对模型侧要求极高 | High | Open | service 只能缩短路由与 admission；模型首 token 需要 runtime 预热和模型能力共同满足。 |
| self-registration 容易滑向自建注册中心 | High | Open | 第一版必须限制为轻量租约视图；生产态需要 K8S/registry 或可恢复后端承接真相源。 |
| 5 个 9 不能由单内存注册表支撑 | High | Open | 文档已明确内存表仅可作为设计验证，生产态必须多副本 + 可恢复/可重建真相源。 |
| 同城 / 异地灾备会放大注册视图一致性问题 | High | Open | 需要在 ADR 中裁决 registry backend、K8S adapter 与异地恢复策略；不能只靠内存表。 |
| 正式 Gateway facade 与 `agent-bus` 的 discovery SPI home 未裁决 | Medium | Open | 本文只建议 service 门面语义；SPI home 需 ADR 裁决。 |
| A2A proxy 是否保持完全协议透明需要后续验证 | Medium | Open | W5 Gateway Facade 必须用 A2A SDK / sample 做端到端验证。 |

## 14. Assumptions

- 这次以 Proposal 评审稿为主，可附带最小 HTTP 验证门面，不提交 ADR。
- `agent-runtime` 继续拥有执行、TaskControl、Session、Internal Event Queue。
- `agent-examples` Gateway facade sample 不暴露多个 runtime 内部状态，只暴露 Agent 列表发现、AgentCard 精确查询、路由和治理入口。
- 自注册为第一版默认；K8S discovery 是后续适配能力，不阻塞第一版文档。
- “首 token 0.5s”按端到端定义，因此 service 与 runtime/model 的时间预算都必须写清楚。

## Authority

- ADR-0159：已归档，只作为历史背景；本轮不依赖它作为实现前置约束，注册、发现与 Gateway facade 以本文 proposal 为本轮评审对象。
- ADR-0016：A2A federation 曾预留 `AgentCard`、`AgentRegistry`、`RemoteAgentClient` 概念。
- GB/T 20988：信息系统灾难恢复规范，按灾难恢复能力等级组织 RTO / RPO / 灾备中心能力。
- JR/T 0044：银行业信息系统灾难恢复管理规范，要求根据信息系统 RTO / RPO 确定灾难恢复能力等级。
- JR/T 0059：证券期货业信息系统备份能力规范；高等级实时系统故障应对能力可达分钟级 RTO 与秒级 RPO。
- JR/T 0168：云计算技术金融应用规范 容灾；金融云平台按灾备等级和 RTO / RPO 建设。
- Rule R-D：后续若声明 SPI，需要同步 module metadata、DFX、contract catalog。
- Rule R-F / R-G：client streaming、cursor flow 与非阻塞外部 I/O 是后续实现的硬约束。
- Rule R-J / R-K：tenant 隔离、取消再授权、本地准入与背压是路由层必须保留的治理点。
