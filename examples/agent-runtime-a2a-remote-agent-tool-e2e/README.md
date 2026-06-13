# agent-runtime A2A remote Agent tool E2E example

本示例演示“远端 A2A Agent 作为本地 OpenJiuwen tool 调用”的链路。示例用同一个 jar 启两个 `agent-runtime` 实例：

- **Remote A2A Agent**（profile `remote-agent`）：mock A2A Agent，不需要大模型。它会流式返回消息，第一轮进入 `INPUT_REQUIRED`，第二轮收到续写后 `COMPLETED`。
- **Local OpenJiuwen Agent**（profile `local-agent`）：LLM 驱动的 OpenJiuwen `ReActAgent`。启动后通过 A2A Agent Card 发现远端 Agent，把它注入为本地 tool：`a2a_remote_remote_a2a_agent`。

## 自动化与手动覆盖边界

自动化测试 `RemoteA2aToolInvocationE2eTest` 保持真实 LLM 路径：它会启动 remote-agent 和 local-agent，验证本地 OpenJiuwen Agent 能在大模型驱动下选择远端 tool。该测试需要 `SAA_SAMPLE_LLM_API_KEY`；未设置时会按 JUnit assumption 自动跳过，适合流水线无大模型环境。

完整两轮闭环建议手动验证：

1. 第一轮：local-agent 发现 remote-agent card，注入 `a2a_remote_remote_a2a_agent`，LLM 选择 tool，remote-agent 返回 stream progress 和 `INPUT_REQUIRED`。
2. 第二轮：用户继续输入，runtime 使用本地 parent task 中保存的远端 `taskId/contextId` 续写 remote-agent；remote-agent `COMPLETED` 后，runtime 将完成文本作为 tool result 回灌给 local-agent，local-agent 输出最终 answer。

## 环境变量

remote-agent 不需要大模型。local-agent 需要大模型。

Bash:

```bash
export SAA_SAMPLE_LLM_API_KEY=sk-xxx
export SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai
export SAA_SAMPLE_OPENJIUWEN_API_BASE=http://localhost:4000/v1
export SAA_SAMPLE_LLM_MODEL=deepseek-chat
export SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY=false
```

PowerShell:

```powershell
$env:SAA_SAMPLE_LLM_API_KEY = "sk-xxx"
$env:SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER = "openai"
$env:SAA_SAMPLE_OPENJIUWEN_API_BASE = "http://localhost:4000/v1"
$env:SAA_SAMPLE_LLM_MODEL = "deepseek-chat"
$env:SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY = "false"
```

## 构建

```bash
mvn -f examples/agent-runtime-a2a-remote-agent-tool-e2e/pom.xml package -DskipTests
```

PowerShell:

```powershell
mvn -f examples/agent-runtime-a2a-remote-agent-tool-e2e/pom.xml package "-DskipTests"
```

## 手动验证

### 第一步：启动 Remote A2A Agent

Bash:

```bash
java -jar examples/agent-runtime-a2a-remote-agent-tool-e2e/target/agent-runtime-a2a-remote-agent-tool-e2e-example-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=remote-agent \
  --server.port=18082
```

PowerShell:

```powershell
java -jar examples/agent-runtime-a2a-remote-agent-tool-e2e/target/agent-runtime-a2a-remote-agent-tool-e2e-example-0.1.0-SNAPSHOT.jar `
  --spring.profiles.active=remote-agent `
  --server.port=18082
```

验证远端 Agent Card:

```bash
curl http://localhost:18082/.well-known/agent-card.json
```

预期：返回 `remote-a2a-agent` 的 Agent Card，`skills` 包含 `remote-a2a-dialog`，`supportedInterfaces` 包含 JSON-RPC `/a2a`。

### 第二步：启动 Local OpenJiuwen Agent

在另一个终端启动。

Bash:

```bash
java -jar examples/agent-runtime-a2a-remote-agent-tool-e2e/target/agent-runtime-a2a-remote-agent-tool-e2e-example-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=local-agent \
  --server.port=18081
```

PowerShell:

```powershell
java -jar examples/agent-runtime-a2a-remote-agent-tool-e2e/target/agent-runtime-a2a-remote-agent-tool-e2e-example-0.1.0-SNAPSHOT.jar `
  --spring.profiles.active=local-agent `
  --server.port=18081
```

`application-local-agent.yaml` 默认配置：

```yaml
agent-runtime:
  remote-agents:
    - url: http://localhost:18082
```

如果 remote-agent 在其他地址，启动 local-agent 时追加：

```bash
--agent-runtime.remote-agents[0].url=http://other-host:18082
```

等待 local-agent 日志出现远端 card 发现和 tool installer 相关输出。关键现象是 remote-agent 被解析成：

```text
remoteAgentId=remote-a2a-agent
toolName=a2a_remote_remote_a2a_agent
endpoint=http://localhost:18082/a2a
```

验证 local-agent card:

```bash
curl http://localhost:18081/.well-known/agent-card.json
```

### 第三步：第一轮请求，触发远端 tool 并进入 INPUT_REQUIRED

Bash:

```bash
curl http://localhost:18081/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "SendStreamingMessage",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-1",
        "contextId": "ctx-remote-agent-tool-1",
        "metadata": {"userId": "manual-user", "agentId": "local-openjiuwen"},
        "parts": [{"text": "Please call the remote A2A agent to run the streaming input-required demo."}]
      }
    }
  }'
```

PowerShell:

```powershell
$body = @'
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "SendStreamingMessage",
  "params": {
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-1",
      "contextId": "ctx-remote-agent-tool-1",
      "metadata": {"userId": "manual-user", "agentId": "local-openjiuwen"},
      "parts": [{"text": "Please call the remote A2A agent to run the streaming input-required demo."}]
    }
  }
}
'@

curl.exe http://localhost:18081/a2a `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  --data-raw $body
```

预期：

- SSE 中能看到 `Remote agent first stream message 1`、`Remote agent first stream message 2`。
- parent task 进入 `TASK_STATE_INPUT_REQUIRED`。
- 返回事件里能看到本地 parent `taskId`，下一轮续写需要复用这个 `taskId` 和同一个 `contextId`。

### 第四步：第二轮续写，完成远端并回灌 Local OpenJiuwen Agent

推荐使用交互式客户端完成第二轮。它会在收到 `INPUT_REQUIRED` 后记录 parent `taskId`，下一条输入自动带上该 taskId。

Bash:

```bash
mvn -f examples/agent-runtime-a2a-remote-agent-tool-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.remoteagenttool.A2aConsoleClientApplication \
  -Dexec.args="http://localhost:18081 local-openjiuwen manual-user"
```

PowerShell:

```powershell
mvn -f examples/agent-runtime-a2a-remote-agent-tool-e2e/pom.xml `
  exec:java `
  "-Dexec.mainClass=com.huawei.ascend.examples.a2a.remoteagenttool.A2aConsoleClientApplication" `
  "-Dexec.args=http://localhost:18081 local-openjiuwen manual-user"
```

交互步骤：

1. 输入：`Please call the remote A2A agent to run the streaming input-required demo.`
2. 看到 `[agent needs more input - type your follow-up]` 后输入任意补充内容。
3. 预期第二轮能看到 `Remote agent second stream message`；随后 remote-agent completed 文本被作为 tool result 回灌给 local-agent，最终由 local-agent 输出摘要，并使 parent task `TASK_STATE_COMPLETED`。

## JUnit 测试

```bash
mvn -f examples/agent-runtime-a2a-remote-agent-tool-e2e/pom.xml test
```

PowerShell:

```powershell
mvn -f examples/agent-runtime-a2a-remote-agent-tool-e2e/pom.xml test
```

说明：

- `RemoteA2aToolInvocationE2eTest` 保持真实 LLM 路径，验证 local-agent 能在大模型驱动下选择 `a2a_remote_remote_a2a_agent`。
- 未设置 `SAA_SAMPLE_LLM_API_KEY` 时，该测试会自动跳过，不会让无大模型的流水线失败。
- 完整第二轮 continuation 和 completed 回灌建议按上面的手动测试执行。

## 链路覆盖

| 链路 | 自动化测试 | 手动测试 |
|---|---|---|
| local-agent 通过 `agent-runtime.remote-agents[0].url` 发现 remote-agent card | 有 key 时覆盖 | 覆盖 |
| 根据 remote-agent card 生成 `RemoteAgentToolSpec` 和 `a2a_remote_remote_a2a_agent` | 有 key 时覆盖 | 覆盖 |
| LLM 自主选择远端 tool | 有 key 时覆盖 | 覆盖 |
| `OpenJiuwenRemoteAgentInterruptRail` 中断并转成 remote invocation | 有 key 时覆盖 | 覆盖 |
| Runtime 通过 A2A client 出站调用 remote-agent | 有 key 时覆盖 | 覆盖 |
| remote-agent 第一轮 stream progress 并返回 `INPUT_REQUIRED` | 有 key 时覆盖 | 覆盖 |
| 第二轮 continuation 带 parent taskId/contextId 续写远端 task | 不覆盖 | 覆盖 |
| remote-agent `COMPLETED` 后回灌 local-agent 并产出最终 answer | 不覆盖 | 覆盖 |
