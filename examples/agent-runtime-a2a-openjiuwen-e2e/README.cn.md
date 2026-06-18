# Agent Runtime A2A OpenJiuwen E2E 示例

## 目的

本示例用于验证当前 `agent-runtime` 的单 runtime / 单 agent 绑定形态：启动一个 Spring Boot runtime，注册一个 openJiuwen ReActAgent，并只通过 A2A JSON-RPC streaming endpoint 调用它。

示例位于 `examples/agent-runtime-a2a-openjiuwen-e2e`，包含：

- Spring Boot 服务端：`com.huawei.ascend.examples.a2a.OpenJiuwenA2aAgentRuntimeApplication`
- openJiuwen agent 配置：`com.huawei.ascend.examples.a2a.OpenJiuwenReactAgentConfiguration`
- A2A SDK 客户端测试辅助：`com.huawei.ascend.examples.a2a.SampleA2aClient`
- 端到端测试：`OpenJiuwenReactAgentA2aE2eTest`

## 验证内容

自动化测试覆盖以下路径：

1. 启动一个嵌入式 Spring Boot runtime。
2. 通过 `/.well-known/agent-card.json` 发现 AgentCard。
3. 确认 AgentCard 名称是 `openjiuwen-react-agent`，且声明支持 JSON-RPC streaming。
4. 如果 `SAA_SAMPLE_LLM_API_KEY` 非空，则通过 A2A SDK streaming 调用发送 `ping`。
5. 客户端消费 SSE stream，直到 SDK `TaskStatusUpdateEvent` 或 runtime 兼容事件进入终态。
6. 最终用户可见文本应为 `pong`。

如果没有配置真实模型 key，测试仍会启动服务并验证 AgentCard；真实 LLM 分支会被 JUnit `assumeTrue()` 跳过。

## .env 支持方式

本示例和原 `agent-runtime-a2a-llm-e2e` 一样支持 `.env`，但 `.env` 不会被 Maven 或 Spring Boot 自动加载。helper 脚本会先加载 env 文件，再启动 Maven。

常用模板：

- `.env.example`：列出 OpenJiuwen-only 示例需要的变量。
- `.env.ollama.example`：本地 Ollama 的 OpenAI-compatible `/v1` 配置。
- `.env.openai-compatible.example`：云端或 LiteLLM 等 OpenAI-compatible API 模板。

本地 `.env` 已被 gitignore。当前目录已经可以直接放置 `.env`，里面可以包含真实 key。

## 必要配置

最小配置如下：

```bash
SAA_SAMPLE_LLM_API_KEY=<your-key-or-local-placeholder>
SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai
SAA_SAMPLE_OPENJIUWEN_API_BASE=http://localhost:4000/v1
SAA_SAMPLE_LLM_MODEL=gpt-5.4-mini
SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY=false
SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER=in-memory
```

变量说明：

- `SAA_SAMPLE_LLM_API_KEY`：模型服务 key。非空时真实 LLM E2E 分支才会执行；Ollama 这类本地 gateway 可使用任意非空字符串。
- `SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER`：openJiuwen model provider，当前样例使用 `openai` 表示 OpenAI-compatible `/v1`。
- `SAA_SAMPLE_OPENJIUWEN_API_BASE`：模型服务 base URL，例如 `http://localhost:4000/v1`、`http://localhost:11434/v1` 或 `https://api.openai.com/v1`。
- `SAA_SAMPLE_LLM_MODEL`：模型名称。
- `SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY`：是否校验证书；本地 HTTP 通常为 `false`，云端 HTTPS 通常为 `true`。
- `SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER`：`in-memory` 或 `redis`。
- `SAA_SAMPLE_OPENJIUWEN_REDIS_URL`：只在 checkpointer 设置为 `redis` 时使用。

runtime 默认绑定配置在 `src/main/resources/application.yaml`：

```yaml
agent-runtime:
  access:
    a2a:
      default-tenant-id: sample-tenant
      default-agent-id: openjiuwen-react-agent
```

## 执行自动化测试

推荐使用 helper 脚本：

```bash
cd examples/agent-runtime-a2a-openjiuwen-e2e
cp .env.openai-compatible.example .env
# 编辑 .env 后执行
bash scripts/test-e2e.sh .env
```

Windows PowerShell：

```powershell
cd examples/agent-runtime-a2a-openjiuwen-e2e
Copy-Item .env.openai-compatible.example .env
.\scripts\test-e2e.ps1 -EnvFile .env
```

脚本会完成两件事：

1. `./mvnw -pl agent-runtime -am install -DskipTests -Dmaven.test.skip=true`
2. `./mvnw -f examples/agent-runtime-a2a-openjiuwen-e2e/pom.xml test`

如果你已经手工 export 了环境变量，也可以从仓库根目录直接执行：

```bash
./mvnw -f examples/agent-runtime-a2a-openjiuwen-e2e/pom.xml test
```

## 手工验证步骤

先确认模型 gateway 可访问：

```bash
curl http://localhost:4000/v1/models \
  -H "Authorization: Bearer ${SAA_SAMPLE_LLM_API_KEY:-sk-local-placeholder}"
```

启动服务端：

```bash
cd examples/agent-runtime-a2a-openjiuwen-e2e
bash scripts/run-server.sh .env
```

服务默认监听 `http://localhost:8080`。

### 交互式客户端验证 ping/pong

另开一个终端，启动控制台客户端：

```bash
cd examples/agent-runtime-a2a-openjiuwen-e2e
bash scripts/run-client.sh .env
```

Windows PowerShell：

```powershell
cd examples/agent-runtime-a2a-openjiuwen-e2e
.\scripts\run-client.ps1 -EnvFile .env
```

客户端启动后会打印类似：

```text
Connected to openjiuwen-react-agent at http://localhost:8080
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

如果服务端不在默认地址，可以通过环境变量覆盖：

```bash
SAA_SAMPLE_A2A_BASE_URL=http://localhost:18080 bash scripts/run-client.sh .env
```

也可以直接传 Maven 参数：

```bash
./mvnw -f examples/agent-runtime-a2a-openjiuwen-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication \
  -Dexec.args="http://localhost:18080 openjiuwen-react-agent manual-user"
```

### curl 验证 A2A surface

检查 AgentCard：

```bash
curl http://localhost:8080/.well-known/agent-card.json
```

期望看到：

- `name` 为 `openjiuwen-react-agent`
- `capabilities.streaming` 为 `true`
- `supportedInterfaces` 包含 JSON-RPC

然后发送 A2A streaming 请求：

```bash
curl http://localhost:8080/a2a \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "jsonrpc": "2.0",
    "id": "manual-1",
    "method": "SendStreamingMessage",
    "params": {
      "metadata": {
        "userId": "manual-user",
        "agentId": "openjiuwen-react-agent"
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

## 输入是什么

A2A 输入是一个 JSON-RPC 2.0 请求：

- `method`：`SendStreamingMessage`，表示请求服务端以 SSE stream 返回消息执行结果。
- `params.message.role`：`ROLE_USER`，表示调用方输入。
- `params.message.messageId`：调用方生成的消息 ID。
- `params.message.contextId`：会话 ID，同一会话可复用。
- `params.metadata.userId`：示例用户 ID。
- `params.metadata.agentId`：目标 agent，本示例固定为 `openjiuwen-react-agent`。
- `params.message.parts[0].text`：用户输入文本，本示例 happy path 使用 `ping`。

OpenJiuwen 配置中的 system prompt 明确要求：如果用户消息正好是 `ping`，则只回答 `pong`。

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

自动化测试不会依赖message metadata 判断终态，而是优先按 A2A SDK 的 stream event 判断是否终止，然后从 `Message`、`TaskStatusUpdateEvent.status.message` 和 `TaskArtifactUpdateEvent.artifact` 中抽取文本。

## 排错

- `SAA_SAMPLE_LLM_API_KEY is blank`
  - 自动化测试会跳过真实 LLM 调用。设置 `.env` 后使用 `bash scripts/test-e2e.sh .env` 重新运行。

- 模型调用失败
  - 检查 `SAA_SAMPLE_OPENJIUWEN_API_BASE`、`SAA_SAMPLE_LLM_MODEL` 和 key。
  - 用同一个 base URL/key 直接 curl `/v1/models` 或 `/v1/chat/completions`。
  - 修改 `.env` 后需要重启 server。

- Maven 找不到 `agent-runtime`
  - 先运行 `./mvnw -pl agent-runtime -am install -DskipTests -Dmaven.test.skip=true`。
  - helper 脚本会自动执行这一步。

- curl stream 没有 completed 事件
  - 确认请求头包含 `Accept: text/event-stream`。
  - 确认 `agentId` 是 `openjiuwen-react-agent`。
  - 查看服务端日志中是否出现 openJiuwen execute failed。
