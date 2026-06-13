# A2A 端点

agent-runtime 暴露的 A2A 协议面。

## 端点

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/.well-known/agent-card.json` | Agent 发现 |
| `GET` | `/.well-known/agent.json` | 旧式别名 |
| `POST` | `/a2a` | JSON-RPC（生成 `application/json`） |
| `POST` | `/a2a` | JSON-RPC（生成 `text/event-stream`，用于流式） |

## JSON-RPC 方法

### SendMessage

非流式消息。返回完整的 Task。

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendMessage",
    "id": "1",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-001",
        "contextId": "session-123",
        "parts": [{"text": "你好"}]
      }
    }
  }'
```

### SendStreamingMessage

流式消息，通过 SSE 推送。Agent 处理过程中实时到达。

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "id": "1",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-001",
        "contextId": "session-123",
        "metadata": {
          "userId": "test-user",
          "agentId": "my-agent",
          "sessionId": "session-123"
        },
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
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{...,"state":"TASK_STATE_COMPLETED","message":{"role":"ROLE_AGENT","parts":[{"text":"你好！"}]}}}}
```

### GetTask

按 ID 查询任务状态。

```json
{"jsonrpc":"2.0","method":"GetTask","id":"1","params":{"id":"task-uuid"}}
```

### CancelTask

取消正在执行的任务。

```json
{"jsonrpc":"2.0","method":"CancelTask","id":"1","params":{"id":"task-uuid"}}
```

### ListTasks

列出任务（支持可选分页/过滤）。

```json
{"jsonrpc":"2.0","method":"ListTasks","id":"1","params":{}}
```

### SubscribeToTask

订阅已有任务的流式事件。

```json
{"jsonrpc":"2.0","method":"SubscribeToTask","id":"1","params":{"id":"task-uuid"}}
```

### tasks/resubscribe（SubscribeToTask 重连）

SSE 连接中断后，通过相同的 taskId 重新订阅。

```json
{"jsonrpc":"2.0","method":"SubscribeToTask","id":"2","params":{"id":"上一次会话中的 task-uuid"}}
```

注意：`tasks/resubscribe` 映射到相同的 `SubscribeToTask` 方法。A2A 协议
对首次订阅和重连都使用 `SubscribeToTask`——服务端通过 taskId 是否已知来区分。

## 方法名别名

Runtime 同时接受 A2A SDK 规范名和旧式路径名：

| 规范名 | 旧式别名 |
|---|---|
| `SendMessage` | `message/send` |
| `SendStreamingMessage` | `message/stream` |
| `GetTask` | `tasks/get` |
| `CancelTask` | `tasks/cancel` |
| `ListTasks` | `tasks/list` |

## 消息结构

### Role 枚举

必须使用 proto 枚举名（不能小写）：

| 有效 | 无效 |
|---|---|
| `ROLE_USER` | `user` |
| `ROLE_AGENT` | `agent` |

### Parts

每个 part 以其类型作为 JSON key：

```json
"parts": [
  {"text": "你好！"},
  {"file": {"uri": "https://...", "mimeType": "image/png"}},
  {"data": {"key": "value"}}
]
```

## 错误响应

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32601,
    "message": "Unsupported JSON-RPC method: '...'",
    "data": [{
      "@type": "type.googleapis.com/google.rpc.ErrorInfo",
      "reason": "METHOD_NOT_FOUND",
      "domain": "a2a-protocol.org"
    }]
  }
}
```

| 错误码 | 原因 |
|---|---|
| `-32700` | `JSON_PARSE` — 非法 JSON 或 schema 不匹配 |
| `-32601` | `METHOD_NOT_FOUND` — 未知方法 |
| `-32602` | `INVALID_REQUEST` — JSON 合法但格式错误 |
| `-32603` | `INTERNAL` — Agent 执行内部错误 |

## 租户路由

`X-Tenant-Id` 头将请求路由到指定租户。缺失时使用
`agent-runtime.access.a2a.default-tenant-id` 属性。

多租户部署中，前置网关必须认证调用方并注入 `X-Tenant-Id`——runtime 直接信任此头。
