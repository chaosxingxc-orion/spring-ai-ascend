# openJiuwen Agent 适配器

如何在 agent-runtime 中托管 openJiuwen Agent（ReActAgent 或 DeepAgent）。

## 快速开始（三步）

### 第一步 — 继承 OpenJiuwenAgentRuntimeHandler

```java
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.singleagent.BaseAgent;

public class MyHandler extends OpenJiuwenAgentRuntimeHandler {
    public MyHandler() {
        super("my-agent-id");
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        // 第二步在此实现
    }
}
```

### 第二步 — 实现 createOpenJiuwenAgent()

```java
@Override
protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
    // 2a. 创建 openJiuwen AgentCard（注意：不是 A2A 的那个 AgentCard）
    AgentCard card = AgentCard.builder()
        .id("my-agent-id")
        .name("My Agent")
        .description("...")
        .build();

    // 2b. 创建 ReActAgent，配置 system prompt 和 model client
    ReActAgent agent = new ReActAgent(card);
    ReActAgentConfig config = ReActAgentConfig.builder()
        .promptTemplate(List.of(Map.of("role", "system", "content", "你是一个有用的助手。")))
        .maxIterations(5)
        .build()
        .configureModelClient("openai", apiKey, apiBase, modelName, true);

    // 2c. 可选：调整模型参数
    config.getModelConfigObj().setTemperature(0.7);
    config.getModelConfigObj().setMaxTokens(1024);

    agent.configure(config);
    return agent;
}
```

### 第三步 — 注册为 Spring Bean

```java
@Configuration(proxyBeanMethods = false)
public class MyConfiguration {

    @Bean
    OpenJiuwenAgentRuntimeHandler myHandler(
            @Value("${sample.openjiuwen.api-key}") String apiKey,
            @Value("${sample.openjiuwen.api-base}") String apiBase,
            @Value("${sample.openjiuwen.model-name}") String modelName) {
        return new MyHandler(apiKey, apiBase, modelName);
    }
}
```

只需注册这一个 Bean。Runtime 自动从 handler 的 `agentId` 生成 A2A AgentCard。

## 模型提供商

`configureModelClient(provider, apiKey, apiBase, modelName, sslVerify)` 支持的 provider：

| provider | api-base 示例 |
|---|---|
| `openai` | `https://api.openai.com/v1` |
| `ollama` | `http://localhost:11434/v1` |
| `openai-compatible` | `http://localhost:4000/v1`（litellm 代理） |

## Agent 类型

### ReActAgent

```java
ReActAgent agent = new ReActAgent(card);
ReActAgentConfig config = ReActAgentConfig.builder()
    .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
    .maxIterations(5)          // ReAct 循环最大迭代次数
    .build()
    .configureModelClient(...);
agent.configure(config);
```

### DeepAgent

```java
DeepAgent agent = new DeepAgent(card);
// DeepAgent 使用自己的配置构建器
agent.configure(deepAgentConfig);
```

## 记忆集成

openJiuwen Agent 可以使用 runtime 的 `MemoryProvider` SPI 实现对话记忆。

### ReActAgent（MemoryRuntimeRail）

```java
@Override
protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
    return List.of(memoryRuntimeRail(context, memoryProvider));
}
```

### DeepAgent（ExternalMemoryRail）

```java
@Override
protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
    return List.of(openJiuwenExternalMemoryRail(context, memoryProvider));
}
```

详见[中间件服务](middleware-services.md)。

## 会话持久化（Checkpointer）

openJiuwen checkpointer 持久化 Agent 会话状态：

```java
@Bean
Checkpointer checkpointer() {
    Checkpointer cp = new InMemoryCheckpointer();
    CheckpointerFactory.setDefaultCheckpointer(cp);
    return cp;
}
```

Redis 持久化：

```java
Checkpointer cp = new RedisCheckpointer.Provider()
    .create(Map.of("connection", Map.of("url", "redis://localhost:6379")));
```

## 远端 A2A 工具

Agent 可以将其他 A2A Agent 作为工具调用。Runtime 的
`OpenJiuwenRemoteToolInstaller` 从 A2A Agent Card 发现远端工具规格，
并将其注册为 openJiuwen `Tool` 实例。

```java
// 当 RemoteSupport Bean 存在时，RuntimeAutoConfiguration 自动注入
handler.setRuntimeToolInstaller(remoteToolInstaller);
```

## 配置参考

```yaml
sample:
  openjiuwen:
    model-provider: openai           # openai | ollama | openai-compatible
    api-key: sk-xxx                  # LLM API key
    api-base: http://localhost:4000/v1
    model-name: gpt-5.4-mini
    ssl-verify: true                 # TLS 证书校验
    checkpointer: in-memory          # in-memory | redis
    redis-url: redis://localhost:6379
```

## 相关

- 示例：`examples/agent-runtime-openjiuwen-simple/`
- 源码：`agent-runtime/src/main/java/.../engine/openjiuwen/`
- SPI：[AgentRuntimeHandler SPI](handler-spi.md)
