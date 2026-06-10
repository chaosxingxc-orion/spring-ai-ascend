# 远端 A2A Agent 工具调用落地方案

本文定义在当前 `agent-runtime` 代码基础上，把远端 A2A runtime 注入为本地 OpenJiuwen agent 工具的 v1 落地方案。方案只描述 A2A 打穿后的实现路径：入口、任务状态、流式事件、远端调用路由都以 A2A `Task`、`TaskStore`、`EventQueue` 和 task metadata 为主线。

## 已定边界

- v1 只适配 OpenJiuwen，不适配 AgentScope 或其他框架。
- v1 采用“一远端 runtime 一个本地 tool”，不把远端 card 内的多个 skill 拆成多个 tool。
- 本地 LLM 选择远端 tool 后，统一先中断本地 OpenJiuwen agent，再由 runtime 调远端 A2A。
- OpenJiuwen `Tool` 和 `Rail` 不持有 A2A HTTP/JSON-RPC client；远端 A2A client 只在 `engine.a2a.A2aRemoteAgentOutboundAdapter` 中出现。
- runtime 只保存 A2A task metadata、remote task/context、toolCallId、local conversation id 等路由信息；OpenJiuwen checkpoint 由 OpenJiuwen 自己保存和恢复。
- parent A2A `Task.metadata` 是远端调用路由事实源；v1 不新增独立 invocation store。
- 远端 terminal result 默认作为 tool result 回灌给本地 OpenJiuwen，不直接写 parent completed。
- 远端 `INPUT_REQUIRED` 必须投影为 parent A2A task 的 `TASK_STATE_INPUT_REQUIRED`，下一轮用户输入必须路由回同一个 remote task/context。
- v1 的远端调用是同步阻塞编排：首次远端调用发生在 `A2aAgentExecutor.execute(...)` 方法内部，远端 completed/failed 后立刻 re-enter 本地 OpenJiuwen；不引入后台 watcher 或异步回调线程。
- 远端 `INPUT_REQUIRED` 后的下一轮用户输入仍进入 A2A SDK `RequestHandler` 和 `A2aAgentExecutor.execute(...)`；executor 开头识别 remote continuation，直接 resume 远端，不进入 OpenJiuwen 本地推理。
- v1 单个 parent task 同一时刻只支持一个远端 tool call。第一个 `REMOTE_INVOCATION` 会中断并关闭本地 result stream；同一轮推理中出现多个并行远端 tool call 时，返回明确错误，不做并发 fan-out。

## 总体闭环

```text
远端 runtime URL 配置
  -> 异步发现远端 card
  -> 生成远端 runtime 描述和一个 remote tool spec
  -> OpenJiuwen handler 执行期注入 ToolCard + placeholder Tool + remote rail
  -> 本地 LLM 选择远端 tool
  -> remote rail 触发 OpenJiuwen interrupt
  -> OpenJiuwenStreamAdapter 映射为 AgentExecutionResult.REMOTE_INVOCATION
  -> A2aAgentResultRouter 返回 remote invocation decision 并停止本地 stream
  -> A2aAgentExecutor 调用 RemoteAgentInvocationService
  -> A2aRemoteAgentOutboundAdapter 调用远端 /a2a
  -> 远端 progress/input-required/completed 投影到 parent A2A task
  -> completed/failed 通过 InteractiveInput.update(toolCallId, toolResult) 恢复本地 OpenJiuwen
  -> 本地 OpenJiuwen 继续推理并产出 parent final answer
```

这个闭环的关键约束是：远端调用发生在 runtime，远端 terminal result 先回到本地 agent，本地 agent 才决定最终如何答复用户。

远端要求用户补充输入时，下一轮输入链路为：

```text
用户下一轮输入
  -> A2A SDK RequestHandler
  -> A2aAgentExecutor.execute(ctx, emitter)
  -> executor 读取 parent task metadata
  -> metadata.runtime.waitingTarget == REMOTE_AGENT
  -> 不调用 OpenJiuwen
  -> RemoteAgentInvocationService.resumeRemoteInput(...)
  -> 远端 INPUT_REQUIRED: 用当前 emitter 更新 parent input-required
  -> 远端 COMPLETED/FAILED: 用 tool result re-enter 本地 OpenJiuwen
```

## 远端 Card 发现与 Tool 注册

v1 静态配置远端 runtime，只需要配置 `url`：

```yaml
agent-runtime:
  remote-agents:
    - url: http://localhost:18081
```

当 `url` 是 runtime 根地址时，默认读取 `${url}/.well-known/agent-card.json`；当 `url` 已经指向 card 文件时，直接读取该地址。本地不配置 `id`、`base-url`、`card-path`、`enabled`、timeout 或 tool name override。

启动发现链路：

```text
Spring runtime 启动
  -> 本地 /a2a 和 /.well-known/agent-card.json ready
  -> RuntimeAutoConfiguration 绑定 remote urls
  -> RemoteAgentCatalog 将每个 url 注册为 PENDING
  -> 启动后台发现任务
  -> RemoteAgentCatalog 拉取远端 AgentCard
  -> 成功: 标记 AVAILABLE，并缓存 descriptor/tool spec
  -> 失败: 保持 PENDING，按固定间隔继续重试，不阻塞本地 runtime
  -> 已 AVAILABLE 的远端不再重试、不刷新
```

远端 URL 的本地状态：

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 尚未成功拉取 card，后台按固定间隔持续重试；不注入 tool，不阻塞请求。 |
| `AVAILABLE` | card 已获取并转换成 tool spec；后续 OpenJiuwen handler 执行生命周期内可注入 tool；成功后不再刷新。 |

`RemoteAgentCatalog` 按规范化 card URL 去重；同一个远端 runtime 被配置多次时只保留一个。v1 不做 TTL、不做运行时刷新、不做后台健康探测；失败的 URL 持续重试，成功的 URL 固化为 `AVAILABLE`，远端 card 变化后通过重启本地 runtime 重新发现。

catalog 只负责发现、缓存和提供 `AVAILABLE` 的 remote tool spec，不直接修改任何本地 agent 实例。真正注入发生在本地 OpenJiuwen handler 每次执行本地 agent 的生命周期内。

## ToolCard 输入 Schema

远端 runtime 注入为 OpenJiuwen tool 时，v1 暴露给本地 LLM 的 tool 入参 schema 是：

```json
{
  "type": "object",
  "properties": {
    "message": {
      "type": "string",
      "description": "Input message sent to the remote A2A runtime."
    }
  },
  "required": ["message"]
}
```

这段 schema 是 `RemoteAgentToolSpec` 暴露给 OpenJiuwen `ToolCard` 的输入描述，不是 A2A JSON-RPC 报文，也不是远端 `/a2a` wire protocol。含义是：本地 LLM 调用远端 tool 时只需要给出一个 `message` 字符串；runtime 再把这个字符串转换成 A2A `Message.parts[TextPart]` 发给远端 runtime。

v1 使用这个宽松 schema 的原因是：A2A AgentCard 描述的是 agent 能力、入口和 skills，不一定提供函数级 JSON Schema；v1 不引入复杂参数模型。为了降低本地 LLM 误用风险，`RemoteAgentCatalog` 生成 `ToolCard.description` 时必须合成远端 `AgentCard.name`、`AgentCard.description` 和 `skills[].description`，而不是只写通用的 “Input message sent to the remote A2A runtime.”。

## OpenJiuwen 注入链路

注入必须发生在 handler 定义的执行生命周期里：拿到本次要执行的 OpenJiuwen agent 实例之后、调用 `Runner.runAgent(...)` 或 `agent.stream(...)` 之前。远端 card 发现成功只让 catalog 变为 `AVAILABLE`，不会主动把 tool 注入到某个长期全局 agent 上。

当前 `OpenJiuwenAgentRuntimeHandler` 的真实执行顺序是：

```text
OpenJiuwenAgentRuntimeHandler.execute(context)
  -> ReActAgent agent = buildAgent() 或 getAgentInstance(context)
  -> installRails(agent, context)          // 来自 openJiuwenRails(context)
  -> installRuntimeTools(agent, context)
  -> Object input = toOpenJiuwenInput(context)
  -> Runner.runAgent(agent, input, openJiuwenConversationId(context), null)
```

最新代码已经提供 `openJiuwenRails(context)` 扩展点，但它只能返回 `AgentRail`，不能完整安装远端 tool，因为远端注入还需要注册 `ToolCard` 和 placeholder `Tool`。因此 v1 需要在 `OpenJiuwenAgentRuntimeHandler` 中新增一个 runtime tool installer hook：`installRuntimeTools(BaseAgent agent, AgentExecutionContext context)`。该 hook 是本方案需要新增的能力，不是当前已有 API。

`OpenJiuwenRemoteToolInstaller` 对同一个 agent 实例完成三件事：

```text
agent.getAbilityManager().add(remote ToolCard)
Runner.resourceMgr().addTool(placeholder Tool, agent.getCard().getId())
agent.registerRail(OpenJiuwenRemoteAgentInterruptRail)
```

`installRuntimeTools(...)` 必须在现有 `installRails(...)` 之后、`toOpenJiuwenInput(...)` 和 `runOpenJiuwenAgent(...)` 之前执行。这样 `openJiuwenRails(context)` 返回的框架自有 rail 先完成注册，remote rail 后注册，并且只拦截远端 tool name；非远端 tool call 必须放行给 OpenJiuwen 原有能力链。

`ToolCard` 让 LLM 看见远端能力；placeholder `Tool` 只满足 OpenJiuwen resource manager 的 tool name 解析；`OpenJiuwenRemoteAgentInterruptRail` 是真实远端调用入口。placeholder `Tool.invoke(...)` 和 `stream(...)` 不访问远端，rail 生效时它不会被执行；rail 未生效时它返回明确错误，避免静默伪成功。

installer 每次执行前从 `RemoteAgentCatalog` 读取当前 `AVAILABLE` tool specs，只安装本次 agent 实例尚未安装的 remote tool。仍处于 `PENDING` 的远端 runtime 不注入；后台重试成功后，从下一个 handler 执行周期开始注入。

## 本地 Tool Call 到 Runtime

本地 LLM 选择远端 tool 后，OpenJiuwen 先中断本地 agent：

```text
OpenJiuwen LLM tool_call
  -> AbilityManager.railedExecuteSingleToolCall(...)
  -> OpenJiuwenRemoteAgentInterruptRail.resolveInterrupt(...)
  -> InterruptRequest.context 写入 runtime.remote.*
  -> BaseInterruptRail.applyDecision(...) 抛出 ToolInterruptException
  -> ReActAgent.commitInterrupt(...) 保存 ToolInterruptionState
  -> OpenJiuwenStreamAdapter 从 interrupt state/context 读取 runtime.remote.*
  -> OpenJiuwenStreamAdapter 构造 AgentExecutionResult.RemoteInvocation
  -> AgentExecutionResult.remoteInvocation(remoteInvocation)
```

`OpenJiuwenRemoteAgentInterruptRail` 只写 OpenJiuwen interrupt context，不调用 `RemoteAgentInvocationService`，不持有 A2A client。`AgentExecutionResult.RemoteInvocation` 由 `OpenJiuwenStreamAdapter` 在 runtime 侧构造。

`InterruptRequest.context` 至少写入：

```text
runtime.remote.kind = REMOTE_AGENT_INVOCATION
runtime.remote.agentId
runtime.remote.toolName
runtime.remote.toolCallId
runtime.remote.parentTaskId
runtime.remote.parentContextId
runtime.remote.arguments
```

实现前置验证：必须用单元测试证明 OpenJiuwen interrupt result/state 能把上述 context 带回 `OpenJiuwenStreamAdapter`。验证不通过时，在 runtime 侧增加一个按 `toolCallId` 索引的临时 intent cache；该 cache 只补偿 OpenJiuwen context 承载限制，不作为远端调用状态事实源。

`AgentExecutionResult` 需要扩展：

```text
Type = OUTPUT / COMPLETED / FAILED / INTERRUPTED / REMOTE_INVOCATION
AgentExecutionResult.remoteInvocation(RemoteInvocation remoteInvocation)
AgentExecutionResult.remoteInvocation()

RemoteInvocation = remote agent id + tool name + toolCallId + parent task/context + arguments
```

现有 `INTERRUPTED(prompt)` 继续表示普通本地 input-required；远端 tool call 使用独立 `REMOTE_INVOCATION`，避免把 remote agent id、toolCallId、arguments、parent task/context 塞进 prompt 字符串。v1 不单独新增 `RemoteAgentInvocationIntent.java`，避免 `engine.spi.AgentExecutionResult` 反向引用 `engine.service` 类型。

## 结果路由与远端调用

当前 `A2aAgentExecutor` 的 `results.forEach(...)` 需要替换为显式路由循环。新增 `A2aAgentResultRouter`，供首次 A2A 请求和远端 completed 后的本地 re-enter 共用：

```text
OUTPUT            -> 写 parent artifact/progress，继续消费
COMPLETED         -> 写 parent completed，停止消费
FAILED            -> 写 parent failed，停止消费
INTERRUPTED       -> 写 parent input-required，停止消费
REMOTE_INVOCATION -> 返回 remote invocation decision，停止消费
未知类型           -> 抛 IllegalStateException
```

`REMOTE_INVOCATION` 必须停止继续消费本地 handler stream，不能让后续 `COMPLETED` 抢先写入 parent task。实现上不能继续使用 `Stream.forEach(...)`；`A2aAgentResultRouter` 必须使用可短路的消费方式，例如在 `try-with-resources` 中通过 `Iterator` 循环消费 result，遇到 `COMPLETED`、`FAILED`、`INTERRUPTED` 或 `REMOTE_INVOCATION` 后 `break`，并确保 raw stream 与 adapted stream 都被关闭。

`A2aAgentResultRouter` 不直接持有 A2A client，也不独占远端调用编排权。它只消费 `AgentExecutionResult` stream，并返回路由决策：

```text
RouteDecision.CONTINUE
RouteDecision.TERMINAL
RouteDecision.REMOTE_INVOCATION(remoteInvocation)
```

`A2aAgentExecutor` 负责 `execute(...)` 内的完整控制流：

```text
A2aAgentExecutor.execute(ctx, emitter)
  -> 从 ctx.getTask() / TaskStore 读取 parent task
  -> 如果 parent task 是 remote waiting，进入 remote continuation branch
  -> 否则调用本地 handler.execute(USER_MESSAGE context)
  -> A2aAgentResultRouter 消费第一段本地 stream
  -> 遇到 REMOTE_INVOCATION decision
  -> 关闭第一段 raw/adapted stream
  -> RemoteAgentInvocationService.invoke(remoteInvocation)
  -> A2aRemoteOutcomeProjector 投影远端 outcome
  -> 远端 INPUT_REQUIRED: emitter.requiresInput() 后返回
  -> 远端 COMPLETED/FAILED: 创建 REMOTE_RESUME context
  -> handler.execute(REMOTE_RESUME context)
  -> A2aAgentResultRouter 消费第二段本地 stream
  -> 本地 final output 写 parent completed
```

最新代码中的 `A2aAgentExecutor` 只持有一个 `AgentRuntimeHandler`，不具备读取 parent task metadata、调用远端服务或复用路由器的能力。落地时需要把 executor 构造参数扩展为：`AgentRuntimeHandler`、`TaskStore`、`A2aAgentResultRouter`、`A2aParentTaskProjector` factory、`RemoteAgentInvocationService` 和 `A2aRemoteOutcomeProjector`。`RuntimeAutoConfiguration` 当前已经提供 `InMemoryTaskStore` bean，executor 可通过配置装配拿到同一个 SDK task store。

本地 stream 的生命周期和 `AgentEmitter` 生命周期不同。`REMOTE_INVOCATION` 后可以关闭第一段本地 stream，但只要 `A2aAgentExecutor.execute(...)` 方法尚未返回，当前 `AgentEmitter` 仍可用于 parent task 投影。SDK 不应因为第一段本地 stream 结束就把 parent task 视为终态；终态只由 router/projector 显式调用 `complete/fail/requiresInput/cancel` 决定。

远端调用链路：

```text
 A2aAgentExecutor
  -> RemoteAgentInvocationService.invoke(remoteInvocation)
  -> RemoteAgentInvocationService.OutboundPort.invoke(request)
  -> engine.a2a.A2aRemoteAgentOutboundAdapter
  -> 远端 /a2a message/send 或 message/stream
```

`RemoteAgentInvocationService` 负责用例编排：读取 parent route metadata、构造 neutral request、调用内部 `OutboundPort`、把远端结果整理成 outcome。它不持有 `AgentEmitter`，也不引用 OpenJiuwen 类型。v1 不单独新增 `RemoteAgentOutboundPort.java`；`RemoteAgentRequest`、`RemoteAgentResult`、`RemoteTaskReference` 和 `OutboundPort` 先作为 `RemoteAgentInvocationService` 的嵌套类型。

`A2aRemoteAgentOutboundAdapter` 是唯一持有远端 A2A client 的类。它把 neutral request 转成远端 A2A JSON-RPC，并在内部把远端 task/event 映射成 `RemoteAgentResult`。v1 不单独新增 `A2aRemoteResultMapper.java`。

## Parent Task 投影

`A2aRemoteOutcomeProjector` 把远端 outcome 投影回 parent A2A task。它不直接依赖 `AgentEmitter`，调用方显式传入 `A2aParentTaskProjector`：

```text
A2aAgentExecutor.execute(ctx, emitter)
  -> A2aParentTaskProjector.fromEmitter(emitter)
  -> A2aRemoteOutcomeProjector.project(outcome, projector)
```

首次远端调用和远端 `INPUT_REQUIRED` 后的下一轮用户输入都进入 SDK `RequestHandler`，并由 `A2aAgentExecutor.execute(...)` 获得当前请求的 `AgentEmitter`。因此 v1 的 parent task 投影统一走 emitter-backed 路径。

`A2aParentTaskProjector` 一个文件内收敛 parent task 投影和 remote route metadata 读写：

```text
startWork(parentTaskId)
addRemoteProgress(parentTaskId, message/artifact)
requireInput(parentTaskId, message, metadata)
mergeRemoteRouteMetadata(parentTaskId, metadata)
remoteRoute(parentTaskId)
complete(parentTaskId, message)
fail(parentTaskId, error)
isTerminal(parentTaskId)
```

它不能只写 EventQueue。远端 progress 至少要让 streaming/subscription 可见；远端 input-required 必须同时更新 parent `Task.status`、`status.message`、`Task.metadata`；`tasks/get` 也必须能从 `TaskStore` 读到已经投影的状态和 metadata。v1 通过 SDK `AgentEmitter` 写这些状态和事件，不在 controller 层手写 A2A JSON-RPC response 或 SSE event。

本地 A2A SDK 的 `TaskManager` 会把 `TaskStatusUpdateEvent.metadata` merge 进已有 `Task.metadata`。因此 projector 保存远端路由字段时应优先通过 SDK status event / emitter 能力写入 metadata，不绕过 SDK 状态机直接改内存对象。只有读取 route 时才从 `ctx.getTask()` 或同一个 `TaskStore` 取 parent task 快照。

`requireInput(parentTaskId, message, metadata)` 必须采用 metadata merge 策略：保留已有 `runtime.remoteTaskId`、`runtime.remoteContextId`、`runtime.toolCallId`、`runtime.localConversationId` 等路由关键字段，只覆盖新的远端 prompt、时间戳、诊断字段和必要的 remote status。远端多轮 `INPUT_REQUIRED` 时，parent `status.message` 更新为最新远端提示，但路由 metadata 仍指向同一个 remote task/context。

远端消息统一归一化 disposition：

| disposition | 默认来源 | 处理 |
| --- | --- | --- |
| `USER_PROGRESS` | terminal 前的远端 message/artifact/progress | 写 parent progress/artifact event，用户可见；不恢复本地 OpenJiuwen。 |
| `USER_INPUT_REQUIRED` | 远端 `TASK_STATE_INPUT_REQUIRED.status.message` | 写 parent `TASK_STATE_INPUT_REQUIRED`、用户可见 prompt/parts、remote route metadata；不恢复本地 OpenJiuwen。 |
| `TOOL_RESULT` | 远端 `COMPLETED/FAILED` terminal result | 生成 `toolResult` 字符串，恢复本地 OpenJiuwen tool_call。 |
| `INTERNAL` | remote task/context/trace/diagnostic metadata | 只用于路由或诊断，不展示给用户，也不作为 tool result。 |

字段名：

```text
runtime.remoteEventDisposition = USER_PROGRESS | USER_INPUT_REQUIRED | TOOL_RESULT | INTERNAL
```

远端 metadata 可以提供 visibility hint，但最终是否展示、是否回灌本地 agent、是否更新 parent status，都由本地 runtime 裁决。

## 远端 Completed / Failed

远端 terminal result 只作为本地 tool result：

```text
远端 TASK_STATE_COMPLETED 或终态失败
  -> A2aRemoteAgentOutboundAdapter 返回 RemoteAgentResult
  -> RemoteAgentInvocationService 生成 remote outcome
  -> A2aRemoteOutcomeProjector 创建 REMOTE_RESUME AgentExecutionContext
  -> 当前 AgentRuntimeHandler.execute(REMOTE_RESUME context)
  -> OpenJiuwenMessageAdapter 创建 InteractiveInput.update(toolCallId, toolResult)
  -> 同一个 conversation_id 恢复被中断的 tool_call
  -> 本地 OpenJiuwen 继续推理
  -> 本地 agent 写 parent final answer
```

`AgentExecutionContext.variables` 传递：

```text
runtime.remoteToolCallId
runtime.remoteToolResult
runtime.remoteInvocationId
agentStateKey = original local conversation id
```

v1 不新增 `RemoteAgentResumePayload`。`runtime.remoteToolResult` 是字符串：普通成功用文本；结构化成功或失败用 JSON 字符串。`InteractiveInput` 只在 `engine.openjiuwen` 内部出现，`engine.service` 和 `engine.a2a` 不引用 OpenJiuwen 类型。

runtime 不保存 `checkpointRef`。OpenJiuwen 在最初 tool call interrupt 时已经保存 `ToolInterruptionState`；runtime 只保证 re-enter 时使用同一个 `agentStateKey` / `conversation_id`，并传入匹配的 `toolCallId` 和 `toolResult`。

## 远端 INPUT_REQUIRED 与下一轮用户输入

远端 SSE 可能先返回多个 message/artifact，最后才把 task 状态更新为 `TASK_STATE_INPUT_REQUIRED`。处理规则：

- terminal 前的远端 message/artifact 写成 parent `USER_PROGRESS`，用户可见，但不设置 `runtime.waitingTarget=REMOTE_AGENT`。
- 只有远端最终 `TASK_STATE_INPUT_REQUIRED` 才把 parent task 切到 `TASK_STATE_INPUT_REQUIRED`。
- parent input-required message 必须包含远端 status message 中用户可见的 prompt/parts。
- `REMOTE_INPUT_REQUIRED` 不 re-enter 本地 OpenJiuwen；本地 OpenJiuwen 继续停在最初 remote tool_call 的 interrupt state。

parent task metadata 至少保存：

```text
runtime.waitingTarget = REMOTE_AGENT
runtime.remoteInvocationId
runtime.remoteAgentId
runtime.remoteTaskId
runtime.remoteContextId
runtime.toolCallId
runtime.localConversationId
```

下一轮用户输入仍进入本地 `/a2a`，继续委托 SDK `RequestHandler`。本地 A2A SDK 源码已经支持这条路径：当 `Message.taskId` 指向一个非终态已有 task 时，`DefaultRequestHandler` 会从 `TaskStore` 读取该 task，校验 `contextId`，把用户新消息追加到 task，并重新创建 `RequestContext` 调用 `AgentExecutor.execute(ctx, emitter)`。因此 v1 不需要 controller pre-dispatch router，也不新增 `A2aRemoteResumeRouter`。

客户端下一轮请求必须携带 parent `taskId` 和匹配的 `contextId`。如果不携带 taskId，SDK 会创建新 parent task，runtime 将无法从旧 parent task metadata 中识别远端 continuation。

```text
用户下一轮输入
  -> A2A SDK RequestHandler
  -> A2aAgentExecutor.execute(ctx, emitter)
  -> executor 从 parent task metadata 读取 remote route
  -> task.status == TASK_STATE_INPUT_REQUIRED
  -> metadata.runtime.waitingTarget == REMOTE_AGENT
  -> emitter.startWork()
  -> RemoteAgentInvocationService.resumeRemoteInput(...)
  -> 将用户输入发送给同一个 remote task/context
  -> 远端 INPUT_REQUIRED: 用当前 emitter 更新 parent input-required message 和 metadata
  -> 远端 COMPLETED/FAILED: 用 tool result 创建 REMOTE_RESUME context
  -> handler.execute(REMOTE_RESUME context)
  -> A2aAgentResultRouter 消费本地恢复后的 result stream
```

`A2aJsonRpcController` 保持委托 SDK `RequestHandler`。blocking 与 streaming 响应格式继续由 SDK `RequestHandler` 和当前 controller 序列化路径保证。`A2aAgentExecutor` 只在 `TASK_STATE_INPUT_REQUIRED + runtime.waitingTarget=REMOTE_AGENT` 时进入 remote continuation branch；其他请求保持当前本地 OpenJiuwen 执行路径。

远端 resume 后如果再次返回 `INPUT_REQUIRED`，executor 只更新 parent 的 input-required message 和合并 metadata，不 re-enter 本地 OpenJiuwen；远端最终 `COMPLETED/FAILED` 后才生成 `runtime.remoteToolResult` 并 re-enter 本地 OpenJiuwen。

## 失败策略

| 场景 | v1 行为 |
| --- | --- |
| 远端 card 暂时不可用 | 保持 `PENDING`，按固定间隔持续后台重试；不阻塞本地 runtime 启动和用户请求。 |
| 已注册远端运行时调用失败 | 生成同一 `toolCallId` 的错误 JSON 字符串，resume 本地 OpenJiuwen；不直接 failed parent task。 |
| 远端调用 + 本地 re-enter 总耗时超过 runtime 内部保护时长 | `RemoteAgentInvocationService` 负责计时，默认 60 秒；超时后 parent task 置为 failed，错误码 `REMOTE_INVOCATION_TIMEOUT`，并 best-effort 取消远端 task。 |
| parent task 在远端调用期间被取消 | `A2aAgentExecutor.cancel(...)` 先把 parent task 置为 canceled；如果 metadata 中已有 remote task/context，则 best-effort 调远端 `tasks/cancel`。取消传播失败不改变 parent canceled 状态。 |
| 远端超时或取消后的迟到结果 | `A2aParentTaskProjector.isTerminal(parentTaskId)` 为 true 时丢弃迟到 outcome，只记录日志，不再回灌本地 OpenJiuwen。 |
| 远端 card 在运行中变化 | v1 不感知运行时变化；重启本地 runtime 后重新拉取 card 并生成 tool。 |
| 远端并发过高 | v1 不暴露并发配置；实际并发受 SDK executor、远端 HTTP client 连接池和远端 runtime 能力共同限制。生产化阶段再增加 remote invocation concurrency / connection pool 配置。 |

## 需要修改的现有文件

| 文件 | 修改点 |
| --- | --- |
| `agent-runtime/pom.xml` | 增加远端 A2A client 所需依赖，供 `engine.a2a.A2aRemoteAgentOutboundAdapter` 使用。 |
| `boot/A2aJsonRpcController.java` | 保持委托 SDK `RequestHandler`；`CancelTask` 继续进入 SDK cancel，并由 executor/projector best-effort 传播远端取消。 |
| `boot/RuntimeAutoConfiguration.java` | 绑定 `agent-runtime.remote-agents[].url`，装配 catalog、invocation service、A2A outbound adapter、OpenJiuwen installer；创建 `A2aAgentExecutor` 时注入 SDK `TaskStore`、result router、parent task projector factory、remote invocation service 和 outcome projector。remote properties 先作为本配置类内部 record/bean 方法，不单独建文件。 |
| `engine/a2a/A2aAgentExecutor.java` | 移除内部 `results.forEach(...)` 和私有 `route(...)` 主逻辑，改为创建 emitter-backed `A2aParentTaskProjector` 后委托 `A2aAgentResultRouter`；在 `execute(...)` 开头通过 `ctx.getTask()` / `TaskStore` 读取 parent task metadata，识别 `TASK_STATE_INPUT_REQUIRED + runtime.waitingTarget=REMOTE_AGENT` 后直接 resume 远端，不进入 OpenJiuwen 本地推理。 |
| `engine/spi/AgentExecutionResult.java` | 新增 `REMOTE_INVOCATION` 类型、factory、`remoteInvocation()` getter 和嵌套 `RemoteInvocation` record；保留 `INTERRUPTED(prompt)` 给普通本地 input-required。 |
| `engine/openjiuwen/OpenJiuwenAgentRuntimeHandler.java` | 在现有 `openJiuwenRails(context)` 之外增加可选 `OpenJiuwenRemoteToolInstaller` 字段和 `installRuntimeTools(...)` hook；执行顺序为 `createOpenJiuwenAgent` -> `installRails` -> `installRuntimeTools` -> `toOpenJiuwenInput` -> `runOpenJiuwenAgent`。 |
| `engine/openjiuwen/OpenJiuwenStreamAdapter.java` | 识别 OpenJiuwen remote interrupt marker，构造 `AgentExecutionResult.remoteInvocation(remoteInvocation)`；普通 interrupt 仍映射为 `interrupted(prompt)`。 |
| `engine/openjiuwen/OpenJiuwenMessageAdapter.java` | 支持 `inputType=REMOTE_RESUME`；从 variables 读取 `runtime.remoteToolCallId` 和 `runtime.remoteToolResult`，转换为 `InteractiveInput.update(...)`。 |
| `engine/AgentExecutionContext.java` | 继续用 `variables` 传递 remote resume 信息；新增输入类型约定 `REMOTE_RESUME` 和相关 key 常量。 |

## 代码改动清单

新增文件控制在现有 `boot + engine.*` 包结构内；`RuntimePackageBoundaryTest` 不需要放宽。

```text
agent-runtime/src/main/java/com/huawei/ascend/runtime/
  boot/
    RuntimeAutoConfiguration.java  (修改)
  engine/
    service/
      RemoteAgentCatalog.java
      RemoteAgentInvocationService.java
    a2a/
      A2aAgentResultRouter.java
      A2aParentTaskProjector.java
      A2aRemoteAgentOutboundAdapter.java
      A2aRemoteOutcomeProjector.java
    openjiuwen/
      OpenJiuwenRemoteAgentInterruptRail.java
      OpenJiuwenRemoteToolInstaller.java
```

| 文件/包 | 作用 |
| --- | --- |
| `boot/RuntimeAutoConfiguration.java` | 在现有配置类中绑定 `agent-runtime.remote-agents[].url` 并装配新增组件；v1 不单独新增 `RemoteAgentRuntimeProperties.java`。 |
| `engine/service/RemoteAgentCatalog.java` | 管理 remote url 的 `PENDING / AVAILABLE`，后台持续重试拉 card，成功后生成 remote descriptor/tool spec 且不再刷新；拉 card 逻辑先内聚在本类，v1 不单独拆 card client。 |
| `engine/service/RemoteAgentInvocationService.java` | 远端调用用例编排类，供 `A2aAgentExecutor` 调用；内部定义 `OutboundPort`、`RemoteAgentRequest`、`RemoteAgentResult`、`RemoteTaskReference`，并由 A2A outbound adapter 实现端口；负责 60 秒总耗时保护和 best-effort remote cancel。v1 不再单独新增接口和 `Default*` 实现类。 |
| `engine/a2a/A2aAgentResultRouter.java` | 共享 result 路由循环，处理 `REMOTE_INVOCATION` 并控制 stream 停止/继续；使用可短路循环并保证关闭 stream。 |
| `engine/a2a/A2aParentTaskProjector.java` | parent task/event 投影和 remote route metadata 读写；v1 只实现 emitter-backed 投影；metadata merge、terminal guard 和迟到 outcome 丢弃逻辑也放在这里。 |
| `engine/a2a/A2aRemoteAgentOutboundAdapter.java` | 唯一持有远端 A2A client 的 adapter；负责远端 JSON-RPC 调用和结果归一化。 |
| `engine/a2a/A2aRemoteOutcomeProjector.java` | 把远端 outcome 投影到 parent task；input-required 只更新 parent，不 re-enter；completed/failed 时 re-enter 本地 handler，并由调用方显式传入当前 projector。 |
| `engine/openjiuwen/OpenJiuwenRemoteAgentInterruptRail.java` | 识别远端 tool call，写 interrupt context，并触发 OpenJiuwen interrupt。 |
| `engine/openjiuwen/OpenJiuwenRemoteToolInstaller.java` | 在 OpenJiuwen handler 每次执行本地 agent 前读取 catalog，把 remote `ToolCard`、placeholder `Tool`、remote rail 安装到当前 agent 实例。 |

文件内聚规则：

- 远端调用意图放在 `AgentExecutionResult.RemoteInvocation`。
- 远端出站端口和 request/result/reference 放在 `RemoteAgentInvocationService` 内部。
- 远端 card 拉取放在 `RemoteAgentCatalog` 内部。
- 远端 result 映射放在 `A2aRemoteAgentOutboundAdapter` 内部。
- parent task metadata key、读写和 route 判断放在 `A2aParentTaskProjector` 内部。
- OpenJiuwen `InteractiveInput` 转换和 remote tool adapter 逻辑分别放在 `OpenJiuwenMessageAdapter` 和 `OpenJiuwenRemoteToolInstaller` 内部。

## 验证策略

| 测试位置 | 验证点 |
| --- | --- |
| `engine/openjiuwen` | installer 把 `ToolCard`、placeholder `Tool`、remote rail 注册到同一个 agent 实例。 |
| `engine/openjiuwen` | remote rail 只触发 interrupt，不调用 `RemoteAgentInvocationService` 和 A2A client。 |
| `engine/openjiuwen` | `OpenJiuwenStreamAdapter` 能从 remote interrupt state/context 构造 `AgentExecutionResult.RemoteInvocation`。 |
| `engine/openjiuwen` | `OpenJiuwenMessageAdapter` 能用 `toolCallId + toolResult` 构造 `InteractiveInput.update(...)`，并保持同一 `conversation_id`。 |
| `engine/a2a` | `A2aAgentResultRouter` 遇到 `REMOTE_INVOCATION` 后返回 remote invocation decision、停止继续消费本地 stream，并关闭 raw/adapted stream。 |
| `engine/a2a` | `A2aAgentExecutor` 在同一个 `execute(...)` 内关闭第一段 stream 后调用远端；远端 completed/failed 后再打开 `REMOTE_RESUME` 第二段 handler stream，并复用当前 emitter 投影结果。 |
| `engine/a2a` | `A2aAgentExecutor` 开头只在 `TASK_STATE_INPUT_REQUIRED + runtime.waitingTarget=REMOTE_AGENT` 时进入 remote continuation branch；其他 input-required 仍走本地 OpenJiuwen 路径。 |
| `engine/a2a` | `A2aRemoteOutcomeProjector` 对 completed/failed re-enter 本地 handler；对 input-required 只写 parent task，不 re-enter。 |
| `engine/a2a` | `A2aParentTaskProjector` 的 progress 可被 stream/subscribe 消费，input-required 可被 `tasks/get` 查询到；重复 input-required 时 metadata merge 不丢 route 字段。 |
| `engine/a2a` | outbound adapter 保留远端 `INPUT_REQUIRED.status.message` 的 parts 和 metadata。 |
| `engine/a2a + engine/service` | 集成测试覆盖 `A2aAgentExecutor + A2aAgentResultRouter + RemoteAgentInvocationService + fake OutboundPort`：远端 completed 会 re-enter 本地 handler，远端 input-required 不 re-enter。 |
| `boot + engine/a2a` | 集成测试验证 parent task 处于 `TASK_STATE_INPUT_REQUIRED` 时，下一轮 `SendMessage` / `SendStreamingMessage` 会重新进入 SDK `AgentExecutor.execute(ctx, emitter)`。 |
| `boot + engine/a2a` | 集成测试覆盖 remote continuation：下一轮用户输入能用新的 emitter 路由到同一 remote task/context，并保持 A2A response/SSE 格式与 SDK 原路径一致。 |
| `engine/service` | timeout/cancel 测试覆盖：超时置 parent failed，parent canceled 后迟到 outcome 不再回灌本地 OpenJiuwen。 |
| `engine/service` | 远端 card 拉取失败保持 `PENDING` 并按固定间隔持续重试；成功后转 `AVAILABLE`，不再刷新。 |
| `boot` | blocking `SendMessage` 和 streaming `SendStreamingMessage` 都继续委托 SDK `RequestHandler`，不绕过 SDK 序列化与 SSE 包装。 |
| `architecture` | 新增文件仍符合当前 package boundary。 |
| e2e example | 两个 runtime 启动后，本地 OpenJiuwen 能把远端 runtime 当 tool 调用；远端 completed 和 input-required 两条路径都可手工验证。 |

## 验收标准

- 本地 OpenJiuwen tool/rail 路径中没有 A2A HTTP/JSON-RPC client。
- 本地 LLM 选择远端 tool 后，先中断本地 OpenJiuwen，再由 runtime 调远端。
- 远端 completed/failed 先变成本地 tool result，再恢复本地 OpenJiuwen。
- 远端 input-required 的用户提示能通过 parent A2A task/stream 被用户看到。
- 用户下一轮输入能根据 parent task metadata 回到同一个 remote task/context。
- 远端多轮 input-required 时，parent prompt 更新为最新远端提示，route metadata 不丢失。
- runtime 不保存 OpenJiuwen checkpoint blob，不新增 `checkpointRef`。
- remote completed 的本地恢复复用原始 `agentStateKey` / `conversation_id`，不创建新的 parent task。
- parent task 超时或取消后，远端迟到结果不会重新写 parent task，也不会恢复本地 OpenJiuwen。
