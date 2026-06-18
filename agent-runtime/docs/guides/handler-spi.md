# Adapter 开发

在 agent-runtime 中托管 Agent 的核心方式是实现 `AgentRuntimeHandler` SPI。实现此接口（或继承框架专用基类），即可通过 A2A JSON-RPC 端点访问你的 Agent。

## 1. 概述

```java
// 最小示例：继承 OpenJiuwen 基类，三步挂载
public class MyHandler extends OpenJiuwenAgentRuntimeHandler {
    public MyHandler() { super("my-agent-id"); }
    @Override protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext ctx) {
        // 构建并返回 Agent
    }
}
```

## 2. 快速开始

### 模式 A：继承框架基类（推荐）

```java
public class MyHandler extends OpenJiuwenAgentRuntimeHandler {
    public MyHandler() { super("my-agent-id"); }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext ctx) {
        ReActAgent agent = new ReActAgent(AgentCard.builder().id("my-agent-id").build());
        ReActAgentConfig config = ReActAgentConfig.builder()
            .promptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
            .maxIterations(5).build()
            .configureModelClient("openai", apiKey, apiBase, modelName, true);
        agent.configure(config);
        return agent;
    }
}

// 注册为 Spring Bean
@Bean OpenJiuwenAgentRuntimeHandler myHandler() { return new MyHandler(); }
```

基类已处理消息转换、流式输出和结果映射。你只需实现 Agent 创建。

### 模式 B：直接实现 SPI

```java
public class MyHandler implements AgentRuntimeHandler, AgentCardProvider {
    @Override public String agentId() { return "my-agent"; }
    @Override public boolean isHealthy() { return true; }

    @Override
    public Stream<?> execute(AgentExecutionContext ctx) {
        // 自定义执行逻辑，返回结果的 Stream
    }

    @Override
    public StreamAdapter resultAdapter() {
        return raw -> raw.map(r -> AgentExecutionResult.answer(r.toString()));
    }
}
```

## 3. 工作原理

```
A2A 客户端
    │
    ▼
┌─────────────────────────────┐
│ A2aJsonRpcController        │  JSON-RPC 解析, SSE 传输
├─────────────────────────────┤
│ A2aAgentExecutor            │  任务生命周期, 调度
├─────────────────────────────┤
│ AgentRuntimeHandler (SPI)   │  统一执行契约
├──────────┬──────────┬───────┤
│ OpenJiuwen│AgentScope│Versatile│  适配器层（框架相关）
└──────────┴──────────┴───────┘
```

每个适配器实现了 `AgentRuntimeHandler`（或继承框架基类）。Runtime 只看到这个接口——适配器内部细节对上层透明。

## 4. 核心接口

```java
public interface AgentRuntimeHandler {
    String agentId();                              // 稳定唯一标识，必须与 A2A 路由键一致
    boolean isHealthy();                           // 健康检查闸门
    Stream<?> execute(AgentExecutionContext ctx);  // 核心执行，返回框架原生结果 Stream
    StreamAdapter resultAdapter();                 // 将原始结果映射为 AgentExecutionResult 流
    default void start() {}                        // 生命周期——runtime 就绪时调用
    default void stop() {}                         // 生命周期——关闭时调用
    default void cancel(String taskId) {}          // 取消执行（OpenJiuwen 仅阻止消费）
}
```

### AgentExecutionContext

`execute()` 方法的输入，携带：

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `getScope()` | `RuntimeIdentity` | tenantId, sessionId, taskId, agentId, userId |
| `getMessages()` | `List<RuntimeMessage>` | 会话消息列表（`role` / `text` / `metadata`） |
| `getInputType()` | `String` | 输入模式（如 `"text"` 或 `"INPUT_TYPE_REMOTE_RESUME"`） |
| `getAgentStateKey()` | `String` | Agent 作用域稳定键（对应 conversation_id） |

### StreamAdapter

```java
@FunctionalInterface
public interface StreamAdapter {
    Stream<AgentExecutionResult> adapt(Stream<?> rawResults);
}
```

将框架原生结果映射为框架中立的 `AgentExecutionResult`。Runtime 在 `execute()` 之后链式调用此适配器。

### AgentExecutionResult

统一四种结果类型：

| Type | 含义 | A2A 映射 |
|------|------|---------|
| `OUTPUT` | 增量输出 | → A2A Artifact |
| `COMPLETED` | 执行完成 | → emitter.complete() |
| `FAILED` | 执行失败 | → emitter.fail() + 结构化错误 |
| `INTERRUPTED` | 中断等待输入 | → Task → INPUT_REQUIRED |

## 5. 能力详述

### 生命周期

1. Spring 创建所有 `AgentRuntimeHandler` Bean
2. Runtime 就绪后，`AgentRuntimeLifecycle` 调用每个 Handler 的 `start()`
3. `A2aAgentExecutor` 选取第一个 Handler（按 `@Order` 排序）用于执行
4. 关闭时，每个 Handler 的 `stop()` 被调用

### 健康检查

`AgentRuntimeHealthIndicator` 调用每个已注册 Handler 的 `isHealthy()`。任意 Handler 不健康时，健康端点会反映该状态。

### 多 Handler 处理

允许多个 Handler Bean 共存——runtime 使用第一个（按 `@Order` 排序），并记录警告，忽略其余 Handler。如需服务多个 Agent，每个 Agent 需要各自的 runtime 实例（独立进程/端口）。

### 新增适配器

为新的 Agent 框架 F 添加适配器的步骤：

1. 在 `agent-runtime` 下创建包 `engine.f`
2. 实现 `AgentRuntimeHandler`（或继承 `AbstractAgentRuntimeHandler`）
3. 实现 `StreamAdapter` 将框架原生结果映射为 `AgentExecutionResult`
4. 可选：实现 `AgentCardProvider` 提供 A2A 卡片元数据
5. 在 `examples/` 下编写示例模块展示适配器用法

Runtime 核心无需修改——通过 Spring `ObjectProvider<AgentRuntimeHandler>` 自动发现新的 Handler。

## 6. 选型指南

| 场景 | 适配器 |
|------|--------|
| 已有 openJiuwen ReActAgent | OpenJiuwen |
| 已有 AgentScope SDK 或 Harness Agent | AgentScope |
| Agent 用 Python / Node.js / Go 编写 | Versatile（REST 代理） |
| Agent 运行在独立进程或容器中 | Versatile（REST 代理） |
| 需要最低延迟 | OpenJiuwen 或 AgentScope |
| 需要将已有 HTTP API 包装为 Agent | Versatile（REST 代理） |

## 7. 限制

- 仅第一个 Handler（按 `@Order`）被选取，一个 runtime 实例只能服务一个 Agent
- OpenJiuwen 通过 streaming runner 执行；cancel 会停止 runtime 继续消费结果，底层 LLM 是否中断取决于 OpenJiuwen/模型客户端
- DeepAgent 不支持（类不继承 BaseAgent）

## 8. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/heterogeneous-agent-framework-compatibility.md` §2.3, §4
- [OpenJiuwen Adapter](openjiuwen-adapter.md)
- [AgentScope Adapter](agentscope-adapter.md)
- [Versatile Adapter](versatile-adapter.md)
- Example：`examples/agent-runtime-openjiuwen-simple/`
