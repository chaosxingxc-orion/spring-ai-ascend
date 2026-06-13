---
level: L2
view: scenarios
status: current-code-summary
parent_l1: architecture/docs/L1/agent-runtime/README.md
authority:
  - architecture/facts/generated/code-symbols.json
  - architecture/facts/generated/runtime-config.json
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/A2aJsonRpcController.java
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/AgentCardController.java
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/RuntimeAutoConfiguration.java
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a/A2aAgentExecutor.java
---

# agent-runtime 对外 A2A 调用方式设计说明

本文基于当前 `agent-runtime` 代码实现说明对外暴露的 A2A 调用方式。它描述调用方能看到的 HTTP 入口、JSON-RPC 方法、三种任务交互模式、请求报文、返回报文、状态语义和当前实现边界。

当前实现的外部事实源如下：

| 能力 | 当前实现 | 事实 ID |
|---|---|---|
| Agent Card 发现 | `GET /.well-known/agent-card.json` 和兼容路径 `GET /.well-known/agent.json` | `code-symbol/com-huawei-ascend-runtime-boot-agentcardcontroller` |
| A2A JSON-RPC 入口 | `POST /a2a` 和 `POST /a2a/` | `code-symbol/com-huawei-ascend-runtime-boot-a2ajsonrpccontroller` |
| A2A SDK 底座 | `RuntimeAutoConfiguration` 装配 `InMemoryTaskStore`、`InMemoryQueueManager`、`DefaultRequestHandler`、`BasePushNotificationSender` | `code-symbol/com-huawei-ascend-runtime-boot-runtimeautoconfiguration` |
| Agent 执行桥 | `A2aAgentExecutor` 将 A2A `RequestContext` 转为 `AgentExecutionContext`，再把 `AgentExecutionResult` 投影成 A2A task/message/artifact/status | `code-symbol/com-huawei-ascend-runtime-engine-a2a-a2aagentexecutor` |

## 1. 对外入口总览

### 1.1 Agent Card 发现

调用方先读取 Agent Card：

```http
GET /.well-known/agent-card.json
Accept: application/json
```

兼容路径：

```http
GET /.well-known/agent.json
Accept: application/json
```

当前 `AgentCardController` 会按请求的 scheme、host、port 把 card 内的相对 URL 解析为绝对 URL。默认 card 由 `RuntimeAutoConfiguration#a2aAgentCard` 生成；如果业务方提供 `AgentCardProvider` bean，则以业务方提供的 card 为准。

默认 card 语义：

```json
{
  "name": "agent",
  "description": "agent-runtime",
  "url": "http://localhost:8080/a2a",
  "version": "0.1.0",
  "capabilities": {
    "streaming": true,
    "pushNotifications": true
  },
  "defaultInputModes": ["text"],
  "defaultOutputModes": ["text"],
  "supportedInterfaces": [
    {
      "protocolBinding": "JSONRPC",
      "url": "http://localhost:8080/a2a"
    }
  ]
}
```

如果使用 `AgentCards.create(...)` 构造 card，`defaultOutputModes` 会包含 `text` 和 `artifact`，并设置 `preferredTransport=JSONRPC`。调用方不应硬编码 `/a2a`，应优先以 card 的 `url` 或 `supportedInterfaces[].url` 为准。

### 1.2 JSON-RPC 单入口

所有 A2A 任务交互都进入：

```http
POST /a2a
Content-Type: application/json
Accept: application/json 或 text/event-stream
```

也支持：

```http
POST /a2a/
```

`A2aJsonRpcController` 的分流规则：

| 请求类型 | HTTP 返回介质 | controller 行为 |
|---|---|---|
| `SendMessage`、`GetTask`、`CancelTask`、push config 管理方法 | `application/json` | 调 `handleBlocking(...)`，返回一个 JSON-RPC response |
| `SendStreamingMessage`、`SubscribeToTask` | `text/event-stream` | 调 `handleStream(...)`，每条 SSE `event=jsonrpc`，`data` 是一条 JSON-RPC response |

当前 SDK 常量使用 PascalCase 方法名，例如 `SendMessage`、`SendStreamingMessage`、`GetTask`。部分 README 或协议草案里会写成 `message/send`、`message/stream`、`tasks/get`、`tasks/cancel`；调用当前 `agent-runtime` 时，以 SDK wrapper 可解析的方法名为准。

## 2. 三种调用方式对比

| 调用方式 | A2A 方法 | HTTP 返回 | 适合场景 | 客户端等待模型 | 任务状态来源 |
|---|---|---|---|---|---|
| 普通阻塞 JSON 调用 | `SendMessage` | 单个 JSON-RPC response | 短任务、简单问答、调用方只关心最终或当前可返回结果 | HTTP 请求保持到 SDK handler 返回 | A2A SDK `TaskStore` / `EventKind` |
| 流式 SSE 调用 | `SendStreamingMessage`，后续可用 `SubscribeToTask` | SSE 多帧，每帧一个 JSON-RPC response | 长任务、需要进度/中间产物/增量输出、交互式 UI | 客户端保持 SSE 连接直到 terminal event 或主动断开 | A2A SDK `QueueManager` / event stream |
| Push Notification 异步回调 | `SendMessage` 携带 `configuration.taskPushNotificationConfig`，或先调用 push config 管理方法 | 首次请求返回 JSON-RPC response；后续由 runtime POST 到回调 URL | 调用方不适合保持长连接、后台任务、服务端集成 | 首次 HTTP 请求返回后，调用方等待 callback 或轮询 `GetTask` | A2A SDK `PushNotificationConfigStore` + `BasePushNotificationSender` |

配套但不单独算一种“调用方式”的方法：

| 方法 | 用途 |
|---|---|
| `GetTask` | 查询已有 task 的当前状态、历史和 artifacts。 |
| `CancelTask` | 取消已有 task，当前 `A2aAgentExecutor#cancel` 投影为 A2A canceled task。 |
| `SubscribeToTask` | 对已有 task 重新打开 SSE 订阅。 |
| `CreateTaskPushNotificationConfig`、`GetTaskPushNotificationConfig`、`ListTaskPushNotificationConfigs`、`DeleteTaskPushNotificationConfig` | 管理 task 级 push callback 配置。 |

## 3. 通用请求模型

所有请求都是 JSON-RPC 2.0：

```json
{
  "jsonrpc": "2.0",
  "id": "request-1",
  "method": "SendMessage",
  "params": {}
}
```

`SendMessage` 和 `SendStreamingMessage` 的 `params` 是 A2A SDK `MessageSendParams`：

| 字段 | 必填 | 说明 |
|---|---|---|
| `message` | 是 | 用户消息。当前 runtime 只从 `parts[]` 中的 text part 抽取文本送入 agent。 |
| `configuration` | 否 | 输出模式、历史长度、push notification、是否立即返回等配置。 |
| `metadata` | 否 | 请求级 metadata。 |
| `tenant` | 否 | 租户标识。当前 `A2aAgentExecutor` 优先从 SDK `RequestContext#getTenant()` 取 tenant，否则使用默认值。 |

`message` 常用字段：

| 字段 | 必填 | 说明 |
|---|---|---|
| `role` | 是 | 调用方消息使用 `ROLE_USER`。 |
| `parts` | 是 | 当前推荐使用 `{"text":"..."}` 文本 part。 |
| `messageId` | 是 | 调用方生成的消息 ID。 |
| `contextId` | 建议 | 会被 runtime 映射为 session id；缺省时使用 task id。 |
| `taskId` | 续写时可带 | 指向已有 task。 |
| `metadata.userId` | 建议 | 当前 runtime 会从 request context metadata 中读取 `userId`，缺省为 `system`。 |
| `metadata.agentId` | 建议 | 当前 runtime 会从 request context metadata 中读取 `agentId`，缺省为 handler 的 `agentId()`。 |
| `metadata.sessionId` | 建议 | 便于调用方侧追踪；真正 session 以 A2A `contextId` 为主。 |

### 3.1 A2A Java Client 通用初始化

Java 调用方推荐先通过 Agent Card 发现 runtime，再用 A2A SDK `JSONRPCTransport` 调用。当前示例模块 `SampleA2aClient` 已经采用这个模式：`A2ACardResolver` 读取 card，`JSONRPCTransport` 按 card 中的 JSON-RPC endpoint 发起请求。

通用初始化代码：

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

String userId = "user-1";
String agentId = "openjiuwen-react-agent";
String sessionId = "session-1";

Message message = Message.builder()
        .role(Message.Role.ROLE_USER)
        .messageId(UUID.randomUUID().toString())
        .contextId(sessionId)
        .metadata(Map.of(
                "userId", userId,
                "agentId", agentId,
                "sessionId", sessionId))
        .parts(List.of(new TextPart("ping")))
        .build();

MessageSendParams params = MessageSendParams.builder()
        .message(message)
        .tenant("tenant-1")
        .build();
```

调用结束后可执行：

```java
transport.close();
```

## 4. 调用方式一：普通阻塞 JSON 调用

### 4.1 使用场景

普通阻塞 JSON 调用适合短问答或调用方只需要一个同步返回对象的场景。它通过 `SendMessage` 进入 `DefaultRequestHandler#onMessageSend`，由 SDK 创建/更新 task，并驱动 `A2aAgentExecutor#execute`。

当前 executor 的结果投影规则：

| `AgentExecutionResult` | A2A 投影 |
|---|---|
| `OUTPUT` | `emitter.addArtifact(...)`，task 保持 working。 |
| `COMPLETED` | 有文本时 `emitter.complete(agent message)`；无文本时 `emitter.complete()`。 |
| `FAILED` | `emitter.fail()`。 |
| `INTERRUPTED` | 先发送 prompt message，再 `emitter.requiresInput()`，task 进入 input-required。 |

### 4.2 请求报文

```http
POST /a2a
Content-Type: application/json
Accept: application/json
```

```json
{
  "jsonrpc": "2.0",
  "id": "request-send-1",
  "method": "SendMessage",
  "params": {
    "message": {
      "role": "ROLE_USER",
      "messageId": "message-1",
      "contextId": "session-1",
      "metadata": {
        "userId": "user-1",
        "agentId": "openjiuwen-react-agent",
        "sessionId": "session-1"
      },
      "parts": [
        {
          "text": "ping"
        }
      ]
    },
    "metadata": {
      "clientRequestId": "client-req-1"
    },
    "tenant": "tenant-1"
  }
}
```

### 4.3 预期返回报文

成功时返回 HTTP 200，body 是 JSON-RPC response。`result` 类型是 A2A `EventKind`，通常是 `Task` 或 `Message`，由 A2A SDK 根据执行过程返回。

任务完成示例：

```json
{
  "jsonrpc": "2.0",
  "id": "request-send-1",
  "result": {
    "kind": "task",
    "id": "task-1",
    "contextId": "session-1",
    "status": {
      "state": "TASK_STATE_COMPLETED",
      "message": {
        "kind": "message",
        "role": "ROLE_AGENT",
        "messageId": "message-agent-1",
        "parts": [
          {
            "text": "pong"
          }
        ]
      }
    },
    "artifacts": [],
    "history": []
  }
}
```

需要用户继续输入时，预期 task 状态为 `TASK_STATE_INPUT_REQUIRED`，并在 status message 或 history message 中带 agent prompt：

```json
{
  "jsonrpc": "2.0",
  "id": "request-send-1",
  "result": {
    "kind": "task",
    "id": "task-1",
    "contextId": "session-1",
    "status": {
      "state": "TASK_STATE_INPUT_REQUIRED",
      "message": {
        "kind": "message",
        "role": "ROLE_AGENT",
        "parts": [
          {
            "text": "请补充必要信息。"
          }
        ]
      }
    }
  }
}
```

失败时可能返回 JSON-RPC error，或由当前 controller 的异常兜底返回 `{}`。调用方不能把空 JSON 当成成功结果，应按“无法解析为合法 A2A response”处理为调用失败。

### 4.4 A2A Java Client 调用示例

普通阻塞调用使用 `JSONRPCTransport#sendMessage(...)`。该方法返回 A2A `EventKind`，通常可按 `Task` 或 `Message` 处理：

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

适用判断：

| 返回类型 | 调用方处理 |
|---|---|
| `Task` | 读取 `task.status().state()` 判断 completed / input-required / failed 等状态。 |
| `Message` | 直接读取 agent message parts。 |
| 抛出 `A2AClientException` | 视为 transport、JSON-RPC 或 runtime 调用失败。 |

## 5. 调用方式二：流式 SSE 调用

### 5.1 使用场景

流式调用适合长任务、需要展示 agent 正在处理、需要读取中间 message/artifact/status 的场景。它通过 `SendStreamingMessage` 进入 `DefaultRequestHandler#onMessageSendStream`，controller 将 SDK `StreamingEventKind` 包成 SSE。

当前 SSE 帧格式固定为：

```text
event: jsonrpc
data: {"jsonrpc":"2.0","id":"request-stream-1","result":{...}}
```

`data` 中的 response 类型是 `SendStreamingMessageResponse`，`result` 是 `StreamingEventKind`，可能是：

| 事件类型 | 调用方处理 |
|---|---|
| `Message` | 展示 agent 文本；示例客户端会忽略 `metadata.accepted=true` 的 accepted message。 |
| `TaskStatusUpdateEvent` | 根据 `status.state` 判断进度、input-required 或 terminal。 |
| `TaskArtifactUpdateEvent` | 展示或保存 artifact parts。 |
| `Task` | 可作为完整 task 快照处理。 |

### 5.2 请求报文

```http
POST /a2a
Content-Type: application/json
Accept: text/event-stream
```

```json
{
  "jsonrpc": "2.0",
  "id": "request-stream-1",
  "method": "SendStreamingMessage",
  "params": {
    "message": {
      "role": "ROLE_USER",
      "messageId": "message-1",
      "contextId": "session-1",
      "metadata": {
        "userId": "user-1",
        "agentId": "openjiuwen-react-agent",
        "sessionId": "session-1"
      },
      "parts": [
        {
          "text": "请生成一份任务执行计划"
        }
      ]
    },
    "configuration": {
      "acceptedOutputModes": ["text", "artifact"],
      "historyLength": 10,
      "returnImmediately": false
    },
    "tenant": "tenant-1"
  }
}
```

### 5.3 预期返回报文

第一类常见事件是 accepted/progress message：

```text
event: jsonrpc
data: {"jsonrpc":"2.0","id":"request-stream-1","result":{"kind":"message","role":"ROLE_AGENT","messageId":"message-accepted","metadata":{"accepted":true},"parts":[{"text":"execution enqueued"}]}}
```

中间 artifact 示例：

```text
event: jsonrpc
data: {"jsonrpc":"2.0","id":"request-stream-1","result":{"kind":"task_artifact_update","taskId":"task-1","contextId":"session-1","artifact":{"artifactId":"artifact-1","parts":[{"text":"中间结果"}]},"append":true,"lastChunk":false}}
```

终态 status 示例：

```text
event: jsonrpc
data: {"jsonrpc":"2.0","id":"request-stream-1","result":{"kind":"status-update","taskId":"task-1","contextId":"session-1","status":{"state":"TASK_STATE_COMPLETED","message":{"kind":"message","role":"ROLE_AGENT","parts":[{"text":"完成"}]}},"final":true}}
```

调用方的终止判断：

| 终止来源 | 判断 |
|---|---|
| A2A task status | `TASK_STATE_COMPLETED`、`TASK_STATE_FAILED`、`TASK_STATE_CANCELED`、`TASK_STATE_REJECTED`。 |
| 当前示例客户端兼容逻辑 | `Message.metadata.runStatus` 为 `completed`、`failed`、`canceled`、`rejected`。 |
| SDK 正常断开 | A2A SDK 可能在 terminal event 后主动取消 SSE subscription；如果 terminal 已收到，这不是失败。 |

如果任务进入 `TASK_STATE_INPUT_REQUIRED`，SSE 不应被当作最终成功；调用方应继续向同一 `contextId` 或同一 task 发送用户补充输入，或用 `GetTask` 查询当前状态。

### 5.4 订阅已有任务

当调用方已知道 task id，希望重新订阅已有任务事件时，使用 `SubscribeToTask`：

```http
POST /a2a
Content-Type: application/json
Accept: text/event-stream
```

```json
{
  "jsonrpc": "2.0",
  "id": "request-subscribe-1",
  "method": "SubscribeToTask",
  "params": {
    "id": "task-1",
    "tenant": "tenant-1"
  }
}
```

返回仍是 SSE，每帧 `data` 是 `SendStreamingMessageResponse` 形状的 JSON-RPC response。

### 5.5 A2A Java Client 调用示例

流式调用使用 `JSONRPCTransport#sendMessageStreaming(...)`。该方法通过 callback 接收 `StreamingEventKind`，调用方通常用 latch 或异步框架等待 terminal event：

```java
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;

List<StreamingEventKind> events = new ArrayList<>();
CountDownLatch completed = new CountDownLatch(1);
AtomicBoolean sawTerminal = new AtomicBoolean(false);
AtomicReference<Throwable> failure = new AtomicReference<>();

transport.sendMessageStreaming(
        params,
        event -> {
            events.add(event);
            if (event instanceof TaskStatusUpdateEvent statusEvent
                    && statusEvent.status() != null
                    && statusEvent.status().state() != null) {
                TaskState state = statusEvent.status().state();
                if (state == TaskState.TASK_STATE_COMPLETED
                        || state == TaskState.TASK_STATE_FAILED
                        || state == TaskState.TASK_STATE_CANCELED
                        || state == TaskState.TASK_STATE_REJECTED) {
                    sawTerminal.set(true);
                    completed.countDown();
                }
            }
            if (event instanceof Message message
                    && message.metadata() != null
                    && "completed".equals(String.valueOf(message.metadata().get("runStatus")))) {
                sawTerminal.set(true);
                completed.countDown();
            }
            if (event instanceof TaskArtifactUpdateEvent artifactEvent) {
                System.out.println("artifact=" + artifactEvent.artifact());
            }
        },
        error -> {
            if (!sawTerminal.get()) {
                failure.set(error);
            }
            completed.countDown();
        },
        callContext);

if (!completed.await(Duration.ofSeconds(60).toMillis(), TimeUnit.MILLISECONDS)) {
    throw new IllegalStateException("A2A stream did not complete before timeout");
}
if (failure.get() != null) {
    throw new IllegalStateException("A2A stream failed", failure.get());
}
```

订阅已有任务使用 `JSONRPCTransport#subscribeToTask(...)`：

```java
import org.a2aproject.sdk.spec.TaskIdParams;

transport.subscribeToTask(
        TaskIdParams.builder()
                .id("task-1")
                .tenant("tenant-1")
                .build(),
        event -> System.out.println("event=" + event),
        error -> System.err.println("subscribe failed: " + error.getMessage()),
        callContext);
```

当前示例模块的 `SampleA2aClient#streamMessage(...)` 就是这一类调用的参考实现。

## 6. 调用方式三：Push Notification 异步回调

### 6.1 使用场景

Push Notification 适合调用方不想保持 HTTP 长连接的后台任务场景。调用方在提交任务时携带回调配置，或对已有 task 注册/查询/删除 push config。runtime 侧由 `BasePushNotificationSender` 和 `InMemoryPushNotificationConfigStore` 处理配置和回调投递。

当前实现特点：

| 项 | 当前语义 |
|---|---|
| 配置存储 | 内存 `InMemoryPushNotificationConfigStore`，进程重启后丢失。 |
| 发送器 | A2A SDK `BasePushNotificationSender`。 |
| Agent Card 声明 | `capabilities.pushNotifications=true`。 |
| 适合生产吗 | 当前更像 runtime SDK 默认能力；生产持久化、签名、重试、鉴权策略需要业务宿主补齐。 |

### 6.2 方式 A：提交任务时携带 push 配置

```http
POST /a2a
Content-Type: application/json
Accept: application/json
```

```json
{
  "jsonrpc": "2.0",
  "id": "request-push-send-1",
  "method": "SendMessage",
  "params": {
    "message": {
      "role": "ROLE_USER",
      "messageId": "message-1",
      "contextId": "session-1",
      "parts": [
        {
          "text": "启动一个后台分析任务"
        }
      ],
      "metadata": {
        "userId": "user-1",
        "agentId": "openjiuwen-react-agent",
        "sessionId": "session-1"
      }
    },
    "configuration": {
      "taskPushNotificationConfig": {
        "id": "push-1",
        "url": "https://client.example.com/a2a/callback",
        "token": "callback-token-1",
        "tenant": "tenant-1"
      },
      "returnImmediately": true
    },
    "tenant": "tenant-1"
  }
}
```

`MessageSendConfiguration` 的注释说明：在 `SendMessage` 中携带 `taskPushNotificationConfig` 时，`taskId` 应为空；task 创建后由 A2A SDK 关联具体 task。

首次返回仍是普通 JSON-RPC response。调用方随后等待 callback endpoint 被调用，或用 `GetTask` 查询：

```json
{
  "jsonrpc": "2.0",
  "id": "request-push-send-1",
  "result": {
    "kind": "task",
    "id": "task-1",
    "contextId": "session-1",
    "status": {
      "state": "TASK_STATE_SUBMITTED"
    }
  }
}
```

后续 callback 的具体 body 由 A2A SDK push sender 生成；调用方应按 A2A task/message/status/artifact 事件处理，而不是依赖 runtime 自定义 envelope。若配置 `token`，SDK sender 会按其约定携带认证信息；调用方服务应校验该 token 或更强认证信息。

### 6.3 方式 B：对已有 task 管理 push 配置

注册或覆盖配置：

```json
{
  "jsonrpc": "2.0",
  "id": "request-push-set-1",
  "method": "CreateTaskPushNotificationConfig",
  "params": {
    "id": "push-1",
    "taskId": "task-1",
    "url": "https://client.example.com/a2a/callback",
    "token": "callback-token-1",
    "tenant": "tenant-1"
  }
}
```

预期返回：

```json
{
  "jsonrpc": "2.0",
  "id": "request-push-set-1",
  "result": {
    "id": "push-1",
    "taskId": "task-1",
    "url": "https://client.example.com/a2a/callback",
    "token": "callback-token-1",
    "tenant": "tenant-1"
  }
}
```

查询单个配置：

```json
{
  "jsonrpc": "2.0",
  "id": "request-push-get-1",
  "method": "GetTaskPushNotificationConfig",
  "params": {
    "taskId": "task-1",
    "id": "push-1"
  }
}
```

列出 task 的配置：

```json
{
  "jsonrpc": "2.0",
  "id": "request-push-list-1",
  "method": "ListTaskPushNotificationConfigs",
  "params": {
    "taskId": "task-1"
  }
}
```

删除配置：

```json
{
  "jsonrpc": "2.0",
  "id": "request-push-delete-1",
  "method": "DeleteTaskPushNotificationConfig",
  "params": {
    "taskId": "task-1",
    "id": "push-1"
  }
}
```

`A2aJsonRpcControllerTest#blockingHandlerDispatchesPushNotificationConfigRequests` 覆盖了这四类 push config 管理请求会被 controller 分发到 SDK `RequestHandler`。

### 6.4 A2A Java Client 调用示例

Push 有两种 client 调用形态。

第一种是在 `SendMessage` 时内嵌 `taskPushNotificationConfig`。这会让 SDK handler 在 task 创建或续写时保存 callback 配置，后续事件由 runtime 的 push sender 投递：

```java
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;

TaskPushNotificationConfig pushConfig = TaskPushNotificationConfig.builder()
        .id("push-1")
        .url("https://client.example.com/a2a/callback")
        .token("callback-token-1")
        .tenant("tenant-1")
        .build();

MessageSendConfiguration configuration = MessageSendConfiguration.builder()
        .taskPushNotificationConfig(pushConfig)
        .returnImmediately(true)
        .build();

MessageSendParams pushParams = MessageSendParams.builder()
        .message(message)
        .configuration(configuration)
        .tenant("tenant-1")
        .build();

EventKind accepted = transport.sendMessage(pushParams, callContext);
System.out.println("accepted=" + accepted);
```

第二种是对已有 task 显式管理 push config：

```java
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;

TaskPushNotificationConfig created = transport.createTaskPushNotificationConfiguration(
        TaskPushNotificationConfig.builder()
                .id("push-1")
                .taskId("task-1")
                .url("https://client.example.com/a2a/callback")
                .token("callback-token-1")
                .tenant("tenant-1")
                .build(),
        callContext);

TaskPushNotificationConfig loaded = transport.getTaskPushNotificationConfiguration(
        new GetTaskPushNotificationConfigParams("task-1", "push-1"),
        callContext);

ListTaskPushNotificationConfigsResult listed = transport.listTaskPushNotificationConfigurations(
        new ListTaskPushNotificationConfigsParams("task-1"),
        callContext);

transport.deleteTaskPushNotificationConfigurations(
        new DeleteTaskPushNotificationConfigParams("task-1", "push-1"),
        callContext);
```

两种形态的差别：

| 形态 | 什么时候用 | taskId 要求 |
|---|---|---|
| `SendMessage` 内嵌 push config | 创建新后台任务时顺手配置 callback。 | `taskPushNotificationConfig.taskId` 应为空，SDK 会在创建 task 后补上真实 task id。 |
| 显式 push config 管理接口 | 已有 task 需要补配、查询或删除 callback。 | 必须传已有 `taskId`。 |

## 7. 查询和取消配套接口

### 7.1 查询任务：GetTask

```http
POST /a2a
Content-Type: application/json
Accept: application/json
```

```json
{
  "jsonrpc": "2.0",
  "id": "request-get-task-1",
  "method": "GetTask",
  "params": {
    "id": "task-1",
    "historyLength": 20,
    "tenant": "tenant-1"
  }
}
```

预期返回：

```json
{
  "jsonrpc": "2.0",
  "id": "request-get-task-1",
  "result": {
    "kind": "task",
    "id": "task-1",
    "contextId": "session-1",
    "status": {
      "state": "TASK_STATE_WORKING"
    },
    "artifacts": [],
    "history": []
  }
}
```

### 7.2 取消任务：CancelTask

```json
{
  "jsonrpc": "2.0",
  "id": "request-cancel-1",
  "method": "CancelTask",
  "params": {
    "id": "task-1",
    "tenant": "tenant-1",
    "metadata": {
      "reason": "user_requested"
    }
  }
}
```

当前 `A2aAgentExecutor#cancel` 调用 `emitter.cancel()`，预期 task 进入 `TASK_STATE_CANCELED`：

```json
{
  "jsonrpc": "2.0",
  "id": "request-cancel-1",
  "result": {
    "kind": "task",
    "id": "task-1",
    "status": {
      "state": "TASK_STATE_CANCELED"
    }
  }
}
```

## 8. 当前状态语义

调用方应按 A2A task state 理解任务生命周期：

| 状态 | 含义 | 是否终态 |
|---|---|---|
| `TASK_STATE_SUBMITTED` | 任务已接收或排队。 | 否 |
| `TASK_STATE_WORKING` | agent 正在处理，可有 message/artifact/status 事件。 | 否 |
| `TASK_STATE_INPUT_REQUIRED` | agent 等待用户补充输入。 | 否，但处于中断态 |
| `TASK_STATE_AUTH_REQUIRED` | agent 等待认证或授权。 | 否，但处于中断态 |
| `TASK_STATE_COMPLETED` | 成功完成。 | 是 |
| `TASK_STATE_CANCELED` | 已取消。 | 是 |
| `TASK_STATE_FAILED` | 执行失败。 | 是 |
| `TASK_STATE_REJECTED` | 请求被拒绝。 | 是 |

当前 `A2aAgentExecutor` 的执行开始会调用 `emitter.startWork()`，因此正常执行会先进入 working。若没有注册 `AgentRuntimeHandler`，executor 会直接 `emitter.fail()`。

## 9. 错误和边界

当前实现边界需要调用方明确知道：

| 边界 | 说明 |
|---|---|
| 方法名 | 运行时代码通过 A2A SDK `JSONRPCUtils.parseRequestBody(...)` 解析。当前可靠方法名是 SDK 常量：`SendMessage`、`SendStreamingMessage`、`SubscribeToTask`、`GetTask`、`CancelTask` 等。 |
| 异常兜底 | `A2aJsonRpcController#handle` 捕获异常后返回 HTTP 200 + `{}`。调用方必须把 `{}` 或无法解析的 JSON-RPC response 视为失败。 |
| 存储 | 默认 `InMemoryTaskStore`、`InMemoryQueueManager`、`InMemoryPushNotificationConfigStore` 都是内存实现。进程重启会丢 task/push 配置。 |
| handler 选择 | `RuntimeAutoConfiguration#a2aAgentExecutor` 取 Spring 容器中第一个 `AgentRuntimeHandler`。一个 runtime 默认暴露一个顶层业务 agent。 |
| 输入内容 | `A2aAgentExecutor` 当前只拼接 `TextPart` 文本，非文本 part 不会进入 `AgentExecutionContext` 的输入文本。 |
| identity 映射 | `tenantId` 优先来自 `RequestContext#getTenant()`；`userId`、`agentId` 来自 request metadata，缺省分别为 `system` 和 handler `agentId()`；`sessionId` 来自 A2A `contextId`，缺省使用 task id。 |
| SSE 断开 | terminal event 后 SDK 客户端可能正常取消 SSE subscription；调用方应先判断是否已收到 terminal。 |
| Push 生产化 | 默认 push sender/config store 可用，但持久化、重试、回调鉴权、签名校验等生产策略需要宿主应用补齐。 |

## 10. 推荐调用选择

| 如果调用方需要 | 推荐 |
|---|---|
| 一问一答、响应较快 | `SendMessage`。 |
| 页面上实时展示进度和中间结果 | `SendStreamingMessage`。 |
| 网络不稳定后恢复已有任务观察 | `SubscribeToTask` + `GetTask`。 |
| 后台任务，调用方不保持连接 | `SendMessage` + `configuration.taskPushNotificationConfig`，并用 `GetTask` 兜底查询。 |
| 用户取消 | `CancelTask`。 |
| 调用方不确定 endpoint | 先读 `/.well-known/agent-card.json`，按 card 中 JSONRPC interface 的 URL 调用。 |

## 11. 验证入口

当前 `agent-runtime` 核心验证面：

```bash
./mvnw -pl agent-runtime test
```

其中与本文直接相关的覆盖点包括：

| 测试 | 覆盖 |
|---|---|
| `A2aJsonRpcControllerTest#streamingResponseDataIsJsonRpcEventReadableByA2aSdkClient` | `SendStreamingMessage` SSE 帧的 `data` 可被 A2A SDK 客户端解析。 |
| `A2aJsonRpcControllerTest#blockingHandlerDispatchesPushNotificationConfigRequests` | push config 的 create/get/list/delete 请求进入 SDK handler。 |
| `A2aJsonRpcControllerTest#defaultPushNotificationSenderUsesA2aSdkBaseSender` | 默认 push sender 是 A2A SDK `BasePushNotificationSender`。 |

AgentScope/LangGraph 示例客户端验证位于 `examples/agent-runtime-a2a-llm-e2e`，需要该示例模块依赖可解析后单独运行：

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml -Dtest=SampleA2aClientTest test
```

OpenJiuwen 端到端验证位于独立示例模块 `examples/agent-runtime-a2a-openjiuwen-e2e`，需要真实 LLM key 环境变量：

```bash
SAA_SAMPLE_LLM_API_KEY=... ./mvnw -f examples/agent-runtime-a2a-openjiuwen-e2e/pom.xml -Dtest=OpenJiuwenReactAgentA2aE2eTest test
```

示例验证覆盖点：

| 测试 | 覆盖 |
|---|---|
| `SampleA2aClientTest` | 示例客户端从 message/status/artifact 三类 streaming event 提取文本，并正确识别 terminal。 |
| `OpenJiuwenReactAgentA2aE2eTest` | 在 `agent-runtime-a2a-openjiuwen-e2e` 中端到端读取 Agent Card，通过 A2A streaming 调用本地 OpenJiuwen runtime。该测试需要真实 LLM key 环境变量。 |
