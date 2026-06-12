# openJiuwen Agent Adapter

How to host an openJiuwen agent (ReActAgent or DeepAgent) inside agent-runtime.

## Quick start (3 steps)

### Step 1 — Extend OpenJiuwenAgentRuntimeHandler

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
        // Step 2 goes here
    }
}
```

### Step 2 — Implement createOpenJiuwenAgent()

```java
@Override
protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
    // 2a. Create openJiuwen AgentCard (NOT the A2A one)
    AgentCard card = AgentCard.builder()
        .id("my-agent-id")
        .name("My Agent")
        .description("...")
        .build();

    // 2b. Create ReActAgent with system prompt and model config
    ReActAgent agent = new ReActAgent(card);
    ReActAgentConfig config = ReActAgentConfig.builder()
        .promptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
        .maxIterations(5)
        .build()
        .configureModelClient("openai", apiKey, apiBase, modelName, true);

    // 2c. Optionally tune model parameters
    config.getModelConfigObj().setTemperature(0.7);
    config.getModelConfigObj().setMaxTokens(1024);

    agent.configure(config);
    return agent;
}
```

### Step 3 — Register as Spring Bean

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

That's the only required bean. The runtime auto-generates the A2A AgentCard.

## Model providers

The `configureModelClient(provider, apiKey, apiBase, modelName, sslVerify)`
method accepts:

| Provider | api-base example |
|---|---|
| `openai` | `https://api.openai.com/v1` |
| `ollama` | `http://localhost:11434/v1` |
| `openai-compatible` | `http://localhost:4000/v1` (litellm proxy) |

## Agent types

### ReActAgent

```java
ReActAgent agent = new ReActAgent(card);
ReActAgentConfig config = ReActAgentConfig.builder()
    .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
    .maxIterations(5)          // max ReAct loop iterations
    .build()
    .configureModelClient(...);
agent.configure(config);
```

### DeepAgent

```java
DeepAgent agent = new DeepAgent(card);
// DeepAgent uses its own configuration builder
agent.configure(deepAgentConfig);
```

## Memory integration

openJiuwen agents can use the runtime's `MemoryProvider` SPI for
conversation memory.

### ReActAgent (memory rail)

```java
@Override
protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
    return List.of(memoryRuntimeRail(context, memoryProvider));
}
```

### DeepAgent (external memory rail)

```java
@Override
protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
    return List.of(openJiuwenExternalMemoryRail(context, memoryProvider));
}
```

See [Memory Integration](memory-integration.md) for details.

## Session persistence (checkpointer)

openJiuwen checkpointer persists agent session state:

```java
@Bean
Checkpointer checkpointer() {
    Checkpointer cp = new InMemoryCheckpointer();
    CheckpointerFactory.setDefaultCheckpointer(cp);
    return cp;
}
```

For Redis-backed persistence:

```java
Checkpointer cp = new RedisCheckpointer.Provider()
    .create(Map.of("connection", Map.of("url", "redis://localhost:6379")));
```

## Remote A2A tools

Agents can call other A2A agents as tools. The runtime's
`OpenJiuwenRemoteToolInstaller` discovers remote tool specs from A2A agent
cards and registers them as openJiuwen `Tool` instances.

```java
// Injected automatically by RuntimeAutoConfiguration when a
// RemoteSupport bean is present.
handler.setRuntimeToolInstaller(remoteToolInstaller);
```

## Configuration reference

```yaml
sample:
  openjiuwen:
    model-provider: openai           # openai | ollama | openai-compatible
    api-key: sk-xxx                  # LLM API key
    api-base: http://localhost:4000/v1
    model-name: gpt-5.4-mini
    ssl-verify: true                 # TLS certificate verification
    checkpointer: in-memory          # in-memory | redis
    redis-url: redis://localhost:6379
```

## Related

- Example: `examples/agent-runtime-openjiuwen-simple/`
- Source: `agent-runtime/src/main/java/.../engine/openjiuwen/`
- SPI: [AgentRuntimeHandler SPI](handler-spi.md)
