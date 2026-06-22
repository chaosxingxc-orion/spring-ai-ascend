# MCP 工具中间件

通过 MCP 工具中间件，OpenJiuwen Agent 可以在执行时发现并调用 MCP Server 暴露的工具。公共层使用 `McpProvider` SPI，OpenJiuwen 通过 `OpenJiuwenMcpToolInstaller` 安装工具。

## 1. 概述

```java
public interface McpProvider {
    List<McpToolSpec> listTools(AgentExecutionContext context);

    McpToolResult callTool(AgentExecutionContext context, String serverId,
            String name, Map<String, Object> arguments);
}
```

MCP 工具中间件不改变 `AgentRuntimeHandler` 的执行接口。无 MCP 配置时，不创建 provider，也不影响 Agent 启动。

## 2. 快速开始

### Step 1 — 配置 MCP Server

```yaml
agent-runtime:
  mcp:
    servers:
      - server-id: localtime
        transport: streamable-http
        url: http://localhost:8091/mcp
        connect-timeout: 5s
        request-timeout: 30s
```

SSE Server 和静态 Header 示例：

```yaml
agent-runtime:
  mcp:
    servers:
      - server-id: howtocook-mcp
        transport: sse
        url: https://mcp.api-inference.modelscope.net/136ad5a3226b4d/sse
        headers:
          Authorization: Bearer ${MCP_API_TOKEN}
```

### Step 2 — 自动或手工安装到 OpenJiuwen

Spring Boot 自动装配条件：

- 存在 `agent-runtime.mcp.servers[0].url`
- classpath 中存在 OpenJiuwen
- Spring 容器中存在 `OpenJiuwenAgentRuntimeHandler`

满足条件时，runtime 创建 `HttpMcpProvider` 和 `OpenJiuwenMcpToolInstaller`，并注入 handler。

手工 wiring：

```java
OpenJiuwenAgentRuntimeHandler handler = ...;
handler.setMcpToolInstaller(new OpenJiuwenMcpToolInstaller(mcpProvider));
```

## 3. 工作原理

```text
OpenJiuwenAgentRuntimeHandler.doExecute(...)
  │
  ├─ createOpenJiuwenAgent(context)
  ├─ installRuntimeTools(agent, context)
  │     └─ OpenJiuwenMcpToolInstaller.install(...)
  │          ├─ mcpProvider.listTools(context)
  │          ├─ 生成 ToolCard
  │          └─ 注册 OpenJiuwen Tool
  └─ Runner.runAgentStreaming(...)
        └─ 模型触发 Tool.invoke(...)
              └─ mcpProvider.callTool(...)
```

`McpToolSpec.name` 是 MCP 原始工具名。多个 server 出现同名工具时，OpenJiuwen installer 会生成框架内名称，例如 `mcp_weather_query`，同时在 ToolCard properties 中保留原始 `serverId` 和 `toolName`。

## 4. 常用配置

| 配置 | 说明 |
|---|---|
| `agent-runtime.mcp.servers[*].server-id` | MCP Server 标识 |
| `agent-runtime.mcp.servers[*].transport` | `streamable-http` 或 `sse` |
| `agent-runtime.mcp.servers[*].url` | MCP endpoint |
| `agent-runtime.mcp.servers[*].headers` | 静态 Header |
| `agent-runtime.mcp.servers[*].connect-timeout` | 连接超时 |
| `agent-runtime.mcp.servers[*].request-timeout` | 请求超时 |

## 5. 错误处理

| 场景 | 行为 |
|---|---|
| MCP Server 不可达 | 工具发现失败只记录日志；工具调用返回 `MCP_SERVER_UNAVAILABLE` |
| 鉴权失败 | 返回 `MCP_AUTH_FAILED` |
| 响应不是合法 JSON-RPC | 返回 `MCP_BAD_RESPONSE` |
| serverId 未配置 | 返回 `MCP_SERVER_NOT_CONFIGURED` |

## 6. 示例

| Example | 说明 |
|---|---|
| `examples/agent-runtime-middleware-mcp-localtime/` | 本地 date/time MCP Server + OpenJiuwen runtime |
| `examples/agent-runtime-middleware-mcp-remote-json/` | 从 JSON 文件接入远端 MCP Server |

运行示例：

```bash
./mvnw -f examples/agent-runtime-middleware-mcp-localtime/pom.xml verify
./mvnw -f examples/agent-runtime-middleware-mcp-remote-json/pom.xml verify
```

手工 curl 流程见对应 example 的 `README.md` 和 `TUTORIAL.cn.md`。

## 7. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/mcp-runtime-tool-middleware-design.md`
- Proposal：`docs/logs/reviews/2026-06-16-agent-runtime-mcp-middleware-proposal.cn.md`
- [OpenJiuwen Adapter](openjiuwen-adapter.md)
