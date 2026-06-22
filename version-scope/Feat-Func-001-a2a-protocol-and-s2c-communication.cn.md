---
scope: version
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-001
status: active
dependency:
  - agent-runtime-release-features.cn.md
  - ../architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-001-a2a-protocol-and-s2c-communication.md
---

# A2A 协议标准与 S2C 通讯模型 — 黑盒行为说明

## 1. 特性定位

agent-runtime 以 Google A2A 协议作为唯一的对外协议标准。北向对外暴露 A2A JSON-RPC 服务端点，任意 A2A 客户端可通过标准化接口发现和调用 Agent。三种 S2C 通讯模式（阻塞请求-响应、流式 SSE、异步 task 查询）通过选择对应的 A2A 方法实现。

- **解决的问题**：Agent 需要一个标准化、可互操作的对外协议。A2A 提供了 Agent Card 发现、JSON-RPC 调用、SSE 流式响应、Task 生命周期管理等完整能力。
- **适用场景**：所有需要对外暴露 Agent 的场景。A2A 客户端可以是其他 Agent、前端应用、CI/CD 流水线等任意 HTTP 客户端。

## 2. 对外能力边界

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| SendStreamingMessage | ✅ | 流式 SSE 消息，Agent 全流程主入口 |
| SendMessage | ✅ | 阻塞请求-响应（A2A 层收集 Stream 后返回 JSON） |
| GetTask | ✅ | 按 taskId 查询任务状态与结果 |
| CancelTask | ✅ | 取消执行中任务（OpenJiuwen 仅阻止消费，不中断 LLM） |
| ListTasks | ✅ | 任务列表查询 |
| SubscribeToTask | ✅ | 断线重连恢复订阅 SSE 流 |
| Push Notification Config CRUD | ✅ | Create/Get/List/Delete Push 配置（SDK 层支持，实际推送未激活） |
| Agent Card 发现 | ✅ | `GET /.well-known/agent-card.json` + `/.well-known/agent.json` |
| Agent Card YAML 配置 | ✅ | YAML 驱动 + Handler 声明 + 自动生成 |
| Agent Card skills 声明 | ✅ | YAML 或 AgentCardProvider 声明 skills，供远程 Agent 发现并注册为 Tool |
| Agent Card capabilities 声明 | ✅ | streaming / pushNotifications 等能力宣告 |
| JSON-RPC 错误处理 | ✅ | Method Not Found / Invalid Request / Parse Error / Internal Error |
| Tenant 标识传播 | ✅ | `X-Tenant-Id` 头提取，贯穿调用链路 |
| Push Notification 实际推送 | ⬜ | SDK 组件已装配，推送未激活 |
| gRPC 传输 | ⬜ | 当前仅 HTTP + SSE |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| A2A 方法名 snake_case 形式 | A2A SDK 使用 PascalCase（`SendStreamingMessage`），`message/stream` 形式不被 parser 识别 | 统一使用 CamelCase |
| 多 Agent 路由 | 当前仅选取第一个 Handler Bean | 每个 Agent 部署独立 runtime 实例 |
| Tenant 认证 | runtime 不认证 tenant header，仅传播 | 在 /a2a 前放置认证网关 |

## 3. 外部行为与用户场景

### 3.1 外部接口

| 端点 | 方法 | Accept | 说明 |
|------|------|--------|------|
| `/.well-known/agent-card.json` | GET | — | Agent 能力发现 |
| `/.well-known/agent.json` | GET | — | 兼容端点 |
| `/a2a` | POST | `application/json` | 阻塞 JSON-RPC |
| `/a2a` | POST | `text/event-stream` | 流式 SSE |

### 3.2 用户示例

#### 3.2.1 流式调用

```bash
# 前置条件：runtime 已启动在 localhost:8080
SESSION_ID="test-$(date +%s)"

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
        "contextId": "'"$SESSION_ID"'",
        "parts": [{"text": "你好"}]
      }
    }
  }' --no-buffer

# 预期结果：SSE 流，event=jsonrpc，包含 ArtifactUpdate 和最终 TaskStatusUpdate(COMPLETED)
```

#### 3.2.2 查询任务

```bash
# 前置条件：已知 taskId
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "GetTask",
    "id": "1",
    "params": {"id": "task-id-from-previous-response"}
  }'

# 预期结果：JSON Task 对象，含 status.state 和 artifacts
```

#### 3.2.3 取消任务

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "CancelTask",
    "id": "1",
    "params": {"id": "task-id-from-previous-response"}
  }'
```

### 3.3 E2E 流程

```
A2A Client                    Runtime
  │                              │
  │── GET agent-card.json ──────>│  发现 Agent 能力
  │<── AgentCard JSON ──────────│
  │                              │
  │── POST /a2a SendStreamingMessage ──>│
  │<── SSE: TaskAccepted ───────│  Task → WORKING
  │<── SSE: ArtifactUpdate ─────│  Agent 增量输出
  │<── SSE: ArtifactUpdate ─────│
  │<── SSE: TaskStatusUpdate ───│  COMPLETED
  │                              │
  │── POST /a2a GetTask ────────>│  查询最终状态
  │<── Task JSON ───────────────│
```

---
