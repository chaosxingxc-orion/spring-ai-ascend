# A2A Endpoints

The A2A protocol surface exposed by agent-runtime.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/.well-known/agent-card.json` | Agent discovery |
| `GET` | `/.well-known/agent.json` | Legacy alias |
| `POST` | `/a2a` | JSON-RPC (produces `application/json`) |
| `POST` | `/a2a` | JSON-RPC (produces `text/event-stream` for streaming) |

## JSON-RPC methods

### SendMessage

Non-streaming message delivery. Returns a Task immediately.

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
        "parts": [{"text": "Hello"}]
      }
    }
  }'
```

### SendStreamingMessage

Streaming message delivery via SSE. Events arrive as the agent processes.

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
        "parts": [{"text": "Hello"}]
      }
    }
  }' --no-buffer
```

SSE event stream:

```
event:jsonrpc
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{...,"state":"TASK_STATE_SUBMITTED"}}}

event:jsonrpc
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{...,"state":"TASK_STATE_WORKING"}}}

event:jsonrpc
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{...,"state":"TASK_STATE_COMPLETED","message":{"role":"ROLE_AGENT","parts":[{"text":"Hello!"}]}}}}
```

### GetTask

Retrieve task state by ID.

```json
{"jsonrpc":"2.0","method":"GetTask","id":"1","params":{"id":"task-uuid"}}
```

### CancelTask

Cancel an in-flight task.

```json
{"jsonrpc":"2.0","method":"CancelTask","id":"1","params":{"id":"task-uuid"}}
```

### ListTasks

List tasks (with optional pagination/filtering).

```json
{"jsonrpc":"2.0","method":"ListTasks","id":"1","params":{}}
```

### SubscribeToTask

Subscribe to streaming events for an existing task.

```json
{"jsonrpc":"2.0","method":"SubscribeToTask","id":"1","params":{"id":"task-uuid"}}
```

## Method name aliases

Both canonical A2A SDK names and legacy names are recognized:

| Canonical | Also accepts |
|---|---|
| `SendMessage` | `message/send` |
| `SendStreamingMessage` | `message/stream` |
| `GetTask` | `tasks/get` |
| `CancelTask` | `tasks/cancel` |
| `ListTasks` | `tasks/list` |

## Message structure

### Role enum

Must use proto enum names (not lowercase):

| Valid | Invalid |
|---|---|
| `ROLE_USER` | `user` |
| `ROLE_AGENT` | `agent` |

### Parts

Each part uses its type as the JSON key:

```json
"parts": [
  {"text": "Hello, world!"},
  {"file": {"uri": "https://...", "mimeType": "image/png"}},
  {"data": {"key": "value"}}
]
```

## Error responses

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

| Code | Reason |
|---|---|
| `-32700` | `JSON_PARSE` — invalid JSON or schema mismatch |
| `-32601` | `METHOD_NOT_FOUND` — unknown method |
| `-32602` | `INVALID_REQUEST` — valid JSON, wrong shape |
| `-32603` | `INTERNAL` — agent execution error |

## Tenant routing

The `X-Tenant-Id` header routes requests to a tenant. When absent, the
`agent-runtime.access.a2a.default-tenant-id` property applies.

In multi-tenant deployments, a fronting gateway must authenticate callers
and inject `X-Tenant-Id` — the runtime trusts the header as-is.
