# 通讯模型

agent-runtime 通过统一的请求/响应类型体系支持三种 S2C（Server-to-Client）通讯模式。
调用方通过选择对应的 A2A 方法来选择通讯模式。

## 模式总览

| 模式 | A2A 方法 | 行为 | 适用场景 |
|---|---|---|---|
| **同步** | `SendMessage` / `message/send` | 客户端发送请求，等待完整结果后返回 | 简短问答、批处理 |
| **流式** | `SendStreamingMessage` / `message/stream` | 服务端通过 SSE 持续推送增量结果 | LLM 流式生成、实时输出 |
| **异步** | `SendMessage` → `GetTask` / `tasks/get` | 客户端提交任务后立即返回 taskId，后续轮询结果 | 长时任务、人工审批流程 |

## 同步模式

```
客户端                    运行时
  |                          |
  |-- SendMessage ---------->|
  |                          | 执行 Agent
  |<-------- Task（结果）----|
  |                          |
```

响应在单个 JSON-RPC 回复中包含完整的 Agent 输出。`Task` 对象携带包含
Agent 回复部件的最终 `Message`。

### 用法

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
        "parts": [{"text": "2+2 等于多少？"}]
      }
    }
  }'
```

## 流式模式

```
客户端                    运行时
  |                          |
  |-- SendStreamingMessage ->|
  |                          | 执行 Agent
  |<-- SSE: TASK_STATE_SUBMITTED
  |<-- SSE: TASK_STATE_WORKING
  |<-- SSE: （增量输出）...
  |<-- SSE: TASK_STATE_COMPLETED
  |                          |
```

事件通过 Server-Sent Events 推送。每个事件是一个 JSON-RPC 通知，包裹一个
流式事件。客户端必须设置 `Accept: text/event-stream`。

### SSE 事件流示例

```
event:jsonrpc
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{"taskId":"...","status":{"state":"TASK_STATE_SUBMITTED",...},"contextId":"..."}}}

event:jsonrpc
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{"taskId":"...","status":{"state":"TASK_STATE_WORKING",...},"contextId":"..."}}}

event:jsonrpc
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{"taskId":"...","status":{"state":"TASK_STATE_COMPLETED","message":{...}},"contextId":"..."}}}
```

### 断线重连（tasks/resubscribe）

当 SSE 连接中断时，客户端可以使用最后收到事件中的 taskId 重新订阅任务流：

```json
{
  "jsonrpc": "2.0",
  "method": "SubscribeToTask",
  "id": "2",
  "params": {"id": "上一次事件中的 task-uuid"}
}
```

服务端从最后一个已确认事件恢复推送。

## 异步模式

```
客户端                    运行时
  |                          |
  |-- SendMessage ---------->|
  |<-------- Task {taskId} --|
  |                          | 执行 Agent...
  |                          |
  |-- GetTask {taskId} ---->|
  |<-------- Task（状态）----|
  |                          |
  |-- GetTask {taskId} ---->|
  |<-------- Task（结果）----|
  |                          |
```

客户端提交消息后立即收到包含 `taskId` 的 `Task` 对象，之后通过 `GetTask`
轮询进度并获取最终结果。

### 用法

```bash
# 第一步：提交
TASK_ID=$(curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"SendMessage","id":"1","params":{"message":{...}}}' \
  | jq -r '.result.id')

# 第二步：轮询
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"method\":\"GetTask\",\"id\":\"2\",\"params\":{\"id\":\"$TASK_ID\"}}"
```

## 响应消息类型

所有模式产生的响应都属于以下四种类型之一：

| 类型 | 事件 | 含义 |
|---|---|---|
| **任务已接受** | `TASK_STATE_SUBMITTED` | Runtime 已接收并排队请求 |
| **增量输出** | `TaskArtifactUpdateEvent` | Agent 执行过程中的部分结果（仅流式模式） |
| **最终结果** | `TASK_STATE_COMPLETED` + `message` | Agent 执行成功完成 |
| **异常** | `TASK_STATE_FAILED` | Agent 执行失败并返回错误 |

### 流式顺序保证

流式模式下，事件按以下顺序到达：

```
TASK_STATE_SUBMITTED  →  （TaskArtifactUpdateEvent × N）  →  TASK_STATE_COMPLETED
                                                           →  TASK_STATE_FAILED
```

`TASK_STATE_COMPLETED` 和 `TASK_STATE_FAILED` 是互斥的终止状态。

## A2A 方法映射

| A2A 方法 | 通讯模式 | 传输方式 | 说明 |
|---|---|---|---|
| `SendMessage` | 同步 | JSON-RPC 响应 | 发送消息，获取完整结果 |
| `SendStreamingMessage` | 流式 | SSE | 发送消息，接收增量事件 |
| `GetTask` | 异步（轮询） | JSON-RPC 响应 | 按 taskId 查询任务状态和结果 |
| `CancelTask` | — | JSON-RPC 响应 | 取消正在执行的任务 |
| `SubscribeToTask` | 流式（重连） | SSE | 断线后重新订阅任务事件流 |
| `ListTasks` | — | JSON-RPC 响应 | 列出任务，支持过滤 |

## 方法名别名

Runtime 同时接受 A2A SDK 规范名和旧式路径名：

| 规范名（PascalCase） | 旧式名（路径格式） |
|---|---|
| `SendMessage` | `message/send` |
| `SendStreamingMessage` | `message/stream` |
| `GetTask` | `tasks/get` |
| `CancelTask` | `tasks/cancel` |
| `ListTasks` | `tasks/list` |

## 相关

- [A2A 端点](a2a-endpoints.md) — 完整的端点和 method 参考
- [配置属性](configuration-properties.md) — 异步模式的调度线程池配置
