# Agent Runtime MCP Local Tools Example

本样例验证 `agent-runtime` 接入本地真实 MCP Server 的最小链路。它不是 mock provider，而是启动一个可通过 JSON-RPC HTTP 访问的 MCP Server，再让 OpenJiuwen Runtime 发现并调用工具。

完整 step by step 教程见：[TUTORIAL.cn.md](TUTORIAL.cn.md)。

## 验证链路

```text
本地 MCP Server (/mcp)
  -> runtime-neutral HttpMcpProvider
  -> OpenJiuwenMcpToolInstaller
  -> OpenJiuwen ReActAgent tool call
  -> A2A /a2a 返回最终回答
```

## 工具列表

| MCP tool | 用途 |
|---|---|
| `get_current_date` | 查询指定时区当前日期 |
| `get_current_time` | 查询指定时区当前时间 |
| `get_machine_info` | 查询本机 OS、CPU 数量、Java 版本等基础信息 |

## 快速启动

终端 1 启动 MCP Server：

```bash
./mvnw -f examples/agent-runtime-middleware-mcp-localtime/pom.xml spring-boot:run \
  -Dspring-boot.run.main-class=com.huawei.ascend.examples.runtime.middleware.mcp.localtime.LocalTimeMcpServerApplication \
  -Dspring-boot.run.arguments="--server.port=8095"
```

终端 2 启动 OpenJiuwen A2A Runtime：

```bash
export SAA_SAMPLE_LLM_API_KEY="<your-api-key>"
export SAA_SAMPLE_OPENJIUWEN_API_BASE="https://api.deepseek.com"
export SAA_SAMPLE_LLM_MODEL="deepseek-chat"

./mvnw -f examples/agent-runtime-middleware-mcp-localtime/pom.xml spring-boot:run \
  -Dspring-boot.run.main-class=com.huawei.ascend.examples.runtime.middleware.mcp.localtime.McpLocalTimeRuntimeApplication \
  -Dspring-boot.run.arguments="--server.port=8081 --agent-runtime.mcp.servers[0].server-id=local-tools --agent-runtime.mcp.servers[0].url=http://127.0.0.1:8095/mcp"
```

终端 3 发起 A2A 请求：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8081/a2a \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"1","method":"SendMessage","params":{"metadata":{"tenantId":"demo","userId":"mcp-user"},"message":{"role":"ROLE_USER","parts":[{"text":"请调用工具告诉我今天日期，并顺便查询本机基础信息"}],"messageId":"m1"}}}'
```

预期：
- runtime 日志出现 `mcp tools discovered serverId=local-tools`。
- runtime 日志出现 `mcp tool call finished serverId=local-tools`。
- A2A 响应包含 MCP Server 返回的日期或本机信息。

Windows PowerShell 可将 `./mvnw` 换成 `./mvnw.cmd`，并把 `export KEY=value` 换成 `$env:KEY="value"`。
