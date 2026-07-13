---
scope: version-draft
module: agent-bus/r-and-d-center
feature_type: functional
feature_id: Feat-Func-015
status: draft
updated: 2026-07-13
authority:
  - ../architecture/L0-Top-Level-Design
  - ../architecture/L1-High-Level-Design/agent-bus
  - ./FEAT-001-standardized-agent-service-entrypoint.md
  - ./FEAT-013-client-invocation-event-forwarding.md
  - ./FEAT-014-a2a-call-event-forwarding.md
  - ./FEAT-016-runtime-instance-route-query.md
drives:
  - ../architecture/L1-High-Level-Design/agent-bus/logical.md
  - ../architecture/L2-Low-Level-Design/agent-bus/Feat-Func-015-agent-card-registration-and-discovery.md
---

# Agent Card 注册与发现特性文档

## 1. 特性定位

FEAT-015 定义 `agent-bus` 逻辑域中 registry-discovery-center 单元对标准 A2A Agent Card 进行主动注册、持续维护和逻辑发现的事实要求。registry-discovery-center 通过可替换的 `DeploymentDiscoveryProvider` 获取部署环境中可信的 Agent Service 发布事实，依据其中的内部 `baseUrl` 主动抓取 `agent-runtime` 暴露的标准 A2A Agent Card，并通过增量事件和周期全量对账建立、更新或移除租户隔离的 Agent Card 目录；经过授权的调用方可以按 Agent、service、A2A 能力声明、协议版本和执行约束查询零个或多个 Agent Card 候选。

本特性解决的问题是：平台需要持续掌握当前有哪些标准 Agent Card、每张 Card 声明了哪些能力与协议元数据，以及这些声明何时更新或撤销。注册中心必须把可信部署发布事实与 runtime 暴露的 Agent Card 关联起来，在实例扩缩容、滚动升级、事件遗漏、注册中心重启和发布源短时不可用时保持目录事实可恢复、可校验和可审计。

registry-discovery-center 和 `DeploymentDiscoveryProvider` 都是逻辑契约，不要求与 gateway、event-bus 或 `agent-runtime` 由同一个进程、制品或部署单元承载。产品可以提供 Kubernetes 等默认 provider，企业客户也可以适配企业 PaaS、存量注册系统、sidecar 或 CMDB 等可信发布事实源；不同实现必须遵守相同的实例观察、Agent Card 主动抓取、持续对账、租户隔离、逻辑发现和失败语义。

对下游设计和实现而言，本特性是当前版本 Agent Card 注册与逻辑发现能力的事实来源。运行时实例路由查询由 FEAT-016 定义：调用方通过 FEAT-015 获得逻辑 Agent Card 候选并确定 `agentId` 或 `serviceId` 后，再由 gateway 或 `agent-runtime` 按 FEAT-016 查询当前可路由实例。

本特性面向以下角色：

- `DeploymentDiscoveryProvider`：提供租户、service、实例、内部 `baseUrl`、部署版本、发布状态和 source revision 等可信发布事实。
- `agent-runtime`：在标准地址暴露标准 A2A Agent Card，供 registry-discovery-center 主动抓取。
- registry-discovery-center：消费可信发布事实，主动抓取并校验 Agent Card，维护逻辑 Agent Card 目录并处理结构化发现请求。
- 经过授权的调用方：查询满足显式约束的 Agent Card 候选，并根据自身业务规则确定逻辑目标。
- 平台集成方与测试团队：适配部署发布事实源，并验证注册、更新、撤销、恢复、租户隔离、逻辑发现和失败结果。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 部署发布事实接入 | MUST | registry-discovery-center 必须能够通过可替换的 `DeploymentDiscoveryProvider` 获取可信部署环境中的实例全量快照和增量事件；provider 至少提供 `tenantId`、`serviceId`、`instanceId`、内部 `baseUrl`、部署版本、发布状态、source identity 和 source revision。 |
| 标准 A2A Agent Card 主动抓取 | MUST | registry-discovery-center 必须依据 provider 给出的可信内部 `baseUrl`，从标准地址 `/.well-known/agent-card.json` 主动抓取 `agent-runtime` 暴露的标准 A2A Agent Card；抓取和校验通过后建立可发现的 Agent Card 注册记录。 |
| Agent Card 注册校验 | MUST | 注册中心必须校验发布事实来源、抓取目标网络边界，以及 Agent Card 的标准必填字段、schema、协议接口、安全声明和可选签名；校验失败的 Card 不得进入有效发现目录。 |
| Agent Card 安全抓取 | MUST | 主动抓取必须限制 scheme、目标网络、重定向、响应大小和超时；平台可以按部署要求使用 mTLS 或 Agent Card 签名验证，未通过校验的响应不得覆盖有效快照。 |
| 多实例发布去重 | MUST | 同一逻辑 Agent Card 可以由多个运行实例同时发布；注册中心必须关联全部可信发布来源，并将内容和版本相同的 Card 作为一个逻辑候选返回。 |
| 多版本共存 | MUST | 同一 Agent Service 在滚动升级期间可以同时发布多个有效 Agent Card 版本；查询必须按调用方声明的版本约束过滤，并返回明确的版本不可用结果。 |
| 事件监听与周期全量对账 | MUST | 注册中心必须同时支持 provider 增量事件监听和周期全量 reconciliation；事件中断、source revision 缺口或注册中心重启后，必须能够通过全量快照恢复 Agent Card 注册目录。 |
| 更新、撤销与失效 | MUST | provider 发布事实或 Card 内容变化时，注册中心必须重新抓取并原子更新有效快照；当某个 Card 已不存在可信发布来源或被明确撤销时，必须将其从有效发现目录移除。 |
| 最后有效快照 | MUST | 已注册 Card 刷新失败或 provider 短时不可用时，注册中心必须保留最后一次校验成功的快照、标记新鲜度并受控重试；发布事实恢复后通过全量对账重新收敛。 |
| 注册发现实现可替换 | MUST | registry-discovery-center 与 `DeploymentDiscoveryProvider` 必须支持按部署环境替换；不同实现必须遵守相同的主动抓取、持续对账、逻辑发现和失败契约。 |
| 租户与调用方边界 | MUST | provider 发布事实、Card 快照和注册记录必须关联可信 `tenantId`；查询必须携带可信调用方上下文，并用于可见性、授权和审计。 |
| 确定性结构化查询 | MUST | 经过授权的调用方必须能够按 tenant、`agentId` / `serviceId` / `a2aSkillId`、协议版本、Card 版本、A2A capabilities、标签、输入输出模式和安全方案查询 Agent Card。 |
| 逻辑候选集合返回 | MUST | 注册中心必须返回零个或多个满足全部显式约束的逻辑 Agent Card 候选；候选以 Card 身份和版本去重，不按运行实例展开。 |
| 明确结果与失败 | MUST | 无匹配 Card、版本不可用、声明约束不满足、发布源不可用、Card 抓取或校验失败、租户拒绝和注册中心不可用必须具有可区分的结构化结果。 |
| 审计与可观测 | SHOULD | 发布事实观察、Card 抓取、校验、reconciliation、注册状态变更和发现查询应产生可审计记录，至少关联 tenant、source、operation、Agent / service、Card digest、版本、freshness、trace、结果和耗时。 |

## 3. 外部接口与入口要求

FEAT-015 的外部接口由 Agent Card 发现服务 API 和部署发现扩展 SPI 组成。发现请求、候选、结果和失败结构是服务 API 的输入输出契约，其字段与行为语义在第五章定义；Agent Card 的注册、更新与下线由部署发现扩展 SPI、标准 Agent Card 抓取和注册中心内部对账共同完成。

### 3.1 Agent Card 发现服务 API

| API | API 类型 | 调用方 | 事实要求 |
|---|---|---|---|
| `DiscoverAgentCards(AgentCardDiscoveryQuery) -> AgentCardDiscoveryResult` | registry-discovery-center 服务 API | 经过授权的 gateway、`agent-runtime` 或其他平台调用方 | 注册中心必须根据可信调用上下文、逻辑目标字段、版本和 Agent Card 声明约束执行确定性过滤，并返回零个或多个按逻辑 Card 身份和版本去重的候选以及稳定分页信息。正常的零候选情况通过 `AgentCardDiscoveryResult.outcome` 表达；请求、授权、deadline 或服务故障通过结构化发现失败表达。服务传输协议和物理 endpoint 由下游设计在保持该输入、输出和失败语义的前提下固化。 |

### 3.2 部署发现扩展 SPI

| API | API 类型 | 实现方 | 事实要求 |
|---|---|---|---|
| `DeploymentDiscoveryProvider` | registry-discovery-center SDK 可扩展开发 SPI | Kubernetes、企业 PaaS、存量注册系统、sidecar 或 CMDB 等部署发布事实适配器 | SDK 必须提供 `listDeploymentInstances(context) -> full snapshot + sourceRevision`，用于返回当前权威实例全量快照；并提供 `watchDeploymentInstances(context, startRevision) -> deployment instance event stream`，用于从指定 revision 监听 `ADDED`、`MODIFIED`、`TERMINATING` 和 `DELETED` 等变化。实现必须返回可信 source identity、连续 source revision 和部署实例观察事实；事件不可恢复或 revision 不连续时必须明确通知注册中心重新读取全量快照。具体语言类型和异步模型由下游 SDK 设计固化。 |

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| Agent Card 首次注册 | provider 观察到 ready 的 Agent Service 实例，并提供可信 tenant、service、instance 和内部访问地址 | registry-discovery-center 主动抓取并校验标准 A2A Agent Card | 校验成功后建立逻辑 Agent Card 注册记录，并进入当前 tenant 与授权调用方的发现目录。 |
| Agent Card 抓取或校验失败 | 发布实例已被观察，但 Card 不可达、内容非法或来源不可信 | 注册中心记录结构化失败并按策略重试 | 该发布来源在校验成功前不形成有效 Agent Card 候选；后续成功后可以完成注册。 |
| 多实例发布同一 Card | 同一 Agent Service 的多个实例发布内容和版本相同的 Agent Card | provider 报告实例扩容、缩容或替换 | 注册中心维护多个可信发布来源，但发现结果只返回一个逻辑 Agent Card 候选。 |
| 滚动升级与多版本共存 | 同一 Agent Service 的新旧实例同时发布不同 Card 版本 | 注册中心抓取并校验各版本 Card | 有效版本分别进入目录；调用方按显式版本约束获得对应逻辑候选。 |
| Agent Card 内容更新 | 已注册发布来源仍然存在，但 Card 内容发生变化 | 注册中心重新抓取、校验并原子更新有效快照和发现索引 | 后续查询使用新 Card 事实；刷新失败时保留最后有效快照并反映新鲜度。 |
| 发布来源撤销 | provider 确认实例终止或删除 | 注册中心移除该实例对应的发布来源，并重新计算逻辑注册记录 | 仍有其他可信来源发布同一 Card 时继续保留逻辑候选；最后一个来源被确认移除后，该 Card 退出有效发现目录。 |
| 部署发现中断与恢复 | provider 事件中断、注册中心重启或 source revision 不连续 | 注册中心保留最后有效 Card 目录；恢复后获取完整实例快照并执行 reconciliation | 目录不会因短时中断整体丢失，恢复后依据权威发布事实重新收敛。 |
| 结构化 Agent Card 发现 | 调用方持有可信上下文，并提供 `agentId`、`serviceId` 或 `a2aSkillId` 及可选约束 | 调用方执行 `DiscoverAgentCards` | 注册中心返回满足全部显式约束的逻辑 Agent Card 候选，或者返回可区分的零候选结果。 |
| Agent Card 发现后调用 | 调用方已经从 015 获得并确定逻辑 `agentId` 或 `serviceId` | gateway 或 `agent-runtime` 使用已知目标发起 FEAT-016 运行时实例路由查询 | FEAT-016 返回该逻辑目标当前的运行实例路由结果，调用方据此继续调用或处理无可用路由结果。 |
| 跨租户或无权限查询 | 请求 tenant 与可信调用方上下文不一致，或 caller 无权查看目标 Card | 注册中心执行 tenant 与 caller 校验 | 请求被明确拒绝，不返回 Agent Card、候选数量或目标存在性。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 事实来源与所有权语义

- `DeploymentDiscoveryProvider` 提供 Agent Service 发布实例事实，包括可信内部抓取地址和发布状态。
- `agent-runtime` 通过标准地址暴露当前有效的标准 A2A Agent Card。
- registry-discovery-center 将可信发布事实与校验成功的 Card 快照关联为逻辑 `AgentCardRegistration`，并维护 Agent Card 发现目录。
- 当前版本按“一个 `serviceId` 暴露一张 Agent Card”建立逻辑身份。注册中心基于可信 `tenantId + serviceId` 形成稳定 `agentId`，Card 的 `name` 作为展示信息。
- 同一逻辑 Card 可以关联多个发布实例；发布实例的变化用于触发 Card 抓取、更新和撤销，发现结果按逻辑 Card 身份和版本去重。
- 运行实例的可路由状态和路由引用由 FEAT-016 维护。Agent Card 注册状态只表达 Card 声明是否已经通过可信来源注册并可以被发现。

`DeploymentInstanceObservation` 表达 `DeploymentDiscoveryProvider` 提供的单个 Agent Service 发布实例事实，其字段语义如下：

| 字段 | 要求 | 事实语义 |
|---|---|---|
| `tenantId` | MUST | 发布实例所属的可信租户边界，用于注册、发现、对账和审计隔离。 |
| `serviceId` | MUST | Agent Service 的稳定逻辑服务标识，用于关联 Agent Card 和运行实例发布事实。 |
| `instanceId` | MUST | 当前部署环境内稳定的发布实例标识。`tenantId + serviceId + instanceId` 构成发布事实对账身份。 |
| `baseUrl` | MUST | 注册中心在可信内部网络中抓取标准 Agent Card 的实例地址。 |
| `deploymentVersion` | MUST | 当前实例的部署版本，用于识别滚动升级和触发 Card 重新校验。 |
| `publicationStatus` | MUST | 表达实例的 ready 或 terminating 等发布状态，用于决定建立、保留或撤销发布来源。 |
| `sourceId` | MUST | 产生该观察事实的 provider 或部署发布事实源身份。 |
| `sourceRevision` | MUST | 该事实在当前 source 中的单调版本或等价顺序标识，用于增量连续性检查和全量对账。 |
| `observedAt` | MUST | provider 观察到该发布事实的时间，用于判断事实时效和审计关联。 |

#### 5.1.2 Agent Card 与字段语义

当前版本的 Agent Card 表面固定到项目采用的 `org.a2aproject.sdk:a2a-java-sdk-server-common:1.0.0.CR1`。注册中心必须按该版本及平台启用的 A2A profile 校验 Card。

| Agent Card 字段 | 要求 | 事实语义 |
|---|---|---|
| `name` | MUST | Agent 的人类可读名称，必须存在且非空；用于展示。 |
| `description` | MUST | Agent 的人类可读说明，必须存在且非空。 |
| `supportedInterfaces` | MUST | 必须存在且至少包含一个结构合法、协议版本明确的标准接口声明。 |
| `version` | MUST | Agent Card 级版本，注册中心将其映射为 `capabilityVersion`；作用于整张 Card 及其中全部 A2A 能力声明。 |
| `capabilities` | MUST | 标准 A2A 协议和执行特征声明，如 streaming、push notifications 和 extended card。 |
| `defaultInputModes` | MUST | Agent 默认支持的输入媒体类型，必须存在且至少包含一种模式。 |
| `defaultOutputModes` | MUST | Agent 默认支持的输出媒体类型，必须存在且至少包含一种模式。 |
| `skills` | MUST | 标准 A2A `AgentSkill` 列表，字段必须存在但可以为空；空列表仍可按 `agentId` 或 `serviceId` 查询。 |
| `provider`、文档地址、图标、安全声明、签名及其他标准可选字段 | MAY | 注册中心按原始快照保存，并在授权边界内提供。 |
| 兼容 `url` | MAY | SDK 兼容字段；对调用方可见时必须是平台批准的逻辑入口。 |

`AgentSkill` 是 A2A 标准类型。本文中文统一称其为“A2A 能力声明”，以区别于 Agent 内部可直接执行的 Skill 或 Tool。

| `AgentSkill` 字段 | 要求 | 事实语义 |
|---|---|---|
| `id` | MUST | 当前 Card 内稳定且唯一的 A2A 能力声明标识；发现接口使用语义名 `a2aSkillId` 与其对应。 |
| `name` | MUST | A2A 能力声明的人类可读名称。 |
| `description` | MUST | 对该 Agent 擅长处理事项的描述。 |
| `tags` | MUST | 结构化标签集合，用于显式标签约束匹配。 |
| `examples` | MAY | 该能力适用的示例表达，作为 Card 元数据保存。 |
| `inputModes` / `outputModes` | MAY | 对 Card 默认输入输出模式的覆盖；未声明时继承 Card 默认模式。 |
| 其他标准可选字段 | MAY | 按 A2A profile 校验和保存。 |

- 同一 Card 中的多个 `AgentSkill` 共用该 Card 的 Agent Service 调用入口。
- 查询命中 `a2aSkillId` 后，调用方先获得对应 Agent Card，再以该 Card 的逻辑 `agentId` 或 `serviceId` 进入 FEAT-016 路由查询。
- 注册中心保存校验成功的原始 Card 快照及内容摘要，并通过摘要识别内容变化。

#### 5.1.3 主动注册、更新与撤销语义

- 正式注册路径是 `DeploymentDiscoveryProvider + registry reconciliation`。注册中心观察到 ready 发布实例后，从可信 `baseUrl + /.well-known/agent-card.json` 主动抓取标准 Agent Card。
- 抓取必须校验可信来源和网络边界，并执行 schema、接口、版本、安全声明和可选签名校验。
- 首次抓取成功后，注册中心建立发布来源与逻辑 `AgentCardRegistration` 的关联；同一 Card 的重复事件和相同 source revision 必须得到等价结果。
- provider source revision、实例部署版本或 Card digest 变化时，注册中心重新抓取并校验 Card；成功后原子更新 Card 快照、A2A 能力声明索引、版本、revision 和新鲜度。
- 已注册 Card 刷新失败时，注册中心保留最后一次校验成功的快照并按受控退避重试，失败响应不得覆盖有效快照。
- provider 确认发布实例终止或删除时，注册中心移除对应 source reference；逻辑 Card 仍有其他可信发布来源时继续保留，最后一个来源被确认移除后退出有效发现目录。
- provider 短时不可用或 revision 连续性中断时，注册中心保留最后有效目录并标记 source stale；provider 恢复后先执行全量 reconciliation，再更新注册目录。

#### 5.1.4 注册状态与新鲜度语义

| `registrationStatus` | 事实语义 |
|---|---|
| `PENDING` | 已观察到发布来源，Card 尚未成功抓取和校验；不进入普通发现结果。 |
| `REGISTERED` | Card 已通过可信来源注册和校验，可以进入发现结果。 |
| `REMOVED` | 已确认不存在可信发布来源或 Card 已撤销；退出有效发现目录，只保留必要审计事实。 |

| `freshness` | 事实语义 |
|---|---|
| `FRESH` | Card 快照和发布来源基于连续的最新 observation 或最近一次全量 reconciliation。 |
| `STALE_SOURCE` | provider 暂时不可用或 source revision 连续性待恢复，当前使用最后有效发布事实。 |
| `STALE_CARD` | 发布来源仍存在，但最近一次 Card 刷新失败，当前使用最后一次校验成功的 Card 快照。 |

- registration status 与 freshness 分别表达 Card 是否完成有效注册、当前快照是否基于最新可验证事实。
- `STALE_SOURCE` 或 `STALE_CARD` 的 Card 可以在受控有效期内继续被发现，并必须携带 `freshness` 与 `lastValidatedAt`。
- 运行实例健康状态、可路由性和路由有效期通过 FEAT-016 查询，不从 registration status 或 freshness 推导。

#### 5.1.5 查询、过滤与候选语义

调用方必须至少提供 `agentId`、`serviceId` 或 `a2aSkillId` 中的一项作为逻辑发现条件；`a2aSkillId` 对应标准 Agent Card 的 `skills[].id`。

| 查询字段 | 事实语义 |
|---|---|
| `RegistryRequestContext` | 必须提供可信 `tenantId`、`callerRef`、`traceId`、`requestId` 和 `deadline`。 |
| `agentId` / `serviceId` / `a2aSkillId` | 至少提供一项；多项同时存在时按 AND 语义组合。 |
| `requiredSkillTags` | 可选结构化标签硬约束；命中的 A2A 能力声明必须包含全部指定标签。 |
| `contractVersion` | 可选 A2A 协议版本硬约束，按 Card 的标准接口声明过滤。 |
| `capabilityVersion` | 可选 Card 级版本硬约束，对应 `AgentCard.version`。 |
| `requiredCapabilities` | 可选 A2A capability 硬约束，按 Card 显式声明过滤。 |
| `requiredInputModes` / `requiredOutputModes` | 可选媒体类型硬约束；优先使用命中 `AgentSkill` 的覆盖值，未覆盖时使用 Card 默认值。 |
| `requiredSecuritySchemes` | 可选安全兼容性硬约束，只判断 Card 是否声明调用链所需认证方式。 |
| `limit` / `continuationToken` | 可选分页约束；token 必须绑定 tenant、caller 和查询条件。 |

- 查询依次应用 tenant / caller 可见性、目标字段、协议版本、Card 版本、A2A capabilities、标签、输入输出模式和安全方案约束。
- `skills = []` 的 Card 可以通过 `agentId` 或 `serviceId` 查询，但不能响应 `a2aSkillId` 或 skill tag 查询。
- `AgentCardCandidate` 包含授权调用方可见的 Agent Card 元数据、逻辑身份、版本、freshness、`lastValidatedAt` 和可选命中的 `a2aSkillId`，不按发布实例展开。
- 同一逻辑 Card 的多个发布来源合并为一个候选；不同有效 Card 版本可以形成不同候选。
- 候选结果使用稳定、确定性的顺序和分页语义，调用方依据自身规则确定逻辑目标。

#### 5.1.6 结果、错误与可观测语义

`AgentCardDiscoveryResult` 是 `DiscoverAgentCards` 正常完成后的返回结构：

| 字段 | 事实语义 |
|---|---|
| `outcome` | 表达发现处理的业务结果，取值为 `SUCCESS`、`NO_MATCH`、`VERSION_UNAVAILABLE` 或 `CONSTRAINT_UNAVAILABLE`。 |
| `candidates` | 零个或多个 `AgentCardCandidate`；每个候选遵守 5.1.5 的逻辑身份、版本、可见性和去重语义。 |
| `nextToken` | 存在后续结果页时返回的继续查询标识；必须绑定 tenant、caller 和原查询条件。没有后续结果时为空。 |
| `traceId` | 关联本次发现请求、结果、日志和审计事实的追踪标识。 |

查询成功但没有候选是正常的结构化结果。当前版本按以下过滤阶段返回唯一的 `AgentCardDiscoveryResult.outcome`：

| outcome | 事实语义 |
|---|---|
| `SUCCESS` | 查询成功并返回一个或多个满足全部显式约束的 Agent Card 候选。 |
| `NO_MATCH` | 当前 tenant 与 caller 的可见范围内不存在匹配 `agentId`、`serviceId` 或 `a2aSkillId` 的有效 Card 注册事实。 |
| `VERSION_UNAVAILABLE` | 存在匹配逻辑 Card，但没有 Card 同时满足 `contractVersion` 和 `capabilityVersion` 约束。 |
| `CONSTRAINT_UNAVAILABLE` | 版本匹配，但没有 Card 同时满足 A2A capabilities、skill tags、输入输出模式或安全方案约束。 |

| 场景 | 失败语义 |
|---|---|
| 查询缺少目标字段、字段组合非法或分页 token 不匹配 | `INVALID_QUERY`。 |
| caller 未授权或 tenant 不匹配 | `CALLER_NOT_AUTHORIZED` 或 `TENANT_SCOPE_DENIED`。 |
| provider 不可访问或 source revision 不连续 | reconciliation 返回 `DEPLOYMENT_SOURCE_UNAVAILABLE` 或 `SOURCE_REVISION_GAP`，已有目录按 source stale 语义处理。 |
| Card 不可达、来源拒绝、schema 非法或签名非法 | 分别返回 `AGENT_CARD_FETCH_FAILED`、`AGENT_CARD_SOURCE_REJECTED`、`AGENT_CARD_INVALID` 或 `AGENT_CARD_SIGNATURE_INVALID`。 |
| 操作超过 deadline 或 registry 自身不可用 | 返回 `DEADLINE_EXCEEDED` 或 `REGISTRY_UNAVAILABLE`，并准确标记 `retryable`。 |

`AgentCardDiscoveryFailure` 是 `DiscoverAgentCards` 未能正常完成时的失败结构：

| 字段 | 事实语义 |
|---|---|
| `failureCode` | 可编程失败码，用于区分请求、授权、tenant、deadline 和 registry 服务故障。 |
| `description` | 面向调用方的可编程失败说明，不包含其无权观察的 Card、租户或内部实现信息。 |
| `retryable` | 表达调用方能否在保持相同业务查询语义的情况下重试。 |
| `traceId` | 关联失败响应、日志和审计事实的追踪标识。 |

`AgentCardDiscoveryFailure.failureCode` 当前取值为 `INVALID_QUERY`、`CALLER_NOT_AUTHORIZED`、`TENANT_SCOPE_DENIED`、`DEADLINE_EXCEEDED` 或 `REGISTRY_UNAVAILABLE`。部署发布事实、Card 抓取和注册校验相关失败用于 reconciliation、注册状态与可观测结果，并通过 freshness 和有效目录事实影响后续发现结果。

- 发布事实观察、Card 抓取、校验、reconciliation、注册状态和 freshness 变化以及发现查询必须产生可审计事实。
- 日志、指标和审计必须保护 provider 内部地址、凭据以及调用方无权观察的 Card 扩展信息。

### 5.2 职责协作边界

| 协作事项 | 责任归属 |
|---|---|
| Agent Card 内容 | `agent-runtime` 按标准 A2A 结构提供，registry-discovery-center 校验、保存并建立逻辑发现索引。 |
| 部署发布事实 | `DeploymentDiscoveryProvider` 提供可信实例观察，registry-discovery-center 用其定位 Card、触发刷新并维护发布来源关联。 |
| 逻辑 Agent Card 发现 | FEAT-015 按显式结构化条件返回 Agent Card 候选。 |
| 运行时实例路由 | FEAT-016 根据已知 `agentId`、`serviceId` 或精确 capability 返回当前运行实例路由结果。 |
| 逻辑目标选择 | gateway、`agent-runtime` 或其他授权调用方依据自身业务规则完成。 |
| 调用凭据 | gateway、event-bus 的安全组件或企业凭据基础设施负责获取、轮换和注入。 |
| Task / Run 状态 | `agent-runtime` 及其执行组件负责维护。 |

## 6. 对下游设计与实现的约束

- 下游设计必须把本文作为 Agent Card 注册与逻辑发现的事实来源，并保持主动抓取、校验、持续对账、注册状态、freshness、结构化查询和失败语义一致。
- `DeploymentDiscoveryProvider` 提供发布事实，`agent-runtime` 提供标准 Agent Card，registry-discovery-center 维护逻辑 `AgentCardRegistration` 与发现目录。
- 同一 Card 的多个发布实例必须合并为逻辑候选；不同 Card 版本可以共存，发布来源变化不得直接复制为面向调用方的实例候选。
- 查询实现必须强制执行 tenant、caller、`agentId` / `serviceId` / `a2aSkillId`、协议版本、Card 版本、A2A capabilities、标签、输入输出模式和安全方案约束。
- FEAT-015 发现结果不得包含运行实例健康状态、实例标识或路由引用。调用方确定逻辑目标后，必须按 FEAT-016 获取运行时实例路由事实。
- 安全实现必须校验 Agent Card 的 security scheme 与 requirement 关联，实际凭据由调用链安全组件处理。
- 测试必须覆盖首次注册、抓取与校验失败、多实例去重、滚动升级、多版本共存、Card 更新、最后发布来源撤销、漏事件对账、provider 不可用、结构化发现、无候选结果和租户拒绝。
- 文档和指南必须统一使用标准 Agent Card、A2A 能力声明、`AgentCardRegistration`、`AgentCardDiscoveryQuery`、`AgentCardCandidate`、`a2aSkillId`、registration status 和 freshness 术语，并明确 FEAT-015 的逻辑发现结果与 FEAT-016 的运行时实例路由结果。

## 7. 关联文档

- `architecture/L0-Top-Level-Design/boundaries.md`
- `architecture/L0-Top-Level-Design/glossary.md`
- `architecture/L1-High-Level-Design/agent-bus/README.md`
- `architecture/L1-High-Level-Design/agent-bus/logical.md`
- `architecture/L1-High-Level-Design/agent-bus/process.md`
- `architecture/L1-High-Level-Design/agent-bus/scenarios.md`
- `architecture/L1-High-Level-Design/agent-bus/features/README.md`
- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `version-scope/FEAT-013-client-invocation-event-forwarding.md`
- `version-scope/FEAT-014-a2a-call-event-forwarding.md`
- `version-scope/FEAT-016-runtime-instance-route-query.md`
