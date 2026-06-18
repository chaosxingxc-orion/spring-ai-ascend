# Agent Runtime A2A OpenJiuwen DeepAgent E2E 示例

## 目的

本示例用于验证 `agent-runtime` 可以托管 openJiuwen `DeepAgent`，并且调用方只通过 A2A JSON-RPC streaming endpoint 访问它。

示例位于 `examples/agent-runtime-a2a-openjiuwen-deepagent-e2e`，包含：

- Spring Boot 服务端：`com.huawei.ascend.examples.a2a.OpenJiuwenA2aAgentRuntimeApplication`
- openJiuwen DeepAgent 配置：`com.huawei.ascend.examples.a2a.OpenJiuwenDeepAgentConfiguration`
- A2A SDK 客户端测试辅助：`com.huawei.ascend.examples.a2a.SampleA2aClient`
- 交互式控制台客户端：`com.huawei.ascend.examples.a2a.A2aConsoleClientApplication`
- 端到端测试：`OpenJiuwenDeepAgentA2aE2eTest`

## 验证内容

自动化测试覆盖以下路径：

1. 启动一个嵌入式 Spring Boot runtime。
2. 注册一个 openJiuwen DeepAgent，agent id 为 `openjiuwen-deep-agent`。
3. 通过 `/.well-known/agent-card.json` 发现 AgentCard。
4. 确认 AgentCard 支持 JSON-RPC streaming。
5. 如果 `SAA_SAMPLE_LLM_API_KEY` 非空，则通过 A2A SDK streaming 发送 `ping`。
6. 客户端消费 SSE stream，直到任务进入终态。
7. 最终用户可见文本应为 `pong`。

如果没有配置真实模型 key，测试仍会启动服务并验证 AgentCard；真实 LLM 分支会被 JUnit `assumeTrue()` 跳过。

## 环境变量

本示例不提供脚本包装，也不提供环境变量模板文件。请在当前终端中直接设置环境变量，然后运行 Maven 命令。

### Bash

```bash
export SAA_SAMPLE_LLM_API_KEY="sk-local-placeholder"
export SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER="openai"
export SAA_SAMPLE_OPENJIUWEN_API_BASE="http://localhost:4000/v1"
export SAA_SAMPLE_LLM_MODEL="gpt-5.4-mini"
export SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY="false"
export SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER="in-memory"
export SAA_SAMPLE_OPENJIUWEN_WORKSPACE_PATH="./target/deepagent-workspace"
```

### PowerShell

```powershell
$env:SAA_SAMPLE_LLM_API_KEY = "sk-local-placeholder"
$env:SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER = "openai"
$env:SAA_SAMPLE_OPENJIUWEN_API_BASE = "http://localhost:4000/v1"
$env:SAA_SAMPLE_LLM_MODEL = "gpt-5.4-mini"
$env:SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY = "false"
$env:SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER = "in-memory"
$env:SAA_SAMPLE_OPENJIUWEN_WORKSPACE_PATH = "./target/deepagent-workspace"
```

变量说明：

- `SAA_SAMPLE_LLM_API_KEY`：模型服务 key。Ollama 或本地 OpenAI-compatible gateway 可使用任意非空占位值；为空时自动化测试会跳过真实 LLM 调用。
- `SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER`：openJiuwen model provider，本示例使用 `openai` 表示 OpenAI-compatible `/v1`。
- `SAA_SAMPLE_OPENJIUWEN_API_BASE`：模型服务 base URL，例如 `http://localhost:4000/v1`、`http://localhost:11434/v1` 或 `https://api.openai.com/v1`。
- `SAA_SAMPLE_LLM_MODEL`：模型名。
- `SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY`：是否校验证书；本地 HTTP 通常为 `false`，云端 HTTPS 通常为 `true`。
- `SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER`：`in-memory` 或 `redis`。
- `SAA_SAMPLE_OPENJIUWEN_REDIS_URL`：只在 checkpointer 设置为 `redis` 时使用，例如 `redis://localhost:6379`。
- `SAA_SAMPLE_OPENJIUWEN_WORKSPACE_PATH`：DeepAgent workspace 路径。

runtime 默认绑定配置在 `src/main/resources/application.yaml`：

```yaml
agent-runtime:
  access:
    a2a:
      default-tenant-id: sample-tenant
      default-agent-id: openjiuwen-deep-agent
```

## 执行自动化测试

以下命令均从仓库根目录执行。

先安装当前工作区的 `agent-runtime` 到本地 Maven 仓库，确保示例使用本地最新代码：

### Bash

```bash
./mvnw -pl agent-runtime install -DskipTests -Dmaven.test.skip=true
```

### PowerShell

```powershell
.\mvnw.cmd -pl agent-runtime install -DskipTests "-Dmaven.test.skip=true"
```

然后运行 DeepAgent 示例测试：

### Bash

```bash
./mvnw -f examples/agent-runtime-a2a-openjiuwen-deepagent-e2e/pom.xml test
```

### PowerShell

```powershell
.\mvnw.cmd -f examples\agent-runtime-a2a-openjiuwen-deepagent-e2e\pom.xml test
```

## 手工验证

### 1. 确认模型 gateway 可访问

### Bash

```bash
curl http://localhost:4000/v1/models \
  -H "Authorization: Bearer ${SAA_SAMPLE_LLM_API_KEY}"
```

### PowerShell

```powershell
curl.exe http://localhost:4000/v1/models `
  -H "Authorization: Bearer $env:SAA_SAMPLE_LLM_API_KEY"
```

如果你的 `SAA_SAMPLE_OPENJIUWEN_API_BASE` 不是 `http://localhost:4000/v1`，请把上面的 URL 换成对应 gateway 的 `/models` 地址。

### 2. 启动服务端

### Bash

```bash
./mvnw -f examples/agent-runtime-a2a-openjiuwen-deepagent-e2e/pom.xml spring-boot:run
```

### PowerShell

```powershell
.\mvnw.cmd -f examples\agent-runtime-a2a-openjiuwen-deepagent-e2e\pom.xml spring-boot:run
```

服务默认监听 `http://localhost:8080`。

### 3. 使用交互式客户端验证 ping/pong

另开一个终端，并保持服务端终端继续运行。

### Bash

```bash
./mvnw -f examples/agent-runtime-a2a-openjiuwen-deepagent-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication
```

### PowerShell

```powershell
.\mvnw.cmd -f examples\agent-runtime-a2a-openjiuwen-deepagent-e2e\pom.xml `
  exec:java `
  "-Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication"
```

客户端启动后会打印类似：

```text
Connected to openjiuwen-deep-agent at http://localhost:8080
Type a message and press Enter. Type exit to quit.
>
```

输入：

```text
ping
```

期望输出：

```text
pong
```

退出客户端：

```text
exit
```

如果服务端不在默认地址，可直接传 Maven 参数：

### Bash

```bash
./mvnw -f examples/agent-runtime-a2a-openjiuwen-deepagent-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication \
  -Dexec.args="http://localhost:18080 openjiuwen-deep-agent manual-user"
```

### PowerShell

```powershell
.\mvnw.cmd -f examples\agent-runtime-a2a-openjiuwen-deepagent-e2e\pom.xml `
  exec:java `
  "-Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication" `
  "-Dexec.args=http://localhost:18080 openjiuwen-deep-agent manual-user"
```

也可以通过环境变量覆盖：

### Bash

```bash
export SAA_SAMPLE_A2A_BASE_URL="http://localhost:18080"
export SAA_SAMPLE_AGENT_ID="openjiuwen-deep-agent"
export SAA_SAMPLE_USER_ID="manual-user"
./mvnw -f examples/agent-runtime-a2a-openjiuwen-deepagent-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication
```

### PowerShell

```powershell
$env:SAA_SAMPLE_A2A_BASE_URL = "http://localhost:18080"
$env:SAA_SAMPLE_AGENT_ID = "openjiuwen-deep-agent"
$env:SAA_SAMPLE_USER_ID = "manual-user"
.\mvnw.cmd -f examples\agent-runtime-a2a-openjiuwen-deepagent-e2e\pom.xml `
  exec:java `
  "-Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication"
```

### 4. 使用 HTTP 命令验证 A2A surface

检查 AgentCard：

### Bash

```bash
curl http://localhost:8080/.well-known/agent-card.json
```

### PowerShell

```powershell
curl.exe http://localhost:8080/.well-known/agent-card.json
```

期望看到：

- `name` 为 `openjiuwen-deep-agent`
- `capabilities.streaming` 为 `true`
- `supportedInterfaces` 包含 JSON-RPC

发送 A2A streaming 请求：

### Bash

```bash
curl http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  --data-raw '{
    "jsonrpc": "2.0",
    "id": "manual-1",
    "method": "SendStreamingMessage",
    "params": {
      "metadata": {
        "userId": "manual-user",
        "agentId": "openjiuwen-deep-agent"
      },
      "message": {
        "role": "ROLE_USER",
        "messageId": "manual-message-1",
        "contextId": "manual-session-1",
        "parts": [
          {
            "text": "ping"
          }
        ]
      }
    }
  }'
```

### PowerShell

```powershell
$body = @'
{
  "jsonrpc": "2.0",
  "id": "manual-1",
  "method": "SendStreamingMessage",
  "params": {
    "metadata": {
      "userId": "manual-user",
      "agentId": "openjiuwen-deep-agent"
    },
    "message": {
      "role": "ROLE_USER",
      "messageId": "manual-message-1",
      "contextId": "manual-session-1",
      "parts": [
        {
          "text": "ping"
        }
      ]
    }
  }
}
'@

curl.exe http://localhost:8080/a2a `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  --data-raw $body
```

## 输入是什么

A2A 输入是一个 JSON-RPC 2.0 请求：

- `method`：`SendStreamingMessage`，表示请求服务端以 SSE stream 返回消息执行结果。
- `params.message.role`：`ROLE_USER`，表示调用方输入。
- `params.message.messageId`：调用方生成的消息 ID。
- `params.message.contextId`：会话 ID，同一会话可复用。
- `params.metadata.userId`：示例用户 ID。
- `params.metadata.agentId`：目标 agent，本示例固定为 `openjiuwen-deep-agent`。
- `params.message.parts[0].text`：用户输入文本，本示例 happy path 使用 `ping`。

DeepAgent 配置中的 system prompt 明确要求：如果用户消息正好是 `ping`，则只回答 `pong`。

## 输出是什么

HTTP 响应是 `text/event-stream`。每个 SSE `data:` 都是一个完整 JSON-RPC response，response 的 `result` 是 A2A SDK streaming event。

典型输出顺序：

1. accepted/working 类事件：表示 runtime 已接收并开始执行任务。
2. artifact 或 status message 事件：携带 agent 生成的文本片段。
3. terminal status event：`TaskStatusUpdateEvent` 中的 state 进入 `completed`、`failed`、`canceled` 或 `rejected`。

happy path 的最终用户可见文本是：

```text
pong
```

自动化测试不依赖 message metadata 判断终态，而是优先按 A2A SDK 的 stream event 判断是否终止，然后从 `Message`、`TaskStatusUpdateEvent.status.message` 和 `TaskArtifactUpdateEvent.artifact` 中抽取文本。

## 排错

- `SAA_SAMPLE_LLM_API_KEY` 为空
  - 自动化测试会跳过真实 LLM 调用。设置环境变量后重新运行示例测试。
- 模型调用失败
  - 检查 `SAA_SAMPLE_OPENJIUWEN_API_BASE`、`SAA_SAMPLE_LLM_MODEL` 和 key。
  - 用同一组 base URL/key 直接请求 `/v1/models` 或 `/v1/chat/completions`。
  - 修改环境变量后需要重启服务端进程。
- Maven 找不到本地 `agent-runtime`
  - 先运行上文的 `agent-runtime` 本地安装命令。
- HTTP stream 没有 completed 事件
  - 确认请求头包含 `Accept: text/event-stream`。
  - 确认 `agentId` 是 `openjiuwen-deep-agent`。
  - 查看服务端日志中是否出现 openJiuwen execute failed。
