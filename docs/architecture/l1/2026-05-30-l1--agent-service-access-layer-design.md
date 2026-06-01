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
| `AccessIntent` | `agentId` | 目标 Agent 标识，用于下游选择 Agent 或能力。 |
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
| `EgressBinding` | `deliveryMode` | A2A 场景当前取 `SYNC` 或 `STREAM`，异步通道取 `ASYNC`。A2A push notification 后续如接入，应使用标准 A2A push config，不新增自定义 `reply.mode`。 |
| `EgressBinding` | `targetRef` | 具体交付目标；流式 A2A 当前可标记为 `sse`，异步通道为 reply topic/queue。 |
| `EgressBinding` | `correlationId` | 外部请求关联标识。 |

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
| `GetTask` | `GetTaskRequest` | 按 `tenantId + sessionId + taskId` 查询 L1 出站 registry，并聚合为 SDK `Task`。 | `GetTaskResponse` |
| `CancelTask` | `CancelTaskRequest` | 转为 `AccessOperation.CANCEL` 并透传给 L4。 | `CancelTaskResponse` |

第一版不实现 A2A push notification 配置方法。后续如需要离线回调，应按 A2A 标准 push notification config 扩展，而不是在请求体里自定义 `reply.mode / reply.target`。

### 5.1 A2A 请求体约定

外部请求体必须是 A2A JSON-RPC 结构，不是内部 `AccessIntent`。

`SendMessage` 示例：

```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "method": "SendMessage",
  "params": {
    "message": {
      "role": "user",
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

`A2aOutput` 当前保留 `kind/body/metadata` 便于内部调试，同时新增持有 SDK `StreamingEventKind event`，真实 A2A SSE 输出使用 `event` 构造 `SendStreamingMessageResponse`。

`GetTask` 不直接返回 `A2aOutput` 列表，而是通过 `A2aTaskMapper` 将 registry 中的输出聚合为 SDK `Task`，包含 status、history 和 artifacts。

---

## 7. 出站队列与通知

```java
public interface EgressAdapter {
    ReplyChannel channel();
    void deliver(EgressBinding binding, NotificationFrame frame);
}

public interface EgressQueueRegistry {
    Queue getOrCreate(EgressBinding binding);
    Optional<Queue> find(String tenantId, String sessionId, String taskId);
    Optional<EgressBinding> findBinding(String tenantId, String sessionId, String taskId);
    void remove(String tenantId, String sessionId, String taskId);
}
```

| 方法 | 入参 | 返回值 | 描述 |
|---|---|---|---|
| `EgressAdapter.channel` | 无 | `ReplyChannel` | 声明出站适配器负责的回消息通道。 |
| `EgressAdapter.deliver` | `EgressBinding binding, NotificationFrame frame` | `void` | 将内部通知帧投递到具体外部通道。 |
| `EgressQueueRegistry.getOrCreate` | `EgressBinding binding` | `Queue` | 按交付绑定获取或通过 L3 `QueueFactory.createQueue` 创建回消息队列。 |
| `EgressQueueRegistry.find` | `tenantId, sessionId, taskId` | `Optional<Queue>` | 查询已有回消息队列。 |
| `EgressQueueRegistry.findBinding` | `tenantId, sessionId, taskId` | `Optional<EgressBinding>` | 查询已有交付绑定。 |
| `EgressQueueRegistry.remove` | `tenantId, sessionId, taskId` | `void` | terminal 后清理队列索引。 |

L1 不继承 L3 队列，也不要求 L3 提供入队回调。L1 通过组合方式持有 L3 `QueueFactory.createQueue(...)` 创建出来的队列实例，由 `EgressDispatcher` 主动消费队列。

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
| `protocol/a2a/DefaultA2aOutputSink.java` | A2A 输出默认实现，写入 registry。 |
| `protocol/async/AsyncIngressPort.java` | 外部异步入站端口。 |
| `protocol/async/AsyncEnvelope.java` | 异步入站消息信封。 |
| `protocol/async/AsyncIngressAdapter.java` | 异步入站适配器。 |
| `protocol/async/AsyncEgressAdapter.java` | 异步出站适配器。 |
| `protocol/async/AsyncOutputSink.java` | 异步出站真实发送端口。 |
| `config/AccessLayerConfiguration.java` | L1 Spring 装配类，注册 SDK Agent Card、入口、出站、队列和临时 L4 handler。 |

---

## 11. 当前落地边界

- A2A 入口、Agent Card 和 JSON-RPC 响应类型已经使用 `org.a2aproject.sdk:a2a-java-sdk-server-common:1.0.0.CR1`。
- A2A push notification 暂不实现；后续需要时按标准 push config 方法扩展。
- L4/L5 不构造 A2A SDK 对象，只发送 `NotificationFrame`。
- L3 队列接口当前在代码中有临时占位，真实 L3 落地后应替换临时实现。
- `TaskHandler.runTask` 是 L1 到 L4 的唯一任务联动点；方法签名需要继续与 L4 文档和代码保持一致。

