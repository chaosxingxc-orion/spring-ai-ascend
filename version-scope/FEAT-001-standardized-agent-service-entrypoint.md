---
scope: v0730
module: agent-runtime
feature_type: functional
feature_id: FEAT-001
status: active
updated: 2026-07-23
---

# 标准化智能体服务入口

## 1. 特性定位

FEAT-001 定义 `agent-runtime` 当前版本作为标准化 Agent 服务端的入口事实：runtime 必须以 A2A JSON-RPC over HTTP 暴露可发现、可调用、可查询、可接收 runtime-to-runtime 异步完成回调的 Agent 服务面，以 Agent Card 作为能力发现入口，并通过 A2A Task / Message / SSE / callback 语义承载 Agent 执行过程。

本特性解决的问题是：普通 client、其他 agent-runtime、agent-bus forwarding 在进入 `agent-runtime` 时都必须看到同一套标准化 Agent 服务面，而不是按调用来源拆出多套私有入口。runtime-to-runtime 远程调用在不保持 SSE 长连接时，可以通过受信任的固定 callback receiver 获得远端 Task 完成、失败、取消或拒绝后的异步结果通知。

本特性只定义 `agent-runtime` 作为服务端被调用的 inbound 入口，以及该入口在 runtime-to-runtime 调用下产生异步完成 callback 的服务端行为。`agent-runtime` 主动发现远端 Agent、安装远端工具、发起 outbound 编排和结果回灌由远程编排特性承接，不属于 FEAT-001 主体。

本特性面向以下角色：

- 普通 Agent client：通过 HTTP / JSON-RPC / SSE 调用 Agent。
- 其他 agent-runtime：把本 runtime 当作远端 A2A Agent 调用，并可在受信任 runtime-to-runtime 场景中配置 callback 等待异步完成通知。
- agent-bus forwarding runtime：把转发消息投递到本 runtime 的标准 Agent 服务入口。
- Agent 开发者：通过 handler 和 Agent Card 声明把自己的 Agent 暴露成 A2A Agent。
- 平台集成方：把 runtime 放到网关、服务发现和多 Agent 协作链路中。
- 测试与验收团队：按本特性定义的外部行为和边界设计黑盒场景。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| A2A Agent Card 发现 | MUST | runtime 必须提供 `GET /.well-known/agent-card.json`，并兼容 `GET /.well-known/agent.json`。返回的 Agent Card 必须表达 Agent 名称、描述、版本、A2A endpoint、capabilities、skills、input/output modes 等当前版本公开事实。 |
| A2A JSON-RPC 统一入口 | MUST | runtime 必须通过 `POST /a2a` 和 `POST /a2a/` 承载 A2A JSON-RPC 请求，按 JSON-RPC `method` 分发，而不是为每个 A2A method 暴露独立业务 URL。 |
| 普通 client inbound 调用 | MUST | runtime 必须允许外部 client 直接通过标准 A2A 服务入口调用 Agent；该入口不得要求调用方了解底层 Agent 框架。 |
| runtime-to-runtime inbound 调用 | MUST | runtime 必须允许其他 agent-runtime 通过 Agent Card 和 `/a2a` 把本 runtime 当作远端 Agent 调用；该路径与普通 client 共享同一入口语义。 |
| agent-bus forwarding inbound 投递 | MUST | agent-bus 将消息转发到 runtime 时，必须落到同一标准 Agent 服务入口；runtime 侧不得为 agent-bus 暴露另一套私有执行入口。 |
| 流式调用 | MUST | `SendStreamingMessage` 必须作为 Agent 全流程流式调用入口，调用方通过 SSE 观察 Task 状态、artifact/progress 和最终终态。 |
| 阻塞调用 | MUST | `SendMessage` 必须接受与流式调用一致的 message 输入，并返回单个 JSON-RPC result。`SendMessage` 可在请求中携带 `pushNotificationConfig`，用于创建 Task 时同步关联异步完成 callback 配置。 |
| 异步查询 | MUST | `GetTask` 必须允许调用方按 task id 查询 Task 状态和结果。 |
| Push Notification Config Create | MUST | 当前版本只承诺创建能力：支持 `CreateTaskPushNotificationConfig` 为既有 Task 创建并关联 callback 配置；也支持 `SendMessage` 时通过 `params.pushNotificationConfig` 同步创建并关联 callback 配置。 |
| Push Notification Config Read/Update/Delete | OUT | 当前版本显式不承诺查询、列出、更新或删除 push notification config；不暴露 `GetTaskPushNotificationConfig`、`ListTaskPushNotificationConfig`、`UpdateTaskPushNotificationConfig`、`DeleteTaskPushNotificationConfig`。 |
| runtime-to-runtime callback receiver | MUST | runtime 在启用 push notification 行为且授权策略配置完成时，必须 host 固定 callback 接收入口 `POST /a2a/push-notifications/callback`；未启用时不得暴露该能力或必须返回未启用响应。 |
| runtime-to-runtime callback 投递 | MUST | runtime 必须具备向受信任调用方 runtime 固定 callback receiver 推送异步完成结果的能力；该能力可由部署配置关闭，关闭时 Agent Card 和 capability 声明不得显示为已启用。 |
| callback 触发范围 | MUST | callback 只在 Task 进入结果性状态时触发：`COMPLETED` 返回完成结果，`FAILED` 返回异常状态、错误码和失败原因；`CANCELED` / `REJECTED` 仅在 SDK/handler 明确产出时承载。submitted / working / progress / artifact update 等中间态不得作为当前版本 callback 主路径。 |
| callback 文本结果 | MUST | 文本类完成结果必须支持在 callback body 中一次性返回；实现应按当前 Agent 上下文规模支持常见文本结果，不要求调用方再通过 SSE 获取文本正文。 |
| callback 大载荷引用 | MUST | 文件类、多模态类、artifact 大正文或超过回调承载策略的结果必须沿用 `SendMessage` result 中 SDK/A2A 可表达的 artifact、file/data part、metadata 或 Task 查询引用，不得为 callback 单独发明强制字段。 |
| callback 与 streaming 模式分离 | MUST | Streaming 调用用于实时过程观察；callback push notification 用于异步完成结果通知。callback 不承载 token-by-token、progress stream 或 SSE frame。 |
| callback 安全边界 | MUST | callback receiver 必须是受信任 runtime 的固定 endpoint，或由 registry/allowlist/部署配置解析出的等价固定入口；当前版本不把普通 client 自报任意 URL 作为事实能力。接收 callback 时必须先校验授权，非法调用必须在进入 TaskStore 或业务处理前拦截。 |
| Agent Card 配置 | MUST | Agent Card 必须支持由运行时配置与服务身份信息生成；配置中未声明的字段必须有可解释的默认值。 |
| Agent Card skills | MUST | 如果 Agent 希望被其他 Agent 发现并作为工具调用，Agent Card 必须能声明 skills；无 skills 的 Agent Card 不应被远程工具安装链误认为可调用工具集合。 |
| Agent Card capabilities | MUST | Agent Card 必须能声明 streaming、pushNotifications、extendedAgentCard 等 A2A capability 状态；capability 声明必须反映当前版本对外承诺，不得夸大未激活能力。若 callback 推送或 callback receiver 被部署配置关闭，`pushNotifications` 不得声明为可用完成回调能力。 |
| JSON-RPC 错误表面 | MUST | 非法 JSON、非法 request shape、未知 method、SDK/handler 异常必须以 JSON-RPC error response 或流式传输错误表面返回；错误 response 必须尽量保留原 request id。 |
| HTTP + SSE 传输 | MUST | 当前版本的 inbound A2A 传输以 HTTP JSON-RPC 和 SSE 为事实要求。 |
| HTTP callback 传输 | MUST | 当前版本的 runtime-to-runtime 异步完成通知以 HTTP POST 到固定 callback receiver 为事实要求；具体签名、token 或 mTLS 机制由部署和 A2A SDK 能力确定。 |
| gRPC 传输 | OUT | 当前版本不要求 runtime 暴露 gRPC northbound 传输。 |
| 普通 client callback | OUT | 当前版本不承诺普通 client 或业务应用自报 callback URL 后由 runtime 主动推送；普通 client 仍应使用 SSE、`GetTask` 或其应用侧集成通道。 |

## 3. 外部接口与入口要求

| 入口 | 类型 | 事实要求 |
|---|---|---|
| `GET /.well-known/agent-card.json` | HTTP endpoint | 必须返回当前 Agent 的 A2A Agent Card。若配置了公开 base URL，card 中相对 URL 必须按该公开 base 解析；否则按当前请求地址解析。 |
| `GET /.well-known/agent.json` | HTTP endpoint | 必须作为 Agent Card 兼容发现入口，返回与标准 card endpoint 等价的 Agent Card 表面。 |
| `POST /a2a` / `POST /a2a/` | HTTP endpoint | 必须承载 A2A JSON-RPC 请求，并按 JSON-RPC `method` 分发到已支持的阻塞、流式、查询或 push config 创建路径。 |
| `SendStreamingMessage` | JSON-RPC method | 必须返回 SSE 事件流；每个 SSE event 必须使用 `event: jsonrpc`，data 为 JSON-RPC envelope。 |
| `SendMessage` | JSON-RPC method | 必须返回单个 JSON-RPC result，结果是 A2A Task 或 Message 表面；可在 `params.pushNotificationConfig` 中携带本次 Task 的 callback 配置。 |
| `GetTask` | JSON-RPC method | 必须返回指定 task 的当前快照。 |
| `CreateTaskPushNotificationConfig` | JSON-RPC method | 必须支持为既有 Task 创建并关联 callback 配置；不提交 Agent message，不启动 Agent 执行。 |
| `Get/List/Update/DeleteTaskPushNotificationConfig` | JSON-RPC method | 当前版本不暴露；调用这些 method 必须返回 method-not-found 或等价 unsupported 表面。 |
| `POST /a2a/push-notifications/callback` | HTTP callback endpoint | 本 runtime 作为调用方时接收远端 runtime 完成通知的固定入口；只有 push notification 行为启用且授权策略可用时暴露。 |
| callback payload | HTTP body | 必须复用 `SendMessage` 的 JSON-RPC response/result 表面：以 A2A Task/Message 表达 taskId、状态、结果或错误。notification id、鉴权、投递时间等通知元数据优先走 HTTP header、签名材料或接收侧记录；不作为 body 顶层强制字段。 |
| callback notification id | idempotency key | 每次 callback 完成通知必须有稳定通知 ID，接收方 runtime 可据此幂等去重；重试同一通知不得生成多个逻辑结果。 |
| A2A access 配置 | YAML 配置 | 必须承载公开 base URL、Agent Card 元数据、skills、capabilities、固定 callback receiver path 等 northbound 暴露配置。 |
| Agent 执行 SPI | Java SPI | 必须作为 Agent 执行接入点被 A2A bridge 调用；handler 产出的结果由 A2A 层转换为 Task / Message / SSE 表面。 |
| agent-bus forwarding delivery | 外部系统调用场景 | agent-bus 对 runtime 的转发投递必须使用标准 A2A 服务入口和相同 Task/SSE/error 语义。runtime 不为转发路径定义额外私有协议。 |

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 发现 Agent 能力 | runtime 已启动并存在可发布的 Agent Card | A2A client 请求 `/.well-known/agent-card.json` | client 获得 Agent 名称、描述、endpoint、capabilities 和 skills；相对 URL 被解析成可访问 URL。 |
| 普通 client 流式调用 Agent | client 已获得 `/a2a` endpoint | client 发送 `SendStreamingMessage` | runtime 返回 SSE stream；调用方按顺序观察 Task 接收、执行、artifact/progress 和最终 `COMPLETED` / `FAILED` / `CANCELED` / interrupted 状态。 |
| 普通 client 阻塞调用 Agent | 请求规模适合一次性响应 | client 发送 `SendMessage` | runtime 调用同一 handler 执行链，收集结果后返回单个 JSON-RPC response。 |
| `SendMessage` 同步创建 callback 配置 | 调用方 runtime 具备固定 callback receiver，且被调用方信任该 receiver | 调用方 runtime 发送 `SendMessage`，并在 `params.pushNotificationConfig` 中携带 callback 配置 | runtime 创建 Task、关联 callback 配置并执行 Agent；Task 进入结果性状态后向固定 callback receiver 投递结果。 |
| 既有 Task 关联 callback 配置 | Task 已创建，调用方仍可访问该 Task | 调用方 runtime 发送 `CreateTaskPushNotificationConfig` | runtime 只创建/关联该 Task 的 callback 配置，不启动新的 Agent 执行。 |
| runtime-to-runtime callback 异步完成 | 调用方 runtime host 固定 callback receiver，且双方存在信任配置 | 被调用方 runtime 的 Task 进入结果性状态 | 被调用方 runtime 向调用方固定 callback endpoint POST 完成结果、失败信息或结果引用；调用方先鉴权再幂等处理。 |
| 查询长任务 | client 已获得 task id | client 调用 `GetTask` | runtime 返回该 task 当前状态、artifact 和 terminal message 等可见信息。 |
| agent-bus forwarding 投递 | agent-bus 已解析 route 并获得本 runtime A2A endpoint | agent-bus forwarding delivery port 向 `/a2a` 发起标准 A2A 请求 | 本 runtime 按标准 Agent 服务入口处理请求；完成、失败、超时等语义对 agent-bus 仍表现为 A2A Task/SSE/error 表面。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 入口来源等价语义

- 普通 client、其他 agent-runtime、agent-bus forwarding 进入 `agent-runtime` 时必须共享同一个 Agent 服务入口事实：Agent Card 发现、`/a2a` JSON-RPC、Task 状态、SSE 事件和错误表面。
- runtime 可以在日志、metadata、trace 或网关层区分调用来源，但不得为不同来源定义互相漂移的执行语义。
- agent-bus forwarding 是标准入口的调用方之一，不是 `agent-runtime` 内部执行 SPI 的绕行入口。
- 其他 agent-runtime 调用本 runtime 时，本 runtime 只承担服务端职责；主动发现远端 runtime、安装远程工具和发起 outbound 调用由远程编排特性约束。

#### 5.1.2 Agent Card 发现语义

- Agent Card 必须是调用方进入 runtime 的能力目录，而不是内部配置转储。
- Agent Card 的 endpoint URL 必须能被外部调用方解析：配置了 public URL 时使用配置值；未配置时由请求地址推导。
- Agent Card 的 `skills` 是跨 Agent 工具发现的事实入口。
- Agent Card 的 `capabilities.pushNotifications` 只能声明受信任 runtime-to-runtime callback 完成通知能力，不表示普通 client 可以注册任意 URL。

#### 5.1.3 JSON-RPC 分发语义

- `/a2a` 必须先解析 JSON-RPC request，再根据具体 A2A wrapper 分发。
- `SendStreamingMessage` 必须进入 streaming 分支。
- `SendMessage`、`GetTask` 和 `CreateTaskPushNotificationConfig` 必须进入 blocking JSON 分支。
- `GetTaskPushNotificationConfig`、`ListTaskPushNotificationConfig`、`UpdateTaskPushNotificationConfig`、`DeleteTaskPushNotificationConfig` 当前版本显式不支持，必须返回 method-not-found 或等价 unsupported 表面。
- 未知 method 必须返回 JSON-RPC method-not-found 错误。
- 非法 JSON 必须返回 parse error；合法 JSON 但 shape 不匹配 A2A request 时必须返回 invalid request 或 invalid params。

#### 5.1.4 runtime-to-runtime callback 完成通知语义

- Callback push notification 是 runtime-to-runtime 的点对点异步完成通知能力，不经过 `agent-bus`，不要求 broker，也不作为普通 client 应用集成 callback 能力。
- 调用方需要实时过程观察时应选择 `SendStreamingMessage` / SSE；调用方希望释放长连接资源时可以选择 `SendMessage` + push notification callback。
- Callback 只在 Task 进入结果性状态后触发。`COMPLETED` 必须通知完成结果；`FAILED` 必须通知异常状态、错误码和失败原因；`CANCELED` / `REJECTED` 仅当 SDK/handler 明确产出时承载。
- Callback 不通知 submitted、working、progress、artifact update 等中间态，不承载 token-by-token、progress stream 或 SSE frame。
- 文本类完成结果应在 callback body 中按 `SendMessage` result 的 A2A Task/Message 结构一次性返回。文件类、多模态类、artifact 大正文或超过回调承载策略的结果必须沿用 SDK/A2A result 可表达的 artifact、file/data part、metadata 或 Task 查询引用，不为 callback 单独定义强制字段。
- Callback 投递失败不得回滚或改变 Task 终态；它只影响通知投递状态。实现可以重试同一 notification，但必须保持 notification id 稳定。
- Callback receiver 必须是固定 endpoint。接收方必须先完成授权校验，再处理 payload；未通过校验的请求不得进入 TaskStore 或业务处理。

#### 5.1.5 流式 S2C 语义

- `SendStreamingMessage` 的 SSE event 名必须为 `jsonrpc`。
- SSE data 必须是 JSON-RPC response envelope，result 承载 A2A SDK `StreamingEventKind`。
- runtime 必须通过 Task 状态和 artifact/progress 向调用方呈现执行过程。
- 对 `SendStreamingMessage`，当状态进入 final 或 interrupted 状态时，当前 message stream 必须关闭。

#### 5.1.6 阻塞 S2C 语义

- `SendMessage` 和 `SendStreamingMessage` 必须接受一致的 message 结构。
- `SendMessage` 可携带 `params.pushNotificationConfig`，用于在创建 Task 时同步创建 callback 配置。
- 阻塞等待不能无限挂起。超过 agent 执行等待窗口时，runtime 可以返回当前 Task 快照；超过消费等待窗口时，runtime 必须返回 JSON-RPC error。

#### 5.1.7 Task 状态语义

- runtime 必须把一次 Agent 调用投射到 A2A Task 生命周期。
- 正常执行至少应经过 submitted/working，并以 completed/failed/canceled 或 interrupted 类状态收束。
- handler 输出 `COMPLETED` 时必须形成 completed Task 表面。
- handler 输出 `FAILED` 或执行异常时必须形成 failed Task 表面，并携带可供客户端程序化判断的错误信息。
- handler 输出需要用户输入的中断时，Task 必须进入 input-required 类语义，而不是伪装成 completed。

#### 5.1.8 错误、状态与可观测结果

| 场景 | 事实要求 |
|---|---|
| JSON parse failure | 返回 JSON-RPC parse error。 |
| request shape invalid | 返回 JSON-RPC invalid request。 |
| method unsupported | 返回 JSON-RPC method-not-found 或等价 unsupported。 |
| handler/runtime exception | 形成 A2A failed Task 或 JSON-RPC internal error；可形成 Task 的路径应携带结构化错误 payload。 |
| no handler registered | 必须拒绝执行，错误语义应表达为不可执行的 no-handler。 |
| callback delivery failure | 不得改变 Task 终态；必须保留可观察的通知投递失败事实，并允许按稳定 notification id 重试。 |
| callback target untrusted | 必须拒绝注册或拒绝投递，不得向未受信任 URL 发送 Task 结果。 |
| callback receiver unauthorized | 必须在进入 TaskStore 或业务处理前拒绝。 |
| correlation observability | 执行窗口内日志、trajectory 和上下文派生字段必须能关联 context、task、agent。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| Push Notification Config R/U/D | 当前版本不承诺查询、列出、更新或删除 push notification config；只保留 Create。 |
| 多 Agent 路由 | 一个 runtime 实例不承诺按 agent id 路由多个 handler。多 Agent 部署应拆分为多个 runtime 实例或由上层路由。 |
| gRPC northbound | 当前版本不承诺 A2A gRPC 暴露面。 |
| 普通 client callback | 当前版本不承诺普通 client 或业务应用直接注册 callback 并接收 runtime 推送；callback 完成通知只面向受信任 runtime-to-runtime 场景。 |
| callback 中间态订阅 | 当前版本不承诺通过 callback 推送 submitted、working、progress、artifact update 等中间态。 |
| callback token 流 | 当前版本不承诺通过 callback 推送 token-by-token、SSE frame 或流式过程正文。 |
| callback HITL 继续执行 | 当前版本不承诺通过 callback 处理 `INPUT_REQUIRED` / `AUTH_REQUIRED` 的交互式继续执行；这类语义应由 HITL / continuation 相关特性定义。 |
| 非文本输入语义 | 当前版本以 text parts 作为 Agent 输入主路径。 |
| outbound 远程 Agent 编排 | 本特性只要求本 runtime 作为服务端发布自己的 Agent Card 并接受调用；远程 Agent 目录、缓存、工具安装、调用发起、结果回灌由远程编排特性承接。 |
| agent-bus 专用私有入口 | runtime 不承诺为 agent-bus 暴露绕过 A2A Task/SSE/error 表面的私有执行接口。 |
| 认证授权协议 | A2A auth 扩展、OAuth、签名校验等具体协议不在本特性事实要求中固定，只要求 callback 能力必须具备授权校验位置。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把本特性作为标准化 Agent 服务入口事实来源，不得把本特性的外部行为降级为实现细节。
- `A2aJsonRpcController`、SDK `RequestHandler` bridge、Agent Card controller、callback receiver 和相关 auto-configuration 必须共同满足第 2-5 节事实要求。
- 开发指南只能解释如何使用这些事实要求，不得引入与本特性冲突的新 method、endpoint、状态语义或 capability 承诺。
- 测试必须覆盖 blocking、streaming、async query、`SendMessage` 内联 push config、`CreateTaskPushNotificationConfig` 创建并关联 Task、固定 callback receiver、callback 文本结果、callback 大载荷引用、callback 失败重试、未受信任 target 拒绝和未授权 callback 拒绝。
- agent-bus 对 runtime 的 forwarding 集成验证必须以标准 A2A 服务入口为边界，不能要求 runtime 增加 agent-bus 专用执行口。
- Agent Card skills/capabilities 的设计和实现必须与远程编排特性保持一致：skills 是远程工具发现入口，capabilities 是能力声明，不是运行时自动证明。
- 任何对 Push Notification Config Read/Update/Delete、普通 client callback、callback 中间态订阅、callback token 流、push notification HITL 继续执行、gRPC、多 handler 路由、非文本输入或认证能力的新增承诺，都必须先回到本特性或新的 version-scope 特性文档更新事实要求，再进入 L2 和实现。

## 7. 关联文档

- `architecture/L2-Low-Level-Design/agent-runtime/FEAT-001-standardized-agent-service-entrypoint.md`
