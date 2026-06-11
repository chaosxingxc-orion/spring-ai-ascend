# Agent Runtime A2A 三种返回方式验证示例

## 目的

本示例验证 `agent-runtime` 当前 A2A 入口的三种返回方式：

1. 同步返回：A2A SDK JSON-RPC `SendMessage`，HTTP JSON-RPC 普通响应。
2. 流式返回：A2A SDK JSON-RPC `SendStreamingMessage`，HTTP SSE 返回多个 A2A streaming event。
3. Push Notification：当前任务通过 `configuration.taskPushNotificationConfig` 携带 callback，runtime 在任务事件发生时回调本地 callback URL。

示例不依赖真实大模型。它使用一个确定性 `AgentRuntimeHandler`，方便稳定验证协议链路。

## 目录

```text
examples/agent-runtime-a2a-return-modes-e2e
  pom.xml
  README.cn.md
  src/main/java/com/huawei/ascend/examples/a2a/returnmodes
    ReturnModesA2aRuntimeApplication.java
    ReturnModesAgentConfiguration.java
    ReturnModesA2aClient.java
    PushNotificationInbox.java
    PushNotificationInboxController.java
  scripts
    push-listener.ps1
  src/test/java/com/huawei/ascend/examples/a2a/returnmodes
    ReturnModesA2aE2eTest.java
```

## 服务行为

示例 agent id 是：

```text
return-modes-agent
```

输入包含不同关键字时，agent 返回不同结果：

| 输入 | 行为 |
| --- | --- |
| `sync` | 返回 `sync-pong` 并完成任务。 |
| `stream` | 先输出 `stream-part-1 `、`stream-part-2 `，最后返回 `stream-done` 并完成任务。 |
| `input` | 返回 input-required prompt：`please provide more input`。 |

## 自动化测试

从仓库根目录执行：

```powershell
$env:JAVA_HOME='D:\Software\Java\jdk-21.0.11'
$env:Path='D:\Software\Java\jdk-21.0.11\bin;D:\Software\apache-maven-3.9.16\bin;' + $env:Path
mvn -f examples/agent-runtime-a2a-return-modes-e2e/pom.xml test
```

成功标准：

- 测试能启动随机端口的 Spring Boot runtime。
- AgentCard 中 `capabilities.streaming=true`。
- AgentCard 中 `capabilities.pushNotifications=true`。
- 同步 `SendMessage` 返回 A2A `Task`，task 状态为 `TASK_STATE_COMPLETED`，文本包含 `sync-pong`。
- 流式 `SendStreamingMessage` 返回 SSE event，能看到 `stream-part-1`、`stream-part-2` 和 `stream-done`。
- 已存在 task 的 push notification config 能 `CreateTaskPushNotificationConfig` / `GetTaskPushNotificationConfig` / `ListTaskPushNotificationConfigs`。
- 当前任务在 `SendStreamingMessage` 的 `configuration.taskPushNotificationConfig` 里携带 callback 后，`/test/push-notifications` 能收到回调 payload，payload 中包含 `stream-part-1`。

## 手工启动

先确保 `agent-runtime` 可被本示例依赖解析。如果没有安装过：

```powershell
mvn -pl agent-runtime -am install -DskipTests -Dmaven.test.skip=true
```

启动示例服务：

```powershell
mvn -f examples/agent-runtime-a2a-return-modes-e2e/pom.xml spring-boot:run
```

服务默认监听：

```text
http://localhost:8080
```

可通过环境变量改端口：

```powershell
$env:SAA_RETURN_MODES_PORT='18080'
mvn -f examples/agent-runtime-a2a-return-modes-e2e/pom.xml spring-boot:run
```

## 手工验证同步返回

```powershell
$body = @{
  jsonrpc = '2.0'
  id = 'sync-1'
  method = 'SendMessage'
  params = @{
    message = @{
      role = 'ROLE_USER'
      messageId = 'msg-sync-1'
      contextId = 'ctx-sync-1'
      metadata = @{
        userId = 'manual-user'
        agentId = 'return-modes-agent'
      }
      parts = @(@{ text = 'sync' })
    }
  }
} | ConvertTo-Json -Depth 10

$response = Invoke-RestMethod http://localhost:8080/a2a -Method Post -ContentType 'application/json' -Body $body

$response | ConvertTo-Json -Depth 20
$response.result.task.status.state
$response.result.task.status.message.parts[0].text
```

说明：PowerShell 直接打印 `Invoke-RestMethod` 的返回值时，嵌套对象可能显示成 `@{task=}`。这只是 PowerShell 的表格折叠展示，不代表 `task` 为空。用 `ConvertTo-Json -Depth 20` 展开后才能看到完整 JSON-RPC result。

成功标准：完整 JSON 中返回 `result.task`；`$response.result.task.status.state` 输出 `TASK_STATE_COMPLETED`；`$response.result.task.status.message.parts[0].text` 输出 `sync-pong`。

## 手工验证流式返回

```powershell
$body = @{
  jsonrpc = '2.0'
  id = 'stream-1'
  method = 'SendStreamingMessage'
  params = @{
    message = @{
      role = 'ROLE_USER'
      messageId = 'msg-stream-1'
      contextId = 'ctx-stream-1'
      metadata = @{
        userId = 'manual-user'
        agentId = 'return-modes-agent'
      }
      parts = @(@{ text = 'stream' })
    }
  }
} | ConvertTo-Json -Depth 10

$tmpBody = Join-Path $env:TEMP 'a2a-stream-body.json'
$body | Set-Content -Path $tmpBody -Encoding UTF8

curl.exe -N http://localhost:8080/a2a `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  --data-binary "@$tmpBody"
```

成功标准：输出 `data:` 开头的 SSE 事件，事件内容中能看到两段 `artifactUpdate`，分别包含 `stream-part-1` 和 `stream-part-2`，最后还能看到状态更新里的 `stream-done`。

## 手工验证 Push Notification

手工验证 push notification 有两种方式。

方式一：使用示例服务内置 inbox。它适合快速验证，但回调和 runtime 在同一个进程里：

```text
POST /test/push-notifications
GET  /test/push-notifications
```

`POST` 是 runtime 的 push callback 地址，`GET` 用于查看已经收到的 callback payload。

方式二：单独起一个 PowerShell callback listener。它更接近真实 push notification 场景，也能在监听窗口直接看到远端回调。推荐手工验证用这个方式。

### 窗口 1：启动 callback listener

```powershell
cd D:\Code\spring-ai-ascend
powershell -ExecutionPolicy Bypass -File examples\agent-runtime-a2a-return-modes-e2e\scripts\push-listener.ps1
```

成功启动后，窗口会显示：

```text
Listening for A2A push notifications at http://localhost:19090/test/push-notifications/
```

### 窗口 2：启动 runtime 服务

```powershell
cd D:\Code\spring-ai-ascend
$env:JAVA_HOME='D:\Software\Java\jdk-21.0.11'
$env:Path='D:\Software\Java\jdk-21.0.11\bin;D:\Software\apache-maven-3.9.16\bin;' + $env:Path
mvn -f examples/agent-runtime-a2a-return-modes-e2e/pom.xml spring-boot:run
```

### 窗口 3：发送带 push callback 的 A2A 请求

如果 runtime 使用默认端口 `8080`，执行：

```powershell
$callbackUrl = 'http://localhost:19090/test/push-notifications/'

$body = @{
  jsonrpc = '2.0'
  id = 'push-stream-1'
  method = 'SendStreamingMessage'
  params = @{
    message = @{
      role = 'ROLE_USER'
      messageId = 'msg-push-stream-1'
      contextId = 'ctx-push-stream-1'
      metadata = @{
        userId = 'manual-user'
        agentId = 'return-modes-agent'
      }
      parts = @(@{ text = 'stream' })
    }
    configuration = @{
      taskPushNotificationConfig = @{
        id = 'manual-push-1'
        url = $callbackUrl
      }
    }
  }
} | ConvertTo-Json -Depth 12

$response = Invoke-WebRequest `
  -UseBasicParsing `
  -Uri http://localhost:8080/a2a `
  -Method Post `
  -ContentType 'application/json' `
  -Headers @{ Accept = 'text/event-stream' } `
  -Body $body

$response.StatusCode
$response.Content
```

成功标准：窗口 3 会看到 SSE 返回，窗口 1 会直接打印 push callback，例如：

```text
===== A2A Push Notification 2026-06-10 10:30:00 =====
POST /test/push-notifications/
...stream-part-1...
```

只要窗口 1 至少打印出一条 callback payload，并且 payload 中能看到 `stream-part-1`，就说明 push notification 链路成功。

如果一定要用 `curl.exe`，不要直接 `-d $body`。Windows PowerShell 下多行 JSON 变量可能被 curl 参数解析截断，runtime 日志会出现 `MalformedJsonException: Unterminated object ... taskPushNotificationConfig.url`。可以先把请求体写入临时文件，再用 `--data-binary '@文件名'`：

```powershell
$tmpBody = Join-Path $env:TEMP 'a2a-push-body.json'
$body | Set-Content -Path $tmpBody -Encoding UTF8

curl.exe -N http://localhost:8080/a2a `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  --data-binary "@$tmpBody"
```

如果不想另起 listener，也可以把 `$callbackUrl` 改成内置 inbox：

```powershell
$callbackUrl = 'http://localhost:8080/test/push-notifications'
```

然后用下面命令查看 runtime 进程内收到的 payload：

```powershell
Invoke-RestMethod http://localhost:8080/test/push-notifications -Method Get
```

说明：`CreateTaskPushNotificationConfig` / `GetTaskPushNotificationConfig` / `ListTaskPushNotificationConfigs` 是“已存在 task 的配置管理 API”。如果要在创建当前任务时立刻收到 push，推荐像上面的例子一样把 callback 放进本次 `SendStreamingMessage` 的 `configuration.taskPushNotificationConfig`，此时 `taskId` 可以不填，runtime 会绑定到本次新建的真实 task。

## 说明

本示例验证的是 A2A 返回方式，不验证真实 LLM。真实 OpenJiuwen 调用请看：

```text
examples/agent-runtime-a2a-openjiuwen-e2e
```
