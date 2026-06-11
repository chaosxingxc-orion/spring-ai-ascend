# 远端 A2A Agent 工具调用落地方案

本文定义在 `agent-runtime` 中把远端 A2A runtime 注入为本地 OpenJiuwen agent 工具的 v1 落地方案。方案以 A2A `Task`、`TaskStore`、`EventQueue`、task status 和 task metadata 为主线；不引入独立 invocation store，不把 OpenJiuwen checkpoint 纳入 runtime 管理。

## 已定边界

- v1 只适配 OpenJiuwen，不适配 AgentScope 或其他框架。
- v1 采用“一远端 runtime 一个本地 tool”，不把远端 card 内多个 skill 拆成多个 tool。
- 本地 LLM 选择远端 tool 后，统一先中断本地 OpenJiuwen agent，再由 runtime 调远端 A2A；本地 tool 的普通 `invoke(...)` 不是远端调用主入口。
- OpenJiuwen `Tool` 和 `Rail` 不持有 A2A HTTP/JSON-RPC client；远端 A2A client 只在 `engine.a2a.A2aRemoteAgentOutboundAdapter` 中出现。
- runtime 只保存 parent A2A task metadata、remote task/context、toolCallId、local conversation id 等路由信息；OpenJiuwen checkpoint 由 OpenJiuwen 自己保存和恢复。
- parent A2A `Task.metadata` 是远端调用路由事实源；v1 不新增独立 invocation store。
- 远端 terminal result 默认作为 tool result 回灌给本地 OpenJiuwen，不直接作为 parent final output。
- 远端 `INPUT_REQUIRED` 必须投影为 parent A2A task 的 `TASK_STATE_INPUT_REQUIRED`，下一轮用户输入必须路由回同一个 remote task/context。
- v1 的远端调用是同步阻塞编排：首次远端调用发生在 `A2aAgentExecutor.execute(...)` 方法内部，远端 completed/failed 后立刻 re-enter 本地 OpenJiuwen；不引入后台 watcher 或异步回调线程。
- 远端 `INPUT_REQUIRED` 后的下一轮用户输入仍进入 A2A SDK `RequestHandler` 和 `A2aAgentExecutor.execute(...)`；executor 开头识别 remote continuation，直接 resume 远端，不进入 OpenJiuwen 本地推理。
- v1 单个 parent task 同一时刻只支持一个远端 tool call。第一个 `REMOTE_INVOCATION` 会中断并关闭本地 result stream；同一轮推理中出现多个并行远端 tool call 时返回明确错误，不做并发 fan-out。

## 端到端链路

启动与注入链路：

```text
远端 runtime URL 配置
  -> RemoteAgentCatalog 异步发现远端 AgentCard
  -> 生成一个 remote tool spec
  -> OpenJiuwen handler 执行期注入 ToolCard + placeholder Tool + remote rail
  -> 本地 LLM 看见远端 runtime 是一个普通 tool
```

首次远端调用链路：

```text
本地 LLM 选择远端 tool
  -> OpenJiuwenRemoteAgentInterruptRail 触发 OpenJiuwen interrupt
  -> OpenJiuwen 保存 ToolInterruptionState
  -> OpenJiuwenStreamAdapter 映射为 AgentExecutionResult.REMOTE_INVOCATION
  -> A2aAgentExecutor 内部 routeResult 返回 remote invocation decision
  -> A2aAgentExecutor 关闭第一段 raw/adapted stream
  -> RemoteAgentInvocationService 调用远端 A2A
  -> A2aRemoteAgentOutboundAdapter 调用远端 /a2a
  -> 远端 progress/input-required/completed 投影到 parent A2A task
```

远端 completed/failed 后的恢复链路：

```text
远端 TASK_STATE_COMPLETED 或终态失败
  -> RemoteAgentInvocationService 生成 RemoteAgentResult
  -> A2aAgentExecutor 创建 REMOTE_RESUME AgentExecutionContext
  -> OpenJiuwenMessageAdapter 创建 InteractiveInput.update(toolCallId, toolResult)
  -> 同一个 conversation_id 恢复被中断的 tool_call
  -> 本地 OpenJiuwen 继续推理
  -> 本地 agent 写 parent final answer
```

远端 input-required 后的下一轮输入链路：

```text
用户下一轮输入
  -> A2A SDK RequestHandler
  -> A2aAgentExecutor.execute(ctx, emitter)
  -> executor 读取 parent task metadata
  -> TASK_STATE_INPUT_REQUIRED + runtime.waitingTarget == REMOTE_AGENT
  -> 不调用 OpenJiuwen
  -> RemoteAgentInvocationService.resumeRemoteInput(...)
  -> 远端 INPUT_REQUIRED: 用当前 emitter 更新 parent input-required
  -> 远端 COMPLETED/FAILED: 用 tool result re-enter 本地 OpenJiuwen
```

这个闭环的关键约束是：远端调用发生在 runtime；远端 terminal result 先回到本地 agent，本地 agent 再决定最终如何答复用户。

## 远端 Card 发现与 Tool 注册

v1 静态配置远端 runtime，只需要配置 `url`：

```yaml
agent-runtime:
  remote-agents:
    - url: http://localhost:18081
```

当 `url` 是 runtime 根地址时，默认读取 `${url}/.well-known/agent-card.json`；当 `url` 已经指向 card 文件时，直接读取该地址。本地不配置 `id`、`base-url`、`card-path`、`enabled`、timeout 或 tool name override。

发现规则：

- `RemoteAgentCatalog` 按规范化 card URL 去重；同一个远端 runtime 被配置多次时只保留一个。
- 启动后把每个 URL 注册为 `PENDING`，后台按固定间隔持续重试。
- 拉取 card 成功后转为 `AVAILABLE`，缓存 descriptor/tool spec，成功后不再刷新。
- 拉取失败不阻塞本地 runtime 启动和用户请求；`PENDING` 的远端不注入 tool。
- v1 不做 TTL、不做运行时刷新、不做后台健康探测；远端 card 变化后通过重启本地 runtime 重新发现。

远端 endpoint 必须来自远端 `AgentCard` 的 JSON-RPC interface 或 card URL 推导，不由本地额外配置重复描述。catalog 只负责发现、缓存和提供 `AVAILABLE` 的 remote tool spec，不直接修改任何本地 agent 实例。

## OpenJiuwen 注入链路

注入必须发生在 handler 定义的执行生命周期里：拿到本次要执行的 OpenJiuwen agent 实例之后、调用 `Runner.runAgent(...)` 或 `agent.stream(...)` 之前。

```text
OpenJiuwenAgentRuntimeHandler.execute(context)
  -> createOpenJiuwenAgent(context)
  -> installRails(agent, context)
  -> installRuntimeTools(agent, context)
  -> toOpenJiuwenInput(context)
  -> runOpenJiuwenAgent(agent, input, openJiuwenConversationId(context))
```

`openJiuwenRails(context)` 只能返回 `AgentRail`，不能完整安装远端 tool，因为远端注入还需要注册 `ToolCard` 和 placeholder `Tool`。因此 v1 在 `OpenJiuwenAgentRuntimeHandler` 增加 runtime tool installer hook：`installRuntimeTools(BaseAgent agent, AgentExecutionContext context)`。

`OpenJiuwenRemoteToolInstaller` 对同一个 agent 实例完成三件事：

```text
agent.getAbilityManager().add(remote ToolCard)
Runner.resourceMgr().addTool(placeholder Tool, agent.getCard().getId())
agent.registerRail(OpenJiuwenRemoteAgentInterruptRail)
```

`installRuntimeTools(...)` 必须在 `installRails(...)` 之后、`toOpenJiuwenInput(...)` 之前执行。这样框架自有 rail 先完成注册，remote rail 后注册，并且只拦截远端 tool name；非远端 tool call 必须放行给 OpenJiuwen 原有能力链。

`ToolCard` 让 LLM 看见远端能力；placeholder `Tool` 只满足 OpenJiuwen resource manager 的 tool name 解析；`OpenJiuwenRemoteAgentInterruptRail` 是真实远端调用入口。placeholder `Tool.invoke(...)` 和 `stream(...)` 不访问远端，rail 生效时它不会被执行；rail 未生效时它返回明确错误，避免静默伪成功。

rail 需要知道本次 parent task/context。这个信息由 installer 创建 rail 时从本次 `AgentExecutionContext` 绑定进去，不从全局变量读取，也不让 OpenJiuwen agent 本身感知 A2A task。

## 数据模型与上下文

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

这段 schema 是 `RemoteAgentToolSpec` 暴露给 OpenJiuwen `ToolCard` 的输入描述，不是 A2A JSON-RPC 报文。含义是：本地 LLM 调用远端 tool 时只给出一个 `message` 字符串；runtime 再把该字符串转换成 A2A `Message.parts[TextPart]` 发给远端 runtime。

v1 使用宽松 schema 的原因是：A2A AgentCard 描述的是 agent 能力、入口和 skills，不一定提供函数级 JSON Schema。为了降低本地 LLM 误用风险，`RemoteAgentCatalog` 生成 `ToolCard.description` 时必须合成远端 `AgentCard.name`、`AgentCard.description` 和 `skills[].description`，不能只写通用描述。

OpenJiuwen remote rail 写入 `InterruptRequest.context`：

```text
runtime.remote.kind = REMOTE_AGENT_INVOCATION
runtime.remote.agentId
runtime.remote.toolName
runtime.remote.toolCallId
runtime.remote.parentTaskId
runtime.remote.parentContextId
runtime.remote.localConversationId
runtime.remote.arguments
```

`OpenJiuwenStreamAdapter` 从 interrupt state/context 读取上述字段，并构造框架中立结果：

```text
Type = OUTPUT / COMPLETED / FAILED / INTERRUPTED / REMOTE_INVOCATION
AgentExecutionResult.remoteInvocation(RemoteInvocation remoteInvocation)

RemoteInvocation =
  remote agent id
  tool name
  toolCallId
  parent task/context
  local conversation id
  arguments
```

现有 `INTERRUPTED(prompt)` 继续表示普通本地 input-required；远端 tool call 使用独立 `REMOTE_INVOCATION`，避免把 remote agent id、toolCallId、arguments、parent task/context 塞进 prompt 字符串。v1 不单独新增 `RemoteAgentInvocationIntent.java`。

远端 completed/failed 恢复本地 OpenJiuwen 时，`AgentExecutionContext.variables` 传递：

```text
inputType = REMOTE_RESUME
runtime.remoteToolCallId
runtime.remoteToolResult
runtime.remoteInvocationId
agentStateKey = original local conversation id
```

`runtime.remoteToolResult` 是字符串：普通成功用文本；结构化成功或失败用 JSON 字符串。v1 不新增 `RemoteAgentResumePayload`，也不保存 `checkpointRef`。OpenJiuwen 在最初 tool call interrupt 时已经保存 `ToolInterruptionState`；runtime 只保证 re-enter 时使用同一个 `agentStateKey` / `conversation_id`，并传入匹配的 `toolCallId` 和 `toolResult`。

实现前置验证：必须用单元测试证明 OpenJiuwen interrupt result/state 能把 remote context 带回 `OpenJiuwenStreamAdapter`。验证不通过时，才在 runtime 侧增加一个按 `toolCallId` 索引的临时 intent cache；该 cache 只补偿 OpenJiuwen context 承载限制，不作为远端调用状态事实源。

## 执行路由与远端调用

`A2aAgentExecutor` 保持顶层编排职责，构造参数收敛为：

```text
A2aAgentExecutor(AgentRuntimeHandler handler, A2aAgentExecutor.RemoteSupport remoteSupport)
```

`RemoteSupport` 聚合远端相关依赖，避免 executor 构造器平铺多个参数：

```text
RemoteSupport =
  RemoteAgentInvocationService
```

v1 不单独新增 `A2aAgentResultRouter.java`。`AgentExecutionResult` stream 只由 `A2aAgentExecutor` 使用，且路由过程强依赖当前 `AgentEmitter`、parent task 和是否允许远端调用；拆成独立文件会增加参数传递和边界复杂度。`A2aAgentExecutor` 内部保留可短路的 `consumeHandler(...)` / `routeResult(...)` / `RouteDecision`：

```text
OUTPUT            -> 写 parent artifact/progress，继续消费
COMPLETED         -> 写 parent completed，停止消费
FAILED            -> 写 parent failed，停止消费
INTERRUPTED       -> 写 parent input-required，停止消费
REMOTE_INVOCATION -> 返回 remote invocation decision，停止消费
未知类型           -> 抛 IllegalStateException
```

`REMOTE_INVOCATION` 必须停止继续消费本地 handler stream，不能让后续 `COMPLETED` 抢先写入 parent task。实现上不能继续使用 `Stream.forEach(...)`；必须使用可短路循环，例如在 `try-with-resources` 中通过 `Iterator` 消费 result，遇到 `COMPLETED`、`FAILED`、`INTERRUPTED` 或 `REMOTE_INVOCATION` 后 `break`，并确保 raw stream 与 adapted stream 都被关闭。

首次远端调用的控制流：

```text
A2aAgentExecutor.execute(ctx, emitter)
  -> emitter.startWork()
  -> 如果 parent task 是 remote waiting，进入 remote continuation branch
  -> 否则调用本地 handler.execute(USER_MESSAGE context)
  -> consumeHandler(...) 消费第一段本地 stream
  -> 遇到 REMOTE_INVOCATION decision
  -> 关闭第一段 raw/adapted stream
  -> RemoteAgentInvocationService.invoke(remoteInvocation, progressCallback)
  -> 远端 INPUT_REQUIRED: A2aParentTaskProjector.requireInput(...) 后返回
  -> 远端 COMPLETED/FAILED: 创建 REMOTE_RESUME context
  -> handler.execute(REMOTE_RESUME context)
  -> consumeHandler(...) 消费第二段本地 stream
```

本地 stream 的生命周期和 `AgentEmitter` 生命周期不同。`REMOTE_INVOCATION` 后可以关闭第一段本地 stream，但只要 `A2aAgentExecutor.execute(...)` 方法尚未返回，当前 `AgentEmitter` 仍可用于 parent task 投影。SDK 不应因为第一段本地 stream 结束就把 parent task 视为终态；终态只由 router/projector 显式调用 `complete/fail/requiresInput/cancel` 决定。

远端调用链路：

```text
A2aAgentExecutor
  -> RemoteAgentInvocationService.invoke(remoteInvocation, progressCallback)
  -> RemoteAgentInvocationService.OutboundPort.invoke(request)
  -> engine.a2a.A2aRemoteAgentOutboundAdapter
  -> 远端 /a2a message/send 或 message/stream
```

`RemoteAgentInvocationService` 负责用例编排：构造 neutral request、调用内部 `OutboundPort`、整理远端结果，并暴露 best-effort remote cancel。它不持有 `AgentEmitter`，也不引用 OpenJiuwen 类型。v1 不单独新增 `RemoteAgentOutboundPort.java`；`RemoteAgentRequest`、`RemoteAgentResult`、`RemoteTaskReference` 和 `OutboundPort` 作为 `RemoteAgentInvocationService` 的嵌套类型。

`A2aRemoteAgentOutboundAdapter` 是唯一持有远端 A2A client 的类。它把 neutral request 转成远端 A2A JSON-RPC，并在内部把远端 task/event 映射成 `RemoteAgentResult`。v1 不单独新增 `A2aRemoteResultMapper.java`。

## Parent Task 投影

`A2aParentTaskProjector` 收敛 parent task 投影、remote route metadata 读写和远端 outcome 处理。不再单独新增 `A2aRemoteOutcomeProjector`。

```text
isRemoteContinuation(ctx)
projectRemoteProgress(result, emitter)
requireRemoteInput(invocation, result, emitter)
remoteRoute(task)
projectRemoteOutcome(invocation, results, emitter)
remoteResumeContext(requestContext, handlerAgentId, invocation, toolResult)
```

它不能只写 EventQueue。远端 progress 至少要让 streaming/subscription 可见；远端 input-required 必须同时更新 parent `Task.status`、`status.message`、`Task.metadata`；`tasks/get` 也必须能从 `TaskStore` 读到已经投影的状态和 metadata。v1 通过 SDK `AgentEmitter` 写这些状态和事件，不在 controller 层手写 A2A JSON-RPC response 或 SSE event。

远端 `INPUT_REQUIRED` 必须使用非 final input-required：

```text
emitter.requiresInput(message, false)
```

如果需要带 metadata，则使用等价的非 final `TaskStatusUpdateEvent`。不能把 input-required 当作 final terminal status 写入，否则后续同一个 `AgentEmitter` 再写 progress、resume 或 complete 时可能触发 SDK terminal guard。

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

blocking 与 streaming 的可见性差异：

- `SendStreamingMessage` / subscribe：远端 progress 通过 parent event queue 实时推给客户端。
- blocking `SendMessage`：客户端通常只在最终响应中看到 terminal task/message；中间 progress 会进入 parent task/event，但不会作为 blocking response 的逐条流返回。

## 续写恢复

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

下一轮用户输入仍进入本地 `/a2a`，继续委托 SDK `RequestHandler`。当 `Message.taskId` 指向一个非终态已有 task 时，SDK 会读取该 task、校验 `contextId`、追加用户新消息，并重新创建 `RequestContext` 调用 `AgentExecutor.execute(ctx, emitter)`。因此 v1 不需要 controller pre-dispatch router，也不新增 `A2aRemoteResumeRouter`。

客户端下一轮请求必须携带 parent `taskId` 和匹配的 `contextId`。如果不携带 `taskId`，SDK 会创建新 parent task，runtime 将无法从旧 parent task metadata 中识别 remote continuation。

remote continuation 分支中，本轮用户输入必须从 `RequestContext.getMessage()` 提取，不从 task history 或 `AgentExecutionContext.messages` 推断。原因是续写时 task history 已包含历史消息，而本轮应发送给远端的是当前用户新增输入。

远端 resume 后如果再次返回 `INPUT_REQUIRED`，executor 只更新 parent 的 input-required message 并 merge metadata，不 re-enter 本地 OpenJiuwen；远端最终 `COMPLETED/FAILED` 后才生成 `runtime.remoteToolResult` 并 re-enter 本地 OpenJiuwen。

`A2aJsonRpcController` 保持委托 SDK `RequestHandler`。blocking 与 streaming 响应格式继续由 SDK `RequestHandler` 和当前 controller 序列化路径保证。`A2aAgentExecutor` 只在 `TASK_STATE_INPUT_REQUIRED + runtime.waitingTarget=REMOTE_AGENT` 时进入 remote continuation branch；其他请求保持当前本地 OpenJiuwen 执行路径。

## 失败、取消与资源约束

| 场景 | v1 行为 |
| --- | --- |
| 远端 card 暂时不可用 | 保持 `PENDING`，按固定间隔持续后台重试；不阻塞本地 runtime 启动和用户请求。 |
| 已注册远端运行时调用失败 | 生成同一 `toolCallId` 的错误 JSON 字符串，resume 本地 OpenJiuwen；不直接 failed parent task。 |
| 远端流式调用超过 runtime 内部保护时长 | v1 在 `A2aRemoteAgentOutboundAdapter` 的远端 SSE 等待处做 60 秒保护；超时被归一化为远端 failed result，再作为同一 `toolCallId` 的错误 JSON resume 本地 OpenJiuwen，不直接 failed parent task。 |
| parent task 在远端调用期间被取消 | `A2aAgentExecutor.cancel(...)` 先把 parent task 置为 canceled；如果 metadata 中已有 remote task/context，则 best-effort 调远端 `tasks/cancel`。取消传播失败不改变 parent canceled 状态。 |
| 远端超时或取消后的迟到结果 | v1 是同步阻塞模型，不引入后台 watcher 或异步回调，因此没有独立的迟到 outcome 回灌通道；transport 超时会被当前调用同步归一化为 failed result。 |
| 远端 card 在运行中变化 | v1 不感知运行时变化；重启本地 runtime 后重新拉取 card 并生成 tool。 |
| 远端并发过高 | v1 不暴露并发配置；实际并发受 SDK executor、远端 HTTP client 连接池和远端 runtime 能力共同限制。生产化阶段再增加 remote invocation concurrency / connection pool 配置。 |

## 落地清单

需要修改的既有文件：

| 文件 | 职责边界 | 本次修改 |
| --- | --- | --- |
| `agent-runtime/pom.xml` | 只声明 `agent-runtime` 编译和运行所需依赖，不承载任何远端调用逻辑。 | 增加远端 A2A client transport/http 依赖，供 `engine.a2a.A2aRemoteAgentOutboundAdapter` 编译使用。 |
| `boot/RuntimeAutoConfiguration.java` | 做 runtime Spring bean 装配和生命周期启动；不承载配置结构定义，不写远端调用流程，不解析 OpenJiuwen interrupt，不直接投影 parent task。v1 为减少新增文件，暂时在这里接入 OpenJiuwen installer。 | 启用 `RemoteAgentProperties`；装配 catalog、invocation service、A2A outbound adapter、OpenJiuwen installer、`A2aAgentExecutor.RemoteSupport` 和内部 catalog refresher；bean 创建阶段不做同步 card 拉取。 |
| `boot/RemoteAgentProperties.java` | 只定义 `agent-runtime.remote-agents[].url` 的配置绑定模型，并提供过滤空 URL 后的 `urls()` 视图；不创建 bean、不拉 card、不装配远端调用组件。 | 新增配置文件读取对象，远端 URL 仍是 list，支持一个或多个远端 runtime。 |
| `engine/a2a/A2aAgentExecutor.java` | A2A SDK `AgentExecutor` 入口和顶层编排者。负责判断本轮是本地 OpenJiuwen 执行还是 remote continuation，负责同步阻塞地调远端和 re-enter 本地 handler；不持有远端 A2A client，不直接注册 OpenJiuwen tool。 | 移除 `results.forEach(...)`，改为内部可短路的 `consumeHandler(...)` / `routeResult(...)` / `RouteDecision`；通过 `RemoteSupport` 使用 parent projector 和 remote invocation service；在 `execute(...)` 开头通过 `ctx.getTask()` 识别 remote continuation。 |
| `engine/spi/AgentExecutionResult.java` | 框架中立的 handler 执行结果模型。只表达结果类型和必要 payload，不依赖 A2A SDK、OpenJiuwen 类型或 service 层类型。 | 新增 `REMOTE_INVOCATION` 类型、factory、`remoteInvocation()` getter 和嵌套 `RemoteInvocation` record；保留 `INTERRUPTED(prompt)` 给普通本地 input-required。 |
| `engine/openjiuwen/OpenJiuwenAgentRuntimeHandler.java` | OpenJiuwen handler 生命周期模板。负责创建 agent、安装 rails/runtime tools、转换输入并运行 OpenJiuwen；不持有远端 A2A client，不编排远端调用。 | 在现有 `openJiuwenRails(context)` 之外增加可选 `OpenJiuwenRemoteToolInstaller` 字段和 `installRuntimeTools(...)` hook；执行顺序为 `createOpenJiuwenAgent` -> `installRails` -> `installRuntimeTools` -> `toOpenJiuwenInput` -> `runOpenJiuwenAgent`。 |
| `engine/openjiuwen/OpenJiuwenStreamAdapter.java` | OpenJiuwen result/interrupt 到 `AgentExecutionResult` 的适配器。只解析 OpenJiuwen 结果结构并输出框架中立结果；不调用远端服务，不写 A2A task。 | 识别 OpenJiuwen remote interrupt marker，构造 `AgentExecutionResult.remoteInvocation(remoteInvocation)`；普通 interrupt 仍映射为 `interrupted(prompt)`。 |
| `engine/openjiuwen/OpenJiuwenMessageAdapter.java` | `AgentExecutionContext` 到 OpenJiuwen 输入对象的适配器。OpenJiuwen 专有的 `InteractiveInput` 只能出现在这里或同包实现内。 | 支持 `inputType=REMOTE_RESUME`；从 variables 读取 `runtime.remoteToolCallId` 和 `runtime.remoteToolResult`，转换为 `InteractiveInput.update(...)`。 |
| `engine/AgentExecutionContext.java` | engine 与框架 adapter 之间的中立上下文。负责承载 input type、messages、variables、agentStateKey；不引入 A2A SDK RequestContext 或 OpenJiuwen 类型。 | 继续用 `variables` 传递 remote resume 信息；新增输入类型约定 `REMOTE_RESUME` 和相关 key 常量。 |

新增文件控制在现有 `boot + engine.*` 包结构内；`RuntimePackageBoundaryTest` 不需要放宽。

```text
agent-runtime/src/main/java/com/huawei/ascend/runtime/
  boot/
    RuntimeAutoConfiguration.java  (修改)
    config/
      RemoteAgentProperties.java
  engine/
    service/
      RemoteAgentCatalog.java
      RemoteAgentInvocationService.java
    a2a/
      A2aParentTaskProjector.java
      A2aRemoteAgentOutboundAdapter.java
    openjiuwen/
      OpenJiuwenRemoteAgentInterruptRail.java
      OpenJiuwenRemoteToolInstaller.java
```

文件内聚规则：

- 远端调用意图放在 `AgentExecutionResult.RemoteInvocation`。
- 远端出站端口和 request/result/reference 放在 `RemoteAgentInvocationService` 内部。
- 远端 card 拉取放在 `RemoteAgentCatalog` 内部。
- 远端 result 映射放在 `A2aRemoteAgentOutboundAdapter` 内部。
- parent task metadata key、读写、route 判断和远端 outcome 投影放在 `A2aParentTaskProjector` 内部。
- OpenJiuwen `InteractiveInput` 转换和 remote tool adapter 逻辑分别放在 `OpenJiuwenMessageAdapter` 和 `OpenJiuwenRemoteToolInstaller` 内部。

新增文件职责边界：

| 文件 | 职责边界 | 不负责 |
| --- | --- | --- |
| `engine/service/RemoteAgentCatalog.java` | 管理远端 URL 的 `PENDING / AVAILABLE` 状态；拉取并缓存远端 AgentCard；生成 remote tool spec；按规范化 card URL 去重；失败持续重试，成功后不刷新。 | 不修改本地 agent 实例；不调用远端 task；不保存 invocation 状态；不持有 OpenJiuwen 类型。 |
| `engine/service/RemoteAgentInvocationService.java` | 远端调用用例编排。构造 neutral request，调用内部 `OutboundPort`，统一 `invoke(...)` 与 `resumeRemoteInput(...)` 的结果模型，暴露 best-effort cancel。 | 不持有 `AgentEmitter`；不写 parent task；不引用 OpenJiuwen；不直接实现 A2A HTTP/JSON-RPC；不负责 transport 级 SSE 等待超时。 |
| `engine/a2a/A2aParentTaskProjector.java` | 统一 parent A2A task/status/event/metadata 投影。封装 progress、input-required、metadata merge、remote route 读取、remote outcome 到本地 resume tool result 的转换。首轮和续轮都基于当前请求的 `AgentEmitter` 走同一投影逻辑。 | 不调用远端 A2A；不创建 OpenJiuwen input；不手写 controller response；不实现后台 watcher 或异步迟到 outcome 处理。 |
| `engine/a2a/A2aRemoteAgentOutboundAdapter.java` | 唯一持有远端 A2A client 的 outbound adapter。把 `RemoteAgentInvocationService` 的 neutral request 转成远端 A2A JSON-RPC，消费远端同步/流式结果并归一化为 `RemoteAgentResult`。 | 不写 parent task；不引用 OpenJiuwen；不决定 terminal result 是否作为 tool result 或用户可见消息。 |
| `engine/openjiuwen/OpenJiuwenRemoteAgentInterruptRail.java` | OpenJiuwen 远端 tool rail。识别远端 tool name，读取 tool arguments，写 `InterruptRequest.context`，触发 OpenJiuwen interrupt，让 OpenJiuwen 保存 `ToolInterruptionState`。 | 不调用远端 A2A；不持有 `RemoteAgentInvocationService`；不写 A2A task；不处理远端返回。 |
| `engine/openjiuwen/OpenJiuwenRemoteToolInstaller.java` | 在每次 OpenJiuwen handler 执行期，把 catalog 中 `AVAILABLE` 的远端 tool spec 安装到当前 agent 实例：`ToolCard`、placeholder `Tool`、remote rail。 | 不拉取 card；不调用远端；不缓存 invocation；不改变用户 agent 源码。 |

## 验证与验收

验证策略：

| 测试位置 | 验证点 |
| --- | --- |
| `engine/openjiuwen` | installer 把 `ToolCard`、placeholder `Tool`、remote rail 注册到同一个 agent 实例。 |
| `engine/openjiuwen` | remote rail 只触发 interrupt，不调用 `RemoteAgentInvocationService` 和 A2A client。 |
| `engine/openjiuwen` | `OpenJiuwenStreamAdapter` 能从 remote interrupt state/context 构造 `AgentExecutionResult.RemoteInvocation`。 |
| `engine/openjiuwen` | `OpenJiuwenMessageAdapter` 能用 `toolCallId + toolResult` 构造 `InteractiveInput.update(...)`，并保持同一 `conversation_id`。 |
| `engine/a2a` | `A2aAgentExecutor` 内部 result 消费循环遇到 `REMOTE_INVOCATION` 后返回 remote invocation decision、停止继续消费本地 stream，并关闭 raw/adapted stream。 |
| `engine/a2a` | `A2aAgentExecutor` 在同一个 `execute(...)` 内关闭第一段 stream 后调用远端；远端 completed/failed 后再打开 `REMOTE_RESUME` 第二段 handler stream，并复用当前 emitter 投影结果。 |
| `engine/a2a` | `A2aAgentExecutor` 开头只在 `TASK_STATE_INPUT_REQUIRED + runtime.waitingTarget=REMOTE_AGENT` 时进入 remote continuation branch；其他 input-required 仍走本地 OpenJiuwen 路径。 |
| `engine/a2a` | `A2aParentTaskProjector` 对 completed/failed 支持 re-enter 所需 tool result；对 input-required 只写 parent task，不 re-enter。 |
| `engine/a2a` | `A2aParentTaskProjector` 的 progress 可被 stream/subscribe 消费，input-required 可被 `tasks/get` 查询到；重复 input-required 时 metadata merge 不丢 route 字段。 |
| `engine/a2a` | outbound adapter 保留远端 `INPUT_REQUIRED.status.message` 的 parts 和 metadata。 |
| `engine/a2a + engine/service` | 集成测试覆盖 `A2aAgentExecutor + RemoteAgentInvocationService + fake OutboundPort`：远端 completed 会 re-enter 本地 handler，远端 input-required 不 re-enter。 |
| `boot + engine/a2a` | 集成测试验证 parent task 处于 `TASK_STATE_INPUT_REQUIRED` 时，下一轮 `SendMessage` / `SendStreamingMessage` 会重新进入 SDK `AgentExecutor.execute(ctx, emitter)`。 |
| `boot + engine/a2a` | 集成测试覆盖 remote continuation：下一轮用户输入能用新的 emitter 路由到同一 remote task/context，并保持 A2A response/SSE 格式与 SDK 原路径一致。 |
| `engine/a2a` | cancel 测试覆盖：parent task 处于 remote waiting 且 metadata 中已有 remote task/context 时，`A2aAgentExecutor.cancel(...)` best-effort 调远端 cancel。 |
| `engine/service` | 远端 card 拉取失败保持 `PENDING` 并按固定间隔持续重试；成功后转 `AVAILABLE`，不再刷新。 |
| `boot` | blocking `SendMessage` 和 streaming `SendStreamingMessage` 都继续委托 SDK `RequestHandler`，不绕过 SDK 序列化与 SSE 包装。 |
| `architecture` | 新增文件仍符合当前 package boundary。 |
| e2e example | 两个 runtime 启动后，验证本地 runtime 发现远端 card、远端调用经 A2A client 出站、远端 input-required 续写回同一 remote task/context、远端 completed 后 resume 本地 OpenJiuwen。示例主路径使用确定性 handler 产生 remote interrupt marker，不把真实 LLM tool choice 作为稳定验收前提；真实 tool 注入/rail 拦截由 `engine/openjiuwen` 单元测试覆盖。 |

验收标准：

- 本地 OpenJiuwen tool/rail 路径中没有 A2A HTTP/JSON-RPC client。
- 本地 LLM 选择远端 tool 后，先中断本地 OpenJiuwen，再由 runtime 调远端。
- 远端 completed/failed 先变成本地 tool result，再恢复本地 OpenJiuwen。
- 远端 input-required 的用户提示能通过 parent A2A task/stream 被用户看到。
- 用户下一轮输入必须携带 parent `taskId` 和匹配的 `contextId`，并能根据 parent task metadata 回到同一个 remote task/context。
- 远端多轮 input-required 时，parent prompt 更新为最新远端提示，route metadata 不丢失。
- runtime 不保存 OpenJiuwen checkpoint blob，不新增 `checkpointRef`。
- remote completed 的本地恢复复用原始 `agentStateKey` / `conversation_id`，不创建新的 parent task。
- v1 不引入后台 watcher 或异步回调；所有远端 outcome 都在当前 `A2aAgentExecutor.execute(...)` 同步调用链内处理。
