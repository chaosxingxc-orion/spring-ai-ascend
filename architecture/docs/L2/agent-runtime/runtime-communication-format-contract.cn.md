---
level: L2
view: development
status: draft
parent: architecture/docs/L1/agent-runtime/ARCHITECTURE.md
authority: "ADR-0154 generated facts + agent-runtime A2A metadata proposal"
last_updated: 2026-06-17
---

# Agent Runtime 通信格式契约

本文定义 `agent-runtime` 在 A2A 入口、AgentExecutionContext、State、Memory、远端 A2A tool 等链路上的通信字段约定。目标是让测试团队和客户开发团队能按同一套格式构造请求、定位问题、验证中间件行为。

事实锚点：

- `code-symbol/com-huawei-ascend-runtime-engine-a2a-a2aagentexecutor`
- `code-symbol/com-huawei-ascend-runtime-engine-agentexecutioncontext`
- `code-symbol/com-huawei-ascend-runtime-common-runtimeidentity`
- `code-symbol/com-huawei-ascend-runtime-common-runtimemessage`
- `code-symbol/com-huawei-ascend-runtime-engine-spi-memoryprovider`
- `code-symbol/com-huawei-ascend-runtime-engine-spi-memoryprovider-memoryhit`
- `code-symbol/com-huawei-ascend-runtime-engine-spi-memoryprovider-memoryrecord`

## 1. 基本原则

1. `params.metadata` 是 runtime metadata 的唯一入口。
2. `params.message` 只表达用户或 Agent 消息本体：角色、文本 parts、`messageId`、可选 `contextId`。
3. `message.metadata` 只作为 A2A message extension 保留；runtime 不解释其中字段，也不再把它作为 identity、state、memory、trajectory 的 fallback。
4. A2A bridge 进入 handler 前会把 request-level metadata 标准化为 `AgentExecutionContext.variables`，并构造 `RuntimeIdentity`。
5. State 是短期会话或检查点语义，默认由 `agentStateKey` 绑定。
6. Memory 是长期记忆语义，推荐由 `memoryScope` 或 `(tenantId, userId)` 绑定，不应默认绑定一次性的 `taskId`。
7. 远端 A2A 调用透传标准化后的 request-level metadata，保证下游 agent 可以看到同一组 tenant、user、correlation、state、memory 信息。

## 2. 请求级 Metadata 字段

| 字段 | 类型 | 必填 | 默认/派生规则 | 主要用途 |
|---|---:|---:|---|---|
| `tenantId` | string | 否 | 缺失时为 `default` | `RuntimeIdentity.tenantId`、日志、State/Memory 默认分区 |
| `userId` | string | 否 | 缺失时为 `system` | `RuntimeIdentity.userId`、Memory 默认分区、审计 |
| `agentId` | string | 否 | 缺失时为当前 `AgentRuntimeHandler.agentId()` | `RuntimeIdentity.agentId`、State 默认 key |
| `agentStateKey` | string | 否 | 显式值优先；否则 `state:{tenantId}:{agentId}:{sessionId}` | State/checkpointer、WaitForUser resume |
| `memoryScope` | string | 否 | 缺失时为 `memory:{tenantId}:{userId}` | MemoryProvider 检索和写入 scope |
| `correlationId` | string | 否 | 显式值优先；否则 `message.messageId`；再否则 `taskId` | MDC、trajectory、日志 |
| `traceId` | string | 否 | 显式值优先；否则 `correlationId` | trace/trajectory |

`sessionId` 不放在 metadata 中。runtime 默认从 `message.contextId` 读取；缺失时回退到 `taskId`。

## 3. 用户输入 A2A 请求

推荐格式：

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "SendMessage",
  "params": {
    "metadata": {
      "tenantId": "demo",
      "userId": "xuefanfan-4",
      "agentId": "hotel-agent",
      "agentStateKey": "hotel:demo:xuefanfan-4:2026-06-18",
      "memoryScope": "memory:demo:xuefanfan-4",
      "correlationId": "corr-001"
    },
    "message": {
      "role": "ROLE_USER",
      "messageId": "m1",
      "contextId": "ctx-hotel-001",
      "parts": [
        {
          "text": "帮我在上海定个 6/18-6/20 四星以上 800 以内的酒店"
        }
      ]
    }
  }
}
```

不推荐格式：

```json
{
  "params": {
    "message": {
      "role": "ROLE_USER",
      "messageId": "m1",
      "metadata": {
        "tenantId": "demo",
        "userId": "xuefanfan-4",
        "agentStateKey": "hotel-state"
      },
      "parts": [
        {
          "text": "帮我订酒店"
        }
      ]
    }
  }
}
```

上面的 `message.metadata` 会被 runtime 忽略。请求仍可执行，但 `tenantId`、`userId`、`agentStateKey` 不会从这里生效。

如果前端没有在 `params.metadata` 提供这些字段，runtime 会使用默认值和派生值：

```text
tenantId      = default
userId        = system
agentId       = 当前 handler.agentId()
sessionId     = message.contextId；如果缺失则使用 taskId
agentStateKey = state:{tenantId}:{agentId}:{sessionId}
memoryScope   = memory:{tenantId}:{userId}
correlationId = message.messageId；如果缺失则使用 taskId
traceId       = correlationId
```

以上面的不推荐请求为例，假设当前 handler 是 `hotel-agent`、`message.contextId` 是 `ctx-001`，则即使 `message.metadata` 写了 `tenantId=demo` 和 `agentStateKey=hotel-state`，runtime 仍会得到：

```text
tenantId      = default
userId        = system
agentId       = hotel-agent
agentStateKey = state:default:hotel-agent:ctx-001
memoryScope   = memory:default:system
```

## 4. A2A 到 AgentExecutionContext

A2A bridge 标准化后，handler 看到的是 `AgentExecutionContext`：

```text
RuntimeIdentity
  tenantId = params.metadata.tenantId or default
  userId = params.metadata.userId or system
  sessionId = message.contextId or taskId
  taskId = A2A task id
  agentId = params.metadata.agentId or handler.agentId()

variables
  tenantId
  userId
  agentId
  agentStateKey
  memoryScope
  correlationId
  traceId
  其他 request-level metadata

messages
  RuntimeMessage.user(<message.parts text>)
```

如果请求没有显式 `agentStateKey`，默认 key 为：

```text
state:{tenantId}:{agentId}:{sessionId}
```

如果请求没有显式 `memoryScope`，默认 scope 为：

```text
memory:{tenantId}:{userId}
```

## 5. State 与 Checkpointer

State 用于短期会话恢复、检查点和 WaitForUser resume。推荐由调用方显式传入稳定的 `agentStateKey`：

```json
{
  "metadata": {
    "tenantId": "demo",
    "userId": "u1",
    "agentId": "order-agent",
    "agentStateKey": "order:demo:u1:20260617"
  }
}
```

OpenJiuwen 场景下，runtime 会把 `agentStateKey` 传给 OpenJiuwen 的会话或 checkpointer 机制。业务如果希望跨多轮保持同一短期上下文，应复用同一个 `agentStateKey`，不要依赖每轮新建的 `taskId`。

## 6. Memory

Memory 是长期记忆，和一次性 task 解耦。推荐请求传入：

```json
{
  "metadata": {
    "tenantId": "demo",
    "userId": "xuefanfan-4",
    "memoryScope": "memory:demo:xuefanfan-4"
  }
}
```

MemoryProvider 的读取语义可以理解为：

```java
List<MemoryHit> search(AgentExecutionContext context, String query, int limit)
```

MemoryProvider 的写入语义可以理解为：

```java
void save(AgentExecutionContext context, List<MemoryRecord> records)
```

OpenJiuwen adapter 通过 runtime rail 在模型调用前检索长期记忆，并把命中的记忆作为工具/上下文片段注入给 Agent。注入内容不应写回长期记忆，避免“检索结果被再次保存”导致记忆污染。

## 7. 远端 A2A Tool 调用

当一个 Agent 调用另一个远端 A2A Agent 时，上游 runtime 会把标准化后的 request-level metadata 透传给下游：

```json
{
  "metadata": {
    "tenantId": "demo",
    "userId": "xuefanfan-4",
    "agentId": "downstream-agent",
    "agentStateKey": "hotel:demo:xuefanfan-4:2026-06-18",
    "memoryScope": "memory:demo:xuefanfan-4",
    "correlationId": "corr-001",
    "traceId": "corr-001"
  },
  "message": {
    "role": "ROLE_USER",
    "messageId": "remote-msg-001",
    "contextId": "ctx-001",
    "parts": [
      {
          "text": "用户希望在上海出差期间安排 6/18 晚上的晚餐和第二天去虹桥站的交通，请结合已选酒店给出行程建议。"
      }
    ]
  }
}
```

注意：

- 下游 `agentId` 可以是远端目标 agent，也可以由远端 handler 自行回退到 `handler.agentId()`。
- `message.metadata` 不参与下游 runtime identity。
- `correlationId` 和 `traceId` 应保持透传，便于跨 Agent 追踪。

## 8. 返回格式

成功返回仍遵循 A2A task 结构：

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "task": {
      "id": "task-001",
      "contextId": "ctx-001",
      "status": {
        "state": "TASK_STATE_COMPLETED",
        "message": {
          "role": "ROLE_AGENT",
          "parts": [
            {
              "text": "已完成。"
            }
          ],
          "messageId": "agent-message-001",
          "contextId": "ctx-001",
          "taskId": "task-001"
        }
      },
      "artifacts": [],
      "history": []
    }
  }
}
```

失败返回应包含 runtime error code、retryable 信息和可观测 message。具体 wire 形态仍以 `A2aResultRouter` 和错误 envelope 的实现为准。

## 9. 日志与 MDC

运行时应在 MDC 中记录：

| MDC 字段 | 来源 |
|---|---|
| `tenantId` | request-level metadata 或 `default` |
| `agentId` | request-level metadata 或 handler agentId |
| `taskId` | A2A task id |
| `contextId` | message contextId 或 taskId |

MDC 是 Mapped Diagnostic Context，用于把这些字段挂到当前执行线程的日志上下文，方便日志聚合系统按租户、任务、会话和 Agent 过滤。

## 10. 测试矩阵

| 场景 | 输入 | 期望 |
|---|---|---|
| 标准用户请求 | `params.metadata.tenantId/userId/agentId` | `RuntimeIdentity` 使用这些字段 |
| 缺失 tenant | 不传 `tenantId` | tenant 回退为 `default` |
| 缺失 user | 不传 `userId` | user 回退为 `system` |
| 缺失 agent | 不传 `agentId` | agent 回退为 `handler.agentId()` |
| 显式 state | 传 `agentStateKey` | State/checkpointer 使用显式 key |
| 默认 state | 不传 `agentStateKey` | 使用 `state:{tenantId}:{agentId}:{sessionId}` |
| 显式 memory | 传 `memoryScope` | MemoryProvider 使用显式 scope |
| 默认 memory | 不传 `memoryScope` | 使用 `memory:{tenantId}:{userId}` |
| message metadata | `message.metadata` 携带 tenant/user | 请求可运行，但 runtime identity 使用 request metadata 或默认值 |
| 远端 A2A | 上游调用远端工具 | 下游收到标准化 request-level metadata |

## 11. 迁移要求

1. README、curl、测试代码里新写的 tenant/user/state/memory 字段必须放在 `params.metadata`。
2. 旧样例中 `params.message.metadata` 的 runtime 字段应迁移到 `params.metadata`；迁移前可保留测试覆盖，确认这些字段被 runtime 忽略。
3. 需要跨任务长期记忆时，不要只依赖默认 `agentStateKey`；应显式传入稳定的 `memoryScope`，或在 provider 中按 `(tenantId, userId)` 分区。
4. 需要多轮短期状态时，应显式传入稳定的 `agentStateKey`；否则默认 key 会随 `contextId` 或 `taskId` 变化。
5. 远端 A2A tool 不应自行重组 metadata；应使用 A2A layer 标准化后的 request metadata 透传。
