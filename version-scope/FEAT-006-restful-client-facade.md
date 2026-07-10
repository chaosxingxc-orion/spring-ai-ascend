---
scope: version
module: agent-runtime
feature_type: functional
feature_id: FEAT-006
status: proposed
updated: 2026-06-26
authority:
  - README.md
  - FEAT-001-standardized-agent-service-entrypoint.md
drives:
  - ../architecture/L2-Low-Level-Design/agent-runtime/FEAT-006-restful-client-facade.md
  - ../agent-runtime/docs/guides/restful-client-facade.md
---

# RESTful Client Facade - 当前版本事实要求

## 1. 特性定位

FEAT-006 定义 `agent-runtime` 面向普通业务 client 的 RESTful 兼容入口。该入口的目标是降低非 A2A 调用方的接入成本，使 Web 应用、后端业务系统、脚本和传统 HTTP client 能用资源化 URL 与常规 HTTP 语义调用 Agent，而不必直接构造 A2A JSON-RPC envelope。

本特性不是新的系统间 Agent 协议，也不是 `FEAT-001` 的替代。RESTful API 必须是边缘适配层：它把 REST 请求映射到 `FEAT-001` 定义的标准 Agent 服务入口语义，并复用同一套 Task、Message、SSE、Cancel、GetTask、错误、租户和可观测规则。

本特性面向以下角色：

- 普通业务 client：希望用 REST 风格 API 调用 Agent，而不是直接使用 JSON-RPC。
- Web / BFF / 后端集成方：需要与现有 HTTP API 网关、API 文档、权限和 SDK 生成流程兼容。
- 测试与验收团队：需要验证 REST facade 不产生独立状态机，不改变标准 Agent 服务入口的事实语义。

本特性明确不面向以下系统间路径：

- 其他 agent-runtime 调用本 runtime：必须继续以 `FEAT-001` 的 A2A Agent 服务入口为标准。
- agent-bus forwarding 投递到 runtime：必须继续落到 `FEAT-001` 的标准 Agent 服务入口。
- 未来事件总线投递：应定义事件消费契约并归一到同一执行语义，不由 REST facade 承担。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| RESTful 同步消息调用 | SHOULD | facade 应提供业务 client 易用的同步调用入口，内部映射到标准 Agent 服务入口的阻塞执行语义，不得定义独立执行状态机。 |
| RESTful 流式消息调用 | SHOULD | facade 应提供 REST 风格流式入口，通过 SSE 向调用方投射同一 Task/artifact/progress/terminal 语义。 |
| RESTful Task 查询 | SHOULD | facade 应提供按 task id 查询状态和结果的资源化入口，内部映射到标准 Task 查询语义。 |
| RESTful Task 取消 | SHOULD | facade 应提供按 task id 请求取消的资源化入口，内部映射到标准 cancel 语义。 |
| 简化请求体 | SHOULD | facade 应允许 client 使用简化 JSON body，例如 text、sessionId、userId、metadata，而不要求 client 构造 A2A `jsonrpc/method/params/message` envelope。 |
| REST 错误表面 | SHOULD | facade 应以 HTTP status + REST error body 表达错误，但错误语义必须可追溯到标准 Agent 服务入口的 Task/error 语义。 |
| 租户标识传递 | MUST | facade 必须支持与标准入口一致的租户上下文传递和认证边界，不得自行声明完成租户认证。 |
| A2A 语义归一 | MUST | facade 的执行、状态、取消、超时、错误和可观测语义必须归一到 `FEAT-001`，不得形成第二套事实权威。 |
| runtime-to-runtime REST 调用 | OUT | 当前版本不要求其他 agent-runtime 通过 REST facade 调用本 runtime。 |
| agent-bus REST 投递 | OUT | 当前版本不要求 agent-bus forwarding 通过 REST facade 投递请求。 |
| REST 独立状态机 | OUT | 当前版本不允许 REST facade 定义独立于 A2A Task 的 run/job/message 状态机。 |
| webhook callback | OUT | 本特性不补充 webhook 主动推送能力；非一次性响应仍使用 SSE 或 Task polling。 |

## 3. 外部接口与入口要求

以下 URL 是事实要求层面的建议形态；L2 可以在不改变语义的前提下细化路径命名。

| 入口 | 类型 | 事实要求 |
|---|---|---|
| `POST /agents/{agentId}/messages` | REST endpoint | 应执行一次同步消息调用。请求体使用 REST-friendly JSON；内部映射到标准阻塞执行语义。 |
| `POST /agents/{agentId}/messages:stream` | REST endpoint | 应执行一次流式消息调用。响应使用 `text/event-stream`；事件内容必须可映射到标准 Task/artifact/progress/terminal 语义。 |
| `GET /tasks/{taskId}` | REST endpoint | 应查询 Task 当前状态和结果；不得返回与标准 Task 语义冲突的状态。 |
| `POST /tasks/{taskId}:cancel` | REST endpoint | 应请求取消 Task；内部映射到标准 cancel 语义。 |
| `X-Tenant-Id` | HTTP header | 必须作为网关注入租户标识的入口之一。REST facade 不认证该 header。 |
| REST request body | JSON body | 应支持 `text`、`sessionId`、`userId`、`metadata` 等简化字段，并由 facade 映射为标准 Agent Message / metadata。 |
| REST error body | JSON body | 应提供稳定字段，例如 `code`、`message`、`retryable`、`taskId`、`correlationId`；字段语义必须来自标准执行错误。 |

示例请求形态：

```http
POST /agents/travel-agent/messages
Content-Type: application/json
X-Tenant-Id: tenant-a
```

```json
{
  "text": "帮我查北京天气并订个酒店",
  "sessionId": "session-123",
  "userId": "user-1",
  "metadata": {
    "correlationId": "corr-001"
  }
}
```

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 业务系统同步调用 Agent | 业务系统只具备普通 HTTP client 能力 | 调用 `POST /agents/{agentId}/messages` 并提交简化 JSON body | facade 映射为标准 Agent 执行，返回一次性 JSON 结果；状态、错误和租户语义与标准入口一致。 |
| Web 前端流式调用 Agent | 前端希望直接消费 SSE，但不想构造 JSON-RPC envelope | 调用 `POST /agents/{agentId}/messages:stream` | facade 返回 SSE；事件投射标准 Task/artifact/progress/terminal 语义。 |
| 业务系统查询任务 | 调用方已获得 task id | 调用 `GET /tasks/{taskId}` | facade 返回同一 Task 的当前快照，不创造 REST 专属状态。 |
| 业务系统取消任务 | 调用方决定终止执行 | 调用 `POST /tasks/{taskId}:cancel` | facade 触发标准 cancel 语义；底层模型调用是否立即中断仍由 adapter 能力决定。 |
| 多租户 API 网关接入 | 前置网关已认证调用方 | 网关注入 `X-Tenant-Id` 后转发 REST 请求 | facade 使用同一租户上下文进入标准执行语义；不自行认证租户。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 Facade 归一语义

- RESTful API 是适配入口，不是事实权威。
- 每个 REST 调用必须映射到 `FEAT-001` 的标准 Agent 服务入口语义。
- REST facade 可以改变调用形态和错误展示形态，但不得改变 Agent 执行、Task 生命周期、取消、超时、租户、metadata 和可观测事实。
- REST facade 不得绕过 `AgentRuntimeHandler`、readiness gate、Task 表面、trajectory、state/memory scope 等标准执行链路。

#### 5.1.2 同步调用语义

- `POST /agents/{agentId}/messages` 应提供业务 client 友好的阻塞请求-响应模式。
- 请求体中的 `text` 必须映射为标准 Agent Message 的 text part。
- `sessionId` 必须映射为标准执行上下文中的会话标识；未提供时可以由 runtime/facade 生成或退化为 task id，但必须可解释。
- 返回结果应表达最终或当前 Task 表面；如果阻塞等待超时，可以返回当前 Task 快照或标准错误，但不得声称任务已完成。

#### 5.1.3 流式调用语义

- `POST /agents/{agentId}/messages:stream` 应通过 SSE 暴露执行过程。
- REST SSE event 可以采用 REST-friendly event 名称，但每个事件必须可追溯到标准 Task 状态、artifact/progress 或 error。
- 流式连接关闭条件必须与标准入口一致：Task final 或 interrupted 状态收束当前调用流。
- 流开始后发生异常时，应以可解析 error event 结束，而不是裸连接中断。

#### 5.1.4 Task 查询与取消语义

- `GET /tasks/{taskId}` 返回的是标准 Task 语义的 REST 投影，不是 REST 独立 job 资源。
- `POST /tasks/{taskId}:cancel` 必须触发标准 cancel 语义，并遵守 `FEAT-001` 关于协作式取消的边界。
- REST facade 不得为同一个底层 Task 创造另一个不一致的 run id / job id 状态源；如需暴露 REST resource id，必须能稳定映射到标准 task id。

#### 5.1.5 错误、状态与可观测结果

| 场景 | 事实要求 |
|---|---|
| 请求体非法 | 返回 4xx HTTP status 和 REST error body；错误应能映射到 invalid request。 |
| agent id 不可路由 | 返回明确错误；当前版本不因此承诺多 Agent handler 路由。 |
| runtime not ready | 返回可重试错误，语义与标准入口 `RUNTIME_NOT_READY` 一致。 |
| handler/runtime exception | 返回 failed Task 投影或 REST error body；错误字段必须保留 code/message/retryable 等可程序化信息。 |
| task 不存在 | 返回资源不存在语义，但不得与标准 Task store 事实冲突。 |
| cancel requested | 返回取消请求结果或取消后 Task 投影；不得承诺强制中断底层 LLM。 |
| tenant/correlation observability | REST 调用必须进入与标准入口一致的 tenant/context/task/agent/correlation/trace 关联链路。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| 系统间标准协议替代 | REST facade 不替代 A2A；runtime-to-runtime 和 agent-bus forwarding 不使用 REST 作为事实标准。 |
| agent-bus 专用 REST 入口 | 不为 agent-bus 提供 REST 私有投递协议。 |
| 事件总线消费协议 | REST facade 不承接未来事件总线投递；事件总线需要独立契约并归一到标准执行语义。 |
| REST 独立状态机 | 不定义独立 run/job/message 状态机。 |
| webhook callback | 不提供提交后主动 callback；非一次性响应使用 SSE 或 task polling。 |
| 多 Agent handler 路由 | `{agentId}` path 不意味着当前版本支持一个 runtime 内按 agent id 路由多个 handler。 |
| 租户认证 | facade 不认证 `X-Tenant-Id` 或 body metadata；认证由前置网关承担。 |
| 非文本主路径 | 当前主路径仍是 text 输入；file/data/multipart 等输入形态需单独进入版本事实。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把本特性设计成 `FEAT-001` 的边缘适配层，而不是与 A2A 平级的第二协议核心。
- REST controller 不得绕过标准 Agent execution chain；实现必须复用或等价映射到 A2A/Task/handler/readiness/cancel/error/trajectory 语义。
- REST 错误码、HTTP status 和 body 字段可以面向业务 client 优化，但必须能追溯到标准错误语义。
- REST stream 可以提供更友好的 event 名称，但事件内容必须能还原到标准 Task/artifact/progress/terminal/error。
- 文档和示例必须明确：REST facade 面向普通业务 client；runtime-to-runtime、agent-bus forwarding、未来事件总线不以 REST facade 为标准入口。
- 若未来要让 REST facade 支持 webhook、multipart、二进制文件、批量消息、独立 run resource 或多 Agent 路由，必须先更新本特性事实要求，再进入 L2 和实现。

## 7. 关联文档

- `version-scope/README.md`
- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `architecture/L2-Low-Level-Design/agent-runtime/FEAT-001-standardized-agent-service-entrypoint.md`
- `agent-runtime/docs/guides/a2a-endpoints.md`
- `agent-runtime/docs/guides/configuration-properties.md`
