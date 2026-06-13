# A2A 远端调用

agent-runtime 支持 Agent 将其他符合 A2A 协议的 Agent 作为工具调用。无需额外
编码——只需配置远端 Agent URL，runtime 自动发现其能力并注册为本地工具。

## 工作原理

```
                            ┌──────────────────┐
                       ┌──> │ 远端 Agent A      │
                       │    │ (天气查询)         │
┌──────────────────────┼    └──────────────────┘
│ 本地 Agent            │
│ └─ LLM 自动选择 tool   │    ┌──────────────────┐
│     ├─ tool: 查天气    ├──> │ 远端 Agent B      │
│     ├─ tool: 订酒店    │    │ (酒店预订)         │
│     └─ tool: ...      │    └──────────────────┘
└──────────────────────┼    ┌──────────────────┐
                       └──> │ 远端 Agent C      │
                            │ (更多能力)         │
                            └──────────────────┘
```

1. 启动时，runtime 从每个配置的远端 URL 拉取 Agent Card，汇总所有 skills 作为 tool spec
2. 本地 Agent 执行时，所有远端 Agent 的能力以独立 tool 形式出现在 tool 列表中
3. LLM 根据用户请求选择调用合适的远端 tool
4. 远端返回的结果作为 tool 结果回传给本地 Agent

## 配置多个远端 Agent

在 `application.yaml` 中列出所有远端 Agent 的地址：

```yaml
agent-runtime:
  remote-agents:
    - url: http://weather-agent:18081      # 天气查询 Agent
    - url: http://hotel-agent:18082        # 酒店预订 Agent
    - url: http://calculator-agent:18083   # 计算服务 Agent
```

每个 `url` 指向一个独立的 agent-runtime 实例（或任何符合 A2A 协议的 Agent 服务）。
运行时最多可配置的远端 Agent 数量没有硬限制。

## 多 Agent 协作示例

### 场景：旅行助手调用多个专业 Agent

**本地 Agent**（旅行助手，端口 8080）配置了 3 个远端：

```yaml
agent-runtime:
  remote-agents:
    - url: http://localhost:18081    # 天气 Agent
    - url: http://localhost:18082    # 酒店 Agent
    - url: http://localhost:18083    # 航班 Agent

sample:
  openjiuwen:
    api-key: sk-xxx
    api-base: http://localhost:4000/v1
    model-name: gpt-5.4-mini
```

### 远端 Agent 启动

每个远端 Agent 是普通的 agent-runtime 实例，各自在不同端口：

```bash
# 终端 1 — 天气 Agent
java -jar weather-agent.jar --server.port=18081

# 终端 2 — 酒店 Agent
java -jar hotel-agent.jar --server.port=18082

# 终端 3 — 航班 Agent
java -jar flight-agent.jar --server.port=18083
```

### 调用效果

本地 Agent 收到 "帮我查北京天气并订个酒店" 后，LLM 在其 tool 列表中看到：

```
可用工具：
- 来自 weather-agent:   query_weather(city, date)
- 来自 hotel-agent:     search_hotels(city, checkin, checkout)
- 来自 flight-agent:    search_flights(from, to, date)
```

LLM 自动按需调用 `query_weather` 和 `search_hotels`，汇总结果返回给用户。

## 自动发现与刷新

配置远端 URL 后，runtime 在启动时：

1. 访问每个 `http://{url}/.well-known/agent-card.json`
2. 读取 Agent Card 中的 `skills` 列表
3. 每个 skill 映射为一个工具规格，携带来源 Agent 的标识
4. 所有远端的工具合并注册到本地 Agent 框架

远端 Agent Card 每 5 秒刷新一次：

- **新增远端**：新启动的远端 runtime 在下一个刷新周期被自动发现
- **远端重启**：重启后 Agent Card 更新，本地自动拉取最新信息
- **远端下线**：不可达的远端工具被移除，不影响其他远端工具的调用

```bash
# 验证所有远端 Agent Card 可发现
curl -s http://localhost:18081/.well-known/agent-card.json | jq '.skills'
curl -s http://localhost:18082/.well-known/agent-card.json | jq '.skills'
curl -s http://localhost:18083/.well-known/agent-card.json | jq '.skills'
```

## openJiuwen 集成

当 classpath 上存在 `openjiuwen-agent-core-java` 时，
`OpenJiuwenRemoteToolInstaller` 自动将所有远端的工具安装到所有
`OpenJiuwenAgentRuntimeHandler` 中：

```java
// 自动完成，无需手动编码
// OpenJiuwenRemoteToolInstaller 发现 handler bean
// → 从 RemoteAgentCardCache 获取所有远端的 tool spec
// → 注册为 openJiuwen Tool
```

### 在 Agent prompt 中利用远端工具

```java
@Override
protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
    // system prompt 中可以提示 LLM 使用远端工具
    String systemPrompt = """
        你是旅行助手。你可以调用以下远端 Agent 的能力来帮助用户：
        - 查天气、订酒店、查航班等
        当用户提出相关需求时，请主动调用对应的工具。
        """;
    // ... 创建 agent
}
```

## 配置参考

| 属性 | 类型 | 说明 |
|---|---|---|
| `agent-runtime.remote-agents[0].url` | String | 第 1 个远端 Agent base URL |
| `agent-runtime.remote-agents[N].url` | String | 第 N 个远端 Agent base URL |

## 架构

```
┌──────────────────────────────────────────┐
│ A2aClientAutoConfiguration（条件激活）    │
│   ├─ RemoteAgentCardCache                │ 缓存所有远端 Agent Card（全量刷新，sticky id）
│   ├─ A2aRemoteAgentOutboundAdapter       │ 发送 A2A 请求到指定远端（按端点缓存 transport）
│   └─ RemoteAgentInvocationService        │ 路由调用到具体远端 Agent，注入 A2aAgentExecutor
├──────────────────────────────────────────┤
│ OpenJiuwenRemoteToolInstaller            │ 汇总所有远端 tool 并安装
└──────────────────────────────────────────┘
```

## 相关

- 示例：`examples/agent-runtime-a2a-remote-openjiuwen-e2e/`
- [适配器总览](adapter-overview.md)
- [openJiuwen 适配器](openjiuwen-adapter.md)
- [配置属性](configuration-properties.md)
- [A2A 端点](a2a-endpoints.md)
