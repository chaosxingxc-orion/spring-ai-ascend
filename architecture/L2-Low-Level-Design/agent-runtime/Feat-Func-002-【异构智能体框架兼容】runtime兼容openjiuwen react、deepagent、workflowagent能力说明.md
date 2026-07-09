# Agent Core 适配结果解析

> 分析对象：`agent-runtime-java` 与 `agent-core-java`
> 更新时间：2026-07-09

---

## 1. 概述

### 1.1 当前适配定位

`agent-runtime-java` 当前对 `agent-core-java` 的适配核心不是为 ReAct Agent、DeepAgent、Workflow 各自实现一套独立入口，而是把三类对象都收敛到统一的 Service SPI：

```text
HTTP / Query API
  -> ServeOrchestrator
  -> AgentHandler
  -> JiuwenCoreAgentHandler
  -> agent-core Runner.runAgent / Runner.runAgentStreaming
  -> ReActAgent / DeepAgent / WorkflowAgent 等 core 对象
```

代码事实：

- Runtime 侧统一 SPI 是 `com.openjiuwen.service.spec.spi.AgentHandler`，只定义 `query`、`streamQuery`、`start`、`stop`、`clearSession`。
- Agent Core 适配器是 `com.openjiuwen.service.adapters.agentcore.agentfw.JiuwenCoreAgentHandler`，构造参数是 `Object agent`，可以是 agent 实例，也可以是 agentId 字符串。
- `JiuwenCoreAgentHandler` 在 `start()` 中注册 middleware / external service 后调用 `Runner.start()`，在 `stop()` 中调用 `Runner.stop()`，在 `clearSession()` 中调用 `Runner.release(conversationId)`。
- 非流式 `query` 不是固定走 streaming：`JiuwenCoreAgentHandler.query()` 会先用 `supportsInvoke(agent)` 判断当前 handler 持有的对象是否直接暴露 `invoke` 方法；如果是 agent 实例且有 `invoke`，走 `Runner.runAgent(...)`；如果是 agentId 字符串或没有 `invoke` 的对象，则回退为收集 `Runner.runAgentStreaming(...)` 的流式输出。
- 流式 `streamQuery` 始终走 `Runner.runAgentStreaming(...)`，只开启 `StreamMode.OUTPUT`。
- Agent Core 的 `RunnerImpl` 通过反射查找 agent 的 `invoke(...)` / `stream(...)` 方法，兼容 `invoke(inputs, session, context)`、`invoke(inputs, session)`、`invoke(inputs)` 以及 `stream(inputs, session, streamModes)`、`stream(inputs, session, context)`、`stream(inputs, session)`、`stream(inputs)`。

### 1.2 当前事实边界

本文只描述当前两个仓库代码中已经落地的适配结果：

- runtime 仓：`service/agent-service-spec`、`service/agent-service-app`、`service/agent-service-adapters/agent-service-adapters-agentcore`、`service/agent-service-demo`。
- core 仓：`com.openjiuwen.core.singleagent.agents.ReActAgent`、`com.openjiuwen.harness.deep_agent.DeepAgent`、`com.openjiuwen.core.application.workflow.WorkflowAgent`、`com.openjiuwen.core.workflow.Workflow`、`com.openjiuwen.core.runner.Runner/RunnerImpl`。

不把 core 内部能力误写成 runtime 独立适配能力。例如 DeepAgent 的 task loop、rails、workspace、subagents 属于 `agent-core-java` / harness；runtime 当前只负责把请求送入 Runner、把输出统一成 QueryResponse / QueryChunk。

---

## 2. Runtime 统一适配层

### 2.1 能力清单

| 能力 | 当前结果 | 代码事实 |
| --- | --- | --- |
| 统一服务 SPI | 已适配 | `AgentHandler` 定义 `query` / `streamQuery` / lifecycle |
| Agent Core handler | 已适配 | `JiuwenCoreAgentHandler implements AgentHandler` |
| Agent 实例适配 | 已适配 | `new JiuwenCoreAgentHandler(Object agent)` |
| agentId 适配 | 已适配 | 自动配置在 `openjiuwen.service.agent-id` 存在且 `openjiuwen.service.handler=agentcore` 时注册 `JiuwenCoreAgentHandler(agentId, ...)` |
| Runner 生命周期 | 已适配 | `start()` 调 `Runner.start()`，`stop()` 调 `Runner.stop()` |
| 会话清理 | 已适配 | `clearSession(conversationId)` 调 `Runner.release(conversationId)` |
| 输入归一化 | 已适配 | `conversation_id`、`messages`、`user_id`、`space_id`、`tenant_id`、`query` 进入 Runner inputs |
| Session 适配 | 已适配 | 有 card 的 agent 使用 conversationId；无 card 的对象构造 synthetic `AgentSessionApi` |
| 流式输出 | 已适配 | `OutputSchema` / `TraceSchema` / Map / 普通对象归一成 QueryChunk payload |
| 中断输出 | 已适配（浅层归一） | ReAct / DeepAgent / WorkflowAgent 只要最终输出 `OutputSchema.type == "__interaction__"`，runtime 就会映射为 `QueryChunk.TYPE_INTERRUPT` / `_interrupt`；WorkflowAgent 在 core 侧已经把 workflow `INPUT_REQUIRED` 中断收敛到该形态，runtime 不直接识别裸 `WorkflowExecutionState.INPUT_REQUIRED` |
| 取消执行 | 流消费层适配 | Orchestrator 只取消 active stream 消费；未透传到 Runner、LLM、tool、workflow 节点或 DeepAgent task loop |

### 2.2 统一调用流程

```text
QueryIngressSupport.validateAndBuild
  -> ServeRequest.fromQueryRequest
  -> DefaultServeOrchestrator.query / streamQuery
  -> JiuwenCoreAgentHandler.query / streamQuery
  -> buildInputs(request)
  -> runnerSession(request)
  -> Runner.runAgent / Runner.runAgentStreaming
  -> normalizeChunk / appendContent / toQueryResponse
```

关键归一化规则：

- `buildInputs()` 固定写入 `conversation_id`、`messages`、`user_id`、`space_id`、`tenant_id`，并从最后一条用户消息提取 `query`。
- `normalizeChunk()` 对 `OutputSchema` 生成 `{type,index,payload}`；对 `TraceSchema` 生成 `{type,payload}`；对普通对象包成 `{type:"chunk",data:...}`。
- `mapToQueryChunkType()` 只有在 normalized map 的 `type` 为 `__interaction__` 时返回 `interrupt`，其他都返回 `chunk`。
- 非流式聚合时，`appendContent()` 只从 `content`、`delta`、`output`、`response` 或 payload 内同名字段抽文本。

### 2.3 query 与 streamQuery 的真实分支

从 runtime 的 `JiuwenCoreAgentHandler` 看，`query` 和 `streamQuery` 的分支是不一样的。

流式入口固定走 streaming：

```text
JiuwenCoreAgentHandler.streamQuery(request, observer)
  -> streamModes = [StreamMode.OUTPUT]
  -> Runner.runAgentStreaming(agent, buildInputs(request), runnerSession(request), null, streamModes)
  -> while source.hasNext()
       -> normalizeChunk(raw)
       -> mapToQueryChunkType(normalized)
       -> observer.onNext(new QueryChunk(type, normalized))
```

非流式入口先尝试同步调用：

```text
JiuwenCoreAgentHandler.query(request)
  -> supportsInvoke(agent)?
       yes:
         Runner.runAgent(agent, buildInputs(request), runnerSession(request), null)
         -> toQueryResponse(rawResult, conversationId)
       no:
         queryViaStreaming(request)
         -> Runner.runAgentStreaming(...)
         -> appendContent(...)
         -> buildQueryResponse(...)
```

这里有一个容易误解的边界：`supportsInvoke(agent)` 检查的是 `JiuwenCoreAgentHandler` 当前持有的对象本身。如果构造 handler 时传的是 `ReActAgent`、`DeepAgent`、`WorkflowAgent` 实例，通常能发现对应 `invoke` 方法，非流式 `query` 会走 `Runner.runAgent`。如果构造 handler 时传的是 `agentId` 字符串，`supportsInvoke()` 直接返回 false，因为真正的 agent 实例要到 core 的 `RunnerImpl.prepareAgent()` 中才会通过 `ResourceMgr.getAgent(agentId)` 解析出来；因此这种场景下 runtime 的非流式 `query` 会走 `Runner.runAgentStreaming` 并聚合流式结果。

### 2.4 Runner 是什么

`Runner` 是 `agent-core-java` 的全局执行门面类，静态方法全部代理到一个全局 `RunnerImpl` 实例。它不是 runtime 的类，而是 core 的运行入口。

它承担几类职责：

- 生命周期：`Runner.start()` / `Runner.stop()` 启停 core runner 组件。
- 资源访问：`Runner.resourceMgr()` 暴露 agent、workflow、tool、model、prompt 等资源管理器。
- Agent 执行：`Runner.runAgent(...)` 进入非流式 agent 调用；`Runner.runAgentStreaming(...)` 进入流式 agent 调用。
- Workflow 执行：`Runner.runWorkflow(...)` / `Runner.runWorkflowStreaming(...)` 是 workflow 专用入口。
- Session 管理：`RunnerImpl.prepareAgentSession(...)` 根据 `conversation_id`、已有 session 或默认值创建/复用 `AgentSessionApi`，执行前 `preRun(inputs)`，非流式执行后 `postRun()`，流式执行则用 `wrapStreamingIterator(...)` 在 iterator 消费完后触发 `postRun()`。

Runtime 的 `JiuwenCoreAgentHandler` 当前只调用 `Runner.runAgent(...)` 和 `Runner.runAgentStreaming(...)`，不会直接调用 `Runner.runWorkflow(...)` / `Runner.runWorkflowStreaming(...)`。因此 workflow 要通过 `WorkflowAgent` 这种 agent 包装形态进入 runtime。

### 2.5 Runner 反射调用实现

`RunnerImpl` 不通过 `instanceof ReActAgent / DeepAgent / WorkflowAgent` 做分发，而是按方法名和参数兼容性反射调用。

非流式入口：

```text
Runner.runAgent(...)
  -> RunnerImpl.runAgent(...)
  -> prepareAgent(agent)
       agent 是 String: resourceManager.getAgent(agentId)
       agent 是实例: 原样返回
  -> prepareAgentSession(agentInstance, inputs, session)
  -> invokeAgent(agentInstance, inputs, agentSession, context)
  -> agentSession.postRun()
```

`invokeAgent(...)` 调用：

```text
invokeFirstCompatibleMethod(agentInstance, "invoke",
  [
    [inputs, session, context],
    [inputs, session],
    [inputs]
  ],
  "Agent does not support invoke method")
```

流式入口：

```text
Runner.runAgentStreaming(...)
  -> RunnerImpl.runAgentStreaming(...)
  -> prepareAgent(agent)
  -> prepareAgentSession(agentInstance, inputs, session, streamModes)
  -> streamAgent(agentInstance, inputs, agentSession, context, streamModes)
  -> wrapStreamingIterator(iterator, agentSession)
```

`streamAgent(...)` 调用：

```text
invokeFirstCompatibleMethod(agentInstance, "stream",
  [
    [inputs, session, streamModes],
    [inputs, session, context],
    [inputs, session],
    [inputs]
  ],
  "Agent does not support stream method")
```

反射匹配细节：

- `invokeFirstCompatibleMethod(...)` 按候选参数列表顺序逐组查找可兼容方法，找到就 `method.invoke(target, args)`。
- `findCompatibleMethod(...)` 先遍历 `targetClass.getMethods()`，再遍历 `targetClass.getDeclaredMethods()`；找到后会 `setAccessible(true)`，所以 package-private 测试类或非 public 声明方法也可能被调用。
- `scoreMethod(...)` 会对每个参数算兼容分，所有参数都兼容才算候选。
- `scoreArgument(...)` 的规则是：实参为 null 时非 primitive 得 1 分；参数类型与实参类型完全一致得 10 分；参数类型可赋值且不是 `Object` 时 class 得 8 分、interface 得 6 分；参数类型为 `Object` 时得 2 分；不兼容返回 -1。
- 因此如果同一参数个数下有多个同名方法，Runner 会选择 score 最高的方法。比如 `stream(Map, AgentSessionApi, List)` 会比 `stream(Object, Session, List)` 更优先。

这个机制解释了三类 agent 为什么都能接入：runtime 只给 Runner 一个 `Object agent`，Runner 再按公开方法形态找到最合适的 `invoke` 或 `stream`。

---

## 3. ReAct Agent 适配结果

### 3.1 Core 侧能力形态

`agent-core-java` 的 ReAct Agent 位于 `com.openjiuwen.core.singleagent.agents.ReActAgent`，继承 `BaseAgent`。它明确提供：

- `invoke(Object inputs, Session session)`：非流式执行。
- `stream(Object inputs, Session session, List<StreamMode> streamModes)`：流式执行。
- 输入兼容 Map 或 String；Map 中重点读取 `query`、`conversation_id` 等字段。
- 输出约定为 Map，如 `{"output": "...", "result_type": "answer|error|interrupt"}`；流式时通过 `AgentSessionApi.writeStream(new OutputSchema(...))` 写出。

ReAct 的执行主线是：

```text
ReActAgent.invoke / stream
  -> 读取 query / conversation_id
  -> 初始化 ModelContext
  -> BEFORE_INVOKE rail/callback
  -> LLM call / stream call
  -> tool call
  -> contextEngine.saveContexts
  -> 返回 answer / error / interrupt
```

### 3.2 Runtime 适配方式

Runtime 不直接理解 ReAct loop，而是通过 `JiuwenCoreAgentHandler` 统一调用：

```text
JiuwenCoreAgentHandler.streamQuery
  -> Runner.runAgentStreaming(reActAgent, inputs, AgentSessionApi/conversationId, null, [OUTPUT])
  -> normalizeChunk(OutputSchema)
  -> QueryChunk("chunk" 或 "interrupt", normalized)
```

非流式：

```text
JiuwenCoreAgentHandler.query
  -> supportsInvoke(reActAgent) == true
  -> Runner.runAgent(reActAgent, inputs, session, null)
  -> toQueryResponse(rawResult, conversationId)
```

### 3.3 中断与续接

ReAct Agent 的工具中断由 core 写入 `OutputSchema("__interaction__", index, InteractionOutput)`。Runtime 做两层归一：

- 流式：`normalizeChunk()` 将 `OutputSchema("__interaction__")` 转成 `{type:"__interaction__", index, payload, message, context, toolCallId, toolName}`，再由 `mapToQueryChunkType()` 标记顶层 chunk type 为 `interrupt`。
- 非流式：如果 Runner 返回 Map 且 `result_type=interrupt`，`JiuwenCoreAgentHandler` 会从 `state` 列表中找最后一个 `OutputSchema`，转成 QueryResponse 的 `_interrupt` 字段，并把 `content` 设置为中断 message。

Core 侧 ReAct 有续接逻辑：`normalizeResumePayload()` 会把用户输入转成 `InteractiveInput`，再恢复被中断的 tool call。但 runtime 当前只是把下一轮请求继续按普通 `query` 送入同一个 conversation/session，是否成功续接取决于 core session/checkpointer 是否保存了 `ToolInterruptionState`。

### 3.4 适配结论

ReAct Agent 是当前 runtime 与 agent-core 对接最完整的一类：

- 同步、流式、会话清理、中断输出都有 runtime 侧映射。
- middleware checkpointer 与 external service registrar 会在 handler start 阶段写入 Runner 环境。
- MCP 等外部工具不是 ReAct 专用适配器能力，而是通过 external registrar + agent ability manager 进入 ReAct loop。

不足：

- runtime 对 ReAct 的 chunk 语义只做浅层透传，`llm_reasoning`、`llm_output`、`llm_usage`、`tool_call`、`tool_result` 等细粒度类型不会提升成标准事件类型。
- 取消只停止 service 层继续消费流，不会可靠打断正在进行的 LLM 或 tool call。

---

## 4. DeepAgent 适配结果

### 4.1 Core 侧能力形态

DeepAgent 位于 `com.openjiuwen.harness.deep_agent.DeepAgent`。它不是继承 `BaseAgent` 的简单单 agent，而是内部持有一个 ReActAgent，并叠加 harness 能力：

- `DeepAgent` 构造时创建内部 `ReActAgent`，并把 `DeepAgentConfig` 转成 `ReActAgentConfig`。
- 支持 workspace、tools、rails、MCP、permissions、task loop、completion policy、subagents 等 harness 能力。
- 对外提供 `invoke(Map<String,Object> inputs)`、`stream(Map<String,Object> inputs)`、`stream(Map<String,Object> inputs, List<StreamMode>)`、`stream(Map<String,Object> inputs, AgentSessionApi session, List<StreamMode>)` 等方法。
- `enableTaskLoop=true` 时，`invoke()` 会进入 `runTaskLoop()`，再经 event queue / scheduler / task executor 组织多轮内部 ReAct 执行。

DeepAgent 的典型流式路径：

```text
DeepAgent.stream(inputs, session, streamModes)
  -> ensureInitialized()
  -> runTaskLoop(normalized, session) 或 agent.stream(...)
  -> writeTopLevelStreamResult(session, index, result)
  -> session.streamIterator()
```

### 4.2 Runtime 适配方式

Runtime 对 DeepAgent 没有单独 handler。DeepAgent 能被适配，是因为：

- `JiuwenCoreAgentHandler` 接收 `Object agent`。
- `RunnerImpl.streamAgent()` 通过反射匹配 `stream(inputs, session, streamModes)` / `stream(inputs, session, context)` / `stream(inputs, session)` / `stream(inputs)`。
- `RunnerImpl.invokeAgent()` 通过反射匹配 `invoke(inputs, session, context)` / `invoke(inputs, session)` / `invoke(inputs)`。
- DeepAgent 暴露的 `invoke(Map)`、`stream(Map, AgentSessionApi, List<StreamMode>)` 等方法能被 Runner 匹配。

因此调用链是：

```text
JiuwenCoreAgentHandler
  -> Runner.runAgentStreaming(deepAgent, inputs, session, null, [OUTPUT])
  -> RunnerImpl.streamAgent reflection
  -> DeepAgent.stream(...)
  -> QueryChunk normalization
```

### 4.3 中断与输出

DeepAgent 的中断最终仍然依赖 `OutputSchema("__interaction__")`：

- 内部 ReAct / interrupt rail 产生 `__interaction__`。
- `DeepAgent.extractFinalStreamResult()` 如果遇到 `OutputSchema("__interaction__")`，会转成 `{"output":"","result_type":"interrupt","state":[outputSchema]}`。
- `DeepAgent.writeTopLevelStreamResult()` 在顶层结果是 interrupt 且含 `state` 时，把其中的 `OutputSchema` 写回 session stream。
- runtime 再按 ReAct 同样规则把它映射为 `QueryChunk.TYPE_INTERRUPT` 或 `_interrupt`。

DeepAgent 还会把 task-loop round 信息、workspace、mode、inputs、final_result、usage 等作为 Map 字段返回。runtime 的 `appendContent()` 只识别少数字段，因此非流式聚合时，如果最终 Map 没有顶层 `output/content/response`，可能退化为 `String.valueOf(lastPayload)`。

### 4.4 适配结论

DeepAgent 当前属于“通过 Runner 反射协议自然接入”的适配结果，而不是 runtime 深度理解 DeepAgent：

- 基本 query/stream 可以接入。
- task loop、rails、interrupt、workspace 等核心能力保留在 core/harness 内部。
- runtime 能识别 `__interaction__`，所以 DeepAgent 的用户确认/询问类中断可传到 service 层。

不足：

- runtime 没有 DeepAgent 专属结果模型，复杂结果只能 Map 透传或字符串化。
- task loop 的 round、mode、workspace、stream_chunks 等结构没有标准化成 service-level schema。
- DeepAgent 的取消、权限中断、长任务状态没有被 runtime 作为一等状态管理，只能依赖流中断/错误或 core 自身状态。

---

## 5. Workflow 适配结果

### 5.1 Core 侧能力形态

Core 有两层 workflow 形态：

1. `com.openjiuwen.core.workflow.Workflow`：底层 DAG / Pregel workflow，对外提供 `invoke(...)`、`stream(...)`，需要 workflow session。
2. `com.openjiuwen.core.application.workflow.WorkflowAgent`：把一个或多个 Workflow 包装成 Agent，继承 `ControllerAgent`，通过 `WorkflowEventHandler` 做 intent detection、workflow 调度、中断恢复、流式输出。

`WorkflowAgent` 明确提供：

- `invoke(Object inputs, Session session)`，返回 `ControllerOutput`。
- `stream(Object inputs, Session session, List<StreamMode> streamModes)`，返回 `Iterator<Object>`。
- `addWorkflows(List<Workflow>)`，把 workflow 注册到 agent config、ability manager 和 Runner resource manager。

`WorkflowEventHandler` 在执行 workflow 时使用：

```text
Runner.runWorkflowStreaming(workflow, inputs, workflowSession, context, streamModes)
```

它会识别：

- `workflow_final`
- `__interaction__`
- `WorkflowExecutionState.INPUT_REQUIRED`
- task status `INPUT_REQUIRED`

### 5.2 裸 Workflow 与 WorkflowAgent 的区别

裸 `Workflow` 是一个底层 DAG / Pregel 执行对象，关注的是“给定输入和 workflow session，执行一张组件图”。它的核心职责包括：

- 持有 `WorkflowCard` 和 `BaseWorkflow` 配置。
- 通过 `setStartComp`、`addWorkflowComp`、`setEndComp`、`addConnection`、`addStreamConnection` 等 API 构建图。
- 直接执行时走 workflow 协议入口，例如 `Workflow.invoke(inputs, session, context, ...)` 或 `Workflow.stream(inputs, session, context, streamModes)`。
- 对 session 类型有 workflow 语义要求，内部会创建/使用 `WorkflowSession`、`WorkflowSessionApi`、`NodeSession`、`SubWorkflowSession` 等。

`WorkflowAgent` 是把一个或多个 `Workflow` 包装成 Agent 协议的外壳，关注的是“像一个 Agent 一样被 `Runner.runAgent` / `Runner.runAgentStreaming` 调用”。它的核心职责包括：

- 继承 `ControllerAgent`，对外提供符合 agent 协议的 `invoke(Object inputs, Session session)` 和 `stream(Object inputs, Session session, List<StreamMode> streamModes)`。
- 通过 `addWorkflows(List<Workflow>)` 把 workflow 写入 `WorkflowAgentConfig`、ability manager 和 Runner resource manager。
- 通过 `WorkflowEventHandler` 做 workflow 选择、intent detection、task 状态管理、`INPUT_REQUIRED` 中断处理、resume 处理和输出归一。
- 在内部真正执行 workflow 时，再调用 `Runner.runWorkflowStreaming(...)`。

二者关系可以理解为：

```text
Workflow
  = 可执行的工作流图
  = workflow 协议对象
  = 适合被 Runner.runWorkflow / runWorkflowStreaming 或 WorkflowAgent 内部调用

WorkflowAgent
  = 对外暴露成 Agent 的包装层
  = agent 协议对象
  = 适合交给 JiuwenCoreAgentHandler / Runner.runAgent / Runner.runAgentStreaming
```

从 runtime 视角看，`JiuwenCoreAgentHandler` 只知道 agent 协议，不知道 workflow 协议。它会调用 `Runner.runAgent(...)` / `Runner.runAgentStreaming(...)`，然后由 Runner 反射寻找 `invoke` / `stream` 方法。裸 `Workflow` 虽然也有执行方法，但方法签名、session 语义和 workflow 专用入口都不是当前 handler 面向的协议；`WorkflowAgent` 则明确提供了 agent 协议方法，所以是 runtime 推荐接入对象。

### 5.3 Runtime 适配方式

Runtime 最稳妥的 workflow 接入对象是 `WorkflowAgent`，而不是直接把裸 `Workflow` 交给 `JiuwenCoreAgentHandler`：

- `WorkflowAgent` 有符合 Runner agent 协议的 `invoke(inputs, session)` / `stream(inputs, session, streamModes)`。
- 裸 `Workflow` 的主要执行入口是 `invoke(inputs, session, context, boolean...)` 和 `stream(...)`，session 类型要求是 workflow session；虽然 `Runner` 有 `runWorkflow` / `runWorkflowStreaming`，但 `JiuwenCoreAgentHandler` 当前调用的是 `Runner.runAgent` / `runAgentStreaming`，不是 `runWorkflow`。

因此当前 runtime 对 workflow 的实际适配链路应理解为：

```text
Workflow
  -> WorkflowAgent.addWorkflows(...)
  -> WorkflowAgent.invoke / stream
  -> Runner.runAgent / runAgentStreaming
  -> JiuwenCoreAgentHandler normalization
```

而不是：

```text
JiuwenCoreAgentHandler -> Runner.runWorkflowStreaming
```

后者在当前 handler 代码中没有出现。

### 5.4 中断与输出

Workflow 的中断与完成输出在 core 侧先归一：

- `Workflow` 在 workflow 执行中遇到 graph interrupt 时返回 `WorkflowOutput(..., INPUT_REQUIRED)` 或恢复 `OutputSchema("__interaction__")`。
- `WorkflowEventHandler` 遇到中断会把 task status 置为 `INPUT_REQUIRED`，并把中断 chunk 写入 session。
- `WorkflowAgent.normalizeInvokeOutput()` 会把 `__interaction__` 优先作为 `ControllerOutput` data 返回；否则查找最后的 `workflow_final` 或 `answer`。
- Runtime 的 `JiuwenCoreAgentHandler.buildQueryResponseFromControllerOutput()` 会遍历 `ControllerOutput.getData()`，对每个 item 做 `normalizeChunk()`，若最后 payload 是 `__interaction__`，则输出 `_interrupt`；否则聚合 content/output。


### 5.5 适配结论

Workflow 适配是“可经 WorkflowAgent 接入”的状态：

- 如果业务把 workflow 包装为 `WorkflowAgent`，runtime 可以通过通用 `JiuwenCoreAgentHandler` 调用。
- workflow 的 `workflow_final`、`answer`、`__interaction__` 能被当前归一化逻辑处理。
- workflow 内部多节点、stream edge、input required、resume 等语义由 core 的 `Workflow` / `WorkflowEventHandler` 负责。

不足：

- Runtime 没有 workflow 专用 handler，不会直接调用 `Runner.runWorkflowStreaming`。
- Runtime 也没有 workflow 专用 QueryChunk 类型，`workflow_final` 只是 payload 内部字段。
- 直接传裸 `Workflow` 给 `JiuwenCoreAgentHandler` 风险较高，因为 handler 按 agent 协议调用 Runner，而不是 workflow 协议。

---

## 6. 三类适配对比

| 类型 | 推荐接入对象 | Runtime handler | query 衔接方法 | streamQuery 衔接方法 | 中断映射 |
| --- | --- | --- | --- | --- | --- |
| ReAct Agent | `ReActAgent` | `JiuwenCoreAgentHandler` | agent 实例：`Runner.runAgent` -> `ReActAgent.invoke(Object, Session)`；agentId 字符串：`Runner.runAgentStreaming` 聚合 | `Runner.runAgentStreaming` -> `ReActAgent.stream(Object, Session, List<StreamMode>)` | `__interaction__` -> `interrupt` / `_interrupt` |
| DeepAgent | `DeepAgent` | `JiuwenCoreAgentHandler` | agent 实例：`Runner.runAgent` -> `DeepAgent.invoke(Map)`；agentId 字符串：`Runner.runAgentStreaming` 聚合 | `Runner.runAgentStreaming` -> 优先匹配 `DeepAgent.stream(Map, AgentSessionApi, List<StreamMode>)` | `__interaction__` -> `interrupt` / `_interrupt` |
| Workflow | `WorkflowAgent` | `JiuwenCoreAgentHandler` | agent 实例：`Runner.runAgent` -> `WorkflowAgent.invoke(Object, Session)`；agentId 字符串：`Runner.runAgentStreaming` 聚合 | `Runner.runAgentStreaming` -> `WorkflowAgent.stream(Object, Session, List<StreamMode>)`，内部再由 `WorkflowEventHandler` 调 `Runner.runWorkflowStreaming` | `__interaction__` / `INPUT_REQUIRED` -> service interrupt |

---

## 7. 当前适配问题分析

1. **适配粒度偏通用，类型语义损失**

   `JiuwenCoreAgentHandler` 只关心 `OutputSchema`、`TraceSchema`、Map 和普通对象。ReAct 的 LLM chunk、DeepAgent 的 task-loop round、Workflow 的 workflow chunk 都没有提升成 runtime 标准事件。因此上层只能读 payload 内部字段。

2. **Workflow 适配路径不够显式**

   Core 已经有 `Runner.runWorkflowStreaming`，但 runtime handler 当前只调用 `runAgent/runAgentStreaming`。Workflow 必须包装成 `WorkflowAgent` 才符合当前服务入口；如果直接把裸 `Workflow` 交给 `JiuwenCoreAgentHandler`，handler 不会按 workflow 协议创建 workflow session，也不会调用 workflow 专用 Runner 入口。

3. **取消语义不完整**

   `DefaultServeOrchestrator.cancelActive()` 取消的是 active stream registry，`JiuwenCoreAgentHandler.streamQuery()` 只检查 observer cancelled 并停止消费。它的效果是 service 层停止继续 `source.next()` / `observer.onNext(...)`，并不等价于取消 core 内部执行。

   未完全适配的边界包括：

   - 没有调用 Runner 级任务取消入口；当前 runtime 也没有维护可透传给 Runner 的 core task handle。
   - 已经发起的 LLM 调用、tool 调用、Workflow 节点执行、DeepAgent task loop 不一定被中断。
   - 对流式 iterator 来说，停止消费不保证底层线程、网络请求或工具执行同步停止。
   - 非流式 `query` 进入 `Runner.runAgent(...)` 后没有 runtime 侧中途取消点，只能等待 core 返回或抛异常。

4. **非流式聚合对复杂结果不友好**

   `appendContent()` 只聚合少数文本字段。DeepAgent / Workflow 返回复杂 Map 时，非流式 response 可能出现字符串化对象或缺少结构化字段的问题。

5. **中断输出可透传，恢复协议仍未统一**

   Runtime 已经能把 core 输出中的 `OutputSchema("__interaction__")` 转成流式 `QueryChunk.TYPE_INTERRUPT`，或在非流式响应中转成 `_interrupt` 字段；这解决的是“把中断信号暴露给调用方”的问题。

   对 WorkflowAgent 来说，core 侧已经做了关键转换：`Workflow.stream()` / `Workflow.invoke()` 中的 `WorkflowExecutionState.INPUT_REQUIRED`、task status `INPUT_REQUIRED` 并不是直接交给 runtime 判断，而是先经过 `WorkflowEventHandler` / `WorkflowAgent.normalizeInvokeOutput()` 归一。`WorkflowEventHandler` 在检测到 `OutputSchema("__interaction__")` 后将 task 置为 `INPUT_REQUIRED`，再把 interaction chunk 写回 session stream；`WorkflowAgent.normalizeInvokeOutput()` 在非流式 invoke 返回中优先提取 `__interaction__` 并包装成 `ControllerOutput`。因此，**在使用 WorkflowAgent 作为 agent 适配对象时，runtime 对 workflow 中断输出是支持的**。

   未完全适配的边界包括：

   - Runtime 只直接识别 `__interaction__` 这一种中断载体，不直接建模 `WorkflowExecutionState.INPUT_REQUIRED`、task status `INPUT_REQUIRED`、`WorkflowOutput(..., INPUT_REQUIRED)`。这不是 WorkflowAgent 路径的功能缺口，因为 core 已经转换；它的含义是 runtime 的识别能力依赖 core/WorkflowAgent 的归一化结果。如果把裸 `Workflow` 或其他未归一对象直接接入 service 层，runtime 不会基于 `WorkflowExecutionState` 自己推导 interrupt。
   - Runtime 没有统一中断 DTO。当前只提取 `message`、`context`、`toolCallId`、`toolName` 等少数字段，没有定义 `interruptId`、source agent、workflow node、resume schema、候选动作等稳定结构。
   - Runtime 没有显式 resume API / DTO。下一轮恢复只是继续用同一个 `conversation_id` 发送普通 `query`，真正能否恢复依赖 core session/checkpointer 中是否保存了 `ToolInterruptionState` 或 workflow 中断状态。
   - ReAct、DeepAgent、Workflow 的中断语义没有在 service 层统一建模；调用方需要理解 payload 内部结构。

---

## 8. 建议后续演进

| 优先级 | 建议 | 收益 |
| --- | --- | --- |
| P1 | 定义标准 QueryChunk event type：`llm_output`、`tool_call`、`tool_result`、`workflow_final`、`usage` | 减少上层解析 payload 内部字段 |
| P1 | 增加显式 resume DTO / API | 稳定 ReAct、DeepAgent、Workflow 的人机交互恢复 |
| P2 | 为 DeepAgent 输出定义 service schema | 避免复杂 Map 字符串化，保留 round / workspace / final_result |
| P2 | 将 cancel 透传到 core Runner / session / task loop | 提升长任务和工具调用的可控性 |
