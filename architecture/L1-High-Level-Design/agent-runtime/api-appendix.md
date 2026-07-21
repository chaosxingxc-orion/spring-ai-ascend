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
- 错误响应、SSE 终止和 push notification 配置语义是什么。
- 哪些能力只是配置、outbound 调用或 host 能力，不属于 runtime 自有 northbound API。

本文档以当前 `service/agent-service-app` 代码为事实权威；L2 详细设计和指南只作为补充线索。

## 2. API 总览

当前 active northbound API 由 Spring Boot host 装配，核心 controller 位于 `com.openjiuwen.service.app.controller.a2a`。

| API 面 | Endpoint | Controller | 说明 |
|---|---|---|---|
| A2A JSON-RPC | `POST /a2a`, `POST /a2a/` | `A2aJsonRpcController` | 单一 A2A JSON-RPC 入口，按 JSON-RPC `method` 分发当前已支持方法。 |
| Agent Card 发现 | `GET /.well-known/agent-card.json` | `AgentCardController` | 标准 Agent Card 发现端点。 |
| Agent Card 兼容发现 | `GET /.well-known/agent.json` | `AgentCardController` | legacy 兼容路径，返回同一类 Agent Card。 |
| Agent Card prefixed 发现 | `GET /a2a/.well-known/agent-card.json` | `AgentCardController` | 兼容 A2A mount prefix 的发现路径。 |

当前 active 代码不提供独立的自研管理 REST API、gRPC API 或非 A2A 的 northbound 执行 API。当前 A2A JSON-RPC 是 `agent-runtime` Service Task API 的 active 实现形态。

## 3. A2A JSON-RPC 入口

### 3.1 Endpoint 与分发方式

`A2aJsonRpcController` 在 `/a2a` 和 `/a2a/` 上承载 JSON-RPC 请求。controller 先解析请求体中的 `method` 和 `id`，再进入当前已支持的分发分支。

| HTTP | Path | 分发依据 | 用途 |
|---|---|---|---|
| `POST` | `/a2a`, `/a2a/` | JSON-RPC `method` | 阻塞 JSON-RPC 响应或 SSE 流式 JSON-RPC 事件。 |

请求体由 controller 解析为 A2A message / task query 参数，并委托 A2A SDK `RequestHandler` 执行。Runtime 不定义新的 wire schema。

### 3.2 同步 JSON-RPC 方法

当前 controller 分派以下同步 JSON-RPC 方法：

| A2A 方法 | RequestHandler 调用 | 说明 |
|---|---|---|
| `SendMessage` | `onMessageSend(...)` | 非流式消息发送，返回 A2A task-or-message oneof 结果。 |
| `GetTask` | `onGetTask(...)` | 按 task id 查询 Task 快照。 |

未知或未分派方法返回 JSON-RPC method-not-found。

### 3.3 SSE 方法

当前 controller 分派以下 SSE 方法：

| A2A 方法 | RequestHandler 调用 | SSE 终止规则 |
|---|---|---|
| `SendStreamingMessage` | `onMessageSendStream(...)` | publisher 完成、发送失败或 controller 关闭时终止本次 SSE 响应。 |

SSE 帧固定使用：

```text
event: jsonrpc
data: <A2A JSON-RPC response JSON>
```

### 3.4 Push Notification 配置与 webhook

当前 auto-configuration 装配 SDK `InMemoryPushNotificationConfigStore`，并向 `DefaultRequestHandler` 提供该 store。当前 `A2aJsonRpcController` 未分发 Create/Get/List/Delete push notification config JSON-RPC method。

当前 `PushNotificationSender` 默认实现为 no-op，因此 runtime-to-runtime HTTP webhook 实际推送未激活。若未来激活 push config CRUD 或 webhook 投递，需要先回写 version-scope 特性和 L2 设计。

## 4. 错误响应

### 4.1 协议层错误

协议层错误由 `A2aJsonRpcController` 返回 JSON-RPC error response。

| 场景 | 错误码 |
|---|---|
| 请求体不是可解析 JSON | parse error (-32700) |
| JSON 结构无法映射为当前方法参数 | invalid request 或 internal error |
| 未知 JSON-RPC method | method-not-found (-32601) |
| A2A SDK 或 request handler 抛出 `A2AError` | 保留原错误码。 |
| 未预期异常 | internal error (-32603) |

错误响应会尽量回显 request `id`；当请求体无法解析出 id 时，id 可能为 `null`。

### 4.2 任务层失败

请求已形成 Task 后，执行失败由 A2A SDK 与 `A2AAgentExecutor` 投射为 A2A failed Task 或 JSON-RPC internal error。当前错误表面以 SDK Task / Message / Artifact 结构为准。

## 5. Agent Card 发现 API

### 5.1 Endpoint

`AgentCardController` 暴露三个 GET 路径：

| HTTP | Path | Produces |
|---|---|---|
| `GET` | `/.well-known/agent-card.json` | `application/json` |
| `GET` | `/.well-known/agent.json` | `application/json` |
| `GET` | `/a2a/.well-known/agent-card.json` | `application/json` |

这些路径都返回 `org.a2aproject.sdk.spec.AgentCard`。

### 5.2 URL 发布规则

Agent Card 返回前会根据访问上下文生成 URL：

- `openjiuwen.service.a2a.public-url` 非空时优先使用该 base URL。
- 未配置 public URL 时，从当前 request 的 scheme、host、port 推导 base。
- JSON-RPC endpoint path 使用 `openjiuwen.service.a2a.json-rpc-path`，默认 `/a2a`。

### 5.3 Agent Card 生成

默认 Agent Card 由 `AgentCardController#buildCard(...)` 生成：

1. name 来自 `AgentServiceIdentity#getAppName()`。
2. description、documentation、icon、capabilities、input/output modes、provider 和 skills 来自 `A2AProperties`。
3. version 来自 `ServiceProperties#getVersion()`。
4. supported interface URL 由 public URL 或当前 request base + JSON-RPC path 生成。

Agent Card 配置前缀为：

```text
openjiuwen.service.a2a
```

关键字段包括：

| 配置 | 默认 | 说明 |
|---|---|---|
| `public-url` | 空 | Agent Card 外部 URL base。 |
| `agent-description` | `OpenJiuwen Agent Runtime Service` | Agent 描述。 |
| `documentation-url` | 空 | 文档 URL。 |
| `icon-url` | 空 | 图标 URL。 |
| `streaming` | `true` | streaming capability。 |
| `push-notifications` | `false` | pushNotifications capability。 |
| `extended-agent-card` | `false` | extendedAgentCard capability。 |
| `default-input-modes` | `[text, text/plain]` | 默认输入模式。 |
| `default-output-modes` | `[text, text/plain]` | 默认输出模式。 |
| `provider-organization` | 空 | provider organization。 |
| `provider-url` | 空 | provider URL。 |
| `json-rpc-path` | `/a2a` | JSON-RPC endpoint path。 |
| `skills` | 空列表 | Agent Card skill 声明。 |

## 6. Health 与运行状态 API

Health API 由 host Spring Boot 应用和 `service/agent-service-app` 的 probe controller / readiness 组件提供，不属于 FEAT-001 A2A northbound Agent 服务入口自身。Actuator 或探针端点路径、认证和暴露策略由宿主应用决定。

## 7. Outbound 远端 Agent 调用边界

远端 Agent 调用不是 northbound API，而是 runtime 的 outbound 能力。当前工程通过 A2A remote agent discovery、registry 和 client 支撑远端 Agent Card 发现与调用，具体远端编排边界由 `FEAT-005` 和对应 L2 文档承接。

## 8. 非 API 边界

以下内容不是当前 active `agent-runtime` 自有服务化 API：

- 自研 REST 管理 API。
- gRPC northbound API。
- 非 A2A 的自定义执行 API。
- 平台级 Run / Session 管理 API。
- 业务 Agent checkpoint 或 memory 产品 API。
- Actuator endpoint 的路径、认证和暴露策略。

这些能力若未来进入 active 架构，需要先在对应设计或提案中明确事实来源，再回写本 L1 API 附录。
