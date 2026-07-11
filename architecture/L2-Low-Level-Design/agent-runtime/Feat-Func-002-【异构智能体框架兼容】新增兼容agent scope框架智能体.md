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
> 最后更新：2026-07-11

说明：本文是 Feat-Func-002 在 openJiuwen 社区 `agent-runtime-java` 扩展仓`agent-solution`中的本地 AgentScope 子设计。L1 中立角色 `AgentRuntimeHandler` 在本实现中映射为 `com.openjiuwen.service.spec.spi.AgentHandler`；adapter 只产出结果/中断/失败语义，不直接写 A2A `TaskStore` 或成为第二个 Task 生命周期 owner。

---

## 1. 概述

### 1.1 特性定位

本特性在 OpenJiuwen runtime 与进程内 AgentScope Java agent 之间提供双向透明的协议适配层。

适配前，runtime 只能调用 `AgentHandler`，AgentScope 使用 `RuntimeContext`、`Msg`、`Mono<Msg>` 和 `Flux<AgentEvent>`；适配后，宿主构建好的 `ReActAgent` 或 `HarnessAgent` 可以通过 runtime 现有 Query/A2A 入口调用。

适用场景是同一 JVM 内复用业务已经构建好的 AgentScope agent。不适用于自动创建 AgentScope agent、启动 AgentScope HTTP 服务或扩展其部署能力。AgentScope Java 兼容基线为 `2.0.0`，开发验证使用本地最新代码。

### 1.2 核心设计原则

1. **双向透明** — 上层只看到 runtime/A2A 通用协议，下层 AgentScope agent 不感知 runtime 类型。
2. **状态归原框架所有** — AgentScope `AgentState` 保存会话和 pending tool，runtime `TaskStore` 保存 A2A Task；adapter 不建立第二套状态。
3. **强类型适配** — 分别适配 `ReActAgent` 和 `HarnessAgent`，避免反射和 deprecated 流接口。
4. **最小 runtime 改动** — runtime 只补充通用 interaction 输出透传和已有 A2A Task 内部标记，不引用 AgentScope 类型。
5. **显式恢复** — 仅已有 A2A Task 的第二轮消息可触发恢复；确认只接受精确控制词，禁止从自然语言猜测执行动作。

### 1.3 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| ReAct 适配 | 调用 `ReActAgent.call/streamEvents` | `ReActAgentScopeInvoker` | ✅ |
| Harness 适配 | 调用 `HarnessAgent.call/streamEvents` | `HarnessAgentScopeInvoker` | ✅ |
| 请求/结果映射 | runtime DTO 与 AgentScope 类型转换 | request/event mapper | ✅ |
| Reactor 桥接 | 将 Mono/Flux 桥接到同步 `AgentHandler` | `AgentScopeAgentHandler` | ✅ |
| 中断与恢复 | 通用 interaction 与 AgentScope 原生恢复对象转换 | interaction/resume mapper | ✅ |
| 生命周期取消 | runtime interrupt 定向取消 AgentScope 调用 | `AgentInterruptHandler` | ✅ |

---
## 2. 功能规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| 非流式调用 | ✅ | `query()` 调用 AgentScope `call(...)` 并返回 `QueryResponse` |
| 流式调用 | ✅ | `streamQuery()` 消费 `streamEvents(...)` 并输出 `QueryChunk` |
| ReAct/Harness 支持 | ✅ | 同一模块提供两个强类型 invoker |
| 文本消息映射 | ✅ | 每次只传当前用户轮次，历史由 AgentScope state 持有 |
| 运行中取消 | ✅ | 调用 ReAct 的 session 定向 `interrupt`；Harness 通过 delegate 使用同一能力 |
| 通用暂停 | ✅ | `RequestStopEvent` 或最终 stop reason 映射为 runtime interrupt |
| 人工确认恢复 | ✅ | `RequireUserConfirmEvent` 经 A2A 转换为 `ConfirmResult` |
| 外部执行恢复 | ✅（条件支持） | 专用事件可映射；当前实际生产路径由 `AgentResultEvent(TOOL_SUSPENDED)` 兜底 |

### 2.2 显式排除

| 排除项 | 原因 | 替代（如有） |
|--------|------|------------|
| 自动构建 AgentScope agent | model、toolkit、workspace、state store 配置属于宿主职责 | 宿主构建 agent bean 后注入 adapter |
| 自动注册 `AgentHandler` | 同一应用可能存在多个 agent，自动选择会产生歧义 | 业务显式声明 `AgentScopeAgentHandler` bean |
| AgentScope 自启动 HTTP 服务 | 本特性是本地 Java adapter | 使用 runtime 现有 HTTP/A2A 入口 |
| WebFlux `/v1/query/reactive` | 当前入口会在响应式线程调用同步 handler | 使用 MVC Query 或 A2A |
| 非 A2A 的中断恢复 | MVC Query 没有 A2A Task/`INPUT_REQUIRED` 上下文 | 仅 A2A JSON-RPC 支持确认和工具结果恢复 |
| 从自然语言推断确认动作 | “同意”“可以”等语义不稳定，可能误执行工具 | A2A 确认续轮只接受精确的 `APPROVE/REJECT` |
| 修改工具参数和 permission rules | 属于 AgentScope 特有确认能力，会向上泄露框架语义 | 只允许批准/拒绝原始 pending tool |
| 完整 session reset | runtime `clearSession` 缺少 AgentScope user/session 完整身份和缓存清理能力 | 使用新 conversation |
| OpenJiuwen rail/middleware 叠加 | AgentScope 调用不进入 Agent Core Runner | 使用 AgentScope 自身 middleware/tool/state |
| 多模态完整映射 | 首版以文本为主 | 后续按通用 content block 契约扩展 |

### 2.3 接口契约（Logical View）

#### 2.3.1 SPI / API 声明

adapter 实现 runtime 现有 SPI，不新增对外 Java SPI：

```java
public final class AgentScopeAgentHandler
        implements AgentHandler, AgentInterruptHandler {

    public QueryResponse query(ServeRequest request);

    /**
     * @implSpec 必须等待 AgentScope Flux terminal 后返回；
     * terminal 后禁止继续写 observer。
     */
    public void streamQuery(ServeRequest request, QueryStreamObserver observer);

    public void interrupt(String conversationId, InterruptReason reason);

    public static AgentScopeAgentHandler forReActAgent(ReActAgent agent);

    public static AgentScopeAgentHandler forHarnessAgent(HarnessAgent agent);
}
```

#### 2.3.2 数据类型

| 类型 | 关键字段 | 含义 | 约束 |
|------|---------|------|------|
| `ServeRequest` | `conversationId`, `userId`, `messages`, `a2aTaskResume`, `metadata["_a2a_resume_interaction_kind"]` | runtime 调用输入 | `conversationId` 非空；resume 标记和上一轮 interaction kind 只由 runtime 从已有 Task 设置 |
| `RuntimeContext` | `sessionId`, `userId`, attributes | AgentScope 调用上下文 | `sessionId = conversationId` |
| `QueryChunk` | `type`, `data` | runtime 流输出 | interrupt 使用 `TYPE_INTERRUPT` |
| `AgentEvent` | `type` | AgentScope 所有流事件的通用基类 | 不是中断专用基类 |
| interaction | `type`, `kind`, `message`, `items` | 框架中立暂停描述 | 不包含 AgentScope Java 类型 |
| resume message | 当前 user message `content` | A2A 恢复输入 | 确认仅接受 `APPROVE/REJECT` |
| `AgentState` | `context` | AgentScope 恢复状态 | 只读；adapter 不持久化 |

通用 interrupt data：

```json
{
  "type": "__interaction__",
  "version": "1.0",
  "kind": "confirmation",
  "message": "The following operation requires confirmation.",
  "items": [
    {
      "type": "tool_call",
      "name": "tool-name",
      "arguments": {}
    }
  ],
  "context": {
    "_interrupt_kind": "ask_user"
  }
}
```

顶层 `kind` 是面向 A2A 客户端的通用暂停语义；`context._interrupt_kind` 是 runtime 现有 `A2AEnabledServeOrchestrator` 的内部路由标记，本 adapter 的人工交互固定使用 `ask_user`，不用于恢复匹配。`RequireUserConfirmEvent.replyId` 和 `ToolUseBlock.id` 均为 AgentScope 内部关联信息，adapter 不向客户端透传。

A2A 确认恢复直接使用同一 Task 的 user message，不要求客户端填写 metadata：

```json
{
  "taskId": "task-id",
  "contextId": "context-id",
  "parts": [{"text": "APPROVE"}]
}
```

#### 2.3.3 行为承诺

- **必须**将同一 `conversationId` 稳定映射为同一 AgentScope `sessionId`，并传递当前 `userId`。
- **必须**只把当前用户轮次写入 AgentScope，禁止重复灌入客户端携带的完整历史。
- **必须**让 AgentScope 管理 `AgentState`，让 runtime 管理 A2A Task；adapter 不保存 pending interaction。
- **必须**仅在 runtime 标记 `a2aTaskResume=true` 时进入恢复判断；普通 Query 和新 A2A Task 不得触发恢复。
- **必须**保证同一 A2A Task 任一时刻至多存在一个待恢复 interaction；一个 confirmation interaction 可以包含多个 ASKING tool，单次 `APPROVE/REJECT` 统一作用于全部 ASKING tool。
- **必须**由 adapter 从当前 `AgentState` 读取 pending `ToolUseBlock.id` 并构造 AgentScope 原生恢复对象，客户端不回传也不感知该 ID。
- **必须**仅将 `REQUEST_STOP`、`REQUIRE_USER_CONFIRM`、`REQUIRE_EXTERNAL_EXECUTION` 及最终 `AgentResultEvent` 中明确的可恢复 `GenerateReason` 识别为暂停；不得把 `AgentEvent` 基类或任意停止原因等价为中断。
- **必须**让 ReAct 与 Harness 暴露相同的取消和暂停语义；Harness 通过公开 delegate 复用 ReAct 实现。
- **必须**在超时、取消和异常后释放订阅并保证 terminal callback 至多一次。
- **禁止**把“同意”“可以”“confirm”等自然语言猜测为确认动作；只有已有 A2A Task 中的精确 `APPROVE/REJECT` 可以构造 `ConfirmResult`。
- **禁止**让 runtime DTO、TaskStore 或控制器依赖 AgentScope 类型。
- **允许**不输出 thinking/tool/final 细粒度事件；首版只保证 answer、interrupt 和 error。

---
## 3. 模块结构（Development View）

### 3.1 包结构

```text
agent-service-adapters-agentscope/
    ├── pom.xml
└── src/main/java/com/openjiuwen/service/adapters/agentscope/agentfw/
    ├── AgentScopeAgentHandler.java          // runtime SPI 实现
    ├── AgentScopeInvoker.java               // ReAct/Harness 内部调用契约
    ├── ReActAgentScopeInvoker.java          // ReAct 强类型适配
    ├── HarnessAgentScopeInvoker.java        // Harness 强类型适配
    ├── AgentScopeRequestMapper.java         // ServeRequest -> RuntimeContext/Msg
    ├── AgentScopeEventMapper.java           // AgentEvent -> QueryChunk
    └── AgentScopeResumeMapper.java          // A2A 续轮消息 -> 原生恢复对象
```

模块加入 `common/agent-runtime-ext-java/pom.xml`。功能所需直接依赖为 `agent-service-spec`、`agentscope-core`、`agentscope-harness`、Reactor 和 SLF4J；AgentScope 版本使用 `2.0.0`。`agentscope-harness` 是普通 compile 依赖，因为公开工厂签名直接引用 `HarnessAgent`。

### 3.2 核心类静态关系

```text
                     «runtime SPI»
             AgentHandler + AgentInterruptHandler
                           ↑ implements
                           │
               AgentScopeAgentHandler
                 │                  │
                 │ delegates        │ uses
                 v                  v
          AgentScopeInvoker       mappers
             ↑         ↑          request/event/resume
             │         │
 ReActAgentScopeInvoker  HarnessAgentScopeInvoker
             │                       │
             v                       v
         ReActAgent          HarnessAgent -> ReAct delegate
```

`AgentScopeInvoker` 是模块内部接口，只统一 `call/stream/interrupt`；不通过反射把两个 agent 压成 AgentScope 的 deprecated `Agent.stream(...)`。

---
## 4. 核心设计（Logical + Process View）

### 4.1 请求与结果适配

#### 4.1.1 关键处理流程

```text
Runtime                     AgentScopeAgentHandler            AgentScope
  │                                  │                           │
  │── ServeRequest ─────────────────>│                           │
  │                                  │── map RuntimeContext      │
  │                                  │── map current Msg         │
  │                                  │── call/streamEvents ─────>│
  │                                  │<──── Msg/AgentEvent ──────│
  │                                  │── map response/chunk      │
  │<── QueryResponse/QueryChunk ─────│                           │
```

请求映射规则：

- `conversationId -> RuntimeContext.sessionId`，`userId -> RuntimeContext.userId`。
- `tenantId`、`spaceId` 和无敏感内容的 trace/request id 可进入 attributes；完整 `ServeRequest.metadata` 和 request 对象禁止进入 AgentScope tool context。
- `a2aTaskResume` 只用于 adapter 选择恢复分支，不写入 `RuntimeContext.attributes`。
- 从 `ServeRequest.messages` 尾部选择最后一条有效 user 消息；没有 user 时使用最后一条有效消息。
- Query 结果默认返回 `{role:"assistant", content:"..."}`。

事件映射规则：

| AgentScope 事件 | runtime 输出 | 说明 |
|----------------|-------------|------|
| `TextBlockDeltaEvent` | `TYPE_CHUNK/answer_delta` | 输出文本增量 |
| `AgentResultEvent` | 正常完成或暂停兜底 | `PERMISSION_ASKING`、`TOOL_SUSPENDED`、三类可恢复 stop reason 在专用事件未输出时补发 interrupt，并按单次 stream 去重 |
| `ExceedMaxItersEvent` | 不结束 | 继续等待 summary 和最终结果 |
| standalone `RequestStopEvent` | `TYPE_INTERRUPT/message` | 普通续轮 |
| `RequireUserConfirmEvent` | `TYPE_INTERRUPT/confirmation` | A2A 消息式恢复 |
| external tool pending/event | `TYPE_INTERRUPT/tool_result` | 能与 pending tool 匹配时支持恢复 |
| 未识别事件 | 默认不输出 | 当前实现静默忽略，不产生 chunk 或 terminal 信号 |

流式路径从 `AgentEvent` 生成 interrupt；非流式 `query()` 在 `call(...)` 返回后检查最终 `Msg.generateReason` 和当前 `AgentState`，发现 standalone stop、ASKING tool 或 external pending tool 时，在 `QueryResponse.result._interrupt` 中返回相同的通用 interaction。两条路径必须保持同一协议结构。

非流式路径先检查当前 `AgentState`，再结合 `GenerateReason` 判定，不能只依赖枚举值：

| `GenerateReason` / 状态 | 非流式行为 |
|--------------------------|-----------|
| `PERMISSION_ASKING` 且存在 ASKING `ToolUseBlock` | `kind=confirmation` |
| `TOOL_SUSPENDED` 且存在 external pending tool | `kind=tool_result` |
| `MIDDLEWARE_STOP_REQUESTED`、`REASONING_STOP_REQUESTED`、`ACTING_STOP_REQUESTED` | `kind=message` |
| `ALL_TOOLS_DENIED` | 终止结果；用户已拒绝全部工具且 middleware/hook 决定停止，不再次生成 interaction |
| `INTERRUPTED` | 取消终态，不生成业务 interaction |
| `MODEL_STOP`、`STRUCTURED_OUTPUT`、`MAX_ITERATIONS` | 不生成 interaction，进入正常结果路径 |
| `TOOL_CALLS` 或枚举值与 AgentState 不一致 | 不猜测恢复语义，按未识别终态失败 |

`ReActAgent` 和 `HarnessAgent` 分别由强类型 invoker 调用。Harness 的中断和状态读取通过其公开 `getDelegate()` 访问内部 ReAct delegate；adapter 不使用反射或 deprecated `Agent.stream(...)`。

### 4.2 Reactor 桥接与取消

#### 4.2.1 关键处理流程

`query()` 订阅 `Mono<Msg>` 并等待结果；`streamQuery()` 订阅 `Flux<AgentEvent>` 并保持方法不返回，直到 complete、error、timeout 或 cancel。

handler 使用 `ConcurrentHashMap<String, Set<ActiveInvocation>>` 按 `conversationId` 跟踪在途调用；使用集合而不是单值，是为了避免同一 conversation 的并发调用互相覆盖。每个 `ActiveInvocation` 只保存本次调用取消所需的 `userId`、`sessionId`、Reactor subscription 和 terminal 原子标志，不保存 AgentScope 会话、interaction 或恢复状态。

为避免“subscription 已启动但 invocation 尚未注册”时漏掉取消，handler 必须先创建并注册 `ActiveInvocation`，再建立 subscription 并通过原子引用绑定；如果绑定前 invocation 已被取消，绑定后立即 dispose。complete、error、timeout 和 cancel 通过原子 closed 标志竞争唯一终态，并在统一 `doFinally`（或等价 `finally`）路径中移除 invocation；集合为空时一并删除 conversation 条目。

`AgentInterruptHandler.interrupt(conversationId, reason)` 取消该 conversation 下的全部 invocation：先将 closed 标志置位，再按各 invocation 保存的 `userId/sessionId` 调用 AgentScope 定向 `interrupt`，随后 dispose subscription 并移除记录。重复取消、绑定前取消和迟到的 terminal 信号必须幂等。

```text
Runtime/client             AgentScopeAgentHandler             AgentScope
  │                                  │                           │
  │── cancel/interrupt ─────────────>│                           │
  │                                  │── interrupt(user,session)>│
  │                                  │── dispose subscription    │
  │                                  │── wake waiting call       │
  │<── cancelled/error terminal ─────│                           │
```

超时、客户端断开和 `AgentInterruptHandler.interrupt` 走同一清理路径。取消后到达的 event/result 必须丢弃；observer 的 `onComplete/onError` 至多调用一次。

兼容基线 2.0.0 中，同一 `ReActAgent` 实例可以服务多个 `(userId, sessionId)`；同一 slot 的调用由 AgentScope 串行化，不同 slot 可以并行。adapter 不增加实例级全局锁，也不要求为每个请求创建 agent。

本地最新 `ReActAgent` 的类头 Javadoc 仍保留“单实例不支持并发”的旧描述，与当前实现不一致。本设计以可执行代码为准：`AgentBase.serializeOnKey(...)` 按 key 串行化，`ReActAgent.callSerializationKey(...)` 返回 `(userId, sessionId)` slot key；上游并发回归测试同时覆盖“同 slot 串行、不同 slot 并行”。

### 4.3 中断与恢复

#### 4.3.1 中断分类与支持边界

AgentScope 中与“中断”相关的机制分为两类，不能混用：

1. **运行中取消 API**：`ReActAgent.interrupt(userId, sessionId)` 触发当前 slot 的 `InterruptControl`，用于结束当前调用。它不是 `AgentEvent`。
2. **可恢复暂停事件**：通过 `streamEvents(...)` 输出，要求当前调用结束并等待下一轮输入。

`InterruptSource.USER/TOOL/SYSTEM` 描述 AgentScope 内部取消来源，也不是暂停事件类型。当前公开的 session 定向 `interrupt(userId, sessionId)` 按 USER 触发；adapter 保证取消效果，不承诺把 runtime timeout/shutdown reason 精确映射为 AgentScope `InterruptSource`。

暂停事件没有共同的中断基类。`RequestStopEvent`、`RequireUserConfirmEvent` 和 `RequireExternalExecutionEvent` 都直接继承通用 `AgentEvent`；而 `AgentEvent` 还包含文本、模型、工具等非中断事件。因此 adapter 按具体事件类型（Java `instanceof`）和最终 `GenerateReason` 白名单支持，不对 `AgentEvent` 基类或任意停止原因做兜底中断映射。

| AgentScope 机制 | 类型/入口 | ReAct | Harness | adapter 支持边界 |
|----------------|----------|-------|---------|------------------|
| 运行中取消 | `interrupt(userId, sessionId)` | 原生支持 | 通过 `getDelegate()` 使用同一 ReAct 能力 | 支持 session 定向取消，不生成 `INPUT_REQUIRED`；不保留 source 细分语义 |
| 普通暂停 | `RequestStopEvent` | 可由 middleware 发射 | delegate 事件原样可见 | 支持，统一映射为 `kind=message`；不保留预算/审计等细分原因 |
| 人工确认 | `RequireUserConfirmEvent` | permission ASK 路径稳定发射 | delegate 事件原样可见 | 支持，映射为 `kind=confirmation`；仅 A2A 消息式恢复 |
| 外部执行 | `RequireExternalExecutionEvent` | 公共类型存在，当前未发现稳定生产发射点 | 与 ReAct 相同 | 条件支持；收到事件且 pending tool 可匹配时映射，否则失败 |
| 确认/执行结果通知 | `UserConfirmResultEvent`、`ExternalExecutionResultEvent` | 结果事件 | 与 ReAct 相同 | 不作为中断；不推进 `INPUT_REQUIRED` |
| 全部工具已拒绝终止 | `ALL_TOOLS_DENIED` | 用户拒绝全部工具后由 middleware/hook 选择终止 | 与 ReAct 相同 | 正常终止，不再次进入 `INPUT_REQUIRED` |
| 其他 `AgentEvent` | text/model/tool/custom 等 | 普通流事件 | delegate 事件原样可见 | 不作为中断，按事件映射策略输出或忽略 |

ReAct 与 Harness 的对外支持矩阵一致。差异只在调用入口：ReAct invoker 直接调用 `ReActAgent`，Harness invoker 调用 `HarnessAgent`，定向取消和状态读取使用其公开 `getDelegate()`。

#### 4.3.2 关键处理流程

暂停事件输出规则：

- `RequireUserConfirmEvent` 携带 `List<ToolUseBlock>`；adapter 转为 `kind=confirmation`。
- 去重状态属于单次 invocation/stream，每次 `streamQuery()` 重置；收到 `RequireUserConfirmEvent` 后，随后的 `RequestStopEvent(PERMISSION_ASKING)` 语义重复，adapter 不再输出第二个 interrupt。非流式路径只检查最终 `Msg/AgentState`，不做事件级去重。
- standalone `RequestStopEvent` 转为 `kind=message`。
- `RequireExternalExecutionEvent` 仅在携带的 tool call 能与 AgentState pending tool 匹配时转为 `kind=tool_result`。当前源码未发现该事件的生产发射点，实际 external tool 暂停由最终 `AgentResultEvent(TOOL_SUSPENDED)` 兜底，并从 AgentState 重建 interaction；若未来两者同时出现，单次 stream 只输出一次。
- legacy `PostReasoningEvent.stopAgent()` / `PostActingEvent.stopAgent()` 不保证发 standalone `RequestStopEvent`，由最终 `AgentResultEvent(REASONING_STOP_REQUESTED/ACTING_STOP_REQUESTED)` 兜底输出 `kind=message`。

A2A 链路：

```text
AgentScope               Adapter                  Runtime/A2A              Client
   │                        │                          │                       │
   │── pause event ────────>│                          │                       │
   │                        │── TYPE_INTERRUPT ───────>│                       │
   │                        │                          │── INPUT_REQUIRED ─────>│
   │                        │                          │   metadata.interaction │
   │                        │                          │<── APPROVE/REJECT ─────│
   │                        │<── ServeRequest          │                       │
   │                        │    a2aTaskResume=true    │                       │
   │                        │── read AgentState        │                       │
   │                        │── native resume ────────>│ AgentScope            │
```

runtime 侧改动限定在现有 `A2AAgentExecutor` 的通用 A2A 转换中：

1. 新增：修改 `A2AAgentExecutor.toStatusMessage()` 和 `toStatusMessageFromMap()`，把 interrupt data 放入 A2A status `Message.metadata["interaction"]`，不再只保留展示文本；同时覆盖流式和非流式中断测试。
2. 保持现有 `TaskStore` 的 `INPUT_REQUIRED` 生命周期。
3. 复用 `A2AAgentExecutor` 已有的 `isResume = RequestContext.getTask() != null` 判定，写入内部 `ServeRequest.a2aTaskResume`；同时从已有 Task 取出上一轮通用暂停类型，写入内部 `ServeRequest.metadata["_a2a_resume_interaction_kind"]`。读取顺序为当前 status `Message.metadata["interaction"].kind` 优先；若 A2A SDK 已在接收续轮消息时把上一轮 status message 移入 history，则从 history 逆序选择最近一条 agent interaction。两者均不属于客户端契约，普通 Query 不设置，也无需修改 A2A controller、message context 或 protocol adapter。

第 1 项让客户端获得通用暂停描述，第 3 项严格限定只有原 A2A Task 的续轮才能恢复。两项都不保存 AgentScope event、pending ID 或 checkpoint，也不引入 AgentScope 类型。

runtime 不保存 AgentScope event、pending ID 或 checkpoint。TaskStore 保存第一轮已有的通用 interaction；A2A SDK 可能在续轮开始前将该 status message 移入 Task history，因此 runtime 从 status/history 读取 `kind` 并作为内部判别信息带给 adapter，解决普通暂停和 external tool 都可能表现为非 ASKING pending tool、仅看 AgentState 无法区分的问题。adapter 再通过 `conversationId=contextId` 定位相同 AgentScope session，并调用 `ReActAgent.getAgentState(userId, sessionId)` 读取真实 pending `ToolUseBlock`：ASKING 状态用于确认，external pending 的 id/name 用于结果回灌。Harness 使用 `getDelegate()` 取得同一 ReAct state。

“同一 Task 只有一个中断”指同一时刻只有一个待恢复 interaction，不等于只能有一个 tool call。confirmation interaction 内可以包含多个 ASKING tool；adapter 对这些 tool 构造同一布尔值的 `ConfirmResult` 列表。客户端只表达一次批准或拒绝，不逐项选择，也不提供任何 item ID。

| 当前状态 | 输入 | adapter 行为 | 结果 |
|---------|------|-------------|------|
| standalone stop | A2A 同 Task 续轮消息 | 消息只作为续轮触发信号；adapter 调用 `agent.call()` 空消息列表，不读取或写入消息正文 | AgentScope 从 hook/middleware 暂停点继续执行 |
| ASKING tool | 精确 `APPROVE/REJECT` 消息 | 为当前全部 ASKING tool 构造 `ConfirmResult` metadata | 整体批准或拒绝原始 tool call |
| ASKING tool | 其他文本 | 不猜测自然语言动作 | 拒绝恢复，不调用工具 |
| 单个 external tool pending | 任意非空结果消息 | 使用当前 pending tool 的内部 id/name 构造 `ToolResultBlock` | 回灌工具结果 |
| 多个 external tool pending | 任意消息 | 无法把单条消息无歧义映射为多个结果 | 拒绝恢复 |
| 无 pending 状态 | `APPROVE/REJECT` | 视为过期确认，不降级成普通消息 | 拒绝恢复 |
| 无 pending 状态且上一轮 `kind=message` | 其他消息 | 调用 `agent.call()` 空消息列表 | AgentScope 继续执行 |

恢复输入必须由 resume mapper 构造成独立的 AgentScope `Msg`，不能把 A2A 消息中的 `APPROVE/REJECT` 作为普通用户内容传给 AgentScope，也不能把恢复对象与普通 user query 合并。

resume mapper 在构造消息前必须校验：请求由 runtime 标记为已有 A2A Task 续轮，且携带 TaskStore 中上一轮通用 interaction kind；confirmation 只接受精确 `APPROVE/REJECT`；tool_result 的 external pending tool 恰好一个且消息为非空文本；message 使用空消息列表恢复。任一校验失败时不调用 `agent.call(...)`，也不修改 AgentState。

确认恢复构造一个 metadata-only `UserMessage`。消息列表必须非空，因为 ASKING 路径从输入消息的 `Msg.METADATA_CONFIRM_RESULTS` 中提取 `ConfirmResult`：

```java
Msg resumeMsg = UserMessage.builder()
    .metadata(Map.of(
        Msg.METADATA_CONFIRM_RESULTS,
        confirmResults))
    .build();

agent.call(List.of(resumeMsg), runtimeContext);
```

外部工具结果恢复构造 `TOOL` role 消息，`ToolResultBlock` 放在 content 中而不是 metadata；block 的 id 和 name 使用当前 AgentState 中 pending `ToolUseBlock` 的值：

```java
ToolResultBlock resultBlock = ToolResultBlock.of(
    pendingTool.getId(),
    pendingTool.getName(),
    TextBlock.builder().text(resultText).build());

Msg resumeMsg = Msg.builder()
    .role(MsgRole.TOOL)
    .content(resultBlock)
    .build();

agent.call(List.of(resumeMsg), runtimeContext);
```

adapter 只读取 AgentScope 状态并构造本次输入，不直接修改或持久化 `AgentState`；状态变更由后续 `agent.call(...)` 内部完成。

---

## 5. 配置模型（Physical View）

### 5.1 完整配置示例

本特性不增加 YAML 配置项。AgentScope agent 的 model、toolkit、workspace 和 state store 仍由宿主代码构建。

```yaml
{}
```

### 5.2 配置属性表

| 属性路径（完整） | 类型 | 默认值 | 必填 | 说明 |
|-----------------|------|--------|------|------|
| 无 | - | - | - | 首版没有 adapter 配置项 |

### 5.3 配置类（如有）

首版不定义公开配置类。调用桥接使用 handler 内部默认值：

```java
public final class AgentScopeAgentHandler {
    private static final Duration QUERY_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);
}
```

这两个值不是公共契约。单元测试需要缩短 timeout 时，可使用 package-private 构造方法注入，不增加公开 options 类型。后续只有形成 YAML 绑定或多项稳定配置需求时，才引入独立 properties/options 类。

---
## 6. 对外呈现 / 用户场景（Scenario View）

### 6.1 外部接口

adapter 不新增 HTTP endpoint。

| 端点 / API | 方法 | 说明 |
|-----------|------|------|
| `POST /v1/query`、`POST /query` | HTTP POST | MVC Query 入口；支持普通调用和通用暂停输出 |
| `POST /a2a` | HTTP POST | A2A JSON-RPC；支持 Task/`INPUT_REQUIRED` 和消息式恢复 |
| `AgentScopeAgentHandler.forReActAgent` | Java | 注册 ReAct adapter |
| `AgentScopeAgentHandler.forHarnessAgent` | Java | 注册 Harness adapter |

### 6.2 用户示例

#### 6.2.1 注册 ReActAgent

前置条件：业务已经构建好 `ReActAgent`。

```java
@Bean
AgentScopeAgentHandler agentscopeAgentHandler(ReActAgent reactAgent) {
    return AgentScopeAgentHandler.forReActAgent(reactAgent);
}
```

预期结果：runtime 通过同一个 `AgentHandler` bean 提供 Query 和 A2A 调用。

#### 6.2.2 A2A 确认恢复

第一轮请求触发 ASKING tool 后，Task 状态为 `INPUT_REQUIRED`，status message metadata 包含通用 interaction。客户端使用同一 `taskId/contextId` 提交：

```json
{
  "jsonrpc": "2.0",
  "method": "SendMessage",
  "params": {
    "message": {
      "role": "ROLE_USER",
      "taskId": "task-id",
      "contextId": "context-id",
      "parts": [{"text": "APPROVE"}]
    }
  },
  "id": "request-id"
}
```

第一轮和第二轮的 Task 均位于 `result.task`；客户端分别检查 `result.task.status.state` 和 `result.task.artifacts`。预期结果：adapter 使用 AgentScope 当前 pending tool 构造 `ConfirmResult`，agent 继续执行；上层不接触 AgentScope 类型。

### 6.3 E2E 流程

```text
Client                    Runtime/A2A                 Adapter                 AgentScope
  │                           │                          │                        │
  │── request ───────────────>│── ServeRequest ────────>│── Msg/Context ───────>│
  │                           │                          │<── confirm event ──────│
  │<── INPUT_REQUIRED ────────│<── generic interaction─│                        │
  │                           │                          │                        │
  │── APPROVE + same task ───>│── ServeRequest ────────>│                        │
  │                           │  a2aTaskResume=true      │── read AgentState      │
  │                           │                          │── ConfirmResult ──────>│
  │                           │                          │<── final result ───────│
  │<── COMPLETED/result ──────│<── QueryResponse ───────│                        │
```

---
## 7. 错误处理（Process View）

| 错误场景 | 触发条件 | 行为 | 对外结果 |
|---------|---------|------|---------|
| 非法请求 | `conversationId` 缺失或无有效消息 | 不调用 AgentScope | runtime 失败响应 |
| query/stream 超时 | 超过 handler 内部 timeout | 取消订阅并定向 interrupt | timeout 失败终态 |
| 客户端取消 | observer cancelled | 停止输出并定向 interrupt | 不再发送 chunk |
| AgentScope 执行异常 | Mono/Flux error | 脱敏记录，清理在途调用 | runtime 失败终态 |
| 非法恢复输入 | 确认消息不是精确 `APPROVE/REJECT`，或 external pending 不唯一 | 不构造原生恢复对象，不调用工具 | Task 失败 |
| 恢复状态不存在 | AgentScope 无对应 pending state | 不使用客户端数据重建状态 | Task 失败 |
| 恢复类型不存在 | 已有 Task 的 status message 和 history 均缺少上一轮 interaction kind | 不根据非 ASKING pending tool 猜测普通暂停或外部执行 | Task 失败 |
| 非原 Task 尝试恢复 | runtime 未设置 `a2aTaskResume` | 不读取 pending 状态，按普通请求处理 | 不恢复旧中断 |
| 未知事件 | 不在首版映射表 | 静默忽略，不输出 chunk | 不影响正常结果 |

流式失败最多输出一个 error chunk，随后调用 `onError`；禁止 error 后再 `onComplete`。日志和错误响应不得包含完整 prompt、认证信息、工具敏感参数或 `AgentState`。

---
## 8. 限制与待补

| 限制 | 影响范围 | 临时方案（如有） |
|------|---------|----------------|
| 恢复仅支持原 A2A Task 的 JSON-RPC 续轮 | MVC Query 和新 A2A Task 不能闭环确认/外部工具结果 | 使用第一轮返回的 `taskId/contextId` 调用 `POST /a2a` |
| `RequestStopEvent` 细分原因被收敛 | 预算、审计、调试等原因统一表现为 message interaction | 客户端只依赖“暂停并继续”语义 |
| runtime 取消原因不映射为 `InterruptSource` | timeout/shutdown 等 reason 不进入 AgentScope 内部来源枚举 | adapter 仍执行 session 定向取消，并在 runtime 日志/trace 保留原 reason |
| `RequireExternalExecutionEvent` 当前无生产发射点 | external tool 实际通过最终 `AgentResultEvent(TOOL_SUSPENDED)` 暂停 | adapter 用 final event 兜底并与未来可能出现的专用事件去重；无法匹配 AgentState 时失败，不猜测 |
| 外部结果消息只支持一个 pending tool | 单条文本无法无歧义映射多个工具结果 | 多个 external pending 时拒绝恢复 |
| 恢复依赖 AgentScope pending state 仍存在 | 状态被清除后无法续接 | 重新发起新会话 |
| 缺少 interaction kind 的既有 `INPUT_REQUIRED` Task 不兼容 | 无法无歧义选择 message/tool_result 恢复构造 | 重新发起新 Task；不按 AgentState 猜测 |
| 文本优先 | 非文本 content block 可能降级 | 首版使用文本输入输出 |
| 不支持完整 session reset | `/reset_conversation` 不能保证清除 AgentScope 状态 | 使用新的 conversationId |
| 不支持 WebFlux Query | `/v1/query/reactive` 不可用 | 使用 MVC Query 或 A2A |
| 同步 SPI 桥接会占用执行线程 | 长流期间 handler 持续等待 Flux terminal | 由 runtime 现有后台 executor 承载 |

---
## 9. 验证与验收

验证命令：

```powershell
mvn -f "agent-solution\common\agent-runtime-ext-java\pom.xml" `
  -pl agent-service-adapters/agent-service-adapters-agentscope -am test
```

```powershell
mvn -f "agent-solution\common\agent-runtime-ext-java\pom.xml" `
  -pl agent-service-adapters/agent-service-adapters-agentscope `
  dependency:tree -Dscope=runtime
```

```powershell
mvn -f "agent-runtime-java\pom.xml" -pl service/agent-service-spec test
mvn -f "agent-runtime-java\pom.xml" -pl service/agent-service-app test
```

```powershell
mvn -f "agentscope-java\pom.xml" `
  -pl agentscope-core `
  "-Dtest=ReActAgentPerSessionStateTest,ReActAgentMiddlewareIntegrationTest" `
  test
```

```powershell
mvn -f "agent-solution\common\example\agentscope-a2a-interrupt-demo\pom.xml" `
  clean package
```

真实模型闭环按 `agent-solution\common\example\agentscope-a2a-interrupt-demo\README.md` 手工执行，不为 example 增加自动测试脚本。

最终落地验证结果：

| 验证项 | 结果 |
|---------|------|
| AgentScope adapter 单元测试 | 37 个通过 |
| AgentScope ReAct 并发/串行化定向回归 | 10 个通过 |
| runtime `agent-service-spec` 回归 | 12 个通过 |
| runtime `agent-service-app` 回归 | 132 个通过 |
| ReAct 真实 DeepSeek A2A 闭环 | `INPUT_REQUIRED -> APPROVE -> COMPLETED` |
| Harness 真实 DeepSeek A2A 闭环 | `INPUT_REQUIRED -> APPROVE -> COMPLETED` |

真实闭环验证时，第一轮 interaction 未暴露 AgentScope tool-call ID；第二轮仅回传同一 Task 的 `taskId/contextId` 和 `APPROVE` 消息，未携带 `params.metadata` 或 item ID。

验收要求：

1. ReAct 和 Harness 均能通过同一个 `AgentHandler` 实现完成 query、stream 和定向 interrupt。
2. stream 在 AgentScope Flux terminal 前不返回；complete、error、cancel 均只产生一次 terminal callback。
3. 每次请求只写入当前用户轮次，不重复写入历史消息。
4. `ExceedMaxItersEvent` 不被映射为 interrupt 或 error，也不阻断后续事件；adapter 不对 summary/final 做超出 AgentScope 后续事件的额外保证。
5. confirmation/tool-result interrupt 经 A2A 进入 `INPUT_REQUIRED`；只有同一 Task 的续轮消息能转换为 AgentScope 原生对象。
6. 客户端无需获得或回传 AgentScope item ID；过期 `APPROVE/REJECT` 和自然语言确认不会触发工具调用。
7. 仅 `RequestStopEvent`、`RequireUserConfirmEvent`、`RequireExternalExecutionEvent` 和 `AgentResultEvent` 中明确的可恢复 `GenerateReason` 可生成 interrupt；结果通知、`ALL_TOOLS_DENIED` 和其他 `AgentEvent` 不得误触发 `INPUT_REQUIRED`。
8. ReAct 与 Harness 通过相同用例验证取消、暂停和恢复语义，Harness 不产生独立分支协议。
9. runtime 只透传通用 interaction 并标记已有 A2A Task，不引用 AgentScope 类型，也不新增 interaction 状态存储。
10. 模块测试、dependency tree、runtime 回归和两个 example 打包通过；ReAct/Harness 按中文 README 完成真实模型手工闭环。
