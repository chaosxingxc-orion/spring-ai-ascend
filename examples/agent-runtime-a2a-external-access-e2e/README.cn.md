# Agent Runtime A2A External Access E2E 示例

这个示例用于端到端验证 `agent-runtime` 对外暴露的 A2A JSON-RPC 协议能力。示例不依赖真实大模型，而是注册一个确定性的 `AgentRuntimeHandler`，用固定输入触发固定任务状态，方便稳定验证返回模式、任务查询、取消和 push notification。

## 已覆盖特性

自动化测试在 `A2aExternalAccessE2eTest` 中覆盖：

- Agent Card：`/.well-known/agent-card.json` 可发现，且 `capabilities.streaming=true`、`capabilities.pushNotifications=true`。
- `SendStreamingMessage`：通过 SSE 返回任务生命周期事件、artifact 增量和最终完成状态。
- `SendMessage` 当前行为：可收到完整 `Task` 快照；本示例覆盖 `COMPLETED`、`INPUT_REQUIRED`、`FAILED` 和 `returnImmediately=true` 的长任务快照。
- `GetTask`：按 task id 查询已创建任务状态。
- `ListTasks`：列出当前内存任务，确认包含前面创建的任务。
- `SubscribeToTask`：订阅已创建的长任务，确认能收到任务事件。
- `CancelTask`：取消长任务，确认任务进入 `TASK_STATE_CANCELED`。
- Push config 管理：`CreateTaskPushNotificationConfig`、`GetTaskPushNotificationConfig`、`ListTaskPushNotificationConfigs`。
- 当前任务 push：在 `SendStreamingMessage.params.configuration.taskPushNotificationConfig` 中携带 callback，确认 callback inbox 收到包含 `stream-part-1` 的 payload。

说明：`SendStreamingMessage` 是当前完整适配真实 agent 流式输出的主路径。`SendMessage` 在本示例中用于展示现有实现会返回什么，但不建议把它作为长耗时真实 agent 的完整对外调用方式；长任务应使用流式调用，或者 `returnImmediately=true` 后配合 `GetTask` / `CancelTask` / `SubscribeToTask`。

## 示例 agent 行为

示例 agent id：

```text
external-access-agent
```

输入文本包含不同关键字时，handler 返回不同结果：

| 输入关键字 | 行为 | 预期状态/内容 |
| --- | --- | --- |
| `sync` | 立即完成 | `TASK_STATE_COMPLETED`，文本包含 `sync-pong` |
| `stream` | 输出两段 artifact 后完成 | SSE 中包含 `stream-part-1`、`stream-part-2`、`stream-done` |
| `input` | 中断等待补充输入 | `TASK_STATE_INPUT_REQUIRED`，文本包含 `please provide more input` |
| `fail` | handler 抛异常 | `TASK_STATE_FAILED`，错误文本和结构化 `DataPart` 包含 `INVALID_INPUT` |
| `slow` | 持续输出，直到被取消 | `returnImmediately=true` 时先返回 `SUBMITTED` 或 `WORKING`，取消后为 `TASK_STATE_CANCELED` |

## 自动化测试方法

从仓库根目录执行：

```powershell
mvn -f examples/agent-runtime-a2a-external-access-e2e/pom.xml test
```

自动化测试会真实启动 Spring Boot runtime，但不是固定 `8080` 端口，而是 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 分配随机端口。测试随后用这个真实 HTTP 地址发起 A2A 请求：Agent Card 通过 SDK resolver 访问 `/.well-known/agent-card.json`，`SendMessage` / `SendStreamingMessage` / push config 通过 A2A SDK 访问 `/a2a`，`GetTask` / `ListTasks` / `CancelTask` 通过原始 HTTP JSON-RPC 报文访问 `/a2a`。

成功时应看到：

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 自动化覆盖与手工验证边界

自动化测试覆盖的是进程内可稳定断言的协议链路：

| 能力 | 自动化是否覆盖 | 自动化验证内容 |
| --- | --- | --- |
| Agent Card | 是 | 真实 HTTP 发现，断言 streaming 和 push notification capability |
| `SendStreamingMessage` | 是 | 真实 SSE 调用，断言 `SUBMITTED`、`WORKING`、artifact 增量和最终完成文本 |
| `SendMessage` completed | 是 | 真实 JSON-RPC 调用，断言 `TASK_STATE_COMPLETED` 和 `sync-pong` |
| `SendMessage` input required | 是 | 真实 JSON-RPC 调用，断言 `TASK_STATE_INPUT_REQUIRED` 和 prompt |
| `SendMessage` failed | 是 | 真实 JSON-RPC 调用，断言 `TASK_STATE_FAILED` 和结构化错误 |
| `SendMessage` `returnImmediately=true` | 是 | 原始 JSON-RPC 调用，断言返回可查询的长任务快照 |
| `GetTask` | 是 | 原始 JSON-RPC 调用，断言能查到前面创建的 task |
| `ListTasks` | 是 | 原始 JSON-RPC 调用，断言列表包含前面创建的 task |
| `SubscribeToTask` | 是 | SDK 订阅长任务，断言能收到至少一个任务事件 |
| `CancelTask` | 是 | 原始 JSON-RPC 调用，断言长任务进入 `TASK_STATE_CANCELED` |
| Push config 管理 | 是 | SDK 创建、查询、列出已存在 task 的 push config |
| 当前任务 push callback | 是 | `SendStreamingMessage` 携带 callback 到示例内置 inbox，断言 inbox 收到 payload |

仍建议手工验证的内容：

- 外部 callback listener：自动化使用同一 Spring Boot 进程内的 `/test/push-notifications` inbox，能证明 runtime 会发 callback，但不能证明跨进程、跨端口、被网络策略影响的真实外部 listener 可达。手工验证可启动 `scripts/push-listener.ps1`，把 callback URL 改为 `http://localhost:19090/test/push-notifications/`。
- 命令行客户端兼容性：自动化不会覆盖 PowerShell、`curl.exe -N`、请求体文件编码、shell 转义等人为执行细节。README 下面提供了可直接执行的 PowerShell 请求命令。
- 固定端口启动：自动化使用随机端口；如果要验证 `8080` 或自定义端口、端口占用、环境变量配置，需要按“手工启动”章节执行。
- 长时间运行和网络中断：自动化只覆盖可稳定完成的短链路，不模拟客户端断网、SSE 中途断连、外部 callback 服务超时或重试策略。
- 真实下游大模型/真实远端 agent：本示例使用确定性 handler，不覆盖真实 OpenJiuwen 或远端 agent 的业务输出质量。

## 手工启动

如果本地还没有安装当前仓库的 `agent-runtime` 依赖，先从仓库根目录执行：

```powershell
mvn -pl agent-runtime -am install "-DskipTests" "-Dmaven.test.skip=true"
```

说明：在 Windows PowerShell 中，`-D...` 这类 Maven system property 建议加引号，确保整个参数原样传给 Maven。否则 `-Dmaven.test.skip=true` 可能被拆坏，Maven 会把 `.test.skip=true` 当成 lifecycle phase 处理并报 `Unknown lifecycle phase`。

启动示例服务：

```powershell
mvn -f examples/agent-runtime-a2a-external-access-e2e/pom.xml spring-boot:run
```

服务默认地址：

```text
http://localhost:8080
```

需要改端口时：

```powershell
$env:SAA_EXTERNAL_ACCESS_PORT='18080'
mvn -f examples/agent-runtime-a2a-external-access-e2e/pom.xml spring-boot:run
```

## 请求体字段说明

所有 JSON-RPC 请求都发送到：

```text
POST http://localhost:8080/a2a
Content-Type: application/json
```

`SendMessage` 和 `SendStreamingMessage` 当前按现有解析逻辑使用下列字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `jsonrpc` | 是 | 固定填 `2.0` |
| `id` | 建议填 | JSON-RPC request id；响应会回显 |
| `method` | 是 | 例如 `SendMessage`、`SendStreamingMessage` |
| `params.message` | 是 | A2A message |
| `params.message.role` | 是 | 示例使用 `ROLE_USER` |
| `params.message.messageId` | 是 | A2A message id，调用方生成唯一值 |
| `params.message.parts` | 是 | 当前 runtime 只把 `TextPart.text` 映射给 handler |
| `params.message.parts[].text` | 是 | 示例 agent 根据这里的文本关键字决定返回 |
| `params.message.contextId` | 否 | 会作为会话上下文；不填时 SDK/runtime 可用 task id 兜底 |
| `params.metadata.userId` | 否 | 不填时 runtime 兜底为 `system` |
| `params.metadata.agentId` | 否 | 不填时 runtime 兜底为当前 handler 的 agent id |
| `params.configuration.returnImmediately` | 否 | `SendMessage` 可用；`true` 时尽快返回当前 task 快照 |
| `params.configuration.taskPushNotificationConfig` | 否 | 可在当前 `SendStreamingMessage` 中携带 callback |
| `params.configuration.taskPushNotificationConfig.id` | 建议填 | push config id |
| `params.configuration.taskPushNotificationConfig.url` | 是 | push callback URL |

`GetTask`、`CancelTask` 的 `params.id` 是必填 task id。`ListTasks` 当前示例使用空对象 `{}`。Push config 管理接口中，`CreateTaskPushNotificationConfig` 需要 `id`、`taskId`、`url`；`GetTaskPushNotificationConfig` 需要 `taskId`、`id`；`ListTaskPushNotificationConfigs` 需要 `taskId`。

## PowerShell 可直接发送命令

下面命令按 Windows PowerShell 格式编写，可以直接粘贴执行。先设置服务地址和临时请求体路径：

```powershell
$Base = 'http://localhost:8080'
$BodyFile = Join-Path $env:TEMP 'a2a-request.json'

function Write-A2aBody {
  param([Parameter(Mandatory = $true, ValueFromPipeline = $true)][string] $Json)
  process {
    [System.IO.File]::WriteAllText($BodyFile, $Json, [System.Text.UTF8Encoding]::new($false))
  }
}
```

说明：这里使用 PowerShell here-string 写 JSON，再通过 `Write-A2aBody` 写入无 BOM 的 UTF-8 临时文件，最后用 `curl.exe --data-binary "@$BodyFile"` 发送，避免命令行转义破坏 JSON。流式请求使用 `curl.exe -N`，普通 JSON-RPC 请求使用 `curl.exe -sS`。

### Agent Card

```powershell
curl.exe -sS "$Base/.well-known/agent-card.json"
```

预期：返回 agent card，`name` 为 `external-access-agent`，`url` 指向 `/a2a`，capabilities 中 streaming 和 push notifications 为 `true`。

### SendStreamingMessage：流式主路径

```powershell
@'
{
  "jsonrpc": "2.0",
  "id": "stream-1",
  "method": "SendStreamingMessage",
  "params": {
    "metadata": {
      "userId": "manual-user",
      "agentId": "external-access-agent"
    },
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-stream-1",
      "contextId": "ctx-stream-1",
      "parts": [
        { "text": "stream" }
      ]
    }
  }
}
'@ | Write-A2aBody

curl.exe -N "$Base/a2a" `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  --data-binary "@$BodyFile"
```

预期：SSE `data:` 帧中先后出现 `TASK_STATE_SUBMITTED`、`TASK_STATE_WORKING`，artifact 内容包含 `stream-part-1`、`stream-part-2`，最终状态为 `TASK_STATE_COMPLETED`，最终文本包含 `stream-done`。

### SendStreamingMessage：当前任务 push callback

```powershell
@'
{
  "jsonrpc": "2.0",
  "id": "push-stream-1",
  "method": "SendStreamingMessage",
  "params": {
    "metadata": {
      "userId": "manual-user",
      "agentId": "external-access-agent"
    },
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-push-stream-1",
      "contextId": "ctx-push-stream-1",
      "parts": [
        { "text": "stream" }
      ]
    },
    "configuration": {
      "taskPushNotificationConfig": {
        "id": "manual-push-1",
        "url": "http://localhost:8080/test/push-notifications"
      }
    }
  }
}
'@ | Write-A2aBody

curl.exe -N "$Base/a2a" `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  --data-binary "@$BodyFile"
```

预期：调用方收到与普通 `SendStreamingMessage` 相同的 SSE 内容；同时内置 inbox 收到 push payload，查询下面地址可以看到包含 `stream-part-1` 的回调内容：

```powershell
curl.exe -sS "$Base/test/push-notifications"
```

也可以单独启动外部 callback listener：

```powershell
powershell -ExecutionPolicy Bypass -File examples\agent-runtime-a2a-external-access-e2e\scripts\push-listener.ps1
```

然后把请求体中的 `url` 改为 `http://localhost:19090/test/push-notifications/`。

### SendMessage：已完成任务

```powershell
@'
{
  "jsonrpc": "2.0",
  "id": "sync-1",
  "method": "SendMessage",
  "params": {
    "metadata": {
      "userId": "manual-user",
      "agentId": "external-access-agent"
    },
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-sync-1",
      "contextId": "ctx-sync-1",
      "parts": [
        { "text": "sync" }
      ]
    }
  }
}
'@ | Write-A2aBody

curl.exe -sS "$Base/a2a" `
  -H "Content-Type: application/json" `
  --data-binary "@$BodyFile"
```

预期：普通 JSON-RPC 响应，`result.task.status.state` 为 `TASK_STATE_COMPLETED`，文本包含 `sync-pong`。

### SendMessage：input required

```powershell
@'
{
  "jsonrpc": "2.0",
  "id": "input-1",
  "method": "SendMessage",
  "params": {
    "metadata": {
      "userId": "manual-user",
      "agentId": "external-access-agent"
    },
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-input-1",
      "contextId": "ctx-input-1",
      "parts": [
        { "text": "input" }
      ]
    }
  }
}
'@ | Write-A2aBody

curl.exe -sS "$Base/a2a" `
  -H "Content-Type: application/json" `
  --data-binary "@$BodyFile"
```

预期：`result.task.status.state` 为 `TASK_STATE_INPUT_REQUIRED`，`status.message.parts[].text` 包含 `please provide more input`。

### SendMessage：失败任务

```powershell
@'
{
  "jsonrpc": "2.0",
  "id": "fail-1",
  "method": "SendMessage",
  "params": {
    "metadata": {
      "userId": "manual-user",
      "agentId": "external-access-agent"
    },
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-fail-1",
      "contextId": "ctx-fail-1",
      "parts": [
        { "text": "fail" }
      ]
    }
  }
}
'@ | Write-A2aBody

curl.exe -sS "$Base/a2a" `
  -H "Content-Type: application/json" `
  --data-binary "@$BodyFile"
```

预期：`result.task.status.state` 为 `TASK_STATE_FAILED`。返回不是 JSON-RPC `error`，而是一个失败态 `Task`；`status.message` 中既有文本错误，也有结构化 `DataPart`，内容包含 `INVALID_INPUT`、`retryable` 等字段。

### SendMessage：立即返回长任务快照

```powershell
@'
{
  "jsonrpc": "2.0",
  "id": "slow-1",
  "method": "SendMessage",
  "params": {
    "metadata": {
      "userId": "manual-user",
      "agentId": "external-access-agent"
    },
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-slow-1",
      "contextId": "ctx-slow-1",
      "parts": [
        { "text": "slow" }
      ]
    },
    "configuration": {
      "returnImmediately": true
    }
  }
}
'@ | Write-A2aBody

curl.exe -sS "$Base/a2a" `
  -H "Content-Type: application/json" `
  --data-binary "@$BodyFile"
```

预期：响应中有 `result.task.id`，状态通常为 `TASK_STATE_SUBMITTED` 或 `TASK_STATE_WORKING`。记录这个 task id，后续用于 `GetTask`、`SubscribeToTask` 和 `CancelTask`。

### GetTask

把前一步返回的 task id 设置到变量：

```powershell
$TaskId = '替换成上一步返回的taskId'

@"
{
  "jsonrpc": "2.0",
  "id": "get-1",
  "method": "GetTask",
  "params": {
    "id": "$TaskId"
  }
}
"@ | Write-A2aBody

curl.exe -sS "$Base/a2a" `
  -H "Content-Type: application/json" `
  --data-binary "@$BodyFile"
```

预期：返回当前 task 快照。对 `sync` 任务查询时，状态为 `TASK_STATE_COMPLETED`；对 `slow` 任务查询时，取消前通常为 `TASK_STATE_WORKING`，取消后为 `TASK_STATE_CANCELED`。

### ListTasks

```powershell
@'
{
  "jsonrpc": "2.0",
  "id": "list-1",
  "method": "ListTasks",
  "params": {}
}
'@ | Write-A2aBody

curl.exe -sS "$Base/a2a" `
  -H "Content-Type: application/json" `
  --data-binary "@$BodyFile"
```

预期：返回 `result.tasks`，包含当前进程内已经创建的 task。示例使用内存 task store，重启服务后列表会清空。

### SubscribeToTask

```powershell
$TaskId = '替换成slow任务的taskId'

@"
{
  "jsonrpc": "2.0",
  "id": "sub-1",
  "method": "SubscribeToTask",
  "params": {
    "id": "$TaskId"
  }
}
"@ | Write-A2aBody

curl.exe -N "$Base/a2a" `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  --data-binary "@$BodyFile"
```

预期：SSE 返回该 task 的后续事件。对 `slow` 任务订阅时会持续收到事件，直到任务被取消或连接关闭。

### CancelTask

```powershell
$TaskId = '替换成slow任务的taskId'

@"
{
  "jsonrpc": "2.0",
  "id": "cancel-1",
  "method": "CancelTask",
  "params": {
    "id": "$TaskId"
  }
}
"@ | Write-A2aBody

curl.exe -sS "$Base/a2a" `
  -H "Content-Type: application/json" `
  --data-binary "@$BodyFile"
```

预期：返回被取消后的 task 快照，`result.task.status.state` 为 `TASK_STATE_CANCELED`。随后再 `GetTask` 查询同一个 id，也应看到 `TASK_STATE_CANCELED`。

### Push config 管理

先用 `SendMessage sync` 创建一个已存在 task，再把返回的 task id 填入下面请求。

```powershell
$TaskId = '替换成已存在任务的taskId'

@"
{
  "jsonrpc": "2.0",
  "id": "push-create-1",
  "method": "CreateTaskPushNotificationConfig",
  "params": {
    "id": "manual-existing-task-push-1",
    "taskId": "$TaskId",
    "url": "http://localhost:8080/test/push-notifications"
  }
}
"@ | Write-A2aBody

curl.exe -sS "$Base/a2a" `
  -H "Content-Type: application/json" `
  --data-binary "@$BodyFile"
```

预期：返回 push config，`result.taskPushNotificationConfig.taskId` 等于请求中的 task id，`url` 等于请求中的 callback URL。

```powershell
@"
{
  "jsonrpc": "2.0",
  "id": "push-get-1",
  "method": "GetTaskPushNotificationConfig",
  "params": {
    "taskId": "$TaskId",
    "id": "manual-existing-task-push-1"
  }
}
"@ | Write-A2aBody

curl.exe -sS "$Base/a2a" `
  -H "Content-Type: application/json" `
  --data-binary "@$BodyFile"
```

预期：返回同一个 push config。

```powershell
@"
{
  "jsonrpc": "2.0",
  "id": "push-list-1",
  "method": "ListTaskPushNotificationConfigs",
  "params": {
    "taskId": "$TaskId"
  }
}
"@ | Write-A2aBody

curl.exe -sS "$Base/a2a" `
  -H "Content-Type: application/json" `
  --data-binary "@$BodyFile"
```

预期：返回 `result.configs`，其中包含 `manual-existing-task-push-1`。

## 手工发送方法

如果只想临时快速发一个短请求，也可以直接在 PowerShell 中写入临时文件再发送。普通 JSON-RPC 示例：

```powershell
'{"jsonrpc":"2.0","id":"list-1","method":"ListTasks","params":{}}' | Write-A2aBody

curl.exe -sS "$Base/a2a" -H "Content-Type: application/json" --data-binary "@$BodyFile"
```

SSE 请求示例：

```powershell
'{"jsonrpc":"2.0","id":"stream-1","method":"SendStreamingMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-stream-1","parts":[{"text":"stream"}]}}}' | Write-A2aBody

curl.exe -N "$Base/a2a" -H "Content-Type: application/json" -H "Accept: text/event-stream" --data-binary "@$BodyFile"
```

## SendMessage 备注

`SendMessage` 当前会被解析并分发，但对于真实下游 agent 大多走流式输出的场景，它不是完整推荐的外部调用主路径。本示例保留它是为了说明现有实现的实际返回：

- 如果任务在阻塞等待窗口内完成，返回 `Task`，状态为 `TASK_STATE_COMPLETED`。
- 如果任务在阻塞等待窗口内进入 input-required，返回 `Task`，状态为 `TASK_STATE_INPUT_REQUIRED`。
- 如果 handler 抛异常，返回 `Task`，状态为 `TASK_STATE_FAILED`，错误信息在 `status.message` 中，而不是 JSON-RPC `error`。
- 如果设置 `configuration.returnImmediately=true`，通常立即返回当前 `Task` 快照，状态可能是 `TASK_STATE_SUBMITTED` 或 `TASK_STATE_WORKING`。
- 如果未设置 `returnImmediately` 且任务长时间没有终态，SDK `DefaultRequestHandler` 会按其阻塞等待配置返回当前快照或超时错误；本示例不把这种长时间阻塞模式作为推荐用法。

真实长输出或需要逐步观察的调用，优先使用 `SendStreamingMessage`。需要后台任务语义时，使用 `returnImmediately=true` 获取 task id，再通过 `GetTask`、`SubscribeToTask` 和 `CancelTask` 管理。
