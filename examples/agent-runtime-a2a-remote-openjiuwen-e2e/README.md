# agent-runtime remote A2A OpenJiuwen E2E example

这个 example 启动两个 `agent-runtime` 实例：

- AgentB：远端 runtime。它先流式返回两条消息，再返回 `INPUT_REQUIRED`；下一轮用户输入后再流式返回两条消息并 `COMPLETED`。
- AgentA：本地 OpenJiuwen agent。runtime 会发现 AgentB 的 A2A card，并把 AgentB 注入为一个 OpenJiuwen tool。

AgentA 有两个模式：

- `deterministic`：默认模式。AgentA handler 直接产生与 remote rail 等价的 interrupt marker。它不依赖真实大模型，适合稳定验证两个 runtime 之间的 A2A 出站、远端 `INPUT_REQUIRED` 续写、远端完成后 resume 本地 OpenJiuwen。
- `llm`：手动验证模式。AgentA 使用真实 OpenJiuwen `ReActAgent`，让大模型选择 runtime 注入的远端 tool `a2a_remote_remote_b`。这个模式用于验证 `LLM tool call -> OpenJiuwenRemoteAgentInterruptRail -> ToolInterruptException/OpenJiuwen interrupt state -> OpenJiuwenStreamAdapter -> REMOTE_INVOCATION` 链路。

example 本身不进 CI；真实 LLM 路径依赖外部模型服务，建议只作为手动验收。

## 覆盖范围

默认 deterministic 路径覆盖：

- AgentA 启动后按 `agent-runtime.remote-agents[0].url` 读取 AgentB 的 A2A card。
- Runtime 根据 AgentB card 生成远端 tool spec，并在 AgentA 的 OpenJiuwen handler 生命周期里安装 runtime tool。
- AgentA 产生远端 tool 调用意图后，runtime 通过 A2A client 调用 AgentB。
- AgentB 通过 streaming A2A 返回 progress，然后进入 `TASK_STATE_INPUT_REQUIRED`。
- AgentA 的 parent task 写入远端路由 metadata，并把 AgentB 的追问返回给用户。
- 第二轮用户请求携带同一个 parent `taskId` / `contextId` 后，runtime 续写同一个远端 AgentB task。
- AgentB 完成后，runtime 把远端结果作为 OpenJiuwen `InteractiveInput` resume 给 AgentA，AgentA 最终 `COMPLETED`。

LLM 路径额外覆盖：

- 远端 tool 不是手写 marker 触发，而是由 OpenJiuwen ReActAgent 的模型调用选择。
- 被选择的 tool 由 `OpenJiuwenRemoteAgentInterruptRail` 中断，本地 placeholder tool 不应真正执行。
- `OpenJiuwenStreamAdapter` 应把 remote interrupt state/context 映射成 `REMOTE_INVOCATION`。

## 构建

```powershell
cd D:\Code\spring-ai-ascend
mvn -f examples\agent-runtime-a2a-remote-openjiuwen-e2e\pom.xml package -DskipTests
```

## 启动 AgentB

在第一个 PowerShell 窗口启动远端 runtime：

```powershell
cd D:\Code\spring-ai-ascend
java -jar examples\agent-runtime-a2a-remote-openjiuwen-e2e\target\agent-runtime-a2a-remote-openjiuwen-e2e-example-0.1.0-SNAPSHOT.jar `
  --server.port=18082 `
  --sample.remote-openjiuwen.role=b
```

## 启动 AgentA 默认确定性路径

在第二个 PowerShell 窗口启动本地 runtime：

```powershell
cd D:\Code\spring-ai-ascend
java -jar examples\agent-runtime-a2a-remote-openjiuwen-e2e\target\agent-runtime-a2a-remote-openjiuwen-e2e-example-0.1.0-SNAPSHOT.jar `
  --server.port=18081 `
  --sample.remote-openjiuwen.role=a `
  --agent-runtime.remote-agents[0].url=http://localhost:18082
```

启动后先确认两个 card 可访问：

```powershell
curl.exe http://localhost:18082/.well-known/agent-card.json
curl.exe http://localhost:18081/.well-known/agent-card.json
```

AgentA 对远端 card 是后台重试发现。启动 AgentA 后等几秒，直到 AgentA 日志出现类似 `installed 1 remote A2A tool(s)`，再发送请求。

## 启动 AgentA 真实 LLM tool-choice 路径

不要把模型 key 写进代码或 README。AgentA LLM 模式只从环境变量或显式 Spring property 读取模型配置。

PowerShell 环境变量示例：

```powershell
$env:SAA_REMOTE_OPENJIUWEN_LLM_PROVIDER = "openai"
$env:SAA_REMOTE_OPENJIUWEN_LLM_API_BASE = "https://api.deepseek.com"
$env:SAA_REMOTE_OPENJIUWEN_LLM_MODEL = "deepseek-chat"
$env:SAA_REMOTE_OPENJIUWEN_LLM_API_KEY = "<your-deepseek-api-key>"
```

推荐先用 JUnit 手动验收真实 LLM tool-choice 链路。这个测试默认跳过，只有设置
`SAA_REMOTE_OPENJIUWEN_RUN_LLM_E2E=true` 时才会真实请求外部模型服务；测试会在同一 JVM
里启动 AgentA 和 AgentB，适合避免手工双进程启动时的 jar 锁定或后台进程清理问题。

```powershell
cd D:\Code\spring-ai-ascend
$env:SAA_REMOTE_OPENJIUWEN_RUN_LLM_E2E = "true"
$env:SAA_REMOTE_OPENJIUWEN_LLM_PROVIDER = "openai"
$env:SAA_REMOTE_OPENJIUWEN_LLM_API_BASE = "https://api.deepseek.com"
$env:SAA_REMOTE_OPENJIUWEN_LLM_MODEL = "deepseek-chat"
$env:SAA_REMOTE_OPENJIUWEN_LLM_API_KEY = "<your-deepseek-api-key>"
mvn -f examples\agent-runtime-a2a-remote-openjiuwen-e2e\pom.xml `
  "-Dtest=RemoteOpenJiuwenA2aE2eTest#manualLlmAgentInvokesRemoteAgentWithInputRequiredAndResume" test
```

如果要用双进程方式手工观察 SSE，先按上一节启动 AgentB，再启动 LLM 模式 AgentA：

```powershell
cd D:\Code\spring-ai-ascend
java -jar examples\agent-runtime-a2a-remote-openjiuwen-e2e\target\agent-runtime-a2a-remote-openjiuwen-e2e-example-0.1.0-SNAPSHOT.jar `
  --server.port=18081 `
  --sample.remote-openjiuwen.role=a `
  --sample.remote-openjiuwen.agent-a.mode=llm `
  --agent-runtime.remote-agents[0].url=http://localhost:18082
```

LLM 模式启动后同样等待 `installed 1 remote A2A tool(s)` 日志。这个日志出现后，模型才能在当前 AgentA 执行中看到 `a2a_remote_remote_b`。

## 第一次请求：触发 AgentA 调用 AgentB

```powershell
$body = @{
  jsonrpc = '2.0'
  id = 'remote-openjiuwen-1'
  method = 'SendStreamingMessage'
  params = @{
    message = @{
      role = 'ROLE_USER'
      messageId = 'msg-a-1'
      contextId = 'ctx-a-1'
      metadata = @{
        userId = 'manual-user'
        agentId = 'local-a'
      }
      parts = @(@{ text = '请调用远端 AgentB 做 streaming input-required 演示' })
    }
  }
} | ConvertTo-Json -Depth 12

$first = curl.exe http://localhost:18081/a2a `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  -d $body

$first
```

成功现象：

- deterministic 模式：AgentA 直接产生 remote interrupt marker。
- llm 模式：AgentA 日志里应先出现 OpenJiuwen 模型调用/工具选择相关日志，然后才出现 remote interrupt / `REMOTE_INVOCATION` 链路。
- SSE 中能看到 AgentB 的两条远端流式消息。
- 最后 parent task 状态为 `TASK_STATE_INPUT_REQUIRED`。
- `status.message.parts[0].text` 包含 AgentB 的追问。
- 记下返回里的 parent `taskId` 和 `contextId`。

从 SSE 输出提取 parent taskId：

```powershell
$events = $first |
  Where-Object { $_ -like 'data:*' } |
  ForEach-Object { ($_ -replace '^data:', '') | ConvertFrom-Json }

$taskId = $events |
  ForEach-Object {
    if ($_.result.statusUpdate.status.state -eq 'TASK_STATE_INPUT_REQUIRED') {
      $_.result.statusUpdate.taskId
    } elseif ($_.result.task.status.state -eq 'TASK_STATE_INPUT_REQUIRED') {
      $_.result.task.id
    }
  } |
  Where-Object { $_ } |
  Select-Object -First 1

$taskId
```

也可以查询 parent task，确认它停在 `INPUT_REQUIRED`，并检查 route metadata：

```powershell
$getTaskBody = @{
  jsonrpc = '2.0'
  id = 'get-parent-1'
  method = 'GetTask'
  params = @{ id = $taskId }
} | ConvertTo-Json -Depth 8

Invoke-RestMethod http://localhost:18081/a2a `
  -Method Post `
  -ContentType 'application/json' `
  -Body $getTaskBody |
  ConvertTo-Json -Depth 20
```

重点看这些 metadata 字段：

- `runtime.waitingTarget = REMOTE_AGENT`
- `runtime.remoteAgentId = remote-b`
- `runtime.remoteTaskId`
- `runtime.remoteContextId`
- `runtime.toolCallId`
- `runtime.localConversationId`

## 第二次请求：续写同一个远端 AgentB task

```powershell
# $taskId = '<第一次返回的 parent taskId>'

$body = @{
  jsonrpc = '2.0'
  id = 'remote-openjiuwen-2'
  method = 'SendStreamingMessage'
  params = @{
    message = @{
      role = 'ROLE_USER'
      messageId = 'msg-a-2'
      taskId = $taskId
      contextId = 'ctx-a-1'
      metadata = @{
        userId = 'manual-user'
        agentId = 'local-a'
      }
      parts = @(@{ text = 'follow up from user' })
    }
  }
} | ConvertTo-Json -Depth 12

$second = curl.exe http://localhost:18081/a2a `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  -d $body

$second
```

成功现象：

- 第二次请求不会重新进入 AgentA 本地推理，而是根据 parent task metadata 续写同一个远端 AgentB task。
- SSE 中能看到 AgentB 第二轮的两条流式消息。
- AgentB 完成后，runtime 用远端结果 resume AgentA。
- parent task 最终进入 `TASK_STATE_COMPLETED`。
- deterministic 模式最终文本包含 `AgentA resumed from remote tool result`。
- llm 模式最终文本应包含模型基于 AgentB tool result 生成的摘要；具体措辞由模型决定。

最终查询 parent task：

```powershell
$getTaskBody = @{
  jsonrpc = '2.0'
  id = 'get-parent-2'
  method = 'GetTask'
  params = @{ id = $taskId }
} | ConvertTo-Json -Depth 8

Invoke-RestMethod http://localhost:18081/a2a `
  -Method Post `
  -ContentType 'application/json' `
  -Body $getTaskBody |
  ConvertTo-Json -Depth 20
```
