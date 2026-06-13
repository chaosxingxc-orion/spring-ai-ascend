---
level: L2
module: agent-runtime
feature: a2a-external-access-protocol
---

# A2A 对外访问协议特性

本文描述 `agent-runtime` 当前已经实现的 A2A 对外访问能力。这里的“对外”指外部客户端通过 HTTP 发现、调用、观察、查询和取消 runtime 托管的业务 Agent；本文只写当前代码和测试已经支持的行为，不写未来规划。

当前 `agent-runtime` 对外暴露一个顶层业务 Agent。调用方先读取 Agent Card，再通过 `/a2a` 使用 A2A SDK JSON-RPC 方法提交消息、接收 SSE、查询 task、取消 task，以及管理 task push notification 配置。

## 已实现特性清单

| 特性 | 对外形态 | 实现状态 |
|---|---|---|
| Agent 发现 | `GET /.well-known/agent-card.json`，兼容 `GET /.well-known/agent.json` | 已实现，入口是 `AgentCardController`。 |
| JSON-RPC 单入口 | `POST /a2a` 和 `POST /a2a/` | 已实现，入口是 `A2aJsonRpcController`。 |
| 普通消息调用（未完整实现） | `SendMessage` + `Accept: application/json` | 当前代码可解析并分发，但暂不作为完整对外调用方式支持；真实 Agent 全流程适配以 `SendStreamingMessage` 为准。 |
| 流式消息调用 | `SendStreamingMessage` + `Accept: text/event-stream` | 已实现，分发到 `RequestHandler#onMessageSendStream`。 |
| 任务流重新订阅 | `SubscribeToTask` + `Accept: text/event-stream` | 已实现，分发到 `RequestHandler#onSubscribeToTask`。 |
| 任务查询 | `GetTask` + `Accept: application/json` | 已实现，分发到 `RequestHandler#onGetTask`。 |
| 任务列表 | `ListTasks` + `Accept: application/json` | 已实现，分发到 `RequestHandler#onListTasks`。 |
| 任务取消 | `CancelTask` + `Accept: application/json` | 已实现，分发到 `RequestHandler#onCancelTask`，执行侧由 `A2aAgentExecutor#cancel` 下传。 |
| Push 配置管理 | `CreateTaskPushNotificationConfig`、`GetTaskPushNotificationConfig`、`ListTaskPushNotificationConfigs`、`DeleteTaskPushNotificationConfig` | 已实现，分发到 A2A SDK request handler，默认使用 SDK push 组件。 |

当前仓库使用的 A2A SDK 依赖版本是 `1.0.0.CR1`，其 `A2AMethods` 常量使用 PascalCase 方法名，例如 `SendMessage`、`SendStreamingMessage`、`GetTask`。外部报文必须按当前 SDK parser 可识别的方法名发送；`message/send`、`message/stream`、`tasks/get`、`tasks/cancel`、`tasks/resubscribe` 不是当前 controller 能解析并分发的方法名。

## Agent Card 发现

调用方应先读取 Agent Card：

```http
GET /.well-known/agent-card.json
Accept: application/json
```

兼容路径：

```http
GET /.well-known/agent.json
Accept: application/json
```

`AgentCardController` 会根据请求的 scheme、host、port，把 card 中的相对 URL 解析为绝对 URL。调用方应优先使用 `card.url` 或 `supportedInterfaces[].url`，不要在客户端硬编码 `/a2a`。

## JSON-RPC 访问入口

所有 task 交互都进入同一个 HTTP endpoint：

```http
POST /a2a
Content-Type: application/json
Accept: application/json
```

尾斜杠路径也已支持：

```http
POST /a2a/
```

流式调用使用 SSE：

```http
POST /a2a
Content-Type: application/json
Accept: text/event-stream
```

`A2aJsonRpcController` 使用 A2A SDK `JSONRPCUtils.parseRequestBody(...)` 解析请求体。`SendStreamingMessage` 和 `SubscribeToTask` 走流式分支；其他已支持的 request wrapper 走阻塞 JSON 分支。

## 请求体字段约束

下面的字段约束来自当前代码的解析和消费路径：JSON-RPC envelope 由 A2A SDK request wrapper 校验，消息执行由 `A2aAgentExecutor` 消费，task 与 push 操作由 SDK `RequestHandler` 消费。

### JSON-RPC envelope

| 字段 | 必填 | 当前行为 |
|---|---|---|
| `jsonrpc` | 建议填写 | JSON-RPC 协议字段，示例固定写 `"2.0"`；当前 SDK 构造路径在未提供时会默认 `"2.0"`，但提供其他值会校验失败。 |
| `id` | 建议填写 | SDK 支持 string、integer 或 null。controller 会把 request id 回写到 JSON-RPC response；为了排查和客户端关联，建议始终传 string。 |
| `method` | 是 | 必须是当前 SDK parser 能识别、且 controller 已分发的方法名。 |
| `params` | 是 | 类型随 `method` 变化；缺失或 shape 不匹配会进入 JSON-RPC error。 |

### `SendMessage` 和 `SendStreamingMessage` 参数

这两个方法都使用 `MessageSendParams`，当前 runtime 执行侧只把用户消息中的 `TextPart` 作为输入文本，其他 part 类型会被忽略并记录 WARN。`SendStreamingMessage` 是当前对外推荐的完整调用方式；`SendMessage` 只说明当前代码真实行为，暂不承诺作为真实 Agent 的完整对外调用能力。

| 字段 | 必填 | 当前行为 |
|---|---|---|
| `params.message` | 是 | SDK `MessageSendParams` 强校验非空。 |
| `params.message.role` | 是 | SDK `Message` 强校验非空；执行侧不按 role 分支处理，外部用户消息建议传 `ROLE_USER`。 |
| `params.message.parts` | 是 | SDK `Message` 强校验非空且不能为空。 |
| `params.message.parts[].text` | 至少应有一个 | runtime 只消费 `TextPart.text`，多个 text part 用换行拼接；如果只有非文本 part，执行输入会变成空字符串。 |
| `params.message.messageId` | 是 | SDK `Message` 强校验非空。Java builder 未设置时会自动生成；裸 JSON 报文应显式传入。 |
| `params.message.contextId` | 否 | SDK `RequestContext` 优先用它作为会话上下文；未传时生成 context id。`A2aAgentExecutor` 用 `contextId` 作为 `RuntimeIdentity.sessionId`。 |
| `params.message.taskId` | 否 | 用于继续已有 task。SDK builder 会校验 message task id 与上下文 task id 一致。新请求通常不传。 |
| `params.message.referenceTaskIds` | 否 | SDK `SimpleRequestContextBuilder` 可据此加载相关 task；runtime 执行桥不直接消费。 |
| `params.message.metadata` | 否 | runtime 合入 `AgentExecutionContext.variables`，并且同名键覆盖 `params.metadata`。 |
| `params.message.extensions` | 否 | SDK 模型支持；当前 runtime 执行桥不直接消费。 |
| `params.configuration` | 否 | SDK handler 支持消息发送配置；当前 runtime 执行桥可通过 `RequestContext#getConfiguration()` 暴露，但 `A2aAgentExecutor` 不直接读取它。 |
| `params.metadata` | 否 | runtime 合入变量；`userId`、`agentId` 会被 `A2aAgentExecutor` 用于构造 `RuntimeIdentity`。 |
| `params.tenant` | 否 | SDK 模型要求非 null，builder/便捷构造会默认空串。runtime 租户优先取 `X-Tenant-Id`，其次取 `params.tenant`，最后回退到 `"default"`。 |

### Task 操作参数

| 方法 | 字段 | 必填 | 当前行为 |
|---|---|---|---|
| `GetTask` | `params.id` | 是 | SDK `TaskQueryParams` 强校验非空，分发到 `RequestHandler#onGetTask`。 |
| `GetTask` | `params.historyLength` | 否 | SDK 支持，必须非负；是否返回历史由 SDK handler/task store 处理。 |
| `GetTask` | `params.tenant` | 否 | SDK 模型要求非 null，builder 默认空串；runtime controller 不用它做鉴权。 |
| `CancelTask` | `params.id` | 是 | SDK `CancelTaskParams` 强校验非空，分发到 `RequestHandler#onCancelTask`。 |
| `CancelTask` | `params.metadata` | 否 | SDK 模型支持取消元数据；当前 `A2aAgentExecutor#cancel` 不直接消费。 |
| `CancelTask` | `params.tenant` | 否 | SDK 模型要求非 null，builder 默认空串；runtime 租户仍以 `X-Tenant-Id` 为优先。 |
| `SubscribeToTask` | `params.id` | 是 | SDK `TaskIdParams` 强校验非空，分发到 `RequestHandler#onSubscribeToTask`。 |
| `SubscribeToTask` | `params.tenant` | 否 | SDK 模型要求非 null，builder 默认空串；runtime controller 不用它做鉴权。 |
| `ListTasks` | `params` | 是，允许 `{}` | SDK `ListTasksParams` 支持空参数对象。 |
| `ListTasks` | `params.contextId` | 否 | SDK 支持按 context 过滤。 |
| `ListTasks` | `params.status` | 否 | SDK 支持按 `TaskState` 过滤。 |
| `ListTasks` | `params.pageSize` | 否 | SDK 校验范围是 1 到 100，默认 50。 |
| `ListTasks` | `params.pageToken` | 否 | SDK 支持分页 token。 |
| `ListTasks` | `params.historyLength` | 否 | SDK 校验非负，默认 0。 |
| `ListTasks` | `params.statusTimestampAfter` | 否 | SDK 支持按更新时间过滤。 |
| `ListTasks` | `params.includeArtifacts` | 否 | SDK 支持控制是否返回 artifacts，默认 false。 |
| `ListTasks` | `params.tenant` | 否 | SDK 模型要求非 null，builder 默认空串；空 JSON 参数会解析为默认空串。 |

### Push 配置参数

Push 配置请求当前由 controller 分发到 SDK `RequestHandler`，参数 shape 以 A2A SDK 模型为准。仓库测试覆盖了以下最小可用形态：

| 方法 | 最小参数 | 当前行为 |
|---|---|---|
| `CreateTaskPushNotificationConfig` | `TaskPushNotificationConfig`，包含 `id`、`taskId`、`url`，可带 token 等字段 | 分发到 `onCreateTaskPushNotificationConfig`。 |
| `GetTaskPushNotificationConfig` | `taskId`、`pushNotificationConfigId` | 分发到 `onGetTaskPushNotificationConfig`。 |
| `ListTaskPushNotificationConfigs` | `taskId` | 分发到 `onListTaskPushNotificationConfigs`。 |
| `DeleteTaskPushNotificationConfig` | `taskId`、`pushNotificationConfigId` | 分发到 `onDeleteTaskPushNotificationConfig`。 |

## 消息体示例

以下示例用 `jsonc` 标注字段含义和必填性，真实 HTTP 请求体必须去掉 `//` 注释。

### 阻塞消息调用（未完整实现，暂不作为完整对外能力支持）

`SendMessage` 当前可以被 SDK parser 解析，并由 controller 分发到 `RequestHandler#onMessageSend`，但本文将它标注为暂不支持完整对外调用。原因是当前真实 Agent 主链路按 stream 适配和验证，`SendMessage` 只是走 A2A SDK 的 blocking 聚合路径，不是 runtime 当前推荐的完整调用入口；返回语义见“已实现特性清单”后的备注。

```jsonc
{
  "jsonrpc": "2.0",              // 必填：JSON-RPC 版本
  "id": "request-1",             // 建议填写：响应会原样回传
  "method": "SendMessage",       // 必填：当前 SDK/parser 可解析；暂不作为完整对外调用入口
  "params": {                    // 必填
    "message": {                 // 必填：MessageSendParams.message
      "role": "ROLE_USER",       // 必填：SDK Message.role
      "parts": [                 // 必填且非空：runtime 只消费 text part
        { "text": "ping" }       // 建议至少一个：多个 text part 会用换行拼接
      ],
      "messageId": "message-1",  // 必填：裸 JSON 报文需显式传入
      "contextId": "session-1",  // 可选：作为 runtime sessionId；不传则由 SDK 生成
      "metadata": {              // 可选：合入变量，并覆盖 params.metadata 同名键
        "intent": "demo"
      }
    },
    "metadata": {                // 可选：合入变量；userId/agentId 会参与 RuntimeIdentity
      "userId": "user-1",
      "agentId": "agent"
    },
    "tenant": ""                 // 可选：SDK 模型字段；runtime 优先使用 X-Tenant-Id
  }
}
```

### 流式消息调用

`SendStreamingMessage` 返回 `text/event-stream`。每一帧 SSE 的 `event` 为 `jsonrpc`，`data` 是 JSON-RPC response envelope，其中 `result` 是 A2A SDK `StreamingEventKind`。

如果 `params.configuration.taskPushNotificationConfig` 随 `SendStreamingMessage` 一起传入，当前 SSE 连接返回的事件流形态不变；区别只是 SDK 会额外保存该 task 的 push 配置，后续 task 事件可由 `PushNotificationSender` 异步推送到 callback URL。push 配置是旁路通知通道，不替代 SSE response。

```jsonc
{
  "jsonrpc": "2.0",                    // 必填
  "id": "stream-1",                    // 建议填写：每帧 JSON-RPC data 会回传该 id
  "method": "SendStreamingMessage",    // 必填
  "params": {
    "message": {
      "role": "ROLE_USER",             // 必填
      "parts": [
        { "text": "ping" }             // runtime 实际输入文本
      ],
      "messageId": "message-2",        // 必填
      "contextId": "session-1"         // 可选
    },
    "metadata": {
      "userId": "user-1",              // 可选
      "agentId": "agent"               // 可选
    }
  }
}
```

如果流已经开始后发生异常，controller 会输出一帧 JSON-RPC error，而不是直接断开连接。

### 查询 task

```jsonc
{
  "jsonrpc": "2.0",                          // 必填
  "id": "get-1",                             // 建议填写
  "method": "GetTask",                       // 必填
  "params": {
    "id": "task-id-from-previous-response",  // 必填
    "historyLength": 10                      // 可选：必须非负
  }
}
```

### 重新订阅 task 事件流

```jsonc
{
  "jsonrpc": "2.0",                          // 必填
  "id": "sub-1",                             // 建议填写
  "method": "SubscribeToTask",               // 必填，SSE 分支
  "params": {
    "id": "task-id-from-previous-response"   // 必填
  }
}
```

### 取消 task

```jsonc
{
  "jsonrpc": "2.0",                          // 必填
  "id": "cancel-1",                          // 建议填写
  "method": "CancelTask",                    // 必填
  "params": {
    "id": "task-id-from-previous-response",  // 必填
    "metadata": {                            // 可选：SDK 支持，当前执行桥不直接消费
      "reason": "client-request"
    }
  }
}
```

### 列出 task

```jsonc
{
  "jsonrpc": "2.0",              // 必填
  "id": "list-1",                // 建议填写
  "method": "ListTasks",         // 必填
  "params": {                    // 必填，允许空对象
    "contextId": "session-1",    // 可选
    "pageSize": 20,              // 可选：1 到 100
    "historyLength": 0,          // 可选：非负
    "includeArtifacts": false    // 可选
  }
}
```

## Task 生命周期与取消

对外 task 生命周期使用 A2A SDK 的 task 生命周期。`RuntimeAutoConfiguration` 默认装配以下 SDK 组件：

| 组件 | 默认实现 |
|---|---|
| Task store | `InMemoryTaskStore` |
| Event bus | `MainEventBus` |
| Queue manager | `InMemoryQueueManager` |
| Request handler | `DefaultRequestHandler` |
| Push config store | `InMemoryPushNotificationConfigStore` |
| Push sender | `BasePushNotificationSender` |

`A2aAgentExecutor` 把 SDK `AgentExecutor` 桥接到 runtime `AgentRuntimeHandler`：执行时提交 task、进入 working、调用 handler、把结果映射回 SDK emitter，并记录 in-flight execution。`CancelTask` 到达后，executor 会调用 `handler.cancel(taskId)`，并关闭正在执行的结果流。

## Tenant Header 行为

`A2aJsonRpcController` 接收 `X-Tenant-Id`。如果请求头存在，trim 后的值会写入 SDK `ServerCallContext`；如果不存在，则使用 `RuntimeAccessProperties` 中配置的默认 tenant。

runtime 自身不认证这个 header。多租户部署必须在 `/a2a` 前放认证网关，由网关剥离客户端自带的 tenant header，并重新注入可信 tenant。

## 错误行为

当前实现已经覆盖以下错误路径：

| 情况 | 对外结果 |
|---|---|
| 请求体不是合法 JSON | JSON-RPC parse error response。 |
| method 未知 | JSON-RPC method-not-found response。 |
| SDK 能解析但 controller 未分发的方法 | JSON-RPC method-not-found response。 |
| 阻塞分支 SDK 抛出 `A2AError` | 带原 request id 的 JSON-RPC error response。 |
| SSE 请求解析失败 | 一帧 SSE JSON-RPC error。 |
| SSE 分支同步抛出 SDK 错误 | 一帧 SSE JSON-RPC error。 |
| 流已经开始后失败 | 末尾输出一帧 SSE JSON-RPC error。 |

## 用 A2A Java Client 测试

Java 调用方推荐先用 `A2ACardResolver` 读取 Agent Card，再用 `JSONRPCTransport` 调用 card 中的 JSON-RPC endpoint。下面示例与当前 SDK 调用方式一致。

Maven 依赖需要包含：

```xml
<dependency>
  <groupId>org.a2aproject.sdk</groupId>
  <artifactId>a2a-java-sdk-http-client</artifactId>
</dependency>
<dependency>
  <groupId>org.a2aproject.sdk</groupId>
  <artifactId>a2a-java-sdk-client-transport-jsonrpc</artifactId>
</dependency>
```

通用初始化：

```java
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.TextPart;

URI runtimeBaseUri = URI.create("http://localhost:8080");
AgentCard card = new A2ACardResolver(runtimeBaseUri.toString()).getAgentCard();
JSONRPCTransport transport = new JSONRPCTransport(card);
ClientCallContext callContext = new ClientCallContext(Map.of(), Map.of());

String sessionId = "session-1";

Message message = Message.builder()
        .role(Message.Role.ROLE_USER)
        .messageId(UUID.randomUUID().toString())
        .contextId(sessionId)
        .metadata(Map.of(
                "userId", "user-1",
                "agentId", "agent",
                "sessionId", sessionId))
        .parts(List.of(new TextPart("ping")))
        .build();

MessageSendParams params = MessageSendParams.builder()
        .message(message)
        .metadata(Map.of(
                "userId", "user-1",
                "agentId", "agent"))
        .build();
```

阻塞调用：

```java
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;

EventKind result = transport.sendMessage(params, callContext);

if (result instanceof Task task) {
    System.out.println("taskId=" + task.id());
    System.out.println("state=" + task.status().state());
    if (task.status().message() != null) {
        System.out.println("message=" + task.status().message());
    }
} else if (result instanceof Message agentMessage) {
    System.out.println("message=" + agentMessage);
}
```

流式调用：

```java
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;

List<StreamingEventKind> events = new ArrayList<>();
CountDownLatch done = new CountDownLatch(1);
AtomicReference<Throwable> failure = new AtomicReference<>();

transport.sendMessageStreaming(
        params,
        event -> {
            events.add(event);
            if (event instanceof TaskArtifactUpdateEvent artifactEvent) {
                System.out.println("artifact=" + artifactEvent.artifact());
            }
            if (event instanceof TaskStatusUpdateEvent statusEvent
                    && statusEvent.status() != null
                    && statusEvent.status().state() != null) {
                TaskState state = statusEvent.status().state();
                if (state == TaskState.TASK_STATE_COMPLETED
                        || state == TaskState.TASK_STATE_FAILED
                        || state == TaskState.TASK_STATE_CANCELED
                        || state == TaskState.TASK_STATE_REJECTED) {
                    done.countDown();
                }
            }
        },
        error -> {
            failure.set(error);
            done.countDown();
        },
        callContext);

if (!done.await(Duration.ofSeconds(60).toMillis(), TimeUnit.MILLISECONDS)) {
    throw new IllegalStateException("A2A stream did not complete before timeout");
}
if (failure.get() != null) {
    throw new RuntimeException("A2A stream failed", failure.get());
}

System.out.println("events=" + events.size());
```

重新订阅已有 task：

```java
import org.a2aproject.sdk.spec.TaskIdParams;

String taskId = "task-id-from-previous-response";

transport.subscribeToTask(
        new TaskIdParams(taskId),
        event -> System.out.println("event=" + event),
        error -> System.err.println("subscribe failed: " + error.getMessage()),
        callContext);
```

如果需要完整可运行参考，可以看当前示例里的 `SampleA2aClient`：`examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/SampleA2aClient.java`。

## 备注：SendMessage 返回语义

`SendMessage` 当前可以被 SDK parser 解析，并由 controller 分发到 `RequestHandler#onMessageSend`，但本文将它标注为暂不支持完整对外调用。它不会返回第一条消息后直接断连，而是启动同一套 `A2aAgentExecutor`，把 `OUTPUT` 写成 task artifact，把 `COMPLETED` / `FAILED` / `INPUT_REQUIRED` 写成 task status，再由 SDK `DefaultRequestHandler#onMessageSend` 聚合为一个 JSON-RPC response。

默认情况下，`params.configuration.returnImmediately` 未传或为 `false`，SDK 会走 blocking 聚合。返回体通常是 JSON-RPC response，`result` 是当前或最终 `Task` 快照。

如果一段时间内执行完成，返回 `TASK_STATE_COMPLETED`。最终文本在 `status.message.parts`，中间输出在 `artifacts[].parts`：

```json
{
  "jsonrpc": "2.0",
  "id": "request-1",
  "result": {
    "id": "task-1",
    "contextId": "session-1",
    "status": {
      "state": "TASK_STATE_COMPLETED",
      "message": {
        "role": "ROLE_AGENT",
        "parts": [
          { "text": "final answer" }
        ],
        "messageId": "message-from-runtime"
      }
    },
    "artifacts": [
      {
        "artifactId": "task-1-response",
        "name": "agent-response",
        "parts": [
          { "text": "streamed chunk" }
        ]
      }
    ]
  }
}
```

如果一段时间内进入 input required，返回 `TASK_STATE_INPUT_REQUIRED`。提示文本在 `status.message.parts`：

```json
{
  "jsonrpc": "2.0",
  "id": "request-1",
  "result": {
    "id": "task-1",
    "contextId": "session-1",
    "status": {
      "state": "TASK_STATE_INPUT_REQUIRED",
      "message": {
        "role": "ROLE_AGENT",
        "parts": [
          { "text": "please provide more information" }
        ],
        "messageId": "message-from-runtime"
      }
    },
    "artifacts": []
  }
}
```

如果 agent 执行超过 SDK blocking agent 等待时间，这个等待超时本身不会直接变成 JSON-RPC error。SDK 会继续返回当时能拿到的 task 快照，可能仍是 `TASK_STATE_WORKING`，并只包含已经消费到的 artifacts；后续执行仍可能继续推进，但不会再写入这次 `SendMessage` 的 HTTP response：

```json
{
  "jsonrpc": "2.0",
  "id": "request-1",
  "result": {
    "id": "task-1",
    "contextId": "session-1",
    "status": {
      "state": "TASK_STATE_WORKING"
    },
    "artifacts": [
      {
        "artifactId": "task-1-response",
        "name": "agent-response",
        "parts": [
          { "text": "partial chunk" }
        ]
      }
    ]
  }
}
```

如果 agent 已经返回，但事件消费/持久化等待超时，controller 会返回 JSON-RPC error，而不是 `Task`：

```json
{
  "jsonrpc": "2.0",
  "id": "request-1",
  "error": {
    "code": -32603,
    "message": "Timeout waiting for task task-1 consumption"
  }
}
```

等待时间由 A2A SDK 配置控制，默认值来自 `META-INF/a2a-defaults.properties`：

| 配置项 | 默认值 | 含义 |
|---|---:|---|
| `a2a.blocking.agent.timeout.seconds` | `30` | blocking `SendMessage` 等待 agent 执行完成的时间。超时后可返回当前 task 快照。 |
| `a2a.blocking.consumption.timeout.seconds` | `5` | 等待事件消费/持久化完成的时间。超时会返回 JSON-RPC error。 |

当前 Spring 装配默认使用 SDK `DefaultValuesConfigProvider`。如需调整等待时间，应覆盖 `A2AConfigProvider` bean，返回新的配置值。
