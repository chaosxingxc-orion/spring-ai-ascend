# AgentScope Adapter

在 agent-runtime 中托管 AgentScope Agent，支持本地 Agent、Harness Agent 和远程 SSE 客户端三种模式。

## 1. 概述

```java
// 最小示例：本地 Agent
AgentScopeAgent myAgent = invocation -> /* 返回 Stream<AgentScopeEvent> */;
AgentScopeAgentRuntimeHandler handler = new AgentScopeAgentRuntimeHandler("my-agent", myAgent);
```

## 2. 快速开始

### 本地 Agent 模式

```java
import com.huawei.ascend.runtime.engine.agentscope.*;

// 实现 AgentScopeAgent
AgentScopeAgent myAgent = invocation -> {
    // 处理消息，返回 Stream<AgentScopeEvent>
    return Stream.of(new AgentScopeEvent(AgentScopeEvent.Type.OUTPUT, "Hello"));
};

// 注册 Handler
@Bean AgentScopeAgentRuntimeHandler handler() {
    return new AgentScopeAgentRuntimeHandler("my-agent", myAgent);
}
```

### 远程 SSE 客户端模式

```java
// 配置
AgentScopeRuntimeClientProperties props = new AgentScopeRuntimeClientProperties();
props.setEndpoint("http://localhost:9000/agentscope");

// 创建 Handler
@Bean AgentScopeRuntimeClientHandler handler(AgentScopeRuntimeClientProperties props) {
    return new AgentScopeRuntimeClientHandler("my-remote-agent",
        new AgentScopeRuntimeClient(props));
}
```

## 3. 工作原理

```
本地 Agent:    AgentScopeAgent.streamEvents(invocation) → Stream<AgentScopeEvent>
Harness Agent: AgentScopeHarnessAgent.streamEvents(invocation) → Stream<AgentScopeEvent>
远程客户端:    HTTP POST → SSE 帧 → SseEventDecoder → Stream<AgentScopeEvent>
                      ↑
                AgentScopeRuntimeClient
```

所有模式最终产出的 `AgentScopeEvent` 流由 `AgentScopeStreamAdapter` 统一转换为 `AgentExecutionResult`。

## 4. 核心接口

```java
@FunctionalInterface
public interface AgentScopeAgent {
    Stream<AgentScopeEvent> streamEvents(AgentScopeInvocation invocation);
}
```

| Handler 类 | 适用场景 |
|-----------|---------|
| `AgentScopeAgentRuntimeHandler` | 进程内直接调用 AgentScope SDK Agent |
| `AgentScopeHarnessRuntimeHandler` | 测试/评估场景下的受控运行 |
| `AgentScopeRuntimeClientHandler` | 连接远程 AgentScope Runtime 实例 |

## 5. 能力详述

### 错误码映射

`AgentScopeStreamAdapter` 将 AgentScope 原生错误码自动映射到 `RuntimeErrorCode` 分类体系。映射通过 walk-the-cause-chain 匹配已知异常类型（超时 → TIMEOUT、不可达 → UPSTREAM_UNAVAILABLE），未知错误归为 INTERNAL。

### 轨迹事件

支持的事件类型：RUN_START/END、TOOL_CALL_START/END、ERROR、PROGRESS。比 OpenJiuwen 多 PROGRESS（AgentScope 原生产出增量事件），少 MODEL_CALL（AgentScope 不暴露模型调用回调）。

## 6. 完整示例

```java
@Configuration(proxyBeanMethods = false)
public class AgentScopeConfig {

    @Bean AgentScopeAgent myAgent() {
        return invocation -> {
            String text = invocation.getMessages().get(0).getText();
            return Stream.of(new AgentScopeEvent(AgentScopeEvent.Type.OUTPUT,
                "Echo: " + text));
        };
    }

    @Bean AgentScopeAgentRuntimeHandler handler(AgentScopeAgent agent) {
        return new AgentScopeAgentRuntimeHandler("my-agentscope-agent", agent);
    }
}
```

前置条件：AgentScope SDK 在 classpath。预期结果：Agent 按 `AgentScopeEvent` 流式产出，runtime 自动转为 A2A SSE 事件。

## 7. 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `agentscope.client.endpoint` | String | — | 远程 AgentScope Runtime URL（远程模式必配） |
| `agentscope.client.connect-timeout` | Duration | — | 连接超时 |
| `agentscope.client.read-timeout` | Duration | — | 读取超时 |

## 8. 限制

| 限制 | 影响 |
|------|------|
| 不支持 Checkpoint 持久化 | 状态不保存 |
| 不支持 Memory 记忆注入 | 无跨会话记忆 |
| 仅支持 Core Agent | Workflow 不可用 |

## 9. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/heterogeneous-agent-framework-compatibility.md` §4.3
- [Adapter 开发](handler-spi.md)
- [OpenJiuwen Adapter](openjiuwen-adapter.md)
- [Versatile Adapter](versatile-adapter.md)
