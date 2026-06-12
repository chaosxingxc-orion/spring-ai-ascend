# AgentRuntimeHandler SPI

在 agent-runtime 中托管 Agent 的核心 SPI。实现此接口（或继承框架专用基类），
即可通过 A2A JSON-RPC 端点访问你的 Agent。

## 接口定义

```java
package com.huawei.ascend.runtime.engine.spi;

public interface AgentRuntimeHandler {
    String agentId();
    boolean isHealthy();
    Stream<?> doExecute(AgentExecutionContext context, TrajectoryEmitter trajectory);
    StreamAdapter resultAdapter();
    default void start() {}
    default void stop() {}
}
```

## 契约说明

| 方法 | 必选 | 用途 |
|---|---|---|
| `agentId()` | 是 | 稳定唯一标识，必须与 A2A 路由键一致 |
| `isHealthy()` | 是 | 每次执行前和健康检查端点调用 |
| `doExecute()` | 是 | 核心执行。接收执行上下文 + 轨迹发射器，返回 `Stream<?>` 框架原生结果 |
| `resultAdapter()` | 是 | 返回 `StreamAdapter`，将 `doExecute()` 的 `Stream<?>` 映射为 `AgentExecutionResult` 流 |
| `start()` | 否 | 生命周期——runtime 就绪时调用 |
| `stop()` | 否 | 生命周期——关闭时调用 |

## AgentExecutionContext

从 `doExecute()` 获取：

| 方法 | 返回值 | 说明 |
|---|---|---|
| `getScope()` | `ExecutionScope` | tenantId, sessionId, taskId, agentId, userId |
| `getMessages()` | `List<Message>` | 请求中的 A2A 消息列表 |
| `getInputType()` | `String` | 输入模式（如 "text"） |
| `getAgentStateKey()` | `String` | Agent 作用域稳定键（对应 conversation_id） |

## StreamAdapter

```java
@FunctionalInterface
public interface StreamAdapter {
    Stream<AgentExecutionResult> adapt(Stream<?> rawResults);
}
```

将框架原生结果（如 openJiuwen `InteractionOutput`、AgentScope 事件）映射为
框架中立的 `AgentExecutionResult` 对象。Runtime 在 `doExecute()` 之后链式
调用此适配器。

## 实现模式

### 模式 A：继承框架基类（推荐）

```java
public class MyHandler extends OpenJiuwenAgentRuntimeHandler {
    public MyHandler() { super("my-agent-id"); }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext ctx) {
        // 构建并返回你的 openJiuwen Agent
    }
}
```

基类已处理消息转换、流式输出和结果映射。你只需实现 Agent 创建。

### 模式 B：直接实现 SPI

```java
public class MyHandler implements AgentRuntimeHandler, AgentCardProvider {
    @Override public String agentId() { return "my-agent"; }
    @Override public boolean isHealthy() { return true; }

    @Override
    public Stream<?> doExecute(AgentExecutionContext ctx, TrajectoryEmitter t) {
        // 自定义执行逻辑，返回结果的 Stream
    }

    @Override
    public StreamAdapter resultAdapter() {
        return raw -> raw.map(r -> AgentExecutionResult.answer(r.toString()));
    }
}
```

## 生命周期

1. Spring 创建所有 `AgentRuntimeHandler` Bean
2. Runtime 就绪后，`AgentRuntimeLifecycle` 调用每个 Handler 的 `start()`
3. `A2aAgentExecutor` 选取第一个 Handler（按 `@Order` 排序）用于执行
4. 关闭时，每个 Handler 的 `stop()` 被调用

## 健康检查

`AgentRuntimeHealthIndicator` 调用每个已注册 Handler 的 `isHealthy()`。
任意 Handler 报告不健康时，健康端点会反映该状态。

## 相关类型

| 类型 | 包 | 用途 |
|---|---|---|
| `AbstractAgentRuntimeHandler` | `engine.spi` | 基类，内置 agentId + trajectory 支持 |
| `OpenJiuwenAgentRuntimeHandler` | `engine.openjiuwen` | openJiuwen Agent 基类 |
| `AgentExecutionResult` | `engine.spi` | 框架中立的结果（answer/error/interrupt/remoteInvocation） |
| `TrajectoryEmitter` | `engine.spi` | 可观测性：发射 run/tool/model-call 事件 |
| `AgentCardProvider` | `engine.spi` | 可选：提供 A2A AgentCard 元数据 |

## 多 Handler 处理

允许多个 Handler Bean 共存——runtime 使用第一个（按 `@Order` 排序），
并记录警告，忽略其余 Handler。如需服务多个 Agent，每个 Agent 需要
各自的 runtime 实例（独立进程/端口）。
