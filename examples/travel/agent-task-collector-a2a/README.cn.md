# agent-task-collector-a2a — 事项收集 A2A 服务

本模块把 `agent-task-collector` 纯 Java library 托管到 `agent-runtime`，对外暴露 A2A JSON-RPC endpoint。

## 服务信息

- AgentCard name：`task-collector-agent`
- A2A endpoint：`/a2a`
- 默认端口：`13002`
- 下游工具：mock MCP 工具 `travel_calendar_search`、`travel_todo_search`、`travel_policy_lookup`

## 配置

主要配置在 `src/main/resources/application.yaml`：

- `task-collector-agent.llm.*`：LLM provider、api key、api base、model、SSL 校验。
- `task-collector-agent.default-user-id`：缺省用户 ID。
- `task-collector-agent.default-city`：缺省城市。
- `task-collector-agent.mcp.mode`：当前只实现 `mock`。

## 启动

```bash
mvn -f examples/travel/agent-task-collector-a2a/pom.xml spring-boot:run
```

启动后，`agent-trip-a2a` 通过如下 remote agent 配置发现它：

```yaml
agent-runtime:
  remote-agents:
    - url: ${TASK_COLLECTOR_A2A_BASE_URL:http://localhost:13002}
```

注意 remote agent URL 只写 base URL，不追加 `/a2a`。

## 手工测试

可使用仓库现有 A2A fixture 或任意 A2A JSON-RPC client 调用 `http://localhost:13002/a2a`，输入：

```text
帮我收集 2026-06-18 到 2026-06-20 北京出差相关的会议、待办和差标。
```

预期返回中文 Markdown 事项清单。
