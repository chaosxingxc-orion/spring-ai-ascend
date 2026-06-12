# AgentRuntimeHandler SPI

The core SPI for hosting an agent inside agent-runtime. Implement this interface
(or extend a framework-specific base class) to make your agent routable through
the A2A JSON-RPC endpoint.

## Interface

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

## Contract

| Method | Required | Purpose |
|---|---|---|
| `agentId()` | yes | Stable unique identifier. Must match the A2A routing key. |
| `isHealthy()` | yes | Health gate called before every execution and by the health indicator. |
| `doExecute()` | yes | Core execution. Receives context + trajectory emitter; returns a `Stream<?>` of framework-native results. |
| `resultAdapter()` | yes | Returns a `StreamAdapter` that maps the `Stream<?>` from `doExecute()` to `AgentExecutionResult` stream. |
| `start()` | no | Lifecycle hook — called when the runtime is ready. |
| `stop()` | no | Lifecycle hook — called on shutdown. |

## AgentExecutionContext

Available from `doExecute()`:

| Method | Returns | Description |
|---|---|---|
| `getScope()` | `ExecutionScope` | tenantId, sessionId, taskId, agentId, userId |
| `getMessages()` | `List<Message>` | A2A messages from the request |
| `getInputType()` | `String` | Input mode (e.g. "text") |
| `getAgentStateKey()` | `String` | Stable key for agent-scoped state (conversation_id) |

## StreamAdapter

```java
@FunctionalInterface
public interface StreamAdapter {
    Stream<AgentExecutionResult> adapt(Stream<?> rawResults);
}
```

Maps framework-native results (e.g. openJiuwen `InteractionOutput`, AgentScope
events) to framework-neutral `AgentExecutionResult` objects. The runtime chains
this adapter after `doExecute()`.

## Implementation patterns

### Pattern A: extend a framework base class (recommended)

```java
public class MyHandler extends OpenJiuwenAgentRuntimeHandler {
    public MyHandler() { super("my-agent-id"); }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext ctx) {
        // build and return your openJiuwen agent
    }
}
```

The base class handles message conversion, streaming, and result mapping.
You only implement agent creation.

### Pattern B: implement the SPI directly

```java
public class MyHandler implements AgentRuntimeHandler, AgentCardProvider {
    @Override public String agentId() { return "my-agent"; }
    @Override public boolean isHealthy() { return true; }

    @Override
    public Stream<?> doExecute(AgentExecutionContext ctx, TrajectoryEmitter t) {
        // custom execution logic, return Stream of results
    }

    @Override
    public StreamAdapter resultAdapter() {
        return raw -> raw.map(r -> AgentExecutionResult.answer(r.toString()));
    }
}
```

## Lifecycle

1. Spring creates all `AgentRuntimeHandler` beans
2. `AgentRuntimeLifecycle` calls `start()` on each when the runtime is ready
3. `A2aAgentExecutor` picks the first handler (by `@Order`) for execution
4. On shutdown, `stop()` is called on each handler

## Health

The `AgentRuntimeHealthIndicator` calls `isHealthy()` on every registered
handler. If any handler reports unhealthy, the health endpoint reflects it.

## Related types

| Type | Package | Purpose |
|---|---|---|
| `AbstractAgentRuntimeHandler` | `engine.spi` | Base class with agentId + trajectory support |
| `OpenJiuwenAgentRuntimeHandler` | `engine.openjiuwen` | Base class for openJiuwen agents |
| `AgentExecutionResult` | `engine.spi` | Framework-neutral result (answer/error/interrupt/remoteInvocation) |
| `TrajectoryEmitter` | `engine.spi` | Observability: emit run/tool/model-call events |
| `AgentCardProvider` | `engine.spi` | Optional: supply A2A AgentCard metadata |
```

## Discovery

Multiple handler beans are tolerated — the runtime uses the first one (by
`@Order`) and logs a warning about ignored handlers. To serve multiple agents,
each needs its own runtime instance (separate process / port).
