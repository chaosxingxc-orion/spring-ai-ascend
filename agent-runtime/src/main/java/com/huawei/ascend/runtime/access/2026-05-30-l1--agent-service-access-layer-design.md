# 06. agent-service L1 Access Layer

## 1. 职责

- 接收标准 A2A JSON-RPC 调用和外部异步入站消息。
- 将外部协议请求归一为内部 `AccessIntent`。
- 通过 `TaskHandler.runTask(AccessIntent)` 调用 L4 任务入口；该方法签名后续需要与 L4 稳定接口保持一致。
- 基于 L3 队列接口创建并持有面向用户回消息的队列实例。
- 对 L4/L5 暴露 `NotificationPort.notify(frame)`，接收用户可见消息帧。
- 将 `NotificationFrame` 映射为 A2A SDK 的 `TaskStatus / Message / Artifact / error / terminal` 语义，或投递到外部异步回消息通道。

---

## 2. 包结构

```text
service/
  access/
    api/
      NotificationPort.java
    core/
      AccessGateway.java
      TaskHandler.java
    config/
      AccessLayerConfiguration.java
    model/
      AccessIntent.java
      AccessAcceptedResponse.java
      AccessOperation.java
      ReplyChannel.java
      NotificationFrame.java
      NotificationType.java
      EgressBinding.java
    egress/
      EgressAdapter.java
      EgressQueueRegistry.java
      DefaultEgressQueueRegistry.java
      DefaultNotificationPort.java
      EgressDispatcher.java
      EgressDeliveryException.java
    protocol/
      a2a/
        A2aJsonRpcController.java
        A2aWellKnownAgentCardController.java
        A2aAccessService.java
        A2aEnvelope.java
        A2aAcceptedResponse.java
        A2aIngressAdapter.java
        A2aEgressAdapter.java
        A2aTaskMapper.java
        A2aTaskQueryParams.java
        A2aOutput.java
        A2aOutputSink.java
        A2aOutputHandle.java
        A2aOutputRegistry.java
        DefaultA2aOutputSink.java
      async/
        AsyncIngressPort.java
        AsyncEnvelope.java
        AsyncIngressAdapter.java
        AsyncEgressAdapter.java
        AsyncOutputSink.java
```

`temp/` 目录只放当前阶段为了本地启动和编译预留的临时代码，例如临时 L3 队列占位和临时 L4 `TaskHandler`，不进入正式方案包结构。

---

## 3. 内部 API

```java
public interface TaskHandler {
    CompletionStage<AccessAcceptedResponse> runTask(AccessIntent intent);
}

public interface NotificationPort {
    void notify(NotificationFrame frame);
}
```

| 方法 | 入参 | 返回值 | 描述 |
|---|---|---|---|
| `TaskHandler.runTask` | `AccessIntent intent` | `CompletionStage<AccessAcceptedResponse>` | L1 调用 L4 的任务入口；L1 不解释 `AccessOperation`，由 L4 自己判断提交、恢复、取消或其它操作语义。 |
| `NotificationPort.notify` | `NotificationFrame frame` | `void` | L4/L5 将用户可见消息交回 L1，L1 再根据 `EgressBinding` 投递到 A2A 或异步通道。 |

---

## 4. POJO

```java
public enum AccessOperation {
    SUBMIT, RESUME, CANCEL, QUERY, SUBSCRIBE, CALLBACK
}

public enum ReplyChannel {
    A2A, ASYNC
}

public record AccessIntent(
    AccessOperation operation,
    String tenantId,
    String userId,
    String agentId,
    String sessionId,
    String query,
    String idempotencyKey,
    Object payload
) {}

public record AccessAcceptedResponse(
    String tenantId,
    String userId,
    String agentId,
    String sessionId,
    String taskId,
    boolean accepted,
    String message
) {}

public enum NotificationType {
    ACK, TOOL_RESULT, LLM_RESULT, ERROR
}

public record NotificationFrame(
    String tenantId,
    String sessionId,
    String taskId,
    NotificationType type,
    Object payload,
    boolean terminal
) {}

public record EgressBinding(
    String tenantId,
    String sessionId,
    String taskId,
    ReplyChannel replyChannel,
    String deliveryMode,
    String targetRef,
    String correlationId
) {}
```

| 类型 | 字段/枚举值 | 描述 |
|---|---|---|
| `AccessOperation` | `SUBMIT / RESUME / CANCEL / QUERY / SUBSCRIBE / CALLBACK` | 归一后的入口操作类型，L1 只透传给 L4。 |
| `ReplyChannel` | `A2A` | 通过 A2A 通道写回。 |
| `ReplyChannel` | `ASYNC` | 通过外部异步 reply topic/queue 写回。 |
| `AccessIntent` | `tenantId` | 租户标识。 |
| `AccessIntent` | `userId` | 用户标识，用于用户隔离、权限判断和下游记忆隔离。 |
| `AccessIntent` | `agentId` | 目标 Agent 标识；入口层负责非空校验，并在接入 agent registry / agent card / 鉴权网关后完成可用性判断。 |
| `AccessIntent` | `sessionId` | 会话标识，可为空。 |
| `AccessIntent` | `query` | 本轮用户请求文本或规范化查询意图。 |
| `AccessIntent` | `idempotencyKey` | 幂等键，可为空。 |
| `AccessIntent` | `payload` | 结构化扩展载荷，例如 A2A parts、metadata、contextId、correlationId、异步 replyTopic。 |
| `AccessAcceptedResponse` | `taskId` | L4 创建或定位到的任务标识，L1 以此创建回消息队列绑定。 |
| `NotificationType` | `ACK` | 内部处理已接收。 |
| `NotificationType` | `TOOL_RESULT` | 工具、检索、规划等非 LLM 直接生成的结果。 |
| `NotificationType` | `LLM_RESULT` | LLM 生成文本、结构化内容或流式结果片段。 |
| `NotificationType` | `ERROR` | 内部执行失败或业务错误。 |
| `NotificationFrame` | `terminal` | 是否最后一帧；结束语义不放在 `NotificationType` 中。 |
| `EgressBinding` | `deliveryMode` | A2A 场景取 `SYNC / STREAM / PUSH_NOTIFICATION`，异步通道取 `ASYNC`。 |
| `EgressBinding` | `targetRef` | 具体交付目标；流式 A2A 当前可标记为 `sse`，push notification 为标准配置中的 callback URL，异步通道为 reply topic/queue。 |
| `EgressBinding` | `correlationId` | 外部请求关联标识。 |
| `EgressBinding` | `attributes` | 出站扩展属性；例如 A2A push notification token/auth 信息。 |

`NotificationFrame` 不要求 L4/L5 填写 `sequence / artifactId / protocol metadata`。这些字段由 L1 出站适配器按 `tenantId + sessionId + taskId` 的投递上下文补齐。

---

## 5. A2A 入站与发现

A2A 对外只暴露标准 JSON-RPC 单入口和标准 Agent Card 发现入口。

| HTTP | 路径 | 请求 | 响应 | 描述 |
|---|---|---|---|---|
| `POST` | `/a2a/` | A2A SDK JSON-RPC request | A2A SDK JSON-RPC response 或 SSE | 标准 A2A 单入口，通过 `method` 区分调用类型。 |
| `GET` | `/.well-known/agent-card.json` | 无 | `org.a2aproject.sdk.spec.AgentCard` | 标准 Agent Card 发现路径。 |

请求头当前只要求：

| Header | 要求 | 描述 |
|---|---|---|
| `Content-Type` | `application/json` | JSON-RPC 请求体。 |
| `Accept` | `application/json` 或 `text/event-stream` | `SendMessage` 用 JSON，`SendStreamingMessage` 用 SSE。 |

`A2aJsonRpcController` 参考 AgentScope 的处理方式：接收原始 JSON body，先读取 JSON-RPC `method`，再用 A2A SDK 的请求类型校验和分发。

| A2A method | SDK request | L1 行为 | SDK response |
|---|---|---|---|
| `SendMessage` | `SendMessageRequest` | 将 `params.message` 转为 `A2aEnvelope -> AccessIntent`，调用 `A2aAccessService.send`。 | `SendMessageResponse` |
| `SendStreamingMessage` | `SendStreamingMessageRequest` | 将请求转为 `A2aEnvelope -> AccessIntent`，创建 stream egress binding，并返回 SSE。 | SSE 中每帧为 `SendStreamingMessageResponse` |
| `SendMessage` + `configuration.taskPushNotificationConfig` | `SendMessageRequest` | 使用 A2A 标准 push notification 配置创建出站绑定；L4/L5 后续通知由 L1 POST 到配置 URL。 | `SendMessageResponse` |
| `GetTask` | `GetTaskRequest` | 按 `tenantId + sessionId + taskId` 查询 L1 出站 registry，并聚合为 SDK `Task`。 | `GetTaskResponse` |
| `CancelTask` | `CancelTaskRequest` | 转为 `AccessOperation.CANCEL` 并透传给 L4。 | `CancelTaskResponse` |

Push notification 不使用自定义 `reply.mode / reply.target`。外部调用方应按 A2A 标准把 `taskPushNotificationConfig` 放在 `params.configuration` 下。

### 5.1 A2A 入站字段契约

外部请求体必须是 A2A JSON-RPC 结构，不是内部 `AccessIntent`。L1 入站会先校验 JSON-RPC 外壳和 A2A SDK request 类型，再从 A2A `params` 中提取平台业务字段，归一为内部 `AccessIntent`。

#### 5.1.1 JSON-RPC 外壳字段

| 字段路径 | 必填 | 类型 | 允许值 | 描述 | 内部映射 |
|---|---|---|---|---|---|
| `jsonrpc` | 是 | string | 固定 `2.0` | JSON-RPC 协议版本。 | 不进入 `AccessIntent`。 |
| `id` | 是 | string / number / boolean | 调用方自定义 | 请求标识，响应原样带回。 | 不进入 `AccessIntent`。 |
| `method` | 是 | string | `SendMessage` / `SendStreamingMessage` / `GetTask` / `CancelTask` | A2A 调用方法。 | 映射为不同处理流程；`SendMessage` 和 `SendStreamingMessage` 对应 `AccessOperation.SUBMIT`，`CancelTask` 对应 `AccessOperation.CANCEL`。 |
| `params` | 是 | object | 随 `method` 变化 | A2A 方法参数。 | 解析为 `A2aEnvelope` 或 task 查询参数。 |

#### 5.1.2 SendMessage / SendStreamingMessage 字段

| 字段路径 | 必填 | 类型 | 允许值 | 描述 | 内部映射 |
|---|---|---|---|---|---|
| `params.tenant` | 是 | string | 租户 ID | A2A SDK 当前版本要求的租户字段；优先作为租户标识。 | `AccessIntent.tenantId`。 |
| `params.message` | 是 | object | A2A `Message` | 用户输入消息。 | 解析为 `A2aEnvelope.message`。 |
| `params.message.role` | 是 | string / enum | 当前使用 `ROLE_USER` | 消息角色，外部用户请求应为用户角色。 | 仅用于 A2A SDK request 校验，不进入 `AccessIntent`。 |
| `params.message.messageId` | 是 | string | 调用方自定义 | 外部消息 ID。 | 保留在 `payload.metadata` 或 A2A 原始 message 中。 |
| `params.message.contextId` | 建议 | string | 会话上下文 ID | A2A 上下文标识；建议与 `metadata.sessionId` 保持一致。 | `payload.contextId`；当 `metadata.sessionId` 缺失时可作为 `AccessIntent.sessionId`。 |
| `params.message.parts` | 是 | array | 至少一个元素 | A2A 消息内容分片。 | `payload.parts`。 |
| `params.message.parts[].kind` | 是 | string | 当前支持 `text` | 分片类型。 | 保留在 `payload.parts`。 |
| `params.message.parts[].text` | text 分片必填 | string | 用户输入文本 | 用户本轮输入内容。多个 text 分片会按换行拼接。 | `AccessIntent.query`。 |
| `params.message.metadata` | 是 | object | key-value | 平台业务字段承载区；这些字段不是 A2A 标准强制字段，是 L1 与调用方的业务约定。 | `payload.metadata`。 |
| `params.message.metadata.tenantId` | 可选 | string | 租户 ID | 兼容字段；当 `params.tenant` 存在时以 `params.tenant` 为准。 | `AccessIntent.tenantId` 的备选来源。 |
| `params.message.metadata.userId` | 是 | string | 用户 ID | 平台用户标识。 | `AccessIntent.userId`。 |
| `params.message.metadata.agentId` | 是 | string | Agent ID | 目标 Agent 标识。 | `AccessIntent.agentId`。 |
| `params.message.metadata.sessionId` | 是 | string | Session ID | 平台会话标识。 | `AccessIntent.sessionId`。 |
| `params.message.metadata.idempotencyKey` | 可选 | string | 调用方自定义 | 幂等键。 | `AccessIntent.idempotencyKey`。 |
| `params.message.metadata.correlationId` | 可选 | string | 调用方自定义 | 链路关联标识。 | `payload.correlationId`，并进入 `EgressBinding.correlationId`。 |

`SendMessage` 示例：

```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "method": "SendMessage",
  "params": {
    "tenant": "tenant-001",
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-001",
      "contextId": "session-123",
      "parts": [
        {
          "kind": "text",
          "text": "帮我规划一个三天上海行程"
        }
      ],
      "metadata": {
        "tenantId": "tenant-001",
        "userId": "user-001",
        "agentId": "travel-agent",
        "sessionId": "session-123",
        "idempotencyKey": "idem-001",
        "correlationId": "corr-001"
      }
    }
  }
}
```

`SendStreamingMessage` 请求体与 `SendMessage` 一致，只是 `method` 改为 `SendStreamingMessage`，响应为 `text/event-stream`。

#### 5.1.3 Push Notification 字段

Push notification 仍然使用 `SendMessage`，只是在 `params.configuration.taskPushNotificationConfig` 中携带 A2A 标准 push notification 配置。L1 不使用自定义 `reply.mode / reply.target`。

| 字段路径 | 必填 | 类型 | 允许值 | 描述 | 内部映射 |
|---|---|---|---|---|---|
| `params.configuration.taskPushNotificationConfig` | Push 模式必填 | object | A2A push notification config | 标准 A2A push notification 配置。 | `payload.a2aPushNotificationConfig`。 |
| `params.configuration.taskPushNotificationConfig.url` | Push 模式必填 | string | HTTP/HTTPS URL | L1 后续 POST 出站事件的 callback 地址。 | `EgressBinding.targetRef`。 |
| `params.configuration.taskPushNotificationConfig.id` | 可选 | string | 调用方自定义 | push 配置 ID。 | `EgressBinding.attributes.pushNotificationConfigId`。 |
| `params.configuration.taskPushNotificationConfig.taskId` | 可选 | string | 调用方自定义 | 外部指定的 task 关联 ID。 | `EgressBinding.attributes.pushNotificationTaskId`。 |
| `params.configuration.taskPushNotificationConfig.token` | 可选 | string | 调用方自定义 | callback 鉴权 token。 | `EgressBinding.attributes.pushNotificationToken`。 |
| `params.configuration.taskPushNotificationConfig.authentication.scheme` | 可选 | string | 如 `Bearer` | callback 鉴权方案。 | `EgressBinding.attributes.pushNotificationAuthScheme`。 |
| `params.configuration.taskPushNotificationConfig.authentication.credentials` | 可选 | string | 调用方自定义 | callback 鉴权凭据。 | `EgressBinding.attributes.pushNotificationAuthCredentials`。 |
| `params.configuration.taskPushNotificationConfig.tenant` | 可选 | string | 租户 ID | push 配置自身携带的租户字段。 | `EgressBinding.attributes.pushNotificationTenant`。 |

Push notification 示例：

```json
{
  "jsonrpc": "2.0",
  "id": "req-004",
  "method": "SendMessage",
  "params": {
    "tenant": "tenant-001",
    "configuration": {
      "taskPushNotificationConfig": {
        "id": "push-config-001",
        "url": "http://localhost:9001/a2a/callback",
        "token": "optional-token",
        "authentication": {
          "scheme": "Bearer",
          "credentials": "optional-credential"
        }
      }
    },
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-004",
      "contextId": "session-123",
      "parts": [
        {
          "kind": "text",
          "text": "帮我规划一个三天上海行程"
        }
      ],
      "metadata": {
        "tenantId": "tenant-001",
        "userId": "user-001",
        "agentId": "travel-agent",
        "sessionId": "session-123"
      }
    }
  }
}
```

#### 5.1.4 GetTask 字段

| 字段路径 | 必填 | 类型 | 允许值 | 描述 | 内部映射 |
|---|---|---|---|---|---|
| `params.id` | 是，或使用 `params.taskId` | string | task ID | A2A task 标识。 | `A2aTaskQueryParams.taskId`。 |
| `params.taskId` | 是，或使用 `params.id` | string | task ID | 兼容字段。 | `A2aTaskQueryParams.taskId`。 |
| `params.metadata` | 是 | object | key-value | 平台查询上下文。 | 解析为 `A2aTaskQueryParams`。 |
| `params.metadata.tenantId` | 是 | string | 租户 ID | 租户标识。 | `A2aTaskQueryParams.tenantId`。 |
| `params.metadata.sessionId` | 是 | string | Session ID | 会话标识。 | `A2aTaskQueryParams.sessionId`。 |

`GetTask` 示例：

```json
{
  "jsonrpc": "2.0",
  "id": "req-002",
  "method": "GetTask",
  "params": {
    "id": "task-001",
    "metadata": {
      "tenantId": "tenant-001",
      "sessionId": "session-123"
    }
  }
}
```

#### 5.1.5 CancelTask 字段

| 字段路径 | 必填 | 类型 | 允许值 | 描述 | 内部映射 |
|---|---|---|---|---|---|
| `params.id` | 是，或使用 `params.taskId` | string | task ID | A2A task 标识。 | `payload.taskId`。 |
| `params.taskId` | 是，或使用 `params.id` | string | task ID | 兼容字段。 | `payload.taskId`。 |
| `params.metadata` | 是 | object | key-value | 平台取消上下文。 | 解析为取消场景的 `A2aEnvelope.context`。 |
| `params.metadata.tenantId` | 是 | string | 租户 ID | 租户标识。 | `AccessIntent.tenantId`。 |
| `params.metadata.userId` | 是 | string | 用户 ID | 用户标识。 | `AccessIntent.userId`。 |
| `params.metadata.agentId` | 是 | string | Agent ID | 目标 Agent 标识。 | `AccessIntent.agentId`。 |
| `params.metadata.sessionId` | 是，或使用 `params.metadata.contextId` | string | Session ID | 会话标识。 | `AccessIntent.sessionId`。 |
| `params.metadata.contextId` | 可选 | string | Context ID | A2A 上下文标识；可作为 sessionId 备选。 | `payload.contextId`。 |
| `params.metadata.idempotencyKey` | 可选 | string | 调用方自定义 | 幂等键。 | `AccessIntent.idempotencyKey`。 |
| `params.metadata.correlationId` | 可选 | string | 调用方自定义 | 链路关联标识。 | `payload.correlationId`。 |

`CancelTask` 示例：

```json
{
  "jsonrpc": "2.0",
  "id": "req-003",
  "method": "CancelTask",
  "params": {
    "id": "task-001",
    "metadata": {
      "tenantId": "tenant-001",
      "userId": "user-001",
      "agentId": "travel-agent",
      "sessionId": "session-123"
    }
  }
}
```

`tenantId / userId / agentId / sessionId` 当前从 A2A `message.metadata` 或 task `params.metadata` 读取。后续如平台网关统一注入鉴权上下文，可由 A2A adapter 从请求头或安全上下文补齐，但仍不改变内部 `AccessIntent`。

---

## 6. A2A 出站映射

`NotificationFrame` 是内部统一消息帧，不直接暴露给 A2A client。`A2aEgressAdapter` 负责映射为 A2A SDK 标准类型，并补齐 `sequence / artifactId / protocol metadata`。

| `NotificationType` | A2A SDK 输出 | 描述 |
|---|---|---|
| `ACK` | `TaskStatusUpdateEvent(SUBMITTED)` | 表示请求已进入内部处理。 |
| `TOOL_RESULT` | `TaskArtifactUpdateEvent(Artifact)` | 工具、检索、规划等结构化或中间结果优先映射为 Artifact。 |
| `LLM_RESULT` | Agent `Message` | LLM 中间片段为 working，terminal 帧为 completed。 |
| `ERROR` | `TaskStatusUpdateEvent(FAILED)` | 表示执行失败；错误信息放入 status message。 |

`A2aOutput` 当前保留 `kind/body/metadata` 便于内部调试，同时新增持有 SDK `StreamingEventKind event`，真实 A2A SSE 输出使用 `event` 构造 `SendStreamingMessageResponse`，push notification 使用同一个 `event` 序列化后 POST 到配置的 callback URL。

`GetTask` 不直接返回 `A2aOutput` 列表，而是通过 `A2aTaskMapper` 将 registry 中的输出聚合为 SDK `Task`，包含 status、history 和 artifacts。

---

## 7. 出站队列与通知

```java
public interface EgressAdapter {
    ReplyChannel channel();
    void deliver(EgressBinding binding, NotificationFrame frame);
}

public interface EgressQueueRegistry {
    InternalEventQueue<NotificationFrame> getOrCreate(EgressBinding binding);
    Optional<InternalEventQueue<NotificationFrame>> find(String tenantId, String sessionId, String taskId);
    Optional<EgressBinding> findBinding(String tenantId, String sessionId, String taskId);
    void remove(String tenantId, String sessionId, String taskId);
}
```

| 方法 | 入参 | 返回值 | 描述 |
|---|---|---|---|
| `EgressAdapter.channel` | 无 | `ReplyChannel` | 声明出站适配器负责的回消息通道。 |
| `EgressAdapter.deliver` | `EgressBinding binding, NotificationFrame frame` | `void` | 将内部通知帧投递到具体外部通道。 |
| `EgressQueueRegistry.getOrCreate` | `EgressBinding binding` | `InternalEventQueue<NotificationFrame>` | 按交付绑定通过公共 `QueueManager.getOrCreate` 获取或初始化回消息队列。 |
| `EgressQueueRegistry.find` | `tenantId, sessionId, taskId` | `Optional<InternalEventQueue<NotificationFrame>>` | 查询已有回消息队列。 |
| `EgressQueueRegistry.findBinding` | `tenantId, sessionId, taskId` | `Optional<EgressBinding>` | 查询已有交付绑定。 |
| `EgressQueueRegistry.remove` | `tenantId, sessionId, taskId` | `void` | terminal 后清理队列索引。 |

L1 不继承 L3 队列，也不要求 L3 提供入队回调。L1 通过组合方式持有公共 IEQ 实例，由 `EgressDispatcher` 订阅 `stream()` 主动消费队列。

`NotificationPort.notify(frame)` 按 `tenantId + sessionId + taskId` 查找已有队列并入队。若队列不存在，第一版不自动创建，因为缺少 `replyChannel / deliveryMode / targetRef` 等交付信息，应抛出明确异常或记录投递失败。

---

## 8. 异步队列消费入口

```java
public interface AsyncIngressPort {
    void enqueue(AsyncEnvelope envelope);
}

public record AsyncEnvelope(
    AsyncHeaders headers,
    AsyncBody body
) {
    public record AsyncHeaders(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        AccessOperation operation,
        String idempotencyKey,
        String correlationId,
        String replyTopic
    ) {}

    public record AsyncBody(
        String query,
        Object payload
    ) {}
}
```

异步队列入口是 L1 对外开放的队列消费端口，不命名为 MQ；它可以由消息队列、事件总线或其他异步传输实现承载，当前仅保留端口和适配层，暂未绑定具体传输客户端。异步出站通过 `AsyncOutputSink` 接具体通道客户端。

### 8.1 异步入站字段契约

异步队列入站不是 A2A 标准协议，而是 L1 约定的异步消息信封。外部队列生产者需要发送 `AsyncEnvelope` 结构，L1 消费后直接归一为内部 `AccessIntent`。

示例：

```json
{
  "headers": {
    "tenantId": "tenant-001",
    "userId": "user-001",
    "agentId": "agent-001",
    "sessionId": "session-001",
    "operation": "SUBMIT",
    "idempotencyKey": "idem-001",
    "correlationId": "corr-001",
    "replyTopic": "agent.reply.tenant-001.session-001"
  },
  "body": {
    "query": "帮我规划一个三天上海行程",
    "payload": {
      "source": "event-bus",
      "raw": {}
    }
  }
}
```

| 字段路径 | 必填 | 类型 | 允许值 | 描述 | 内部映射 |
|---|---|---|---|---|---|
| `headers` | 是 | object | `AsyncHeaders` | 异步消息头，承载路由、身份、幂等和回消息信息。 | 解析为 `AsyncEnvelope.headers`。 |
| `headers.tenantId` | 是 | string | 租户 ID | 租户标识。 | `AccessIntent.tenantId`。 |
| `headers.userId` | 是 | string | 用户 ID | 用户标识。 | `AccessIntent.userId`。 |
| `headers.agentId` | 是 | string | Agent ID | 目标 Agent 标识。 | `AccessIntent.agentId`。 |
| `headers.sessionId` | 建议必填 | string | Session ID | 会话标识；异步场景通常需要用它绑定任务和回消息。 | `AccessIntent.sessionId`。 |
| `headers.operation` | 可选 | string / enum | `SUBMIT / RESUME / CANCEL / QUERY / SUBSCRIBE / CALLBACK` | 操作类型；缺省时按 `SUBMIT` 处理。 | `AccessIntent.operation`。 |
| `headers.idempotencyKey` | 可选 | string | 调用方自定义 | 幂等键。 | `AccessIntent.idempotencyKey`。 |
| `headers.correlationId` | 可选 | string | 调用方自定义 | 链路关联标识。 | `AccessIntent.payload.correlationId`，并进入 `EgressBinding.correlationId`。 |
| `headers.replyTopic` | 需要异步回消息时必填 | string | topic / queue / subject 名称 | 异步回消息目标。 | `AccessIntent.payload.replyTopic`，并作为 `EgressBinding.targetRef`。 |
| `body` | 是 | object | `AsyncBody` | 异步消息体。 | 解析为 `AsyncEnvelope.body`。 |
| `body.query` | 是 | string | 用户输入文本或规范化查询 | 本轮用户请求文本。 | `AccessIntent.query`。 |
| `body.payload` | 可选 | object / array / string / number / boolean | 调用方自定义 | 原始业务载荷或扩展参数。 | `AccessIntent.payload.payload`。 |

第一版建议外部异步生产者只使用 `SUBMIT` 和 `CANCEL`；其它 `AccessOperation` 枚举值保留给后续任务恢复、订阅、回调等能力扩展。

---

## 9. 核心流程

```text
A2A JSON-RPC / async ingress
  -> A2aEnvelope / AsyncEnvelope
  -> AccessIntent
  -> TaskHandler.runTask
  -> AccessAcceptedResponse
  -> EgressBinding + L3 reply queue

L4/L5
  -> NotificationPort.notify(NotificationFrame)
  -> L3 reply queue
  -> EgressDispatcher
  -> EgressAdapter(A2A / ASYNC)
```

`AccessGateway.bindEgress(...)` 在 L4 返回 `AccessAcceptedResponse` 后创建 `EgressBinding`。`taskId` 由 L4 返回，L1 不创建 Task，只用 `tenantId + sessionId + taskId` 绑定回消息队列和外部回包目标。

---

## 10. 文件职责

| 文件 | 职责 |
|---|---|
| `api/NotificationPort.java` | L1 暴露给 L4/L5 的通知端口。 |
| `core/AccessGateway.java` | L1 主编排器，负责入站归一、调用 L4、建立出站绑定。 |
| `core/TaskHandler.java` | L1 当前依赖的 L4 任务入口边界，后续与 L4 稳定接口对齐。 |
| `model/AccessIntent.java` | L1 内部统一请求对象。 |
| `model/AccessAcceptedResponse.java` | L4 接收结果对象，包含 task 标识。 |
| `model/AccessOperation.java` | 内部操作类型枚举。 |
| `model/ReplyChannel.java` | 出站通道类型。 |
| `model/NotificationFrame.java` | L4/L5 返回给用户的统一消息帧。 |
| `model/NotificationType.java` | 内部通知语义枚举。 |
| `model/EgressBinding.java` | 出站路由绑定。 |
| `egress/EgressAdapter.java` | 出站适配器接口。 |
| `egress/EgressQueueRegistry.java` | L3 回消息队列索引接口。 |
| `egress/DefaultEgressQueueRegistry.java` | 默认队列索引实现。 |
| `egress/DefaultNotificationPort.java` | `NotificationPort` 默认实现。 |
| `egress/EgressDispatcher.java` | 消费回消息队列并分发到对应 `EgressAdapter`。 |
| `egress/EgressDeliveryException.java` | 出站投递失败异常。 |
| `protocol/a2a/A2aJsonRpcController.java` | 标准 A2A JSON-RPC 单入口，使用 A2A SDK request/response 类型。 |
| `protocol/a2a/A2aWellKnownAgentCardController.java` | 暴露标准 `/.well-known/agent-card.json`。 |
| `protocol/a2a/A2aAccessService.java` | 控制器到 A2A 入站适配器之间的内部服务接口。 |
| `protocol/a2a/A2aEnvelope.java` | A2A 请求进入 L1 后的协议侧归一对象。 |
| `protocol/a2a/A2aAcceptedResponse.java` | A2A 入站接收确认的内部中间对象。 |
| `protocol/a2a/A2aIngressAdapter.java` | 将 A2A envelope 转为 `AccessIntent` 并调用 `AccessGateway`。 |
| `protocol/a2a/A2aEgressAdapter.java` | 将 `NotificationFrame` 映射为 A2A SDK 出站事件。 |
| `protocol/a2a/A2aTaskMapper.java` | 聚合 A2A 输出为 SDK `Task/Message/Artifact`。 |
| `protocol/a2a/A2aTaskQueryParams.java` | `GetTask` 查询所需参数。 |
| `protocol/a2a/A2aOutput.java` | A2A 出站输出缓存对象，持有 SDK event 和内部调试信息。 |
| `protocol/a2a/A2aOutputSink.java` | A2A 输出落点接口。 |
| `protocol/a2a/A2aOutputHandle.java` | A2A 输出索引键。 |
| `protocol/a2a/A2aOutputRegistry.java` | A2A 输出 registry，支持列表查询和 SSE 订阅。 |
| `protocol/a2a/DefaultA2aOutputSink.java` | A2A 输出默认实现，写入 registry；push notification 模式下同时 POST 到配置的 callback URL。 |
| `protocol/async/AsyncIngressPort.java` | 外部异步入站端口。 |
| `protocol/async/AsyncEnvelope.java` | 异步入站消息信封。 |
| `protocol/async/AsyncIngressAdapter.java` | 异步入站适配器。 |
| `protocol/async/AsyncEgressAdapter.java` | 异步出站适配器。 |
| `protocol/async/AsyncOutputSink.java` | 异步出站真实发送端口。 |
| `config/AccessLayerConfiguration.java` | L1 Spring 装配类，注册 SDK Agent Card、入口、出站、队列和临时 L4 handler。 |

---

## 11. 当前落地边界

- A2A 入口、Agent Card 和 JSON-RPC 响应类型已经使用 `org.a2aproject.sdk:a2a-java-sdk-server-common:1.0.0.CR1`。
- A2A 普通 JSON、SSE 流式、push notification 三种回消息模式均按标准 JSON-RPC 入口接入。
- L4/L5 不构造 A2A SDK 对象，只发送 `NotificationFrame`。
- L3 队列接口当前在代码中有临时占位，真实 L3 落地后应替换临时实现。
- `TaskHandler.runTask` 是 L1 到 L4 的唯一任务联动点；方法签名需要继续与 L4 文档和代码保持一致。

---

## 12. Postman 验证 A2A 三种模式

当前不再保留单独的临时 L1 启动类。验证时应随正式 Spring Boot 应用启动，或在后续正式装配完成后通过平台启动入口暴露 `/a2a/`。

```powershell
$env:JAVA_HOME='D:\Software\Java\jdk-21.0.11'
$env:Path='D:\Software\Java\jdk-21.0.11\bin;D:\Software\apache-maven-3.9.16\bin;' + $env:Path
mvn -pl agent-service spring-boot:run
```

公共地址：`POST http://localhost:8080/a2a/`。

### 12.1 普通 JSON 模式

Header：

```text
Content-Type: application/json
Accept: application/json
```

Body：

```json
{
  "jsonrpc": "2.0",
  "id": "req-send",
  "method": "SendMessage",
  "params": {
    "tenant": "tenant-001",
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-send",
      "contextId": "session-send",
      "parts": [
        {
          "kind": "text",
          "text": "hello from send mode"
        }
      ],
      "metadata": {
        "tenantId": "tenant-001",
        "userId": "user-001",
        "agentId": "agent-001",
        "sessionId": "session-send",
        "idempotencyKey": "idem-send",
        "correlationId": "corr-send"
      }
    }
  }
}
```

预期响应：HTTP 200；JSON-RPC `jsonrpc=2.0`，`id=req-send`，`result.role=ROLE_AGENT`，`result.taskId` 为 L4 返回的任务 ID。

### 12.2 SSE 流式模式

Header：

```text
Content-Type: application/json
Accept: text/event-stream
```

Body 与普通 JSON 模式一致，只把 `method` 改为 `SendStreamingMessage`。

预期响应：HTTP 200；`Content-Type` 包含 `text/event-stream`；响应体包含多帧 `event:jsonrpc`，第一帧是 accepted，后续帧是 L1 将 `NotificationFrame` 映射出的 A2A `TaskStatusUpdateEvent / Message / TaskArtifactUpdateEvent`。

### 12.3 Push Notification 模式

先准备一个能接收 HTTP POST 的 callback 服务，例如本地启动在 `http://localhost:9001/a2a/callback`。

Header：

```text
Content-Type: application/json
Accept: application/json
```

Body：

```json
{
  "jsonrpc": "2.0",
  "id": "req-push",
  "method": "SendMessage",
  "params": {
    "tenant": "tenant-001",
    "configuration": {
      "taskPushNotificationConfig": {
        "id": "push-config-001",
        "url": "http://localhost:9001/a2a/callback",
        "token": "test-token"
      }
    },
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-push",
      "contextId": "session-push",
      "parts": [
        {
          "kind": "text",
          "text": "hello from push mode"
        }
      ],
      "metadata": {
        "tenantId": "tenant-001",
        "userId": "user-001",
        "agentId": "agent-001",
        "sessionId": "session-push",
        "idempotencyKey": "idem-push",
        "correlationId": "corr-push"
      }
    }
  }
}
```

预期响应：调用 `/a2a/` 立即返回 HTTP 200 和 `SendMessageResponse` accepted；随后 callback 服务会收到 L1 POST 的 A2A 出站事件，Header 包含 `Content-Type: application/json`，如配置了 `token` 还会包含 `X-A2A-Notification-Token`。
