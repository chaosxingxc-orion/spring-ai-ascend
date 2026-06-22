---
level: L1-HLD
TAG:
  - api-appendix
  - service-api
  - northbound-contract
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - logical.md
  - development.md
  - process.md
  - physical.md
  - spi-appendix.md
---

# `agent-runtime` — API 附录

## 1. API 附录定位

本文档描述 `agent-runtime` 当前 active 代码对外暴露的服务化 API、发现端点、调用语义、错误语义、可选健康面和 outbound 远端 Agent 调用边界。

API 附录回答以下问题：

- Runtime 当前暴露哪些 northbound HTTP API。
- A2A JSON-RPC 方法如何映射到同步 JSON 与 SSE 流式响应。
- Agent Card 发现端点如何生成和发布 URL。
- 请求 header、错误响应、SSE 终止和 push notification 配置语义是什么。
- 哪些能力只是配置、outbound 调用或 host 能力，不属于 runtime 自有 northbound API。

本文档以 `agent-runtime/` 代码为事实权威；L2 详细设计和指南只作为补充线索。

## 2. API 总览

当前 active northbound API 由 Spring Boot host 装配，核心 controller 位于 `com.huawei.ascend.runtime.boot`。

| API 面 | Endpoint | Controller | 说明 |
|---|---|---|---|
| A2A JSON-RPC | `POST /a2a`, `POST /a2a/` | `A2aJsonRpcController` | 单一 A2A JSON-RPC 入口，按请求方法和 `Accept` 分派同步或流式路径。 |
| Agent Card 发现 | `GET /.well-known/agent-card.json` | `AgentCardController` | 标准 Agent Card 发现端点。 |
| Agent Card 兼容发现 | `GET /.well-known/agent.json` | `AgentCardController` | legacy 兼容路径，返回同一类 Agent Card。 |
| Health | host actuator path | `AgentRuntimeHealthIndicator` | 可选 Actuator health contributor，不是 runtime 自有路径。 |

当前 active 代码不提供独立的自研管理 REST API、gRPC API 或非 A2A 的 northbound 执行 API。

## 3. A2A JSON-RPC 入口

### 3.1 Endpoint 与内容协商

`A2aJsonRpcController` 在同一路径上注册两个 handler：

| HTTP | Path | Produces | 用途 |
|---|---|---|---|
| `POST` | `/a2a`, `/a2a/` | `application/json` | 阻塞 JSON-RPC 响应。 |
| `POST` | `/a2a`, `/a2a/` | `text/event-stream` | SSE 流式 JSON-RPC 事件。 |

请求体由 A2A SDK `JSONRPCUtils.parseRequestBody(...)` 解析为 A2A JSON-RPC request wrapper。Runtime 不定义新的 wire schema。

### 3.2 租户 header

Runtime 读取可选 header：

```text
X-Tenant-Id
```

处理规则：

- 当 header 存在且非空时，trim 后写入 A2A `ServerCallContext` state，key 为 `tenantId`。
- 当 header 缺失时，使用配置 `agent-runtime.access.a2a.default-tenant-id`，默认值为 `default`。
- 该 header 只做传播，不做认证。多租户部署必须由前置网关剥离客户端自带 header，并在认证后重新注入可信值。

### 3.3 同步 JSON-RPC 方法

`handleBlocking(...)` 当前分派以下 A2A request wrapper：

| A2A 方法 | Request wrapper | Response wrapper | 说明 |
|---|---|---|---|
| `SendMessage` | `SendMessageRequest` | `SendMessageResponse` | 非流式消息发送，返回 A2A task-or-message oneof 结果。 |
| `GetTask` | `GetTaskRequest` | `GetTaskResponse` | 查询 Task，响应中的 `result` 是 proto Task 直接形态。 |
| `ListTasks` | `ListTasksRequest` | `ListTasksResponse` | 列出 Task。 |
| `CancelTask` | `CancelTaskRequest` | `CancelTaskResponse` | 取消 Task，响应中的 `result` 是 proto Task 直接形态。 |
| `CreateTaskPushNotificationConfig` | `CreateTaskPushNotificationConfigRequest` | `CreateTaskPushNotificationConfigResponse` | 创建 Task push notification 配置。 |
| `GetTaskPushNotificationConfig` | `GetTaskPushNotificationConfigRequest` | `GetTaskPushNotificationConfigResponse` | 查询 Task push notification 配置。 |
| `ListTaskPushNotificationConfigs` | `ListTaskPushNotificationConfigsRequest` | `ListTaskPushNotificationConfigsResponse` | 列出 Task push notification 配置。 |
| `DeleteTaskPushNotificationConfig` | `DeleteTaskPushNotificationConfigRequest` | `DeleteTaskPushNotificationConfigResponse` | 删除 Task push notification 配置；成功响应使用空 proto payload。 |

未知或未分派方法返回 JSON-RPC `METHOD_NOT_FOUND`。代码测试覆盖了 `GetExtendedAgentCard` 这类可解析但未分派方法返回 method-not-found。

### 3.4 SSE 方法

`handleStream(...)` 当前只接受以下两类请求：

| A2A 方法 | Request wrapper | RequestHandler 调用 | SSE 终止规则 |
|---|---|---|---|
| `SendStreamingMessage` | `SendStreamingMessageRequest` | `onMessageSendStream(...)` | Task final 状态或 interrupted 状态出现时终止本次 SSE 响应。 |
| `SubscribeToTask` | `SubscribeToTaskRequest` | `onSubscribeToTask(...)` | 不因 `INPUT_REQUIRED` 等 interrupted 状态自动终止，保持订阅语义。 |

SSE 帧固定使用：

```text
event: jsonrpc
data: <A2A JSON-RPC response or error JSON>
```

中途异常不会裸断传输；controller 会发送一个 JSON-RPC error frame，然后结束流。

## 4. 错误响应

### 4.1 协议层错误

协议层错误由 `A2aJsonRpcController` 返回 JSON-RPC error response，HTTP 层仍使用成功响应体承载 A2A JSON-RPC 错误。

| 场景 | 错误码 |
|---|---|
| 请求体不是可解析 JSON | `JSON_PARSE` (-32700) |
| JSON 结构无法映射为 A2A request | `INVALID_REQUEST` (-32600) |
| 未知 JSON-RPC method | `METHOD_NOT_FOUND` (-32601) |
| A2A SDK 或 request handler 抛出 `A2AError` | 保留原错误码。 |
| 未预期异常 | `INTERNAL` (-32603) |

错误响应会尽量回显 request `id`；当请求体无法解析出 id 时，id 可能为 `null`。

### 4.2 任务层失败

请求已形成 Task 后，执行失败由 `A2aAgentExecutor` 和 `A2aResultRouter` 映射为 A2A Task 失败。失败 message 同时包含：

- `TextPart`：人读文本，形如 `code: detail`。
- `DataPart`：机器可读结构，包含 `kind`、`code`、`message`、`retryable`、`schema_version`。
- message metadata：镜像 `a2a.error`。

任务层错误码归类见 `RuntimeErrorCode`，包括 `INVALID_INPUT`、`TIMEOUT`、`UPSTREAM_UNAVAILABLE`、`CANCELLED` 和 `INTERNAL`。

## 5. Agent Card 发现 API

### 5.1 Endpoint

`AgentCardController` 暴露两个 GET 路径：

| HTTP | Path | Produces |
|---|---|---|
| `GET` | `/.well-known/agent-card.json` | `application/json` |
| `GET` | `/.well-known/agent.json` | `application/json` |

两个路径都返回 `org.a2aproject.sdk.spec.AgentCard`。

### 5.2 URL 发布规则

Agent Card 返回前会根据访问上下文重写 URL：

- `agent-runtime.access.a2a.public-base-url` 非空时优先使用该 base URL；该值可以包含代理 path prefix。
- 未配置 public base URL 时，从当前 request 的 scheme、host、port 推导 base。
- 若 host 配置了 `server.forward-headers-strategy=framework`，Spring Web 的 forwarded header 支持会先改写 request facade。
- card 中相对 URL 会拼接到 base；绝对 URL 保持原样。
- provider URL、supported interfaces URL 和 card URL 使用同一 base 解析规则。

### 5.3 Agent Card 生成与覆盖

默认 Agent Card 由 `RuntimeAutoConfiguration#a2aAgentCard(...)` 生成：

1. 如果存在 `AgentCardProvider` bean，直接使用其 `agentCard()`。
2. 否则，若 `agent-runtime.access.a2a.agent-card.name` 显式配置，使用该 name。
3. 否则，尝试使用 `agent-runtime.access.a2a.default-agent-id` 匹配当前唯一注册的 handler。
4. 否则，使用当前唯一 `AgentRuntimeHandler.agentId()`；没有 handler 时退回 `agent`。

当前 active host 只允许一个本地 `AgentRuntimeHandler`。多个本地 handler / 多本地 Agent 的同实例路由不属于当前版本能力，应进入未来版本提案；需要暴露多个本地 Agent 时，应拆分为多个 runtime 实例或通过明确的 `AgentCardProvider` 覆盖发布元数据。

Agent Card 配置前缀为：

```text
agent-runtime.access.a2a.agent-card
```

当前字段包括：

| 配置 | 默认 | 说明 |
|---|---|---|
| `name` | handler agentId 或 `agent` | Agent 名称；显式配置后进入 YAML-driven card 控制。 |
| `description` | `agent-runtime` | 描述文本。 |
| `version` | `0.1.0` | Agent 版本。 |
| `organization` | `spring-ai-ascend` | provider organization。 |
| `organization-url` | 空，serve 时解析为 published base | provider URL。 |
| `endpoint` | `/a2a` | A2A endpoint path。 |

## 6. Health 与运行状态 API

`AgentRuntimeHealthIndicator` 是可选 Actuator health contributor。只有 Actuator health 类型在 classpath 时，`RuntimeAutoConfiguration` 才注册该 bean。

Health 语义：

| 状态来源 | Health 影响 |
|---|---|
| `RuntimeReadiness` 未 ready | `OUT_OF_SERVICE`。 |
| readiness ready 且当前 handler `isHealthy()` 为 false | `DOWN`。 |
| readiness ready 且当前 handler 健康 | `UP`。 |
| remote A2A agents 不可达 | 只进入 health detail，不降低整体状态。 |

Health detail 包含当前 handler 的 agentId 健康状态；配置远端 Agent 时，还包含 `remoteAgents.available`、`remoteAgents.pending` 和 `remoteAgents.pendingUrls`。

Actuator 端点路径和暴露策略由宿主 Spring Boot 应用决定，通常是 `/actuator/health`，不属于 runtime 自有 controller path。

## 7. Outbound 远端 Agent 调用边界

远端 Agent 调用不是 northbound API，而是 runtime 的 outbound 能力。它由 `A2aClientAutoConfiguration` 条件装配：

```text
agent-runtime.remote-agents[0].url
```

只有至少一个 remote agent URL 被配置时，才启用远端 card cache、outbound adapter、invocation service 和 card refresh lifecycle。

配置模型：

| 配置 | 说明 |
|---|---|
| `agent-runtime.remote-agents[n].url` | 远端 A2A Agent base URL。 |
| `agent-runtime.remote-agents[n].stream-timeout` | 该远端流式调用超时；未配置时使用 adapter 默认值。 |
| `agent-runtime.remote-agents[n].output.default-target` | 远端非终端输出的默认 target，未配置为 `BOTH`。 |
| `agent-runtime.remote-agents[n].output.completion-target` | 远端完成消息的 target，未配置为 `BOTH`。 |

远端 card cache 会解析远端 Agent Card，并把可调用远端 Agent 投影为中立 `RemoteAgentToolSpec`，供框架适配器作为工具消费。远端调用执行、resume 和 cancel 由 `RemoteAgentInvocationService` 与 A2A outbound adapter 编排。

## 8. 非 API 边界

以下内容不是当前 active `agent-runtime` 自有服务化 API：

- 自研 REST 管理 API。
- gRPC northbound API。
- 非 A2A 的自定义执行 API。
- 平台级 Run / Session / Tenant 管理 API。
- 业务 Agent checkpoint 或 memory 产品 API。
- Actuator endpoint 的路径、认证和暴露策略。

这些能力若未来进入 active 架构，需要先在对应设计或提案中明确事实来源，再回写本 L1 API 附录。
