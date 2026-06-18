# Agent Runtime MCP Remote JSON Example

本样例验证“从 JSON 文件读取 MCP Server 配置”的接入方式。它适合客户或测试团队把一个远端 MCP Server 的 URL、transport、静态 headers 写入 JSON，再启动 OpenJiuwen Runtime 完成工具发现和调用。

完整 step by step 教程见：[TUTORIAL.cn.md](TUTORIAL.cn.md)。

## 配置文件

默认配置文件：

```text
examples/agent-runtime-middleware-mcp-remote-json/mcp-servers.example.json
```

格式：

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

同时也兼容常见的 MCP 客户端配置写法：

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

其中对象 key 会作为 `serverId`，`type` 会映射到 runtime 的 `transport`。

把 `url` 换成真实远端 MCP Server 地址即可。需要鉴权时，可在 `headers` 中加入静态 header；生产级动态鉴权建议自定义 `McpProvider`。

## 快速启动

为了保证本地可复现，可以先启动 sibling example 中的本地 MCP Server：

```bash
./mvnw -f examples/agent-runtime-middleware-mcp-localtime/pom.xml spring-boot:run \
  -Dspring-boot.run.main-class=com.huawei.ascend.examples.runtime.middleware.mcp.localtime.LocalTimeMcpServerApplication \
  -Dspring-boot.run.arguments="--server.port=8095"
```

再启动本样例 runtime：

```bash
export SAA_SAMPLE_LLM_API_KEY="<your-api-key>"
export SAA_SAMPLE_OPENJIUWEN_API_BASE="https://api.deepseek.com"
export SAA_SAMPLE_LLM_MODEL="deepseek-chat"

./mvnw -f examples/agent-runtime-middleware-mcp-remote-json/pom.xml spring-boot:run \
  -Dspring-boot.run.main-class=com.huawei.ascend.examples.runtime.middleware.mcp.remotejson.McpRemoteJsonRuntimeApplication \
  -Dspring-boot.run.arguments="--server.port=8082 --sample.mcp.config-file=examples/agent-runtime-middleware-mcp-remote-json/mcp-servers.example.json"
```

curl 验证：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8082/a2a \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"1","method":"SendMessage","params":{"metadata":{"tenantId":"demo","userId":"remote-mcp-user"},"message":{"role":"ROLE_USER","parts":[{"text":"请通过 MCP 工具查询当前日期"}],"messageId":"m1"}}}'
```

Windows PowerShell 可将 `./mvnw` 换成 `./mvnw.cmd`，并把 `export KEY=value` 换成 `$env:KEY="value"`。
