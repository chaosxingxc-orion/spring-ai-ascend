# OpenJiuwen Adapter

在 agent-runtime 中托管 OpenJiuwen ReActAgent，三步即可通过 A2A 端点访问。

## 1. 概述

```java
// 最小示例
public class MyHandler extends OpenJiuwenAgentRuntimeHandler {
    public MyHandler() { super("my-agent-id"); }
    @Override protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext ctx) {
        // 构建并返回 ReActAgent
    }
}
@Bean OpenJiuwenAgentRuntimeHandler myHandler() { return new MyHandler(); }
```

## 2. 快速开始

### 第一步 — 继承 Handler

```java
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.singleagent.BaseAgent;

public class MyHandler extends OpenJiuwenAgentRuntimeHandler {
    public MyHandler() { super("my-agent-id"); }

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
    AgentCard card = AgentCard.builder().id("my-agent-id").name("My Agent").build();
    ReActAgent agent = new ReActAgent(card);
    ReActAgentConfig config = ReActAgentConfig.builder()
        .promptTemplate(List.of(Map.of("role", "system", "content", "你是一个有用的助手。")))
        .maxIterations(5).build()
        .configureModelClient("openai", apiKey, apiBase, modelName, true);
    agent.configure(config);
    return agent;
}
```

### 第三步 — 注册为 Spring Bean

```java
@Configuration(proxyBeanMethods = false)
public class MyConfiguration {
    @Bean OpenJiuwenAgentRuntimeHandler myHandler() { return new MyHandler(); }
}
```

只需注册这一个 Bean。Runtime 自动从 `agentId` 生成 A2A AgentCard。

## 3. 工作原理

```
A2A 请求 → A2aAgentExecutor
  │
  ├─ OpenJiuwenMessageAdapter: AgentExecutionContext → query + conversation_id
  ├─ createOpenJiuwenAgent(): 子类构建 Agent
  ├─ Rails 注入:
  │   ├─ MemoryRuntimeRail（记忆注入）
  │   ├─ OpenJiuwenTrajectoryRail（轨迹事件）
  │   └─ OpenJiuwenRemoteAgentInterruptRail（远程工具中断）
  ├─ Runner.runAgentStreaming(agent, input, conversationId, null, List.of(StreamMode.OUTPUT)) — streaming 执行
  └─ OpenJiuwenStreamAdapter: OutputSchema chunk → AgentExecutionResult
```

## 4. 核心接口

```java
public abstract class OpenJiuwenAgentRuntimeHandler extends AbstractAgentRuntimeHandler {
    /** 子类必须实现：构建并返回 openJiuwen Agent。 */
    protected abstract BaseAgent createOpenJiuwenAgent(AgentExecutionContext context);

    /** 按需覆盖：注入 Rails。 */
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) { return List.of(); }
}
```

| 方法 | 用途 |
|------|------|
| `createOpenJiuwenAgent` | 构建 Agent 实例（必实现） |
| `openJiuwenRails` | 注入轨迹/记忆/远程工具 Rail |
| `openJiuwenConversationId` | 返回稳定 conversation_id（= agentStateKey） |
| `toOpenJiuwenInput` | AgentExecutionContext → openJiuwen 输入 |

## 5. 能力详述

### 模型提供商

`configureModelClient(provider, apiKey, apiBase, modelName, sslVerify)` 支持：

| provider | api-base 示例 |
|----------|-------------|
| `openai` | `https://api.openai.com/v1` |
| `ollama` | `http://localhost:11434/v1` |
| `openai-compatible` | `http://localhost:4000/v1`（litellm 代理） |

### Rails 注入

覆盖 `openJiuwenRails()` 注入扩展：

```java
@Override protected List<AgentRail> openJiuwenRails(AgentExecutionContext ctx) {
    return List.of(
        memoryRuntimeRail(ctx, memoryProvider),          // 记忆注入
        openJiuwenExternalMemoryRail(ctx, memoryProvider) // Harness 兼容
    );
}
```

三种内置 Rail：

| Rail | 用途 | 文档 |
|------|------|------|
| `memoryRuntimeRail` | ReActAgent 记忆注入 | [Memory 服务](memory-services.md) |
| `openJiuwenExternalMemoryRail` | Harness 兼容记忆 | [Memory 服务](memory-services.md) |
| 自动注册的 TrajectoryRail | 轨迹事件 | [轨迹可观测性](trajectory-observability.md) |
| 自动注册的 InterruptRail | 远程工具中断 | [远程调用](remote-invocation.md) |

### Agent 类型

**ReActAgent**（完整支持）：

```java
ReActAgent agent = new ReActAgent(card);
ReActAgentConfig config = ReActAgentConfig.builder()
    .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
    .maxIterations(5).build()
    .configureModelClient(...);
agent.configure(config);
```

> DeepAgent 当前不完善（类不继承 BaseAgent），不推荐生产使用。

### 会话持久化

```java
@Bean Checkpointer checkpointer() {
    Checkpointer cp = new InMemoryCheckpointer();
    CheckpointerFactory.setDefaultCheckpointer(cp);
    return cp;
}
```

详见 [State 持久化](state-persistence.md)。

### 远程 A2A 工具

当 `agent-runtime.remote-agents[0].url` 配置存在时，`OpenJiuwenRemoteToolInstaller` 自动发现远端 Agent Card 的 skills，注册为当前 Agent 的 Tool。LLM 调用时走中断-续接流水线。

## 6. 完整示例

```java
@Configuration(proxyBeanMethods = false)
public class AgentConfig {
    @Value("${sample.openjiuwen.api-key}") private String apiKey;
    @Value("${sample.openjiuwen.api-base}") private String apiBase;
    @Value("${sample.openjiuwen.model-name}") private String modelName;

    @Bean OpenJiuwenAgentRuntimeHandler myHandler() {
        return new OpenJiuwenAgentRuntimeHandler("my-agent") {
            @Override protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext ctx) {
                AgentCard card = AgentCard.builder().id("my-agent").name("My Agent").build();
                ReActAgent agent = new ReActAgent(card);
                agent.configure(ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role","system","content","You are helpful.")))
                    .maxIterations(5).build()
                    .configureModelClient("openai", apiKey, apiBase, modelName, true));
                return agent;
            }
        };
    }
}
```

预期结果：启动后 `GET /.well-known/agent-card.json` 返回卡片，`POST /a2a SendStreamingMessage` 可调用。

## 7. 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `sample.openjiuwen.model-provider` | String | `openai` | 模型提供商（必配） |
| `sample.openjiuwen.api-key` | String | — | LLM API Key（必配） |
| `sample.openjiuwen.api-base` | String | — | LLM API 地址（必配） |
| `sample.openjiuwen.model-name` | String | `gpt-5.4-mini` | 模型名称 |
| `sample.openjiuwen.ssl-verify` | boolean | `true` | TLS 证书校验 |
| `sample.openjiuwen.checkpointer` | String | `in-memory` | checkpoint 后端 |

## 8. 限制

| 限制 | 影响 | 替代 |
|------|------|------|
| cancel 仅阻止结果消费，不中断 LLM 调用 | 长时间 LLM 调用无法真正取消 | 使用 AgentScope 或 Versatile Adapter |
| 仅支持 ReActAgent，不支持 Workflow | 多步工作流不可用 | 使用 Versatile 代理 Workflow 引擎 |
| DeepAgent 不完善 | 不可用于生产 | — |
| streaming 执行依赖底层模型客户端取消能力 | cancel 后底层 LLM 是否立即停止取决于 OpenJiuwen/模型客户端 | — |

## 9. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/heterogeneous-agent-framework-compatibility.md` §4.2
- [Adapter 开发](handler-spi.md)
- [Memory 服务](memory-services.md)
- [State 持久化](state-persistence.md)
- [远程调用](remote-invocation.md)
- Example：`examples/agent-runtime-openjiuwen-simple/`
