# RESTful 接入 A2A 协议适配方案报告

## 1. 报告背景

当前 `edp-agent-trae` 已经通过 A2A JSON-RPC `/a2a` 链路完成技术穿刺：

```text
stream-debug-ui
  → /api/stream
  → http://localhost:8190/a2a
  → A2aJsonRpcController
  → EdpaRuntimeHandler
  → DeepAgent
  → SSE
  → 前端展示
```

但如果前端只能支持 RESTful 接口：

```text
/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
```

就会出现协议不匹配问题。

本报告用于说明该问题的现象、原因、短期修改建议和长期修改建议。

---

## 2. 问题现象

### 2.1 前端只能发送 RESTful 请求

前端期望调用：

```http
POST /v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
Content-Type: application/json
Accept: text/event-stream
```

请求体可能是：

```json
{
  "query": "帮我推荐几款稳健型理财产品"
}
```

或：

```json
{
  "inputs": {
    "query": "帮我推荐几款稳健型理财产品"
  }
}
```

### 2.2 当前 EDPAgent 实际入口是 A2A JSON-RPC

当前 A2A 入口是：

```java
@PostMapping(value = {"/a2a", "/a2a/"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> handleSse(...)
```

对应文件：

```text
agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/A2aJsonRpcController.java
```

它期望收到的是 A2A JSON-RPC 请求，例如：

```json
{
  "jsonrpc": "2.0",
  "method": "SendStreamingMessage",
  "id": "stream-debug-ui-...",
  "params": {
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-...",
      "contextId": "conv-...",
      "metadata": {
        "agentId": "edp-agent",
        "sessionId": "conv-..."
      },
      "parts": [
        {
          "text": "帮我推荐几款稳健型理财产品"
        }
      ]
    }
  }
}
```

### 2.3 直接对接会失败或无法识别

如果前端直接把 RESTful 请求体发给 `/a2a`，后端无法按 A2A JSON-RPC 协议解析。

如果前端只调用：

```text
/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
```

当前 `edp-agent-trae/spike` 也没有对应 RESTful Controller，因此请求不会进入 EDPAgent 执行链路。

### 2.4 即使请求转换成功，响应仍不兼容

A2A 返回的是 JSON-RPC SSE：

```text
event:jsonrpc
data:{"jsonrpc":"2.0","result":{"artifactUpdate":...}}
```

而前端通常期望的是 UI 可识别的 SSE 数据，例如：

```json
{
  "success": true,
  "custom_rsp_data": {
    "event": "summary",
    "content": "模型输出内容"
  }
}
```

因此需要同时解决：

```text
RESTful 请求 → A2A JSON-RPC 请求
A2A JSON-RPC SSE → RESTful/UI SSE
```

---

## 3. 原因分析

### 3.1 前端协议与后端协议不一致

当前存在两个不同协议：

| 层面 | 前端期望 | 当前后端 |
|---|---|---|
| URL | `/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}` | `/a2a` |
| 协议 | RESTful Conversation API | A2A JSON-RPC |
| 请求体 | `{query}` / `{inputs}` | `jsonrpc + method + params.message` |
| 流式响应 | 前端自定义 SSE | A2A JSON-RPC SSE |
| 会话字段 | `conversation_id` | `contextId/sessionId` |

根因一句话：

> 前端是 RESTful Conversation API 语义，后端是 A2A JSON-RPC 语义，中间缺少协议适配层。

---

### 3.2 A2A JSON-RPC 对请求结构有强约束

现有 A2A Controller 会使用 SDK 解析 JSON-RPC 请求：

```java
request = JSONRPCUtils.parseRequestBody(body, null);
```

因此普通 RESTful body 不能被当作合法 A2A 请求。

A2A 请求至少需要包含：

| 字段 | 说明 |
|---|---|
| `jsonrpc` | 固定为 `2.0` |
| `method` | 流式请求为 `SendStreamingMessage` |
| `id` | JSON-RPC 请求 ID |
| `params.message.role` | 用户角色 |
| `params.message.messageId` | 消息 ID |
| `params.message.contextId` | 会话 ID |
| `params.message.parts[].text` | 用户输入文本 |

---

### 3.3 A2A 流式响应不是前端 RESTful UI 格式

A2A 返回：

```text
event:jsonrpc
data:{"jsonrpc":"2.0","id":"...","result":{"statusUpdate":...}}
```

前端展示通常需要：

```json
{
  "success": true,
  "custom_rsp_data": {
    "event": "summary",
    "content": "..."
  }
}
```

所以响应侧也必须做协议转换。

---

### 3.4 examples 中已有相反方向的参考

`examples` 下存在调用该 RESTful 路径的 Versatile 样例：

```text
examples/agent-runtime-a2a-versatile-parent-e2e
examples/agent-runtime-a2a-versatile-e2e
```

相关配置：

```yaml
versatile:
  url: ${VERSATILE_URL:http://localhost:18083/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}}
```

但该样例方向是：

```text
A2A → RESTful Versatile API
```

当前需要的是反方向：

```text
RESTful → A2A
```

因此 examples 不能直接复用为入口 Controller，但可以参考其适配思想。

可参考组件：

```text
agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/versatile/VersatileAgentRuntimeHandler.java
agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/versatile/VersatileMessageAdapter.java
agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/versatile/VersatileClient.java
agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/versatile/VersatileStreamAdapter.java
```

---

## 4. 短期修改建议

### 4.1 修改范围

短期建议只在 EDPAgent spike 中增加适配层：

```text
agent-store/edp-agent-trae/spike
```

建议新增：

```text
agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/rest/RestfulA2aAdapterController.java
```

目的不是产品化，而是快速完成穿刺验证。

---

### 4.2 短期架构

```text
前端
  ↓
POST /v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
  ↓
RestfulA2aAdapterController
  ↓ 构造 A2A JSON-RPC
A2aJsonRpcController.handleSse(...)
  ↓
EdpaRuntimeHandler
  ↓
DeepAgent
  ↓
A2A JSON-RPC SSE
  ↓
RestfulA2aAdapterController 转换 SSE
  ↓
前端展示
```

---

### 4.3 RESTful → A2A 字段映射

| RESTful 字段 | A2A 字段 |
|---|---|
| `project_id` | `metadata.projectId` |
| `agent_id` | `metadata.agentId` |
| `conversation_id` | `contextId` |
| `conversation_id` | `metadata.sessionId` |
| `query/content/inputs.query` | `parts[0].text` |
| `userId` | `metadata.userId` |
| `roleName` | `metadata.roleName` |
| `X-Tenant-Id` | 传给 A2A Controller |

---

### 4.4 短期实现方式

不要通过 HTTP 再请求本机 `/a2a`，而是直接注入：

```java
A2aJsonRpcController
```

然后调用：

```java
handleSse(a2aJson, tenantId)
```

优点：

- 少一次网络调用
- 避免端口依赖
- 避免 HTTP header 二次转发问题
- 快速验证 RESTful → A2A 可行性

---

### 4.5 短期请求示例

前端调用：

```http
POST http://localhost:8190/v1/demo-project/agents/edp-agent/conversations/conv-001
Content-Type: application/json
Accept: text/event-stream
```

请求体：

```json
{
  "query": "帮我推荐几款稳健型理财产品",
  "userId": "1",
  "roleName": "mobile-bank"
}
```

适配器内部构造：

```json
{
  "jsonrpc": "2.0",
  "method": "SendStreamingMessage",
  "id": "rest-adapter-1710000000000",
  "params": {
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-1710000000000",
      "contextId": "conv-001",
      "metadata": {
        "projectId": "demo-project",
        "agentId": "edp-agent",
        "sessionId": "conv-001",
        "userId": "1",
        "roleName": "mobile-bank",
        "source": "restful-a2a-adapter"
      },
      "parts": [
        {
          "text": "帮我推荐几款稳健型理财产品"
        }
      ]
    }
  }
}
```

---

### 4.6 短期响应转换

A2A JSON-RPC SSE 到前端 SSE 的建议映射：

| A2A JSON-RPC SSE | RESTful/UI SSE |
|---|---|
| `statusUpdate.status.state = TASK_STATE_SUBMITTED` | `conversation_start` |
| `statusUpdate.status.state = TASK_STATE_WORKING` | `progress` |
| `artifactUpdate.artifact.parts[].text` | `summary` |
| `statusUpdate.status.state = TASK_STATE_INPUT_REQUIRED` | `interrupt_start` |
| `statusUpdate.status.state = TASK_STATE_COMPLETED` | `conversation_end` |
| `TASK_STATE_FAILED/CANCELED/REJECTED` | `stream_error` |
| JSON-RPC `error` | `stream_error` |

输出给前端的 SSE 示例：

```text
data: {"success":true,"custom_rsp_data":{"event":"conversation_start","content":"TASK_STATE_SUBMITTED"}}

data: {"success":true,"custom_rsp_data":{"event":"summary","content":"这里是模型输出内容..."}}

data: {"success":true,"custom_rsp_data":{"event":"conversation_end","content":"TASK_STATE_COMPLETED"}}
```

---

### 4.7 短期验收标准

新增一个 P18 穿刺点：

```text
P18 RESTful → A2A 协议适配验证
```

验收项：

1. 前端能调用：

```text
POST http://localhost:8190/v1/demo-project/agents/edp-agent/conversations/conv-001
```

2. 请求体：

```json
{
  "query": "帮我推荐几款稳健型理财产品"
}
```

3. 后端日志能看到内部构造：

```text
method=SendStreamingMessage
contextId=conv-001
agentId=edp-agent
```

4. A2A runtime 能正常执行。
5. 前端能收到并显示 SSE 内容。

---

## 5. 长期修改建议

### 5.1 不建议长期放在 EDPAgent

RESTful → A2A 不是 EDPAgent 专属能力，而是 runtime 通用协议适配能力。

如果只放在：

```text
agent-store/edp-agent-trae
```

会导致：

- 其他 agent 无法复用
- 每个 agent 都要重复实现 RESTful adapter
- RESTful/A2A 映射规则分散
- 前端协议与 runtime 协议长期不统一

---

### 5.2 长期应上移到 `agent-runtime`

建议在：

```text
agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/
```

新增通用组件：

```text
RestfulConversationController.java
RestfulToA2aMessageAdapter.java
A2aToRestfulSseAdapter.java
```

长期架构：

```text
前端 RESTful
  ↓
agent-runtime RestfulConversationController
  ↓
RestfulToA2aMessageAdapter
  ↓
A2A RequestHandler / A2aAgentExecutor
  ↓
任意 AgentRuntimeHandler
  ↓
A2aToRestfulSseAdapter
  ↓
前端 SSE
```

---

### 5.3 长期 Controller 入口

```java
@PostMapping(
    value = "/v1/{projectId}/agents/{agentId}/conversations/{conversationId}",
    produces = MediaType.TEXT_EVENT_STREAM_VALUE
)
```

这个接口应成为 runtime 的统一 RESTful Conversation API。

---

### 5.4 长期需要统一的协议规则

| 能力 | 建议归属 |
|---|---|
| URL path 到 A2A metadata 映射 | `agent-runtime` |
| `conversation_id` 到 `contextId/sessionId` 映射 | `agent-runtime` |
| `query/content/inputs` 提取 | `agent-runtime` |
| tenant header 处理 | `agent-runtime` |
| A2A JSON-RPC 构造 | `agent-runtime` |
| A2A SSE 到 RESTful SSE 转换 | `agent-runtime` |
| 中断/恢复状态映射 | `agent-runtime` |
| 错误 envelope 统一 | `agent-runtime` |

---

### 5.5 EDPAgent 长期只保留业务能力

EDPAgent 应只负责：

| 内容 | 位置 |
|---|---|
| `EdpaRuntimeHandler` | EDPAgent |
| 业务 Rail | EDPAgent |
| 业务 Tool | EDPAgent |
| `edp-agent.yaml` | EDPAgent |
| `edp-config.yaml` | EDPAgent |
| prompt / scope / limit / MCP / LiteTodo | EDPAgent |

即：

```text
agent-runtime 负责协议
edp-agent-trae 负责业务
```

---

## 6. 推荐落地路线

### 阶段 1：短期穿刺

在 EDPAgent spike 中新增 RESTful adapter：

```text
RestfulA2aAdapterController
```

目标：

```text
验证 RESTful 前端可以不理解 A2A，也能调用 EDPAgent。
```

### 阶段 2：补充 P18 测试

新增：

```text
P18RestfulToA2aAdapterSpikeTest
```

覆盖：

- RESTful body 提取 query
- path 参数映射到 metadata
- conversationId 映射到 contextId
- A2A artifactUpdate 映射为 summary
- completed 映射为 conversation_end

### 阶段 3：前端回归 RESTful preset

让前端使用已有：

```text
/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
```

不再需要 A2A preset。

### 阶段 4：产品化上移

穿刺稳定后，将 adapter 从：

```text
agent-store/edp-agent-trae/spike
```

上移到：

```text
agent-runtime
```

形成通用能力。

---

## 7. 最终结论

### 问题现象

前端只能支持 RESTful Conversation API，而当前 EDPAgent 只能通过 A2A JSON-RPC `/a2a` 调用。

### 根本原因

缺少：

```text
RESTful → A2A JSON-RPC
A2A JSON-RPC SSE → RESTful/UI SSE
```

的协议适配层。

### 短期建议

在：

```text
agent-store/edp-agent-trae/spike
```

新增 `RestfulA2aAdapterController`，快速完成 P18 穿刺。

### 长期建议

将该能力沉淀到：

```text
agent-runtime
```

作为所有 Agent 通用的 RESTful Conversation API 入口。

最终目标：

```text
前端只懂 RESTful
runtime 负责协议适配
EDPAgent 只负责业务能力
```
