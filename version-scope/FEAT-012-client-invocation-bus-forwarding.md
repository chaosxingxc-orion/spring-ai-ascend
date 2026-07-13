---
version: 0715
module: agent-bus
feature_type: functional
feature_id: FEAT-2026-012
status: active
---
# agent-gateway 组件客户端调用总线转发特性文档

## 1. 特性定位

FEAT-2026-012 定义 `agent-gateway` 当前版本按 agentId 把客户端调用通过 pub-sub 模式转发到 `Event Bus` 的事实：Gateway 可以将 agent-client 的调用标准化为控制事件，使用 client 提交的 `clientInvocationId` 作为弱关联句柄，由后端 worker / consumer 投递到 `agent-runtime` 标准入口，再通过状态投影事件把接受、响应、流准备、等待输入和终态反馈给客户端。

本特性解决的问题是：客户端不应直接感知 runtime 物理 endpoint、worker、topic 或内部投递机制；Gateway 需要在 pub-sub 模式下提供事件化入队、同步阻塞等待窗口、`clientInvocationId` 关联、幂等恢复、payloadRef、大载荷引用、状态投影和流桥接能力。Event Bus 只承载控制事件、状态投影和引用，不执行 Agent、不承载 token chunk、不持有 Task 权威状态。

在总体架构中，本特性位于 agent-client、Gateway、Event Bus、forwarding worker / consumer 和 agent-runtime 之间。Gateway 是客户端入口和投影交付者，Event Bus 是发布订阅与投递治理设施，runtime 仍是 Task owner。

本特性面向以下角色：

- Gateway 开发者：实现 pub-sub 入队、同步等待窗口、clientInvocationId 关联、事件发布、状态投影交付和流桥接。
- Event Bus 集成方：提供发布订阅、缓存、投递重试和失败可观测。
- forwarding worker / consumer 开发者：消费事件并投递 runtime 标准入口。
- agent-client 接入方：生成 clientInvocationId，使用 taskId 进行任务操作，并在 UNKNOWN 后以同一 clientInvocationId 和同一幂等键恢复。
- 测试与验收团队：验证入队、接受、流准备、input_required、终态、取消、幂等和大载荷引用。

本特性只定义 Gateway 按 agentId 发起的客户端调用总线转发通道。直接路由转发由 FEAT-2026-011 承接；更完整的事件转发总契约由 FEAT-013 承接；runtime worker 如何消费事件由 FEAT-2026-017 承接；Agent 执行、任务接续控制和 TaskStore 不属于 Event Bus 或 Gateway 职责。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| pub-sub 调用入队 | MUST | Client 必须生成并携带 `clientInvocationId` 作为 Gateway 侧弱关联句柄；Gateway 发布 `CLIENT_INVOCATION_REQUESTED`，并向 client 返回可用于后续状态观察和幂等恢复的关联信息。 |
| 外层事件信封 | MUST | 调用事件必须携带 tenant、user、session、correlation、routeHandle、deadline、幂等键和 payload 描述；A2A JSON-RPC 只能作为 payload 或 payloadRef 出现。 |
| 大载荷引用 | MUST | 大输入、大结果、文件、多模态内容必须使用 payloadRef / artifactRef，不写入 Event Bus 事件正文。 |
| 服务端接受投影 | MUST | runtime 创建或复用 Task 后，消费者必须发布 `INVOCATION_ACCEPTED`，并携带 `taskId`、correlation 和幂等结果；窗口内完成时也必须先有接受投影，`clientInvocationId` 不得替代 `taskId`。 |
| 同步阻塞等待 | MUST | Gateway 必须支持在阻塞等待窗口内消费响应投影，并向 client 返回 `COMPLETED_RESPONSE`、`ACCEPTED_WITH_TASK`、`REJECTED`、`FAILED` 或 `UNKNOWN` 等结果。 |
| 流准备投影与桥接 | MUST | 流式任务必须通过 `INVOCATION_STREAM_READY` 表达可订阅事实；Gateway 收到流准备信号后，仅在 client 连接仍存在时与 runtime 建立 A2A SSE 长连接并桥接给 client，Event Bus 不承载 token 流。 |
| 等待输入投影 | MUST | runtime 的 INPUT_REQUIRED 必须可投影为 `INVOCATION_INPUT_REQUIRED`；Gateway 只把新的 input_required 状态、所需输入描述和关联信息交付给 client，不负责管理任务接续状态机。 |
| 终态投影 | MUST | completed、failed、canceled、rejected 等结果必须通过 `INVOCATION_TERMINAL` 或等价投影表达；Task 终态仍由 runtime 拥有。 |
| UNKNOWN 与幂等恢复 | MUST | Gateway 在接受等待窗口内无法确认 runtime 是否创建 Task 时，必须返回 `UNKNOWN` 或等价状态，并允许 client 使用同一 `clientInvocationId` 和同一 `idempotencyKey` 重试原始创建类调用；当前版本不新增私有 `ResolveInvocation` 查询接口。 |
| 取消请求事件 | MUST | Gateway 必须能发布 `CLIENT_INVOCATION_CANCEL_REQUESTED`；消费者最终将请求映射到 Task owner 的 `CancelTask`，不得只在总线侧标记取消。 |
| Event Bus 不执行 Agent | MUST | Event Bus 不得执行 Agent、调用模型、运行工具、保存业务 checkpoint 或决定 Task 终态。 |
| token 流入总线 | OUT | 当前版本不允许 token-by-token、SSE frame 或大对象正文进入 Event Bus。 |

## 3. 外部接口与事件要求

本节定义 Gateway 面向 client 的接口事实，以及 Gateway 通过 Event Bus 参与 pub-sub 转发时生产和消费的事件事实。下游 runtime 标准 Agent 服务入口由 `FEAT-001` 定义；完整事件转发总契约由 `FEAT-013` 约束；runtime worker 如何消费事件由 `FEAT-2026-017` 承接。

### 3.1 Gateway Client-Facing Interface

Gateway 对 client 的接口面与 `FEAT-2026-011` 保持一致；本特性只说明这些接口在 Gateway 内部通过 pub-sub 转发实现。

| Gateway 接口面 | 调用方向 | 输入要求 | 输出要求 | 约束 |
|---|---|---|---|---|
| Gateway A2A facade | client -> Gateway | A2A 兼容请求；创建类调用必须携带 `agentId`，应携带 `idempotencyKey`，必须携带 client 生成的 `clientInvocationId`。 | 返回 A2A 兼容响应、错误或 SSE 流。 | 不向 client 暴露 runtime endpoint、routeHandle 解析信息、broker topic、worker、内部实例、stream endpoint 或私有执行口。 |
| 阻塞调用入口 | client -> Gateway | `SendMessage` 语义；Gateway 基于认证主体和策略解析 tenant，并归一 trace、correlation、幂等键和请求上下文。 | 在等待窗口内返回 `COMPLETED_RESPONSE`、`ACCEPTED_WITH_TASK`、`REJECTED`、`FAILED` 或 `UNKNOWN`。 | Gateway 已获得 runtime `taskId` 后不得再把该调用报告为 `UNKNOWN`。 |
| 流式调用入口 | client -> Gateway | `SendStreamingMessage` 语义；client 通过同一 Gateway A2A facade 建立 SSE 连接。 | Gateway 在收到 `INVOCATION_STREAM_READY` 后桥接 runtime A2A SSE，直到 Task 终态、中断、下游流错误或 client 连接关闭。 | Gateway 不生成 token、不缓存 token 流、不定义第二套 stream 协议。 |
| Task 查询入口 | client -> Gateway | `GetTask` 语义；必须基于 runtime 生成的 `taskId`。 | 返回 A2A 兼容 Task 快照或确定错误。 | `clientInvocationId` 不得替代 `taskId`。 |
| Task 取消入口 | client -> Gateway | `CancelTask` 语义；必须基于 runtime 生成的 `taskId`。 | 返回取消请求结果、Task 快照或确定错误。 | Gateway 不承诺强制中断底层模型调用。 |
| Task 重订阅入口 | client -> Gateway | `SubscribeToTask` 语义；必须基于 runtime 生成的 `taskId`。 | Gateway 通过总线请求订阅准备，并在流准备后重新桥接该 Task 的标准 SSE 流。 | 找不到 Task 时不得隐式创建新 Task。 |
| UNKNOWN 恢复入口 | client -> Gateway | client 使用同一 `clientInvocationId`、同一 `idempotencyKey` 重试原始创建类调用。 | 若原 Task 已创建，返回同一 `taskId`、接受投影或当前 Task 快照；若未创建，则按新投递或明确拒绝处理。 | 当前版本不新增 `ResolveInvocation` 之类 Gateway 私有查询接口。 |

### 3.2 Gateway Bus Event Interface

本节只定义 Gateway 生产或消费的 bus 事件接口，事件语义必须与 `FEAT-013` 保持一致。客户端不直接消费这些事件。

| 事件封装 / 字段 | 方向 / 类型 | 事实要求 |
|---|---|---|
| `CLIENT_INVOCATION_REQUESTED` | Gateway -> Event Bus 控制事件 | 表示客户端调用已入队，必须携带 clientInvocationId、tenant、routeHandle、payloadRef 和幂等信息；client 可通过 Gateway 侧投影观察该关联状态，但不直接消费该事件。 |
| `CLIENT_INVOCATION_CANCEL_REQUESTED` | Gateway -> Event Bus 控制事件 | 表示客户端请求取消，由消费者映射到 Task owner 的 `CancelTask`；不得只在总线侧标记取消。 |
| `CLIENT_INVOCATION_QUERY_REQUESTED` | Gateway -> Event Bus 控制事件 | 表示客户端查询 Task 或任务列表；payload 必须可映射到标准 Task 查询语义，通常基于 `taskId`。 |
| `CLIENT_STREAM_SUBSCRIBE_REQUESTED` | Gateway -> Event Bus 控制事件 | 表示客户端希望订阅已有服务端 Task 的 A2A SSE 流；必须基于 `taskId`，不得以 `clientInvocationId` 替代。 |
| `INVOCATION_ACCEPTED` | Event Bus -> Gateway 投影事件 | 表示 runtime / consumer 已接受调用并创建或复用 Task，必须携带 taskId、correlation 和幂等结果；创建或复用 Task 后必发。 |
| `INVOCATION_REJECTED` | Event Bus -> Gateway 投影事件 | 表示 runtime / consumer 明确拒绝调用且未创建 Task，必须携带可编程拒绝原因。 |
| `INVOCATION_FAILED` | Event Bus -> Gateway 投影事件 | 表示消费调用事件或处理请求时发生确定失败，必须携带错误码和是否可重试的语义。 |
| `INVOCATION_RESPONSE` | Event Bus -> Gateway 投影事件 | 表示阻塞调用在等待窗口内得到一次性 A2A 响应；必须在同一调用已发布 `INVOCATION_ACCEPTED` 后出现。 |
| `INVOCATION_STREAM_READY` | Event Bus -> Gateway 投影事件 | 表示流可订阅，携带 streamRef 或桥接所需引用，不携带 token、SSE frame 或大对象正文。 |
| `INVOCATION_INPUT_REQUIRED` | Event Bus -> Gateway 投影事件 | 表示 runtime Task 进入等待输入状态，必须携带 taskId、correlation 和输入需求描述；Gateway 只负责向 client 呈现该新状态。 |
| `INVOCATION_TERMINAL` | Event Bus -> Gateway 投影事件 | 表示任务 completed、failed、canceled 或 rejected；Task 终态仍由 runtime 拥有。 |
| payloadRef / artifactRef | 事件引用字段 | 指向大载荷或结果，不要求 Event Bus 保存正文。 |
| clientInvocationId | Gateway 侧关联字段 | 由 client 生成并提交给 Gateway，用于入队、状态观察和 UNKNOWN 后幂等恢复，不能替代 taskId。 |

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| pub-sub 提交调用 | Gateway 和 Event Bus 可用，client 已生成 `clientInvocationId` | client 带着 `clientInvocationId`、tenant、幂等键和 payloadRef 访问 Gateway；Gateway 发布 `CLIENT_INVOCATION_REQUESTED` | Gateway 返回入队接受、等待窗口结果或明确失败；后续 runtime 接受、响应、流准备、input_required 和终态通过投影关联到同一 `clientInvocationId`。 |
| 阻塞调用并返回最终响应 | Gateway 已发布调用事件，目标 runtime 可在等待窗口内完成 | consumer 投递 runtime，runtime 创建 Task 后发布 `INVOCATION_ACCEPTED`，完成后发布 `INVOCATION_RESPONSE` | Gateway 返回 `COMPLETED_RESPONSE` 或等价 A2A 一次性响应。 |
| 阻塞调用退化为 Task 引用 | runtime 已创建或复用 Task，但最终响应未在等待窗口内完成 | consumer 发布 `INVOCATION_ACCEPTED`，但窗口内未发布 `INVOCATION_RESPONSE` | Gateway 返回 `ACCEPTED_WITH_TASK` 或等价 Task 引用；client 后续使用 `taskId` 查询或订阅。 |
| 流式任务观察 | runtime 支持 A2A SSE，服务端已接受 Task 或稍后发布流准备投影 | 消费者发布 `INVOCATION_ACCEPTED` 和 `INVOCATION_STREAM_READY`；client 维持连接或后续基于 `taskId` 订阅 | Gateway 根据 `taskId` 和 streamRef 桥接服务端 A2A SSE；Event Bus 只转发流准备事实和引用，不承载 token chunk。 |
| input_required 投影处理 | runtime Task 进入 INPUT_REQUIRED，且服务端发布 `INVOCATION_INPUT_REQUIRED` | Gateway 消费 input_required 投影并按 `clientInvocationId` / `taskId` 通知 client | Gateway 只呈现新的 input_required 状态、输入需求和关联信息；任务接续、输入校验和 Task 状态推进由 runtime 控制管理。 |
| 取消总线转发任务 | client 已获得服务端 `taskId`，或可通过 `clientInvocationId` 关联到已接受 Task | client 通过 Gateway 发起取消请求 | Gateway 发布 `CLIENT_INVOCATION_CANCEL_REQUESTED`；消费者定位 Task owner 并调用 `CancelTask`；runtime 决定取消结果并发布终态或错误投影。 |
| 接受状态未知与幂等恢复 | Gateway 已发布调用事件，但在接受等待窗口内未观察到接受、拒绝或失败投影 | Gateway 向 client 返回 `UNKNOWN` 或等价状态；client 使用同一 `idempotencyKey` / `clientInvocationId` 重试或继续观察 | 若 runtime 已创建 Task，重复投递必须幂等返回同一 `taskId` 或接受投影；若未创建，则按新投递创建或明确拒绝，不得因重试创建多个 Task。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.0 入队与关联语义

- `clientInvocationId` 表示 Gateway 入队和投影关联，不表示 runtime 已创建 Task。
- 只有 `INVOCATION_ACCEPTED` 携带的 taskId 才是 runtime Task 句柄。
- Client 必须生成并携带 `clientInvocationId` 访问 Gateway；Gateway 可以校验、记录和复用该关联，但不拥有该 ID 的主权。
- 重复提交必须通过同一幂等键和 clientInvocationId 避免重复副作用。

#### 5.1.1 事件投影语义

- Event Bus 事件表达调用控制和状态投影，不表达 runtime 内部状态机全部细节。
- 流式内容、token、SSE frame 和大对象正文必须留在 runtime stream 或引用对象中。
- 投影事件必须携带 tenant、correlation、trace、schemaVersion、clientInvocationId 或可映射关联、taskId 和时间戳，便于审计和恢复；`INVOCATION_REJECTED` / `INVOCATION_FAILED` 等未创建 Task 的事件可以不携带 `taskId`，但必须携带可映射关联。

#### 5.1.2 状态观察、取消和 input_required 语义

- 状态观察应区分 queued、accepted、stream_ready、input_required、terminal、rejected、failed 和 unknown。
- 取消请求必须最终路由到 Task owner，不得只在总线侧标记取消。
- input_required 是 runtime Task 状态投影；Gateway 只负责把新的等待输入状态和输入需求交付给 client。
- 任务接续、输入校验、callbackId 解释和 Task 状态推进由 runtime 或目标服务控制管理，不属于 Gateway 或 Event Bus 职责。
- `clientInvocationId` 不得作为 A2A `GetTask` / `SubscribeToTask` 的标准输入，也不得替代 `taskId`。

#### 5.1.3 UNKNOWN 与幂等语义

- Gateway 发布调用事件后，如果在接受等待窗口内未观察到 `INVOCATION_ACCEPTED`、`INVOCATION_REJECTED` 或 `INVOCATION_FAILED`，必须返回 `UNKNOWN` 或等价未知状态。
- `UNKNOWN` 不表示成功或失败，只表示 Gateway 无法确认 runtime 是否已创建 Task。
- Client 可以使用同一 `idempotencyKey` / `clientInvocationId` 重试原始创建类调用来恢复；当前版本不新增 `ResolveInvocation` 之类私有查询接口。
- runtime 或消费者必须以 `tenantId + idempotencyKey` 约束创建类调用；已创建 Task 时应返回同一 `taskId` 或等价接受投影。

#### 5.1.4 阻塞等待窗口语义

- `COMPLETED_RESPONSE` 表示 Gateway 在阻塞等待窗口内已观察到 `INVOCATION_ACCEPTED` 和最终 `INVOCATION_RESPONSE`。
- `ACCEPTED_WITH_TASK` 表示 Gateway 已观察到 `INVOCATION_ACCEPTED` 并获得 `taskId`，但最终响应未在当前窗口内返回。
- `REJECTED` 表示服务端明确拒绝调用且未创建 Task。
- `FAILED` 表示调用事件投递、消费或服务端处理发生确定失败。
- `UNKNOWN` 表示 Gateway 未能在接受等待窗口内确认服务端是否已创建 Task，不等同于成功或失败。
- runtime 创建或复用 Task 后必须发布 `INVOCATION_ACCEPTED`；即使阻塞调用在等待窗口内完成，也必须先有接受投影，再有 `INVOCATION_RESPONSE`。

#### 5.1.5 错误、状态与可观测结果

| 场景           | 事实要求                                              |
| -------------- | ----------------------------------------------------- |
| 入队失败       | Gateway 返回明确错误，不生成成功 clientInvocationId。 |
| 消费失败       | 按策略重试，仍失败时发布失败投影或可观测错误。           |
| 重复事件       | 消费者幂等处理，不重复创建 Task 或副作用。            |
| streamRef 失效 | Gateway 返回流不可用并允许 GetTask 查询。             |
| input_required 投影 | Gateway 向 client 呈现等待输入状态，不推进 runtime Task。 |
| 接受阶段超时 | 返回 UNKNOWN 或等价未知状态，允许 client 用同一 `clientInvocationId` 和同一幂等键重试原始调用恢复。 |
| 终态投影丢失   | 可通过 taskId 查询 runtime 或重建投影。               |

### 5.2 显式边界与不承诺项

| 边界                 | 当前版本不承诺                                          |
| -------------------- | ------------------------------------------------------- |
| Event Bus 执行 Agent | 总线不调用模型、工具、memory、knowledge 或业务 Agent。  |
| Task 权威状态        | Event Bus 不保存 runtime TaskStore，不决定 Task 终态。  |
| token 流承载         | 不通过总线传 token chunk、SSE frame 或大对象正文。      |
| 物理 endpoint 暴露   | routeHandle 不等于 endpoint，不向 client 暴露内部拓扑。 |
| 强一致编排           | 总线投影是异步事实，不替代 runtime Task 查询。          |
| 私有 runtime 入口    | 消费者应投递标准 A2A 或等价 Task control path。         |
| 任务接续控制         | Gateway 和 Event Bus 不管理 input_required 后的 Task 状态推进。 |
| 调度治理策略         | 本特性不定义运行时调度治理策略。 |
| 私有恢复查询         | 当前版本不新增 `ResolveInvocation` 之类以 `clientInvocationId` 查询 runtime Task 的私有接口。 |
| 后台流缓存           | Gateway 不在 client 断开后长期消费、缓存或重放 token 流。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把本特性作为客户端调用总线转发事实来源，Event Bus 只能承载控制事件、状态投影和引用。
- Gateway、Event Bus、worker / consumer 和 runtime 必须共享 clientInvocationId、taskId、routeHandle、payloadRef、streamRef、schemaVersion、eventId、correlation 和幂等语义。
- 测试必须覆盖带 clientInvocationId 的 pub-sub 入队、`INVOCATION_ACCEPTED` 必发、阻塞五类响应状态、流准备、Gateway 只桥接不缓存、input_required 投影、取消、终态、UNKNOWN 后同一 clientInvocationId + idempotencyKey 恢复、重复事件、大载荷引用和 token 不入总线。
- 开发指南不得把 Event Bus 描述为 Agent 执行引擎或 TaskStore。
- 开发指南不得把 Gateway 描述为 input_required 接续控制器；接续控制和 Task 状态推进属于 runtime 或目标服务。
- 任何对总线承载 token 流、执行 Agent、保存业务 checkpoint、承担运行时调度治理或绕过 runtime 标准入口的新增承诺，都必须先更新本特性或新增 version-scope 特性。
- 本特性术语必须保持稳定：CLIENT_INVOCATION_REQUESTED、INVOCATION_ACCEPTED、INVOCATION_STREAM_READY、INVOCATION_INPUT_REQUIRED、INVOCATION_TERMINAL、UNKNOWN、clientInvocationId、idempotencyKey、payloadRef、streamRef。

## 7. 关联文档

- `agent-sdk/Docs/agent-gateway组件客户端调用总线转发特性设计.md`
- `Docs/FEAT_Design/FEAT-2026-011-agent-gateway-client-invocation-route-forwarding.md`
- `JAVA local working/version-scope/FEAT-013-client-invocation-event-forwarding.md`
- `Docs/FEAT_Design/FEAT-2026-017-agent-runtime-bus-event-subscription-consumption.md`
- `Docs/FEAT_Design/FEAT-001-standardized-agent-service-entrypoint.md`
