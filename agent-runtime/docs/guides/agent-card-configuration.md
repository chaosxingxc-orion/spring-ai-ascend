# Agent Card 配置

配置 A2A Agent Card——Agent 的能力名片，通过 `GET /.well-known/agent-card.json` 暴露给 A2A 客户端和远程 Agent。

## 1. 概述

```yaml
# 最小示例：不配置任何属性，runtime 自动从 handler.agentId() 生成
# 结果：GET /.well-known/agent-card.json 返回 name=agentId 的最小卡片
```

## 2. 快速开始

### YAML 配置

```yaml
agent-runtime:
  access:
    a2a:
      agent-card:
        name: my-agent
        description: 我的自定义 Agent
        version: "1.0.0"
```

配置后 `GET /.well-known/agent-card.json` 返回包含上述字段的卡片。未配置的字段使用默认值。

### skills 声明

```yaml
agent-runtime:
  access:
    a2a:
      agent-card:
        name: my-agent
        skills:
          - id: my-skill
            name: My Skill
            description: 这个 skill 会被远程 Agent 发现并注册为 Tool
```

声明 skills 后，远程 Agent 可通过 A2A 发现并注册为可调用 Tool。无 skills 的 Agent 不会被注入为远程 Tool。

## 3. 工作原理

```
配置优先级（高→低）：
  @Bean AgentCard       → 完全接管
  AgentCardProvider     → Handler 编程声明
  YAML 配置              → application.yaml
  自动生成               → 从 handler.agentId() 推导
```

## 4. 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | handler.agentId() | Agent 名称 |
| `description` | String | `agent-runtime` | Agent 描述 |
| `version` | String | `0.1.0` | 版本号 |
| `organization` | String | `spring-ai-ascend` | 组织名 |
| `organization-url` | String | `http://localhost:8080` | 组织 URL |
| `endpoint` | String | `/a2a` | A2A 端点路径 |
| `skills` | List | — | Skill 声明（远程 Tool 注入的依据） |
| `capabilities` | Map | — | 能力宣告（streaming / pushNotifications） |

**必配项**：无。所有属性都有默认值，不配置可正常运行。

**关键行为**：`skills` 是非必配但决定性的——有 skills 的 Agent 才能被远程 Agent 发现并注册为 Tool。

## 5. 能力详述

### 编程覆盖

**方式 A：自定义 AgentCard Bean**（最高优先级）

```java
@Bean AgentCard agentCard() {
    return AgentCard.builder()
        .name("custom-name").description("Custom").version("2.0").build();
}
```

**方式 B：AgentCardProvider**（Handler 编程声明）

```java
public class MyHandler extends OpenJiuwenAgentRuntimeHandler implements AgentCardProvider {
    @Override public AgentCardDescriptor describe() {
        return AgentCardDescriptor.builder()
            .skills(List.of(Skill.builder().id("s1").name("S1")
                .description("Skill description").build()))
            .build();
    }
}
```

### public-base-url

反向代理后必配：

```yaml
agent-runtime:
  access:
    a2a:
      public-base-url: https://agents.example.com/runtime
```

### 端点发现

```bash
curl -s http://localhost:8080/.well-known/agent-card.json | jq .
curl -s http://localhost:8080/.well-known/agent.json | jq .
```

## 6. 完整示例

```yaml
agent-runtime:
  access:
    a2a:
      public-base-url: https://agents.example.com/runtime
      agent-card:
        name: travel-assistant
        description: 旅行助手 Agent，可查询天气和预订酒店
        version: "1.0.0"
        skills:
          - id: query-weather
            name: Query Weather
            description: 查询指定城市的天气信息
          - id: book-hotel
            name: Book Hotel
            description: 预订指定城市的酒店
        capabilities:
          streaming: true
```

预期结果：`GET /.well-known/agent-card.json` 返回包含 skills 的完整卡片。远程 Agent 配置 URL 指向本 Agent 后，会发现两个 Tool。

## 7. 限制

- 无 skills 的 Agent Card 不会被远程 Agent 注入为 Tool

## 8. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/a2a-protocol-and-communication-design.md` §2.3, §4.4
- [A2A 协议调用](a2a-endpoints.md)
- [远程调用](remote-invocation.md)
- Example：`examples/agent-runtime-openjiuwen-simple/`
