# agent-runtime 五层重构实施方法

> 范围：只讨论 `agent-runtime` 内部重构。  
> 目标：把当前混合了接入、任务、队列、执行、Spring Boot 启动、框架适配和历史残留的实现，重构为一个可运行、可嵌入、可通过 A2A 暴露的 Java agent runtime。  
> 原则：先做减法，再做实现；五个基本模块是 `access`、`session`、`queue`、`control`、`engine`。允许少量公共类型与启动入口包，但它们不承载业务架构含义。

---

## 0. 结论

`agent-runtime` 的主结构是：

```text
agent-runtime/
  common/
  access/
  session/
  queue/
  control/
  engine/
  app/
```

其中只有五个业务层：

```text
access  = 对外接入与用户可见输出通道；当前只实现 A2A。
session = runtime session 管理；只管理外部连续会话，不等于 agent session。
queue   = 无业务队列能力；供 access/control/engine 在内部创建自己的队列。
control = task 生命周期、状态机、中断/恢复/取消、父子 task 编排。
engine  = agent 执行调度与具体 agent 框架适配；当前只完整实现 openJiuwen adapter。
```

两个辅助包：

```text
common = 公共数据类型，不承载流程；例如 AgentRequest、AgentResponse、ErrorInfo。
app    = runtime 运行入口，提供类似 AgentScope Runtime Java `AgentApp.run(...)` 的能力。
```

跨层规则：

```text
access  -> control API
control -> engine API
engine  -> control API / access API
```

禁止跨层直接操作对方内部队列。每个业务模块如果需要异步处理，只能使用 `queue` 提供的队列能力在本模块内部创建队列，并通过本模块 API 对外暴露写入能力。

本文不讨论其它模块架构，不引用旧设计预设，不把当前旧目录结构当约束。

---

## 1. 必须删除什么

### 1.1 删除目录

| 当前目录 | 处理 | 原因 |
|---|---|---|
| `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/async/` | 删除 | 当前没有真实 northbound async protocol。异步能力应由各层内部队列实现，不应伪装成一种外部协议。 |
| `agent-runtime/src/main/java/com/huawei/ascend/runtime/dispatch/` | 删除顶层概念 | `dispatch` 实际承载 engine 执行、handler registry、engine event、openJiuwen adapter，概念错位。全部并入 `engine` 或拆入 `access/control` API。 |
| `agent-runtime/src/main/java/com/huawei/ascend/runtime/dispatch/dispatch/` | 删除目录层级 | `dispatch/dispatch` 是命名错误；其中 `EngineDispatcher` 的职责是 engine worker/runtime。 |
| `agent-runtime/src/main/java/com/huawei/ascend/runtime/bootstrap/` | 删除混合包 | 该包混合 Boot 入口、Spring 配置、agent 基类、access notification adapter，不是一个架构层。 |
| `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/runtime/` | 删除目录层级 | 与 `dispatch` 形成双 engine。需要合并到唯一 `engine` 层。 |
| `agent-runtime/src/main/java/com/huawei/ascend/runtime/**/2026-*.md` | 移出源码目录 | 源码目录只放代码；历史设计文档移入 `docs/logs/reviews/` 或删除副本。 |

### 1.2 删除或替换旧类

| 当前类 | 处理 | 新概念 |
|---|---|---|
| `dispatch.spi.AgentHandler` | 删除旧形态 | `engine.spi.AgentRuntimeHandler` |
| `dispatch.spi.AgentResultAdapter` | 删除 | `engine.spi.StreamAdapter` |
| `dispatch.spi.AgentExecutionResult` | 删除旧形态 | `common.AgentResponse` + engine 内部执行信号 |
| `dispatch.adapter.openjiuwen.OpenJiuwenMessageConverter` | 删除旧实现 | `engine.openjiuwen.OpenJiuwenMessageAdapter` |
| `dispatch.adapter.openjiuwen.OpenJiuwenResultMapper` | 删除旧实现 | `engine.openjiuwen.OpenJiuwenStreamAdapter` |
| `bootstrap.AbstractRuntimeAgentHandler` | 删除 bootstrap 归属 | `engine.spi.AbstractAgentRuntimeHandler` |
| `bootstrap.AccessNotificationClient` | 删除 bootstrap 归属 | `access.output.EngineOutputSink` 或平铺为 `access.EngineOutputSink` |
| `dispatch.port.TaskControlClient` | 删除命名 | `control.api.TaskControlApi` 的 engine 回调方法 |
| `taskcontrol.api.TaskControlClient` | 改名 | `control.api.TaskControlApi` |

### 1.3 删除文档中的旧架构表达

不再把这些作为目标结构：

```text
agent-runtime-sdk 顶层模块
agent-runtime-middleware 顶层模块
agent-runtime-spring-boot-starter 顶层模块
agent-service 部署包设计
Product Claim 追踪表
旧 ADR / 旧 L1 约束
```

如果需要 Spring Boot 或其它 HTTP server，它只是 `app` / `access.a2a` 的某个 host 实现依赖，不是 runtime core 的依赖。

---

## 2. 目标目录

目标 Java 包结构：

```text
agent-runtime/src/main/java/com/huawei/ascend/runtime/
  common/
    AgentRequest
    AgentResponse
    ErrorInfo
    ResponseMode
    ResponseType
    ResponseStatus

  access/
    api/
      AccessRequestApi
      AccessOutputApi
    a2a/
      A2aController
      A2aJsonRpcHandler
      A2aAgentCardController
      A2aRequestMapper
      A2aResponseMapper
    output/
      OutputChannel
      OutputChannelRegistry
      SseOutputChannel
      BlockingOutputChannel
      EngineOutputSink
    DefaultAccessRequestApi

  session/
    api/
      RuntimeSessionApi
      RuntimeSessionRepository
    RuntimeSession
    SessionId
    ConversationId
    HistoryWindow
    MessageRef
    InMemoryRuntimeSessionRepository

  queue/
    RuntimeQueue
    QueuePublisher
    QueueSubscriber
    QueueFactory
    QueueManager
    QueueRegistration
    QueueName
    InMemoryRuntimeQueue
    InMemoryQueueFactory

  control/
    api/
      TaskControlApi
    TaskSubmitResult
    TaskRecord
    TaskState
    TaskResumeRequest
    TaskCancelRequest
    TaskQuery
    TaskControlSignal
    TaskStateMachine
    TaskTransition
    TaskControlWorker

  engine/
    api/
      EngineExecutionApi
    spi/
      AgentRuntimeHandler
      AbstractAgentRuntimeHandler
      MessageAdapter
      StreamAdapter
      ToolProvider
      McpToolProvider
      MemoryProvider
      StateProvider
      SandboxProvider
    service/
      ToolService
      MemoryService
      StateService
      SandboxService
      AgentSessionHistoryService
    openjiuwen/
      OpenJiuwenAgentRuntimeHandler
      OpenJiuwenMessageAdapter
      OpenJiuwenStreamAdapter
      OpenJiuwenToolAdapter
      OpenJiuwenMemoryAdapter
      OpenJiuwenStateAdapter
      OpenJiuwenSandboxAdapter
    EngineExecutionRequest
    EngineResumeRequest
    EngineCancelRequest
    AgentExecutionInput
    AgentExecutionContext
    AgentDescriptor
    EngineWorker

  app/
    RuntimeApp
    RuntimeHost
    RuntimeComponents
    RunningRuntime
    LocalA2aRuntimeHost
```

子目录规则：

```text
只为外部边界建子目录：
  api      = 其它模块调用本模块的入口。
  spi      = 用户或框架实现的扩展点。
  service  = runtime 提供给 agent adapter 的能力。
  a2a      = 外部协议适配。
  openjiuwen = 外部框架适配。
  output   = access 的外部输出通道，属于用户可见边界。

不为实现类型提前建子目录：
  store
  memory
  manage
  worker
  state
```

`session` 不建 `store/` 子包。持久化能力直接表达为接口：`RuntimeSessionRepository`；首版实现为 `InMemoryRuntimeSessionRepository`。未来 Redis 实现可命名为 `RedisRuntimeSessionRepository`，实现多到需要隔离时再建 `session/redis/`。

---

## 3. 对外暴露包

对照 AgentScope Runtime Java，它对外暴露的是：

```text
AgentHandler
AgentApp
Runner
MemoryService / StateService / SessionHistoryService / SandboxService
A2A starter / protocol integration
```

本项目对外暴露：

```text
common.*             // AgentRequest、AgentResponse 等公共模型。
app.RuntimeApp       // 类似 AgentScope AgentApp.run(...) 的运行入口。
app.RuntimeHost      // host SPI，支持 local A2A host。
engine.spi.*         // 用户/框架实现扩展点。
engine.service.*     // 注入给 agent adapter 的 runtime 能力。
access.api.*         // A2A 或 host 接入层调用面。
```

不默认暴露：

```text
queue.*              // 普通用户不需要直接使用。
control.*            // 普通用户不直接推进 task。
session.RuntimeSessionRepository // 普通用户不直接持久化 runtime session。
engine.openjiuwen.*  // 只有需要继承 openJiuwen 基类或定制 adapter 的用户使用。
```

---

## 4. 外部依赖规则

### 4.1 common

```text
只能依赖 JDK。
不能依赖 Spring Boot。
不能依赖 A2A SDK。
不能依赖 openJiuwen。
```

### 4.2 queue

```text
只能依赖 JDK 和 common 中必要的错误类型。
不能依赖 Spring Boot。
不能依赖任何业务模块。
不能出现 AgentRequest、TaskRecord、EngineExecutionRequest 等业务类型。
```

### 4.3 session

```text
可依赖 common。
可使用 queue，但不暴露 queue 类型。
不能依赖 Spring Boot。
不能依赖 openJiuwen。
不能依赖 engine。
不能保存 openJiuwen / AgentScope 原生 session 对象。
```

### 4.4 control

```text
可依赖 common、session.api、queue、engine.api。
不能依赖 Spring Boot。
不能依赖 openJiuwen。
不能依赖 access.a2a。
```

### 4.5 engine

```text
engine api/spi/service 只能依赖 common、queue 和 JDK。
engine.openjiuwen 可以依赖 com.openjiuwen:agent-core-java。
engine 核心不能依赖 Spring Boot。
engine 核心不能依赖 A2A SDK。
```

### 4.6 access

```text
access.api 和 access core 可依赖 common、session.api、control.api、queue。
access.a2a 可以依赖 A2A SDK 和 HTTP stack。
access 不依赖 openJiuwen。
access 不直接依赖 engine.openjiuwen。
```

### 4.7 app

```text
app 可依赖五层 API。
app.RuntimeApp / RuntimeHost 不依赖 Spring Boot。
app.LocalA2aRuntimeHost 如果使用 Spring Boot，Spring Boot 依赖只停留在该 host 实现内。
```

---

## 5. access 层

### 5.1 职责

`access` 负责对外接入与用户可见输出通道。当前只实现 A2A。

它做：

```text
A2A JSON-RPC 请求 -> AgentRequest
AgentResponse -> A2A JSON response / SSE event
每个外部请求维护 OutputChannel
调用 control API 提交、恢复、取消 task
维护请求接入、输出、关闭、异常清理状态
```

它不做：

```text
不执行 agent
不推进 task 状态
不直接操作 engine queue
不保存 agent session
不实现独立 async protocol
```

### 5.2 AgentRequest

`AgentRequest` 是 access 转换后的内部标准输入。用户不需要理解 A2A 原始对象，只需要理解这个对象。

```text
AgentRequest
  requestId
  tenantId
  userId
  agentId
  sessionId?          // 可为空；access/session 创建或解析
  input               // 当前最小集只支持 text
  responseMode        // BLOCKING / STREAMING
  metadata
```

说明：

- `input` 当前只实现 text，后续多模态不改变五层结构。
- `sessionId` 是 runtime 外部连续会话 ID，不等于 openJiuwen / AgentScope session。
- A2A 专有字段不进入主模型，必要时进入 `metadata`。

### 5.3 AgentResponse

`AgentResponse` 是用户可见响应对象。阻塞和流式都使用同一个类型。

```text
AgentResponse
  requestId
  sequence             // streaming 时递增；blocking 可为 1
  responseType         // TASK / DELTA / FINAL / ERROR
  tenantId
  userId
  agentId
  sessionId
  taskId
  status               // ACCEPTED / RUNNING / INPUT_REQUIRED / COMPLETED / FAILED / CANCELLED
  output               // 当前最小集只支持 text
  error
  metadata
```

阻塞模式：

```text
AgentRequest -> AgentResponse(responseType=FINAL)
```

流式模式：

```text
AgentRequest
  -> AgentResponse(responseType=TASK,  status=ACCEPTED)
  -> AgentResponse(responseType=DELTA, status=RUNNING, output=partial text)
  -> AgentResponse(responseType=DELTA, status=RUNNING, output=partial text)
  -> AgentResponse(responseType=FINAL, status=COMPLETED, output=final text)
```

失败流：

```text
AgentResponse(responseType=ERROR, status=FAILED, error=...)
```

取消流：

```text
AgentResponse(responseType=FINAL, status=CANCELLED)
```

### 5.4 A2A method 映射

| A2A method | access 行为 |
|---|---|
| `message/send` | 转 `AgentRequest(responseMode=BLOCKING)`，调用 `TaskControlApi.submit`，等待 output channel final response。 |
| `message/stream` | 转 `AgentRequest(responseMode=STREAMING)`，创建 SSE `OutputChannel`，调用 `TaskControlApi.submit`，持续输出 `AgentResponse`。 |
| `tasks/get` | 调 `TaskControlApi.getTask`，映射为 `AgentResponse` 或 A2A task view。 |
| `tasks/cancel` | 转取消请求，调用 `TaskControlApi.cancel`。 |
| `tasks/resubscribe` | 重新绑定 `OutputChannel`，从已有 task/output 状态继续输出。 |

### 5.5 access 内部队列

access 不暴露 queue。access 为每个请求创建 `OutputChannel`，`OutputChannel` 底层队列由 `queue.QueueFactory` 创建。

```text
OutputChannel
  - internal RuntimeQueue<AgentResponse>
  - write(AgentResponse)
  - subscribe(OutputSubscriber)
  - close()
  - fail(Throwable)
```

engine 不直接写 access 内部队列；engine 只调用：

```java
AccessOutputApi.onAgentResponse(AgentResponse response)
```

`AccessOutputApi` 内部把 response 写入对应 `OutputChannel`。

---

## 6. session 层

### 6.1 职责

`session` 只管理 runtime session。

```text
RuntimeSession 是外部用户、agent、conversation 的连续上下文。
RuntimeSession 不是 AgentSession。
RuntimeSession 不保存 openJiuwen / AgentScope 原生 session 对象。
RuntimeSession 不推进 task 状态。
RuntimeSession 不执行 agent。
```

### 6.2 RuntimeSession

```text
RuntimeSession
  sessionId
  tenantId
  userId
  agentId
  conversationId
  createdAt
  updatedAt
  expiresAt
  historyWindow        // 首版 in-memory 可直接存 text history
  messageRefs          // 接口保留 ref 抽象
  lastRequestId
  lastTaskId
  agentStateRef?       // 只存引用
  checkpointRef?       // 只存引用
  metadata             // 小而可序列化，不放 Java 对象和大 payload
```

### 6.3 AgentSession 与 RuntimeSession 区分

```text
RuntimeSession:
  属于 session 层。
  服务外部请求连续性。
  用于 access/control 查找 runtime 上下文。

AgentSession:
  属于 engine 层。
  服务 openJiuwen / AgentScope / 其它 agent 框架执行。
  由 engine service/provider 管理。
```

禁止：

```text
禁止把 openJiuwen Session 放进 RuntimeSession。
禁止把 checkpoint 本体放进 RuntimeSession。
禁止把 memory 内容放进 RuntimeSession。
禁止把 tool result 大对象放进 RuntimeSession。
```

允许：

```text
允许 RuntimeSession 保存 checkpointRef。
允许 RuntimeSession 保存 agentStateRef。
允许首版 InMemoryRuntimeSessionRepository 直接保存 text history。
允许接口表达为 historyWindow/messageRefs，未来替换为外部 message repository。
```

### 6.4 API 与 Repository

```java
RuntimeSession loadOrCreate(SessionLookup lookup);
void appendUserInput(SessionId sessionId, String requestId, String text);
void appendAgentOutput(SessionId sessionId, String requestId, String text);
Optional<RuntimeSession> find(SessionId sessionId);
void updateRefs(SessionId sessionId, SessionRefs refs);
void close(SessionId sessionId);
```

持久化接口：

```java
interface RuntimeSessionRepository {
    RuntimeSession save(RuntimeSession session);
    Optional<RuntimeSession> find(SessionId sessionId);
    void delete(SessionId sessionId);
}
```

首版实现：

```text
InMemoryRuntimeSessionRepository
```

未来实现：

```text
RedisRuntimeSessionRepository
JdbcRuntimeSessionRepository
```

不需要 `session/store/` 子包。

---

## 7. queue 层

### 7.1 职责

`queue` 是无业务队列能力层。它不认识 `AgentRequest`、`TaskRecord`、`EngineExecutionRequest` 的业务含义。

它提供：

```text
RuntimeQueue<T>
QueueFactory
QueueManager
QueueSubscriber<T>
QueuePublisher<T>
QueueLifecycle
```

access、control、engine 都会使用它实例化自己的内部业务队列。

### 7.2 类

queue 层平铺，不拆 `api/memory/manage` 子包。

```text
queue/
  RuntimeQueue<T>
  QueuePublisher<T>
  QueueSubscriber<T>
  QueueFactory
  QueueManager
  QueueRegistration
  QueueName
  InMemoryRuntimeQueue<T>
  InMemoryQueueFactory
```

### 7.3 使用方式

queue 对外只提供队列能力。access、control、engine 可以在模块内部使用 `QueueFactory` 创建队列，但不把队列作为模块 API 暴露。

access 内部：

```text
OutputChannel 内部持有 RuntimeQueue<AgentResponse>
```

control 内部：

```text
TaskControlWorker 内部持有 RuntimeQueue<TaskControlSignal>
```

engine 内部：

```text
EngineWorker 内部持有 RuntimeQueue<EngineExecutionRequest>
```

跨模块禁止传队列实例。只能调用 API：

```text
access 调 control API
control 调 engine API
engine 调 control/access API
```

---

## 8. control 层

### 8.1 职责

`control` 是 task 生命周期唯一拥有者。

它负责：

```text
task 创建
task 状态机
task 内部队列
task parent/child 编排
中断/恢复/取消
根据状态调用 engine API
接收 engine 回调推进状态
```

它不负责：

```text
不解析 A2A。
不执行 agent。
不管理 agent session。
不直接操作 access output queue。
不允许外部 publish 任意 TaskEvent。
```

### 8.2 强类型 API

control 对外只暴露强类型 API，不暴露 `publish(TaskEvent)`。

```java
TaskControlApi.submit(AgentRequest request)
TaskControlApi.resume(TaskResumeRequest request)
TaskControlApi.cancel(TaskCancelRequest request)
TaskControlApi.getTask(TaskQuery query)

TaskControlApi.onEngineRunning(EngineRunningSignal signal)
TaskControlApi.onEngineOutput(EngineOutputSignal signal)
TaskControlApi.onEngineInputRequired(EngineInputRequiredSignal signal)
TaskControlApi.onEngineCompleted(EngineCompletedSignal signal)
TaskControlApi.onEngineFailed(EngineFailedSignal signal)
TaskControlApi.onEngineCancelled(EngineCancelledSignal signal)
```

API 内部做：

```text
强类型请求 -> TaskControlSignal -> TaskControlWorker 内部队列
```

### 8.3 内部信号

```text
TaskControlSignal
  SubmitTaskSignal
  ResumeTaskSignal
  CancelTaskSignal
  EngineRunningSignal
  EngineOutputSignal
  EngineInputRequiredSignal
  EngineCompletedSignal
  EngineFailedSignal
  EngineCancelledSignal
```

只有 `TaskControlWorker` 消费 `TaskControlSignal` 并调用 `TaskStateMachine`。

### 8.4 Task 模型

```text
TaskRecord
  taskId
  tenantId
  userId
  agentId
  sessionId
  parentTaskId?
  state
  revision
  input
  createdAt
  updatedAt
  checkpointRef?
  error?
  metadata
```

状态：

```text
CREATED
QUEUED
RUNNING
WAITING_INPUT
WAITING_CHILD
CANCELLING
COMPLETED
FAILED
CANCELLED
```

### 8.5 control -> engine 最小请求

control 调 engine 时只传最小执行请求。

```text
EngineExecutionRequest
  requestId
  tenantId
  userId
  agentId
  sessionId
  taskId
  input
  responseMode
  checkpointRef?
  metadata
```

不传：

```text
完整 AgentDefinition 装配对象
完整 history
工具实例
memory 内容
openJiuwen ReActAgent
framework session
A2A 原始对象
```

### 8.6 类

```text
control/
  api/
    TaskControlApi
  TaskSubmitResult
  TaskRecord
  TaskState
  TaskResumeRequest
  TaskCancelRequest
  TaskQuery
  TaskControlSignal
  TaskStateMachine
  TaskTransition
  TaskControlWorker
```

control 不暴露 `TaskControlQueue`。它只在 `TaskControlWorker` 内部使用 `queue.RuntimeQueue<TaskControlSignal>`。

---

## 9. engine 层

### 9.1 职责

`engine` 负责 agent 执行调度与具体 agent 框架适配。

它负责：

```text
对 control 暴露 EngineExecutionApi
内部维护 execution queue
EngineWorker 消费 execution queue
找到 AgentRuntimeHandler
构建 AgentExecutionContext
调用 openJiuwen adapter
执行中同步调用 control/access API
提供 agent 实现需要的 service/provider
```

它不负责：

```text
不解析 A2A。
不创建 runtime session。
不推进 task 状态。
不维护第二条 event queue。
```

### 9.2 API

```java
EngineExecutionApi.submit(EngineExecutionRequest request)
EngineExecutionApi.resume(EngineResumeRequest request)
EngineExecutionApi.cancel(EngineCancelRequest request)
```

API 内部只做入队：

```text
EngineExecutionApi.submit
  -> EngineWorker 内部队列 offer(request)
```

后台 worker：

```text
EngineWorker
  -> subscribe internal execution queue
  -> AgentRuntimeHandlerRegistry.find(agentId)
  -> handler.execute(input, context)
  -> call TaskControlApi.onEngineXxx
  -> call AccessOutputApi.onAgentResponse
```

### 9.3 南向 SPI

```text
engine/spi/
  AgentRuntimeHandler
  AbstractAgentRuntimeHandler
  MessageAdapter
  StreamAdapter
  ToolProvider
  McpToolProvider
  MemoryProvider
  StateProvider
  SandboxProvider
```

`AgentRuntimeHandler`：

```java
public interface AgentRuntimeHandler {
    AgentDescriptor descriptor();
    void start();
    void stop();
    boolean isHealthy();
    Stream<AgentResponse> execute(AgentExecutionInput input, AgentExecutionContext context);
    default Stream<AgentResponse> resume(AgentExecutionInput input, AgentExecutionContext context) {
        return execute(input, context);
    }
    default void cancel(AgentExecutionContext context, String reason) {
    }
}
```

说明：

- handler 返回 `AgentResponse` 流，engine 再分发给 access/control。
- handler 不直接写 task 状态。
- handler 不直接操作 A2A。
- handler 使用 `AgentExecutionContext` 获取 tool/memory/state/sandbox service。

### 9.4 engine service/provider

不使用 `middleware` 作为顶层概念，使用 service/provider。

```text
engine/service/
  ToolService
  MemoryService
  StateService
  SandboxService
  AgentSessionHistoryService

engine/spi/
  ToolProvider
  McpToolProvider
  MemoryProvider
  StateProvider
  SandboxProvider
```

这些能力是给 agent 实现和 adapter 使用的，不属于 runtime session。

### 9.5 ToolProvider 使用方式

用户实现：

```java
public final class LoanPolicyToolProvider implements ToolProvider {
    @Override
    public List<ToolDescriptor> describe() {
        return List.of(new ToolDescriptor(
            "loan-policy-lookup",
            "Lookup loan policy by product and region"
        ));
    }

    @Override
    public ToolResult invoke(ToolInvocation invocation) {
        return ToolResult.text("...");
    }
}
```

用户或 example 代码注册 provider：

```java
ToolService toolService = new ToolService();
toolService.register(new LoanPolicyToolProvider());
```

运行时由 handler/adapter 决定把哪些 tool 注入 agent。runtime 只提供可注册、可解析、可调用的 tool 能力，不负责“通过配置初始化 agent 实例”。

### 9.6 类

engine 层保留三个关键隔离：

```text
api      = control 调 engine 的入口。
spi      = 用户/框架实现的扩展面。
service  = runtime 注入给 agent adapter 的能力。
openjiuwen = 首个外部框架适配实现。
```

```text
engine/
  api/
    EngineExecutionApi
  spi/
    AgentRuntimeHandler
    AbstractAgentRuntimeHandler
    MessageAdapter
    StreamAdapter
    ToolProvider
    McpToolProvider
    MemoryProvider
    StateProvider
    SandboxProvider
  service/
    ToolService
    MemoryService
    StateService
    SandboxService
    AgentSessionHistoryService
  openjiuwen/
    OpenJiuwenAgentRuntimeHandler
    OpenJiuwenMessageAdapter
    OpenJiuwenStreamAdapter
    OpenJiuwenToolAdapter
    OpenJiuwenMemoryAdapter
    OpenJiuwenStateAdapter
    OpenJiuwenSandboxAdapter
  EngineExecutionRequest
  EngineResumeRequest
  EngineCancelRequest
  AgentExecutionInput
  AgentExecutionContext
  AgentDescriptor
  EngineWorker
```

engine 不暴露 `EngineExecutionQueue`。它只在 `EngineWorker` 内部使用 `queue.RuntimeQueue<EngineExecutionRequest>`。

---

## 10. app 运行入口

### 10.1 为什么需要 app

AgentScope Runtime Java 提供 `AgentApp.run(...)`：用户把 `AgentHandler` 交给 runtime，runtime 创建 `Runner`，再通过 `DeployManager` 启动 HTTP/A2A 服务。

本项目也需要这个能力，否则 runtime 只有零散模块，没有一个“能跑起来”的开发者入口。

但不能照搬 AgentScope 的 Spring Boot 依赖方式。`RuntimeApp` 和 `RuntimeHost` 必须是纯 Java API；具体 host 实现可以选择 Spring Boot、JDK HTTP server 或其它 HTTP stack。

### 10.2 RuntimeApp

```java
public final class RuntimeApp {
    public static RuntimeApp create(AgentRuntimeHandler handler);

    public RuntimeApp access(AccessRequestApi access);
    public RuntimeApp session(RuntimeSessionApi session);
    public RuntimeApp queue(QueueFactory queueFactory);
    public RuntimeApp control(TaskControlApi control);
    public RuntimeApp engine(EngineExecutionApi engine);

    public RuntimeApp toolService(ToolService service);
    public RuntimeApp memoryService(MemoryService service);
    public RuntimeApp stateService(StateService service);
    public RuntimeApp sandboxService(SandboxService service);

    public RunningRuntime run(RuntimeHost host);
}
```

### 10.3 RuntimeHost

```java
public interface RuntimeHost {
    RunningRuntime start(RuntimeComponents components);
}
```

首个 host：

```text
LocalA2aRuntimeHost
```

示例：

```java
public static void main(String[] args) {
    AgentRuntimeHandler handler = new PingPongOpenJiuwenHandler();

    RuntimeApp.create(handler)
        .run(LocalA2aRuntimeHost.port(8080));
}
```

这个入口只负责把五层组装并运行起来，不负责通过配置初始化 agent。agent 的创建由 example 或业务应用代码完成。

---

## 11. AgentScope Runtime Java 能力映射

AgentScope Runtime Java 的可借鉴点不是目录名，而是能力组合：

```text
AgentHandler + MessageAdapter + StreamAdapter
Runner
AgentApp.run
MemoryService / InMemoryMemoryService
StateService
SessionHistoryService
SandboxService
ToolkitInit / MCP tools
A2A Controller / JSONRPCHandler / SSE
```

映射到本项目：

| AgentScope Runtime Java | agent-runtime |
|---|---|
| `AgentHandler` | `engine.spi.AgentRuntimeHandler` |
| `MessageAdapter` | `engine.spi.MessageAdapter` |
| `StreamAdapter` | `engine.spi.StreamAdapter` |
| `Runner` | `engine.EngineWorker` + `EngineExecutionApi` |
| `AgentApp.run(...)` | `app.RuntimeApp.run(RuntimeHost)` |
| `DeployManager` / `LocalDeployManager` | `app.RuntimeHost` / `LocalA2aRuntimeHost` |
| `MemoryService` / `InMemoryMemoryService` | `engine.service.MemoryService` / in-memory provider |
| `StateService` | `engine.service.StateService` |
| `SessionHistoryService` | `engine.service.AgentSessionHistoryService`，不是 `session.RuntimeSession` |
| `SandboxService` | `engine.service.SandboxService` |
| `ToolkitInit` / MCP tools | `engine.service.ToolService` + `McpToolProvider` |
| `A2aController` / `JSONRPCHandler` | `access.a2a.A2aController` / `A2aJsonRpcHandler` |
| SSE streaming JSON-RPC response | `common.AgentResponse` stream model |

关键选择：

```text
AgentScope Runtime Java 内部有 Event/Content/Message 模型。
本项目当前不把 Event/Content 作为用户可见概念。
用户只看 AgentRequest / AgentResponse。
```

---

## 12. openJiuwen 首个实现

### 12.1 覆盖能力

openJiuwen adapter 是 `engine` 的第一个完整实现。必须覆盖：

```text
ReActAgent
Runner.runAgent
Runner.runAgentStreaming
Tool
LongTermMemory
framework session / checkpoint ref
```

`BaseWorkflow` / `BaseGroup` 可以作为 handler 能力接入，但不扩展 access/control 架构。

### 12.2 执行链路

```text
EngineWorker
  -> OpenJiuwenAgentRuntimeHandler
  -> OpenJiuwenMessageAdapter
  -> OpenJiuwenToolAdapter
  -> OpenJiuwenMemoryAdapter
  -> build/configure ReActAgent
  -> Runner.runAgent 或 Runner.runAgentStreaming
  -> OpenJiuwenStreamAdapter
  -> Stream<AgentResponse>
```

### 12.3 输入映射

| AgentExecutionInput | openJiuwen |
|---|---|
| `tenantId` | tool/memory/state scope，不丢失。 |
| `sessionId` | runtime 外部 session id，不直接等于 openJiuwen session。 |
| `taskId` | runner execution id / release id。 |
| `input` | 当前映射为 query text。 |
| `checkpointRef` | resume 时给 openJiuwen checkpoint/session 恢复逻辑。 |
| `metadata` | model/tool/memory 额外配置。 |

### 12.4 输出映射

| openJiuwen 输出 | AgentResponse |
|---|---|
| accepted / start | `responseType=TASK`, `status=ACCEPTED/RUNNING` |
| stream text chunk | `responseType=DELTA`, `status=RUNNING`, `output=chunk` |
| final answer | `responseType=FINAL`, `status=COMPLETED`, `output=final text` |
| need human input | `responseType=FINAL`, `status=INPUT_REQUIRED` |
| exception | `responseType=ERROR`, `status=FAILED`, `error=...` |
| cancel | `responseType=FINAL`, `status=CANCELLED` |

### 12.5 资源释放

openJiuwen adapter 必须在 terminal response 后释放资源：

```text
try execute
finally Runner.release(taskId)
```

terminal response 包括：

```text
COMPLETED
FAILED
CANCELLED
INPUT_REQUIRED
```

---

## 13. 用户如何使用 runtime

### 13.1 A2A 调用

用户通过 A2A 调用：

```http
POST /a2a
Content-Type: application/json
```

`message/send` 返回一个最终 `AgentResponse`。

`message/stream` 返回多条 SSE，每条 SSE data 是一个 `AgentResponse`。

### 13.2 启动 runtime

runtime 不负责“通过配置初始化 agent 实例”。example 或业务应用代码负责构造 handler，然后交给 runtime。

```java
AgentRuntimeHandler handler = new PingPongOpenJiuwenHandler();

RuntimeApp.create(handler)
    .run(LocalA2aRuntimeHost.port(8080));
```

### 13.3 注入 service/provider

如果用户要工具能力：

```java
ToolService toolService = new ToolService();
toolService.register(new LoanPolicyToolProvider());

RuntimeApp.create(handler)
    .toolService(toolService)
    .run(LocalA2aRuntimeHost.port(8080));
```

runtime 关心的是：

```text
能运行 handler。
能提供 A2A 接入。
能注入 ToolService / MemoryService / StateService / SandboxService。
能把 agent 输出转换为 AgentResponse。
```

runtime 不关心：

```text
业务应用如何从 YAML 创建 handler。
业务应用是否固定写死 agent。
业务应用是否用自己的配置系统。
```

---

## 14. 验收标准

### 14.1 删除验收

源码中不再存在：

```text
runtime/access/protocol/async
runtime/dispatch
runtime/bootstrap
runtime/engine/runtime
runtime/**/2026-*.md
```

文档中不再把这些作为目标架构：

```text
agent-service 部署包设计
agent-runtime-sdk 顶层模块
middleware 顶层模块
旧设计约束
```

### 14.2 目录验收

```text
common/access/session/queue/control/engine/app 目录存在。
五个业务层是 access/session/queue/control/engine。
common 和 app 不承载业务分层。
session 不存在 store 子包。
queue 不存在 api/memory/manage 子包。
control 不存在 queue/state/worker 子包。
engine 只为 api/spi/service/openjiuwen 建子包。
```

### 14.3 access 验收

```text
A2A message/send -> AgentRequest -> AgentResponse(FINAL)
A2A message/stream -> AgentResponse(TASK/DELTA/FINAL)
tasks/get -> task view
tasks/cancel -> control cancel
OutputChannel 正常关闭，无资源泄露
access 不暴露 Queue 类型 API
```

### 14.4 session 验收

```text
RuntimeSession 与 AgentSession 类型隔离
in-memory 首版支持 text history
RuntimeSession 只保存 checkpointRef/agentStateRef，不保存状态本体
多轮 A2A 请求能通过 sessionId 恢复上下文
RuntimeSessionRepository 是接口
InMemoryRuntimeSessionRepository 是首版实现
不存在 session/store 子包
```

### 14.5 queue 验收

```text
queue 层无业务类型依赖
queue 目录平铺，不拆 api/memory/manage 子包
access/control/engine 可在内部使用 QueueFactory 创建队列
access/control/engine 不暴露任何 Queue 类型 API
跨层不传递队列实例
```

### 14.6 control 验收

```text
submit/resume/cancel 是强类型 API
不暴露 publish(TaskEvent)
TaskControlWorker 独占推进状态机
control 调 engine 时只传最小 EngineExecutionRequest
control 不依赖 Spring Boot
control 不依赖 openJiuwen
```

### 14.7 engine 验收

```text
engine 只维护 execution queue
EngineWorker 消费队列并调用 openJiuwen adapter
engine 同步调用 control/access API 回写
ToolProvider 通过 ToolService 注册后可被 openJiuwen adapter 包装为 Tool
MemoryService/StateService/SandboxService 只供 agent adapter 使用
engine core 不依赖 Spring Boot
engine.openjiuwen 是唯一允许依赖 openJiuwen 的位置
```

### 14.8 app 验收

```text
RuntimeApp.create(handler).run(host) 能启动 runtime。
RuntimeApp / RuntimeHost 本身不依赖 Spring Boot。
LocalA2aRuntimeHost 可以依赖 HTTP stack，但依赖不泄露到 common/session/queue/control/engine core。
```

### 14.9 openJiuwen 验收

```text
ReActAgent blocking 通过
ReActAgent streaming 通过
Runner.release 在 terminal response 后执行
ToolProvider -> openJiuwen Tool 调用通过
MemoryService 不污染 RuntimeSession
checkpointRef 可用于 resume 链路
```

### 14.10 端到端样例验收

`examples/agent-runtime-a2a-llm-e2e` 是本轮重构的标准验收样例，不是可选 demo。验收方式要模拟 terminal 用户调用：

```text
terminal -> A2A client -> /a2a message/send 或 message/stream -> agent-runtime -> openJiuwen -> A2A response/SSE
```

必须覆盖两组最小对话：

```text
user: ping
agent: pong

user: hello
agent: hello
```

验收要求：

```text
示例只通过 A2A 入口调用，不绕过 access/control/engine。
ping/pong 验证单轮请求闭环。
hello/hello 验证第二轮请求和 session 恢复不破坏输出。
streaming 模式下 terminal 能看到 AgentResponse(TASK/DELTA/FINAL) 顺序输出。
blocking 模式下 terminal 能看到单个 AgentResponse(FINAL)。
```

---

## 15. 最终方法

这次重构按以下顺序做：

1. 删除 `async`、`dispatch`、`bootstrap`、`engine/runtime` 这些错误目录概念。
2. 建立目录：`common/access/session/queue/control/engine/app`。
3. 定义 `AgentRequest` / `AgentResponse`，打通 A2A 阻塞和流式。
4. 定义 `RuntimeSession`，明确它不是 AgentSession。
5. 定义 `RuntimeSessionRepository` 接口与 `InMemoryRuntimeSessionRepository` 实现，不建 `store` 子包。
6. 定义无业务 `queue` 能力，供 access/control/engine 各自内部使用。
7. 定义 `TaskControlApi` 强类型 API 和内部 `TaskControlSignal`。
8. 定义 `EngineExecutionApi` 和 engine execution queue。
9. 定义 engine SPI/service/provider：handler、adapter、tool、memory、state、sandbox。
10. 定义 `RuntimeApp` / `RuntimeHost` / `LocalA2aRuntimeHost`，补齐 app.run 能力。
11. 用 openJiuwen `ReActAgent` 完成首个 adapter。
12. 用 A2A E2E 验证：terminal -> access -> control -> engine -> openJiuwen -> access response。

完成后的 agent-runtime 只有一个清晰模型：

```text
terminal A2A request
  -> common.AgentRequest
  -> control.TaskControlApi
  -> control internal queue/state machine
  -> engine.EngineExecutionApi
  -> engine internal execution queue
  -> openJiuwen adapter
  -> common.AgentResponse stream
  -> access OutputChannel
  -> A2A JSON / SSE
```

这就是本轮 agent-runtime 重构的实施方法。