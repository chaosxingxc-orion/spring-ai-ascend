# AgentCard 配置

A2A Agent Card（在 `/.well-known/agent-card.json` 提供）向客户端描述你的 Agent：
名称、能力、端点、提供商元数据。

## 配置优先级（从高到低）

| 优先级 | 方式 | 适用场景 |
|---|---|---|
| 1 | 自定义 `@Bean AgentCard` | 需要完全编程控制 |
| 2 | `AgentCardProvider` Bean | 需要运行时动态生成卡片 |
| 3 | YAML `agent-runtime.access.a2a.agent-card.*` | 声明式静态配置 |
| 4 | 从 `handler.agentId()` 自动生成 | 零配置默认 |

## YAML 配置

```yaml
agent-runtime:
  access:
    a2a:
      agent-card:
        name: my-agent-id                   # 默认值：handler.agentId()
        description: 我的自定义 Agent       # 默认值："agent-runtime"
        version: "1.0.0"                    # 默认值："0.1.0"
        organization: my-org                # 默认值："spring-ai-ascend"
        organization-url: https://my.co     # 默认值："http://localhost:8080"
        endpoint: /a2a                      # 默认值："/a2a"
```

所有字段可选。未设置的字段使用合理默认值。若 `name` 未设置，runtime 从
第一个 `AgentRuntimeHandler` 的 `agentId()` 派生卡片名称。

## 编程覆盖

### 方式 A：自定义 AgentCard Bean

```java
@Bean
public AgentCard myAgentCard() {
    return AgentCard.builder()
        .name("my-agent")
        .description("自定义 Agent")
        .version("1.0")
        .url("/a2a")
        .provider(new AgentProvider("my-org", "https://my.co"))
        .capabilities(AgentCapabilities.builder()
            .streaming(true).pushNotifications(true).build())
        .defaultInputModes(List.of("text"))
        .defaultOutputModes(List.of("text"))
        .supportedInterfaces(List.of(
            new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
        .build();
}
```

此方式完全替代自动生成的卡片。利用 `@ConditionalOnMissingBean` 语义——
只要存在任意 `AgentCard` Bean，自动配置就退让。

### 方式 B：AgentCardProvider

```java
@Bean
public AgentCardProvider myCardProvider() {
    return () -> buildDynamicCard();
}
```

适用于需要运行时计算的场景（如从配置服务读取）。

## 自动生成（零配置）

当没有 `AgentCard` Bean、没有 `AgentCardProvider`、且 YAML 中未设置 `name` 时，
runtime 自动生成：

```
name:        <第一个 handler 的 agentId()>   例如 "openjiuwen-simple-agent"
description: "agent-runtime"
version:     "0.1.0"
provider:    AgentProvider("spring-ai-ascend", "http://localhost:8080")
endpoint:    "/a2a"
capabilities: streaming=true, pushNotifications=true
```

## public-base-url

当 runtime 位于反向代理或负载均衡器之后时，Agent Card 中自动检测的本地 URL
可能无法被外部 A2A 客户端访问。设置：

```yaml
agent-runtime:
  access:
    a2a:
      public-base-url: https://agents.example.com/runtime
```

`AgentCardController` 使用此值作为解析接口 URL 的基础。为空时从当前 HTTP
请求派生。

## 端点发现

客户端通过以下端点发现 Agent Card：

```
GET /.well-known/agent-card.json
GET /.well-known/agent.json          # 旧式别名
```

卡片中的 `url` 和 `supportedInterfaces[].url` 相对于请求的基础 URL（或
`public-base-url`，若已设置）解析。当注册了 `ForwardedHeaderFilter` 时，
`AgentCardController` 会遵循 `X-Forwarded-*` 头。
