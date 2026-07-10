---
version: 0715
module: agent-bus
feature_type: functional
feature_id: FEAT-013
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
---

# 客户端调用事件转发特性文档

## 1. 特性定位

FEAT-013 定义 `agent-bus` 逻辑域中 gateway 单元与 event-bus 单元协作时，对客户端调用与智能体服务端响应事件的转发事实要求。该特性使 gateway 单元能够把客户端调用封装为可治理的事件消息投递到 event-bus 单元，供智能体服务端消费，并把服务端接受、拒绝、响应、流准备和终态等响应事件转发回 gateway 单元，由 gateway 单元继续向客户端呈现 A2A 兼容的调用表面。

本特性解决的问题是：当客户端不直接连接某个智能体服务端，而是通过 `agent-bus` gateway 进入平台时，调用请求、响应确认、流式订阅准备、失败和超时必须有统一的事件转发语义。调用方不应感知具体服务端实例、物理 endpoint、broker 或内部投递机制；智能体服务端也不应把 `agent-bus` 的转发状态当作自己的 Task 生命周期状态。

本特性所说的 `agent-bus` 是逻辑域，不要求 gateway、event-bus 和 registry-discovery-center 必须由同一个进程、制品或部署单元承载。产品可以提供默认实现，企业客户也可以用存量 API 网关、消息平台或注册发现系统替换其中任一单元；替换前提是这些单元满足本文定义的事件信封、租户、trace、幂等、路由引用、响应状态和 A2A 兼容边界。

对下游设计和实现而言，本特性是 `agent-bus` 客户端调用事件转发的事实来源。L2 设计、网关适配、事件信封、服务端消费、测试和指南必须以本文定义的外部行为、边界和恢复语义为准；实现中已经存在但本文未声明的投递、缓存、流代理或状态能力，不能自动成为当前版本对外事实承诺。

本特性面向以下角色：

- 普通 Agent client：通过 `agent-bus` 网关发起阻塞或流式 Agent 调用。
- `agent-bus` gateway 单元：代理客户端入口，发布调用事件，消费服务端响应事件，并向客户端呈现 A2A 兼容响应。
- `agent-bus` event-bus 单元：承载调用事件与响应事件的 pub-sub 转发、correlation、幂等和投递治理。
- `agent-bus` registry-discovery-center 单元：为 gateway 或 event-bus 提供服务发现、能力路由和 route handle 支撑，但不直接生产或消费本特性的调用/响应事件。
- 智能体服务端：消费调用事件，按自身 Task 生命周期创建、查询、取消或订阅 Task，并发布响应事件。
- 平台集成方：在跨租户、跨服务、跨部署场景下使用事件总线承载调用转发。
- 测试与验收团队：按本特性定义的黑盒行为验证调用事件转发、幂等、超时和流式恢复。

本特性只定义 `agent-bus` 逻辑域中 gateway 与 event-bus 之间的客户端调用事件转发语义。标准 Agent 服务入口和 A2A Task/SSE 语义由 `FEAT-001` 约束；`agent-runtime` 仍拥有 Task 生命周期、Task 状态和服务端 SSE 流。`agent-bus` 不拥有 Task execution state，不承载 token-by-token 服务流，不替代智能体服务端的 A2A 服务入口。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 客户端调用事件发布 | MUST | `agent-bus` 网关组件必须能够把客户端调用封装为事件消息发布到事件总线，供目标智能体服务端消费。 |
| 服务端响应事件转发 | MUST | 智能体服务端必须能够把调用接受、拒绝、失败、同步响应、流准备和终态等响应事实封装为事件消息发布回事件总线，由网关组件消费。 |
| 三单元可替换部署 | MUST | gateway、event-bus、registry-discovery-center 必须作为 `agent-bus` 逻辑域内可独立实现、独立构建、独立部署或由客户存量系统替换的运行时单元来描述；本特性只约束它们之间的契约语义。 |
| 外层 bus 事件信封 | MUST | 调用与响应事件必须使用 `agent-bus` 外层事件信封承载租户、trace、correlation、幂等、路由和 deadline 等治理字段；A2A JSON-RPC 只能作为 payload 或 payload 引用出现，不承担 bus 投递治理职责。 |
| A2A payload 兼容 | MUST | 客户端调用内容和服务端响应内容应保持 A2A JSON-RPC / Task / SSE 语义兼容；网关面对客户端时不得要求客户端理解服务端内部执行框架。 |
| 同步阻塞调用转发 | MUST | 阻塞调用可以通过事件总线投递 A2A 调用信封，并在网关等待窗口内返回最终 A2A 响应、已接受 Task 引用、拒绝、失败或未知状态。 |
| 流式调用请求转发 | MUST | 流式调用的请求事件与阻塞调用一样通过事件总线投递标准化调用信封；实时 token chunk 不得进入事件总线。 |
| A2A SSE 流桥接 | MUST | 流式响应内容必须继续使用智能体服务端的 A2A SSE 语义。事件总线只转发流准备事件和相关引用；网关组件在客户端连接存在时根据该引用与服务端建立 A2A SSE 通道并桥接给客户端。 |
| `INVOCATION_ACCEPTED` 与 `INVOCATION_STREAM_READY` 分离 | MUST | 服务端接受调用并创建 Task 的事实必须与服务端流可订阅的事实分离表达；二者可以连续发生，但不得在语义上互相替代。 |
| `UNKNOWN` 响应状态 | MUST | 网关在接受等待窗口内无法确认服务端是否创建 Task 时，必须向客户端表达 `UNKNOWN` 或等价未知状态，而不得误报成功或失败。 |
| 服务端创建幂等 | MUST | 智能体服务端必须以 `tenantId + idempotencyKey` 为创建类调用幂等键；重复投递或客户端重试不得创建多个 Task，已创建时应返回同一个 `taskId` 或等价接受事实。 |
| bus 投递幂等 | MUST | `agent-bus` 必须用事件 ID、消息 ID、correlation 或幂等键约束事件投递副作用，避免重复投递导致重复可见响应。 |
| gateway 客户端幂等 | MUST | 网关组件必须在客户端重试同一调用时复用或传递同一 `idempotencyKey`，使服务端能够幂等返回同一 Task。 |
| `clientInvocationId` 网关侧关联 | SHOULD | 网关可以向客户端暴露 `clientInvocationId` 作为调用关联句柄，但该 ID 不得成为服务端 Task 生命周期状态，也不得替代 `taskId`。 |
| 标准 Task 查询与订阅 | MUST | `GetTask` 与 `SubscribeToTask` 仍必须基于服务端 `taskId`；当前版本不要求用 `clientInvocationId` 直接查询或订阅服务端 Task。 |
| 大载荷引用 | MUST | 事件总线不承载大对象正文、多模态正文或 token 流；需要携带大载荷时必须使用 `payloadRef` 或等价数据引用。 |
| 租户隔离 | MUST | 调用事件和响应事件必须携带 `tenantId`，并在路由、投递、消费、幂等和审计中作为强制隔离维度。 |
| 物理投递机制透明 | MUST | 客户端和智能体服务端不得依赖具体 broker、数据库 outbox、worker、topic 或物理 endpoint 细节；这些属于 L2 或运行态实现。 |
| registry 支撑但不接管 | MUST | registry-discovery-center 可以为路由选择、route handle 解析和服务健康提供支撑，但不得接管调用事件、响应事件或服务端 Task 生命周期。 |

## 3. 外部接口与入口要求

| 入口 / 信封 | 类型 | 事实要求 |
|---|---|---|
| `AgentBusEventEnvelope` | event envelope | 必须承载事件类型、事件 ID、租户、correlation、trace、幂等、deadline、源、目标路由引用和 payload 描述。它是 gateway 与 event-bus、event-bus 与服务端之间的跨单元契约；字段命名可由 L2 固化，但这些治理语义必须存在。 |
| `CLIENT_INVOCATION_REQUESTED` | client-to-server event | 表达客户端发起一次 Agent 调用、Task 查询、取消或订阅请求。payload 可以是 A2A JSON-RPC request envelope 或 payload 引用。 |
| `CLIENT_INVOCATION_CANCEL_REQUESTED` | client-to-server event | 表达客户端请求取消已有服务端 Task；payload 必须可映射到 A2A `CancelTask` 或等价 Task 引用。 |
| `CLIENT_INVOCATION_QUERY_REQUESTED` | client-to-server event | 表达客户端查询 Task 或任务列表；payload 必须可映射到 A2A `GetTask` / `ListTasks` 等查询语义。 |
| `CLIENT_STREAM_SUBSCRIBE_REQUESTED` | client-to-server event | 表达客户端希望订阅已有服务端 Task 的 A2A SSE 流；必须基于 `taskId`，不得以 `clientInvocationId` 直接替代。 |
| `INVOCATION_ACCEPTED` | server-to-gateway event | 表达服务端已接受调用并创建或复用 Task；必须携带 `taskId`、correlation、idempotency 结果和必要的 Task 可见元数据。 |
| `INVOCATION_REJECTED` | server-to-gateway event | 表达服务端明确拒绝调用且未创建 Task；必须携带可编程拒绝原因。 |
| `INVOCATION_FAILED` | server-to-gateway event | 表达服务端消费调用事件或处理请求时发生确定失败；必须携带错误码和是否可重试的语义。 |
| `INVOCATION_RESPONSE` | server-to-gateway event | 表达阻塞调用在等待窗口内得到一次性 A2A 响应；payload 应为 A2A JSON-RPC response envelope 或等价引用。 |
| `INVOCATION_STREAM_READY` | server-to-gateway event | 表达某个服务端 Task 已有可订阅 A2A SSE 流；必须携带 `taskId` 和网关内部可解析的 stream 引用或订阅条件。 |
| `INVOCATION_TERMINAL` | server-to-gateway event | 表达服务端 Task 已完成、失败、取消或进入终态；用于网关收尾、审计和离线观察，不承载完整 token 流。 |
| A2A JSON-RPC payload | payload | 作为事件 payload 时保持 A2A 请求/响应语义；bus 外层治理字段不得强塞为 A2A 协议字段的唯一事实来源。 |
| A2A SSE stream | service stream | 实时流内容仍由智能体服务端 A2A SSE 提供，网关只在客户端连接存在时桥接，不通过事件总线搬运 token chunk。 |
| route handle / discovery result | routing reference | 可由 registry-discovery-center 产生或解析，用于 gateway/event-bus 定位目标服务；不得向客户端暴露物理 endpoint，也不得携带 Task execution state。 |

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 阻塞调用并返回最终响应 | 客户端通过 `agent-bus` 网关发起阻塞 A2A 调用；目标服务可用 | 网关发布 `CLIENT_INVOCATION_REQUESTED`，服务端消费并完成 Task | 网关在等待窗口内消费 `INVOCATION_RESPONSE`，向客户端返回 A2A 兼容的一次性响应。 |
| 阻塞调用退化为 Task 引用 | 服务端已创建 Task，但阻塞等待窗口内未完成 | 服务端发布 `INVOCATION_ACCEPTED`，但暂未发布最终响应 | 网关向客户端返回已接受 Task 引用；客户端可后续调用 `GetTask` 或 `SubscribeToTask`。 |
| 接受阶段状态未知 | 网关发布调用事件后，在接受等待窗口内未观察到服务端接受、拒绝或失败事件 | 客户端仍在等待阻塞或流式入口建立 | 网关返回 `UNKNOWN` 或等价状态，并提供可用于客户端重试的 `idempotencyKey` / `clientInvocationId` 关联信息。 |
| UNKNOWN 后幂等重试 | 客户端收到 `UNKNOWN`，无法确认服务端是否已创建 Task | 客户端使用同一 `idempotencyKey` 重新发起原调用 | 如果服务端已经创建 Task，必须幂等返回同一 `taskId`；如果未创建，则按新投递创建或明确拒绝。 |
| 流式调用建立 | 客户端通过网关发起流式 A2A 调用，客户端连接仍保持 | 网关发布调用事件；服务端发布 `INVOCATION_ACCEPTED` 和 `INVOCATION_STREAM_READY` | 网关根据 `taskId` 和 stream 引用建立到服务端的 A2A SSE 通道，并把 SSE 流桥接给客户端。 |
| 流式调用接受超时后重连 | 首次流式调用返回 `UNKNOWN` 或连接中断，客户端之后持有 `taskId` 或通过幂等重试获得 `taskId` | 客户端通过网关发起 `SubscribeToTask(taskId)` | 网关发布订阅事件，服务端返回流准备事件后，网关桥接服务端 A2A SSE。 |
| 取消执行中任务 | 客户端已获得服务端 `taskId` | 客户端通过网关请求取消 Task | 网关发布取消事件；服务端按 A2A `CancelTask` 语义处理并发布响应事件；Task 状态仍由服务端拥有。 |
| 查询任务状态 | 客户端已获得服务端 `taskId` | 客户端通过网关请求 `GetTask` | 网关发布查询事件，服务端返回 A2A 兼容 Task 快照响应。 |
| 服务端拒绝调用 | 服务端因鉴权、租户、能力、输入或策略原因拒绝调用 | 服务端发布 `INVOCATION_REJECTED` | 网关向客户端返回可编程拒绝响应；不得伪造成 Task 已创建。 |
| 服务端终态通知 | 服务端 Task 完成、失败或取消 | 服务端发布 `INVOCATION_TERMINAL` | 网关可用于收尾、审计和恢复提示；实时流内容仍以 A2A SSE 或 Task 查询为准。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 生产方与消费方语义

- 客户端调用方向上，事件消息生产方是反向代理客户端的 `agent-bus` gateway 单元，消费方是智能体服务端。
- 服务端响应方向上，事件消息生产方是智能体服务端，消费方是 `agent-bus` gateway 单元。
- event-bus 单元负责在生产方与消费方之间转发事件和维护投递治理语义；它不成为客户端连接 owner，也不成为服务端 Task owner。
- registry-discovery-center 单元可以为路由和目标选择提供支撑，但不是本特性调用/响应事件的生产方或消费方。
- 客户端不直接作为事件总线 consumer；客户端面对的是网关暴露的 A2A 兼容 HTTP / JSON-RPC / SSE 表面。
- 智能体服务端不应感知 `clientInvocationId` 作为业务生命周期状态；它只需要消费租户、correlation、idempotency 和 A2A payload 等通用调用上下文。

#### 5.1.2 可替换单元语义

- `agent-bus` 可以由 gateway、event-bus、registry-discovery-center 三类运行时单元组合而成；三者可以同进程部署，也可以独立部署。
- 企业客户已有 API gateway、消息平台或注册发现中心时，可以通过适配满足本文契约，而不必替换为产品默认实现。
- 单元可替换不改变统一契约：租户、trace、correlation、idempotency、deadline、payloadRef、route handle、响应状态和 A2A 兼容语义必须保持一致。
- 单元拆分不得把内部物理 endpoint、broker topic、注册发现存储结构或 gateway 连接状态暴露为客户端必须感知的事实。

#### 5.1.3 外层事件信封语义

- `agent-bus` 事件信封是跨边界治理事实来源，必须承载 tenant、trace、correlation、idempotency、deadline、route 和 payload 描述。
- A2A JSON-RPC envelope 是调用协议 payload，不替代 bus 事件信封。
- A2A request id 可以与 bus correlation 建立映射，但当前版本不要求二者相等。
- 事件信封不得携带服务端 Task execution state 的写入意图；Task 状态只能由智能体服务端推进。

#### 5.1.4 阻塞调用语义

- 阻塞调用通过事件总线投递调用事件，并由网关等待服务端响应事件。
- 网关等待服务端接受的窗口超时且无法判断是否创建 Task 时，必须返回 `UNKNOWN`。
- 网关已经观察到 `INVOCATION_ACCEPTED` 且获得 `taskId` 后，若最终响应等待超时，不得返回 `UNKNOWN`；应返回已接受 Task 引用或等价 A2A Task 快照。
- 网关在阻塞窗口内观察到最终 `INVOCATION_RESPONSE` 时，向客户端返回 A2A 兼容一次性响应。

#### 5.1.5 流式调用语义

- 流式调用的请求事件与阻塞调用一样通过事件总线投递。
- 服务端接受调用与服务端流可订阅必须分别通过 `INVOCATION_ACCEPTED` 与 `INVOCATION_STREAM_READY` 表达。
- 网关与服务端之间的实时流必须继续使用 A2A SSE。
- 网关只有在客户端与网关的连接仍然存在时才桥接服务端 A2A SSE；如果客户端连接不存在，网关不应长期后台消费并缓存 token 流。
- 客户端可以通过首次 `SendStreamingMessage` 建立流，也可以在获得 `taskId` 后通过 `SubscribeToTask` 重新建立流。

#### 5.1.6 幂等与重试语义

- `agent-bus` 负责事件投递幂等，避免同一事件重复投递产生重复可见副作用。
- 智能体服务端负责创建类调用幂等。同一 `tenantId + idempotencyKey` 的重复调用不得创建多个 Task。
- 网关负责客户端请求幂等。客户端重试同一调用时，网关必须复用或传递同一 `idempotencyKey`。
- `UNKNOWN` 后客户端可以使用同一 `idempotencyKey` 重试原调用；服务端如果已经创建 Task，必须返回同一个 `taskId` 或等价接受事实。
- `SubscribeToTask` 不得暗中创建新 Task；它只订阅已有 `taskId` 的服务端 A2A SSE。

#### 5.1.7 响应状态语义

| 状态 | 事实语义 |
|---|---|
| `COMPLETED_RESPONSE` | 阻塞等待窗口内已获得最终 A2A 响应。 |
| `ACCEPTED_WITH_TASK` | 服务端已创建或复用 Task，并返回 `taskId`，但最终响应尚未完成或不在当前窗口内返回。 |
| `STREAM_READY` | 服务端 Task 的 A2A SSE 流已可订阅，网关可以在客户端连接存在时桥接。 |
| `REJECTED` | 服务端明确拒绝调用，未创建 Task。 |
| `FAILED` | 调用事件或服务端处理发生确定失败。 |
| `UNKNOWN` | 网关未能在接受等待窗口内确认服务端是否已创建 Task；该状态不等同于成功或失败。 |

#### 5.1.8 错误与可观测语义

| 场景 | 事实要求 |
|---|---|
| 调用事件信封非法 | 网关必须拒绝发布或服务端必须拒绝消费，并返回可编程错误；不得创建 Task。 |
| tenant 不匹配 | 必须拒绝路由或消费；不得跨 tenant fallback。 |
| 服务端拒绝 | 必须以 `INVOCATION_REJECTED` 或等价响应表达；不得伪造 `taskId`。 |
| 服务端创建后阻塞等待超时 | 必须返回 `ACCEPTED_WITH_TASK` 或等价 Task 引用；不得误报 `UNKNOWN`。 |
| 接受阶段超时 | 必须返回 `UNKNOWN` 或等价未知状态，允许客户端用同一幂等键重试。 |
| 流准备超时 | 若已有 `taskId`，客户端应可稍后通过 `SubscribeToTask(taskId)` 重连；若没有 `taskId`，进入 `UNKNOWN` 处理。 |
| 实时流中断 | 客户端可通过 `SubscribeToTask(taskId)` 重新订阅；网关不得要求服务端通过 bus 重放 token chunk。 |
| 服务端终态失败 | 必须以 A2A Task 失败语义或 `INVOCATION_TERMINAL` 终态事件表达，并保留 correlation / trace。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| token chunk 事件化 | 不承诺把 token-by-token chunk、SSE frame、artifact 大正文或多模态正文作为事件总线消息体转发。 |
| bus 拥有 Task 状态 | `agent-bus` 不创建、不写入、不推进服务端 Task execution state。 |
| `clientInvocationId` 替代 `taskId` | `clientInvocationId` 只是网关侧关联句柄，不作为 A2A `GetTask` / `SubscribeToTask` 的标准输入。 |
| 隐式重建 Task | `SubscribeToTask` 不得在找不到 Task 时自动重新创建 Task。 |
| 私有流协议 | 当前版本不要求为网关与服务端之间定义 A2A SSE 之外的私有流协议。 |
| 物理 endpoint 暴露 | 客户端不直接消费服务端物理 stream endpoint；网关内部解析 stream 引用并桥接。 |
| 具体 broker 产品 | 本特性不规定具体 broker、topic、queue、outbox、worker 或数据库实现。 |
| 单体部署假设 | 不承诺 gateway、event-bus、registry-discovery-center 必须同进程、同制品或同部署单元。 |
| 默认实现强绑定 | 不要求企业客户必须使用产品自带 gateway、event-bus 或 registry-discovery-center；可用存量系统适配，只要满足本文契约。 |
| registry 参与主流程 | registry-discovery-center 不作为调用/响应事件的生产方或消费方，也不拥有 Task 状态。 |
| 大载荷数据通道 | 事件总线不作为大对象正文或敏感正文的数据通道；大载荷必须走引用路径。 |
| 服务端感知 gateway 生命周期 | 智能体服务端不需要把 `clientInvocationId`、网关连接状态或客户端 HTTP 连接作为自身 Task 生命周期事实。 |
| 非 A2A 查询扩展 | 当前版本不要求新增 `ResolveInvocation` 之类的非 A2A 标准查询方法；UNKNOWN 后通过同一 `idempotencyKey` 重试来恢复。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把本特性作为 `agent-bus` 客户端调用事件转发的事实来源，不得把内部投递机制写成外部调用方必须感知的协议。
- L2 设计必须保留 gateway、event-bus、registry-discovery-center 的可替换边界；默认实现、客户存量系统适配和混合部署都必须对齐同一事件信封与路由契约。
- 网关对客户端暴露的阻塞、查询、取消和流式调用行为必须保持 A2A 兼容，不得要求客户端理解事件总线内部消息格式。
- 服务端消费调用事件后，必须以自身 Task 生命周期语义处理请求，并通过响应事件报告接受、拒绝、失败、响应、流准备和终态事实。
- 实现必须把外层 bus 治理字段与内层 A2A payload 分离；不得把 route、tenant、trace、deadline 和幂等语义只塞进 A2A body 后让 bus 失去治理字段。
- 流式响应实现必须保持“事件总线转发流准备与终态，A2A SSE 承载实时流内容”的边界。
- 测试必须覆盖阻塞成功、阻塞退化为 Task、接受阶段 UNKNOWN、UNKNOWN 后同键重试、流准备、流重连、取消、查询、服务端拒绝、重复投递和租户隔离。
- 文档和指南必须明确：`agent-bus` 是事件转发与治理边界，不是服务端 Task owner，也不是 token stream broker。

## 7. 关联文档

- `architecture/L0-Top-Level-Design/boundaries.md`
- `architecture/L0-Top-Level-Design/glossary.md`
- `architecture/L1-High-Level-Design/agent-bus/README.md`
- `architecture/L1-High-Level-Design/agent-bus/logical.md`
- `architecture/L1-High-Level-Design/agent-bus/process.md`
- `architecture/L1-High-Level-Design/agent-bus/scenarios.md`
- `architecture/L1-High-Level-Design/agent-bus/features/README.md`
- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
