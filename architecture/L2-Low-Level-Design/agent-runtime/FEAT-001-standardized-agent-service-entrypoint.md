---
level: L2-LLD
module: agent-runtime
feature_type: functional
feature_id: FEAT-001
status: active
updated: 2026-07-25
dependency:
  - ../../L1-High-Level-Design/agent-runtime/README.md
  - ../../L1-High-Level-Design/agent-runtime/development.md
  - ../../L1-High-Level-Design/agent-runtime/process.md
  - ../../../version-scope/FEAT-001-standardized-agent-service-entrypoint.md
---

# 标准化智能体服务入口设计文档

## 1. 设计目标与边界

### 1.1 特性范围

FEAT-001 定义 `agent-runtime` 作为被调用方时的标准 Agent 服务入口。当前 L2 只承接 inbound 服务面和该服务面进入内部执行链路后的运行模型，包含 Agent Card discovery、`/a2a` JSON-RPC 统一入口、`SendStreamingMessage`、`SendMessage`、`GetTask`、`SendMessage` 内联 push notification config 的异步 callback 路径、runtime-to-runtime callback 完成通知的服务端行为，以及 A2A SDK `RequestHandler` / `AgentExecutor` 到内部 `ServeOrchestrator` / `AgentHandler` 的桥接。

本特性不承接 outbound 远程 Agent 编排。当前实现中 `A2AAutoConfiguration` 同时装配 remote agent registry/client/discovery 和 `A2AEnabledServeOrchestrator`，这是实现上共用 A2A 自动装配类，不代表 FEAT-001 要把 `remote-agents` 作为本特性的配置主体。远程 Agent 发现、远程工具安装、`a2a_delegate` 发起、shadow task 编排和远程结果回灌属于远程编排特性，FEAT-001 只在内部运行模型中说明 inbound 请求触发这些能力时如何穿过同一入口。

agent-bus forwarding 在 FEAT-001 中只作为标准入口调用来源之一。runtime 不为 agent-bus 暴露绕过 A2A Task/SSE/error 语义的私有执行入口。

### 1.2 能力对齐矩阵

| 能力 | version-scope 要求 | 当前实现状态 | L2 设计响应 |
|------|--------------------|--------------|-------------|
| Agent Card discovery | MUST | 已实现 | 三个 well-known endpoint 返回同一 Agent Card 表面。 |
| `/a2a` JSON-RPC | MUST | 已实现主路径 | controller 统一分发 `SendMessage`、`SendStreamingMessage`、`GetTask`；push config 只作为 `SendMessage` 内联参数处理，独立 CRUD method 当前版本显式排除。 |
| 普通 client inbound | MUST | 已实现主路径 | 普通 client 与 runtime-to-runtime 调用共享同一入口。 |
| runtime-to-runtime inbound | MUST | 已实现首迭代 | 入站调用共用 `/a2a`；callback 完成通知通过固定 receiver 和 orchestrator 回灌恢复承载。 |
| agent-bus forwarding inbound | MUST | 边界已定义 | agent-bus 必须投递到标准 A2A 入口，FEAT-001 不定义 agent-bus 私有协议。 |
| 流式调用 | MUST | 已实现 | SSE event 固定为 `jsonrpc`，data 为 JSON-RPC envelope。 |
| 阻塞调用 | MUST | 已实现主路径 | 不携带 push config 时复用同一 executor，`_a2a_stream=false`；携带 push config 时进入异步接受路径，创建 Task 后尽快返回 Task 表面。 |
| 异步查询 | MUST | 已实现 | `GetTask` 通过 SDK `RequestHandler#onGetTask()` 查询 `TaskStore`。 |
| SendMessage 内联 Push Notification Config | MUST | 已实现首迭代 | `SendMessage` 内联携带 push config，并通过 SDK `PushNotificationConfigStore` 随本次 Task 创建同步关联；独立 Create/Read/Update/Delete method 显式排除。 |
| runtime-to-runtime callback | MUST | 已实现首迭代 | `PushNotificationSender` 已替换为 HTTP sender，支持 trusted host 校验、稳定 notification id、投递结果记录和固定 receiver 回灌；持久化重试不纳入首迭代强验收。 |
| Agent Card capabilities | MUST | 已实现首迭代 | `pushNotifications` 由配置、HTTP sender、trusted hosts、callback store/handler 等可用性共同决定。 |
| JSON-RPC 错误表面 | MUST | 部分实现 | parse error、method-not-found 已覆盖；invalid request/invalid params 需从 internal error 收敛为标准错误。 |
| HTTP + SSE | MUST | 已实现 | 当前 northbound 只承诺 HTTP JSON-RPC 和 SSE。 |
| gRPC northbound | OUT | 未实现 | 不纳入 FEAT-001 当前版本。 |

### 1.3 当前实现差距

当前实现已经形成 Agent Card、`/a2a`、blocking、streaming、`GetTask`、SDK TaskStore、QueueManager、基础 executor bridge 和 callback 首迭代主路径。当前实现约束是：

- `A2aJsonRpcController` 保留 `SendMessage` 的 `params.pushNotificationConfig`，并交由 SDK `MessageSendConfiguration` / `PushNotificationConfigStore` 随 Task 创建关联；`Create/Get/List/Update/DeleteTaskPushNotificationConfig` 当前版本显式排除。
- `PushNotificationSender` 已替换为 HTTP sender，按绑定的 push config 向调用方固定 callback receiver 发起 HTTP POST。
- callback target 首迭代按 trusted host 校验，投递使用稳定 notification id，并保留内存投递记录。
- Agent Card 的 `pushNotifications` 已与配置开关、HTTP sender、trusted hosts、callback store/handler 等可用性绑定；完整授权策略和持久化重试不参与首迭代 capability 强判定。
- JSON-RPC params shape/type 错误可能被兜底成 `-32603 Internal error`，需要收敛为 `-32600 Invalid Request` 或 `-32602 Invalid params`。
- A2A 入站 request 的 header/context 透传当前较弱：`A2AMessageContext.headers` 未从 servlet request 填充，`A2AProtocolAdapter` 只能在 headers 存在时映射 `x-user-id`、`x-space-id`、`x-tenant-id`；该项属于标准入口增强，不作为 callback 首迭代强验收。

## 2. 外部服务面设计

### 2.1 HTTP Endpoint 清单

| endpoint | 方法 | Controller | 服务入口名称 | 返回类型 | 说明 |
|----------|------|------------|--------------|----------|------|
| `/.well-known/agent-card.json` | GET | `AgentCardController#getStandardCard` | Standard Agent Card Discovery | `AgentCard` JSON | 标准 Agent Card 发现入口。 |
| `/.well-known/agent.json` | GET | `AgentCardController#getCompatCard` | Legacy Agent Card Discovery | `AgentCard` JSON | 兼容旧发现路径，返回与标准入口等价的 card。 |
| `/a2a/.well-known/agent-card.json` | GET | `AgentCardController#getPrefixedCard` | Prefixed Compat Agent Card Discovery | `AgentCard` JSON | 当前实现支持的兼容路径。 |
| `/a2a` | POST | `A2aJsonRpcController#handleJsonRpc` | A2A JSON-RPC Endpoint | JSON 或 SSE | 标准 A2A JSON-RPC 入口。 |
| `/a2a/` | POST | `A2aJsonRpcController#handleJsonRpc` | A2A JSON-RPC Endpoint No-Slash | JSON 或 SSE | trailing slash 兼容入口。 |
| `/a2a/push-notifications/callback` | POST | `A2aPushNotificationCallbackController` | A2A Push Notification Callback Receiver | JSON | 固定 runtime-to-runtime 完成回调接收入口；首迭代使用固定路径，暴露行为与 push notification 配置和 capability gate 保持一致。 |

`openjiuwen.service.a2a.agent-card-path` 不是标准发现路径开关。当前默认值是 `/a2a/.well-known/agent-card.json`，实现中的标准 endpoint 由 `A2AServicePaths` 固定映射，`AgentCardController` 不使用该配置决定标准发现路径。因此 L2 将它定义为兼容路径配置/遗留配置，不把它作为 FEAT-001 的标准发现契约。标准发现路径始终以 `A2AServicePaths.WELL_KNOWN_AGENT_CARD` 和 `A2AServicePaths.WELL_KNOWN_AGENT_JSON` 为准。

### 2.2 Agent Card Discovery 契约

**入口名称**：Standard Agent Card Discovery / Legacy Agent Card Discovery / Prefixed Compat Agent Card Discovery。

**入参**：

| 来源 | 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| HTTP request | scheme/host/port | string | public-url 为空时必需 | 用于推导 JSON-RPC URL。 |
| 配置 | `openjiuwen.service.a2a.public-url` | string | 否 | 配置后优先作为 Agent Card URL base。 |
| 配置 | `openjiuwen.service.a2a.json-rpc-path` | string | 是 | 拼接为 Agent Card `url` 和 `supportedInterfaces[].url`，默认 `/a2a`。 |
| 配置 | `openjiuwen.service.a2a.agent-description` | string | 是 | Agent 描述，默认 `OpenJiuwen Agent Runtime Service`。 |
| 配置 | `openjiuwen.service.a2a.skills[]` | list | 否 | 映射为 Agent Card skills。 |
| 配置 | capability 开关 | boolean | 是 | `streaming`、`push-notifications`、`extended-agent-card`。 |

**出参**：

| Agent Card 字段 | 来源 |
|-----------------|------|
| `name` | `AgentServiceIdentity#getAppName()`。 |
| `description` | `openjiuwen.service.a2a.agent-description`。 |
| `provider` | `provider-organization` / `provider-url`。 |
| `version` | `ServiceProperties#getVersion()`。 |
| `documentationUrl` | `documentation-url`。 |
| `capabilities.streaming` | 配置开关与 `SendStreamingMessage` 可用性共同决定；当前实现只按配置值。 |
| `capabilities.pushNotifications` | 由配置启用、`SendMessage` 内联配置、HTTP sender、trusted hosts、callback store/handler 等可用性共同决定。 |
| `capabilities.extendedAgentCard` | 配置开关与扩展 card 表面可用性共同决定；当前实现只按配置值。 |
| `defaultInputModes` / `defaultOutputModes` | A2A 配置默认模式。 |
| `skills` | `skills[]` 映射为 SDK `AgentSkill`。 |
| `supportedInterfaces[]` | 当前固定声明 `JSONRPC`，URL 为 base URL + `json-rpc-path`。 |
| `url` / `preferredTransport` | 当前 `url` 同 JSON-RPC URL，`preferredTransport=JSONRPC`。 |

**异常响应**：

| 场景 | HTTP 表面 | 说明 |
|------|-----------|------|
| 配置字段为空 | 200，字段按默认值或空值返回 | provider/documentation/icon 等可为空。 |
| public-url 缺失 | 200，从请求地址推导 | 当前实现使用 `scheme://serverName:serverPort`。 |
| skills 为空 | 200，`skills=[]` | 不声明远程工具能力。 |
| 内部构造异常 | 5xx | 由 Spring MVC 默认异常表面承载；后续可补统一 error body。 |

### 2.3 `/a2a` JSON-RPC Envelope 契约

**入口名称**：A2A JSON-RPC Endpoint。

**HTTP 入参**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| body | JSON object | 是 | JSON-RPC request。 |
| `Content-Type` | `application/json` | 建议 | 当前 controller 直接读取 raw string，未强校验。 |
| HTTP headers | map | 否 | 设计要求可进入 `ServerCallContext` 和 `ServeRequest`；当前实现只记录 `remote-addr`、`path`，未填充到 `A2AMessageContext.headers`。 |

**JSON-RPC 基础入参**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `jsonrpc` | string | 是 | 应为 `2.0`；当前实现未显式校验，需补 invalid request。 |
| `id` | string/number/null | 否 | response 尽量原样保留。 |
| `method` | string | 是 | JSON-RPC 分发键。当前实现支持 `SendMessage`、`SendStreamingMessage`、`GetTask`；独立 push notification config method 当前版本不纳入。 |
| `params` | object | 按 method | method 参数。 |

**method 分发表**：

| method | transport | 当前实现 | method 级契约位置 |
|--------|-----------|----------|--------------------|
| `SendStreamingMessage` | SSE | 已分发 | 见 2.4。 |
| `SendMessage` | JSON response | 已分发 | 见 2.5。 |
| `GetTask` | JSON response | 已分发 | 见 2.6。 |
| `CreateTaskPushNotificationConfig` | JSON error | 显式排除 | 当前版本显式排除。 |

push notification config 不新增 HTTP endpoint，也不形成独立外部 URL 面。当前 callback 首迭代只把它作为 `SendMessage` 的 `params.pushNotificationConfig` 内联参数处理，由同一个 JSON-RPC envelope、同一个 `ServerCallContext`、同一套 JSON-RPC error 表面承载。`CreateTaskPushNotificationConfig`、`GetTaskPushNotificationConfig`、`ListTaskPushNotificationConfig`、`UpdateTaskPushNotificationConfig`、`DeleteTaskPushNotificationConfig` 显式排除。

**通用异常响应**：

| 场景 | HTTP status | JSON-RPC code | message | 当前实现 |
|------|-------------|---------------|---------|----------|
| 非法 JSON | 400 | `-32700` | `Parse error` | 已实现。 |
| JSON 非 object、缺失 `method`、`jsonrpc` 非 `2.0` | 400 | `-32600` | `Invalid Request` | 待补，目前可能 method-not-found 或 internal error。 |
| 未知 method | 400 | `-32601` | `Method not found: <method>` | 已实现。 |
| params 缺失或类型错误 | 400 | `-32602` | `Invalid params` | 待补，目前可能 `-32603`。 |
| SDK 抛出 `A2AError` | 200 | SDK error code | SDK message | 已实现 `jsonRpcError`。 |
| handler/runtime 未能形成 Task 的异常 | 400 或 500 | `-32603` | `Internal error` | 当前兜底为 400 + internal error。 |

### 2.4 `SendStreamingMessage` 契约

**入口名称**：A2A SendStreamingMessage。

**入参**：

| JSON path | 类型 | 必填 | 映射目标 | 说明 |
|-----------|------|------|----------|------|
| `method` | string | 是 | controller 分发 | 固定 `SendStreamingMessage`。 |
| `params.message.role` | string | 否 | `Message.role` -> `ServeRequest.messages[].role` | 缺省为 `ROLE_USER`；当前实现用 `Message.Role.valueOf()`，非法值需转 invalid params。 |
| `params.message.parts[]` | array | 否 | `ServeRequest.messages[].content` | 当前只提取非空 `text` part，并按顺序拼接。 |
| `params.message.messageId` | string | 否 | SDK Message | 空串归一为 null。 |
| `params.message.contextId` | string | 否 | SDK contextId / `ServeRequest.conversationId` | 用于会话和 Task 关联。 |
| `params.message.taskId` | string | 否 | SDK taskId | 用于 resume 或关联既有 task。 |
| `params.metadata` | object | 否 | `A2AMessageContext.metadata` / `ServeRequest.metadata` | 原样保留给 runtime 和 adapter。 |
| HTTP headers | map | 否 | `ServeRequest.userId/spaceId/tenantId` | 设计要求支持 `x-user-id`、`x-space-id`、`x-tenant-id`；当前实现未从 request 填充。 |

**内部调用**：

1. controller 设置 `ServerCallContext.state["_a2a_stream"]=true`。
2. `parseParams()` 构造 `MessageSendParams`。
3. `RequestHandler#onMessageSendStream(params, ctx)` 建立 SDK Task、event queue 和 executor 调用。
4. `A2AAgentExecutor` 将 A2A message 转成 `ServeRequest`，并调用 `ServeOrchestrator.streamQuery()`。
5. SDK publisher 输出 `StreamingEventKind`。
6. controller 将每个 event 包装为 SSE `event: jsonrpc`。

**出参**：

| HTTP/SSE 字段 | 值 |
|---------------|----|
| HTTP status | `200 OK`。 |
| Content-Type | `text/event-stream`。 |
| SSE event name | `jsonrpc`。 |
| SSE data | `{"jsonrpc":"2.0","id":<request id>,"result":<StreamingEventKind JSON>}`。 |

**终止语义**：

- `completed`、`failed`、`canceled` 等 SDK 可表达终态事件发送后关闭 SSE。
- `requiresInput` / `INPUT_REQUIRED` 发送后关闭当前 SSE，但 Task 保持可 resume 的 interrupted 状态。
- 当前实现没有独立 `auth-required` 来源；只有 SDK/handler 明确产出对应状态时才承载。

**异常响应**：

| 场景 | 表面 |
|------|------|
| params 缺失、`message` 缺失、`parts` 非数组、role 非法 | 应返回 `-32602 Invalid params`；当前待补。 |
| executor 中 `IllegalStateException` / `NullPointerException` | executor 调用 `emitter.fail()`，Task 进入 failed。 |
| SSE serialize/send 失败 | 取消 subscription，`SseEmitter.completeWithError()`；Task 状态由执行侧决定。 |
| client 断开 | subscription 或 emitter 结束；如进入 executor cancel 路径，则 `orchestrator.cancelActive(contextId)` 和 `emitter.cancel()`。 |

### 2.5 `SendMessage` 契约

**入口名称**：A2A SendMessage。

**入参**：与 `SendStreamingMessage` 使用同一 `params.message` 和 `params.metadata` 结构；当调用方希望任务完成后通过固定 callback receiver 接收结果时，可在同一次 `SendMessage` 中携带 push notification 配置。

| JSON path | 类型 | 必填 | 说明 |
|-----------|------|------|------|
| `params.message` | object | 是 | 与 `SendStreamingMessage` 一致，承载本次 Agent 要执行的消息。 |
| `params.metadata` | object | 否 | 与 `SendStreamingMessage` 一致，承载 trace/correlation/runtime identity 等上下文。 |
| `params.pushNotificationConfig.id` 或 SDK 等价字段 | string | 否 | 本次 Task 的 push config id；缺省时可由 SDK/runtime 生成。 |
| `params.pushNotificationConfig.callbackUrl` | string | 条件必填 | 调用方 runtime 的固定 callback receiver，例如 `<caller-public-url>/a2a/push-notifications/callback`。如果可由调用方 runtime 身份或 registry 解析，则可省略。 |
| `params.pushNotificationConfig.authentication` | object | 否 | 被调用方投递 callback 时使用的认证材料；首迭代支持 token 或 SDK authentication 字段，完整签名 key id / mTLS 策略不作为强制输入。 |
| `params.pushNotificationConfig.headers` | object | 否 | 投递 callback 时附加的受控 headers；首迭代不承诺完整透传所有自定义 headers。 |

**内部调用**：

1. controller 设置 `ServerCallContext.state["_a2a_stream"]=false`。
2. 解析 `params.message` 和 `params.metadata`。
3. 如果 `params.pushNotificationConfig` 存在，校验 callbackUrl 合法性和 trusted host，并把配置关联到本次 Task；调用方身份和授权策略复用现有访问控制能力。
4. 如果存在 push config，runtime 进入异步接受路径：创建 Task、登记 callback 配置、启动 Agent 后台执行，并尽快返回已创建或已接受的 Task 表面，不阻塞等待 Agent 终态。
5. 如果不存在 push config，`RequestHandler#onMessageSend(params, ctx)` 调用 executor，保持普通阻塞聚合路径。
6. 普通阻塞路径中，`A2AAgentExecutor` 构造 `ServeRequest(stream=false)`，`ServeOrchestrator.query()` 执行非流式路径。
7. 如果 `QueryResponse.result.content` 存在，executor 写入 `TextPart` artifact 后 complete。
8. 如果结果包含 `_interrupt`，executor 调用 `emitter.requiresInput()`，并执行 SDK event queue drain 补偿。

**出参**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `jsonrpc` | string | 固定 `2.0`。 |
| `id` | same as request | 尽量保留 request id。 |
| `result` | A2A `Task` 或 `Message` / `EventKind` 解包结果 | 当前 `jsonRpcResponse()` 会解包 `task/message/statusUpdate/artifactUpdate` 单字段 wrapper。 |

**异常响应**：

| 场景 | 表面 |
|------|------|
| 请求解析/params 绑定失败 | 应返回 `-32600` 或 `-32602`；当前待补。 |
| 内联 push config 的 callbackUrl 不合法或 host 不可信 | 应返回 trust-policy error 或 `-32602`；不得保存配置、不得创建异步执行、不得启动投递。 |
| SDK 抛 `A2AError` | JSON-RPC error，HTTP 200。 |
| handler 运行异常且已建立 Task | Task failed。 |
| handler 运行异常且未建立 Task | JSON-RPC `-32603 Internal error`。 |

### 2.6 `GetTask` 契约

**入口名称**：A2A GetTask。

**入参**：

| JSON path | 类型 | 必填 | 说明 |
|-----------|------|------|------|
| `method` | string | 是 | 固定 `GetTask`。 |
| `params.id` 或 SDK `TaskQueryParams` 等价 task id 字段 | string | 是 | 查询目标 Task。 |
| `params.historyLength` 等 SDK 支持字段 | number | 否 | 由 SDK `TaskQueryParams` 承载。 |

**内部调用**：

1. controller 将 `params` 反序列化为 SDK `TaskQueryParams`。
2. 调用 `RequestHandler#onGetTask(tqp, ctx)`。
3. SDK 从 `TaskStore` 读取 Task 快照。

**出参**：JSON-RPC success response，`result` 为 Task 快照，包含 task id、context id、status、artifacts、history、metadata 等 SDK 可表达字段。

**异常响应**：

| 场景 | 表面 |
|------|------|
| params 缺失或 task id 缺失 | 应返回 `-32602 Invalid params`；当前待补。 |
| task 不存在 | 由 SDK `A2AError` 表达，controller 包装为 JSON-RPC error。 |
| TaskStore 访问异常 | JSON-RPC internal error 或 SDK error；不能伪造成 completed。 |

### 2.7 runtime-to-runtime callback receiver 契约

**入口名称**：A2A Push Notification Callback Receiver。

**固定 endpoint**：`POST /a2a/push-notifications/callback`。

该入口是本 runtime 作为调用方时接收远端 runtime 完成通知的固定 HTTP endpoint。首迭代使用固定路径，不为每个 Task 生成临时 callback URL；当 push notification 行为未启用或 capability gate 不满足时，接收入口应返回未启用或未实现的等价错误。

收到 callback 后，runtime 通过现有授权框架和 callback 配置完成访问控制，再处理 payload。未通过校验的请求在进入 TaskStore、业务 handler 或结果分发前被拦截。

**HTTP 入参**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `Content-Type` | `application/json` | 是 | callback body 为 JSON。 |
| `Authorization` / 签名 header / mTLS identity | string | 否 | 具体机制由部署策略决定；首迭代复用现有授权框架和 token/authentication 字段，不内建签名或 mTLS 策略。 |
| `X-A2A-Notification-Id` 或 body `notificationId` | string | 是 | 幂等键；同一 Task + push config + terminal result 重试必须稳定。 |
| body | object | 是 | 完成通知 payload，结构必须复用 `SendMessage` 的 JSON-RPC response/result 表面。 |

**body 入参**：

callback body 必须复用 `SendMessage` 的 JSON-RPC response/result 表面，不定义独立的业务 payload 模型。通知幂等、鉴权、发送时间等投递元数据优先通过 HTTP header、签名材料或接收侧投递记录承载；除 `notificationId` 作为兼容兜底外，不要求在 body 顶层新增字段。

| JSON path | 类型 | 必填 | 说明 |
|-----------|------|------|------|
| `jsonrpc` | string | 建议 | 与 `SendMessage` response 一致，建议为 `2.0`。如果 SDK/远端只能投递裸 result，接收方可兼容，但内部应归一成 JSON-RPC response。 |
| `id` | string/number/null | 否 | 与远端原始 `SendMessage` request id 或 push 投递关联 id 一致；接收方不依赖它做幂等。 |
| `result` | A2A `Task` / `Message` / SDK 等价 result | 条件必填 | 与 `SendMessage` 成功出参一致。首迭代接收方至少兼容本 runtime sender 产生的 Task result 表面。 |
| `error` | JSON-RPC error object | 否 | 普通 handler 失败优先表现为 `result.status=failed` 的 Task；JSON-RPC error 形态保留为兼容输入，不作为首迭代强验收。 |
| `notificationId` | string | 否 | 仅作无法传递 `X-A2A-Notification-Id` 时的兼容兜底；同时存在 header 与 body 字段时必须一致。 |

**出参**：

| HTTP status | body | 说明 |
|-------------|------|------|
| `200 OK` | `{"status":"accepted","notificationId":"..."}` | 授权通过且首次接收成功，或幂等重复请求已确认。 |
| `202 Accepted` | `{"status":"accepted","notificationId":"..."}` | 接收成功但本地异步处理尚未完成。 |
| `400 Bad Request` | error body | body 不是可归一为 `SendMessage` response/result 的结构、notification id 缺失或 header/body 不一致。 |
| `401 Unauthorized` / `403 Forbidden` | error body | 授权失败、签名错误、mTLS 身份不可信或来源 runtime 不在 allowlist。 |
| `404 Not Found` / `501 Not Implemented` | error body | push notification callback 行为未启用或 endpoint 未暴露。 |
| `409 Conflict` | error body | notification id 与已记录 payload 冲突，疑似重放或篡改。 |
| `5xx` | error body | 本地持久化、shadow task 更新或结果分发失败。 |

**触发条件**：

| Task 状态 | 是否触发 | payload |
|-----------|----------|---------|
| `completed` | 是 | `SendMessage` result 中可表达的文本结果、Task artifact 或结果引用。 |
| `failed` | 是 | 优先以 `SendMessage` result 中的 failed Task status、status message 或 SDK error 字段表达。 |
| `canceled` | 仅当 SDK/handler 明确产出 | 取消状态和原因；当前版本无外部任务取消入口。 |
| `rejected` | 仅当 SDK/handler 明确产出 | 拒绝状态和原因。当前实现无独立映射来源。 |
| `submitted` / `working` / artifact update / progress | 否 | 不通过 callback 推中间态。 |
| `input-required` / `auth-required` | 扩展 | 首迭代核心验收以结果性状态为主；实现可接收并恢复为本地等待态，但不作为 HITL continuation 完整契约。 |

callback 投递失败不得改变远端 Task 终态。接收方按 `notificationId` 幂等处理：同一 notification 重试返回同一接收结果；同一 id 对应不同 payload 时拒绝。投递记录和幂等记录在首迭代采用运行时内存承载。

## 3. 核心运行模型

本章按特性文档第 4 章的场景/用户旅程重排。每个小节只回答一个问题：外部黑盒场景进入 runtime 后，内部调用链按什么顺序经过哪些组件、在哪里分支、在哪里落状态。类级行为和函数签名不在本章展开，统一放到第 4 章。

### 3.1 发现 Agent 能力

对应特性文档第 4 章“发现 Agent 能力”。该旅程不进入 `/a2a` JSON-RPC，也不创建 Task。

```text
A2A client
  -> GET /.well-known/agent-card.json
  -> AgentCardController.getStandardCard(request)
  -> AgentCardController.buildCard(request)
       read A2AProperties
       read AgentServiceIdentity
       read ServiceProperties
       resolve baseUrl
       build jsonRpcUrl = baseUrl + configured json-rpc-path
       map configured skills to AgentSkill
       map configured capabilities to AgentCapabilities
  -> return AgentCard
```

内部边界：Agent Card 描述的是本 runtime 作为服务端的 inbound 能力，不读取 `remote-agents`，不访问 remote agent registry/client/discovery。`agent-card-path` 只是兼容/遗留配置语义，标准 discovery path 由 `A2AServicePaths` 决定。

### 3.2 普通 client 流式调用 Agent

对应特性文档第 4 章“普通 client 流式调用 Agent”。该旅程从 `/a2a` 进入，method 为 `SendStreamingMessage`，返回 SSE。

```text
Client
  -> POST /a2a, method=SendStreamingMessage
  -> A2aJsonRpcController.handleJsonRpc(rawBody, servletRequest)
       parse JSON-RPC envelope
       build ServerCallContext
       ctx.state["_a2a_stream"] = true
       parse MessageSendParams from params.message / params.metadata
  -> SDK RequestHandler.onMessageSendStream(params, ctx)
       SDK creates Task / event queue / publisher
       SDK invokes A2AAgentExecutor.execute(ctx, emitter)
  -> A2AAgentExecutor.execute(ctx, emitter)
       A2AMessageContext.from(ctx)
       A2AProtocolAdapter.toServeRequest(messageContext)
       req.stream = true
       if new task: emitter.submit()
       emitter.startWork()
       ServeOrchestrator.streamQuery(req, observer)
  -> A2AEnabledServeOrchestrator.streamQuery(req, observer)
       ActiveStreamRegistry.register(conversationId)
       AgentHandler.streamQuery(current, wrapped observer)
       normal QueryChunk -> observer.onNext(chunk)
       interrupt QueryChunk -> observer.onNext(interrupt), observer.onComplete()
       finally ActiveStreamRegistry.unregister(conversationId)
  -> A2AAgentExecutor observer
       normal chunk -> ChunkMapper.toParts(chunk) -> emitter.addArtifact(parts)
       interrupt chunk -> emitter.requiresInput(statusMessage) -> SDK queue drain compensation -> close queue
       complete -> emitter.complete() with drain compensation
       error -> emitter.fail()
  -> SDK publisher emits StreamingEventKind
  -> A2aJsonRpcController.streamToSse(publisher, id)
       event: jsonrpc
       data: {"jsonrpc":"2.0","id":id,"result":event}
```

关键内部承诺：`INPUT_REQUIRED` 必须先于 stream close 被客户端观察到。当前实现通过反射访问 SDK `AgentEmitter.eventQueue` 并等待 in-flight drain，这是 SDK 事件队列一致性补偿，不是理想模型里的自然桥接能力。后续应替换为 SDK 正式 drain/close API。如果该流式执行链触发下游 A2A 调用，下游必须优先使用 `SendStreamingMessage`，把远端过程事件桥回当前 SSE；不得在可流式时静默降级为 callback 或同步阻塞。

### 3.3 普通 client 阻塞调用 Agent

对应特性文档第 4 章“普通 client 阻塞调用 Agent”。该旅程从 `/a2a` 进入，method 为 `SendMessage`，返回单个 JSON-RPC response。

```text
Client
  -> POST /a2a, method=SendMessage
  -> A2aJsonRpcController.handleJsonRpc(rawBody, servletRequest)
       parse JSON-RPC envelope
       build ServerCallContext
       ctx.state["_a2a_stream"] = false
       parse MessageSendParams from params.message / params.metadata
  -> SDK RequestHandler.onMessageSend(params, ctx)
       SDK creates or resumes Task
       SDK invokes A2AAgentExecutor.execute(ctx, emitter)
  -> A2AAgentExecutor.execute(ctx, emitter)
       A2AMessageContext.from(ctx)
       A2AProtocolAdapter.toServeRequest(messageContext)
       req.stream = false
       if new task: emitter.submit()
       emitter.startWork()
       ServeOrchestrator.query(req)
  -> A2AEnabledServeOrchestrator.query(req)
       syncResumePending(conversationId)
       AgentHandler.query(current)
       no interrupt -> QueryResponse
       local interrupt -> input-required response
       a2a_delegate interrupt -> remote orchestration path (shared implementation, non FEAT-001 outbound contract)
       if downstream A2A is required and target supports callback:
           A2ARemoteAgentClient.callWithCallback(...)
           TaskStore records local delegation binding + remote taskId when known
           A2aJsonRpcController keeps bounded wait handle for this SendMessage request
           downstream callback -> callback receiver -> orchestrator resumes local agent
           local agent completes -> wake bounded wait handle and return JSON-RPC result
       if downstream callback does not arrive before wait window:
           return queryable Task surface to original client
  -> A2AAgentExecutor.executeQuery(...)
       result.content -> emitter.addArtifact(TextPart)
       interrupt -> emitter.requiresInput(statusMessage)
       normal -> emitter.complete()
       error -> emitter.fail()
  -> SDK RequestHandler returns EventKind
  -> A2aJsonRpcController wraps SendMessageResponse(id, result)
  -> HTTP 200 JSON-RPC response or JSON-RPC error
```

关键内部承诺：阻塞和流式共享同一 Message -> `ServeRequest` 适配，不允许出现两套 handler 输入语义。普通 client 发起的 `SendMessage` 不暴露服务端 callback client 的能力；如果内部执行链触发下游 A2A 调用，远程编排分支按当前 metadata、目标能力和调用模式选择 callback、streaming 或 blocking。callback 回灌后先由 `A2AEnabledServeOrchestrator` 按绑定关系恢复本地 agent 执行，本地 agent 产出结果后再唤醒仍存在的 bounded wait 句柄。窗口耗尽时，controller 返回可查询 Task 表面，后续由 `GetTask` 观察结果。`rejected`、`auth-required` 等状态只有 SDK/handler 明确产出时才承载，当前实现不凭空推导。

### 3.4 `SendMessage` 同步创建 callback 配置

对应特性文档第 4 章“`SendMessage` 同步创建 callback 配置”。这是 3.3 普通阻塞调用旅程的异步扩展分支：消息仍由 `SendMessage` 提交，callback config 随 Task 创建一起关联；一旦配置合法，runtime 应创建 Task 后尽快返回 Task 表面，不等待 Agent 终态。该语义只适用于 runtime-to-runtime 调用，不能被普通 client 用来自报任意 callback URL。

```text
Caller runtime
  -> POST /a2a, method=SendMessage
       params.message = Agent execution message
       params.pushNotificationConfig = callback config
  -> A2aJsonRpcController.handleJsonRpc(rawBody, servletRequest)
       parse JSON-RPC envelope
       build ServerCallContext with request path / remote address metadata
       ctx.state["_a2a_stream"] = false
       parse MessageSendParams from params.message / params.metadata
       retain pushNotificationConfig instead of dropping it during params parsing
       reject independent push config CRUD methods if present
  -> A2aJsonRpcController / RuntimeAccessProperties validate inline callback config
       validate callbackUrl syntax and trusted host
       reject untrusted URL before Task or push config is persisted
  -> SDK RequestHandler + A2AAgentExecutor async accepted branch
       create or reserve A2A Task through SDK TaskStore / QueueManager
       persist PushNotificationConfig for Task via PushNotificationConfigStore
       bind taskId + contextId + callback config for later delivery
       publish submitted/working Task events into SDK event bus / queue
       schedule the same A2AAgentExecutor / ServeOrchestrator execution in background
       return accepted Task surface after Task is queryable and execution is scheduled
  -> A2AAgentExecutor / ServeOrchestrator background execution
       A2AMessageContext.from(ctx)
       A2AProtocolAdapter.toServeRequest(messageContext)
       req.stream = false
       emitter.submit()
       emitter.startWork()
       ServeOrchestrator.query(req) executes on background worker
       result.content -> emitter.addArtifact(TextPart)
       interrupt -> emitter.requiresInput(statusMessage)
       normal -> emitter.complete()
       error -> emitter.fail()
  -> A2aJsonRpcController returns SendMessage JSON-RPC result immediately after Task is accepted
       result = created/accepted Task surface, not final Agent result
  -> Task terminal event is observed by MainEventBusProcessor
       PushNotificationSender loads bound push config
       PushNotificationSender implementation validates delivery target before POST
       PushNotificationSender builds callback body from SendMessage response/result surface
       delivery attempt is recorded by the HTTP sender runtime record
       HTTP POST caller fixed callback receiver
       success -> mark delivered
       failure -> keep Task terminal state and record failure with the same notificationId
```

关键内部承诺：相比 3.3，`SendMessage + pushNotificationConfig` 不是更短链路，而是把“等待最终结果”拆成“同步接收并创建 Task + 后台执行 + 终态事件投递”。它仍必须经过 TaskStore、QueueManager、MainEventBusProcessor、`A2AAgentExecutor`、`ServeOrchestrator` 和 `AgentHandler`，只是 HTTP response 不等待这些后半段完成。该路径必须保证 Task 已可查询、push config 已绑定、后台执行已被可靠调度后才返回 accepted Task 表面。

如果该执行链继续触发下游 A2A 调用，首迭代由远程调用 metadata 决定是否携带本 runtime callback config；下游 callback 回到本 runtime 后，再推进当前 Task 或继续向上游投递。

当前实现约束：`params.pushNotificationConfig` 已保留并交由 SDK configuration/store 绑定；async accepted 语义依赖 A2A SDK `returnImmediately` 和后台执行链；trusted host 校验应在保存配置和创建异步执行前完成；投递失败只记录运行时事实，不承诺持久化重试。

### 3.5 runtime-to-runtime callback 回灌恢复

对应特性文档第 4 章“runtime-to-runtime callback 回灌恢复”。该旅程只描述本 runtime 作为调用方时提供的固定 callback receiver 如何接收远端完成通知、恢复本地上下文并推进本地执行链。远端 Task 终态后的 HTTP 投递行为属于第 3.4 `SendMessage` 同步创建 callback 配置旅程的后半段，不在本节重复展开。

```text
POST /a2a/push-notifications/callback
  -> A2aPushNotificationCallbackController.receiveCallback(...)
       endpoint behavior follows push notification enabled state and capability gate
       authenticate through existing authorization framework where configured
       parse raw body and normalize to supported SendMessage response/result surface
       validate notificationId header or compatible body field
       return early on malformed / unauthorized request before touching TaskStore
  -> A2AEnabledServeOrchestrator binding lookup
       Agent produces remote delegation; orchestrator proxies the delegation out
       therefore orchestrator owns local wait point, parent task/context and recovery scheduling
       A2ARemoteAgentClient records remote taskId returned by downstream SendMessage response
       TaskStore stores binding facts available in the current shadow task model: local parent taskId/contextId, remote taskId and tool call / remote invocation metadata
       duplicate same payload -> accepted
       duplicate different payload -> conflict
       reject if no pending binding is found after early-callback handling
  -> existing task and execution continuation path
       attach normalized result to local Task metadata/history or remote invocation record
       publish callback-arrived fact through existing MainEventBus / QueueManager path where possible
       resume local agent through A2AEnabledServeOrchestrator continuation path
       local agent continues from recovered context and produces next QueryResponse/Task event
       if original SendMessage wait handle is still held by A2aJsonRpcController, wake it with the local agent result
       if local task reaches terminal state, update TaskStore through SDK Task event path
  -> A2aPushNotificationCallbackController returns 200/202 accepted or explicit error
```

关键内部承诺：callback receiver 只是固定 HTTP 回灌入口，不直接承担全部恢复逻辑，也不绕过任务中心化的 TaskStore、事件队列和智能体执行层。远端委派由 agent 产生、由 `A2AEnabledServeOrchestrator` 代理发起，因此本地等待点、绑定查找和恢复调度由 orchestrator 承载；`A2ARemoteAgentClient` 负责执行下游 A2A 调用并把远端 runtime 响应中的 taskId 交回绑定关系；`TaskStore` 的 shadow task 记录 local parent task/context、remote taskId 和 tool call 关联。迟到、重复或冲突 callback 不得直接改写 Task 终态，必须先通过 notification id 和本地绑定记录校验。

当前实现约束：固定 callback receiver、稳定 notification id、远端 taskId 回填、shadow task 绑定、幂等冲突检测和 orchestrator 回灌恢复已形成首迭代闭环；binding not found 的响应语义需要与 early callback 缓存区分；callback body 首迭代以 Task result 表面为主。
### 3.6 查询长任务

对应特性文档第 4 章“查询长任务”。该旅程只读 TaskStore，不访问 `AgentHandler`，不重新触发 Agent 执行。

```text
Client
  -> POST /a2a, method=GetTask
  -> A2aJsonRpcController.handleJsonRpc(rawBody, servletRequest)
       parse JSON-RPC envelope
       parse TaskQueryParams
  -> SDK RequestHandler.onGetTask(taskQueryParams, ctx)
       TaskStore.get/list via SDK
  -> Task snapshot
  -> A2aJsonRpcController.jsonRpcResponse(id, task)
```

TaskStore 当前默认为 SDK `InMemoryTaskStore`；配置 Redis checkpointer 时，`A2AAutoConfiguration` 使用自研 `WriteThrottlingTaskStore(new RedisTaskStore(jedisPool))`，降低流式场景频繁 Task 持久化对 SSE 的拖慢。

## 4. 代码结构与类级设计

本章按功能拆二级标题，用类声明和函数签名表达第 3 章各场景背后的实现承载点。每个代码块后说明类的归属：`自研 app` 表示本仓库 app 模块实现，`自研 spec` 表示本仓库 spec 模块定义，`A2A SDK` 表示 `org.a2aproject.sdk` 依赖，`远程编排共用` 表示当前实现共用但不属于 FEAT-001 inbound 服务入口契约主体。

### 4.1 Discovery 与路径常量

```java
// 自研 spec: service/agent-service-spec
public final class A2AServicePaths {
    public static final String WELL_KNOWN_AGENT_CARD;
    public static final String WELL_KNOWN_AGENT_JSON;
    public static final String A2A_JSONRPC;
    public static final String A2A_JSONRPC_NO_SLASH;
    public static final String A2A_WELL_KNOWN_CARD;
}
```

承载标准 Agent Card discovery、兼容 discovery、`/a2a` JSON-RPC endpoint 常量。标准 discovery path 由这里决定，不由 `agent-card-path` 配置决定。

```java
// 自研 app: service/agent-service-app
@RestController
public class AgentCardController {
    public AgentCard getStandardCard(HttpServletRequest request);
    public AgentCard getCompatCard(HttpServletRequest request);
    public AgentCard getPrefixedCard(HttpServletRequest request);

    private AgentCard buildCard(HttpServletRequest request);
}
```

承载第 3.1 “发现 Agent 能力”：读取 `A2AProperties`、`AgentServiceIdentity`、`ServiceProperties`，解析 baseUrl，组装 Agent Card、skills、capabilities 和 JSON-RPC endpoint。它只描述本 runtime inbound 能力，不访问 remote agent registry。

```java
// 自研 app: service/agent-service-app
@ConfigurationProperties("openjiuwen.service.a2a")
public class A2AProperties {
    private String publicUrl;
    private String agentDescription;
    private String documentationUrl;
    private String iconUrl;
    private boolean isStreaming;
    private boolean isPushNotifications;
    private boolean isExtendedAgentCard;
    private List<String> defaultInputModes;
    private List<String> defaultOutputModes;
    private List<SkillProperties> skills;
    private List<RemoteAgentProperties> remoteAgents;
    private String jsonRpcPath;
    private String agentCardPath;
    private int taskCompletionTimeoutSeconds;

    public static class SkillProperties { ... }
    public static class RemoteAgentProperties { ... }
}
```

承载 Agent Card 和 A2A auto-configuration 输入。`remoteAgents` 是远程编排共用配置，不是 FEAT-001 配置主体；`agentCardPath` 是兼容/遗留配置语义。

### 4.2 `/a2a` JSON-RPC controller

```java
// 自研 app: service/agent-service-app
@RestController
public class A2aJsonRpcController {
    public A2aJsonRpcController(RequestHandler requestHandler);

    public ResponseEntity<?> handleJsonRpc(String rawBody, HttpServletRequest servletRequest);

    private ResponseEntity<SseEmitter> streamToSse(
        Flow.Publisher<StreamingEventKind> publisher,
        Object requestId);

    private ResponseEntity<?> handleGetTask(
        String rawBody,
        Object id,
        ServerCallContext ctx) throws JsonProcessingException;

    private MessageSendParams parseParams(String rawBody);
    private static List<Part<?>> parseParts(JsonObject messageJson);
    private static void extractTextPart(JsonObject partJson, List<Part<?>> parts);
    private static Message buildMessage(JsonObject messageJson, List<Part<?>> parts);

    private ServerCallContext buildCallContext(HttpServletRequest request);

    private static ResponseEntity<String> jsonRpcResponse(Object id, Object result);
    private static ResponseEntity<String> jsonRpcError(Object id, A2AError error);
    private ResponseEntity<String> badRequest(A2AError error, Object id);
}
```

承载第 3.2、3.3、3.4、3.5、3.6 的 HTTP 入口分发。当前已分发 `SendMessage`、`SendStreamingMessage`、`GetTask`；inline `pushNotificationConfig` 已保留并进入 SDK configuration/store；独立 push config method 返回 unsupported/method-not-found 表面。controller 不承接 callback 回灌恢复入口，callback receiver 由第 4.5 的独立 controller 承载。invalid params 精确映射和 header/caller identity/trace 透传仍属于标准入口实现约束。

```java
// 自研 app: JSON-RPC controller callback-related helper shape
public class A2aJsonRpcController {
    private ResponseEntity<String> unsupportedPushNotificationConfigMethod(
        String method,
        Object id);

    private MessageSendParams parseMessageSendParams(JsonObject root);
    private Optional<PushNotificationConfig> parseInlinePushNotificationConfig(JsonObject params);
    private ResponseEntity<String> handleAsyncAcceptedSendMessage(
        MessageSendParams params,
        PushNotificationConfig config,
        ServerCallContext ctx,
        Object id);

    private ResponseEntity<String> waitForBlockingSendMessageResult(
        Object localInvocationHandle,
        Object id);
}
```

这些是 controller 级职责边界。它们只承接 `SendMessage` 内联 push config、独立 push config method 的 unsupported 表面，以及普通 `SendMessage` 在内部下游 callback 场景中的 bounded wait 句柄管理，不引入 `Create/Get/List/Update/DeleteTaskPushNotificationConfig` 主路径。callback 回灌恢复入口不放在本 controller 中。

### 4.3 A2A SDK 桥接与执行适配

```java
// 自研 app, implements A2A SDK AgentExecutor
public class A2AAgentExecutor implements AgentExecutor {
    public A2AAgentExecutor(ServeOrchestrator orchestrator, A2AProtocolAdapter adapter);

    public void execute(RequestContext ctx, AgentEmitter emitter);
    public void cancel(RequestContext ctx, AgentEmitter emitter);

    private void executeStreaming(
        A2AMessageContext msgCtx,
        RequestContext ctx,
        ServeRequest req,
        AgentEmitter emitter);

    private void executeQuery(
        A2AMessageContext msgCtx,
        RequestContext ctx,
        ServeRequest req,
        AgentEmitter emitter);

    private static Optional<Message> toStatusMessage(QueryChunk chunk);
    private static Optional<Message> toStatusMessageFromMap(Map<?, ?> interruptData);

    private static void closeEventQueue(AgentEmitter emitter, String taskId);
    private static void completeAndDrain(AgentEmitter emitter, String taskId);
    private static Optional<EventQueue> emitterEventQueue(AgentEmitter emitter);
    private static void awaitInFlightDrained(EventQueue childQueue, String taskId);
}
```

承载 SDK Task 执行入口到内部 `ServeOrchestrator` 的桥接。`executeStreaming` 对应第 3.2，`executeQuery` 对应第 3.3；`closeEventQueue` / `completeAndDrain` 是当前 SDK queue drain 一致性补偿，通过反射访问 SDK `AgentEmitter.eventQueue`，后续应替换成 SDK 正式 API。

```java
// 自研 app
public class A2AMessageContext {
    public static A2AMessageContext from(RequestContext ctx);
}

public class A2AProtocolAdapter {
    public ServeRequest toServeRequest(A2AMessageContext ctx);
    private String extractText(List<Part<?>> parts);
}

public class ChunkMapper {
    public List<Part<?>> toParts(QueryChunk chunk);
}
```

`A2AMessageContext` 从 SDK `RequestContext` 提取 message、contextId、taskId、metadata、existingTask。`A2AProtocolAdapter` 将 A2A Message 归一成内部 `ServeRequest`，保持阻塞与流式输入一致。`ChunkMapper` 将内部 `QueryChunk` 转为 A2A `Part<?>`，当前 `String` 转 `TextPart`，非字符串转 JSON 文本 `TextPart`。

```java
// A2A SDK: org.a2aproject.sdk:a2a-java-sdk-server-common:1.0.0.Final
public interface RequestHandler {
    EventKind onMessageSend(MessageSendParams params, ServerCallContext ctx);
    Flow.Publisher<StreamingEventKind> onMessageSendStream(MessageSendParams params, ServerCallContext ctx);
    Task onGetTask(TaskQueryParams params, ServerCallContext ctx);
}

public final class DefaultRequestHandler {
    public static RequestHandler create(...);
}

public interface AgentExecutor {
    void execute(RequestContext ctx, AgentEmitter emitter);
    void cancel(RequestContext ctx, AgentEmitter emitter);
}

public interface AgentEmitter {
    void submit(...);
    void startWork(...);
    void addArtifact(...);
    void requiresInput(...);
    void complete(...);
    void fail(...);
    void cancel(...);
}
```

以上为 A2A SDK 依赖类，不在本仓库定义。文档只承诺本特性对这些接口的使用位置，不承诺 SDK 内部实现。`AgentEmitter` 的精确重载以 SDK 为准，当前代码通过其公开方法写 Task 状态，并反射读取内部 queue。

### 4.4 内部执行 SPI 与 orchestrator

```java
// 自研 spec: service/agent-service-spec
public interface ServeOrchestrator {
    QueryResponse query(ServeRequest request);
    void streamQuery(ServeRequest request, QueryStreamObserver observer);
    void cancelActive(String conversationId);
    void resetConversation(String conversationId);
}

public interface AgentHandler {
    QueryResponse query(ServeRequest request);
    void streamQuery(ServeRequest request, QueryStreamObserver observer);
    default void start();
    default void stop();
    default void clearSession(String conversationId);
}
```

`ServeOrchestrator` 是 A2A bridge 面向 runtime 内核的统一执行 SPI。`AgentHandler` 是 Agent framework adapter SPI，由开发者实现或由 holder 委派。

```java
// 自研 spec DTO
public class ServeRequest {
    private String conversationId;
    private List<Map<String, Object>> messages;
    private String userId;
    private String spaceId;
    private String tenantId;
    private boolean stream;
    private Map<String, Object> metadata;

    public static ServeRequest fromQueryRequest(QueryRequest request);
    public String lastUserQuery();
}

public class QueryResponse {
    private Object result;
    private String conversationId;

    public QueryResponse();
    public QueryResponse(Object result, String conversationId);
}

public class QueryChunk {
    public static final String TYPE_INTERRUPT;
    public static final String TYPE_CHUNK;
    public static final String TYPE_ERROR;

    private String type;
    private Object data;

    public QueryChunk();
    public QueryChunk(String type, Object data);
}
```

这些 DTO 是 A2A Message 进入内部执行链后的中间模型。`ServeRequest.stream` 由 `A2AAgentExecutor` 根据 `_a2a_stream` state 覆盖，避免 blocking/streaming 两套输入模型漂移。第 3.4 的 async accepted 分支也必须复用 `A2AAgentExecutor`：差异在 RequestHandler/controller 侧先返回 accepted Task，后台仍通过同一 executor 和 orchestrator 推进执行。

```java
// 自研 app, shared implementation; outbound branch is not FEAT-001 contract
public class A2AEnabledServeOrchestrator implements ServeOrchestrator {
    public QueryResponse query(ServeRequest request);
    public void streamQuery(ServeRequest request, QueryStreamObserver observer);

    private QueryResumeResult syncResumePending(ServeRequest current);
    private Optional<ServeRequest> tryResumePending(...);
    private QueryChunk runAgentAndCaptureInterrupt(...);
    private Optional<ServeRequest> handleInterrupt(...);
    private Optional<ServeRequest> handleA2ADelegate(...);
}
```

承载第 3.2/3.3 中进入 handler 的默认执行链。类中包含 remote delegate 分支，这是当前实现共用能力；FEAT-001 只承诺 inbound 请求正确进入该执行链，不承诺 outbound 远程编排契约。远端委派由 agent 产生、由 orchestrator 代理发起，因此一旦共享分支参与 FEAT-001 入站请求，`A2AEnabledServeOrchestrator` 应继续承载本地等待点、parent task/context、remote invocation 绑定查找和 callback 回来后的恢复调度。若该共享分支触发下游 A2A 调用，调用模式选择分散在第 3.2、3.3、3.4 的对应旅程中：上游 streaming 则下游优先 streaming；非流式下游在能力可用时优先 callback，无法 callback 时退化为同步阻塞。

```java
// 自研 app
public class ActiveStreamRegistry {
    public StreamCancellationHandle register(String conversationId);
    public void unregister(String conversationId, StreamCancellationHandle handle);
    public void cancel(String conversationId);
    public void cancelAll();
    public int activeCount();
    public boolean awaitDrain(long timeoutMs);
}
```

承载流式执行注册、取消和 shutdown drain。第 3.2 streaming 旅程进入 `A2AEnabledServeOrchestrator.streamQuery` 后使用它维护 conversation 维度活动流。

### 4.5 Task、Push Config 与 callback 装配

```java
// 自研 app
@AutoConfiguration(after = AgentServiceAutoConfiguration.class)
public class A2AAutoConfiguration {
    public MainEventBus a2aMainEventBus();
    public TaskStore a2aTaskStore(ObjectProvider<MiddlewareProperties> middlewareProvider,
                                  ObjectProvider<CredentialDecryptor> decryptorProvider);
    public PushNotificationConfigStore a2aPushNotificationConfigStore();
    public PushNotificationSender a2aPushNotificationSender();
    public QueueManager a2aQueueManager(TaskStore taskStore, MainEventBus mainEventBus);
    public MainEventBusProcessor a2aMainEventBusProcessor(MainEventBus mainEventBus,
                                                           TaskStore taskStore,
                                                           PushNotificationSender pushSender,
                                                           QueueManager queueManager);
    public A2AConfigProvider a2aConfigProvider();
    public A2AProtocolAdapter a2aProtocolAdapter();
    public A2AAgentExecutor a2aAgentExecutor(ServeOrchestrator orchestrator,
                                             A2AProtocolAdapter adapter);
    public A2AEnabledServeOrchestrator a2aEnabledServeOrchestrator(...);
    public RequestHandler a2aRequestHandler(A2AAgentExecutor agentExecutor,
                                            TaskStore taskStore,
                                            QueueManager queueManager,
                                            PushNotificationConfigStore pushConfigStore,
                                            MainEventBusProcessor eventBusProcessor);
}
```

承载 A2A SDK server bean 装配。当前 `a2aPushNotificationSender()` 装配 HTTP sender，`a2aPushNotificationConfigStore()` 默认 `InMemoryPushNotificationConfigStore`；`SendMessage` inline push config 通过 SDK store 绑定，投递记录由 HTTP sender 的运行时记录承载。

```java
// 自研 app, implements A2A SDK TaskStore
public class RedisTaskStore implements TaskStore {
    public void save(Task task, boolean isOverwrite);
    public Task get(String taskId);
    public void delete(String taskId);
    public ListTasksResult list(ListTasksParams params);
}

// 自研 app, implements A2A SDK TaskStore
public class WriteThrottlingTaskStore implements TaskStore {
    public void save(Task task, boolean isOverwrite);
    public Task get(String taskId);
    public void delete(String taskId);
    public ListTasksResult list(ListTasksParams params);
}
```

`RedisTaskStore` 是 Redis-backed A2A TaskStore；`WriteThrottlingTaskStore` 包装 Redis store，降低 streaming 每个 chunk 都持久化 Task 对 SSE 的拖慢。

```java
// A2A SDK
public interface TaskStore { ... }
public interface PushNotificationConfigStore { ... }
public interface PushNotificationSender { ... }
public class MainEventBus { ... }
public class MainEventBusProcessor { ... }
public interface QueueManager { ... }
```

这些是 A2A SDK 提供的 Task、push config、event bus 和 queue SPI。FEAT-001 的 `SendMessage` inline push config 能力应复用 SDK store 能力，但当前不承诺独立 CRUD method。

```java
// 自研 app: fixed callback receiver
public class A2aPushNotificationCallbackController {
    public ResponseEntity<?> receiveCallback(String rawBody, HttpServletRequest request);
}

// Existing SDK extension point: HTTP sender.
public class HttpPushNotificationSender implements PushNotificationSender {
    public void send(...);
}

// Existing stores and processors remain primary state/event carriers.
public interface TaskStore { ... }
public interface PushNotificationConfigStore { ... }
public class MainEventBusProcessor { ... }
public interface QueueManager { ... }

// Existing runtime execution and remote invocation components should be extended first.
public class A2AEnabledServeOrchestrator implements ServeOrchestrator {
    private Optional<ServeRequest> tryResumePending(...);
    private Optional<ServeRequest> handleA2ADelegate(...);
}

public class A2ARemoteAgentClient {
    public RemoteInvocationRef callWithCallback(RemoteCall call, PushNotificationConfig callbackConfig);
}
```

这些是 callback 能力的承载点选择原则：优先迭代已有类和 SDK 扩展点，而不是为每个步骤创建新抽象。`A2aPushNotificationCallbackController` 是固定 HTTP 入口；`PushNotificationSender` 已替换为 HTTP sender；`PushNotificationConfigStore` 继续承载 inline config；`TaskStore` 的 shadow task 承载 local parent task/context、remote taskId 和 tool call 关联；`MainEventBusProcessor` 和 `QueueManager` 继续承载 Task 状态与事件流转；`A2AEnabledServeOrchestrator` 因为代理 agent 发起远端委派，承载绑定查找和恢复调度；`A2ARemoteAgentClient` 因为接收下游 runtime 的 `SendMessage` 响应，承载远端 taskId 的提取和回填。callback receiver 是独立回灌恢复入口，不是 `/a2a` JSON-RPC method。

### 4.6 远程编排共用类边界

```java
// 自研 app, 远程编排共用，非 FEAT-001 主体
public class A2ARemoteAgentCardRegistry {
    public void register(String name, AgentCard card);
    public void register(String name, AgentCard card, int timeoutSeconds);
    public List<RemoteAgentEntry> getAll();
    public Optional<RemoteAgentEntry> get(String name);
    public String resolveUrl(String name);

    public record RemoteAgentEntry(String name, AgentCard card, int timeoutSeconds) { }
}

public class A2AAgentCardDiscovery {
    public void discoverAll();
    public void shutdown();
}

public class A2ARemoteAgentClient {
    public record RemoteCall(String agentName, String message, String contextId, String taskId, ... ) { }

    public CompletableFuture<String> callStreaming(RemoteCall call, QueryStreamObserver streamObserver);
    public String callSync(String agentName, String message, String contextId, String taskId, ...);
    public RemoteInvocationRef callWithCallback(RemoteCall call, PushNotificationConfig callbackConfig);
    private RemoteInvocationRef bindRemoteTask(RemoteInvocationRef ref, String remoteTaskId);

    private RemoteInvocationMode chooseInvocationMode(RemoteCall call, AgentCard targetCard, InvocationContext context);
}

enum RemoteInvocationMode {
    STREAMING,
    CALLBACK,
    BLOCKING
}
```

这些类当前由 `A2AAutoConfiguration` 装配，也可能被 `A2AEnabledServeOrchestrator` 的 remote delegate 分支使用。FEAT-001 只说明 inbound 请求可能经过共用 orchestrator，不把 remote agent discovery/client/registry 作为本特性的服务入口契约主体。若共享分支参与 FEAT-001 入站请求的执行链，模式选择逻辑应优先作为 `A2ARemoteAgentClient` 或 `A2AEnabledServeOrchestrator#handleA2ADelegate` 的迭代职责：streaming 优先保持全链路流式；非流式下游在能力可用时优先 callback；callback 不可用时才同步阻塞。callback 模式下，下游 runtime 返回给 `A2ARemoteAgentClient` 的 accepted Task surface 是远端 taskId 的来源，client 应把它回填到 `RemoteInvocationRef`，再由 orchestrator 写入 TaskStore 的结构化绑定记录。只有该策略被多处复用且现有类明显承载不下时，才抽出独立 policy。

## 5. 关键数据对象与映射设计

本章只描述 FEAT-001 运行中跨组件传递、持久化或对外映射的关键数据对象，不承载配置项和 bean 装配说明。

### 5.1 A2A 外部对象

```java
// A2A SDK spec objects
public class AgentCard { ... }
public class AgentSkill { ... }
public class AgentCapabilities { ... }
public class Message { ... }
public class MessageSendParams { ... }
public class PushNotificationConfig { ... }
public class Task { ... }
public class TaskQueryParams { ... }
public interface EventKind { ... }
public interface StreamingEventKind { ... }
```

`AgentCard` 是 discovery 输出；`MessageSendParams` 是 `SendMessage` / `SendStreamingMessage` 的输入模型；`PushNotificationConfig` 只作为 `SendMessage` 的 inline 参数参与本次 Task 创建和 callback 绑定，不形成独立 CRUD 主路径；`Task` 是执行状态和结果的对外事实；`EventKind` / `StreamingEventKind` 是 blocking 与 streaming 的 SDK 返回表面。FEAT-001 的 callback body 复用 `SendMessage` JSON-RPC response/result 表面，不定义独立业务 payload。

### 5.2 内部执行对象

```java
// 自研 spec DTO
public class ServeRequest {
    private String conversationId;
    private List<Map<String, Object>> messages;
    private String userId;
    private String spaceId;
    private String tenantId;
    private boolean stream;
    private Map<String, Object> metadata;
}

public class QueryResponse {
    private Object result;
    private String conversationId;
}

public class QueryChunk {
    public static final String TYPE_INTERRUPT;
    public static final String TYPE_CHUNK;
    public static final String TYPE_ERROR;

    private String type;
    private Object data;
}
```

`ServeRequest` 是 A2A Message 进入 `AgentHandler` 前的统一内部请求。`stream` 由 A2A method 决定并在 executor 中覆盖；`messages` 当前只承载 text part 主路径；`metadata` 是 trace/correlation/runtime identity 的预留承载点，也可保存 remote invocation id 等不改变 Agent 输入语义的关联键。`pushNotificationConfig` 不进入 `ServeRequest` 作为 handler 输入，而是在 Task/push config/binding 存储侧承载，避免业务 handler 感知 A2A callback 协议细节。`QueryResponse` 对应阻塞执行结果，`QueryChunk` 对应流式 chunk、error 和 interrupt。

### 5.3 Message 到 ServeRequest 映射

| 来源 | 目标 | 当前实现 | 设计要求 |
|------|------|----------|----------|
| `message.contextId` / SDK context id | `ServeRequest.conversationId` | 已映射 | 作为会话、Task、stream registry 和远端委派绑定的本地上下文键。 |
| `message.taskId` / SDK task id | `TaskStore` / SDK task identity | 已映射 | 用于 resume、GetTask 和 callback 绑定，不直接作为 `ServeRequest` 字段进入 handler。 |
| `message.parts[].text` | `ServeRequest.messages[0].content` | 已映射 | 当前版本 text 主路径；非文本输入不承诺。 |
| `message.role` | `ServeRequest.messages[].role` | 已映射 | 非法 role 应返回 invalid params，当前待补。 |
| `params.metadata` | `ServeRequest.metadata` | 已保留 | 用于 trace/correlation/runtime identity，不得覆盖可信身份字段。 |
| HTTP headers | `A2AMessageContext.headers` / `ServeRequest.userId/spaceId/tenantId` | 待补 | 需要 allowlist，避免任意 header 进入 Agent 上下文。 |
| `params.pushNotificationConfig` | `PushNotificationConfigStore` + Task binding metadata | 待补 | `SendMessage` inline 创建 callback config 时必须保留、校验并与本次 taskId/contextId/caller identity 绑定；不得透传给 AgentHandler。 |

### 5.4 QueryChunk 到 A2A 输出映射

| `QueryChunk` | 当前转换 | A2A 表面 |
|--------------|----------|----------|
| `data == null` | 空 parts | 不发送 artifact。 |
| `data instanceof String` | `TextPart(data)` | artifact text part。 |
| 其他对象 | Gson JSON 文本 `TextPart` | artifact text part，保留原始 envelope。 |
| `type == interrupt` | 不走 `ChunkMapper` | `emitter.requiresInput(statusMessage)`。 |
| observer error | `emitter.fail()` | failed task。 |
| observer complete | `emitter.complete()` | completed task。 |

当前实现有意透明透传 AgentCore stream chunk envelope，包括 final answer，由调用方按 envelope 自己解释。

### 5.5 Task、Push Config 与投递记录对象

```java
// A2A SDK task/push objects
public class Task { ... }
public interface TaskStore { ... }
public interface PushNotificationConfigStore { ... }
public interface PushNotificationSender { ... }
```

`TaskStore` 是 `GetTask`、execution state 和 callback 恢复绑定的持久化承载。`PushNotificationConfigStore` 承载 `SendMessage` inline callback config；当前版本不承诺独立 CRUD method。callback 投递记录和幂等记录在首迭代由运行时内存对象承载；内联配置、投递 target 和 receiver 鉴权复用现有 access 配置和 trusted host 配置。

Task/push 相关结构化事实至少需要覆盖：

| 数据事实 | 首选承载 | 说明 |
|----------|----------|------|
| 本地 taskId / contextId | `TaskStore` / Task metadata | 本地执行、查询、恢复和上游响应等待的主关联键。 |
| inline push config id / callback target / auth ref | `PushNotificationConfigStore` | 只随 `SendMessage` 创建和绑定，不提供独立 CRUD 表面。 |
| caller runtime identity / trace id | Task metadata、push config metadata 或 HTTP metadata | 用于观测关联；首迭代不把 caller identity 作为完整信任根。 |
| notificationId / delivery attempt / delivery status | HTTP sender 运行时投递记录 | 用于终态 callback 幂等、失败记录和可观察性；投递失败不得改变 Task 终态。 |
| remote invocation id / remote taskId | `TaskStore` shadow task 绑定记录 | 下游 callback 回灌恢复的查找键；remote taskId 由 `A2ARemoteAgentClient` 从下游 accepted Task surface 回填。 |
| bounded wait state | controller 内存句柄 + TaskStore shadow task | `A2aJsonRpcController` 持有等待句柄；窗口超时后仍可通过 shadow task 关联处理后续 callback。 |
## 6. 关键配置项设计

本章只描述 FEAT-001 需要暴露或消费的关键配置项，以及配置项如何影响 Agent Card、JSON-RPC endpoint、TaskStore、callback 能力和 capability gating。bean 归属和函数签名见第 4 章。

### 6.1 A2A 服务入口配置

```yaml
openjiuwen:
  service:
    a2a:
      public-url: https://agents.example.com/runtime
      agent-description: My Agent
      documentation-url: https://example.com/docs
      icon-url: https://example.com/icon.png
      streaming: true
      push-notifications: false
      extended-agent-card: false
      default-input-modes: [text, text/plain]
      default-output-modes: [text, text/plain]
      provider-organization: My Company
      provider-url: https://example.com
      json-rpc-path: /a2a
      agent-card-path: /a2a/.well-known/agent-card.json
      skills:
        - id: my-skill
          name: My Skill
          description: This skill is published in Agent Card.
          tags: [my-tag]
          input-modes: [text]
          output-modes: [text]
```

`remote-agents` 不列为 FEAT-001 配置主体。它存在于当前 `A2AProperties` 和 `A2AAutoConfiguration` 中，是远程编排特性借用共用配置类的实现细节。若文档需要提及，只能标注为“共用配置类中由远程编排特性使用，非 FEAT-001 服务入口契约”。

### 6.2 配置默认值与语义

| 配置路径 | 类型 | 当前默认值 | FEAT-001 语义 |
|----------|------|------------|---------------|
| `openjiuwen.service.a2a.public-url` | String | null | Agent Card URL base；为空时从请求推导。 |
| `openjiuwen.service.a2a.agent-description` | String | `OpenJiuwen Agent Runtime Service` | Agent 描述。 |
| `openjiuwen.service.a2a.documentation-url` | String | null | 文档 URL。 |
| `openjiuwen.service.a2a.icon-url` | String | null | 图标 URL。 |
| `openjiuwen.service.a2a.streaming` | boolean | `true` | streaming capability 输入之一。 |
| `openjiuwen.service.a2a.push-notifications` | boolean | `false` | push capability 输入之一；为 false 时 callback receiver 不暴露。 |
| `/a2a/push-notifications/callback` | 固定路径 | `/a2a/push-notifications/callback` | callback receiver 固定路径；当前不提供独立配置项。 |
| `openjiuwen.service.a2a.extended-agent-card` | boolean | `false` | extended card capability 输入之一。 |
| `openjiuwen.service.a2a.default-input-modes` | List | `[text, text/plain]` | 默认输入模式。 |
| `openjiuwen.service.a2a.default-output-modes` | List | `[text, text/plain]` | 默认输出模式。 |
| `openjiuwen.service.a2a.provider-organization` | String | `""` | provider organization。 |
| `openjiuwen.service.a2a.provider-url` | String | `""` | provider URL。 |
| `openjiuwen.service.a2a.skills` | List | `[]` | Agent Card skill 声明。 |
| `openjiuwen.service.a2a.json-rpc-path` | String | `/a2a` | Agent Card 中声明的 JSON-RPC endpoint path。 |
| `openjiuwen.service.a2a.agent-card-path` | String | `/a2a/.well-known/agent-card.json` | 兼容/遗留路径配置；不是标准 discovery endpoint 决策源。 |
| `openjiuwen.service.a2a.task-completion-timeout-seconds` | int | `300` | task 完成等待相关配置。 |

### 6.3 自动装配与配置作用点

`A2AAutoConfiguration` 当前由这些配置和 classpath 条件驱动，装配结果影响 FEAT-001 的外部能力声明：

| Bean | 当前实现 | 配置作用点 |
|------|----------|------------|
| `MainEventBus` | SDK event bus | 支撑 Task/SSE 事件。 |
| `TaskStore` | 默认 `InMemoryTaskStore`；Redis checkpointer 时 `WriteThrottlingTaskStore(RedisTaskStore)` | middleware checkpointer 配置决定是否 Redis-backed。 |
| `PushNotificationConfigStore` | `InMemoryPushNotificationConfigStore` | `SendMessage` inline push config 的存储承载。 |
| `PushNotificationSender` | `HttpPushNotificationSender` | 使用绑定 push config 投递 callback；push capability 不能仅凭配置声明完整可用。 |
| `QueueManager` | `InMemoryQueueManager` | SDK event queue。 |
| `MainEventBusProcessor` | SDK processor | 持久化/分发事件，触发 push sender。 |
| `A2AProtocolAdapter` | 自研 adapter | A2A message -> `ServeRequest`。 |
| `A2AAgentExecutor` | 自研 executor | SDK executor -> `ServeOrchestrator`。 |
| `RequestHandler` | `DefaultRequestHandler.create(...)` | A2A method 到 executor/task/store/queue 的 SDK 桥。 |
| `A2AEnabledServeOrchestrator` | 默认 `ServeOrchestrator` | FEAT-001 inbound 请求进入内部 handler 的默认路径。 |
| remote registry/client/discovery | 已装配 | 远程编排特性使用，非 FEAT-001 服务入口契约主体。 |

### 6.4 Capability gating 配置原则

Agent Card 的 `capabilities.pushNotifications` 不仅由 `openjiuwen.service.a2a.push-notifications=true` 决定。首迭代 gating 同时检查配置启用、`SendMessage` inline config 可用、HTTP sender 可用、trusted callback hosts、callback store 和 callback handler；完整授权策略、签名/mTLS 和持久化 retry 不作为首迭代 capability 前置条件。

## 7. 特性实现验收标准

本章定义 FEAT-001 当前版本完成的可验证标准。验收用例应覆盖 version-scope 的能力矩阵和场景旅程；用例通过后，才能认为标准化 Agent 服务入口、`SendMessage` inline callback、runtime-to-runtime callback receiver 和核心运行模型已经按当前版本完成。

### 7.1 能力矩阵验收

| 能力 | 验收标准 | 建议用例 |
|------|----------|----------|
| Agent Card discovery | 三个 well-known endpoint 返回同一 Agent Card 语义；`url` 按 `public-url + json-rpc-path` 或当前请求地址生成；capability 不夸大未启用能力。 | controller slice test；不同 `public-url` / request host 组合。 |
| `/a2a` JSON-RPC 入口 | `POST /a2a` 和 `POST /a2a/` 均可分发 `SendMessage`、`SendStreamingMessage`、`GetTask`；未知 method 返回 method-not-found；非法 envelope 返回 parse/invalid request。 | JSON-RPC dispatcher test。 |
| 普通阻塞 `SendMessage` | 不携带 push config 时，创建或恢复 Task，进入 `A2AAgentExecutor`，调用 `ServeOrchestrator.query()`，返回单个 JSON-RPC result。 | executor bridge test；handler success/fail/input-required。 |
| 流式 `SendStreamingMessage` | 返回 SSE；每个事件使用 `event: jsonrpc`；`INPUT_REQUIRED` 必须先于 stream close 被观察；下游 A2A 调用优先保持 streaming。 | SSE integration test；event order test。 |
| `GetTask` | 按 taskId 从 `TaskStore` 查询 Task；不存在、store 异常和正常 Task 分别映射到标准 JSON-RPC/SDK 表面；不重新触发 Agent。 | TaskStore query test。 |
| `SendMessage` inline push config | `params.pushNotificationConfig` 被保留、校验并与本次 Task 绑定；合法请求返回 created/accepted Task 表面，不等待 Agent 终态。 | async accepted sendMessage test。 |
| Push config 独立 CRUD method | `Create/Get/List/Update/DeleteTaskPushNotificationConfig` 当前版本均不进入主路径，返回 method-not-found 或等价 unsupported 表面。 | unsupported method matrix test。 |
| runtime-to-runtime callback 投递 | Task 进入结果性状态后，`PushNotificationSender` 使用绑定配置向固定 callback receiver POST `SendMessage` response/result 表面；失败不改变 Task 终态，并记录运行时投递事实。 | sender contract test；delivery/idempotency test。 |
| runtime-to-runtime callback receiver | 固定 endpoint 按现有授权框架拦截，解析 Task result 表面并查找绑定；按 notification id 幂等处理；回灌后恢复本地 agent 执行链或更新 Task。 | callback receiver integration test。 |
| capability gating | `capabilities.pushNotifications=true` 只能在配置启用、HTTP sender、trusted hosts、callback store/handler 和 inline config 处理可用时声明。 | Agent Card capability gating test。 |

### 7.2 场景旅程验收

| 场景旅程 | Given | When | Then |
|----------|-------|------|------|
| 发现 Agent 能力 | runtime 已启动，配置了 Agent Card 基础信息。 | client 请求 `/.well-known/agent-card.json`、`/.well-known/agent.json` 或兼容 card endpoint。 | 返回 Agent Card；endpoint URL 稳定；pushNotifications 只在真实可用时为 true。 |
| 普通 client 阻塞调用 Agent | handler 返回普通文本结果，请求不携带 push config。 | client 调用 `SendMessage`。 | controller 设置 `_a2a_stream=false`；executor 构造 `ServeRequest(stream=false)`；最终 JSON-RPC result 包含 completed Task/Message 表面。 |
| 普通 client 流式调用 Agent | handler 产生多个 chunk。 | client 调用 `SendStreamingMessage`。 | controller 设置 `_a2a_stream=true`；SSE 逐个输出 JSON-RPC envelope；完成时正常关闭。 |
| 查询长任务 | Task 已存在于 `TaskStore`。 | client 调用 `GetTask`。 | 返回当前 Task 快照；不调用 `AgentHandler`；Task 不存在时返回标准错误。 |
| `SendMessage` 同步创建 callback 配置 | callbackUrl 合法且 host 命中 trusted callback hosts。 | 调用方 runtime 发送携带 `params.pushNotificationConfig` 的 `SendMessage`。 | Task 已可查询、push config 已绑定、后台执行已调度；HTTP response 返回 accepted Task 表面；终态通过 callback 投递。 |
| 内联 push config 不可信 | callback URL 不合法或 host 不满足 trusted callback hosts。 | 调用方发送携带 push config 的 `SendMessage`。 | 返回 invalid params / trust-policy error；不得保存 push config；不得创建异步执行；不得投递 callback。 |
| runtime-to-runtime callback 回灌恢复 | 本 runtime 曾向下游发起 callback 模式 A2A 调用，TaskStore 中存在 shadow task 绑定记录。 | 下游 runtime POST 固定 callback receiver。 | receiver 完成授权框架拦截和幂等校验；`A2AEnabledServeOrchestrator` 按 remote taskId 找到绑定；恢复本地 agent 执行。 |
| 普通 `SendMessage` 内部下游 callback | 普通 client 发起阻塞调用，内部 agent 委派下游 A2A，且下游支持 callback。 | 下游 callback 在 bounded wait 窗口内回灌。 | `A2aJsonRpcController` 持有的等待句柄被本地 agent 最终结果唤醒，原始 client 收到 JSON-RPC result。 |
| callback 迟到 | 普通 client 的 bounded wait 窗口已耗尽，controller 已返回可查询 Task 表面。 | 下游 callback 随后回灌。 | receiver 仍可按 TaskStore 绑定恢复执行或更新 Task；不得因为内存等待句柄消失而丢失结果。 |
| callback 重复或冲突 | 已处理过同一 notification id。 | 远端重试相同 payload 或发送同 id 不同 payload。 | 相同 payload 幂等 accepted；不同 payload 返回 conflict/error；不得改写既有 Task 终态。 |

### 7.3 错误表面验收

| 错误场景 | 验收标准 |
|----------|----------|
| 非法 JSON | 返回 JSON-RPC parse error。 |
| envelope 缺少必要字段或类型错误 | 返回 invalid request。 |
| method params shape/type 错误 | 返回 invalid params，不降级为 internal error。 |
| unsupported push config CRUD method | 返回 method-not-found 或等价 unsupported 表面。 |
| inline push config 不可信 | 返回 invalid params / trust-policy error；无 Task、push config 或投递副作用。 |
| handler 异常且 Task 已建立 | 形成 failed Task 或 SDK 等价失败事件。 |
| handler 异常且 Task 未建立 | 返回 JSON-RPC internal error。 |
| callback unauthorized | 由现有授权框架返回 401/403 或等价错误；不得恢复执行。 |
| callback binding not found | early callback 可进入短暂缓存；确认无绑定时返回 404/409 或等价错误，不得创建新的本地 Task 终态。 |
| callback delivery failed | 保留远端 Task terminal state，记录运行时失败事实；同一 notification id 保持稳定。 |

### 7.4 最小测试套件建议

当前版本至少应形成以下测试分组：

| 测试分组 | 覆盖重点 |
|----------|----------|
| `A2aJsonRpcControllerTest` | method 分发、trailing slash、invalid request/params、unsupported push CRUD、inline push config parsing。 |
| `A2AAgentExecutorTest` | blocking/streaming 统一映射、`ServeRequest.stream` 覆盖、文本结果、error、input-required 和 queue drain。 |
| `A2aSendMessageCallbackTest` | 携带 push config 的 async accepted 语义、Task 可查询、push config 绑定、后台执行调度。 |
| `PushNotificationSenderTest` | callback payload 映射、target trust 校验、notification id 稳定性、投递失败记录。 |
| `A2aPushNotificationCallbackControllerTest` | receiver 授权框架拦截、Task result payload 解析、幂等、冲突、binding lookup、malformed request no side effect。 |
| `A2AEnabledServeOrchestratorCallbackRecoveryTest` | remote invocation 绑定、remote taskId 回填、本地 agent 续跑、bounded wait 唤醒和迟到 callback 更新 Task。 |
| `AgentCardCapabilityGatingTest` | pushNotifications capability 与配置、`public-url`、receiver、sender、trusted hosts、callback store/handler 的组合 gating。 |
| `TaskStoreCallbackBindingTest` | TaskStore shadow task 中 local/remote task、context、tool call、wait state 的保存和查询。 |
