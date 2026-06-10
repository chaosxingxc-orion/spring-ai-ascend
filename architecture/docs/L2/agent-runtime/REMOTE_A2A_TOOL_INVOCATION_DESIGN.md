# A2A 打穿前提下的远端 Agent 工具调用设计

本文定义 Agent Runtime 在全路径 A2A 化后的远端 agent 工具调用方案。全路径 A2A 化指：入口协议、内部任务事实源、事件队列、流式输出、任务查询、远端调用状态，都以 A2A `Task` / `TaskStore` / `EventQueue` / `TaskStatusUpdateEvent` / `TaskArtifactUpdateEvent` 为主线。

目标链路：

```text
A2A JSON-RPC ingress
  -> A2A TaskControl facade
  -> A2A TaskStore + EventQueue
  -> engine execution
  -> A2A task events
  -> A2A tasks/get or streaming egress
```

远端 agent 作为工具调用时，链路为：

```text
OpenJiuwen LLM tool_call
  -> injected OpenJiuwen ToolCard
  -> OpenJiuwenRemoteAgentInterruptRail
  -> RemoteAgentInvocationService
  -> RemoteInvocationControl
  -> parent A2A Task metadata + EventQueue
  -> RemoteAgentOutboundPort
  -> access.a2a outbound client
  -> remote A2A runtime
```

## 设计原则

1. A2A `Task` 是任务事实源，任务状态只以 A2A task status 和 metadata 表达。
2. A2A `EventQueue` 是 caller-visible 输出事实源，`tasks/get` 和 streaming 都从 A2A task/event 读取。
3. control 仍是状态权威，但它的写入目标是 A2A `TaskStore` 和 `EventQueue`。
4. access.a2a 只负责 A2A 协议 I/O，包括 inbound JSON-RPC、well-known AgentCard、outbound A2A client。
5. OpenJiuwen 的远端 tool/rail bridge 只通过 `RemoteAgentInvocationService` 调用远端，A2A JSON-RPC client、`TaskStore` 和 `EventQueue` 都由 runtime 边界组件持有。
6. 远端 terminal result 默认作为 tool result 回到 parent agent，不直接作为 parent caller-visible final output。
7. 远端 `input-required` 必须投影为 parent A2A task 的 `TASK_STATE_INPUT_REQUIRED`，下一轮用户输入必须路由回同一个 remote task/context。

## 核心组件

### A2aTaskControlService

`A2aTaskControlService` 是 control 层的 A2A-native 任务控制门面，负责：

- 从 A2A ingress 创建或恢复 parent A2A `Task`。
- 通过 `TaskStore` 保存 task。
- 通过 `EventQueue` 发布 task status/artifact 事件。
- 根据 task status 和 metadata 决定 run/resume/cancel 路由。
- 对 engine 和 remote invocation 的状态写入做统一仲裁。

它不拼 JSON-RPC 报文，不依赖 OpenJiuwen。

### A2aTaskEventPublisher

`A2aTaskEventPublisher` 负责把 runtime 内部事件写成 A2A event：

```text
TASK_STATE_SUBMITTED
TASK_STATE_WORKING
TASK_STATE_INPUT_REQUIRED
TASK_STATE_AUTH_REQUIRED
TASK_STATE_COMPLETED
TASK_STATE_FAILED
TASK_STATE_CANCELED
TASK_STATE_REJECTED
TaskArtifactUpdateEvent
```

优先复用 SDK `AgentEmitter` 的 public 方法；如果某些写法需要组合 `TaskStore` 与 `EventQueue`，封装在该 publisher 内部。

### A2aTaskMetadataRouter

`A2aTaskMetadataRouter` 只读取 A2A `Task.status` 与 `Task.metadata`，判断下一轮输入去向：

```text
LOCAL_AGENT
REMOTE_AGENT
REJECTED
```

它是 resume 分流的单一判断点。

### RemoteAgentInvocationService

`RemoteAgentInvocationService` 是被 OpenJiuwen 远端 tool/rail bridge 调用的中性服务接口。OpenJiuwen 侧只调用它，不依赖 A2A SDK。

职责：

- 接收 `RemoteAgentInvocationIntent`。
- 校验 remote agent、policy、timeout、parent task live 状态。
- 调用 `RemoteInvocationControl` 写 parent A2A task metadata/event。
- 调用 `RemoteAgentOutboundPort` 访问远端 A2A runtime。
- 把远端结果包装成 tool result 返回给 parent agent。

### RemoteInvocationControl

`RemoteInvocationControl` 是 control 层给 remote invocation service 的生命周期 API：

```java
interface RemoteInvocationControl {
    RemoteInvocationHandle open(OpenRemoteInvocationCommand command);

    void markSubmitted(RemoteInvocationHandle handle, RemoteInvocationCommands.Submitted command);

    void markInputRequired(RemoteInvocationHandle handle, RemoteInvocationCommands.InputRequired command);

    void markSucceeded(RemoteInvocationHandle handle, RemoteInvocationCommands.Succeeded command);

    void markFailed(RemoteInvocationHandle handle, RemoteInvocationCommands.Failed command);

    void markTimeout(RemoteInvocationHandle handle, RemoteInvocationCommands.Timeout command);

    void markCancelled(RemoteInvocationHandle handle, RemoteInvocationCommands.Cancelled command);
}
```

这些 command/handle 定义在 `control.api` 下，只使用 JDK 类型、A2A spec 类型和 runtime control 自己的枚举，不引用 `engine.openjiuwen`、`engine.service` 或 `access.a2a` JSON-RPC wrapper。实现时可以把 command 做成 `RemoteInvocationCommands` 中的嵌套 record，避免为每个生命周期动作创建一个只有字段的文件。`DefaultRemoteAgentInvocationService` 负责把 engine.spi 的 remote intent/result 转换成 control.api command。

实现规则：

- `open(...)` 在 parent A2A task metadata 中记录 remote invocation 关联。
- `markSubmitted(...)` 写 parent task `TASK_STATE_WORKING` event，并记录 remote task/context。
- `markInputRequired(...)` 写 parent task `TASK_STATE_INPUT_REQUIRED` event，status message 携带远端 prompt。
- `markSucceeded(...)` 保存 tool result，清理等待路由，触发 parent agent resume。
- `markFailed(...)` 默认保存 tool error；只有 fatal policy error 才写 parent `TASK_STATE_FAILED`。
- `markTimeout(...)` 默认保存 tool error，并按 policy 决定是否 cancel remote task。
- `markCancelled(...)` 清理 parent task metadata 中的 remote 等待路由。

### RemoteAgentOutboundPort

`RemoteAgentOutboundPort` 是访问远端 A2A runtime 的中性端口，由 `access.a2a` 实现。它不是 A2A client 本身，也不暴露 A2A JSON-RPC client / SDK client 类型；真正的 A2A client 放在 `A2aRemoteAgentOutboundAdapter` 内部。

这样拆分的原因：

- `engine.service.DefaultRemoteAgentInvocationService` 只需要表达“我要调用远端 agent”，不应该知道 JSON-RPC、HTTP、SDK client 类。
- `access.a2a` 是协议 I/O 边界，负责把 neutral request 转成 A2A `message/send`、`message/stream`、`tasks/get`、`tasks/cancel`。
- 将来如果 A2A client SDK 替换、HTTP client 替换、认证方式变化，只改 `access.a2a` adapter，不影响 engine/openjiuwen/control。
- 单元测试可以用 fake `RemoteAgentOutboundPort` 验证 control/engine 逻辑，不需要启动真实远端 A2A 服务。

```java
interface RemoteAgentOutboundPort {
    RemoteAgentResult invoke(RemoteInvocationHandle handle, RemoteAgentRequest request);

    RemoteTaskReference submit(RemoteInvocationHandle handle, RemoteAgentRequest request);

    RemoteAgentResult resumeInput(
            RemoteInvocationHandle handle,
            RemoteTaskReference remoteTask,
            List<Message> userInput);

    void cancel(RemoteInvocationHandle handle, RemoteTaskReference remoteTask);
}
```

`A2aRemoteAgentOutboundAdapter` 负责：

- 持有或创建真正的 A2A client。
- neutral request -> A2A `message/send` 或 `message/stream`。
- `submit(...)` -> A2A 非阻塞提交并解析 remote task/context。
- `resumeInput(...)` -> A2A 对同一 remote task/context 发送用户补充输入。
- `cancel(...)` -> A2A `tasks/cancel` 或远端支持的取消语义。
- A2A remote `TASK_STATE_INPUT_REQUIRED` -> `RemoteAgentResult.InputRequired`。
- A2A remote terminal success -> `RemoteAgentResult.Completed`。
- A2A remote terminal failure/protocol error -> `RemoteAgentResult.Failed`。

`invoke(...)` 与 `resumeInput(...)` 复用同一个 `RemoteAgentResult` 结果模型。resume 后远端仍然只会进入 `InputRequired`、`Completed`、`Failed`、`Timeout`、`Cancelled` 等状态，不需要为 resume 再拆一套结果类型。

实现形态：

```text
DefaultRemoteAgentInvocationService
  -> RemoteAgentOutboundPort
      implemented by access.a2a.A2aRemoteAgentOutboundAdapter
        -> A2A client / HTTP JSON-RPC client
        -> remote /a2a
```

## Parent A2A Task Metadata（父任务路由字段）

远端调用关联放在 parent A2A `Task.metadata` 中。统一 key 由 `A2aTaskMetadataKeys` 定义：

```json
{
  "runtime.waitingTarget": "REMOTE_AGENT",
  "runtime.remoteInvocationId": "rinv-123",
  "runtime.remoteAgentId": "weather-agent",
  "runtime.remoteTaskId": "remote-task-9",
  "runtime.remoteContextId": "remote-context-9",
  "runtime.toolCallId": "tool-call-7",
  "runtime.parentAgentId": "local-openjiuwen-agent"
}
```

字段含义：

| 字段 | 含义 |
| --- | --- |
| `runtime.waitingTarget` | 下一轮用户输入路由目标，取值 `LOCAL_AGENT` 或 `REMOTE_AGENT`。 |
| `runtime.remoteInvocationId` | 本地 remote invocation 关联 id。 |
| `runtime.remoteAgentId` | 被调用的远端 agent id。 |
| `runtime.remoteTaskId` | 远端 A2A task id。 |
| `runtime.remoteContextId` | 远端 A2A context id。 |
| `runtime.toolCallId` | parent agent 内部工具调用 id。 |
| `runtime.parentAgentId` | 发起远端调用的本地 agent id。 |

`RemoteInvocationIndex` 只做索引加速，不做状态事实源：

```text
remoteInvocationId -> parentTaskId, parentContextId, remoteAgentId, remoteTaskId, remoteContextId, toolCallId
```

状态事实源始终是 parent A2A `Task`、remote A2A task reference 和 A2A `EventQueue`。

## 远端 Card 发现与工具注入

远端 runtime 暴露：

```text
GET /.well-known/agent-card.json
```

### 远端发现输入

v1 使用静态配置发现远端 runtime。配置只描述远端 runtime endpoint，不直接描述工具：

```yaml
agent-runtime:
  remote-agents:
    - id: weather-runtime
      base-url: http://localhost:18081
      card-path: /.well-known/agent-card.json
      enabled: true
      invocation:
        mode: BLOCKING
        blocking-timeout: 30s
        background-timeout: 10m
        input-required: SURFACE_TO_USER
```

`id` 是本地配置 id，用于日志、缓存和策略匹配；真正暴露给 LLM 的名称来自远端 `AgentCard`。如果远端 card 中没有稳定 id，则使用配置 id 作为 `remoteAgentId`。

阻塞/不阻塞由本地 `invocation.mode` 决定，远端 card 不作为唯一决策源。原因是 A2A `AgentCard` 描述的是远端 agent 能力、入口和技能，不一定稳定表达“这次调用会不会很久”。card 中的 streaming、push notification、自定义 metadata 可以作为默认策略推断输入，但最终必须落到本地 `RemoteInvocationPolicy`，这样本地 runtime 才能控制线程占用、超时、用户可见状态和 cancel 传播。

策略优先级：

1. 远端配置显式 `invocation.mode`。
2. 本地按 remote agent id / skill / tool name 配置的 policy override。
3. 从 card capabilities 或 metadata 推断默认值。
4. runtime 默认值：`BLOCKING` + 短 timeout。

推荐配置含义：

| 字段 | 取值 | 含义 |
| --- | --- | --- |
| `invocation.mode` | `BLOCKING` / `BACKGROUND` / `AUTO` | 调用模式。`AUTO` 只用于根据本地规则推断，推断后必须固化到一次调用的 `RemoteInvocationPolicy`。 |
| `blocking-timeout` | duration | 阻塞等待远端 terminal/input-required 的最长时间。 |
| `background-timeout` | duration | 后台远端 task 的整体超时时间。 |
| `input-required` | `SURFACE_TO_USER` | 远端 input-required 必须投影给 parent task 的用户可见 message。 |

### Card 到 Tool 的转换链路

发现、转换与注入链路：

```text
A2aRemoteAgentCardFetcher
  -> RemoteAgentCatalog
  -> RemoteAgentToolProvider
  -> OpenJiuwenRemoteToolInstaller
  -> OpenJiuwen ToolCard + placeholder Tool + OpenJiuwenRemoteAgentInterruptRail
```

职责拆分：

| 组件 | 输入 | 输出 | 职责 |
| --- | --- | --- | --- |
| `A2aRemoteAgentCardFetcher` | `RemoteAgentEndpoint` | A2A `AgentCard` | 通过 HTTP 拉取远端 card，只属于 `access.a2a`。 |
| `RemoteAgentCatalog` | 远端配置 + `AgentCard` | `RemoteAgentDescriptor` | 缓存远端 agent 描述，处理启停、TTL、健康状态。 |
| `RemoteAgentToolProvider` | `RemoteAgentDescriptor` | `RemoteAgentToolSpec` | 把远端 agent 能力收敛为本地工具定义。 |
| `OpenJiuwenRemoteToolAdapter` | `RemoteAgentToolSpec` | OpenJiuwen `ToolCard` + 占位 `Tool` | 只做 OpenJiuwen 类型适配；占位 tool 不负责 ReActAgent 主调用。 |
| `OpenJiuwenRemoteToolInstaller` | `BaseAgent` + `AgentExecutionContext` | 无 | 注册 tool card、占位 tool 和远端 interrupt rail。 |

v1 采用“一远端 runtime 一个 tool”，不把远端多个 skill 拆成多个本地 tool。这样可先保证路由、状态和 resume 闭环稳定：

```text
toolName = "ask_" + normalizedRemoteAgentId
description = descriptor.description()
inputSchema = {
  "message": "string",
  "context": "object"
}
```

tool 描述必须明确告诉本地 LLM：这是远端 agent 调用，可能直接返回结果，也可能要求用户补充输入。描述中不暴露 A2A 协议细节。

### OpenJiuwen Agent 实例到 Handler 的注入点

v1 只适配 OpenJiuwen agent。落地形态是“已有 `BaseAgent` / `ReActAgent` 实例 -> runtime handler adapter”。关键约束是：**注册工具的 agent 实例，必须就是后续传给 `Runner.runAgent(agent, ...)` 的同一个实例**。

推荐在 `engine.openjiuwen` 提供一个持有 agent 实例的 adapter：

```java
final class OpenJiuwenAgentRuntimeHandlerAdapter extends OpenJiuwenAgentRuntimeHandler {
    private final BaseAgent agent;

    @Override
    public Stream<?> execute(AgentExecutionContext context) {
        installRuntimeTools(agent, context);
        Object input = toOpenJiuwenInput(context);
        return Stream.of(Runner.runAgent(agent, input, openJiuwenSession(context), null));
    }
}
```

`installRuntimeTools(...)` 放在 `Runner.runAgent(...)` 之前：

```java
protected final void installRuntimeTools(BaseAgent agent, AgentExecutionContext context) {
    remoteToolInstaller.install(agent, context);
}
```

OpenJiuwen installer 负责两件事：

```text
agent.getAbilityManager().add(tool.getCard())
Runner.resourceMgr().addTool(tool, localAgentId)
agent.registerRail(remoteAgentInterruptRail)
```

前两步让 LLM 能看到远端 tool card，并让 OpenJiuwen 的 resource manager 能解析到一个占位 tool；第三步是 ReActAgent v1 的主桥接点。远端 tool 名称必须注册到 `OpenJiuwenRemoteAgentInterruptRail`，由 rail 在真实 tool 执行前完成远端调用、中断或 synthetic tool result 注入。注册时使用 `localAgentId = context.getScope().agentId()`，确保 OpenJiuwen 的 resource manager 能在当前 agent 执行时找到对应 tool。

OpenJiuwen 的会话连续性由 OpenJiuwen 自己维护。runtime adapter 必须保证每次进入同一个 parent task 时都使用稳定的 OpenJiuwen `sessionId` / `conversation_id`，推荐直接使用 parent A2A task id。`OpenJiuwenMessageAdapter` 已经把 `context.getScope().taskId()` 写入 `conversation_id`；如果显式传 `AgentSessionApi`，也必须使用同一个 id。runtime 不保存 OpenJiuwen checkpoint blob，也不保存 `checkpointRef`；OpenJiuwen 通过自己的 `Checkpointer` 和 `sessionId` 恢复 agent state。

安装必须幂等。推荐幂等 key：

```text
localAgentId + ":" + remoteAgentId + ":" + toolName + ":" + cardVersion
```

如果 handler 复用同一个 agent 实例，多轮执行不会重复注册同一个 tool。如果 handler 每轮新建 agent，则 catalog/card 仍走缓存，installer 只对当前 agent 实例注册一次。

不支持的接入形态：

| 形态 | 结论 | 原因 |
| --- | --- | --- |
| handler 不持有也拿不到 `BaseAgent` | v1 不支持 | runtime 无法调用 OpenJiuwen `AbilityManager`。 |
| handler 自己绕过 `OpenJiuwenAgentRuntimeHandler` 直接 `Runner.runAgent` | 不自动注入 | hook 不会被调用。 |
| 修改 OpenJiuwen 框架源码注册工具 | 不采用 | 侵入第三方框架，破坏 runtime 边界。 |

因此，实现时要把 OpenJiuwen 接入收敛到 runtime 提供的 adapter/factory：业务只交出 OpenJiuwen agent 实例，runtime 负责包装为 `AgentRuntimeHandler` 并安装远端工具。

## 运行闭环与状态规则

### 本地 LLM 选择远端 Tool

当远端 tool 已注入后，OpenJiuwen 的本地 LLM 会在普通工具选择流程中看到该 tool card。ReActAgent v1 的真实调用链路以 rail 为主，不让占位 tool 再独立发起一次远端调用：

```text
OpenJiuwen LLM decides tool_call
  -> OpenJiuwen AbilityManager / Runner resolves Tool
  -> OpenJiuwenRemoteAgentInterruptRail.beforeToolCall(...)
  -> OpenJiuwenRemoteAgentInterruptRail builds RemoteAgentInvocationIntent
  -> RemoteAgentInvocationService.invoke(intent)
  -> RemoteInvocationControl.open(...)
  -> RemoteAgentOutboundPort.invoke(...) or submit(...)
  -> A2aRemoteAgentOutboundAdapter
  -> remote runtime A2A message/send or message/stream
  -> rail returns interrupt(...) or reject(...) synthetic tool result
```

`OpenJiuwenRemoteTool` 在 ReActAgent v1 中只作为 ToolCard/占位 tool 或非 ReActAgent fallback 存在；如果 rail 已拦截该 tool name，它不能再在 `Tool.invoke(...)` 中再次调用远端。rail 构造的 intent 是中性模型，不拼 A2A JSON-RPC：

```java
RemoteAgentInvocationIntent intent = new RemoteAgentInvocationIntent(
        parentTaskId,
        parentContextId,
        localAgentId,
        remoteAgentId,
        toolCallId,
        message,
        policy);
```

`RemoteAgentInvocationService` 是唯一编排点。它决定本次调用走 blocking short call 还是 background long call，并且先调用 `RemoteInvocationControl.open(...)`，让 control 感知 parent task 正在进入远端调用生命周期。rail 只负责 OpenJiuwen 扩展点适配，不拥有远端状态，也不直接调用 A2A client。

### 短调用与长调用判断

调用模式由 policy 决定，不让 OpenJiuwen tool/rail bridge 自己猜：

| 条件 | 模式 | 行为 |
| --- | --- | --- |
| `RemoteInvocationPolicy.mode = BLOCKING` | 短调用 | rail 在当前工具执行阶段等待远端 terminal 或 input-required。 |
| `mode = BACKGROUND` | 长调用 | 提交远端 task 后，parent task 进入等待/工作态，watcher 后续推进。 |
| 未显式配置 | 默认短调用 | 使用较短 timeout，超时后按 tool error 返回。 |
| 远端 card 标记长耗时能力或本地策略命中 | 长调用 | 避免阻塞 OpenJiuwen 执行线程。 |

第一阶段可以先实现 blocking；但接口要保留 `submit/resume/cancel`，因为 `input-required` 和长任务需要同一套 remote task reference。

两种模式的执行差异：

| 维度 | `BLOCKING` | `BACKGROUND` |
| --- | --- | --- |
| access outbound | 调 `RemoteAgentOutboundPort.invoke(...)`，同步等待远端返回 terminal 或 input-required。 | 调 `RemoteAgentOutboundPort.submit(...)`，只等待远端 task/context 引用。 |
| OpenJiuwen bridge | rail 在当前工具执行阶段等待结果；completed/failed/timeout 通过 `reject(...)` 注入 synthetic tool result，input-required 通过 `interrupt(...)` 触发 OpenJiuwen 原生中断。 | rail 提交远端 task 后立即进入 waiting/interrupted，后续由 watcher 和同一 session resume 恢复 parent agent。 |
| control 感知 | `open(...)` 后，结果回来再 `markSucceeded/markInputRequired/markFailed/markTimeout`。 | `open(...)` 后立即 `markSubmitted(...)`，保存 remote task/context。 |
| parent task 状态 | 通常保持 `WORKING`，直到本地 agent 最终输出；远端 input-required 时变为 `INPUT_REQUIRED`。 | remote task 未完成期间保持 `WORKING` 或进入 runtime waiting metadata；远端 input-required 时变为 `INPUT_REQUIRED`。 |
| 超时 | `blocking-timeout` 到期后转 tool error 或 fatal failed。 | `background-timeout` 到期后 watcher 标记 timeout，可传播 remote cancel。 |
| 用户可见输出 | 远端 terminal 不直接可见；远端 input-required 可见。 | 同左；额外可选择把后台进度作为非 caller-visible metadata。 |

`AUTO` 模式只在 `DefaultRemoteAgentInvocationService` 内解析一次：

```text
RemoteAgentDescriptor + local policy override + call args
  -> RemoteInvocationPolicy(mode=BLOCKING/BACKGROUND, timeouts, fatal policy)
  -> RemoteAgentInvocationService executes fixed mode
```

解析后不要把 `AUTO` 继续传到 access.a2a outbound adapter，避免协议适配层承担策略判断。

### 状态闭环

| 远端结果 | parent task/event 处理 | OpenJiuwen 处理 | 用户是否看到 |
| --- | --- | --- | --- |
| `COMPLETED` | `markSucceeded(...)` 保存 tool result，清理 remote waiting metadata。 | tool result 回到本地 agent，继续推理。 | 不直接看到远端结果，只看到 parent agent 最终输出。 |
| `INPUT_REQUIRED` | `markInputRequired(...)` 写 parent `TASK_STATE_INPUT_REQUIRED`，保存 remote task/context，`waitingTarget=REMOTE_AGENT`，并把远端 status message 投影到 parent status message。 | 当前执行停止为 interrupted/waiting，不能继续让 LLM 编最终答案。 | 看到 parent task 的 input-required message。 |
| `PROGRESS` / streaming message | 写 parent `EventQueue`，标记为远端过程事件；不改变 parent 的最终输出语义。 | 不恢复本地 agent。 | streaming 调用方可看到；`tasks/get` 按 A2A task/event 能力看到历史或当前 working message。 |
| `FAILED` | 默认保存 tool error；fatal policy 才写 parent `TASK_STATE_FAILED`。 | 非 fatal 时作为 tool error 恢复本地 agent。 | fatal 才直接看到失败。 |
| `TIMEOUT` | 默认保存 tool error，可按 policy cancel remote task。 | 非 fatal 时作为 tool error 恢复本地 agent。 | fatal 才直接看到失败。 |
| parent cancel | parent 写 `TASK_STATE_CANCELED`，传播 remote cancel。 | 停止本地执行。 | 看到取消。 |

#### 远端 streaming 消息与最终状态

远端 `message/stream` 场景下，远端 agent 可能先发若干 message/artifact/status.message，最后才把 task 状态更新为 `TASK_STATE_INPUT_REQUIRED`。这些中间消息不能丢，但也不能被当成 parent agent 的最终答案。

处理原则：

1. access.a2a outbound adapter 读取远端 SSE 事件后，先映射成 neutral remote event，再交给 `RemoteInvocationControl` 写 parent A2A `EventQueue`。
2. 用户可见的远端 message/artifact 作为 parent 任务的“远端过程事件”发布，metadata 必须包含 `runtime.remoteInvocationId`、`runtime.remoteAgentId`、`runtime.remoteTaskId`、`runtime.remoteEventRole=REMOTE_PROGRESS`。
3. parent task 在远端最终状态到达前保持 `TASK_STATE_WORKING`；中间 working message 可以作为 working status event 或 artifact event 出现在 stream 中，但不能设置 `waitingTarget=REMOTE_AGENT`。
4. 只有远端最终 `TASK_STATE_INPUT_REQUIRED` 才能把 parent task 切到 `TASK_STATE_INPUT_REQUIRED`，并写 `runtime.waitingTarget=REMOTE_AGENT`。
5. 只有 parent agent 消化远端 completed tool result 后生成的回答，才是 parent final output。

事件映射：

| 远端 SSE 事件 | parent 映射 | 用户可见性 | 备注 |
| --- | --- | --- | --- |
| remote `TaskStatusUpdateEvent(WORKING, message)` | parent `TaskStatusUpdateEvent(WORKING, projected message)`，metadata 标记 `REMOTE_PROGRESS`。 | 可见。 | 表示远端进度或阶段性说明，不触发用户输入路由。 |
| remote `TaskArtifactUpdateEvent` / assistant message | parent `TaskArtifactUpdateEvent`，metadata 标记 `REMOTE_PROGRESS`。 | 可见。 | 可用于展示远端阶段性内容；不是 parent final artifact。 |
| remote `TASK_STATE_INPUT_REQUIRED(status.message)` | parent `TaskStatusUpdateEvent(INPUT_REQUIRED, projected message)`，更新 parent status 和 waiting metadata。 | 可见且需要用户回复。 | 这是下一轮输入路由的唯一触发点。 |
| remote `TASK_STATE_COMPLETED` | 生成 `RemoteAgentResult.Completed`，作为 tool result 交回 parent agent。 | 不直接作为 final output。 | 若 completed 前已有远端过程消息，保留为过程历史。 |
| remote `FAILED/TIMEOUT/CANCELED` | 按 policy 写 tool error 或 parent failed/canceled。 | fatal 时可见。 | 非 fatal 时交给 parent agent 消化。 |

去重规则：

- 最终 `INPUT_REQUIRED.status.message` 是权威用户提示。
- 如果最终 prompt 与最后一条远端过程消息语义相同，`markInputRequired(...)` 仍必须写 parent `INPUT_REQUIRED` status event；UI/egress 可根据 `runtime.remoteMessageId` 或 `runtime.promptSourceEventId` 去重展示。
- 如果远端最终 `INPUT_REQUIRED` 没有 `status.message`，`A2aRemoteResultMapper` 可用最后一条用户可见远端过程消息作为 fallback prompt；若仍没有可用内容，再生成兜底 text message。
- 中间过程消息不设置 `runtime.waitingTarget`，所以用户在最终状态前追加输入时，control 仍按 parent `WORKING` 处理：拒绝新输入或返回“仍在工作”。

远端直接 `COMPLETED` 时：

```text
remote TASK_STATE_COMPLETED
  -> A2aRemoteResultMapper maps to RemoteAgentResult
  -> RemoteInvocationControl.markSucceeded(...)
  -> RemoteAgentInvocationService returns tool result
  -> OpenJiuwen receives tool result
  -> OpenJiuwen continues reasoning
  -> parent agent emits final answer
  -> parent A2A EventQueue writes artifact/completed
```

这里有一个硬规则：**远端 completed 不是 parent completed**。远端结果只是 parent agent 的工具结果。只有 parent agent 消化工具结果后生成的最终回答，才写 parent `TaskArtifactUpdateEvent` 和 `TASK_STATE_COMPLETED`。

tool result 建议结构：

```json
{
  "remoteAgentId": "weather-agent",
  "remoteTaskId": "remote-task-9",
  "status": "completed",
  "content": "远端 agent 的最终回答",
  "artifacts": []
}
```

远端返回 `TASK_STATE_INPUT_REQUIRED` 时，不能把它当作普通 tool result 交给 OpenJiuwen 继续编答案。它表示远端 agent 正在等待用户补充输入，必须把远端携带的用户可见 message 投影到 parent task：

```text
remote TASK_STATE_INPUT_REQUIRED(status.message + metadata)
  -> A2aRemoteResultMapper maps visible message + remote task/context
  -> RemoteInvocationControl.markInputRequired(...)
  -> parent Task.status = TASK_STATE_INPUT_REQUIRED
  -> parent Task.status.message = projected remote status.message
  -> parent Task.metadata.runtime.waitingTarget = REMOTE_AGENT
  -> EventQueue writes TaskStatusUpdateEvent(INPUT_REQUIRED, message)
  -> current OpenJiuwen execution stops as interrupted/waiting
  -> user sees remote message through parent A2A response/stream
```

这时用户看到的消息来自 parent A2A task，不直接来自 remote task。投影规则：

| 远端 input-required 内容 | parent task 投影 |
| --- | --- |
| `remoteTask.status.message.role` | 保留为 agent/assistant 侧消息；如远端 role 为空，使用 agent/assistant。 |
| `remoteTask.status.message.parts` | 原样保留可展示 parts；至少支持 text part，file/data part 按 A2A part 类型透传或降级为 metadata 引用。 |
| `remoteTask.status.message.metadata` | 合并到 parent status message metadata，并增加 `runtime.remoteAgentId`、`runtime.remoteTaskId`。 |
| `remoteTask.metadata` 中的协议/路由信息 | 只保存必要字段到 parent `Task.metadata.runtime.*`，不直接暴露给用户。 |
| 远端没有 message 但有状态原因 | 生成一个兜底 text message，例如“远端 agent 需要补充输入”。 |

parent status message 推荐保留远端可见内容，同时 metadata 保存 remote 关联：

```json
{
  "status": {
    "state": "TASK_STATE_INPUT_REQUIRED",
    "message": {
      "role": "agent",
      "parts": [
        { "kind": "text", "text": "请补充你要查询的城市。" }
      ]
    }
  },
  "metadata": {
    "runtime.waitingTarget": "REMOTE_AGENT",
    "runtime.remoteInvocationId": "rinv-123",
    "runtime.remoteAgentId": "weather-agent",
    "runtime.remoteTaskId": "remote-task-9",
    "runtime.remoteContextId": "remote-context-9",
    "runtime.parentAgentId": "local-openjiuwen-agent"
  }
}
```

对应的 `TaskStatusUpdateEvent` 也必须携带同一个 parent status message；streaming 调用方通过这个 event 立即看到远端要求用户补充的信息，`tasks/get` 调用方通过 parent task status 看到同样的信息。不能只把 message 存在 metadata 里，也不能只写日志或内部索引。

OpenJiuwen bridge 必须使用 OpenJiuwen 原生中断机制，而不是让 runtime 自己保存 agent checkpoint。推荐实现是把远端 tool name 注册到 `OpenJiuwenRemoteAgentInterruptRail`，由 rail 的 `beforeToolCall(...)` 在真实 tool 执行前完成远端调用和中断决策。这样中断发生在 OpenJiuwen `railedExecuteSingleToolCall(...)` 外层能识别的位置，不会被普通 tool invoke 异常包装成 tool error。

```java
final class OpenJiuwenRemoteAgentInterruptRail extends BaseInterruptRail {
    @Override
    protected InterruptDecision resolveInterrupt(
            AgentCallbackContext ctx,
            ToolCall toolCall,
            Object userInput) {
        RemoteAgentResult result = userInput == null
                ? remoteInvocationService.invoke(toIntent(ctx, toolCall))
                : remoteInvocationService.resumeRemoteInput(toResumeIntent(ctx, toolCall, userInput));

        if (result instanceof RemoteAgentResult.InputRequired inputRequired) {
            return interrupt(InterruptRequest.builder()
                    .interruptId(toolCall.getId())
                    .message(inputRequired.userVisibleText())
                    .context(remoteContext(inputRequired, toolCall))
                    .build());
        }
        if (result instanceof RemoteAgentResult.Completed completed) {
            return reject(completed.asToolResultJson(), toolMessage(toolCall, completed));
        }
        return reject(result.asToolErrorJson(), toolMessage(toolCall, result));
    }
}
```

OpenJiuwen `BaseInterruptRail` 会把 `InterruptResult` 转成 `ToolInterruptException`，`AbilityManager` 会把该异常收集为 interrupted tool execution，`ReActAgent` 会把它提交为 `result_type=interrupt` 和 `InteractionOutput`，OpenJiuwen checkpointer 负责按同一个 `sessionId` / `conversation_id` 保存现场。远端 completed/failed/timeout 则通过 `RejectResult` / `_skip_tool` 注入 synthetic tool result，跳过真实 tool 执行。

这里不是“不抛中断异常”，而是“不在自定义 rail 里手写抛异常”。`OpenJiuwenRemoteAgentInterruptRail.resolveInterrupt(...)` 应返回 `interrupt(InterruptRequest)`；随后由 `BaseInterruptRail.applyDecision(...)` 在统一位置抛 `ToolInterruptException`。这样可以复用 OpenJiuwen 的 rail contract，保证 `RejectResult`、`_skip_tool`、`ToolCallInputs`、`ToolMessage` 和 interrupt exception 的处理路径一致。若在自定义 rail 或占位 `Tool.invoke(...)` 中直接抛异常，会把 runtime 代码耦合到 OpenJiuwen 内部异常细节；尤其在普通 `Tool.invoke(...)` 分支里，异常可能被包装成 tool error，导致 `ReActAgent` 不能按原生 interrupt/resume 流程保存现场。

`OpenJiuwenRemoteTool` 不作为 ReActAgent v1 的主执行入口；若保留这个类名，应实现为占位 tool 或非 ReActAgent fallback。ReActAgent 路径中的远端调用和 input-required 中断触发必须收敛在 rail 或 OpenJiuwen 官方支持的等价中断扩展点。若直接在 `Tool.invoke(...)` 中抛异常，OpenJiuwen 的普通 tool 执行分支可能把它包装成 tool error。

runtime 只保存 parent A2A task metadata 和 remote invocation index，不能保存 OpenJiuwen checkpoint blob，也不需要 `checkpointRef`。不要把远端 input-required 映射成 OpenJiuwen 普通 `answer`、普通 `error`，也不要由 runtime adapter 捕获后伪造自己的 checkpoint。正确的中断事实由 OpenJiuwen 的 `ToolInterruptException` / `InteractionOutput` / checkpointer 维护；runtime 的事实只到 A2A parent task 状态和路由 metadata。

### 用户下一轮输入分流

用户下一轮输入仍然打到本地 runtime 的 A2A ingress。control 不看 OpenJiuwen 内部状态，而是读取 parent A2A task：

```text
new user message
  -> access.a2a ingress
  -> A2aTaskControlService loads parent Task
  -> A2aTaskMetadataRouter sees INPUT_REQUIRED + waitingTarget=REMOTE_AGENT
  -> RemoteAgentInvocationService.resumeRemoteInput(...)
  -> A2aRemoteAgentOutboundAdapter sends input to remote task/context
```

判断规则：

```java
Task task = taskStore.get(taskId);
Map<String, Object> metadata = task.metadata();

if (task.status().state() == TaskState.TASK_STATE_INPUT_REQUIRED
        && "REMOTE_AGENT".equals(metadata.get(A2aTaskMetadataKeys.WAITING_TARGET))) {
    remoteAgentInvocationService.resumeRemoteInput(
            String.valueOf(metadata.get(A2aTaskMetadataKeys.REMOTE_INVOCATION_ID)),
            userInput);
    return accepted(task);
}

enqueueLocalAgentResume(task, userInput);
```

| A2A task state | `runtime.waitingTarget` | 下一步 |
| --- | --- | --- |
| `TASK_STATE_INPUT_REQUIRED` | `REMOTE_AGENT` | 调 `resumeRemoteInput(...)`，输入发给远端 task/context。 |
| `TASK_STATE_INPUT_REQUIRED` | `LOCAL_AGENT` 或空 | 恢复本地 engine。 |
| `TASK_STATE_WORKING` | 任意 | 拒绝新输入或返回仍在工作。 |
| final state | 任意 | 拒绝 resume。 |

远端 resume 后有三种结果：

| 远端结果 | parent 处理 |
| --- | --- |
| 再次 `INPUT_REQUIRED` | 更新 parent status message，保留 `waitingTarget=REMOTE_AGENT`。 |
| `COMPLETED` | 保存 tool result，清理 remote waiting metadata，恢复 parent OpenJiuwen agent。 |
| `FAILED/TIMEOUT/CANCELED` | 按 policy 转为 tool error 或 parent failed。 |

远端 completed 后恢复 parent agent 的输入不是用户原话，而是“远端 tool result”。恢复链路：

```text
remote completed after resume
  -> control stores tool result against remoteInvocationId/toolCallId
  -> clear runtime.waitingTarget / remote waiting metadata
  -> build OpenJiuwen InteractiveInput with toolCallId -> remote tool result
  -> re-enter OpenJiuwen with the same sessionId / conversation_id
  -> OpenJiuwen checkpointer restores the interrupted tool_call
  -> parent final answer becomes caller-visible output
```

如果 OpenJiuwen 当前版本的某个 agent 类型没有可用的 tool interrupt resume 能力，v1 可退化为把远端 tool result 作为追加上下文重新调用本地 agent，但这个降级必须由 `engine.openjiuwen` 封装，不能泄漏到 access/control。对 ReActAgent，优先使用同一个 `sessionId` / `conversation_id` 加 `InteractiveInput.update(toolCallId, remoteToolResult)` 恢复。

用户可见输出分三类，语义不能混用：远端过程事件只表示 remote agent 的阶段性输出；parent `INPUT_REQUIRED` 表示必须等待用户补充输入；parent agent 最终输出才表示本地任务完成。

## 实现切片

### Phase 1：A2A Task/EventQueue 底座

1. 新增 `A2aTaskControlService`，统一创建、恢复、取消 parent A2A task。
2. 新增 `A2aTaskEventPublisher`，统一写 `TaskStatusUpdateEvent` 和 `TaskArtifactUpdateEvent`。
3. 新增 `A2aTaskMetadataKeys` 和 `A2aTaskMetadataRouter`。
4. 装配 SDK `TaskStore`、`InMemoryTaskStore`、`EventQueue`、`InMemoryQueueManager`。
5. 改造 `A2aJsonRpcHandler`，让 inbound 进入 `A2aTaskControlService`。
6. 改造 `A2aTaskMapper`，从 `TaskStore/EventQueue` 读取 task 和事件。
7. 改造 engine output，使其写 A2A event。

### Phase 2：Remote A2A Tool Blocking Call

1. 新增远端 runtime 静态配置。
2. 新增 `A2aRemoteAgentCardFetcher`。
3. 新增 `RemoteAgentCatalog` 与 `RemoteAgentToolProvider`。
4. 新增 `RemoteAgentInvocationService` 默认实现。
5. 新增 `RemoteInvocationControl` command 模型与实现，写 parent A2A task metadata/event。
6. 新增 `A2aRemoteAgentOutboundAdapter.invoke(...)`。
7. 增加 OpenJiuwen tool injection hook、installer、`OpenJiuwenRemoteAgentInterruptRail`、agent-instance-to-handler adapter/factory，确保 ReActAgent v1 由 rail-first 发起远端调用，占位 tool 不重复调用。
8. 覆盖 blocking 下的 `COMPLETED`、`FAILED`、`TIMEOUT`、`INPUT_REQUIRED`：其中 `INPUT_REQUIRED` 必须写 parent task status message，并触发 OpenJiuwen 原生 interrupt。
9. 覆盖远端 `message/stream` 中最终状态前的 message/artifact 投影：写 parent EventQueue 的 `REMOTE_PROGRESS` 事件，但不设置 remote waiting route。

### Phase 3：Background + Remote Resume

1. 新增 `RemoteInvocationIndex`。
2. 实现 `submit(...)`、watcher、`markSubmitted(...)`。
3. 将 watcher 收到的 `INPUT_REQUIRED` 复用 Phase 2 的 `markInputRequired(...)` 投影规则。
4. 实现 A2A ingress resume 分流。
5. 实现 `resumeRemoteInput(...)`，结果仍映射为 `RemoteAgentResult`。
6. 实现远端 completed 后 parent agent resume。
7. 实现 parent cancel 到 remote cancel 的传播。

## 需要新增的文件

```text
agent-runtime/
  src/main/java/com/huawei/ascend/runtime/
    access/
      a2a/
        A2aRemoteAgentCardFetcher.java
        A2aRemoteAgentOutboundAdapter.java
        A2aRemoteResultMapper.java
    control/
      A2aTaskControlService.java
      A2aTaskEventPublisher.java
      A2aTaskMetadataKeys.java
      A2aTaskMetadataRouter.java
      RemoteInvocationIndex.java
      InMemoryRemoteInvocationIndex.java
    control/api/
      OpenRemoteInvocationCommand.java
      RemoteInvocationCommands.java
      RemoteInvocationControl.java
    engine/
      spi/
        RemoteAgentCatalog.java
        RemoteAgentDescriptor.java
        RemoteAgentEndpoint.java
        RemoteAgentInvocationHandle.java
        RemoteAgentInvocationIntent.java
        RemoteAgentInvocationService.java
        RemoteAgentOutboundPort.java
        RemoteAgentRequest.java
        RemoteAgentResult.java
        RemoteAgentToolProvider.java
        RemoteAgentToolSpec.java
        RemoteInvocationMode.java
        RemoteInvocationPolicy.java
        RemoteTaskReference.java
      service/
        DefaultRemoteAgentCatalog.java
        DefaultRemoteAgentInvocationService.java
        DefaultRemoteAgentToolProvider.java
      openjiuwen/
        OpenJiuwenAgentRuntimeHandlerAdapter.java
        OpenJiuwenAgentRuntimeHandlerFactory.java
        OpenJiuwenInteractiveInputMapper.java
        OpenJiuwenRemoteAgentInterruptRail.java
        OpenJiuwenRemoteToolAdapter.java
        OpenJiuwenRemoteToolInstaller.java
        NoopOpenJiuwenRemoteToolInstaller.java
```

新增文件作用：

| 文件 | 作用 |
| --- | --- |
| `control/A2aTaskControlService.java` | A2A-native task control facade。 |
| `control/A2aTaskEventPublisher.java` | 统一写 A2A task status/artifact event。 |
| `control/A2aTaskMetadataKeys.java` | 定义 runtime metadata key。 |
| `control/A2aTaskMetadataRouter.java` | 根据 A2A task status + metadata 做 resume 路由。 |
| `control/RemoteInvocationIndex.java` | remote invocation 到 parent/remote task 的轻量索引。 |
| `control/InMemoryRemoteInvocationIndex.java` | 索引的内存实现。 |
| `control/api/RemoteInvocationControl.java` | 远端调用生命周期控制 API，接收 control.api command，不暴露 access/engine 实现类型。 |
| `control/api/OpenRemoteInvocationCommand.java` | 打开远端调用的 control 命令，包含 parent task、remote agent、tool call、policy 摘要。 |
| `control/api/RemoteInvocationCommands.java` | 远端 submitted/input-required/succeeded/failed/timeout/cancelled 等生命周期命令的嵌套 record 集合，减少碎文件。 |
| `access/a2a/A2aRemoteAgentCardFetcher.java` | 拉取远端 AgentCard。 |
| `access/a2a/A2aRemoteAgentOutboundAdapter.java` | 调用远端 A2A runtime。 |
| `access/a2a/A2aRemoteResultMapper.java` | 远端 A2A task/event 到 `RemoteAgentResult` 的映射。 |
| `engine/spi/RemoteAgentResult.java` | 远端调用结果模型；`InputRequired` 必须包含可投影给用户的 message/parts、remote task/context 和必要 metadata，不能只有 prompt 字符串。 |
| `engine/spi/RemoteInvocationPolicy.java` | 单次远端调用的固化策略，包含 `mode`、blocking/background timeout、input-required 行为和 fatal policy。 |
| `engine/spi/*` | framework-neutral remote agent/tool/invocation 端口和模型，只供 engine.openjiuwen 与 engine.service 使用；不包含 A2A card fetcher 这类协议 I/O。 |
| `engine/service/DefaultRemoteAgentCatalog.java` | remote agent catalog 默认实现。 |
| `engine/service/DefaultRemoteAgentInvocationService.java` | remote tool call 编排实现。 |
| `engine/service/DefaultRemoteAgentToolProvider.java` | remote descriptor 到 tool spec 的默认映射。 |
| `engine/openjiuwen/OpenJiuwenAgentRuntimeHandlerAdapter.java` | 持有 `BaseAgent` 实例，把已存在的 OpenJiuwen agent 包装成 runtime `AgentRuntimeHandler`，并在 `Runner.runAgent(...)` 前调用工具注入 hook。 |
| `engine/openjiuwen/OpenJiuwenAgentRuntimeHandlerFactory.java` | 对外提供 `BaseAgent/ReActAgent -> AgentRuntimeHandler` 的工厂入口，收敛 OpenJiuwen 接入方式。 |
| `engine/openjiuwen/OpenJiuwenInteractiveInputMapper.java` | 把远端 completed 的 tool result 转成 OpenJiuwen `InteractiveInput.update(toolCallId, result)`，并保证使用同一个 `sessionId` / `conversation_id` 恢复。 |
| `engine/openjiuwen/OpenJiuwenRemoteAgentInterruptRail.java` | ReActAgent v1 的主桥接组件：拦截远端 tool name，调用 `RemoteAgentInvocationService`，对 input-required 返回 `interrupt(...)`，对 terminal/error 返回 `reject(...)` synthetic tool result；不保存状态，不替代 OpenJiuwen checkpointer。 |
| `engine/openjiuwen/OpenJiuwenRemoteToolAdapter.java` | 将 `RemoteAgentToolSpec` 转成 OpenJiuwen `ToolCard` 与占位 `Tool`；占位 tool 只用于 resource 解析或非 ReActAgent fallback，不能在 rail 已处理后再次调用远端。 |
| `engine/openjiuwen/OpenJiuwenRemoteToolInstaller.java` | 将远端工具安装到当前 OpenJiuwen `BaseAgent` 实例，并为 ReActAgent v1 注册必需的 `OpenJiuwenRemoteAgentInterruptRail`。 |
| `engine/openjiuwen/NoopOpenJiuwenRemoteToolInstaller.java` | 未配置远端 agent 时的空实现，保持 OpenJiuwen handler 可独立运行。 |

## 需要修改的现有文件

```text
agent-runtime/
  src/main/java/com/huawei/ascend/runtime/
    access/a2a/A2aJsonRpcHandler.java
    access/a2a/A2aTaskMapper.java
    access/a2a/A2aOutputRegistry.java
    access/a2a/DefaultNotificationPort.java
    access/output/EngineOutputSink.java
    app/RuntimeWiringConfiguration.java
    control/TaskControlService.java
    control/TaskQueueRegistry.java
    queue/QueueManager.java
    queue/InternalEventQueue.java
    engine/InternalEngineCommandGateway.java
    engine/EngineAutoConfiguration.java
    engine/openjiuwen/OpenJiuwenAgentRuntimeHandler.java
```

修改目标：

| 文件 | 修改目标 |
| --- | --- |
| `A2aJsonRpcHandler.java` | inbound 直接进入 A2A task control facade。 |
| `A2aTaskMapper.java` | 从 `TaskStore/EventQueue` 构造 A2A task 查询结果。 |
| `A2aOutputRegistry.java` | 从 caller-visible 主链路移除，`tasks/get` 和 streaming 以 A2A `TaskStore/EventQueue` 为准。 |
| `DefaultNotificationPort.java` / `EngineOutputSink.java` | 改为调用 `A2aTaskEventPublisher`。 |
| `RuntimeWiringConfiguration.java` | 装配 A2A task/event、remote invocation、OpenJiuwen installer。 |
| `TaskControlService.java` | 被 `A2aTaskControlService` 替代或收敛为兼容 facade，不再持有自定义 `Task` 事实源。 |
| `TaskQueueRegistry.java` | 移出任务事实源路径；如仍需要，只保留 parent task 到 engine command 的临时调度索引。 |
| `queue/QueueManager.java` / `InternalEventQueue.java` | 只保留 engine command 调度能力，不再作为 caller-visible event queue 或 task 状态来源。 |
| `InternalEngineCommandGateway.java` | 从 A2A task control 生成 engine command；不从 A2A EventQueue 反推任务事实。 |
| `EngineAutoConfiguration.java` | 装配新的 task control、remote invocation 和可选 engine command 调度队列。 |
| `OpenJiuwenAgentRuntimeHandler.java` | 增加 remote tool installer hook。 |

## 验证策略

必须覆盖：

- A2A ingress 创建的 task 存在于 SDK `TaskStore`。
- `tasks/get` 返回来自 `TaskStore/EventQueue` 的 A2A task。
- engine output 写入 `TaskStatusUpdateEvent` / `TaskArtifactUpdateEvent`。
- OpenJiuwen remote tool/rail bridge 只调用 `RemoteAgentInvocationService`，不直接调用 A2A client，且不会由 rail 与占位 tool 双重触发远端调用。
- remote tool call 会写 parent task metadata。
- remote streaming message/artifact 会写 parent EventQueue 的远端过程事件，不会触发 `waitingTarget=REMOTE_AGENT`。
- `TASK_STATE_INPUT_REQUIRED + runtime.waitingTarget=REMOTE_AGENT` 会路由到 `resumeRemoteInput(...)`。
- remote terminal success 只作为 tool result，不直接写 parent completed。
- parent agent final answer 才写 `TASK_STATE_COMPLETED`。
- control 不依赖 access.a2a JSON-RPC wrapper。
- engine.openjiuwen 不依赖 A2A server task/event 类型。

## 可落地性检查

该方案可以转代码落地，因为：

- A2A SDK 已提供 `TaskStore`、`Task`、`TaskStatusUpdateEvent`、`TaskArtifactUpdateEvent`、`EventQueue`、`AgentEmitter`。
- runtime 只需要封装 public API，不依赖 SDK 包可见方法。
- resume 路由有单一事实源：parent A2A task status + metadata。
- 远端调用有单一编排入口：`RemoteAgentInvocationService`。
- 远端协议 I/O 有单一实现边界：`access.a2a`。
- OpenJiuwen 注入只发生在 runtime adapter，不修改 OpenJiuwen 框架源码。
