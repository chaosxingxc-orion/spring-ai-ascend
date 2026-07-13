---
version: 0715
module: agent-runtime
feature_type: functional
feature_id: FEAT-017
status: draft
related_docs:
  - ../architecture/L0-Top-Level-Design/boundaries.md
  - ../architecture/L0-Top-Level-Design/glossary.md
  - ../architecture/L1-High-Level-Design/agent-runtime/README.md
  - ../architecture/L1-High-Level-Design/agent-runtime/logical.md
  - ../architecture/L1-High-Level-Design/agent-runtime/process.md
  - ../architecture/L1-High-Level-Design/agent-runtime/scenarios.md
  - ../architecture/L1-High-Level-Design/agent-bus/README.md
  - ../architecture/L1-High-Level-Design/agent-bus/logical.md
  - ./FEAT-001-standardized-agent-service-entrypoint.md
  - ./FEAT-004-remote-agent-orchestration.md
  - ./FEAT-012-client-invocation-bus-forwarding.md
  - ./FEAT-013-client-invocation-event-forwarding.md
  - ./FEAT-014-a2a-call-event-forwarding.md
---

# 订阅消费总线事件消息特性文档

## 1. 特性定位

FEAT-017 定义 `agent-runtime` 当前版本作为目标智能体服务端时，内嵌订阅并消费事件总线上的客户端调用事件消息与智能体服务间 A2A 调用事件消息的事实要求。该特性使 runtime 能够从 `agent-bus` event-bus 接收调用、查询、取消和流订阅控制事件，将其映射到自身标准 A2A Task 控制面，并发布接受、拒绝、失败、响应、流准备、等待输入和终态等状态投影事件。

本特性解决的问题是：当调用不再以 HTTP 直连方式进入某个 runtime，而是由 `agent-bus` 以事件消息投递时，runtime 仍必须保持与标准 Agent 服务入口一致的 Task 生命周期、A2A payload、错误表面、幂等和流式边界。事件订阅消费不能引入第二套 bus 专用执行入口，也不能要求 runtime 感知 broker topic、offset、consumer group、物理 endpoint 或 outbox/inbox 存储细节。

当前版本选择 `agent-runtime` 边界内的嵌入式订阅消费能力，不新增 sidecar、独立 worker 或外部 consumer 实体作为特性事实主体。实现可以通过 host application、auto-configuration 或内部端口接入事件总线，但对外事实是 runtime 自己消费事件、进入自身 Task 控制面并发布响应事件。物理 broker、relay、receiver、ack 通道和 retry 机制仍属于 `agent-bus` 或 L2 运行态实现，不反向定义 runtime 领域模型。

本特性要求 runtime 消费事件后复用 `FEAT-001` 的标准 A2A 服务入口语义，但不要求通过本机 HTTP `/a2a` 回环调用实现。实现可以使用内部 bridge 直达与 `SendMessage`、`SendStreamingMessage`、`GetTask`、`CancelTask` 和 `SubscribeToTask` 等价的 RequestHandler / Task 控制面语义；无论物理实现如何，外部可观察行为必须与标准入口保持一致。

本特性面向以下角色：

- `agent-runtime` 实现方：实现嵌入式事件订阅消费、事件到 Task 控制面的映射、响应事件发布和幂等恢复。
- `agent-bus` event-bus 单元：向 runtime 投递调用事件，并接收 runtime 发布的响应事件。
- `agent-bus` gateway 单元：通过客户端调用事件触发 runtime，并消费 `INVOCATION_*` 响应投影。
- 调用方智能体服务端：通过服务间 A2A 事件触发目标 runtime，并消费 `A2A_*` 响应投影。
- 智能体服务开发者：仍通过标准 Agent handler / A2A Task 语义接收调用，不需要理解事件总线物理细节。
- 测试与验收团队：按本文定义的黑盒行为验证事件消费、Task 创建、幂等、ack、输入等待、流准备、终态投影和边界不变量。

本特性主线只覆盖 runtime 作为目标/被调用方时订阅消费请求事件，并发布对应响应事件。调用方 runtime 如何消费远端 `A2A_CALL_*` 响应事件并回灌本地父 Task、tool call 或远程调用上下文，由 `FEAT-005` 和服务间 A2A 调用转发相关设计承接；本文只在边界中说明协作关系，不重新定义调用方本地编排所有权。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 嵌入式事件订阅消费 | MUST | `agent-runtime` 必须能够作为边界内能力订阅并消费事件总线投递给本 runtime 的调用、查询、取消和流订阅事件；当前版本不新增独立 sidecar / consumer 实体作为事实主体。 |
| 客户端调用事件消费 | MUST | runtime 必须能够消费 `CLIENT_INVOCATION_REQUESTED`、`CLIENT_INVOCATION_QUERY_REQUESTED`、`CLIENT_INVOCATION_CANCEL_REQUESTED` 和 `CLIENT_STREAM_SUBSCRIBE_REQUESTED`，并按标准 A2A Task 语义处理。 |
| 服务间 A2A 请求事件消费 | MUST | runtime 必须能够消费 `A2A_CALL_REQUESTED`、`A2A_CALL_QUERY_REQUESTED`、`A2A_CALL_CANCEL_REQUESTED` 和 `A2A_STREAM_SUBSCRIBE_REQUESTED`，并作为被调用方按自身 Task 生命周期处理。 |
| 标准入口语义复用 | MUST | 事件消费后必须进入与 `FEAT-001` 标准 A2A 服务入口等价的 Task 控制面，不得新增 bus 专用执行协议；实现不强制 HTTP `/a2a` 回环。 |
| 外层事件信封消费 | MUST | runtime 必须消费 bus 外层事件信封中的租户、trace、correlation、幂等、deadline、route、source 和 payload 描述；A2A JSON-RPC 只能作为 payload 或 payload 引用被解释。 |
| A2A payload 兼容 | MUST | runtime 对事件 payload 的解释必须保持 A2A JSON-RPC / Task / SSE 语义兼容；业务 handler 不应感知调用来自 HTTP 直连还是 bus 事件。 |
| 接受投影发布 | MUST | runtime 创建或复用 Task 后必须发布 `INVOCATION_ACCEPTED` 或 `A2A_CALL_ACCEPTED`，并携带 `taskId`、correlation、idempotency 结果和必要 Task 可见元数据。 |
| 拒绝与失败投影发布 | MUST | runtime 在未创建 Task 时明确拒绝调用必须发布 `*_REJECTED`；消费事件或处理请求发生确定失败时必须发布 `*_FAILED`，并携带可编程错误语义。 |
| 阻塞响应投影发布 | MUST | 对等待窗口内可完成的一次性调用，runtime 必须发布 `INVOCATION_RESPONSE` 或 `A2A_CALL_RESPONSE`，payload 为 A2A JSON-RPC response envelope 或等价引用。 |
| 等待输入投影发布 | MUST | runtime Task 进入 `INPUT_REQUIRED` 时必须发布 `INVOCATION_INPUT_REQUIRED` 或 `A2A_CALL_INPUT_REQUIRED`，使 gateway 或调用方 runtime 能及时感知等待输入状态。 |
| 流准备投影发布 | MUST | runtime Task 的 A2A SSE 可被订阅时必须发布 `INVOCATION_STREAM_READY` 或 `A2A_STREAM_READY`，并携带 `taskId` 和 `streamRef`。 |
| `streamRef` 稳定字段 | MUST | `streamRef` 是流准备事件的稳定语义字段，用于调用方内部解析并桥接/订阅服务端 A2A SSE；它不得暴露物理 endpoint，不携带 token、SSE frame 或大正文，也不得替代 `taskId`。 |
| 终态投影发布 | MUST | runtime Task 完成、失败、取消或拒绝后必须发布 `INVOCATION_TERMINAL` 或 `A2A_CALL_TERMINAL`，用于调用方收尾、审计和恢复。 |
| ack 到接收边界 | MUST | 总线消费确认语义必须收敛到 runtime 已可靠接收事件并落入 Task 控制面；不得等待 Agent 执行终态后才确认消费。 |
| bus 投递去重配合 | MUST | runtime 必须与 event-bus/inbox 的 `tenantId + messageId + consumerServiceId` 去重语义兼容；`idempotencyKey` 不得被误用为 inbox 去重键。 |
| Task 创建幂等 | MUST | 创建类调用必须以 `tenantId + idempotencyKey` 约束 runtime Task 创建；重复投递或重试不得创建多个 Task。 |
| 响应事件发布幂等 | MUST | runtime 重复消费同一事件时不得重复产生可见副作用；已创建 Task 时应复用同一 `taskId` 并补发等价接受或状态投影。 |
| 租户隔离 | MUST | runtime 消费事件、创建 Task、查询 Task、取消 Task、订阅流和发布响应事件时必须强制校验 `tenantId`；禁止跨租户 fallback。 |
| token 流不进总线 | MUST | runtime 不得通过事件总线发布 token-by-token chunk、SSE frame 或大正文；实时流内容继续由 A2A SSE 承载。 |
| 物理 broker 透明 | MUST | runtime 特性文档、接口和业务 handler 不得依赖 RocketMQ、Kafka、topic、offset、consumer group、broker retry 或 outbox/inbox 表结构等物理细节。 |
| 调用方响应回灌排除 | OUT | 当前 FEAT-017 不完整定义调用方 runtime 消费远端响应事件并回灌本地父 Task 的编排语义；该范围由 `FEAT-005` 及服务间调用相关设计承接。 |

## 3. 外部接口与入口要求

| 入口 / 信封 | 类型 | 事实要求 |
|---|---|---|
| Runtime bus-event subscription capability | runtime inbound capability | runtime 必须具备订阅本实例目标事件的能力。具体配置项、consumer 注册方式、topic/route 映射和 broker client 由 L2 固化；本文只约束外部行为。 |
| `AgentBusEventEnvelope` | event envelope | runtime 必须读取事件类型、事件 ID / message ID、租户、source、target、correlation、trace、idempotency、deadline、payload / payloadRef 和 route 语义；字段命名可由 L2 固化。 |
| `CLIENT_INVOCATION_REQUESTED` | gateway-to-runtime event | 表达客户端经 gateway 发起创建或推进类调用；runtime 必须映射到标准 A2A message send 语义。 |
| `CLIENT_INVOCATION_QUERY_REQUESTED` | gateway-to-runtime event | 表达客户端查询 Task 或任务列表；runtime 必须映射到标准 Task 查询语义，通常基于 `taskId`。 |
| `CLIENT_INVOCATION_CANCEL_REQUESTED` | gateway-to-runtime event | 表达客户端取消已有 Task；runtime 必须映射到标准取消语义，不得只在 bus 侧标记取消。 |
| `CLIENT_STREAM_SUBSCRIBE_REQUESTED` | gateway-to-runtime event | 表达客户端订阅已有 Task 的 A2A SSE；runtime 必须基于 `taskId` 准备流订阅，不得隐式创建新 Task。 |
| `A2A_CALL_REQUESTED` | source-runtime-to-target-runtime event | 表达调用方 runtime 发起远端 A2A 创建或推进类调用；本 runtime 作为被调用方消费并处理。 |
| `A2A_CALL_QUERY_REQUESTED` | source-runtime-to-target-runtime event | 表达调用方 runtime 查询远端 Task；本 runtime 必须返回 A2A 兼容 Task 快照或错误投影。 |
| `A2A_CALL_CANCEL_REQUESTED` | source-runtime-to-target-runtime event | 表达调用方 runtime 取消远端 Task；本 runtime 必须按标准 Task owner 语义处理取消。 |
| `A2A_STREAM_SUBSCRIBE_REQUESTED` | source-runtime-to-target-runtime event | 表达调用方 runtime 订阅已有远端 Task 的 A2A SSE；本 runtime 必须基于远端 `taskId` 发布流准备或失败投影。 |
| `INVOCATION_ACCEPTED` / `A2A_CALL_ACCEPTED` | runtime response event | 表达 runtime 已接受事件并创建或复用 Task；必须携带 `taskId`、correlation、idempotency 结果和必要元数据。 |
| `INVOCATION_REJECTED` / `A2A_CALL_REJECTED` | runtime response event | 表达 runtime 明确拒绝请求且未创建 Task；必须携带可编程拒绝原因。 |
| `INVOCATION_FAILED` / `A2A_CALL_FAILED` | runtime response event | 表达 runtime 消费事件或处理请求时发生确定失败；必须携带错误码、可重试语义和 correlation。 |
| `INVOCATION_RESPONSE` / `A2A_CALL_RESPONSE` | runtime response event | 表达一次性 A2A 响应；payload 应为 A2A JSON-RPC response envelope 或等价引用。 |
| `INVOCATION_INPUT_REQUIRED` / `A2A_CALL_INPUT_REQUIRED` | runtime response event | 表达 Task 进入等待输入状态；必须携带 `taskId`、输入需求描述、correlation 和可恢复上下文引用。 |
| `INVOCATION_STREAM_READY` / `A2A_STREAM_READY` | runtime response event | 表达 Task 的 A2A SSE 可被订阅；必须携带 `taskId`、`streamRef`、correlation 和必要订阅条件。 |
| `INVOCATION_TERMINAL` / `A2A_CALL_TERMINAL` | runtime response event | 表达 Task 已进入 completed、failed、canceled 或 rejected 等结果性终态；用于收尾、审计和恢复，不承载 token 流。 |
| `streamRef` | stream reference | runtime 生成或返回的流订阅引用。它是内部可解析引用，不是公开物理 endpoint；调用方必须与 `taskId` 一起使用。 |
| `payloadRef` / `artifactRef` | data reference | 表达大载荷、多模态正文、artifact 大正文或其他数据正文引用；runtime 不应把这些正文塞入总线事件。 |

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 消费客户端创建类调用 | gateway 已发布 `CLIENT_INVOCATION_REQUESTED`，事件目标为本 runtime | runtime 嵌入式订阅器消费事件并进入 Task 控制面 | runtime 创建或复用 Task，发布 `INVOCATION_ACCEPTED`；若等待窗口内完成，继续发布 `INVOCATION_RESPONSE` 和 `INVOCATION_TERMINAL`。 |
| 客户端调用被拒绝 | 事件信封合法但 runtime 因鉴权、租户、handler 不可用、能力或输入策略拒绝请求 | runtime 消费事件后拒绝创建 Task | runtime 发布 `INVOCATION_REJECTED`，不得伪造 `taskId`。 |
| 客户端流式调用建立 | gateway 发布流式调用事件，本 runtime 支持 A2A SSE | runtime 创建 Task 并启动流式执行 | runtime 先发布 `INVOCATION_ACCEPTED`，流可订阅后发布携带 `streamRef` 的 `INVOCATION_STREAM_READY`；token 流仍由 A2A SSE 提供。 |
| 客户端查询 Task | gateway 发布 `CLIENT_INVOCATION_QUERY_REQUESTED`，payload 基于 `taskId` | runtime 查询自身 TaskStore | runtime 发布 `INVOCATION_RESPONSE` 或失败投影；`clientInvocationId` 不得替代 `taskId`。 |
| 客户端取消 Task | gateway 发布 `CLIENT_INVOCATION_CANCEL_REQUESTED`，目标 Task 属于本 runtime | runtime 执行标准取消语义 | runtime 发布取消响应和后续 `INVOCATION_TERMINAL`；Task 状态仍由 runtime 拥有。 |
| 客户端重新订阅流 | gateway 发布 `CLIENT_STREAM_SUBSCRIBE_REQUESTED`，payload 包含已有 `taskId` | runtime 准备已有 Task 的 SSE 订阅 | runtime 发布 `INVOCATION_STREAM_READY`；找不到 Task 时发布确定失败或拒绝，不得隐式创建新 Task。 |
| 消费服务间 A2A 创建类调用 | 调用方 runtime 通过 event-bus 发布 `A2A_CALL_REQUESTED` | 本 runtime 作为被调用方消费事件 | runtime 创建或复用远端 Task，发布 `A2A_CALL_ACCEPTED`；完成时发布 `A2A_CALL_RESPONSE` / `A2A_CALL_TERMINAL`。 |
| 服务间调用进入等待输入 | 本 runtime 执行远端 Task 时需要人工输入或外部继续 | Task 状态推进到 `INPUT_REQUIRED` | runtime 发布 `A2A_CALL_INPUT_REQUIRED`，调用方 runtime 后续回灌策略由 `FEAT-005` 承接。 |
| 重复投递恢复 | 同一 `tenantId + messageId + consumerServiceId` 或同一 `tenantId + idempotencyKey` 的创建类调用重复出现 | runtime 或 event-bus 观察到重复 | 不创建第二个 Task；runtime 复用同一 `taskId` 并补发等价状态投影或返回幂等结果。 |
| 消费确认后长任务继续执行 | runtime 已创建 Task 并发布 accepted，但 Agent 执行尚未完成 | runtime 对总线完成消费确认 | broker 消费不因长任务阻塞；后续状态通过响应事件、TaskStore、`GetTask` 和终态投影恢复。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 订阅消费主体语义

- 当前版本的订阅消费主体是 `agent-runtime` 边界内的嵌入式能力。
- host application 可以负责装配 broker client、订阅配置或运行时开关，但这些不形成新的领域实体。
- runtime 不把 topic、offset、partition、consumer group 或 outbox/inbox 表结构暴露给 handler、Agent 或外部调用方。
- runtime 消费事件时必须先按 envelope 校验租户、目标、schema、deadline、payload 描述和 correlation，再进入 Task 控制面。

#### 5.1.2 标准入口复用语义

- 总线事件消费与 HTTP `/a2a` 调用必须共享同一套 A2A Task 生命周期语义。
- runtime 可以通过内部 bridge 调用 RequestHandler / Task 控制面，不要求本机 HTTP 回环。
- bus 事件不得绕过 TaskStore、MainEventBus、Task 状态机或 handler 执行边界直接驱动业务 Agent。
- 业务 handler 不应区分调用来自 HTTP client、gateway 事件还是服务间 A2A 事件；必要来源信息只作为 metadata / trace / audit 上下文存在。

#### 5.1.3 生产方与消费方语义

- 客户端调用事件方向上，生产方是 `agent-bus` gateway / event-bus，消费方是目标 `agent-runtime`。
- 客户端响应事件方向上，生产方是目标 `agent-runtime`，消费方是 gateway。
- 服务间 A2A 请求事件方向上，生产方是调用方 runtime，消费方是被调用方 runtime。
- 服务间 A2A 响应事件方向上，本文只要求被调用方 runtime 发布响应事件；调用方 runtime 的本地回灌和编排收敛由 `FEAT-005` 等相关特性承接。

#### 5.1.4 ack、幂等与重试语义

- 消费 ack 的事实边界是 runtime 已可靠接收事件并落入 Task 控制面，不是 Agent 执行完成。
- 总线投递去重以 `tenantId + messageId + consumerServiceId` 为主，`idempotencyKey` 只作为业务创建幂等键和审计字段。
- 创建类调用以 `tenantId + idempotencyKey` 为 runtime Task 创建幂等键；重复投递不得创建多个 Task。
- runtime 重复消费已创建 Task 的事件时，应返回同一 `taskId` 或补发等价 `*_ACCEPTED` / 当前状态投影。
- 如果 Task 已创建但某次响应事件发布失败，runtime 必须允许通过后续状态投影、终态投影或 `GetTask` 查询恢复，不得依赖原始消费消息长期不 ack。

#### 5.1.5 响应事件发布语义

- `*_ACCEPTED` 表达 runtime 已创建或复用 Task，是进入 Task 生命周期的明确事实。
- `*_REJECTED` 表达 runtime 明确拒绝且未创建 Task。
- `*_FAILED` 表达消费事件、payload 解析、内部处理或确定失败；应携带可编程错误码和可重试语义。
- `*_RESPONSE` 表达一次性 A2A 响应，通常用于阻塞等待窗口内完成或查询类请求。
- `*_INPUT_REQUIRED` 表达 Task 等待输入，不应只隐藏在后续 Task 查询结果中。
- `*_TERMINAL` 表达结果性终态，可用于调用方收尾、审计和恢复。

#### 5.1.6 流式订阅语义

- `*_ACCEPTED` 与 `*_STREAM_READY` 必须分离。前者表达 Task 已创建或复用，后者表达 A2A SSE 已可订阅。
- `*_STREAM_READY` 不表示业务完成，也不表示 token 已经开始产出。
- `streamRef` 必须与 `taskId` 一起使用；`streamRef` 不替代 `taskId`。
- runtime 不通过事件总线发布 token chunk、SSE frame、progress stream 或大正文。
- gateway 或调用方 runtime 基于 `taskId + streamRef` 建立到 runtime 的 A2A SSE 订阅或桥接。
- 如果上游连接已经断开，runtime 不负责为上游长期缓存 token 流；Task 状态、artifact 和终态可通过 `GetTask` 或状态投影恢复。

#### 5.1.7 查询、取消和订阅控制语义

- 查询、取消和订阅事件必须基于 runtime 拥有的 `taskId` 或 A2A 标准 Task 引用。
- `clientInvocationId`、调用方本地 Task ID、tool call ID 或 remote invocation ID 不得替代被调用方 runtime 的 `taskId`。
- 查询事件不得创建新 Task。
- 取消事件必须进入 Task owner 的取消语义，不得只在 bus 侧标记取消。
- 订阅事件只准备已有 Task 的 SSE 订阅，不得在找不到 Task 时隐式重建 Task。

#### 5.1.8 错误与可观测语义

| 场景 | 事实要求 |
|---|---|
| 事件信封非法 | runtime 必须拒绝消费或发布失败投影；不得创建 Task。 |
| tenant 不匹配 | runtime 必须拒绝消费；不得跨 tenant 查询、复用或 fallback。 |
| payload / payloadRef 不可解析 | runtime 必须发布 `*_FAILED` 或 `*_REJECTED`，并携带可编程错误语义。 |
| handler 不存在或未 ready | runtime 必须拒绝创建或形成 failed Task；不得静默 ack 后无响应。 |
| 创建后响应发布失败 | 不回滚 Task；必须保留可恢复状态，并允许补发状态投影或通过 Task 查询恢复。 |
| 重复投递 | 不重复创建 Task，不重复产生可见副作用；发布同一 Task 的等价投影。 |
| Task 进入 INPUT_REQUIRED | 必须发布 `*_INPUT_REQUIRED` 并保留 correlation / trace。 |
| 实时流中断 | 上游可基于 `taskId` 重新订阅；runtime 不通过 bus 重放 token chunk。 |
| Task 终态失败 | 必须发布 `*_TERMINAL` 或 A2A failed Task 语义，并携带 correlation / trace。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| 独立 consumer 实体 | 当前版本不新增 sidecar、外部 worker 或独立 consumer 服务作为 FEAT-017 主体。 |
| broker 产品绑定 | 不承诺 runtime 直接理解 RocketMQ、Kafka、topic、offset、consumer group、partition 或 broker retry 语义。 |
| bus 专用执行入口 | 不为 bus 事件新增绕过标准 A2A Task 控制面的私有执行协议。 |
| HTTP 回环强制 | 不要求嵌入式消费器必须通过本机 HTTP `/a2a` 调用 runtime。 |
| 调用方响应回灌 | 不完整定义调用方 runtime 如何消费 `A2A_CALL_*` 响应事件并回灌本地父 Task、tool call 或 remote invocation。 |
| token chunk 事件化 | 不通过事件总线承载 token-by-token chunk、SSE frame、progress stream 或大正文。 |
| `streamRef` 暴露物理地址 | `streamRef` 不作为公开 endpoint、broker topic 或实例地址。 |
| Task 状态外部写入 | event-bus、gateway 或调用方 runtime 不通过事件直接写入本 runtime Task execution state。 |
| `clientInvocationId` 替代 `taskId` | `clientInvocationId` 只可作为 gateway 侧关联，不作为 runtime Task 查询、取消或订阅标准输入。 |
| 隐式重建 Task | 查询、取消和订阅事件不得在找不到 Task 时自动创建新 Task。 |
| 大载荷数据通道 | runtime 不把总线事件作为大对象正文、多模态正文或敏感正文的数据通道。 |
| registry 语义发现 | FEAT-017 不定义 Agent Card 发现、能力搜索、路由查询或 registry-discovery-center 行为。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把本特性作为 `agent-runtime` 订阅消费总线事件消息的事实来源，不得把 broker 物理接线写成 runtime 外部协议。
- 实现必须保持嵌入式订阅消费与标准 A2A 服务入口语义一致；内部 bridge 可以替代 HTTP 回环，但行为不得漂移。
- runtime 的事件消费路径必须进入 Task 控制面，不得绕过 TaskStore、Task 状态机和 handler 执行边界。
- runtime 必须发布与事件来源匹配的响应事件族：客户端链路使用 `INVOCATION_*`，服务间 A2A 链路使用 `A2A_CALL_*`。
- `INPUT_REQUIRED` 必须作为响应事件投影出现，不能只作为 Task 查询结果存在。
- `STREAM_READY` 必须携带稳定 `streamRef`，并保持与 `ACCEPTED`、终态和实时 token 流的语义分离。
- 消费 ack 必须在 Task 接收/创建边界收敛；长任务执行、流式输出和终态通过响应事件与 Task 查询恢复。
- 幂等实现必须区分 bus 投递去重键和 runtime Task 创建幂等键，不得把 `messageId`、`idempotencyKey`、`clientInvocationId` 和 `taskId` 混成一个语义。
- 测试必须覆盖客户端创建、查询、取消、流订阅，服务间 A2A 创建、查询、取消、流订阅，accepted 必发，input-required 投影，stream-ready + streamRef，terminal 投影，重复投递，Task 创建幂等，租户隔离，信封非法，payloadRef 错误，ack 不等待终态，以及 token 不进总线。
- 文档和指南必须明确：FEAT-017 是 runtime 消费事件并进入标准 Task 控制面的能力，不是 `agent-bus` 转发语义、broker 接线设计、远端响应回灌编排或 token stream broker。

## 7. 关联文档

- `architecture/L0-Top-Level-Design/boundaries.md`
- `architecture/L0-Top-Level-Design/glossary.md`
- `architecture/L1-High-Level-Design/agent-runtime/README.md`
- `architecture/L1-High-Level-Design/agent-runtime/logical.md`
- `architecture/L1-High-Level-Design/agent-runtime/process.md`
- `architecture/L1-High-Level-Design/agent-runtime/scenarios.md`
- `architecture/L1-High-Level-Design/agent-bus/README.md`
- `architecture/L1-High-Level-Design/agent-bus/logical.md`
- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `version-scope/FEAT-004-remote-agent-orchestration.md`
- `version-scope/FEAT-012-client-invocation-bus-forwarding.md`
- `version-scope/FEAT-013-client-invocation-event-forwarding.md`
- `version-scope/FEAT-014-a2a-call-event-forwarding.md`
