# Remote JSON MCP 样例教程

跟着做完，你会把 MCP Server 信息写入一个 JSON 文件，再启动 OpenJiuwen A2A Runtime，通过 curl 验证 runtime 能从 JSON 配置发现并调用 MCP 工具。

---

## 0. 你将验证什么

本样例验证 JSON 配置驱动的 MCP 工具接入链路：

```text
mcp-servers.example.json
        -> RemoteMcpServerConfigLoader
        -> HttpMcpProvider
        -> OpenJiuwenMcpToolInstaller
        -> OpenJiuwen ReActAgent
        -> curl /a2a 触发工具调用
```

它和 `agent-runtime-middleware-mcp-localtime` 的区别是：

- localtime example 通过 Spring properties 传入 MCP Server。
- remote-json example 通过 JSON 文件传入 MCP Server，更接近客户平台集中维护远端工具配置的方式。

---

## 1. 环境准备

在仓库根目录执行命令。需要：

- JDK 21
- curl
- 可用的 OpenAI-compatible LLM API
- 本地 8095 和 8082 端口未被占用

Windows PowerShell 可以把下面的 `./mvnw` 换成 `./mvnw.cmd`，把 `export KEY=value` 换成 `$env:KEY="value"`。

---

## 2. Step 1 - 准备 MCP Server JSON

默认配置文件：

```text
examples/agent-runtime-middleware-mcp-remote-json/mcp-servers.example.json
```

默认内容指向本地可复现 MCP Server：

```json
{
  "servers": [
    {
      "serverId": "remote-tools",
      "transport": "streamable-http",
      "url": "http://127.0.0.1:8095/mcp",
      "headers": {
      }
    }
  ]
}
```

本样例也兼容常见的 MCP 客户端配置格式。例如 ModelScope SSE MCP Server 可以写成：

```json
{
  "mcpServers": {
    "howtocook-mcp": {
      "type": "sse",
      "url": "https://mcp.api-inference.modelscope.net/136ad5a3226b4d/sse"
    }
  }
}
```

这里的 `howtocook-mcp` 会作为 runtime 的 `serverId`，`type: "sse"` 会映射为 runtime MCP transport。SSE Server 会先返回 `endpoint` 事件，runtime 再向该 endpoint 发送 JSON-RPC 请求。

如果你有真实远端 MCP Server，只需要把 `url` 改成远端地址。需要静态鉴权时，把 `headers`
放在对应的 server 配置对象内部，和 `serverId`、`transport`、`url` 同一级。例如：

```json
{
  "servers": [
    {
      "serverId": "company-tools",
      "transport": "streamable-http",
      "url": "https://your-company.example.com/mcp",
      "headers": {
        "Authorization": "Bearer demo-token"
      }
    }
  ]
}
```

如果使用 `mcpServers` 兼容格式，也是在具体 server 对象内加入 `headers`：

```json
{
  "mcpServers": {
    "howtocook-mcp": {
      "type": "sse",
      "url": "https://mcp.api-inference.modelscope.net/136ad5a3226b4d/sse",
      "headers": {
        "Authorization": "Bearer demo-token"
      }
    }
  }
}
```

生产环境中的租户级动态鉴权建议通过自定义 `McpProvider` Bean 实现，不要把动态密钥写进样例 JSON。

---

## 3. Step 2 - 启动一个可复现的 MCP Server

如果你还没有远端 MCP Server，可以先启动 sibling example 中的本地 MCP Server：

```bash
./mvnw -f examples/agent-runtime-middleware-mcp-localtime/pom.xml spring-boot:run \
  -Dspring-boot.run.main-class=com.huawei.ascend.examples.runtime.middleware.mcp.localtime.LocalTimeMcpServerApplication \
  -Dspring-boot.run.arguments="--server.port=8095"
```

这个 server 暴露：

- `get_current_date`
- `get_current_time`
- `get_machine_info`

---

## 4. Step 3 - 启动 Remote JSON Runtime

另开终端：

```bash
export SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai
export SAA_SAMPLE_OPENJIUWEN_API_BASE=https://api.deepseek.com
export SAA_SAMPLE_LLM_MODEL=deepseek-chat
export SAA_SAMPLE_LLM_API_KEY=sk-your-key

./mvnw -f examples/agent-runtime-middleware-mcp-remote-json/pom.xml spring-boot:run \
  -Dspring-boot.run.main-class=com.huawei.ascend.examples.runtime.middleware.mcp.remotejson.McpRemoteJsonRuntimeApplication \
  -Dspring-boot.run.arguments="--server.port=8082 --sample.mcp.config-file=examples/agent-runtime-middleware-mcp-remote-json/mcp-servers.example.json"
```

启动日志应包含：

```text
mcp tools discovered serverId=remote-tools
installed MCP tool into openjiuwen agent=middleware-mcp-remote-json-agent
```

---

## 5. Step 4 - curl 端到端验证

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8082/a2a \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"1","method":"SendMessage","params":{"metadata":{"tenantId":"demo","userId":"remote-mcp-user"},"message":{"role":"ROLE_USER","parts":[{"text":"请通过 MCP 工具查询当前日期"}],"messageId":"m1"}}}'
```

预期：

- runtime 日志出现 `mcp tool call finished serverId=remote-tools toolName=get_current_date`。
- A2A 响应中的 agent message 包含 MCP Server 返回的日期。

---

## 6. 切换到真实远端 MCP Server

修改 JSON：

```json
{
  "servers": [
    {
      "serverId": "company-tools",
      "transport": "streamable-http",
      "url": "https://your-company.example.com/mcp",
      "headers": {
        "Authorization": "Bearer ${replace-me}"
      }
    }
  ]
}
```

然后重启 runtime。第一版样例只演示静态 header；如果远端 MCP Server 需要租户级 OAuth、mTLS 或动态 token，请在业务侧提供自定义 `McpProvider`。

---

## 7. 代码入口

关键代码在：

- `McpRemoteJsonRuntimeApplication.java`
- `RemoteMcpServerConfigLoader`
- `HttpMcpProvider`
- `OpenJiuwenMcpToolInstaller`

---

## 8. 清理

在启动服务的终端按 `Ctrl+C` 停止进程。
