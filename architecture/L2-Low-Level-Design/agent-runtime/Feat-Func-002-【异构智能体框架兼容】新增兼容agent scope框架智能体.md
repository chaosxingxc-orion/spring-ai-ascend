---
level: L2-LLD
module: agent-runtime-ext-java
feature_type: functional
feature_id: Feat-Func-002
status: active
dependency:
  - ../../L1-High-Level-Design/agent-runtime/api-appendix.md
  - Feat-Func-002-heterogeneous-agent-framework-compatibility.md
  - openJiuwen/agent-runtime-java/service/agent-service-spec
  - AgentFramework/agentscope-java
---

# AgentScope Java Adapter 设计文档

> 目标模块：`common/agent-runtime-ext-java/agent-service-adapters/agent-service-adapters-agentscope`
> 最后更新：2026-07-10

说明：本文是 Feat-Func-002 在 openJiuwen 社区 `agent-runtime-java` 扩展仓中的本地 AgentScope 子设计，不替代同目录主文档对 `spring-ai-ascend/agent-runtime` 的本地/远程 AgentScope adapter 设计。L1 中立角色 `AgentRuntimeHandler` 在本实现中映射为 `com.openjiuwen.service.spec.spi.AgentHandler`；adapter 只产出结果/中断/失败语义，不直接写 A2A `TaskStore` 或成为第二个 Task 生命周期 owner。

---

## 1. 概述

### 1.1 特性定位

本特性为 `agent-runtime-java` 的扩展仓 `agent-runtime-ext-java` 增加 AgentScope Java 本地适配层。适配层把 OpenJiuwen runtime 的 `AgentHandler` SPI 桥接到 AgentScope Java 的进程内强类型 API，使宿主应用可以通过同一个 runtime 服务入口调用 AgentScope Java agent。

本特性只做 **本地进程内 Java 适配**：宿主应用在同一个 JVM 内构建好 `ReActAgent` 或 `HarnessAgent`，adapter 负责请求、响应、流式事件、有限的通用暂停输出，以及可用普通消息恢复的同会话续轮输入透传。

### 1.2 问题与目标

OpenJiuwen runtime 对外只认识 `AgentHandler`、`ServeRequest`、`QueryResponse`、`QueryChunk`，而 AgentScope Java 本地 API 使用 `RuntimeContext`、`Msg`、`Mono<Msg>`、`Flux<AgentEvent>`。本特性目标是在不改变 runtime SPI 的前提下，把业务已经构建好的 AgentScope `ReActAgent` / `HarnessAgent` 暴露为 OpenJiuwen runtime 可调用的 `AgentHandler`。

首版目标：

- `query()` / `streamQuery()` 调用 AgentScope Java 本地 API。
- 将 runtime 请求映射为 AgentScope `RuntimeContext` 和 `Msg`。
- 将 AgentScope `Msg` / `AgentEvent` 映射为 runtime `QueryResponse` / `QueryChunk`。
- 将同一个 `conversationId` 稳定映射到同一个 AgentScope `sessionId`；仅对 standalone `RequestStopEvent` 承诺通用暂停和普通 `Msg` 续轮。

### 1.3 当前事实边界

已核验的当前代码事实：

1. `AgentHandler` SPI 只有 `query`、`streamQuery`、`start`、`stop`、`clearSession(String conversationId)`。
2. 本模块不提供运行时 handler 选择器，不把配置项解释为自动激活 `AgentHandler` 的开关。
3. 首版不引入 adapter 专属业务配置项；宿主应用只需显式声明 `AgentHandler` bean。
4. AgentScope Java `ReActAgent` 按 `(userId, sessionId)` 缓存状态并按 slot 串行化调用；同一 slot 串行，不同 slot 可以并行，adapter 不增加全局互斥锁。
5. AgentScope `streamEvents(...)` 是异步 `Flux<AgentEvent>`，而 `AgentHandler.streamQuery(...)` 是同步回调方法；handler 必须维持调用直到流终止，否则 `ServeOrchestrator` 会提前注销 active stream。
6. runtime 生命周期中断通过可选 `AgentInterruptHandler` 下发；adapter handler 同时实现该接口，并将 conversation 定向到 AgentScope `(userId, sessionId)` 中断。
7. `RequestStopEvent` 支持同 session 下一次 `call(...)` 继续，但其源码用途还包括预算保护、审计、调试步进和内容审核；首版统一投影为通用暂停，不宣称保留这些细分语义。
8. `RequireUserConfirmEvent` 需要 `Msg.METADATA_CONFIRM_RESULTS`，pending external tool 需要 `ToolResultBlock`。首版不伪造这些原生恢复对象。
9. 首版不修改 runtime，因此不支持通过 WebFlux `/v1/query/reactive` 调用 AgentScope handler；支持范围限定为 MVC Query 与 A2A 入口。

### 1.4 版本

AgentScope Java：`2.0.0-SNAPSHOT`。

---

## 2. 功能规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| 新增 Maven 模块 | 必须 | `agent-service-adapters-agentscope`，统一承载 ReAct 与 Harness 适配 |
| `AgentHandler.query` | 必须 | 调用 AgentScope `call(...)`，返回 `QueryResponse` |
| `AgentHandler.streamQuery` | 必须 | 调用 AgentScope `streamEvents(...)`，输出 `QueryChunk` |
| `ServeRequest -> RuntimeContext` | 必须 | 映射 conversationId/userId/tenantId/spaceId |
| `ServeRequest.messages -> List<Msg>` | 必须 | 支持文本消息、常见 role；原生 confirm/tool-result 不在通用消息映射中猜测 |
| `Msg -> QueryResponse.result` | 必须 | 默认 `{role:"assistant", content:"..."}` |
| `AgentEvent -> QueryChunk` | 必须 | text delta、有限的通用 interrupt、error 映射 |
| `ReActAgent` 适配 | 必须 | 本地进程内调用 `ReActAgent.call/streamEvents`；并发交给 AgentScope per-slot 串行化 |
| `HarnessAgent` 适配 | 必须 | 本地进程内调用 `HarnessAgent.call/streamEvents` |
| Spring Boot properties | 排除首版 | 首版不引入 adapter 专属业务配置项 |
| Reactor 桥接 | 必须 | Mono 超时阻塞；Flux 订阅、取消、超时和 terminal 生命周期闭环 |
| WebFlux Query 入口 | 排除首版 | `/v1/query/reactive` 当前会在响应式调用线程进入同步 handler；本特性不修改 runtime |
| 生命周期中断 | 必须 | handler 实现 `AgentInterruptHandler`，按 conversation 定向 dispose + AgentScope interrupt |
| 中断输出 | 部分支持 | standalone `RequestStopEvent` 固定映射为通用 runtime interrupt；原生确认和外部执行进入明确错误终态 |
| 可普通续轮输入透传 | 必须 | standalone `RequestStopEvent` 后使用同一 `conversationId/sessionId`，把用户新输入作为普通 `Msg` 交给 AgentScope |
| AgentScope 原生恢复转换 | 排除首版 | 不在首版自动构造 AgentScope 原生恢复对象 |
| 多模态完整映射 | 排除首版 | 首版文本优先 |
| 自动构建 AgentScope agent | 排除首版 | model/toolkit/workspace/sandbox 配置面过大 |
| 自动注册 `AgentHandler` | 排除首版 | 避免模块被引入后自动接管服务入口 |
| 通用 AgentScope 原生 resume SPI | 排除首版 | runtime 当前没有 ConfirmResult/ToolResultBlock 的稳定 DTO |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| `openjiuwen.service.handler=agentscope` 作为激活开关 | 本模块不提供 handler 选择器，配置项不承担自动装配语义 | 业务显式 `@Bean AgentHandler` |
| adapter 专属业务配置项 | 首版行为固定，配置会扩大使用和测试面 | 代码内固定默认策略，后续确有差异化需求再加配置 |
| 自动构建 `ReActAgent` | model/toolkit/state/tool 配置面太大，超出适配层职责 | 宿主应用自行构建 agent bean |
| 依赖 `Agent.stream(...)` | `StreamableAgent.stream(...)` 已 deprecated，事件模型粗糙 | 使用 `ReActAgent/HarnessAgent.streamEvents(...)` |
| 自动构造 AgentScope 原生恢复对象 | 当前 runtime 没有通用 resume DTO，A2A 入口也只透传文本；适配层不应猜测用户确认或工具结果 | standalone `RequestStopEvent` 可同 session 普通续轮；确认/外部工具恢复返回明确 unsupported error |
| AgentScope 自启动 HTTP 服务 | AgentScope Java core/harness 本身不是 runtime server | 由宿主 Spring Boot 应用暴露 OpenJiuwen runtime 服务 |
| adapter 全局并发锁 | AgentScope 已按 `(userId, sessionId)` 串行化，额外全局锁会错误压制跨会话并行 | 直接使用 AgentScope per-slot 调度 |

## 3. 核心设计（Logical + Process View）

### 3.1 为什么不是统一适配 `Agent` 接口

`AgentScopeAgentHandler` 对 runtime 实现 `AgentHandler` 和 `AgentInterruptHandler`。handler 委托给模块内部的 `AgentScopeInvoker`，首版提供两个强类型实现：

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

因此设计是 **一个共享 handler + 一个内部 invoker 契约 + 两个强类型实现**。用户只通过 handler 工厂传入已构建好的 `ReActAgent` 或 `HarnessAgent`，无需直接实现 `AgentScopeInvoker`。

### 3.2 两类 AgentScope agent 差异

| 来源 | 定位 | 适配策略 |
|------|------|----------|
| `ReActAgent` | AgentScope core 基础 ReAct 实现 | adapter 直接调用 `call/streamEvents`；同 slot 串行、跨 slot 并行由 AgentScope 自身保证 |
| `HarnessAgent` | 带 workspace/sandbox/skills/memory/subagent/plan mode 的增强运行时 | adapter 直接调用 `call/streamEvents`；状态和并发语义由 AgentScope 自身负责 |

两类 agent 不能压成一种实现的关键原因：

- `ReActAgent` 和 `HarnessAgent` 的公共 `Agent` 接口不足以表达细粒度 streaming。
- `ReActAgent` 的状态与串行键来自 `RuntimeContext.userId/sessionId`；adapter 必须稳定映射这两个字段，不能再加实例级互斥覆盖框架并发模型。
- `HarnessAgent` 的运行模型、依赖对象和并发语义不同于 `ReActAgent`，需要独立 invoker 明确边界。

### 3.3 ReActAgent 调用边界

当前 AgentScope Java `ReActAgent.callSerializationKey(...)` 返回 `(userId, sessionId)` slot key。框架缓存每个 slot 的 `AgentState` / `PermissionEngine`，同一 slot 的调用串行，不同 slot 的调用可以并行；每次 `streamEvents` 订阅也使用独立 event sink。

`ReActAgentScopeInvoker` 的职责边界：

- 必须把 `ServeRequest.userId/conversationId` 稳定映射为 `RuntimeContext.userId/sessionId`。
- 禁止增加覆盖整个 `ReActAgent` 实例的 semaphore / synchronized 全局互斥。
- 同一 conversation 的并发到达交给 AgentScope slot gate 排队；不同 conversation 不被 adapter 阻塞。
- adapter 只管理 Reactor 订阅生命周期，不接管 AgentScope 的状态锁、线程池、限流或调度公平性。

定向中断必须调用 `ReActAgent.interrupt(userId, sessionId)`；禁止使用 deprecated 的无参 `interrupt()`，否则并发场景下可能中断错误的会话。

### 3.4 HarnessAgent 策略

`HarnessAgent` 是首版必须支持的本地 AgentScope agent 类型。它面向更完整的运行时能力，通常包含 workspace、sandbox、skills、memory、subagent、plan mode 等能力；adapter 不构建这些对象，只负责把已经由宿主应用构建好的 `HarnessAgent` 暴露为 OpenJiuwen runtime 的 `AgentHandler`。

首版提供 `HarnessAgentScopeInvoker`：

- `call(...)` 直接调用 `HarnessAgent.call(messages, context)`。
- `streamEvents(...)` 直接调用 `HarnessAgent.streamEvents(messages, context)`。
- 不增加 adapter 全局互斥；以 Harness 包装的 ReAct per-slot 状态和 Harness 资源生命周期语义为准。
`HarnessAgentScopeInvoker` 与 `ReActAgentScopeInvoker` 同属本地强类型适配，不通过字符串事件协议或网络协议中转。

### 3.5 Reactor 到 AgentHandler 的桥接

`AgentHandler` 是同步/回调契约，桥接层不能仅调用 `subscribe()` 后立即返回。

非流式 `query()`：

```text
map request/context/messages
  -> invoker.call(messages, context)
  -> timeout(options.queryTimeout)
  -> block()
  -> Msg / ASKING tool state 映射
  -> QueryResponse
```

- 调用前登记 active invocation；正常、异常、超时均在 `finally` 移除。
- 超时调用 invoker 的定向 `interrupt(userId, sessionId)`，抛出 `AGENTSCOPE_QUERY_TIMEOUT`，由 runtime 错误链统一返回。
- 非流式结果若携带 ASKING tool 状态，不包装成普通 ask-user interrupt，而返回 `AGENTSCOPE_NATIVE_CONFIRM_RESUME_UNSUPPORTED`。

流式 `streamQuery()`：

```text
Flux<AgentEvent>
  -> absolute stream timeout
  -> subscribe(serial event mapping)
  -> retain Disposable + terminal latch
  -> current thread waits for complete/error/cancel
  -> finally dispose, unregister active invocation
```

- `streamQuery()` 必须等待流 terminal，防止 `ServeOrchestrator.streamQuery()` 的 `finally` 提前注销 active stream。
- `onNext` 映射和 `observer.onNext` 按单订阅顺序串行执行；用原子 terminal 标记保证 `onError/onComplete` 至多一次。
- 等待期间按 `cancellationPollInterval` 检查 `observer.isCancelled()`；断连时 dispose subscription，并定向 interrupt AgentScope slot。
- `streamTimeout` 是单次流的绝对最长执行时间，不因中间 event 到达而重置；超时最多输出一个 `TYPE_ERROR`，随后以 `observer.onError(IllegalStateException)` 结束。
- active registry 使用 `conversationId -> invocationId -> ActiveInvocation`，同 conversation 有多个在途调用时，生命周期中断会取消全部记录，不能因覆盖 map entry 漏掉旧调用。

错误终态规则：

1. 同步 query 的校验失败、超时、unsupported 状态和 AgentScope 异常统一转换为 runtime 当前接受的 `IllegalStateException`，使 `A2AAgentExecutor` 非流式异常链能够推进 Task 到 failed。
2. 流式错误最多向 observer 输出一个 `TYPE_ERROR` chunk，随后必须调用一次 `observer.onError(IllegalStateException)`。
3. 发出错误 chunk 后禁止再调用 `observer.onComplete()`，也禁止继续转发后续 AgentScope event。
4. `TYPE_ERROR` 负责向 Query/SSE facade 表达错误码，`onError` 负责通知 runtime/A2A 这是失败终态；错误码直接写入当次 payload/exception message，不新增本地错误状态、错误码类型或专用异常类型。

调用线程要求：

- Spring MVC 当前通过 `CompletableFuture.runAsync(...)` 执行 `orchestrator.streamQuery(...)`；A2A 执行由 runtime 后台 executor 承载。两条路径都允许 handler 阻塞等待 AgentScope Flux terminal。
- Spring WebFlux 当前在 `/v1/query/reactive` 的调用线程直接执行同步 `orchestrator.query(...)`，并在 `Flux.create(...)` subscription callback 中执行同步 `orchestrator.streamQuery(...)`。AgentScope handler 会阻塞等待结果或流 terminal，因此可能阻塞 Netty event-loop。
- 本特性不修改 runtime，也不在 adapter 内伪造异步返回；首版明确不支持 `/v1/query/reactive`。这不影响 MVC Query 和 A2A 入口的 AgentScope 能力。

### 3.6 中断支持边界

首版不引入额外的中断分类 SPI 或 AgentScope 原生 resume SPI，只保留固定映射：

1. standalone `RequestStopEvent` 映射为 `TYPE_INTERRUPT/__interaction__`，下一轮以同 session 普通消息继续。
2. `RequireUserConfirmEvent` / `PERMISSION_ASKING` 返回原生确认恢复不支持错误。
3. pending external tool / external execution 返回各自的恢复不支持错误。

该映射只保留“暂停并等待下一次调用”的共性，不保留 RequestStop 的细分原因。AgentScope 源码允许 RequestStop 用于预算保护、审计检查点、调试步进和内容审核；runtime 会把这些场景统一呈现为通用交互暂停，不能据此推导精确的业务中断类型、确认结构或恢复动作。需要保留这些原生语义的 Agent 不属于首版适配范围。

---

## 4. 模块结构（Development View）

### 4.1 包结构

```text
agent-service-adapters-agentscope/
├─ pom.xml
└─ src/main/java/com/openjiuwen/service/adapters/agentscope/
   ├─ agentfw/
   │  ├─ AgentScopeAgentHandler.java       # AgentHandler + AgentInterruptHandler + 两类工厂
   │  ├─ AgentScopeInvoker.java            # 模块内部桥接契约
   │  ├─ ReActAgentScopeInvoker.java       # 强类型 ReActAgent 调用与定向中断
   │  ├─ HarnessAgentScopeInvoker.java     # 强类型 HarnessAgent 调用与定向中断
   │  ├─ AgentScopeAdapterOptions.java     # timeout / cancel poll 配置
   │  └─ AgentScopeInvocationRegistry.java # active invocation 生命周期
   └─ mapping/
      ├─ AgentScopeMessageMapper.java
      ├─ AgentScopeRuntimeContextMapper.java
      └─ AgentScopeEventMapper.java
```

### 4.2 核心静态关系

```text
AgentHandler --------------------┐
AgentInterruptHandler -----------+--> AgentScopeAgentHandler
                                      | uses
                                      v
                                AgentScopeInvoker <<internal interface>>
                                  ^                         ^
                                  |                         |
                         ReActAgentScopeInvoker    HarnessAgentScopeInvoker
```

`AgentScopeInvoker` 不进入对外 API。`AgentScopeAgentHandler.forReActAgent(...)` 与 `forHarnessAgent(...)` 分别创建对应 invoker；公共 handler、mapper、options 和 invocation registry 由两类 Agent 共用。

执行链路：

```text
ServeRequest
  -> AgentScopeRuntimeContextMapper
  -> AgentScopeMessageMapper
  -> AgentScopeAgentHandler / AgentScopeInvoker
  -> AgentScopeEventMapper / response mapper
  -> QueryResponse / QueryChunk
```

`AgentScopeMessageMapper` 只负责把 runtime 消息转换为 AgentScope `Msg`。active invocation registry 只保存取消所需的 userId/sessionId、Disposable 和 terminal signal，不保存或伪造 AgentScope pending 恢复对象。

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

- 默认要求非空；缺失时 fail-fast，抛出包含 `AGENTSCOPE_INVALID_REQUEST` 的 `IllegalStateException`。
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
4. 不识别 adapter 自定义 resume schema；普通消息只用于 standalone `RequestStopEvent` 续轮，不用于替代 `ConfirmResult` 或 `ToolResultBlock`。

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
| standalone `RequestStopEvent` | `TYPE_INTERRUPT` | `__interaction__` |
| `RequireUserConfirmEvent` | `TYPE_ERROR` + `onError` | `AGENTSCOPE_NATIVE_CONFIRM_RESUME_UNSUPPORTED` |
| `RequireExternalExecutionEvent` | `TYPE_ERROR` + `onError` | `AGENTSCOPE_EXTERNAL_EXECUTION_RESUME_UNSUPPORTED` |
| `ExceedMaxItersEvent` | `TYPE_ERROR` + `onError` | `AGENTSCOPE_EXECUTION_FAILED` |
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

### 6.2 中断与恢复支持矩阵

AgentScope 的暂停事件不是同一种恢复语义。首版只提供收敛后的有限映射：

| AgentScope 状态/事件 | 源码恢复要求 | 首版映射 | 后续输入 |
| --- | --- | --- | --- |
| standalone `RequestStopEvent` | 源码允许下一次 `agent.call(...)` 继续；细分 stop 原因不进入 runtime 协议 | `TYPE_INTERRUPT` + `data.type=__interaction__` | 同一 userId/sessionId 的普通消息 |
| `RequireUserConfirmEvent` | `Msg.METADATA_CONFIRM_RESULTS` 中的 `List<ConfirmResult>` | `TYPE_ERROR`，code=`AGENTSCOPE_NATIVE_CONFIRM_RESUME_UNSUPPORTED` | 首版不接受伪造的 yes/no 文本恢复 |
| pending external tool result | 对应 tool id 的 `ToolResultBlock` | `TYPE_ERROR`，code=`AGENTSCOPE_EXTERNAL_TOOL_RESULT_UNSUPPORTED` | 首版不构造 tool result block |
| `RequireExternalExecutionEvent` | 事件类型存在，但当前已核验生产路径未发现实例化 | 保留识别分支并返回 `AGENTSCOPE_EXTERNAL_EXECUTION_RESUME_UNSUPPORTED` | 首版不支持 |
| runtime 主动取消 | 定向 `interrupt(userId, sessionId)` | 不输出 ask-user interrupt；结束当前调用 | 后续是否新开调用由客户端决定 |

`RequestStopEvent` 的 interrupt payload 只保留 runtime 共性字段：

```json
{
  "type": "__interaction__",
  "message": "AgentScope execution paused.",
  "context": {
    "_interrupt_kind": "ask_user"
  }
}
```

若 `RequireUserConfirmEvent` 后紧跟 `RequestStopEvent`，mapper 在 confirm unsupported error 后进入 terminal 状态，后续 stop event 必须丢弃，禁止把错误覆盖成看似可用普通文本恢复的 `INPUT_REQUIRED`。

非流式路径同样检查最终 `Msg`：若存在 ASKING tool blocks 或缺少 required `ConfirmResult` / `ToolResultBlock` 的 pending 状态，返回对应 unsupported error，不生成 `_interrupt`。若最终 `Msg.generateReason` 是 `MIDDLEWARE_STOP_REQUESTED`，则按 standalone RequestStop 生成通用 `_interrupt`；不尝试还原更细的 stop 原因。

AgentScope adapter 首版不生成 `_interrupt_kind=a2a_delegate`、`agentName` 或 `_stream_mode`；事件的 `id`、`createdAt`、`replyId` 只可作为追踪信息，不能成为 runtime 恢复协议必填字段。

### 6.3 Runtime/A2A 处理链路

runtime 的流式编排只捕获外层 `QueryChunk.TYPE_INTERRUPT`。因此仅 standalone `RequestStopEvent` 进入该链路：

1. 读取 `data.context._interrupt_kind`。
2. 没有 `_interrupt_kind` 或值为 `ask_user` 时，向客户端输出中断并结束本轮。

A2A 入口收到这个可普通续轮的中断后，会把 task 置为 `INPUT_REQUIRED`。A2A status message 只从 `data.message` 取可展示文本；它不理解也不保存 AgentScope 原生事件结构。

`RequireUserConfirmEvent` 和 external-tool pending 不进入 `INPUT_REQUIRED`，而是以 runtime 接受的 `IllegalStateException` 结束，使 A2A Task 进入 failed，避免暴露一个实际上无法用普通文本恢复的 Task。AgentScope adapter 首版也不触发 A2A delegation。

### 6.4 续轮输入

runtime 对普通 `AgentHandler` 没有单独的 resume API，续轮仍然调用 `AgentHandler.query(...)` 或 `AgentHandler.streamQuery(...)`。adapter 的职责只有两点：

1. 同一个 `ServeRequest.conversationId` 映射为同一个 AgentScope `RuntimeContext.sessionId`。
2. 下一轮 `ServeRequest.messages` 按普通消息映射为 AgentScope `Msg`。

首版不从纯文本自动推断用户决策或工具结果，也不自动构造 AgentScope 原生恢复对象。只有 standalone `RequestStopEvent` 对外承诺同 session 普通续轮；其他 native resume 场景在事件出现时立即返回稳定错误码并进入失败终态。

需要特别区分：

- `RequestStopEvent` 源码注释确认下一次 `agent.call(...)` 可继续；adapter 只把它呈现为通用暂停，不承诺保留预算、审计、调试或审核等原始业务含义。
- `RequireUserConfirmEvent` 明确要求 `Msg.METADATA_CONFIRM_RESULTS`，缺失时源码抛 `IllegalStateException`。
- pending external tool 结果必须以 `ToolResultBlock` 携带，并校验 tool id；普通文本不能等价替代。
- `RequireExternalExecutionEvent` / `ExternalExecutionResultEvent` 类型虽然存在，但当前已核验的 ReAct 生产路径未发现发射点，首版只做防御性 unsupported 映射。

### 6.5 状态边界

adapter 只负责协议转换，不拥有 AgentScope 内部 checkpoint。续轮能否成功取决于：

- 同一个 `conversationId` 映射到同一个 AgentScope `sessionId`。
- AgentScope agent 的 state store 中仍保留对应会话状态。
- 当前暂停类型是否属于首版支持的 standalone `RequestStopEvent`。

`clearSession(conversationId)` 首版只取消并清理 adapter active invocation，不实现完整的 AgentScope session reset。虽然 `AgentStateStore` 提供 `delete(userId, sessionId)`，但 runtime SPI 只传入 conversationId，且 `ReActAgent` 没有公开的统一单 session 缓存清理 API。因此 `/reset_conversation` 对 AgentScope handler 只是 best-effort：它能结束当前调用，但下一次调用仍可能读取旧会话状态。本文只记录该限制，不在首版增加本地身份映射、缓存操作或 runtime 改造。

### 6.6 生命周期中断闭环

`AgentScopeAgentHandler.interrupt(conversationId, reason)` 执行：

1. 从 active registry 取得该 conversation 的全部 invocation。
2. 原子标记 cancelled，dispose 流式 `Disposable`。
3. 对每条记录调用 `AgentScopeInvoker.interrupt(userId, sessionId)`；ReAct 直接调用定向 interrupt，Harness 通过 `getDelegate()` 调用同一 API。
4. 唤醒等待中的 `streamQuery()`，由其 `finally` 注销记录。

`stop()` 对全部 active invocation 执行相同清理。重复 interrupt、客户端断连和 terminal 回调必须幂等，且不得在取消后再向 observer 发送 chunk。

---

## 7. 装配与配置

### 7.1 首版装配策略

采用方案：**不自动注册 `AgentHandler`**。

原因：

1. AgentScope agent 的 model、toolkit、workspace、state store 等对象由宿主应用构建，adapter 无法通过配置安全推断默认实例。
2. 同一应用可能同时存在多个 `ReActAgent` 或 `HarnessAgent`，自动注册会带来歧义。
3. `openjiuwen.service.handler` 不属于本模块配置面；把 `agentscope` 写成激活开关会误导实现者和使用者。

业务侧显式声明。Bean 返回类型必须写为 `AgentScopeAgentHandler`，使 Spring 同时发现其 `AgentHandler` 与 `AgentInterruptHandler` 契约：

```java
@Bean
AgentScopeAgentHandler agentscopeAgentHandler(ReActAgent reactAgent) {
    return AgentScopeAgentHandler.forReActAgent(
        reactAgent, AgentScopeAdapterOptions.defaults());
}
```

或：

```java
@Bean
AgentScopeAgentHandler agentscopeAgentHandler(HarnessAgent harnessAgent) {
    return AgentScopeAgentHandler.forHarnessAgent(
        harnessAgent, AgentScopeAdapterOptions.defaults());
}
```

两个工厂都位于 `AgentScopeAgentHandler`。用户侧只关心已经构建好的 AgentScope agent 对象，不直接操作 `AgentScopeInvoker`。

### 7.2 程序化配置

首版不提供空 `AgentScopeAutoConfiguration`，也不创建 `AutoConfiguration.imports`。一个不注册任何 bean 的 auto-configuration 没有装配价值，只会制造错误的自动启用预期。

`AgentScopeAdapterOptions` 提供代码级稳定默认值：

| 属性 | 默认值 | 语义 |
| --- | --- | --- |
| `queryTimeout` | 5 min | 非流式调用最长时间 |
| `streamTimeout` | 30 min | 单次流绝对最长时间 |
| `cancellationPollInterval` | 100 ms | 等待线程检查客户端取消的周期 |

options 必须校验为正值。后续若形成稳定运维需求，可由宿主配置类绑定后传给工厂；adapter 首版不直接依赖 Spring Boot autoconfigure。

---

## 8. Maven 依赖策略

父模块新增一个模块：

```xml
<module>agent-service-adapters/agent-service-adapters-agentscope</module>
```

版本属性：

```xml
<agentscope.version>2.0.0-SNAPSHOT</agentscope.version>
```

模块依赖：

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
</dependency>
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-core</artifactId>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
</dependency>
```

`agentscope-harness` 是普通 compile 依赖，不是 optional。单模块公开 `forHarnessAgent(...)` 并直接编译 `HarnessAgentScopeInvoker`，因此必须保证引入 adapter 后相关 API 和依赖完整可用。

实现前必须运行 dependency tree 并记录 `agentscope-harness` 的传递依赖影响；若依赖冲突影响宿主应用，需要通过宿主侧 dependencyManagement 或 exclusion 固定版本。

---

## 9. 对外呈现 / 用户场景（Scenario View）

### 9.1 外部接口

本适配器不新增 HTTP endpoint。业务注册 `AgentScopeAgentHandler` 后，通过 runtime 现有 MVC `/v1/query`、`/query` 或 Task-owning `/a2a` 入口调用；首版不支持 WebFlux `/v1/query/reactive`。L1 定义的是框架中立角色，openJiuwen Java 实现与本适配器的映射如下：

| L1 中立角色/职责 | openJiuwen Java 实现 | AgentScope adapter 边界 |
| --- | --- | --- |
| `AgentRuntimeHandler.execute` | `AgentHandler.query/streamQuery` | `AgentScopeAgentHandler` 实现同步与流式执行 |
| `AgentExecutionContext` | `ServeRequest` | context/message mapper 转换为 `RuntimeContext` 与 `Msg` |
| `AgentExecutionResult` | `QueryResponse` / `QueryChunk` | response/event mapper 生成 runtime 结果 |
| `resultAdapter` | AgentScope response/event mapper | 只转换内容、中断和失败，不推进 Task 状态 |
| cancel | `QueryStreamObserver.isCancelled` + `AgentInterruptHandler` | dispose subscription，并按 userId/sessionId 定向 interrupt |
| health | `AgentReadiness` | 复用 runtime readiness gate，不在 adapter 内新增健康协议 |
| `agentId` | runtime deployment/config binding | 由宿主选择并注册 handler，不由 AgentScope 对象名推导 |
| Task 状态推进 | `A2AAgentExecutor` / `AgentEmitter` | adapter 不直接写 `TaskStore`；Query facade 不创建 Task |

### 9.2 典型场景

```text
Client -> Runtime Query/A2A -> ServeOrchestrator
       -> AgentScopeAgentHandler
       -> ReActAgent or HarnessAgent (same JVM)
       -> QueryResponse / QueryChunk
```

- 普通完成：answer delta / final result 经 runtime 原有响应返回。
- 通用暂停：standalone `RequestStopEvent` 在 A2A 路径进入 `INPUT_REQUIRED`，下一轮沿用 conversationId；Query facade 只在当前响应中返回 interrupt。预算、审计、调试、审核等原始 stop 原因不会被保留为独立协议语义。
- 原生确认暂停：返回稳定 unsupported error，不暴露一个无法闭合的 `INPUT_REQUIRED`。
- 客户端断连或 runtime 生命周期中断：handler dispose 订阅并定向中断 AgentScope slot。

---

## 10. 错误处理（Process View）

| 错误场景 | 触发条件 | adapter 行为 | 对外 code/type |
| --- | --- | --- | --- |
| 非法请求 | conversationId 缺失 | fail-fast，不调用 AgentScope | `AGENTSCOPE_INVALID_REQUEST` |
| query 超时 | 超过 `queryTimeout` | 定向 interrupt，清理 active record | `AGENTSCOPE_QUERY_TIMEOUT` |
| stream 超时 | 超过绝对 `streamTimeout` | dispose + 定向 interrupt，最多输出一次错误帧，随后 `onError` | `AGENTSCOPE_STREAM_TIMEOUT` / `TYPE_ERROR` |
| 原生确认恢复不支持 | `RequireUserConfirmEvent` 或 ASKING tool state | 终止映射，忽略随后 stop event | `AGENTSCOPE_NATIVE_CONFIRM_RESUME_UNSUPPORTED` |
| 外部工具结果不支持 | pending tool 需要 `ToolResultBlock` | 不从普通文本猜测结果 | `AGENTSCOPE_EXTERNAL_TOOL_RESULT_UNSUPPORTED` |
| 外部执行恢复不支持 | 收到保留的 external execution event | 防御性终止 | `AGENTSCOPE_EXTERNAL_EXECUTION_RESUME_UNSUPPORTED` |
| AgentScope 执行异常 | Mono/Flux error | 脱敏日志；同步转换为 `IllegalStateException`，流式最多输出一次 error 后调用 `onError` | `AGENTSCOPE_EXECUTION_FAILED` |
| 客户端取消 | observer cancelled | 不再输出，dispose + 定向 interrupt | 无新增响应帧 |

所有异常路径都必须注销 active invocation。除客户端取消外，失败统一转换为 runtime 当前接受的 `IllegalStateException`；流式路径不得在 error chunk 后正常 complete。错误 code 只存在于当次响应 payload 或 exception message，不作为 adapter 本地状态保存。错误消息不得包含 prompt、tool 参数、认证信息或完整 state；详细异常只写受控日志与 trace。

---

## 11. 测试与验收

### 11.1 单元测试

| 测试类 | 覆盖点 |
|--------|--------|
| `AgentScopeRuntimeContextMapperTest` | conversationId 必填、userId/tenantId/spaceId 映射、metadata 只作附加 |
| `AgentScopeMessageMapperTest` | role 映射、文本提取、full history；普通续轮不伪造 `ConfirmResult` / `ToolResultBlock` |
| `AgentScopeEventMapperTest` | standalone stop -> generic interrupt；confirm/external resume -> stable error；confirm 后 stop 不覆盖 terminal error |
| `AgentScopeAgentHandlerTest` | Mono block/timeout、Flux 等待 terminal、客户端断连、lifecycle interrupt、错误帧后 onError、terminal exactly-once、active registry 清理 |
| `ReActAgentScopeInvokerTest` | 同 slot 串行、不同 slot 并行、定向 interrupt，不存在 adapter 全局锁 |
| `AgentScopeNativeResumeTest` | RequestStop 同 session 普通续轮；ASKING tool 在非流式/流式均返回稳定错误 |
| `HarnessAgentScopeInvokerTest` | Harness query/stream 与 delegate 定向 interrupt |

### 11.2 集成/验证命令

```powershell
mvn -f common\agent-runtime-ext-java\pom.xml -pl agent-service-adapters/agent-service-adapters-agentscope -am test
```

```powershell
mvn -f common\agent-runtime-ext-java\pom.xml -pl agent-service-adapters/agent-service-adapters-agentscope dependency:tree -Dscope=runtime
```

### 11.3 验收标准

1. 单个 `agent-service-adapters-agentscope` 模块加入 parent module list，并同时提供 ReAct/Harness 工厂。
2. 不存在空 `AgentScopeAutoConfiguration` 或 AutoConfiguration imports。
3. 业务显式声明 concrete `AgentScopeAgentHandler` bean 后，query/stream 和 `AgentInterruptHandler` 都可被 runtime 发现。
4. `streamQuery()` 在 Flux complete/error/cancel 前不返回，active stream 不会被 orchestrator 提前注销。
5. AgentScope handler 可通过 MVC `/v1/query`、`/query` 和 `/a2a` 调用；首版不支持 `/v1/query/reactive`，且不要求修改 runtime WebFlux controller。
6. query/stream timeout、客户端断连和 lifecycle interrupt 都执行定向 AgentScope interrupt，并清理 active registry。
7. 同一 `(userId, sessionId)` 调用由 AgentScope 串行，不同 session 可并行；adapter 无全局实例锁。
8. `TextBlockDeltaEvent` 映射为 `TYPE_CHUNK/answer_delta`；thinking/tool/final 按首版固定策略处理。
9. standalone `RequestStopEvent` 映射 `TYPE_INTERRUPT/__interaction__`，同 session 普通消息可续轮；该映射只承诺通用暂停，不承诺保留细分 stop 语义。
10. `RequireUserConfirmEvent` 映射 `AGENTSCOPE_NATIVE_CONFIRM_RESUME_UNSUPPORTED`，后续 stop 不得覆盖该错误。
11. pending external tool / `RequireExternalExecutionEvent` 返回各自 stable unsupported error，不伪造 `ToolResultBlock`。
12. 非流式 ASKING tool 状态与流式 confirm event 采用相同 unsupported 语义。
13. observer terminal 回调至多一次；失败最多输出一个 `TYPE_ERROR` 后调用 `onError`，禁止 error 后 `onComplete`，取消后不再输出 chunk。
14. `conversationId` 缺失时 fail-fast；tenantId 继续按现有设计写入 string attribute。
15. `agentscope-harness` 在单模块中作为普通 compile 依赖，不得声明为 optional。
16. dependency tree 已检查并记录 Harness 的传递依赖影响。
17. 文档和代码保持本地进程内适配边界，不引入独立网络客户端或服务端。

---

## 12. 限制与待补

| 限制 | 影响 | 后续方向 |
| --- | --- | --- |
| 不支持 `ConfirmResult` 原生恢复 | AgentScope ASK 权限工具不能经普通 A2A 文本续接 | runtime 定义显式 confirm DTO 后增加转换 |
| 不支持外部工具 `ToolResultBlock` 回灌 | pending external tool 无法闭环 | 定义 tool-result resume contract |
| `RequireExternalExecutionEvent` 当前仅防御性识别 | 当前 ReAct 生产调用链中未发现稳定发射点 | AgentScope 出现稳定发射路径后完善事件与恢复映射 |
| 文本消息优先 | 多模态 block 可能降级为字符串 | 增加 block-level 映射 |
| `/reset_conversation` 仅 best-effort | 只结束 active invocation，下一次调用仍可能延续旧会话状态 | 首版不实现完整 AgentScope session reset；需要严格隔离时使用新的 conversationId |
| WebFlux Query 不支持 | `/v1/query/reactive` 进入同步 handler 时可能阻塞 Netty event-loop | 首版使用 MVC Query 或 A2A 入口，不修改 runtime |
| stream bridge 占用一个执行线程 | 长流在 MVC/A2A 后台执行期间持续占用线程 | 首版接受同步 `AgentHandler` 契约的该限制 |
| 仅程序化 options | 运维侧不能直接用 adapter YAML 调参 | 形成稳定需求后由宿主绑定配置 |

---

## 13. 实施计划

1. 新增 `agent-service-adapters-agentscope` Maven 模块，父 POM 增加 module 和 `agentscope.version`。
2. 实现模块内部 `AgentScopeInvoker`、options、mappers、invocation registry 和共享 handler；错误直接转换为 runtime 的 `TYPE_ERROR` / `IllegalStateException` 契约。
3. 实现 `ReActAgentScopeInvoker`，直接使用 per-slot 并发并提供 `interrupt(userId, sessionId)`。
4. 实现 Mono timeout/block 和 Flux subscribe/wait/cancel/timeout 桥接，保证 terminal exactly-once；接入范围限定为 MVC Query 与 A2A，不修改 runtime WebFlux controller。
5. 实现 standalone RequestStop 通用暂停与 confirm/external unsupported 状态机；流式失败保证 error chunk 至多一次并以 `onError` 终止，覆盖流式和非流式。
6. 让 `AgentScopeAgentHandler` 同时实现 `AgentHandler`、`AgentInterruptHandler`，完成 active invocation 定向中断。
7. 在同一模块实现 `HarnessAgentScopeInvoker` 和 handler 的 `forHarnessAgent(...)` 工厂。
8. 添加并发、取消、timeout、错误终态、中断边界和恢复语义测试；不添加空 auto-configuration 测试。
9. 运行模块 test 和 dependency tree，处理 Harness 传递依赖冲突。
