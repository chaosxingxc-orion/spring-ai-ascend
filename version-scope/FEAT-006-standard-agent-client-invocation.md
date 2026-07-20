---
version: 0715
module: agent-client
feature_type: functional
feature_id: FEAT-006
status: active
---

# 客户端发起标准化智能体调用特性文档

## 1. 特性定位

FEAT-006 定义 `agent-client` 当前版本作为业务应用侧标准 Agent 调用 SDK 的入口事实：业务应用通过统一 client facade 发起智能体调用、接收调用结果、观察执行过程、查询调用、取消调用和继续等待输入，而不是直接感知后端 `agent-runtime`、Gateway、Event Bus、runtime endpoint、服务端 `taskId` 或 A2A JSON-RPC 协议形态。

本特性解决的问题是：不同业务应用需要用同一套客户端调用语义接入智能体平台。业务应用侧应感知稳定的客户端调用语义，包括 `conversationId`、`invocationRef`、调用模式、调用状态投影、等待输入状态、错误分类和恢复线索；平台侧可以通过 Gateway / IngressGateway、agent-bus 或 runtime 标准 A2A 入口完成治理、路由、事件转发和执行。客户端只维护调用过程所需的本地上下文、服务端 task 引用和只读状态投影，不拥有服务端 Task 权威状态。

对总体设计而言，本特性是 C/S 流量进入智能体框架的客户端 SDK 入口约束。`agent-client` 位于 application 与 Gateway / IngressGateway / agent-bus 入口之间，负责把业务输入、凭证上下文、`conversationId`、`invocationId`、幂等键、trace、调用模式和关联旧 invocation 的继续意图标准化。服务端 Task、SSE、取消控制、等待输入校验和终态由 `agent-runtime` 拥有，并可由 Gateway / Event Bus 投影给客户端。客户端 facade 可以有 SDK、starter、HTTP/SSE client 等交付形态，但不得形成独立于 runtime Task 的第二套服务端状态机。

本特性面向以下角色：

- 业务应用开发者：通过 `agent-client` 发起智能体调用并展示 invocation 状态。
- 企业终端集成方：把业务页面、凭证上下文、conversation 和 invocation 恢复线索接入标准调用链路。
- agent-client SDK 开发者：实现调用 facade、流消费、轮询、重订阅、取消、继续等待输入和只读状态展示。
- Gateway / IngressGateway 开发者：把 client facade 请求映射到标准平台入口。
- agent-runtime 开发者：承接标准 A2A Task、SSE、取消、订阅和续接语义。
- 测试与验收团队：按客户端外部行为设计黑盒和集成验证。

本特性只定义 `agent-client` 面向业务应用的标准调用入口、业务可见语义和只读状态投影。服务端 Agent Card、A2A JSON-RPC、Task 权威状态、agent-bus forwarding、客户端本地工具治理和 Gateway 路由转发由对应 runtime、gateway、bus 和 client capability 特性承接。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 标准调用 facade | MUST | `agent-client` 必须向业务应用提供统一调用入口，覆盖创建 invocation、查询 invocation、取消 invocation、重订阅 invocation 和以新 invocation 继续等待输入。 |
| Conversation 传递 | MUST | SDK 必须支持业务应用传入或委托生成 `conversationId`，并在后续同一 conversation 的调用中稳定传递；conversation 主权归业务应用。 |
| 调用模式声明 | MUST | 创建 invocation 时必须声明 `BLOCKING`、`STREAMING` 或 `ASYNC` 调用模式；调用模式是端到端执行诉求，不是调用创建后的本地观察选项。 |
| 业务上下文与凭证传递 | MUST | SDK 必须允许请求携带 agentId、业务输入、correlation、trace、幂等键和凭证上下文，并传递到 Gateway / IngressGateway 或受治理平台入口。 |
| Invocation 回显 | MUST | SDK 必须向业务应用回显 `conversationId`、`invocationRef`、幂等键、调用模式、调用结果类型、状态投影和恢复线索；不得要求业务应用持有 `taskId` 才能操作调用。 |
| Task 内部映射 | MUST | SDK 必须在内部保存 invocation 与服务端 taskRef 的受治理映射，用于查询、取消、重订阅、继续等待输入和 UNKNOWN 恢复；该映射不得成为服务端 TaskStore。 |
| 平台入口对接 | MUST | SDK 请求必须进入受治理平台入口；业务应用不得直接配置 runtime 内部 API、物理 endpoint、broker、Event Bus topic 或服务端 `taskId` 操作入口。 |
| 流式调用 | MUST | SDK 必须支持在创建 invocation 时声明全链路流式诉求，并消费 Gateway / runtime A2A SSE 或等价服务流，把服务端事件映射为 invocation 可见只读投影。 |
| 阻塞调用 | MUST | SDK 必须支持一次性响应窗口；阻塞结果必须来自服务端标准 Task / Message 表面或 Gateway 投影，不能伪造成功或失败。 |
| 异步调用 | MUST | SDK 必须支持提交后异步观察的调用模式；服务端接受后由 client 内部绑定 taskRef，业务应用后续通过 invocationRef 查询、重订阅或接收投影。 |
| Invocation 状态观察 | MUST | SDK 必须支持通过 Gateway 投影、状态查询、重订阅或受治理映射观察 invocation 进展；不得只返回本地缓存状态。 |
| 取消调用 | MUST | SDK 必须提供 cancel 能力，业务应用基于 invocationRef 请求取消；client 内部解析 taskRef 并通过 Gateway / IngressGateway 映射到 runtime 取消控制路径，取消结果以 runtime Task 状态为准。 |
| 继续等待输入 | MUST | SDK 必须支持业务应用以新的 invocation 关联旧 invocation 的 input_required 状态，提交用户补充输入、本地确认或客户端能力结果；恢复点校验和 Task 状态推进由 runtime 控制。 |
| 幂等处理 | MUST | SDK 必须支持生成、提交或复用创建类 invocation 的幂等键；继续等待输入类 invocation 也必须具备独立幂等语义；重试不得造成重复 Task 或重复副作用。 |
| UNKNOWN 恢复 | MUST | SDK 必须在接受状态未知时回显 `UNKNOWN` 和 invocation 恢复线索，并允许使用同一 `invocationId`、同一幂等键、同一原始创建类请求恢复关联；当前版本不新增私有 `ResolveInvocation` 查询语义。 |
| 错误分类 | MUST | SDK 必须区分网络错误、路由错误、服务端 A2A / Task 错误、业务失败、取消、拒绝、接受未知、关联 invocation 不可续接和流式能力不可用。 |
| 本地状态边界 | MUST | SDK 只能保存调用过程所需的本地上下文、幂等键、游标、taskRef 映射和只读状态投影；不得实现服务端 TaskStore 或决定 Task 终态。 |

## 3. 外部接口与入口要求

本节定义业务应用通过 `agent-client` 可感知的接口面和输入输出事实，不承诺具体 Java 方法名、HTTP path 或类名。L2 设计可以选择 SDK 方法、Spring Boot starter、REST-like facade 或其他交付形态，但不得改变本文定义的语义。

| 接口面 | 类型 | 输入要求 | 输出要求 | 约束 |
|---|---|---|---|---|
| 创建 invocation 入口 | client facade | 必须携带 agentId、`conversationId`、调用模式、业务输入、trace/correlation、凭证上下文；必须生成或接收 `invocationId` 与幂等键。可选携带 `relatedInvocationRef` 或 `inputRequiredRef` 表达继续等待输入。 | 返回 `InvocationResult`，回显 `conversationId`、`invocationRef`、幂等键、调用模式、结果类型、状态投影和恢复线索。 | 每次业务应用发起 client 调用都形成新的 invocation；不得要求业务应用传入或操作 `taskId`。 |
| invocation 查询入口 | client facade | 必须基于 `invocationRef`，可携带 `conversationId` 用于业务侧校验和隔离。 | 返回 `InvocationSnapshot`、等待输入状态、终态投影或确定错误。 | client 内部可使用 taskRef 查询 runtime，但不得把 taskId 变成业务应用操作句柄。 |
| invocation 重订阅入口 | client facade | 必须基于 `invocationRef`，可携带 stream cursor 或恢复上下文。 | 返回该 invocation 关联的服务流或 Gateway 投影流。 | 重订阅只观察已有 invocation，不改变创建时的调用模式，不隐式创建新 invocation。 |
| invocation 取消入口 | client facade | 必须基于 `invocationRef`，并携带租户、trace、幂等和权限上下文。 | 返回取消请求结果、Invocation 快照或确定错误。 | client 内部映射到 runtime `CancelTask`；SDK 不承诺强制中断底层模型调用。 |
| 继续等待输入入口 | client facade | 通过创建新的 invocation 表达；必须携带同一 `conversationId`、新的输入内容，以及旧 invocation 的 `relatedInvocationRef` 或 `inputRequiredRef`。 | 返回新的 `InvocationResult`，并在状态投影中表达与旧 invocation 的关联和当前等待输入推进结果。 | 如果关联状态不可续接、已过期、已终态或存在多义性，client 必须返回明确错误，不得偷偷创建普通新任务。 |
| UNKNOWN 恢复入口 | client facade | 使用同一 `invocationId`、同一幂等键和同一原始创建类请求。 | 若原 Task 已创建，client 内部恢复 taskRef 映射并返回同一 invocation 的当前投影；若未创建，则按新投递或明确拒绝处理。 | 当前版本不新增以 `invocationId` 查询 runtime Task 的私有接口。 |
| 错误与状态返回 | client facade | 适用于所有入口。 | 必须提供可编程错误码、retryable、correlation/trace、可恢复线索、invocation 状态和必要诊断信息。 | 不把业务失败包装成网络失败，不把 `UNKNOWN` 当成功或失败。 |

业务应用不需要感知 A2A JSON-RPC envelope、Gateway routeHandle、Event Bus topic、runtime endpoint、服务端 `taskId` 或内部 TaskStore；但必须能够感知和展示 client facade 回显的业务级调用语义。若 SDK 为诊断目的回显 server task 引用，该引用必须标记为非操作性诊断信息，不得成为业务应用发起查询、取消、订阅或继续输入的必需参数。

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 新会话发起新调用 | 应用已集成 SDK，平台入口可用 | 应用使用新的 `conversationId` 通过 client facade 发起调用，并声明 `BLOCKING`、`STREAMING` 或 `ASYNC` 模式 | SDK 创建新的 invocation，标准化请求并提交 Gateway；返回 `conversationId`、`invocationRef`、幂等键和结果类型。服务端接受后，client 内部绑定 taskRef 并投影 invocation 状态。 |
| 旧会话发起新调用 | 应用希望在已有上下文中继续提问或下达新指令 | 应用复用已有 `conversationId` 发起新的 client 调用 | SDK 创建新的 invocation；runtime 可基于相同 conversation 获得历史上下文可见性。该调用不是旧 invocation 的续接，通常对应新的服务端受理事务。 |
| 阻塞等待退化为可查询调用 | 应用声明 `BLOCKING`，请求适合一次性响应窗口 | Gateway/runtime 在等待窗口内处理调用 | 若窗口内完成，返回完成响应投影；若已接受但未完成，返回 `ACCEPTED_WITH_INVOCATION` 或等价已接受状态；若无法确认是否已接受，返回 `UNKNOWN` 和 invocation 恢复线索。 |
| 全链路流式调用 | 应用声明 `STREAMING`，目标链路支持流式能力 | SDK 创建 invocation 并保持服务流消费 | 流式诉求从 client 创建点进入 Gateway/runtime，并在后续远端 A2A 多跳中作为执行上下文传播；SDK 消费 SSE / 服务流并映射 token、progress、input_required 和终态投影。 |
| 异步提交与状态观察 | 应用声明 `ASYNC` 或阻塞调用退化为已接受 invocation | 应用后续通过 invocationRef 查询、重订阅或接收 Gateway 投影 | SDK 基于 invocationRef 观察执行中、等待输入、完成、失败、取消等状态；client 内部通过 taskRef 与平台交互。 |
| 查询和取消调用 | 应用已获得 `invocationRef` | 应用调用查询或取消 facade | SDK 基于 invocationRef 解析内部 taskRef，通过 Gateway 转发 `GetTask` 或 `CancelTask` 语义；runtime 作为 Task owner 返回快照、取消结果或确定错误，SDK 映射为 invocation 投影。 |
| 继续 input_required | 某个旧 invocation 进入等待输入状态 | 应用复用同一 `conversationId`，携带 `relatedInvocationRef` 或 `inputRequiredRef` 发起新的 invocation 并提交补充信息 | SDK 创建新的 invocation，解析旧 invocation 的等待输入状态和内部 taskRef，提交给 Gateway / runtime 继续既有 Task；runtime 校验恢复点并决定是否推进。 |
| 多个等待输入消歧 | 同一 conversation 下存在多个 input_required invocation | 应用选择要继续的旧 invocation 或等待输入引用 | SDK 只续接被明确指定的目标；若未指定或存在多义性，返回明确错误，不凭 conversationId 猜测。 |
| UNKNOWN 后恢复 | Gateway 无法确认 runtime 是否创建 Task，且 client 尚未建立 taskRef 映射 | 应用或 SDK 使用同一 invocation 关联和幂等信息重试原始创建类调用 | 若原 Task 已创建，client 内部恢复 taskRef 映射并返回同一 invocation 投影；若未创建，则按新投递或明确拒绝处理；不得因重试创建多个 Task。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.0 Conversation、Invocation 与 Task 边界

- `conversationId` 标定多轮交互中的上下文生命周期和隔离边界；conversation 主权归业务应用，client 负责传递、回显和关联，不决定业务会话生命周期。
- `invocationId` / `invocationRef` 标定客户端发起的一次智能体服务调用事务；invocation 主权归 `agent-client`，业务应用通过 invocationRef 操作调用，Gateway 可引用 invocation 进行关联和投影。
- `taskId` / internal `taskRef` 标定服务端已受理的执行事务单元；Task 主权归 `agent-runtime`，其创建、生命周期、状态迁移、终态和与其他 Task 的关联关系均由 runtime 管理。
- 一个 conversation 下可以产生多个 invocation；每次业务应用通过 client 发起调用，都必须形成新的 invocation，包括普通新问题、新指令和继续 input_required。
- 一个 invocation 在 client 内部可以绑定零个、一个或多个服务端 taskRef 投影；业务应用不依赖 task 引用完成操作。
- 继续等待输入不是独立的 turn 语义，也不是业务应用直接操作 Task；它是业务应用发起的新 invocation，并通过 `relatedInvocationRef` 或 `inputRequiredRef` 关联旧 invocation 的等待输入状态。
- 如果同一 conversation 下存在多个等待输入的 invocation，业务应用必须明确指定要关联的 relatedInvocationRef 或 inputRequiredRef；client 不得仅凭 conversationId 猜测目标。
- `sessionId` 与 `conversationId` 语义相近，但当前版本术语统一使用 `conversationId`，避免与微服务 session 语义碰撞。

#### 5.1.1 客户端入口等价语义

- 不同业务应用通过 `agent-client` 调用 Agent 时，应看到同一组创建 invocation、查询 invocation、取消 invocation、重订阅 invocation 和继续等待输入语义。
- SDK 可以根据部署选择 HTTP、SSE、Gateway facade 或总线异步入口，但 invocation 状态必须最终对齐服务端 Task 或受治理投影。
- SDK 不得因为底层经过 Gateway、Event Bus、agent-service 或 runtime 而暴露互相漂移的业务状态机。
- 业务应用不感知 A2A 协议形态和服务端 `taskId`，但必须能感知 `conversationId`、`invocationRef`、调用模式、调用结果分类、等待输入状态、错误分类和恢复线索。

#### 5.1.2 调用模式语义

| 调用模式 | 语义 | 结果边界 |
|---|---|---|
| `BLOCKING` | 客户端声明希望在当前请求窗口内获得一次性结果，不要求全链路流式投递。 | 可返回完成响应投影、已接受 invocation、`REJECTED`、`FAILED` 或 `UNKNOWN`。服务端不应为了满足 blocking 语义而强制走流式聚合路径；只有当底层天然以流式产生且聚合成本可接受时，才可聚合后返回。 |
| `STREAMING` | 客户端声明本次 invocation 需要全链路流式能力。 | 流式诉求必须沿 Gateway/runtime/远端 A2A 调用链传播；不支持时不得静默降级，可以按显式策略降级并在返回中回显降级事实。 |
| `ASYNC` | 客户端声明提交后异步观察即可。 | 创建成功后返回 invocation 接受投影；后续通过 invocation 查询、重订阅或 Gateway 投影观察。 |

调用模式必须在创建 invocation 时声明。基于 invocation 的重订阅入口只用于观察已有 invocation，不能把普通调用升级成流式调用。

#### 5.1.3 调用提交与继续输入语义

- 提交请求必须携带可关联的业务上下文、`conversationId`、调用模式、幂等键和输入内容。
- SDK 必须生成或接收 `invocationId`，并将其作为客户端调用关联回显给业务应用。
- SDK 必须复用幂等键处理创建类 invocation 重试；重复提交不得造成重复 Task。
- 继续 input_required 时，业务应用必须发起新的 invocation，并关联旧 invocation 的等待输入状态。
- 继续输入请求必须由 client 内部携带 runtime 所需的 taskRef、callbackId、correlationId 或等价上下文，便于 runtime 校验恢复点。
- 过期、重复、跨租户、跨用户、跨 conversation、不存在、存在多义性或终态后的续接必须返回明确错误。

#### 5.1.4 流式调用与重订阅语义

- `STREAMING` 是端到端执行诉求，不是 SDK 本地观察选项。
- SDK 消费到的流式事件必须映射为 invocation 可理解状态，但状态来源仍是 Gateway / runtime 投影。
- SSE 中断不等于 Task 失败；SDK 应提示调用方通过 invocation 查询或基于 invocationRef 重订阅确认进展。
- 流式 token、progress、input_required 和终态必须与 runtime Task/SSE 语义保持一致。
- 不支持流式的链路不得静默降级；如平台策略允许降级到 `ASYNC` 或 `BLOCKING`，SDK 返回必须显式暴露降级事实和实际模式。

#### 5.1.5 查询、取消与 UNKNOWN 恢复语义

- 查询能力必须基于 `invocationRef` 返回服务端 Task 或受治理投影映射后的 invocation 状态，不得只读取本地调用对象。
- `cancel` 必须由 client 内部映射到 runtime 取消控制路径；底层执行是否立即中断由 runtime/adapter 能力决定。
- `UNKNOWN` 表示 Gateway / SDK 无法确认 runtime 是否已经接受创建类 invocation，不表示成功、失败或已创建 Task。
- UNKNOWN 后只能使用同一 `invocationId`、同一幂等键和同一原始创建类请求恢复关联。
- 当前版本不新增 `ResolveInvocation` 之类以 `invocationId` 查询 runtime Task 的私有接口。
- 若服务端已经创建或复用 Task，client 必须恢复内部 taskRef 映射并返回同一 invocation 的投影；若未创建，则按新投递或明确拒绝处理。

#### 5.1.6 错误、状态与可观测结果

| 场景 | 事实要求 |
|---|---|
| 网络失败 | SDK 返回可重试网络错误，并回显 invocation 幂等键和调用关联用于恢复判断。 |
| 路由失败 | SDK 暴露 route not found、permission denied、service unavailable 等平台错误。 |
| 服务端失败 | SDK 展示 failed invocation 或 A2A / Task 错误投影，不把业务失败包装成网络失败。 |
| 接受未确认 | SDK 返回 `UNKNOWN` 或等价未知状态，并回显 `invocationRef`、幂等键和恢复要求。 |
| 已接受未完成 | SDK 返回已接受 invocation 或等价结果，并回显 invocation 状态投影。 |
| SSE 中断 | SDK 提示调用方通过 invocation 查询或重订阅确认任务进展。 |
| 流式能力不可用 | 不得静默降级；返回明确错误或显式降级结果。 |
| 关联 invocation 不可续接 | SDK 返回明确错误，不得静默创建普通新任务。 |
| 用户取消 | SDK 转发取消请求，并以服务端 Task 状态映射后的 invocation 投影为准。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| 服务端 TaskStore | `agent-client` 不保存或复制服务端 Task 权威状态。 |
| 业务应用 Task 操作 | 业务应用不通过 `taskId` 查询、取消、订阅或继续任务；这些操作必须通过 `invocationRef` 进入 client facade。 |
| Conversation 主权 | SDK 不拥有业务会话生命周期；conversation 的创建、命名、归档和业务隔离策略由业务应用决定。 |
| 直接内部调用 | SDK 不直接调用 agent-runtime、agent-core、agent-middleware 内部 SPI。 |
| 租户认证 | SDK 不认证租户身份；租户认证、清洗和注入由 Gateway 或企业入口完成。 |
| Agent 执行 | SDK 不执行 Agent 推理、工具调用、记忆、知识库或模型访问。 |
| 强制取消底层模型 | SDK cancel 不承诺强制中断已进入模型客户端的阻塞调用。 |
| 多 Agent 编排 | SDK 只发起和观察调用，不定义服务端多 Agent 路由、聚合和调度策略。 |
| Turn 语义 | 当前版本不引入独立 turn 概念；多轮通过 conversation 下的多个 invocation 表达，等待输入继续通过新 invocation 关联旧 invocation 状态表达。 |
| 本地工具治理 | SDK 标准调用只承接客户端能力结果作为继续等待输入的一种内容；本地工具注册、执行、审批、审计和结果提交闭环由 FEAT-007 定义。 |
| 业务应用持久化策略 | SDK 必须回显恢复所需句柄和状态，但不规定业务应用是否持久化、如何持久化或如何展示这些信息。 |
| RESTful runtime facade | 历史 `FEAT-006-restful-client-facade.md` 已废弃，不再作为 FEAT-006 的事实来源；REST-like 交付形态不得改变本文定义的 client invocation 语义。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把本特性作为业务应用侧标准 Agent 客户端调用入口事实来源，不得把 `agent-client` 降级为示例代码或直接 runtime API 包装。
- `agent-client` SDK、Gateway facade、IngressGateway、agent-bus 转发和 runtime A2A 入口必须保持同一套 Conversation、Invocation、Task 内部映射、SSE、取消、继续输入、幂等、UNKNOWN 恢复和错误语义。
- SDK 状态模型必须区分业务应用主权的 Conversation、client 主权的 Invocation、runtime 主权的 Task、平台投影状态和本地 UI 展示状态。
- 测试必须覆盖三种调用模式、新 conversation 调用、旧 conversation 新调用、input_required 关联旧 invocation 继续、同 conversation 多个等待输入消歧、标准提交、流式端到端诉求传播、阻塞响应、阻塞退化、异步查询、重订阅、取消、幂等重试、UNKNOWN 恢复、显式降级和错误分类。
- 开发指南不得要求业务应用理解 runtime 内部 handler、TaskStore、Agent-core loop、agent-middleware 工具实现、Gateway routeHandle、Event Bus topic、服务端 `taskId` 或 A2A JSON-RPC envelope。
- 任何对客户端 TaskStore、业务应用 Task 操作接口、SDK 持有 Conversation 主权、服务端编排能力、私有 ResolveInvocation 接口、独立 turn 模型或静默流式降级的新增承诺，都必须先回到本特性或新的 version-scope 特性文档更新事实要求。
- 本特性使用的术语必须保持稳定：agent-client、conversationId、invocationRef、invocationId、idempotencyKey、Task、taskRef、Gateway、IngressGateway、SSE、BLOCKING、STREAMING、ASYNC、UNKNOWN、Cancel。

## 7. 关联文档

- `architecture/L1-High-Level-Design/agent-client/overview.md`
- `architecture/L1-High-Level-Design/agent-client/logical.md`
- `architecture/L1-High-Level-Design/agent-client/scenarios.md`
- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `version-scope/FEAT-007-local-tool-registration-remote-driven-invocation.md`
- `version-scope/FEAT-011-client-invocation-route-forwarding.md`
- `version-scope/FEAT-012-client-invocation-bus-forwarding.md`
