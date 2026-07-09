---
level: L2-LLD
module: agent-runtime-ext-java
feature_type: functional
feature_id: Feat-Func-003
status: draft
dependency:
  - openJiuwen/agent-runtime-java/service/agent-service-spec
  - AgentFramework/agentscope-java
---

# AgentScope Java Adapter 设计文档

> 目标模块：`common/agent-runtime-ext-java/agent-service-adapters/agent-service-adapters-agentscope`
> 最后更新：2026-07-09

---

## 1. 概述

### 1.1 特性定位

本特性为 `agent-runtime-java` 的扩展仓 `agent-runtime-ext-java` 增加 AgentScope Java 本地适配层。适配层把 OpenJiuwen runtime 的 `AgentHandler` SPI 桥接到 AgentScope Java 的进程内强类型 API，使宿主应用可以通过同一个 runtime 服务入口调用 AgentScope Java agent。

本特性只做 **本地进程内 Java 适配**：宿主应用在同一个 JVM 内构建好 `ReActAgent` 或 `HarnessAgent`，adapter 负责请求、响应、流式事件、中断输出以及同会话续轮输入透传。

### 1.2 问题与目标

OpenJiuwen runtime 对外只认识 `AgentHandler`、`ServeRequest`、`QueryResponse`、`QueryChunk`，而 AgentScope Java 本地 API 使用 `RuntimeContext`、`Msg`、`Mono<Msg>`、`Flux<AgentEvent>`。本特性目标是在不改变 runtime SPI 的前提下，把业务已经构建好的 AgentScope `ReActAgent` / `HarnessAgent` 暴露为 OpenJiuwen runtime 可调用的 `AgentHandler`。

首版目标：

- `query()` / `streamQuery()` 调用 AgentScope Java 本地 API。
- 将 runtime 请求映射为 AgentScope `RuntimeContext` 和 `Msg`。
- 将 AgentScope `Msg` / `AgentEvent` 映射为 runtime `QueryResponse` / `QueryChunk`。
- 将同一个 `conversationId` 稳定映射到同一个 AgentScope `sessionId`，续轮输入按普通 `Msg` 透传。

### 1.3 当前事实边界

已核验的当前代码事实：

1. `AgentHandler` SPI 只有 `query`、`streamQuery`、`start`、`stop`、`clearSession(String conversationId)`。
2. 本模块不提供运行时 handler 选择器，不把配置项解释为自动激活 `AgentHandler` 的开关。
3. 首版不引入 adapter 专属业务配置项；宿主应用只需显式声明 `AgentHandler` bean。
4. AgentScope Java `ReActAgent` 单实例不是线程安全的；如果 adapter 直接包装同一个 `ReActAgent` bean，需要避免发起 AgentScope 明确不支持的重入调用。
5. AgentScope Java `HarnessAgent` 的状态、并发和资源语义由 AgentScope 自身负责，adapter 只做调用桥接。
6. AgentScope Java `ReActAgent` 内部按 `(userId, sessionId)` 维护 agent state；同一个 `sessionId` 是找回 pending 状态的必要条件。
7. AgentScope Java 部分确认/外部执行场景可能需要框架原生恢复对象。首版 adapter 不构造这些对象，只做同 session 普通消息透传；如果 AgentScope 自身不能用普通消息继续推进，则按运行时错误处理。

---

## 2. 特性规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| 新增 Maven 模块 | 必须 | `agent-service-adapters-agentscope` |
| `AgentHandler.query` | 必须 | 调用 AgentScope `call(...)`，返回 `QueryResponse` |
| `AgentHandler.streamQuery` | 必须 | 调用 AgentScope `streamEvents(...)`，输出 `QueryChunk` |
| `ServeRequest -> RuntimeContext` | 必须 | 映射 conversationId/userId/tenantId/spaceId |
| `ServeRequest.messages -> List<Msg>` | 必须 | 支持文本消息、常见 role、同会话续轮输入透传 |
| `Msg -> QueryResponse.result` | 必须 | 默认 `{role:"assistant", content:"..."}` |
| `AgentEvent -> QueryChunk` | 必须 | text delta、interrupt、error 映射 |
| `ReActAgent` 适配 | 必须 | 本地进程内调用 `ReActAgent.call/streamEvents`；直接包装单实例时做最小调用保护 |
| `HarnessAgent` 适配 | 必须 | 本地进程内调用 `HarnessAgent.call/streamEvents` |
| Spring Boot properties | 排除首版 | 首版不引入 adapter 专属业务配置项 |
| 中断输出 | 必须 | AgentScope 暂停/中断事件映射为 `QueryChunk.TYPE_INTERRUPT` |
| 续轮输入透传 | 必须 | 下一次 `query` / `streamQuery` 使用同一 `conversationId/sessionId`，把用户新输入作为普通 `Msg` 交给 AgentScope |
| AgentScope 原生恢复转换 | 排除首版 | 不在首版自动构造 AgentScope 原生恢复对象 |
| 多模态完整映射 | 排除首版 | 首版文本优先 |
| 自动构建 AgentScope agent | 排除首版 | model/toolkit/workspace/sandbox 配置面过大 |
| 自动注册 `AgentHandler` | 排除首版 | 避免模块被引入后自动接管服务入口 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| `openjiuwen.service.handler=agentscope` 作为激活开关 | 本模块不提供 handler 选择器，配置项不承担自动装配语义 | 业务显式 `@Bean AgentHandler` |
| adapter 专属业务配置项 | 首版行为固定，配置会扩大使用和测试面 | 代码内固定默认策略，后续确有差异化需求再加配置 |
| 自动构建 `ReActAgent` | model/toolkit/state/tool 配置面太大，超出适配层职责 | 宿主应用自行构建 agent bean |
| 依赖 `Agent.stream(...)` | `StreamableAgent.stream(...)` 已 deprecated，事件模型粗糙 | 使用 `ReActAgent/HarnessAgent.streamEvents(...)` |
| 自动构造 AgentScope 原生恢复对象 | 当前 runtime 没有通用 resume DTO，A2A 入口也只透传文本；适配层不应猜测用户确认或工具结果 | 首版只做同 session 续轮透传；原生恢复转换后续按明确协议增强 |
| AgentScope 自启动 HTTP 服务 | AgentScope Java core/harness 本身不是 runtime server | 由宿主 Spring Boot 应用暴露 OpenJiuwen runtime 服务 |

## 3. 核心实现

### 3.1 为什么不是统一适配 `Agent` 接口

`AgentScopeAgentHandler` 对 runtime 只暴露 `AgentHandler`。handler 内部统一委托给 `AgentScopeAdapterInvoker`，但该接口是模块内部实现细节，首版只由 adapter 内置的两个实现创建：

1. `ReActAgentScopeInvoker`：包装业务已经构建好的 `ReActAgent`。
2. `HarnessAgentScopeInvoker`：包装业务已经构建好的 `HarnessAgent`。

不能只适配 `io.agentscope.core.agent.Agent` 的原因是：`Agent` 继承的统一流式接口是 deprecated 的 `StreamableAgent.stream(...)`，返回旧的粗粒度事件。首版需要 AgentScope Java v2 细粒度事件：

```java
ReActAgent.streamEvents(List<Msg>, RuntimeContext)
HarnessAgent.streamEvents(List<Msg>, RuntimeContext)
```

这两个方法在具体类上，不在 `Agent` 接口上。若强行统一到 `Agent`，只能选择：

1. 使用 deprecated stream API，丢失细粒度 `AgentEvent`。
2. 使用反射调用 `streamEvents(...)`，丢失编译期安全和版本可控性。
3. 只支持 `call(...)` 非流式，无法满足 runtime streaming 和中断事件输出。

因此设计是 **一个 handler + 一个内部 adapter invoker 抽象 + 两个内置 invoker 实现**。用户装配时只传入已构建好的 `ReActAgent` 或 `HarnessAgent`，不直接感知 `AgentScopeAdapterInvoker`。

### 3.2 两类 AgentScope agent 差异

| 来源 | 定位 | 适配策略 |
|------|------|----------|
| `ReActAgent` | AgentScope core 基础 ReAct 实现 | adapter 直接调用 `call/streamEvents`；直接包装单实例时做最小调用保护 |
| `HarnessAgent` | 带 workspace/sandbox/skills/memory/subagent/plan mode 的增强运行时 | adapter 直接调用 `call/streamEvents`；状态和并发语义由 AgentScope 自身负责 |

两类 agent 不能压成一种实现的关键原因：

- `ReActAgent` 和 `HarnessAgent` 的公共 `Agent` 接口不足以表达细粒度 streaming。
- `ReActAgent` 单实例存在重入风险，需要 adapter 明确直接包装时的调用边界；限制来源于 `ReActAgent` 源码 thread-safety 注释：单实例一次只处理一个 `call()`，同实例并发调用会抛 `IllegalStateException`。
- `HarnessAgent` 的运行模型、依赖对象和并发语义不同于 `ReActAgent`，需要独立 invoker 明确边界。

### 3.3 ReActAgent 调用边界

`ReActAgent` 单实例不支持重入调用。这个限制来自 AgentScope Java `ReActAgent` 源码自身的 thread-safety 契约：单个实例一次只处理一个 `call()`，同一实例的并发调用会抛 `IllegalStateException`。adapter 不是通用并发治理层，但在直接包装同一个 `ReActAgent` bean 时，必须避免把两个 runtime 请求同时打到该实例上。

`ReActAgentScopeInvoker` 的职责边界：

- 可以用轻量互斥保护同一个被包装实例，保证同一实例一次只执行一个 `call(...)` 或 `streamEvents(...)`。
- 结束、错误或取消时必须释放保护，避免后续请求永久阻塞。
- 不负责线程池、排队策略、租户隔离、对象池、限流或调度公平性。

如果宿主应用需要更高并发，应由 runtime 部署形态、业务声明的 agent bean 或 AgentScope 实例构建方式处理。adapter 只遵守被包装实例的合法调用边界。

### 3.4 HarnessAgent 策略

`HarnessAgent` 是首版必须支持的本地 AgentScope agent 类型。它面向更完整的运行时能力，通常包含 workspace、sandbox、skills、memory、subagent、plan mode 等能力；adapter 不构建这些对象，只负责把已经由宿主应用构建好的 `HarnessAgent` 暴露为 OpenJiuwen runtime 的 `AgentHandler`。

首版提供 `HarnessAgentScopeInvoker`：

- `call(...)` 直接调用 `HarnessAgent.call(messages, context)`。
- `streamEvents(...)` 直接调用 `HarnessAgent.streamEvents(messages, context)`。
- 不使用 `ReActAgentScopeInvoker` 的单实例重入保护；以 `HarnessAgent` 自身的状态和调用语义为准。
`HarnessAgentScopeInvoker` 与 `ReActAgentScopeInvoker` 同属本地强类型适配，不通过字符串事件协议或网络协议中转。

---

## 4. 代码结构

### 4.1 包结构

```text
agent-service-adapters-agentscope/
├─ pom.xml
├─ src/main/java/com/openjiuwen/service/adapters/agentscope/
│  ├─ agentfw/
│  │  ├─ AgentScopeAgentHandler.java
│  │  ├─ AgentScopeMessageMapper.java
│  │  ├─ AgentScopeRuntimeContextMapper.java
│  │  └─ AgentScopeEventMapper.java
│  └─ autoconfigure/
│     └─ AgentScopeAutoConfiguration.java
└─ src/main/resources/META-INF/spring/
   └─ org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 4.2 内部调用抽象

`AgentScopeAdapterInvoker` 是 `AgentScopeAgentHandler` 内部接口，不需要拆成独立 Java 文件。首版只有两个实现，建议作为 `AgentScopeAgentHandler` 的 `private static` 内部类：

- `ReActAgentScopeInvoker`：包装 `ReActAgent`，负责单实例互斥保护。
- `HarnessAgentScopeInvoker`：包装 `HarnessAgent`，直接桥接 `call/streamEvents`。

这样用户侧只看到 `AgentScopeAgentHandler.forReActAgent(...)` / `forHarnessAgent(...)`，实现侧也避免为内部策略创建过多顶层类。

执行链路：

```text
ServeRequest
  -> AgentScopeRuntimeContextMapper
  -> AgentScopeMessageMapper
  -> AgentScopeAgentHandler 内部 AgentScopeAdapterInvoker
  -> AgentScopeEventMapper / response mapper
  -> QueryResponse / QueryChunk
```

`AgentScopeMessageMapper` 只负责把 runtime 消息转换为 AgentScope `Msg`。首版不实现独立的 AgentScope 原生恢复转换器，也不保存 pending 恢复上下文。

---

## 5. 请求与响应映射

### 5.1 RuntimeContext 映射

| `ServeRequest` 字段 | AgentScope `RuntimeContext` |
|---------------------|-----------------------------|
| `conversationId` | `sessionId` |
| `userId` | `userId` |
| `tenantId` | string attribute `tenantId` |
| `spaceId` | string attribute `spaceId` |
| `metadata` | string attribute `metadata`，仅作为附加信息 |
| full request | 可选 typed/string attribute `serveRequest` |

`conversationId` 策略：

- 默认要求非空；缺失时 fail-fast，抛出 `IllegalArgumentException`。
- 不把 `metadata.sessionId` 作为标准 fallback。

### 5.2 消息映射

| runtime role | AgentScope role/type |
|--------------|----------------------|
| `user` | `UserMessage` |
| `assistant` | `MsgRole.ASSISTANT` |
| `system` | `MsgRole.SYSTEM` |
| `tool` | `MsgRole.TOOL` |
| unknown/null | 保守映射为 `USER`，并把原 role 放入 metadata |

内容提取：

1. `content` 是字符串：直接转文本。
2. `content` 是 list/map：提取 `text`、`content`、OpenAI-style `{type:"text", text:"..."}`。
3. 无法结构化提取：`String.valueOf(content)`，原始对象放入 metadata。
4. 不识别 adapter 自定义 resume schema；续轮输入仍按普通消息进入 AgentScope，由 AgentScope 根据同一 `sessionId` 的内部状态自行处理。

消息模式首版固定为 full history：adapter 按 `ServeRequest.messages` 当前内容顺序整体转换为 AgentScope `Msg` 列表，不提供 `last-user-only` 等配置开关。

### 5.3 QueryResponse 映射

默认 result：

```json
{
  "role": "assistant",
  "content": "final answer"
}
```

若存在 AgentScope metadata，可追加：

```json
{
  "role": "assistant",
  "content": "final answer",
  "metadata": {}
}
```

实现前需核对 `DefaultServeOrchestrator` / `A2AEnabledServeOrchestrator` 对 `QueryResponse.result` 的消费契约。若 runtime app 层只透传 result，则保持上述结构；若 app 层依赖特定字段，需要在 mapper 中对齐。

---

## 6. 流式事件与中断

### 6.1 AgentEvent 映射

普通增量、thinking、tool 过程事件都通过 `QueryChunk.TYPE_CHUNK` 传输；细粒度语义放在 payload 的 `type` 字段中。`QueryChunk.type` 只表达 runtime 生命周期大类，payload `type` 表达 AgentScope 事件语义。

| AgentScope event | `QueryChunk.type` | payload `type` |
|------------------|-------------------|----------------|
| `TextBlockDeltaEvent` | `TYPE_CHUNK` | `answer_delta` |
| `ThinkingBlockDeltaEvent` | `TYPE_CHUNK` | `thinking_delta` |
| `ToolCallStartEvent` | `TYPE_CHUNK` | `tool_call_start` |
| `ToolCallDeltaEvent` | `TYPE_CHUNK` | `tool_call_delta` |
| `ToolCallEndEvent` | `TYPE_CHUNK` | `tool_call_end` |
| `ToolResultTextDeltaEvent` | `TYPE_CHUNK` | `tool_result_delta` |
| `AgentResultEvent` | `TYPE_CHUNK`，首版默认不输出 | `final` |
| AgentScope pause/interrupt event | `TYPE_INTERRUPT` | `__interaction__` |
| `ExceedMaxItersEvent` | `TYPE_ERROR` | `agentscope_error` |
| unknown event | `TYPE_CHUNK`，首版默认不输出 | `agentscope_event` |

`TYPE_CHUNK` payload 示例：

```json
{
  "type": "tool_call_delta",
  "toolCallId": "tool-call-1",
  "name": "browser_search",
  "delta": "..."
}
```

默认只输出：

- answer delta
- interrupt
- error

### 6.2 中断输出

adapter 不把 AgentScope 内部中断类型暴露给 runtime 使用方。只要 AgentScope 产出需要暂停调用的事件，adapter 都归一为 runtime 可识别的外层中断 chunk：

```java
new QueryChunk(QueryChunk.TYPE_INTERRUPT, data)
```

runtime 硬识别的是 `QueryChunk.type == QueryChunk.TYPE_INTERRUPT`。`data.type="__interaction__"` 是与现有 agent-core adapter 对齐的 payload 语义标签，便于下游统一理解“这是一次交互请求”，但不作为 AgentScope 原生恢复协议。

首版中断 payload 只保留共性字段：

| 字段 | 必须 | 说明 |
|------|------|------|
| `type="__interaction__"` | 是 | payload 语义标签，表示交互请求 |
| `message` | 是 | 面向用户或上层编排的中断说明 |
| `context` | 否 | runtime/A2A 编排上下文；首版建议显式输出默认 `_interrupt_kind=ask_user` |
| `toolCallId` | 否 | AgentScope 事件存在单个 tool call id 时兼容输出 |
| `toolName` | 否 | AgentScope 事件存在单个 tool name 时兼容输出 |

`context` 不是 AgentScope adapter 首版中断的必要字段；runtime 在没有 `_interrupt_kind` 时也会按 `ask_user` 处理。为了让输出语义更明确，adapter 建议显式带上默认值：

```json
{
  "type": "__interaction__",
  "message": "...",
  "context": {
    "_interrupt_kind": "ask_user"
  }
}
```

AgentScope adapter 首版不生成 `_interrupt_kind=a2a_delegate`、`agentName` 或 `_stream_mode`。这些字段属于具备远端 agent 注入能力的业务 rail/工具层输出，AgentScope  Adapter当前版本未适配远端 agent 注入能力。

非流式 `query()` 如果最终结果是中断，`QueryResponse.result` 保持 runtime 可识别的 assistant result 形态，并把中断数据放入 `_interrupt`：

```json
{
  "role": "assistant",
  "content": "AgentScope requires input.",
  "_interrupt": {
    "type": "__interaction__",
    "message": "AgentScope requires input.",
    "context": {
      "_interrupt_kind": "ask_user"
    }
  }
}
```

首版不把 AgentScope 原生事件完整内容塞进 `QueryChunk.data`，也不把中断输出设计成恢复协议。AgentScope 事件的 `id`、`createdAt`、`replyId` 等字段最多作为轻量追踪信息放入 `context`，不能成为 runtime 恢复所依赖的必填字段。

### 6.3 Runtime/A2A 处理链路

runtime 的流式编排只捕获外层 `QueryChunk.TYPE_INTERRUPT`。捕获后：

1. 读取 `data.context._interrupt_kind`。
2. 没有 `_interrupt_kind` 或值为 `ask_user` 时，向客户端输出中断并结束本轮。

A2A 入口收到中断后，会把 task 置为 `INPUT_REQUIRED`。A2A status message 只需要从 `data.message` 取可展示文本；它不理解也不保存 AgentScope 原生事件结构。

AgentScope adapter 首版不触发 A2A delegation，因此不会由 adapter 产生远端 agent 回灌请求。用户对 `INPUT_REQUIRED` 的下一轮输入进入 adapter 时，表现为下一次普通 `query()` / `streamQuery()` 调用。

### 6.4 续轮输入

runtime 对普通 `AgentHandler` 没有单独的 resume API，续轮仍然调用 `AgentHandler.query(...)` 或 `AgentHandler.streamQuery(...)`。adapter 的职责只有两点：

1. 同一个 `ServeRequest.conversationId` 映射为同一个 AgentScope `RuntimeContext.sessionId`。
2. 下一轮 `ServeRequest.messages` 按普通消息映射为 AgentScope `Msg`。

首版不从纯文本自动推断用户决策或工具结果，也不自动构造 AgentScope 原生恢复对象。AgentScope 内部能否用同 session 的下一轮普通消息继续推进，由 AgentScope 自身决定；如果 AgentScope 抛出缺少原生恢复输入的异常，adapter 按 runtime error 映射。

需要特别区分：

- `RequestStopEvent` 源码注释明确说明下一次 `agent.call(...)` 可继续，适合首版同 session 普通消息续轮。
- `RequireUserConfirmEvent` / `RequireExternalExecutionEvent` 带有 AgentScope 原生 tool call 结构；如果 AgentScope 当前实现要求原生确认或工具结果对象，首版 adapter 不补造这些对象。

### 6.5 状态边界

adapter 只负责协议转换，不拥有 AgentScope 内部 checkpoint。续轮能否成功取决于：

- 同一个 `conversationId` 映射到同一个 AgentScope `sessionId`。
- AgentScope agent 的 state store 中仍保留对应会话状态。
- AgentScope 自身能否用下一轮普通 `Msg` 继续推进当前状态。

如果 AgentScope 返回无法继续的异常，首版 adapter 不补造恢复对象，应映射为 runtime error。

---

## 7. 装配与配置

### 7.1 首版装配策略

采用方案：**不自动注册 `AgentHandler`**。

原因：

1. AgentScope agent 的 model、toolkit、workspace、state store 等对象由宿主应用构建，adapter 无法通过配置安全推断默认实例。
2. 同一应用可能同时存在多个 `ReActAgent` 或 `HarnessAgent`，自动注册会带来歧义。
3. `openjiuwen.service.handler` 不属于本模块配置面；把 `agentscope` 写成激活开关会误导实现者和使用者。

业务侧显式声明。首版不需要 `AgentScopeProperties`：

```java
@Bean
AgentHandler agentscopeAgentHandler(ReActAgent reactAgent) {
    return AgentScopeAgentHandler.forReActAgent(reactAgent);
}
```

或：

```java
@Bean
AgentHandler agentscopeAgentHandler(HarnessAgent harnessAgent) {
    return AgentScopeAgentHandler.forHarnessAgent(harnessAgent);
}
```

`forReActAgent(...)` / `forHarnessAgent(...)` 在 handler 内部创建对应的 `ReActAgentScopeInvoker` / `HarnessAgentScopeInvoker` 和 mapper。用户侧只关心已经构建好的 AgentScope agent 对象，不感知 `AgentScopeAdapterInvoker`。

### 7.2 AutoConfiguration

首版提供轻量 `AgentScopeAutoConfiguration`，并通过 Spring Boot auto-configuration imports 注册，用于符合当前 adapter 模块装配惯例。但它不注册业务 Bean：

- adapter 首版没有专属业务配置项。
- 不自动创建 `AgentHandler`。
- mapper / invoker 都是 `AgentScopeAgentHandler` 内部实现细节，不需要暴露为 Spring Bean。

```java
@AutoConfiguration
public class AgentScopeAutoConfiguration {
}
```

注册文件：

```text
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

内容：

```text
com.openjiuwen.service.adapters.agentscope.autoconfigure.AgentScopeAutoConfiguration
```

---

## 8. Maven 依赖策略

父模块新增：

```xml
<module>agent-service-adapters/agent-service-adapters-agentscope</module>
```

版本属性：

```xml
<agentscope.version>2.0.0-RC5</agentscope.version>
```

首版默认依赖：

```xml
<dependency>
    <groupId>com.openjiuwen</groupId>
    <artifactId>agent-service-spec</artifactId>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-core</artifactId>
    <version>${agentscope.version}</version>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
    <version>${agentscope.version}</version>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
</dependency>
```

`HarnessAgent` 适配是首版必须实现的能力，但 `agentscope-harness` 在本模块 POM 中应声明为 optional。原因是本模块不会通过 AutoConfiguration 自动注册 Harness 相关 `AgentHandler`，只有显式使用 Harness 的宿主应用才需要传递 `agentscope-harness` 及其 workspace、sandbox、filesystem 等依赖。宿主应用若声明 `@Bean AgentHandler` 并调用 `AgentScopeAgentHandler.forHarnessAgent(...)`，必须自行引入 `io.agentscope:agentscope-harness`。

实现前必须运行 dependency tree 并记录 `agentscope-harness` 的传递依赖影响；若依赖冲突影响宿主应用，需要通过宿主侧 dependencyManagement 或 exclusion 固定版本。

---

## 9. 测试与验收

### 9.1 单元测试

| 测试类 | 覆盖点 |
|--------|--------|
| `AgentScopeRuntimeContextMapperTest` | conversationId 必填、userId/tenantId/spaceId 映射、metadata 只作附加 |
| `AgentScopeMessageMapperTest` | role 映射、文本提取、full history 固定策略、同会话续轮输入按普通 `Msg` 透传 |
| `AgentScopeEventMapperTest` | AgentScope pause/interrupt event -> runtime `QueryChunk(TYPE_INTERRUPT, data.type="__interaction__")`、轻量 interrupt payload、text delta、error、默认不输出 tool/thinking/final |
| `AgentScopeAgentHandlerTest` | query、streamQuery、cancel、timeout、clearSession best effort、相同 conversationId 复用相同 AgentScope sessionId、ReAct 单实例重入保护、Harness 调用桥接 |
| `AgentScopeAutoConfigurationTest` | auto-configuration imports 存在；不注册 `AgentHandler`、mapper、invoker 或 Harness 相关 bean |

### 9.2 集成/验证命令

```powershell
mvn -f common\agent-runtime-ext-java\pom.xml -pl agent-service-adapters/agent-service-adapters-agentscope test
```

```powershell
mvn -f common\agent-runtime-ext-java\pom.xml -pl agent-service-adapters/agent-service-adapters-agentscope dependency:tree -Dscope=runtime
```

### 9.3 验收标准

1. 新模块加入 parent module list。
2. AutoConfiguration imports 文件存在并只注册 `AgentScopeAutoConfiguration`。
3. `AgentScopeAutoConfiguration` 不自动创建 `AgentHandler`、mapper bean、invoker bean 或 Harness 相关 bean。
4. 业务通过 `AgentScopeAgentHandler.forReActAgent(...)` / `forHarnessAgent(...)` 显式声明 `@Bean AgentHandler` 后，`query()` 能返回 assistant result。
5. `streamQuery()` 能把 `TextBlockDeltaEvent` 转成 `QueryChunk.TYPE_CHUNK`，payload `type=answer_delta`。
6. thinking/tool/final 过程事件按首版固定策略处理：answer delta、interrupt、error 默认输出；thinking/tool/final 不默认输出。
7. AgentScope pause/interrupt 事件能转成 runtime 中断格式：外层 `QueryChunk.TYPE_INTERRUPT`，payload `data.type="__interaction__"`；payload 只包含 `message`、`context` 和可选 `toolCallId/toolName` 等共性字段，不暴露 AgentScope 内部中断类型作为协议。
8. 中断 payload 的 `context._interrupt_kind=ask_user` 非必要但建议显式输出；首版不得由 AgentScope adapter 生成 `a2a_delegate`、`agentName`、`_stream_mode` 等远端委托字段。
9. A2A INPUT_REQUIRED 的可展示文本来自 `data.message`，不依赖 AgentScope 原生事件结构。
10. 下一轮 `query()` 或 `streamQuery()` 使用同一个 `conversationId` 时，adapter 映射到同一个 AgentScope `sessionId`。
11. 下一轮用户输入、A2A INPUT_REQUIRED 续轮输入，首版都按普通 `Msg` 传给 AgentScope，不自动构造 AgentScope 原生恢复对象。
12. 如果 AgentScope 要求原生恢复输入并抛出异常，adapter 映射为 runtime error。
13. 包装同一 `ReActAgent` bean 时不会对同一实例发起重入调用；取消或异常后保护能释放。
14. 包装 `HarnessAgent` bean 时能通过 handler 完成 `query()` 与 `streamQuery()`。
15. `conversationId` 缺失时默认 fail-fast。
16. 不依赖真实模型 key 的单元测试全部通过。
17. dependency tree 已检查并记录 `agentscope-harness` 传递依赖影响；本模块 POM 中 `agentscope-harness` 为 optional。
18. 文档和代码保持本地进程内适配边界，不引入独立网络调用客户端或服务端。

---

## 10. 实施计划

1. 新增 `agent-service-adapters-agentscope` Maven 模块。
2. 父 POM 增加 module 和 `agentscope.version`。
3. 实现 `AgentScopeRuntimeContextMapper`，默认要求 `conversationId` 非空。
4. 实现 `AgentScopeMessageMapper`，覆盖 role/text/full history 策略，并把续轮输入按普通 `Msg` 透传。
5. 实现 `AgentScopeEventMapper`，把 AgentScope pause/interrupt event 转成外层 `QueryChunk.TYPE_INTERRUPT` 和 payload `type=__interaction__` 的中断数据；`context._interrupt_kind=ask_user` 非必要但建议作为默认值显式输出。
6. 在 `AgentScopeAgentHandler` 内实现内部 `AgentScopeAdapterInvoker` 以及 ReAct/Harness 两个内部实现。
7. 实现 `AgentScopeAgentHandler.forReActAgent(...)` / `forHarnessAgent(...)` 工厂方法，并确保同一 `conversationId` 稳定映射到同一 AgentScope `sessionId`。
8. 实现轻量 `AgentScopeAutoConfiguration` 和 auto-configuration imports，不注册业务 Bean。
9. 添加单元测试和 auto-configuration 测试。
10. 运行 test 和 dependency tree，并处理 `agentscope-harness` 传递依赖冲突。
