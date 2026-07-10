---
version: 0715
module: agent-bus
feature_type: functional
feature_id: FEAT-014
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
  - ./FEAT-005-remote-agent-orchestration.md
  - ./FEAT-013-client-invocation-event-forwarding.md
---

# A2A 调用事件转发特性文档

## 1. 特性定位

FEAT-014 定义 `agent-bus` 逻辑域中 event-bus 单元承载智能体服务之间 A2A 调用事件转发时的事实要求。该特性使调用方智能体服务端能够把面向远端智能体服务端的 A2A 调用封装为可治理的事件消息，经 event-bus 投递到目标服务，并把目标服务的接受、拒绝、失败、响应、流准备和终态等响应事实转发回调用方服务端。

本特性解决的问题是：当一个智能体服务在执行过程中需要调用另一个智能体服务，而双方不应直接感知具体物理 endpoint、broker、数据库 outbox、topic、worker 或部署细节时，需要一个统一的 A2A 调用事件转发语义。调用方服务端应只依赖 route handle、correlation、幂等键、租户和 A2A payload 语义；被调用方服务端仍按自身 A2A Task 生命周期处理请求；`agent-bus` 不应成为跨服务编排 owner、Task owner 或 token stream broker。

本特性所说的 A2A 调用事件转发属于 `agent-bus` 的真 bus / event-bus 逻辑范围。当前主生产方是调用方智能体服务端或调用方 `agent-runtime`，主消费方是被调用方智能体服务端或远端 `agent-runtime`。未来如果需要中心化编排，可以把编排能力实现为一个特殊的编排智能体服务，该服务仍通过 `agent-runtime + agent-core` 拥有自己的 Task 生命周期，并作为调用方复用本文定义的 A2A 调用事件转发能力；这不意味着 `agent-bus` 本身获得编排状态或 Task 状态所有权。

本特性只定义 A2A 调用事件转发的外部行为、边界、状态语义和验收要求。L2 设计、当前 forwarding substrate、未来 broker 接线、runtime 适配、测试和指南必须服从本文定义的边界与外部行为；实现中已经存在但本文未声明的 outbox、inbox、worker、topic、流代理、缓存、重放或状态能力，不能自动成为当前版本对外事实承诺。

本特性面向以下角色：

- 调用方智能体服务端：在本地 Task 执行过程中发起远端 A2A 调用、查询、取消或订阅。
- 被调用方智能体服务端：消费 A2A 调用事件，按自身 Task 生命周期创建、查询、取消或订阅 Task，并发布响应事件。
- `agent-bus` event-bus 单元：承载 A2A 调用事件与响应事件的转发、correlation、幂等、投递治理和审计线索。
- `agent-bus` registry-discovery-center 单元：为调用方或 event-bus 提供 route handle、服务能力路由和健康信息支撑，但不直接定义 Agent Card 发现或生产 / 消费本文的调用事件。
- `agent-runtime` / `agent-core` 实现方：把远端 A2A 响应映射为本地执行继续、挂起、失败、查询、取消或订阅，但这些本地回灌细节不由本文定义。
- 平台集成方：在跨服务、跨部署或跨信任边界场景下，用 event-bus 承载服务间 A2A 调用控制面。
- 测试与验收团队：按本文定义的黑盒行为验证服务间 A2A 调用事件转发、幂等、超时、流准备、重试和租户隔离。

本特性不替代 `FEAT-001` 定义的标准 Agent 服务入口，也不替代 `FEAT-005` 中 runtime 侧远程 Agent 编排的本地回灌、中断续接和工具化适配。`agent-runtime` 仍拥有本地 Task 生命周期和远端 Task 引用的本地绑定关系；被调用方 `agent-runtime` 仍拥有远端 Task 生命周期、Task 状态和 A2A SSE 流；`agent-bus` 只治理跨服务事件转发和窄控制通道。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| A2A 调用事件发布 | MUST | 调用方智能体服务端必须能够把面向远端智能体服务端的 A2A 调用封装为事件消息发布到 event-bus，供目标服务消费。 |
| A2A 响应事件回传 | MUST | 被调用方智能体服务端必须能够把调用接受、拒绝、失败、同步响应、流准备和终态等响应事实封装为事件消息发布回 event-bus，由调用方服务端消费。 |
| 调用方 runtime 作为主生产方 | MUST | 当前版本的服务间 A2A 调用事件以调用方 `agent-runtime` 为主生产方；`agent-bus` 不作为中心化编排 owner。 |
| 未来编排智能体复用 | SHOULD | 未来中心化编排应建模为特殊编排智能体服务复用本文能力，而不是让 `agent-bus` 直接拥有跨服务 Task tree 或编排状态。 |
| 外层 bus 事件信封 | MUST | 调用与响应事件必须使用 `agent-bus` 外层事件信封承载租户、trace、correlation、幂等、路由、deadline、源和目标等治理字段；A2A JSON-RPC 只能作为 payload 或 payload 引用出现。 |
| A2A payload 兼容 | MUST | 调用、查询、取消、订阅和响应 payload 应保持 A2A JSON-RPC / Task / SSE 语义兼容；调用方服务端不得要求被调用方理解本地执行框架或本地工具调用状态。 |
| 创建类 A2A 调用转发 | MUST | event-bus 必须支持 `message/send` 或等价创建 / 推进远端 Task 的 A2A 调用事件转发。 |
| 流式 A2A 调用请求转发 | MUST | event-bus 必须支持流式 A2A 调用请求事件转发；实时 token chunk、SSE frame 或大正文不得进入 event-bus。 |
| A2A SSE 点对点桥接 | MUST | 流式响应内容必须继续使用被调用方服务端的 A2A SSE 语义。event-bus 只转发流准备事件和相关引用；调用方服务端在需要消费实时流时，根据 `taskId` 和 stream 引用与被调用方服务端建立点对点 A2A SSE 通道。 |
| `A2A_CALL_ACCEPTED` 与 `A2A_STREAM_READY` 分离 | MUST | 被调用方接受调用并创建或复用 Task 的事实必须与远端流可订阅的事实分离表达；二者可以连续发生，但不得在语义上互相替代。 |
| 远端 `taskId` 返回 | MUST | 创建类 A2A 调用被接受时，被调用方响应事件必须携带远端 A2A `taskId`，供调用方后续查询、取消、订阅或本地绑定远端调用句柄。 |
| `UNKNOWN` 响应状态 | MUST | 调用方服务端在接受等待窗口内无法确认被调用方是否创建 Task 时，必须得到 `UNKNOWN` 或等价未知状态，而不得误报成功或失败。 |
| 远端创建幂等 | MUST | 被调用方服务端必须以 `tenantId + idempotencyKey` 为创建类调用幂等键；重复投递或调用方重试不得创建多个远端 Task，已创建时应返回同一个 `taskId` 或等价接受事实。 |
| bus 投递幂等 | MUST | event-bus 必须用事件 ID、消息 ID、correlation 或幂等键约束事件投递副作用，避免重复投递导致重复可见响应。 |
| 调用方重试幂等 | MUST | 调用方服务端重试同一远端 A2A 调用时，必须复用或传递同一 `idempotencyKey`，使被调用方能够幂等返回同一远端 Task。 |
| 标准 Task 查询与订阅 | MUST | `GetTask`、`CancelTask` 与 `SubscribeToTask` 仍必须基于被调用方返回的远端 `taskId`；当前版本不定义基于调用方本地 Task ID、tool call ID 或 remote invocation ID 的跨 bus 查询。 |
| A2A 查询、取消和订阅控制事件 | MUST | event-bus 必须支持面向远端 Task 的查询、取消和订阅控制事件转发；这些事件不得隐式创建新 Task。 |
| Agent Card 发现排除 | MUST | 本特性不定义 Agent Card 发现、能力发现、版本选择或服务健康发现；这些能力由 registry-discovery-center 相关特性承载。 |
| route handle 消费 | MUST | A2A 调用事件可以消费 registry-discovery-center 产生的 route handle 或等价路由引用；事件转发不得向调用方暴露物理 endpoint，也不得绕过 route handle 直接绑定物理地址为外部契约。 |
| 大载荷引用 | MUST | event-bus 不承载大对象正文、多模态正文、artifact 大正文或 token 流；需要携带大载荷时必须使用 `payloadRef` 或等价数据引用。 |
| 小型 payload 内联 | MAY | 小型 A2A JSON-RPC request/response envelope 可以作为 inline payload 出现在事件中，但不得承担外层 bus 治理字段职责。 |
| 租户隔离 | MUST | A2A 调用事件和响应事件必须携带 `tenantId`，并在路由、投递、消费、幂等和审计中作为强制隔离维度。 |
| 物理投递机制透明 | MUST | 调用方和被调用方智能体服务端不得依赖具体 broker、数据库 outbox、worker、topic、queue、schema 或物理 endpoint 细节；这些属于 L2 或运行态实现。 |
| Task 生命周期所有权不变 | MUST | A2A 调用事件转发不得改变调用方本地 Task owner 或被调用方远端 Task owner；`agent-bus` 不创建、不写入、不推进任何服务端 Task execution state。 |

## 3. 外部接口与入口要求

| 入口 / 信封 | 类型 | 事实要求 |
|---|---|---|
| `AgentBusEventEnvelope` | event envelope | 必须承载事件类型、事件 ID、租户、correlation、trace、幂等、deadline、源服务、目标路由引用和 payload 描述。它是调用方服务端、event-bus 与被调用方服务端之间的跨单元契约；字段命名可由 L2 固化，但这些治理语义必须存在。 |
| `A2A_CALL_REQUESTED` | source runtime to target runtime event | 表达调用方服务端发起一次远端 A2A 调用或推进远端 Task。payload 可以是 A2A JSON-RPC request envelope 或 payload 引用。 |
| `A2A_CALL_CANCEL_REQUESTED` | source runtime to target runtime event | 表达调用方服务端请求取消已有远端 Task；payload 必须可映射到 A2A `CancelTask` 或等价 Task 引用。 |
| `A2A_CALL_QUERY_REQUESTED` | source runtime to target runtime event | 表达调用方服务端查询远端 Task；payload 必须可映射到 A2A `GetTask` 或等价查询语义。 |
| `A2A_STREAM_SUBSCRIBE_REQUESTED` | source runtime to target runtime event | 表达调用方服务端希望订阅已有远端 Task 的 A2A SSE 流；必须基于被调用方返回的远端 `taskId`，不得以调用方本地 Task ID、tool call ID 或 remote invocation ID 替代。 |
| `A2A_CALL_ACCEPTED` | target runtime to source runtime event | 表达被调用方已接受调用并创建或复用远端 Task；必须携带远端 `taskId`、correlation、idempotency 结果和必要的 Task 可见元数据。 |
| `A2A_CALL_REJECTED` | target runtime to source runtime event | 表达被调用方明确拒绝调用且未创建远端 Task；必须携带可编程拒绝原因。 |
| `A2A_CALL_FAILED` | target runtime to source runtime event | 表达被调用方消费调用事件或处理请求时发生确定失败；必须携带错误码和是否可重试的语义。 |
| `A2A_CALL_RESPONSE` | target runtime to source runtime event | 表达调用方等待窗口内得到一次性 A2A 响应；payload 应为 A2A JSON-RPC response envelope 或等价引用。 |
| `A2A_STREAM_READY` | target runtime to source runtime event | 表达某个远端 Task 已有可订阅 A2A SSE 流；必须携带远端 `taskId` 和调用方内部可解析的 stream 引用或订阅条件。 |
| `A2A_CALL_TERMINAL` | target runtime to source runtime event | 表达远端 Task 已完成、失败、取消或进入终态；用于调用方收尾、审计和恢复提示，不承载完整 token 流。 |
| A2A JSON-RPC payload | payload | 作为事件 payload 时保持 A2A 请求/响应语义；bus 外层治理字段不得强塞为 A2A 协议字段的唯一事实来源。 |
| A2A SSE stream | service stream | 实时流内容仍由被调用方服务端 A2A SSE 提供。调用方服务端在需要时点对点订阅并消费或桥接，不通过 event-bus 搬运 token chunk。 |
| route handle / discovery result | routing reference | 可由 registry-discovery-center 产生或解析，用于调用方服务端或 event-bus 定位目标服务；不得向调用方暴露物理 endpoint，也不得携带 Task execution state。 |
| `payloadRef` | data reference | 表达大载荷、多模态正文、artifact 大正文或其他数据正文的引用；`agent-bus` 可以治理引用信封，但不拥有引用所指数据正文。 |

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 服务间阻塞 A2A 调用并返回最终响应 | 调用方服务端已获得目标 route handle；目标服务可用 | 调用方发布 `A2A_CALL_REQUESTED`，被调用方消费并完成远端 Task | 调用方在等待窗口内消费 `A2A_CALL_RESPONSE`，把 A2A 兼容响应交给本地执行逻辑处理。 |
| 服务间阻塞调用退化为远端 Task 引用 | 被调用方已创建远端 Task，但调用方等待窗口内未完成 | 被调用方发布 `A2A_CALL_ACCEPTED`，但暂未发布最终响应 | 调用方获得远端 `taskId`，可后续发起 `A2A_CALL_QUERY_REQUESTED` 或 `A2A_STREAM_SUBSCRIBE_REQUESTED`。 |
| 接受阶段状态未知 | 调用方发布调用事件后，在接受等待窗口内未观察到被调用方接受、拒绝或失败事件 | 调用方本地执行仍等待远端调用结果 | 调用方得到 `UNKNOWN` 或等价状态，并可使用同一 `idempotencyKey` 重试恢复；不得创建第二个逻辑远端调用。 |
| UNKNOWN 后幂等重试 | 调用方无法确认远端是否已创建 Task | 调用方使用同一 `idempotencyKey` 重新发起原 A2A 调用 | 如果远端已经创建 Task，必须幂等返回同一远端 `taskId`；如果未创建，则按新投递创建或明确拒绝。 |
| 服务间流式 A2A 调用建立 | 调用方发起流式远端调用，目标服务支持 A2A SSE | 调用方发布 `A2A_CALL_REQUESTED`；被调用方发布 `A2A_CALL_ACCEPTED` 和 `A2A_STREAM_READY` | 调用方根据远端 `taskId` 和 stream 引用与被调用方建立点对点 A2A SSE 通道；event-bus 不转发 token chunk。 |
| 流式调用接受超时后重连 | 首次流式调用返回 `UNKNOWN` 或连接中断，调用方之后持有远端 `taskId` 或通过幂等重试获得远端 `taskId` | 调用方发布 `A2A_STREAM_SUBSCRIBE_REQUESTED` | 被调用方返回 `A2A_STREAM_READY` 后，调用方点对点订阅远端 A2A SSE。 |
| 取消远端任务 | 调用方已获得远端 `taskId` | 调用方发布 `A2A_CALL_CANCEL_REQUESTED` | 被调用方按 A2A `CancelTask` 语义处理并发布响应事件；远端 Task 状态仍由被调用方拥有。 |
| 查询远端任务状态 | 调用方已获得远端 `taskId` | 调用方发布 `A2A_CALL_QUERY_REQUESTED` | 被调用方返回 A2A 兼容 Task 快照响应。 |
| 被调用方拒绝调用 | 被调用方因鉴权、租户、能力、输入、策略或版本原因拒绝调用 | 被调用方发布 `A2A_CALL_REJECTED` | 调用方收到可编程拒绝响应；不得把拒绝伪造成远端 Task 已创建。 |
| 被调用方终态通知 | 远端 Task 完成、失败或取消 | 被调用方发布 `A2A_CALL_TERMINAL` | 调用方可用于收尾、审计和恢复提示；实时流内容仍以 A2A SSE 或 Task 查询为准。 |
| 编排智能体复用 | 未来存在部署在平台侧的特殊编排智能体服务 | 编排智能体作为调用方发布 A2A 调用事件 | 编排智能体通过自身 runtime 拥有编排 Task；`agent-bus` 仍只转发事件，不拥有编排状态。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 生产方与消费方语义

- A2A 调用方向上，事件消息生产方是调用方智能体服务端或调用方 `agent-runtime`，消费方是被调用方智能体服务端或远端 `agent-runtime`。
- A2A 响应方向上，事件消息生产方是被调用方智能体服务端，消费方是调用方智能体服务端。
- event-bus 单元负责在生产方与消费方之间转发事件和维护投递治理语义；它不成为调用方本地 Task owner，也不成为被调用方远端 Task owner。
- registry-discovery-center 单元可以为路由和目标选择提供支撑，但不是本特性调用/响应事件的生产方或消费方。
- 调用方智能体服务端不应把本地 tool call ID、remote invocation ID 或 parent Task ID 作为被调用方必须理解的跨 bus 标准字段。
- 被调用方智能体服务端只需要消费租户、correlation、idempotency、route 和 A2A payload 等通用调用上下文；它不需要感知调用方本地任务树。

#### 5.1.2 编排所有权语义

- 当前版本不引入 `agent-bus` 中心化编排 owner。
- 服务间 A2A 调用的决策、join、失败收敛和本地回灌由调用方 runtime 或未来编排智能体自身拥有。
- 未来中心化编排如果需要进入 `agent-bus` 模块附近部署，也应以特殊编排智能体服务的形式运行在 `agent-runtime + agent-core` 上。
- 编排智能体可以复用本文的 A2A 调用事件转发能力，但其 Task 生命周期、编排状态、成员关系和恢复策略不由 `agent-bus` event-bus 拥有。

#### 5.1.3 外层事件信封语义

- `agent-bus` 事件信封是跨边界治理事实来源，必须承载 tenant、trace、correlation、idempotency、deadline、route、source 和 payload 描述。
- A2A JSON-RPC envelope 是调用协议 payload，不替代 bus 事件信封。
- A2A request id 可以与 bus correlation 建立映射，但当前版本不要求二者相等。
- 事件信封不得携带服务端 Task execution state 的写入意图；调用方和被调用方 Task 状态只能由各自 runtime 推进。

#### 5.1.4 阻塞调用语义

- 阻塞 A2A 调用通过 event-bus 投递调用事件，并由调用方服务端等待被调用方响应事件。
- 调用方等待被调用方接受的窗口超时且无法判断是否创建远端 Task 时，必须得到 `UNKNOWN`。
- 调用方已经观察到 `A2A_CALL_ACCEPTED` 且获得远端 `taskId` 后，若最终响应等待超时，不得返回 `UNKNOWN`；应进入 `ACCEPTED_WITH_TASK` 或等价远端 Task 引用状态。
- 调用方在阻塞窗口内观察到最终 `A2A_CALL_RESPONSE` 时，可以把 A2A 兼容一次性响应交给本地执行逻辑处理。

#### 5.1.5 流式调用语义

- 流式 A2A 调用的请求事件与阻塞调用一样通过 event-bus 投递。
- 被调用方接受调用与远端流可订阅必须分别通过 `A2A_CALL_ACCEPTED` 与 `A2A_STREAM_READY` 表达。
- 调用方和被调用方之间的实时流必须继续使用 A2A SSE 或等价点对点服务流。
- event-bus 只转发流准备、订阅请求、终态和恢复所需的控制信号，不转发 token-by-token chunk、SSE frame 或流式正文。
- 调用方可以通过首次流式调用建立流，也可以在获得远端 `taskId` 后通过 `A2A_STREAM_SUBSCRIBE_REQUESTED` 重新建立流。

#### 5.1.6 幂等与重试语义

- event-bus 负责事件投递幂等，避免同一事件重复投递产生重复可见副作用。
- 被调用方服务端负责创建类 A2A 调用幂等。同一 `tenantId + idempotencyKey` 的重复调用不得创建多个远端 Task。
- 调用方服务端负责远端调用重试幂等。重试同一远端调用时，调用方必须复用或传递同一 `idempotencyKey`。
- `UNKNOWN` 后调用方可以使用同一 `idempotencyKey` 重试原调用；被调用方如果已经创建 Task，必须返回同一个远端 `taskId` 或等价接受事实。
- `messageId` / `eventId` 用于 bus 投递去重与审计；`idempotencyKey` 用于远端创建类调用幂等；二者不得混为同一个唯一语义。
- `SubscribeToTask` / `A2A_STREAM_SUBSCRIBE_REQUESTED` 不得暗中创建新远端 Task；它只订阅已有远端 `taskId` 的 A2A SSE。

#### 5.1.7 响应状态语义

| 状态 | 事实语义 |
|---|---|
| `COMPLETED_RESPONSE` | 调用方等待窗口内已获得最终 A2A 响应。 |
| `ACCEPTED_WITH_TASK` | 被调用方已创建或复用远端 Task，并返回远端 `taskId`，但最终响应尚未完成或不在当前窗口内返回。 |
| `STREAM_READY` | 远端 Task 的 A2A SSE 流已可订阅，调用方可以点对点订阅。 |
| `REJECTED` | 被调用方明确拒绝调用，未创建远端 Task。 |
| `FAILED` | 调用事件或被调用方处理发生确定失败。 |
| `UNKNOWN` | 调用方未能在接受等待窗口内确认被调用方是否已创建远端 Task；该状态不等同于成功或失败。 |

#### 5.1.8 错误与可观测语义

| 场景 | 事实要求 |
|---|---|
| 调用事件信封非法 | 调用方必须拒绝发布或被调用方必须拒绝消费，并返回可编程错误；不得创建远端 Task。 |
| tenant 不匹配 | 必须拒绝路由或消费；不得跨 tenant fallback。 |
| route handle 不可解析 | 必须表达 route / target 不可用错误；不得暴露物理 endpoint 作为替代恢复路径。 |
| 被调用方拒绝 | 必须以 `A2A_CALL_REJECTED` 或等价响应表达；不得伪造远端 `taskId`。 |
| 被调用方创建后阻塞等待超时 | 必须返回 `ACCEPTED_WITH_TASK` 或等价远端 Task 引用；不得误报 `UNKNOWN`。 |
| 接受阶段超时 | 必须返回 `UNKNOWN` 或等价未知状态，允许调用方用同一幂等键重试。 |
| 流准备超时 | 若已有远端 `taskId`，调用方应可稍后通过 `A2A_STREAM_SUBSCRIBE_REQUESTED` 重连；若没有远端 `taskId`，进入 `UNKNOWN` 处理。 |
| 实时流中断 | 调用方可通过远端 `taskId` 重新订阅；event-bus 不负责重放 token chunk。 |
| 被调用方终态失败 | 必须以 A2A Task 失败语义或 `A2A_CALL_TERMINAL` 终态事件表达，并保留 correlation / trace。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| token chunk 事件化 | 不承诺把 token-by-token chunk、SSE frame、artifact 大正文或多模态正文作为 event-bus 消息体转发。 |
| bus 拥有 Task 状态 | `agent-bus` 不创建、不写入、不推进调用方或被调用方 Task execution state。 |
| bus 拥有跨服务任务树 | 不定义由 `agent-bus` 维护 parent-child Task hierarchy、join 状态或跨服务编排状态。 |
| 调用方本地 ID 标准化 | 不把调用方本地 parent Task ID、tool call ID 或 remote invocation ID 作为跨 bus 标准字段。 |
| Agent Card 发现 | 不定义 Agent Card 获取、能力发现、版本选择、健康过滤或 registry runtime；这些属于 registry-discovery-center 相关特性。 |
| 隐式重建 Task | `A2A_STREAM_SUBSCRIBE_REQUESTED`、查询和取消不得在找不到远端 Task 时自动重新创建 Task。 |
| 私有流协议 | 当前版本不要求为调用方和被调用方之间定义 A2A SSE 之外的私有流协议。 |
| 物理 endpoint 暴露 | 调用方不直接依赖被调用方物理 endpoint；route handle / stream 引用由运行态内部解析。 |
| 具体 broker 产品 | 本特性不规定具体 broker、topic、queue、outbox、worker、数据库或 RocketMQ 接线方式。 |
| 默认实现强绑定 | 不要求企业客户必须使用产品自带 event-bus 或 registry-discovery-center；可用存量系统适配，只要满足本文契约。 |
| 大载荷数据通道 | event-bus 不作为大对象正文或敏感正文的数据通道；大载荷必须走引用路径。 |
| 本地执行回灌机制 | 不定义调用方 runtime 如何把远端结果回灌到本地 handler、agent-core、memory、trajectory 或执行上下文。 |
| 非 A2A 查询扩展 | 当前版本不要求新增 `ResolveRemoteInvocation` 之类的非 A2A 标准查询方法；UNKNOWN 后通过同一 `idempotencyKey` 重试来恢复。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把本特性作为 `agent-bus` A2A 调用事件转发的事实来源，不得把内部投递机制写成调用方或被调用方必须感知的协议。
- L2 设计必须保持调用方 `agent-runtime`、event-bus、被调用方 `agent-runtime` 和 registry-discovery-center 的边界；默认实现、客户存量系统适配和混合部署都必须对齐同一事件信封与路由契约。
- 实现可以复用现有 forwarding substrate、outbox / inbox、route handle、payloadRef、retry、DLQ、RLS、broker SPI 等能力，但这些实现细节不得反向定义本特性的外部行为。
- 调用方服务端暴露给本地 Agent 执行逻辑的远端调用结果必须保持 A2A 兼容，不得要求被调用方理解调用方内部执行框架或本地工具调用状态。
- 被调用方消费 A2A 调用事件后，必须以自身 Task 生命周期语义处理请求，并通过响应事件报告接受、拒绝、失败、响应、流准备和终态事实。
- 实现必须把外层 bus 治理字段与内层 A2A payload 分离；不得把 route、tenant、trace、deadline 和幂等语义只塞进 A2A body 后让 bus 失去治理字段。
- 流式响应实现必须保持“event-bus 转发流准备与终态，A2A SSE 点对点承载实时流内容”的边界。
- Agent Card 发现、服务能力发现、健康选择和版本匹配必须留给 registry-discovery-center 相关特性，不得塞入 FEAT-014 的调用事件主流程。
- 测试必须覆盖阻塞成功、阻塞退化为远端 Task、接受阶段 UNKNOWN、UNKNOWN 后同键重试、流准备、流重连、取消、查询、被调用方拒绝、重复投递、租户隔离、route handle 不可解析和 token 流不进 event-bus。
- 文档和指南必须明确：`agent-bus` 是 A2A 调用事件转发与治理边界，不是跨服务编排 owner、服务端 Task owner，也不是 token stream broker。

## 7. 关联文档

- `architecture/L0-Top-Level-Design/boundaries.md`
- `architecture/L0-Top-Level-Design/glossary.md`
- `architecture/L1-High-Level-Design/agent-bus/README.md`
- `architecture/L1-High-Level-Design/agent-bus/logical.md`
- `architecture/L1-High-Level-Design/agent-bus/process.md`
- `architecture/L1-High-Level-Design/agent-bus/scenarios.md`
- `architecture/L1-High-Level-Design/agent-bus/features/README.md`
- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `version-scope/FEAT-005-remote-agent-orchestration.md`
- `version-scope/FEAT-013-client-invocation-event-forwarding.md`
