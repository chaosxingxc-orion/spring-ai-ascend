# A2A 协议调用

agent-runtime 通过 A2A JSON-RPC 协议对外暴露 Agent，支持三种通讯模式：阻塞请求-响应、流式 SSE、异步 task 轮询。调用方可以是 curl、A2A Java SDK 或任意 HTTP 客户端。

## 1. 概述

```bash
# 最小示例：流式调用
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","method":"SendStreamingMessage","id":"1",
       "params":{"message":{"role":"ROLE_USER","messageId":"m1",
       "parts":[{"text":"你好"}]}}}' --no-buffer
```

## 2. 快速开始

### 端点

| 方法 | 路径 | Accept | 用途 |
|------|------|--------|------|
| `GET` | `/.well-known/agent-card.json` | — | Agent 发现 |
| `POST` | `/a2a` | `application/json` | 阻塞 JSON-RPC |
| `POST` | `/a2a` | `text/event-stream` | 流式 SSE |

### 通讯模式

| 模式 | A2A 方法 | 行为 | 适用场景 |
|------|---------|------|---------|
| 阻塞请求-响应 | `SendMessage` | A2A 层收集 Stream 后一次性返回 JSON | 简短问答、批处理 |
| 流式 | `SendStreamingMessage` | 通过 SSE 持续推送结果 | LLM 流式生成、实时输出 |
| 异步轮询 | `GetTask` | 提交后立即返回 taskId，后续轮询 | 长时任务 |

> Handler 始终以 Stream 方式产出。`SendMessage` 的阻塞返回由 A2A 层收集 Stream 实现。

## 3. 工作原理

```
客户端                      agent-runtime
  │                              │
  │── POST /a2a ────────────────>│  JSON-RPC 解析（method 字段）
  │                              │  → SendStreamingMessage → SSE 流
  │<── SSE: TaskAccepted ───────│  → SendMessage → 收集 Stream → JSON
  │<── SSE: ArtifactUpdate ─────│  → GetTask → 查询 TaskStore
  │<── SSE: TaskStatusUpdate ───│
```

## 4. A2A Methods

### SendMessage

阻塞请求-响应。完整的 Agent 输出在单个 JSON-RPC 回复中返回。

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0", "method": "SendMessage", "id": "1",
    "params": {
      "message": {
        "role": "ROLE_USER", "messageId": "msg-001", "contextId": "session-123",
        "parts": [{"text": "你好"}]
      }
    }
  }'
```

### SendStreamingMessage

流式消息，通过 SSE 推送。Agent 处理过程中事件实时到达。

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0", "method": "SendStreamingMessage", "id": "1",
    "params": {
      "message": {
        "role": "ROLE_USER", "messageId": "msg-001", "contextId": "session-123",
        "metadata": {"userId": "test-user", "agentId": "my-agent", "sessionId": "session-123"},
        "parts": [{"text": "你好"}]
      }
    }
  }' --no-buffer
```

SSE 事件流示例：
```
event:jsonrpc
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{...,"state":"TASK_STATE_SUBMITTED"}}}

event:jsonrpc
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{...,"state":"TASK_STATE_WORKING"}}}

event:jsonrpc
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{...,"state":"TASK_STATE_COMPLETED",
  "message":{"role":"ROLE_AGENT","parts":[{"text":"你好！"}]}}}}
```

流式事件按顺序到达：`TASK_STATE_SUBMITTED → ArtifactUpdate × N → TASK_STATE_COMPLETED/FAILED`。

### GetTask

按 taskId 查询任务状态和结果。

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"GetTask","id":"1","params":{"id":"task-uuid"}}'
```

### CancelTask

取消正在执行的任务。

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"CancelTask","id":"1","params":{"id":"task-uuid"}}'
```

> OpenJiuwen 通过 streaming runner 执行；cancel 会停止 runtime 继续消费结果，但是否中断底层 LLM 调用取决于 OpenJiuwen/模型客户端。

### ListTasks

列出任务。

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"ListTasks","id":"1","params":{"pageSize":20}}'
```

### SubscribeToTask

断线后通过 taskId 重新订阅 SSE 流。

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","method":"SubscribeToTask","id":"2","params":{"id":"task-uuid"}}' --no-buffer
```

## 5. Java SDK 客户端调用

Maven 依赖：

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

### 通用初始化

```java
URI runtimeUri = URI.create("http://localhost:8080");
AgentCard card = new A2ACardResolver(runtimeUri.toString()).getAgentCard();
JSONRPCTransport transport = new JSONRPCTransport(card);

String sessionId = "session-" + UUID.randomUUID();
Message message = Message.builder()
    .role(Message.Role.ROLE_USER)
    .messageId(UUID.randomUUID().toString())
    .contextId(sessionId)
    .metadata(Map.of("userId", "user-1", "agentId", "agent", "sessionId", sessionId))
    .parts(List.of(new TextPart("你好")))
    .build();

MessageSendParams params = MessageSendParams.builder()
    .message(message)
    .metadata(Map.of("userId", "user-1", "agentId", "agent"))
    .build();

ClientCallContext callCtx = new ClientCallContext(Map.of(), Map.of());
```

### 流式调用

```java
List<StreamingEventKind> events = new ArrayList<>();
CountDownLatch done = new CountDownLatch(1);

transport.sendMessageStreaming(params, event -> {
    events.add(event);
    if (event instanceof TaskStatusUpdateEvent status
            && status.status() != null && status.status().state() != null) {
        TaskState state = status.status().state();
        if (state.isTerminal()) done.countDown();
    }
}, error -> { error.printStackTrace(); done.countDown(); }, callCtx);

done.await(60, TimeUnit.SECONDS);
```

### 阻塞调用

```java
EventKind result = transport.sendMessage(params, callCtx);
if (result instanceof Task task) {
    System.out.println("taskId=" + task.id() + " state=" + task.status().state());
}
```

### 重新订阅

```java
transport.subscribeToTask(new TaskIdParams(taskId),
    event -> System.out.println("event=" + event),
    error -> System.err.println("failed: " + error.getMessage()),
    callCtx);
```

参考完整示例：`examples/agent-runtime-a2a-llm-e2e/src/main/java/.../SampleA2aClient.java`

## 6. 消息格式

### Role

必须使用 proto 枚举名：`ROLE_USER`、`ROLE_AGENT`。小写形式无效。

### Parts

```json
"parts": [
  {"text": "你好！"},
  {"file": {"uri": "https://...", "mimeType": "image/png"}},
  {"data": {"key": "value"}}
]
```

runtime 只消费 `TextPart.text`，多个 text part 用换行拼接。

## 7. 错误响应

```json
{
  "jsonrpc": "2.0", "id": 1,
  "error": {
    "code": -32601,
    "message": "Unsupported JSON-RPC method: '...'"
  }
}
```

| 错误码 | 原因 |
|--------|------|
| `-32700` | JSON 非法或 schema 不匹配 |
| `-32601` | 未知方法 |
| `-32602` | JSON 合法但格式错误 |
| `-32603` | Agent 执行内部错误 |

## 8. 租户路由

`X-Tenant-Id` 头将请求路由到指定租户。多租户部署中，前置网关必须认证调用方并注入此头——runtime 直接信任。

## 9. 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `agent-runtime.access.a2a.default-tenant-id` | String | `default` | 默认租户 |
| `agent-runtime.access.a2a.public-base-url` | String | — | Agent Card 外部 URL |

## 10. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/a2a-protocol-and-communication-design.md`
- [Agent Card 配置](agent-card-configuration.md)
- [配置属性参考](configuration-properties.md)
- Example：`examples/agent-runtime-a2a-llm-e2e/`、`examples/agent-runtime-a2a-external-access-e2e/`
