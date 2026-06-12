# agent-runtime remote A2A OpenJiuwen E2E example

两个 `agent-runtime` 实例通过 A2A 远程调用协作：

- **Agent B**（远端，profile `agent-b`）：mock agent，流式返回消息 → `INPUT_REQUIRED` → 续写 → `COMPLETED`。**不需要大模型**。
- **Agent A**（本地，profile `agent-a`）：LLM 驱动的 OpenJiuwen `ReActAgent`。启动后通过 A2A card 发现 Agent B，将其注入为 tool（`a2a_remote_remote_b`），大模型自主选择调用。

## 环境变量

Agent A 需要大模型，Agent B 不需要：

```bash
export SAA_SAMPLE_LLM_API_KEY=sk-x00550472
export SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai
export SAA_SAMPLE_OPENJIUWEN_API_BASE=http://localhost:4000/v1
export SAA_SAMPLE_LLM_MODEL=gpt-5.4-mini
export SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY=false
```

## 构建

```bash
mvn -f examples/agent-runtime-a2a-remote-openjiuwen-e2e/pom.xml package -DskipTests
```

## 手工验证

### 第一步：启动 Agent B（远端，mock）

```bash
java -jar examples/agent-runtime-a2a-remote-openjiuwen-e2e/target/agent-runtime-a2a-remote-openjiuwen-e2e-example-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=agent-b \
  --server.port=18082
```

验证 card：

```bash
curl http://localhost:18082/.well-known/agent-card.json
```

预期：返回 `remote-b` 的 agent card，skills 包含 `remote-b-dialog`。

### 第二步：启动 Agent A（本地，LLM）

在**另一个终端**启动：

```bash
java -jar examples/agent-runtime-a2a-remote-openjiuwen-e2e/target/agent-runtime-a2a-remote-openjiuwen-e2e-example-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=agent-a \
  --server.port=18081
```

> `application-agent-a.yaml` 已配置 `agent-runtime.remote-agents[0].url=http://localhost:18082`（默认）。
> 如果 Agent B 在其他地址，追加 `--agent-runtime.remote-agents[0].url=http://other-host:18082`。

等待日志出现（关键 DFx 输出）：

```
remote agent card cache initialized configuredUrls=1 uniqueUrls=1
installed remote tool installer into openjiuwen handler agentId=local-a
remote agent card cache refresher started intervalMs=5000
remote agent card refresh starting pendingUrls=1 totalUrls=1
remote agent card resolving url=http://localhost:18082
remote agent card resolved url=http://localhost:18082 agentId=remote-b toolName=a2a_remote_remote_b endpoint=http://localhost:18082/a2a
remote agent card refresh complete succeeded=1 failed=0 availableTools=1
```

验证 card：

```bash
curl http://localhost:18081/.well-known/agent-card.json
```

### 第三步：发送请求

使用 curl 发送 A2A JSON-RPC 请求：

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
        "contextId": "ctx-1",
        "metadata": {"userId": "manual-user", "agentId": "local-a"},
        "parts": [{"text": "Please call remote AgentB to run the streaming input-required demo."}]
      }
    }
  }'
```

**成功现象：**
- SSE 中能看到远端 Agent B 的流式消息（`AgentB first stream message` 等）
- 最终 `TASK_STATE_COMPLETED`，响应文本包含 Agent B 的 tool result 摘要

也可使用交互式控制台客户端：

```bash
mvn -f examples/agent-runtime-a2a-remote-openjiuwen-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.remoteopenjiuwen.A2aConsoleClientApplication \
  -Dexec.args="http://localhost:18081 local-a manual-user"
```

出现 `>` 提示符后输入消息回车，输入 `exit` 退出。

## 配置文件说明

| 文件 | 用途 |
|------|------|
| `application.yaml` | 公共配置（server.port） |
| `application-agent-a.yaml` | Agent A：runtime 完整配置 + LLM + `agent-runtime.remote-agents` 发现 Agent B |
| `application-agent-b.yaml` | Agent B：runtime 完整配置，无远端依赖 |

## JUnit 测试

```bash
mvn -f examples/agent-runtime-a2a-remote-openjiuwen-e2e/pom.xml test
```

> 真实 LLM 测试需要 `SAA_SAMPLE_LLM_API_KEY`，未设置时自动跳过。

## 链路覆盖

- Agent A 通过 `agent-runtime.remote-agents[0].url` 发现 Agent B 的 A2A card
- Runtime 根据 card 生成 `RemoteAgentToolSpec`，注入为 OpenJiuwen tool `a2a_remote_remote_b`
- LLM 选择调用该 tool → `OpenJiuwenRemoteAgentInterruptRail` 中断 → `REMOTE_INVOCATION`
- Runtime 通过 A2A client 出站调用 Agent B
- Agent B 流式返回 → 完成后 runtime 将结果 resume Agent A → Agent A 生成摘要 → `COMPLETED`
