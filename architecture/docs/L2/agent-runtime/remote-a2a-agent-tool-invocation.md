---
level: L2
module: agent-runtime
feature: remote-a2a-agent-tool-invocation
---

# 远端 A2A Agent 工具化调用特性

---

## 1. 概述

远端 A2A Agent 工具化调用，是 `agent-runtime` 把一个或多个远端 A2A Agent 暴露成本地 OpenJiuwen Agent 可选 tool 的能力。配置里的 `agent-runtime.remote-agents[].url` 不是要求对方一定也是本项目的 runtime，而是要求该 URL 能被 A2A Java SDK 解析出 Agent Card，并能从 Agent Card 找到可调用的 JSON-RPC endpoint。

外在表现从远端 Agent Card 发现开始，而不是从用户发起一次任务才开始：

```text
部署配置
  -> 声明 agent-runtime.remote-agents[].url
  -> runtime 后台读取远端 A2A Agent Card
  -> 从 card 中解析 name/description/skills.description 和 JSON-RPC endpoint
  -> 生成 RemoteAgentToolSpec
  -> 注入本地 OpenJiuwen Agent 的 placeholder Tool 及其 ToolCard，并注册 interrupt rail
  -> 本地 OpenJiuwen Agent 看到一个 a2a_remote_xxx tool

用户请求
  -> LLM 选择 a2a_remote_xxx 并传入 {"message":"..."}
  -> OpenJiuwen rail 中断本地推理
  -> agent-runtime 调远端 A2A sendMessageStreaming
  -> 远端 progress 投影到本地 parent task
  -> 远端 completed 后，把完成文本作为 tool result 回灌给本地 OpenJiuwen
  -> 本地 OpenJiuwen 继续推理，产出最终 answer
```

用户侧始终只面对本地 parent task。远端 `taskId/contextId` 被保存在本地 task metadata 里，用于 input-required 续写和取消传播。

---

## 2. 远端 Agent Card 到本地 tool

### 2.1 配置入口

```yaml
agent-runtime:
  remote-agents:
    - url: http://localhost:18082
```

`url` 可以是远端服务根地址，也可以是 A2A well-known card 地址。当前实现会把这些等价配置归一到同一个远端入口，避免重复生成 tool：

```text
http://remote-agent-url
http://remote-agent-url/
http://remote-agent-url/.well-known/agent-card.json
```

### 2.2 当前使用的 Agent Card 字段

`RemoteAgentCardCache` 解析远端 Agent Card 后，只消费以下字段：

| Agent Card 字段 | 用途 |
|---|---|
| `name` | 生成稳定的 `remoteAgentId`，再生成 tool name |
| `description` | 拼入 tool description，给本地 LLM 判断工具用途 |
| `skills[].description` | 拼入 tool description，补充远端能力描述 |
| `supportedInterfaces[].protocolBinding` | 优先选择 `JSONRPC` 接口 |
| `supportedInterfaces[].url` | JSON-RPC 出站调用 endpoint；相对地址会按配置 URL 解析 |
| `url` | 当没有可用 `supportedInterfaces` 时作为 fallback endpoint |

当前不会用 `skills[].id/name/tags` 生成独立 tool，也不会用 `capabilities/defaultInputModes/defaultOutputModes` 做能力过滤。换句话说，一个远端 Agent Card 当前最多生成一个本地 tool。

### 2.3 生成的 RemoteAgentToolSpec

假设远端 card 是：

```json
{
  "name": "Remote Planner",
  "description": "Plans trips",
  "url": "/a2a",
  "supportedInterfaces": [
    { "protocolBinding": "JSONRPC", "url": "/a2a" }
  ],
  "skills": [
    { "id": "plan", "name": "Plan", "description": "Create a step-by-step plan" }
  ]
}
```

本地生成的 `RemoteAgentToolSpec` 形态是：

```json
{
  "remoteAgentId": "remote-planner",
  "toolName": "a2a_remote_remote_planner",
  "description": "Remote Planner\nPlans trips\nCreate a step-by-step plan",
  "inputSchema": {
    "type": "object",
    "properties": {
      "message": {
        "type": "string",
        "description": "Input message sent to the remote A2A runtime."
      }
    },
    "required": ["message"]
  }
}
```

如果多个远端 card 的 `name` 归一化后相同，后发现的可用远端会加后缀：

```text
remoteAgentId: shared-remote
toolName:      a2a_remote_shared_remote

remoteAgentId: shared-remote-2
toolName:      a2a_remote_shared_remote_2
```

`remoteAgentId` 一旦分配给某个配置项，后续刷新会保持稳定，避免缓存 transport 或已经 parked 的 input-required task 被重新路由。

---

## 3. 注入 OpenJiuwen 后长什么样

### 3.1 携带 ToolCard 的 placeholder Tool

`OpenJiuwenRemoteToolInstaller` 在本地 OpenJiuwen Agent 执行前安装一个同名 placeholder `Tool`。这个 `Tool` 携带下面的 `ToolCard`，所以 LLM 能在工具列表中看到远端 Agent：

```json
{
  "id": "a2a_remote_remote_planner",
  "name": "a2a_remote_remote_planner",
  "description": "Remote Planner\nPlans trips\nCreate a step-by-step plan",
  "inputParams": {
    "type": "object",
    "properties": {
      "message": {
        "type": "string",
        "description": "Input message sent to the remote A2A runtime."
      }
    },
    "required": ["message"]
  },
  "properties": {
    "runtime.remoteAgentId": "remote-planner"
  }
}
```

placeholder `Tool` 本身不是远端调用实现，只是让 OpenJiuwen resource manager 能解析这个 tool 名。真实调用由后面的 interrupt rail 接管。

如果 rail 没有拦截到这个调用，placeholder 的返回值是：

```json
{
  "error": "REMOTE_AGENT_TOOL_NOT_INTERRUPTED",
  "message": "Remote A2A tools must be intercepted by the runtime interrupt rail."
}
```

### 3.2 interrupt rail

真实的远端调用由 `OpenJiuwenRemoteAgentInterruptRail` 触发。它注册到 OpenJiuwen Agent 上，只监听当前已可用的远端 tool name。

当 LLM 发出 tool call：

```json
{
  "id": "tool-call-1",
  "name": "a2a_remote_remote_planner",
  "arguments": "{\"message\":\"hello remote\"}"
}
```

rail 不直接访问远端，而是抛出 OpenJiuwen interrupt。interrupt request 的 context 形态如下：

```json
{
  "runtime.remote.kind": "REMOTE_AGENT_INVOCATION",
  "runtime.remote.agentId": "remote-planner",
  "runtime.remote.toolName": "a2a_remote_remote_planner",
  "runtime.remote.toolCallId": "tool-call-1",
  "runtime.remote.parentTaskId": "task-1",
  "runtime.remote.parentContextId": "ctx-1",
  "runtime.remote.localConversationId": "conversation-1",
  "runtime.remote.arguments": {
    "message": "hello remote"
  }
}
```

如果 `arguments` 不是合法 JSON object，当前实现会退化为：

```json
{
  "runtime.remote.arguments": {
    "message": "<原始 arguments 字符串>"
  }
}
```

---

## 4. 本地 Agent 到远端 A2A 的请求

### 4.1 OpenJiuwen interrupt 到 RemoteInvocation

`OpenJiuwenStreamAdapter` 读取 OpenJiuwen 返回 map：

```json
{
  "result_type": "interrupt",
  "state": [
    {
      "payload": {
        "value": {
          "context": {
            "runtime.remote.kind": "REMOTE_AGENT_INVOCATION"
          }
        }
      }
    }
  ]
}
```

只要 context 中 `runtime.remote.kind` 是 `REMOTE_AGENT_INVOCATION`，就映射为：

```json
{
  "type": "INTERRUPTED",
  "interruptPayload": "RemoteAgentInterrupt",
  "remoteInvocation": {
    "remoteAgentId": "remote-planner",
    "toolName": "a2a_remote_remote_planner",
    "toolCallId": "tool-call-1",
    "parentTaskId": "task-1",
    "parentContextId": "ctx-1",
    "localConversationId": "conversation-1",
    "arguments": {
      "message": "hello remote"
    }
  }
}
```

`A2aResultRouter` 收到这个 `INTERRUPTED` 后停止消费本地 OpenJiuwen 第一段输出，并把远端调用交给 `A2aRemoteInvocationOrchestrator`。

### 4.2 首次发给远端的 MessageSendParams

`A2aRemoteAgentOutboundAdapter` 通过 A2A Java SDK 调远端 `sendMessageStreaming`。首次远端调用时，发送的 `MessageSendParams` 等价为：

```json
{
  "message": {
    "role": "ROLE_USER",
    "messageId": "<runtime-generated-uuid>",
    "parts": [
      { "text": "hello remote" }
    ]
  },
  "metadata": {
    "message": "hello remote"
  }
}
```

字段来源：

| 发送字段 | 来源 |
|---|---|
| `message.role` | 固定 `ROLE_USER` |
| `message.messageId` | 本地生成 UUID |
| `message.parts[0].text` | tool arguments 里的 `message` 字段 |
| `message.taskId` | 首次调用为空 |
| `message.contextId` | 首次调用为空 |
| `metadata` | 原始 tool arguments |

因此，远端 Agent 看到的是一个普通 A2A user message，不知道自己是被本地 Agent 当成 tool 调用。

### 4.3 input-required 后续写给远端的 MessageSendParams

当远端先返回 `INPUT_REQUIRED`，本地 parent task 会停在 input-required。用户下一轮仍然调本地 `/a2a`，本地 runtime 从 parent task metadata 找到远端 route，并直接续写远端，不先调用本地 OpenJiuwen。

续写时发送给远端的 `MessageSendParams` 等价为：

```json
{
  "message": {
    "role": "ROLE_USER",
    "messageId": "<runtime-generated-uuid>",
    "taskId": "remote-task-1",
    "contextId": "remote-ctx-1",
    "parts": [
      { "text": "用户补充输入" }
    ]
  },
  "metadata": {}
}
```

---

## 5. 远端 A2A 返回如何映射

`A2aRemoteAgentOutboundAdapter` 只把远端 streaming 事件映射成五类 `RemoteAgentResult`：

| 远端事件 | 条件 | 本地结果 |
|---|---|---|
| `Message` | 任意 agent message | `MESSAGE(text, remoteTaskId, remoteContextId, metadata)` |
| `TaskArtifactUpdateEvent` | artifact update | `ARTIFACT(text, remoteTaskId, remoteContextId, metadata)` |
| `TaskStatusUpdateEvent` 或 `Task` | `state=TASK_STATE_INPUT_REQUIRED` | `INPUT_REQUIRED(text, remoteTaskId, remoteContextId, metadata)` |
| `TaskStatusUpdateEvent` 或 `Task` | `state=TASK_STATE_COMPLETED` | `COMPLETED(text, remoteTaskId, remoteContextId, metadata)` |
| `TaskStatusUpdateEvent` 或 `Task` | 其他 final state | `FAILED(text, remoteTaskId, remoteContextId, metadata)` |

`text` 的提取规则统一使用 `Messages.text(...)`：只取 `TextPart`，多个 text part 用换行拼接。例如远端一个 message 有两个 text part `a`、`b`，本地得到的文本是：

```text
a
b
```

远端非 terminal 的 working/submitted 状态不会产出 `RemoteAgentResult`。

### 5.1 progress 投影

`MESSAGE` 和 `ARTIFACT` 都被当作远端 progress，只要 `text` 非空，就立即投影为本地 parent task 的 artifact：

```text
parent task artifact: TextPart("<remote progress text>")
```

这意味着外部用户通过本地 A2A stream 或 subscribe 看到的是 parent task 的流式进展，而不是远端 task 本身。

### 5.2 input-required 返回

远端返回：

```json
{
  "statusUpdate": {
    "taskId": "remote-task-1",
    "contextId": "remote-ctx-1",
    "status": {
      "state": "TASK_STATE_INPUT_REQUIRED",
      "message": {
        "role": "ROLE_AGENT",
        "parts": [
          { "text": "need more" }
        ]
      }
    }
  }
}
```

本地 parent task 会进入：

```json
{
  "status": {
    "state": "TASK_STATE_INPUT_REQUIRED",
    "message": {
      "role": "ROLE_AGENT",
      "parts": [
        { "text": "need more" }
      ]
    }
  },
  "metadata": {
    "runtime.waitingTarget": "REMOTE_AGENT",
    "runtime.remoteInvocationId": "tool-call-1",
    "runtime.remoteAgentId": "remote-planner",
    "runtime.remoteTaskId": "remote-task-1",
    "runtime.remoteContextId": "remote-ctx-1",
    "runtime.toolCallId": "tool-call-1",
    "runtime.localConversationId": "conversation-1"
  }
}
```

这个 input-required 不是本地 OpenJiuwen 的普通 user-input interrupt，而是 remote continuation 标记。下一轮本地执行时，`A2aAgentExecutor` 看到：

```text
task.status.state == TASK_STATE_INPUT_REQUIRED
metadata.runtime.waitingTarget == REMOTE_AGENT
```

就直接调用远端 continuation。

### 5.3 completed 返回

远端返回：

```json
{
  "statusUpdate": {
    "taskId": "remote-task-1",
    "contextId": "remote-ctx-1",
    "status": {
      "state": "TASK_STATE_COMPLETED",
      "message": {
        "role": "ROLE_AGENT",
        "parts": [
          { "text": "remote answer" }
        ]
      }
    }
  }
}
```

本地提取的是 `status.message.parts[*].text`。多个 text part 用换行拼接。提取后得到：

```text
remote answer
```

这个文本不会直接作为 parent task 的最终 answer。它会被当成本地 OpenJiuwen 的 tool result 回灌。

### 5.4 failed、timeout、无 terminal

| 场景 | 回灌给本地 OpenJiuwen 的 tool result |
|---|---|
| 远端 final state 不是 completed/input-required | `{"error":"<远端 final message 文本>"}` |
| 远端超时 | `{"error":"remote A2A stream timed out","code":"REMOTE_TIMEOUT"}` |
| 远端 stream 结束但没有 terminal result | `{"error":"REMOTE_TERMINAL_RESULT_MISSING"}` |

超时时，本地会保留已经投影给 parent task 的 progress，并 best-effort 调远端 `CancelTask`。

---

## 6. completed 后如何回灌本地 Agent

远端 completed 后，`A2aParentTaskProjector` 构造一个新的 `AgentExecutionContext`：

```json
{
  "inputType": "REMOTE_RESUME",
  "scope": {
    "tenant": "<沿用本地请求 tenant>",
    "user": "<沿用本地请求 userId>",
    "sessionId": "ctx-1",
    "taskId": "task-1",
    "agentId": "<本地 handler agentId>"
  },
  "messages": [],
  "variables": {
    "runtime.agentStateKey": "conversation-1",
    "runtime.remoteToolCallId": "tool-call-1",
    "runtime.remoteToolResult": "remote answer"
  },
  "agentStateKey": "conversation-1"
}
```

`OpenJiuwenMessageAdapter` 看到 `inputType=REMOTE_RESUME` 后，不再生成普通 query，而是生成 `InteractiveInput`：

```java
InteractiveInput interactiveInput = new InteractiveInput();
interactiveInput.update("tool-call-1", "remote answer");
```

传给 OpenJiuwen 的输入等价为：

```json
{
  "query": "InteractiveInput.update(tool-call-1, remote answer)",
  "conversation_id": "conversation-1"
}
```

OpenJiuwen rail 在 resume 分支会把这个 `InteractiveInput` 转成合成 tool result：

```json
{
  "_skip_tool": true,
  "toolResult": "remote answer",
  "toolMessage": {
    "toolCallId": "tool-call-1",
    "content": "remote answer"
  }
}
```

这一步的语义是：告诉本地 OpenJiuwen“刚才那个远端 tool call 已经有结果了，请从原来的 tool call 中断点继续推理”。

---

## 7. 本地 Agent 什么时候算结束

远端 completed 只代表远端 tool leg 结束，不代表 parent task 结束。parent task 的最终结束由本地 OpenJiuwen resume 后的结果决定。

`OpenJiuwenStreamAdapter` 对本地 OpenJiuwen 返回 map 的识别规则是：

| OpenJiuwen `result_type` | 本地 `AgentExecutionResult` | parent task 行为 |
|---|---|---|
| `answer` | `COMPLETED(output)` | `A2aResultRouter` 调 `emitter.complete(...)`，parent task 进入 `COMPLETED` |
| `interrupt` 且 context 是 `REMOTE_AGENT_INVOCATION` | `INTERRUPTED(RemoteInvocation)` | 发起远端 tool 调用 |
| `interrupt` 且不是远端 context | `INTERRUPTED(prompt)` | parent task 进入普通 `INPUT_REQUIRED` |
| 其他 | `FAILED(OPENJIUWEN_ERROR, output)` | parent task 进入 `FAILED` |

因此，真正的结束条件是本地 OpenJiuwen resume 后返回：

```json
{
  "result_type": "answer",
  "output": "最终回答"
}
```

此时 `A2aResultRouter` 发送 parent task terminal message：

```json
{
  "status": {
    "state": "TASK_STATE_COMPLETED",
    "message": {
      "role": "ROLE_AGENT",
      "parts": [
        { "text": "最终回答" }
      ]
    }
  }
}
```

如果本地 OpenJiuwen resume 后再次请求远端 tool，当前实现不支持嵌套远端调用，会让 parent task 失败：

```json
{
  "code": "NESTED_REMOTE_INVOCATION_UNSUPPORTED",
  "message": "remote A2A invocation after REMOTE_RESUME is not supported",
  "retryable": false
}
```

如果本地 handler 的结果流自然结束但没有 `COMPLETED/FAILED/INTERRUPTED` terminal，`A2aResultRouter.completeDrainedStream(...)` 会补一个空 completed，避免 parent task 一直停在 `WORKING`。

---

## 8. 端到端链路示例

### 8.1 远端 completed 场景

```text
1. 用户调用本地 /a2a message/stream
2. 本地 parent task: SUBMITTED -> WORKING
3. 本地 OpenJiuwen 返回 result_type=interrupt，context.runtime.remote.kind=REMOTE_AGENT_INVOCATION
4. 本地 runtime 发远端 sendMessageStreaming:
   message.parts[0].text = tool arguments.message
5. 远端返回 MESSAGE/ARTIFACT:
   本地投影成 parent artifact progress
6. 远端返回 TASK_STATE_COMPLETED:
   本地提取 status.message TextPart 文本
7. 本地 runtime 用 InteractiveInput.update(toolCallId, remoteText) 回灌 OpenJiuwen
8. 本地 OpenJiuwen 返回 result_type=answer:
   parent task COMPLETED，最终 message 是本地 OpenJiuwen output
```

### 8.2 远端 input-required 场景

```text
1. 远端返回 TASK_STATE_INPUT_REQUIRED + message("need more")
2. 本地 parent task 进入 INPUT_REQUIRED
3. parent metadata 保存 remoteAgentId / remoteTaskId / remoteContextId / toolCallId
4. 用户下一轮继续调用本地 /a2a，带同一个 parent task
5. 本地 runtime 识别 metadata.runtime.waitingTarget=REMOTE_AGENT
6. 本地 runtime 直接发远端 sendMessageStreaming:
   message.taskId = remoteTaskId
   message.contextId = remoteContextId
   message.parts[0].text = 用户补充输入
7. 远端 completed 后，再按 completed 场景回灌本地 OpenJiuwen
```

---

## 9. 失败、刷新与取消

| 场景 | 当前行为 |
|---|---|
| 远端 card 初次解析失败 | 该 URL 保持 pending，不注入 tool，不影响本地 runtime 启动 |
| 已 available 的远端后续刷新失败 | 保留上一次成功 card、endpoint、tool spec |
| 远端 endpoint 变化 | card cache 更新 endpoint；outbound adapter 下次调用重建 transport，并关闭旧 transport |
| 远端超时 | 保留已收到 progress，追加 `REMOTE_TIMEOUT` failed result，best-effort cancel 远端 task |
| parent task 取消 | 本地 handler cancel；如果 parent 正在等待远端 input 或远端 leg 运行中，则 best-effort cancel 远端 task |
| 远端 late event | terminal/timeout 后到达的 late event 会被丢弃，不再投影到 parent task |
