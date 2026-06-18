# Local Tools MCP 样例教程

跟着做完，你会启动一个本地 MCP Server，再启动一个 OpenJiuwen A2A Runtime，通过 curl 让用户请求触发 MCP 工具发现和工具调用。

---

## 0. 你将验证什么

本样例验证 MCP 作为工具中间件的真实接入链路：

```text
curl 请求本地 MCP Server
        -> tools/list 返回 date/time/machine tools
        -> OpenJiuwen Runtime 通过 HttpMcpProvider 发现 tools
        -> OpenJiuwenMcpToolInstaller 把 MCP tools 注册到 Agent
        -> curl 请求 /a2a
        -> LLM 选择并调用 MCP tool
        -> MCP Server 返回日期、时间或本机信息
```

本样例不依赖公网 MCP 广场，适合测试团队先验证 `McpProvider` 的基本接入形态。

---

## 1. 环境准备

在仓库根目录执行命令。需要：

- JDK 21
- curl
- 可用的 OpenAI-compatible LLM API
- 本地 8095 和 8081 端口未被占用

Windows PowerShell 可以把下面的 `./mvnw` 换成 `./mvnw.cmd`，把 `export KEY=value` 换成 `$env:KEY="value"`。

---

## 2. Step 1 - 启动本地 MCP Server

终端 1：

```bash
./mvnw -f examples/agent-runtime-middleware-mcp-localtime/pom.xml spring-boot:run \
  -Dspring-boot.run.main-class=com.huawei.ascend.examples.runtime.middleware.mcp.localtime.LocalTimeMcpServerApplication \
  -Dspring-boot.run.arguments="--server.port=8095"
```

看到 Spring Boot 启动完成后，不要关闭终端。MCP Server 地址：

```text
http://127.0.0.1:8095/mcp
```

---

## 3. Step 2 - 直接验证 MCP tools/list

另开终端执行：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8095/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

预期响应中包含：

- `get_current_date`
- `get_current_time`
- `get_machine_info`

---

## 4. Step 3 - 直接验证 MCP tools/call

查询日期：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8095/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_current_date","arguments":{"timezone":"Asia/Shanghai"}}}'
```

查询本机信息：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8095/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_machine_info","arguments":{}}}'
```

预期响应中的 `result.structuredContent` 包含日期或本机信息字段。

---

## 5. Step 4 - 启动 OpenJiuwen A2A Runtime

终端 2：

```bash
export SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai
export SAA_SAMPLE_OPENJIUWEN_API_BASE=https://api.deepseek.com
export SAA_SAMPLE_LLM_MODEL=deepseek-chat
export SAA_SAMPLE_LLM_API_KEY=sk-your-key

./mvnw -f examples/agent-runtime-middleware-mcp-localtime/pom.xml spring-boot:run \
  -Dspring-boot.run.main-class=com.huawei.ascend.examples.runtime.middleware.mcp.localtime.McpLocalTimeRuntimeApplication \
  -Dspring-boot.run.arguments="--server.port=8081 --agent-runtime.mcp.servers[0].server-id=local-tools --agent-runtime.mcp.servers[0].url=http://127.0.0.1:8095/mcp"
```

启动日志应包含：

```text
installed MCP tool installer into openjiuwen handler
mcp tools discovered serverId=local-tools
installed MCP tool into openjiuwen agent=middleware-mcp-localtime-agent
```

---

## 6. Step 5 - curl 端到端验证

终端 3：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8081/a2a \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"1","method":"SendMessage","params":{"metadata":{"tenantId":"demo","userId":"mcp-user"},"message":{"role":"ROLE_USER","parts":[{"text":"请调用工具告诉我今天日期，并查询本机基础信息"}],"messageId":"m1"}}}'
```

预期：

- runtime 日志出现 `mcp tool call finished serverId=local-tools toolName=get_current_date` 或 `toolName=get_machine_info`。
- A2A 响应中的 agent message 包含 MCP Server 返回的日期或本机信息。

---

## 7. 代码入口

关键代码在：

- `LocalTimeMcpServerApplication.java`
- `McpLocalTimeRuntimeApplication.java`
- `HttpMcpProvider`
- `OpenJiuwenMcpToolInstaller`

业务侧复用这个模式时，可以直接配置：

```bash
--agent-runtime.mcp.servers[0].server-id=local-tools
--agent-runtime.mcp.servers[0].url=http://127.0.0.1:8095/mcp
```

如果 MCP Server 需要静态鉴权 header，可追加：

```bash
--agent-runtime.mcp.servers[0].headers.Authorization="Bearer demo-token"
```

生产环境中的租户级动态鉴权应通过自定义 `McpProvider` Bean 实现，不要把密钥写入 AgentCard、tool description 或普通日志。

---

## 8. 清理

在两个启动服务的终端分别按 `Ctrl+C` 停止进程。
