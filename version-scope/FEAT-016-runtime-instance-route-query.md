---
version: 0715
module: agent-bus
feature_type: functional
feature_id: FEAT-016
status: draft
related_docs:
  - ../architecture/L0-Top-Level-Design/boundaries.md
  - ../architecture/L0-Top-Level-Design/glossary.md
  - ../architecture/L1-High-Level-Design/agent-bus/README.md
  - ../architecture/L1-High-Level-Design/agent-bus/logical.md
  - ../architecture/L1-High-Level-Design/agent-bus/process.md
  - ../architecture/L1-High-Level-Design/agent-bus/scenarios.md
  - ../architecture/L1-High-Level-Design/agent-bus/features/README.md
  - ./FEAT-001-standardized-agent-service-entrypoint.md
  - ./FEAT-004-remote-agent-orchestration.md
  - ./FEAT-013-client-invocation-event-forwarding.md
  - ./FEAT-014-a2a-call-event-forwarding.md
---

# 运行时实例路由查询特性文档

## 1. 特性定位

FEAT-016 定义 `agent-bus` 逻辑域中 registry-discovery-center 单元支持运行时实例路由查询的事实要求。`agent-bus` 的逻辑域只包含 gateway、event-bus 和 registry-discovery-center；`agent-runtime` 是与 `agent-bus` 协作的外部逻辑模块，不属于 `agent-bus` 逻辑域。

本特性解决的问题是：当 gateway 或 `agent-runtime` 已经知道目标 `agentId`、`serviceId` 或 `capability`，并且当前调用路径需要点对点或直连通信时，系统必须能够查询该目标当前由哪些运行时实例承载，以及获得可用于通信分发的路由引用。调用方不应把 `agentId`、Agent Card 描述或自然语言能力发现结果误当作物理地址；agent 和 client 也不应感知物理 endpoint、broker topic、数据库 key 或注册发现中心内部结构。

本特性不定义 Agent Card 注册与语义发现。Agent Card 注册与发现回答“有哪些智能体、服务和能力存在，它们会做什么”；FEAT-016 回答“某个已知目标当前由哪些运行时实例可路由承载”。面向 agent 或 client 的脱敏信息只能表达已知目标的路由可用性投影，不替代 Agent Card 发现，也不提供面向 agent/client 的候选发现、能力搜索或语义画像检索。

本特性面向以下角色：

- `agent-bus` gateway 单元：在客户端请求走直连路由时，按已知目标查询运行时实例路由。
- registry-discovery-center 单元：维护运行时路由索引和发现视图，返回租户内可路由实例候选和路由引用。
- `agent-runtime`：在本地 agent 下发下游任务、点对点调用远端 agent 或桥接服务流时，作为 agent 与 registry-discovery-center 之间的代理方发起查询，并负责向 agent 呈现路由可用性投影。
- `agent-core` 实现的 agent：通过 `agent-runtime` 注入的工具查询已知目标的路由可用性，并以 `agentId` 或能力意图表达选择；它不直接调用 registry-discovery-center。
- `agent-client`：可以通过 gateway 或 `agent-runtime` 暴露的路由可用性投影了解已知目标是否可路由，但不通过本特性发现候选 agent/service，也不获得物理路由信息。
- 平台集成方和测试团队：验证路由查询、租户隔离、脱敏、中心不可用降级和路由失败恢复等黑盒行为。

本特性只定义当前版本纳入范围的外部行为和能力边界。标准 Agent 服务入口由 `FEAT-001` 约束；远程 Agent 编排的本地工具化、执行回灌和中断续接由 `FEAT-004` 约束；客户端调用事件转发由 `FEAT-013` 约束；服务间 A2A 调用事件转发由 `FEAT-014` 约束。`agent-bus` 不拥有 Task execution state，不接管 Agent Card 语义发现，不定义 event-bus 查询 registry-discovery-center 的运行态能力。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 已知目标路由查询 | MUST | registry-discovery-center 必须支持在已知 `tenantId + agentId`、`tenantId + serviceId` 或 `tenantId + capability` 的前提下查询可路由运行时实例候选。 |
| 统一查询语义 | MUST | gateway 直连路由和 `agent-runtime` 代理查询必须复用同一类已知目标路由查询语义；二者差异只体现在调用方、权限上下文和结果呈现方式。 |
| `agent-runtime` 代理查询 | MUST | agent 不得直接调用 registry-discovery-center；agent 只能通过 `agent-runtime` 注入的工具表达已知目标路由可用性查询或目标选择，由 `agent-runtime` 代理查询并完成脱敏呈现。 |
| 多实例候选 | MUST | 当多个运行时实例承载同一 `agentId` 或共享同一 `serviceId` 时，查询结果必须能够表达候选集合，而不是要求 `agentId` 或 `serviceId` 在实例维度唯一。 |
| 运行时实例标识 | MUST | 查询结果必须能够区分逻辑服务与具体实例；`serviceId` 是逻辑服务标识，具体实例应有独立的运行时实例标识。 |
| 路由引用 | MUST | 查询结果必须提供可被 gateway 或 `agent-runtime` 用于通信分发的路由引用；该引用对 agent/client 必须保持不透明。 |
| 路由可用性投影 | MUST | 面向 agent/client 的视图只能表达已知目标的 `agentId`、能力、可选 `serviceId`、简化健康状态和版本可用性，不得包含物理 endpoint、topic 或实例地址，也不得扩展为候选发现或能力搜索。 |
| 租户隔离 | MUST | 查询、结果、路由引用和缓存语义必须受 `tenantId` 约束；禁止跨 tenant fallback。 |
| 版本约束 | MUST | 查询结果必须能够表达 `contractVersion` / `capabilityVersion` 的可用性；版本不匹配不得被当作可路由候选。 |
| 健康与可用性 | MUST | 查询结果必须表达候选是否可用于新路由，至少能区分可用、可能不可用、有限可用、暂不可用或版本不匹配等结果语义。 |
| 中心短时不可用降级 | MUST | registry-discovery-center 短时不可用时，gateway 或 `agent-runtime` 可以使用仍满足有效性约束的本地已知路由信息维持已知目标调用；没有可用本地信息时必须显式失败。 |
| 反枚举保护 | MUST | 无权限、跨租户或不可见目标查询不得返回“存在但不可访问”的候选暗示，避免泄漏其他租户的 agent/service 存在性。 |
| Task 状态隔离 | MUST | 路由查询结果不得携带 Task execution state、Task hierarchy、执行进度或 agent 内部编排状态。 |
| 物理细节透明 | MUST | agent/client 不得依赖 Consul、PostgreSQL、PGVector、broker、topic、outbox、worker 或 endpoint 等物理实现细节。 |

## 3. 外部接口与入口要求

| 入口 | 类型 | 事实要求 |
|---|---|---|
| 已知目标路由查询 | registry-discovery-center capability | 按 `tenantId`、`agentId` / `serviceId` / `capability` 和可选版本约束查询运行时实例候选。gateway 直连路由与 `agent-runtime` 代理查询共享该语义；字段命名和协议形态由后续设计固化。 |
| 中心不可用结果 | failure / degraded result | registry-discovery-center 不可用且调用方没有可用本地信息时，必须返回显式失败，而不是猜测路由或跨租户降级。 |

当前版本不强制固化具体 Java 方法、HTTP 路径、工具名、缓存结构或路由策略。本文只约束外部可观察行为、权限边界和结果语义。

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| gateway 直连调用已知 agent | client 通过 gateway 调用某个已知 `agentId` 或 `capability`，当前路径选择直连目标运行时实例 | gateway 查询 registry-discovery-center 或使用仍有效的本地已知路由信息 | gateway 获得可路由候选或明确失败；client 不感知物理 endpoint。 |
| gateway 无可用路由 | 目标 agent/service 不存在、不可见、版本不匹配或无可用实例 | gateway 请求路由查询 | gateway 得到明确不可路由结果，并向上游表达可编程失败，不猜测物理目标。 |
| agent 下发下游任务并已知 agentId | 本地 agent 已通过 Agent Card 发现或会话上下文明确目标 `agentId` | agent 通过 `agent-runtime` 注入工具表达查询或下游任务目标 | `agent-runtime` 代理查询并向 agent 返回该已知目标的路由可用性投影；agent 决策后由 `agent-runtime` 完成路由分发。 |
| 多实例承载同一 agent | 多个运行时实例承载相同 `agentId` 或共享同一 `serviceId` | gateway 或 `agent-runtime` 查询目标 agent 的可路由实例 | 查询结果能够表达多个候选，调用方可获得一个可用于通信分发的目标选择结果或候选集合。 |
| 中心短时不可用且本地信息仍有效 | gateway 或 `agent-runtime` 已有仍满足有效性约束的已知目标路由信息 | 调用方继续处理已知目标调用 | 调用可以在受控窗口内继续；该行为必须可被观察为降级或使用本地已知路由。 |
| 中心不可用且本地信息缺失 | gateway 或 `agent-runtime` 没有目标的可用本地路由信息 | 调用方请求路由查询 | 返回显式 discovery unavailable / no route 语义；不得跨租户 fallback、不得猜测 endpoint。 |
| 路由可用性反馈给 agent | `agent-runtime` 已获得某个已知目标的路由可用性结果 | `agent-runtime` 向 agent 返回简化可用性 | agent 只看到该目标是否可用、可能不可用、有限可用、暂不可用或版本不匹配，不看到运行时实例地址或通信层目标。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 已知目标路由语义

- FEAT-016 只处理已知目标的运行时实例路由查询；目标可以由 `agentId`、`serviceId` 或精确 `capability` 表达。
- 自然语言“找谁会做什么”的 Agent Card 发现、能力画像和工具 schema 发现不属于本特性。
- `agentId` 是智能体对象标识，不是物理地址；agent 可以记住 `agentId`，但实际通信仍必须由 `agent-runtime` 或 gateway 完成路由。
- `serviceId` 是逻辑服务标识，可以被多个运行时实例共享。
- 具体运行时实例标识属于路由查询结果的系统侧事实，不应成为 agent/client 必须理解的语义。

#### 5.1.2 `agent-runtime` 代理语义

- registry-discovery-center 不直接向 agent 暴露工具化接口。
- `agent-runtime` 可以向 agent 注入查询或下游任务选择工具，但该工具是 `agent-runtime` 的对 agent 表面，不是 registry-discovery-center 原始 API。
- agent 使用该工具查询已知目标的简化健康状态、版本可用性或表达下游任务目标。
- `agent-runtime` 代理调用 registry-discovery-center，并把运行时路由结果脱敏为路由可用性投影。
- agent 决策后可以用 `agentId` 表达目标；通信分发由 `agent-runtime` 负责。

#### 5.1.3 gateway 直连路由语义

- gateway 直连路径需要获得目标运行时实例路由信息。
- gateway 面对 client 时不得暴露物理 endpoint、topic、实例地址或 registry 内部结构。
- 查询无结果、版本不匹配、目标不可见或中心不可用且无可用本地信息时，gateway 必须返回明确失败语义。

#### 5.1.4 视图分层与脱敏语义

| 视图 | 面向对象 | 可见内容 | 不可见内容 |
|---|---|---|---|
| 系统路由视图 | gateway / `agent-runtime` | 目标标识、运行时实例候选、路由引用、版本和可用性 | Task execution state、agent 内部编排状态 |
| 路由可用性投影 | agent / client | 已知目标的 `agentId`、能力、可选 `serviceId`、简化健康状态、版本可用性 | 候选发现结果、Agent Card 语义画像、物理 endpoint、topic、实例地址、数据库 key、探活实现 |

- 路由可用性投影的重点是让 agent/client 理解已知目标是否可路由，不是发现新候选，也不是通信层寻址。
- agent/client 看到的是“这个已知目标当前是否可用或可能可用”，不是“还有哪些 agent 可选”，也不是“应该连接哪个物理地址”。
- gateway 和 `agent-runtime` 是最终路由 gate。

#### 5.1.5 可用性状态语义

| 脱敏状态 | 行为语义 |
|---|---|
| 可用 | 可作为新调用候选。 |
| 可能不可用 | 目标存在但健康或新鲜度存在风险；调用方可按自身策略降级或重试。 |
| 有限可用 | 不推荐新任务，可用于受控场景或已有任务延续。 |
| 暂不可用 | 不得用于新路由。 |
| 版本不匹配 | 目标存在但不满足请求版本；不得作为兼容候选使用。 |

- 脱敏状态可以帮助 agent 做任务规划，但不能替代 gateway 或 `agent-runtime` 的最终路由判断。
- 无权限、跨租户或不可见目标不得以“存在但不可用”的形式泄漏。

#### 5.1.6 中心不可用与恢复语义

- registry-discovery-center 短时不可用时，gateway 或 `agent-runtime` 可以使用仍满足有效性约束的本地已知路由信息维持已知目标调用。
- 该降级能力只适用于已知目标和受控窗口，不承诺长期离线工作。
- 没有可用本地信息时必须显式失败。
- 中心恢复后，调用方应重新获得权威路由结果或让后续查询回到正常路径。

#### 5.1.7 错误语义

| 场景 | 事实要求 |
|---|---|
| 查询参数缺少 `tenantId` | 必须 fail-fast，不得进入默认租户或跨租户搜索。 |
| tenant 不匹配 | 必须拒绝查询或结果使用；不得跨 tenant fallback。 |
| 无权限目标 | 不返回存在性暗示；结果应为空或显式权限失败。 |
| 目标不存在 | 返回 no route candidates / not found 语义，不得猜测路由。 |
| 中心不可用且本地信息可用 | 可以返回 degraded / cached route 语义。 |
| 中心不可用且本地信息缺失 | 返回 discovery unavailable / no route 语义。 |
| 版本不匹配 | 返回 version unavailable / incompatible 语义，或从候选中排除。 |
| 路由失败 | 返回 route unavailable 或等价可编程错误；不得向 agent/client 暴露物理失败细节。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| Agent Card 发现 | 不定义 Agent Card 注册、自然语言能力发现、工具 schema 发现、语义画像检索或“谁会做什么”的目录能力。 |
| 面向 agent/client 的候选发现 | 不通过 FEAT-016 向 agent/client 提供“有哪些 agent/service 可选”的发现能力；agent/client 的候选发现应来自 Agent Card 注册与发现特性。 |
| event-bus 查询注册发现中心 | 当前版本不纳入 event-bus 查询 registry-discovery-center 的运行态能力；event-bus 路径按 `FEAT-013` / `FEAT-014` 的事件转发语义约束。 |
| agent 直连 registry | 不允许 agent 直接调用 registry-discovery-center 原始接口；必须经 `agent-runtime` 注入工具或代理表面。 |
| client 获得物理地址 | 不向 client 暴露 endpoint、topic、运行时实例地址、数据库 key 或探活实现细节。 |
| 复杂全局调度 | 不定义资源水位、容量、全局限流、跨区域成本或运维观测驱动的复杂路由策略。 |
| 具体缓存结构 | 不规定缓存键结构、缓存介质、刷新算法、淘汰算法或本地路由策略实现。 |
| 跨租户 fallback | 不承诺任何跨 tenant 查询、缓存复用、降级或 fallback。 |
| Task 状态查询 | 不返回 Task execution state、Task hierarchy、运行进度、上下文记忆或 agent 内部编排状态。 |
| token / payload 通道 | 不承载 token-by-token 流、大对象正文、多模态正文或 payload 数据通道。 |
| 具体存储产品绑定 | 不规定必须使用 Consul、PostgreSQL、PGVector、broker 或某个服务发现产品作为对外事实。 |
| 无界离线工作 | 短时自治只在受控有效性约束内成立，不承诺注册发现中心长期不可用时无限持续路由。 |

## 6. 对下游设计与实现的约束

- 下游设计必须把 FEAT-016 限定为已知目标的运行时实例路由查询，不得把 Agent Card 语义发现写入本文实现范围。
- 下游设计必须保持 `agent-bus` 逻辑域边界：gateway、event-bus、registry-discovery-center 属于 `agent-bus`；`agent-runtime` 是外部协作模块。
- registry-discovery-center 对外承诺的是运行时路由查询事实，不承诺 Task 状态、Agent 业务定义或 agent 内部编排状态。
- `agent-runtime` 注入给 agent 的工具必须是 `agent-runtime` 表面，不能让 agent 绕过 `agent-runtime` 直接访问 registry-discovery-center。
- 面向 agent/client 的路由可用性投影必须与系统路由视图区分，不得泄漏运行时实例地址、endpoint、topic 或探活实现，也不得替代 Agent Card 候选发现。
- 点对点直连路径必须经过 gateway 或 `agent-runtime` 的最终路由 gate；agent 记住 `agentId` 是允许的，但 `agentId` 不得被实现为物理地址。
- 多个运行时实例承载同一 agent 或 service 是正常形态；实现不得要求 `agentId` 或 `serviceId` 在实例维度唯一。
- 测试必须覆盖 gateway 直连查询、`agent-runtime` 代理 agent 查询、多实例候选、中心不可用且本地信息可用、中心不可用且本地信息缺失、租户隔离、版本不匹配、路由可用性投影不泄漏物理信息且不承担 Agent Card 候选发现。
- 文档和指南必须明确：Agent Card 发现告诉 agent “谁会做什么”，FEAT-016 告诉 gateway / `agent-runtime` “这个已知目标当前是否有可路由运行时实例”；两者不能混写。

## 7. 关联文档

- `architecture/L0-Top-Level-Design/boundaries.md`
- `architecture/L0-Top-Level-Design/glossary.md`
- `architecture/L1-High-Level-Design/agent-bus/README.md`
- `architecture/L1-High-Level-Design/agent-bus/logical.md`
- `architecture/L1-High-Level-Design/agent-bus/process.md`
- `architecture/L1-High-Level-Design/agent-bus/scenarios.md`
- `architecture/L1-High-Level-Design/agent-bus/features/README.md`
- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `version-scope/FEAT-004-remote-agent-orchestration.md`
- `version-scope/FEAT-013-client-invocation-event-forwarding.md`
- `version-scope/FEAT-014-a2a-call-event-forwarding.md`
