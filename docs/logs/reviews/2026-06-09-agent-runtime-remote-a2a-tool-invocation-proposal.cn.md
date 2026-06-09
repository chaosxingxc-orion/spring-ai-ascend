---
affects_level: L2
proposal_status: review
authors: ["yougq", "Codex"]
related_adrs: []
related_rules: [R-C, R-D, R-G, R-M]
affects_artefact:
  - architecture/docs/L2/agent-runtime/REMOTE_A2A_TOOL_INVOCATION_DESIGN.md
  - agent-runtime
---

# Proposal: Agent Runtime 远端 A2A Agent 工具调用与 A2A 打穿

> **Date:** 2026-06-09
> **Status:** Pending Review / 非 ADR / 非已批准实现
> **Affects:** `agent-runtime` L2 设计、A2A task/event 打穿、OpenJiuwen agent-as-tool 注入、runtime-to-runtime A2A outbound。
> **Primary Design:** `architecture/docs/L2/agent-runtime/REMOTE_A2A_TOOL_INVOCATION_DESIGN.md`
> **Fact Baseline:** 已按 Rule G-15 先读 `architecture/facts/generated/`；当前代码事实仍是 custom `TaskControlService` / `QueueManager` / `A2aOutputRegistry` 与 `OpenJiuwenAgentRuntimeHandler`，本提案描述的是 A2A 打穿后的目标形态。

---

## 0. 摘要

本提案定义 `agent-runtime` 在 **A2A 打穿** 前提下，为本地 OpenJiuwen agent 赋予调用远端 A2A agent 的能力。

核心决策：

- A2A `Task` / `TaskStore` / `EventQueue` 成为 runtime 内部任务与 caller-visible 事件事实源。
- 远端 agent 通过 `/.well-known/agent-card.json` 发现，转换为本地 OpenJiuwen tool 注入到当前 `BaseAgent` / `ReActAgent` 实例。
- 本地 LLM 选择远端 tool 后，由 OpenJiuwen remote tool/rail bridge 调用 runtime 中性服务 `RemoteAgentInvocationService`，不直接接触 A2A client。
- control 仍是状态权威，远端调用生命周期先经过 `RemoteInvocationControl` 写 parent A2A task metadata/event。
- 真正的 A2A client 只存在于 `access.a2a.A2aRemoteAgentOutboundAdapter` 内部，通过 `RemoteAgentOutboundPort` 隔离协议 I/O。
- 远端 terminal result 只作为 tool result 回到 parent agent；只有 parent agent 的最终回答才成为 parent caller-visible final output。
- 远端 `input-required` 必须把远端 `status.message.parts` 投影到 parent A2A task，让用户能看到远端要求补充的信息；下一轮用户输入按 parent task metadata 路由回同一个 remote task/context。

一句话定位：

```text
给本地 OpenJiuwen agent 注入远端 A2A agent-as-tool 能力；
用 control + parent A2A Task metadata 保证状态闭环；
用 access.a2a outbound adapter 承载真实 A2A client。
```

---

## 1. 背景与根因

当前 `agent-runtime` 已经具备 A2A ingress、OpenJiuwen handler、engine dispatch、task control 和 output registry 等能力。但它的远端互调能力仍缺一个闭环：

```text
本地 OpenJiuwen LLM 决定调用远端 agent
  -> 远端 agent 应作为本地 tool 出现
  -> runtime 需要把 tool_call 转成 A2A outbound
  -> control 必须感知远端调用状态
  -> 远端 input-required 必须能回到用户
  -> 用户下一轮输入必须回到同一个远端 task/context
```

根因一句话：**A2A 目前主要在入口和输出边界出现，尚未成为内部 task/event 事实源；远端 agent-as-tool 需要同时打通发现、注入、调用、状态、输出和 resume 分流。**

现有代码事实包括：

| 事实 | 当前含义 |
| --- | --- |
| `A2aJsonRpcHandler` | A2A JSON-RPC ingress 当前仍通过 access submission 进入 runtime。 |
| `A2aOutputRegistry` / `OutputChannelRegistry` | 当前 caller-visible 输出仍有自定义 registry/channel。 |
| `TaskControlService` / `Task` | 当前 task control 仍持有自定义 task 事实。 |
| `QueueManager` / `InternalEventQueue` | 当前 queue 仍是 runtime 内部调度/事件机制。 |
| `OpenJiuwenAgentRuntimeHandler` | OpenJiuwen handler 基类存在，但远端 tool injection hook 尚未实现。 |
| `EngineDispatcher` | engine 通过 `TaskControlClient` 把执行结果交给 control，control 是状态权威。 |

Strongest interpretation：目标不是再加一条旁路 A2A client，而是让远端 A2A 调用成为 runtime 正式 lifecycle 的一部分；否则 input-required、cancel、timeout、tasks/get 和 streaming 都会出现双事实源。

---

## 2. Scope Statement

### 范围内

- `agent-runtime` 内部 A2A `TaskStore/EventQueue` 作为任务与事件事实源的目标设计。
- 远端 runtime endpoint 配置与 AgentCard 拉取。
- `AgentCard -> RemoteAgentDescriptor -> RemoteAgentToolSpec -> OpenJiuwen ToolCard/Tool` 转换链路。
- OpenJiuwen agent 实例到 runtime handler adapter/factory。
- `RemoteAgentInvocationService`、`RemoteInvocationControl`、`RemoteAgentOutboundPort` 的边界。
- blocking / background 两种远端调用模式。
- 远端 `input-required` 到 parent task status message 的投影与下一轮 resume 分流。
- 目标文件目录树与实现切片。

### 范围外

- 不修改 OpenJiuwen 框架源码。
- 不在 engine/openjiuwen/control 中暴露 A2A JSON-RPC client 或 SDK client 类型。
- 不把远端 terminal result 直接写成 parent final output。
- 不在第一版做多框架工具注入；第一版只适配 OpenJiuwen。
- 不把 route grant、tenant 授权、生产级服务发现作为本提案主内容；这些可由后续 L1/L2 proposal 细化。

---

## 3. 评审结论

### 3.1 A2A 打穿是前提，不是附加优化

本设计要求 runtime 内部任务事实源从自定义 `Task` / output registry 收敛到 A2A 原生对象：

```text
A2A JSON-RPC ingress
  -> A2aTaskControlService
  -> A2A TaskStore + EventQueue
  -> engine execution
  -> A2A TaskStatusUpdateEvent / TaskArtifactUpdateEvent
  -> tasks/get / streaming egress
```

control 仍是状态权威，但写入目标变为 A2A `TaskStore/EventQueue`。`QueueManager` 如果保留，只能作为 engine command 调度机制，不能再承担 task 事实或 caller-visible event 事实。

### 3.2 远端 agent 发现与工具注入

远端 runtime 暴露：

```text
GET /.well-known/agent-card.json
```

本地配置只描述远端 endpoint 与调用策略：

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

转换链路：

```text
A2aRemoteAgentCardFetcher
  -> RemoteAgentCatalog
  -> RemoteAgentToolProvider
  -> OpenJiuwenRemoteToolAdapter
  -> OpenJiuwenRemoteToolInstaller
  -> BaseAgent AbilityManager + Runner.resourceMgr() + OpenJiuwenRemoteAgentInterruptRail
```

v1 采用“一远端 runtime 一个 tool”，不把远端多个 skill 拆成多个本地 tool。注入结果是 `ToolCard + 占位 Tool + OpenJiuwenRemoteAgentInterruptRail`：ToolCard 让 LLM 可选择，placeholder tool 让 resource manager 可解析，rail 是 ReActAgent v1 的真实远端调用入口。这样先保证状态与 resume 闭环稳定，再考虑细粒度 skill 暴露。

OpenJiuwen 注入要求：

```text
同一个 BaseAgent/ReActAgent 实例：
  installRuntimeTools(agent, context)
  -> Runner.runAgent(agent, input, stableOpenJiuwenSession, null)
```

如果 handler 拿不到 `BaseAgent` 实例，v1 不支持自动注入。业务方应把已有 OpenJiuwen agent 实例交给 runtime 提供的 `OpenJiuwenAgentRuntimeHandlerFactory`，由 runtime 包装成 `AgentRuntimeHandler`。

OpenJiuwen 的连续执行由 OpenJiuwen 自己的 `sessionId` / `conversation_id`、`Checkpointer` 和 interaction 机制负责。runtime 只保证每次进入同一个 parent A2A task 时使用稳定的 OpenJiuwen session，推荐直接使用 parent task id。runtime 不保存 OpenJiuwen checkpoint blob，也不保存 `checkpointRef`。

### 3.3 调用链路

本地 LLM 选择远端 tool 后，ReActAgent v1 以 rail 为主桥接点：

```text
OpenJiuwen LLM tool_call
  -> OpenJiuwen AbilityManager resolves injected ToolCard/tool name
  -> OpenJiuwenRemoteAgentInterruptRail.beforeToolCall(...)
  -> rail builds RemoteAgentInvocationIntent
  -> RemoteAgentInvocationService.invoke(intent)
  -> RemoteInvocationControl.open(...)
  -> RemoteAgentOutboundPort.invoke(...) / submit(...)
  -> access.a2a.A2aRemoteAgentOutboundAdapter
  -> A2A client / HTTP JSON-RPC client
  -> remote /a2a
  -> rail returns interrupt(...) or reject(...) synthetic tool result
```

关键边界：

- `OpenJiuwenRemoteAgentInterruptRail` 是 ReActAgent v1 的主桥接组件；`OpenJiuwenRemoteTool` 只作为 ToolCard/占位 tool 或非 ReActAgent fallback，不能与 rail 双重触发远端调用。
- `RemoteAgentInvocationService` 负责策略、超时、control lifecycle、tool result 包装。
- `RemoteInvocationControl` 负责 parent task metadata/event 写入。
- `RemoteAgentOutboundPort` 是中性端口，不是 A2A client。
- `A2aRemoteAgentOutboundAdapter` 是 access.a2a 实现，内部持有真正 A2A client。

### 3.4 Blocking / Background 调用模式

阻塞或不阻塞由本地 `RemoteInvocationPolicy` 决定，不由远端 card 单独决定。card capabilities / metadata 只能作为策略推断输入。

策略优先级：

1. 远端配置显式 `invocation.mode`。
2. 本地按 remote agent id / skill / tool name 的 policy override。
3. 从 card capabilities 或 metadata 推断。
4. runtime 默认 `BLOCKING + 短 timeout`。

| 维度 | `BLOCKING` | `BACKGROUND` |
| --- | --- | --- |
| outbound | `RemoteAgentOutboundPort.invoke(...)` | `RemoteAgentOutboundPort.submit(...)` |
| OpenJiuwen bridge | rail 在当前工具执行阶段等待 terminal 或 input-required；completed/error 通过 synthetic tool result 回给 OpenJiuwen，input-required 触发原生中断 | rail 提交远端 task 后进入 waiting/interrupted，后续由 watcher 和同一 session resume |
| control | 结果回来后 `markSucceeded/markInputRequired/markFailed/markTimeout` | `markSubmitted(...)` 后由 watcher 推进 |
| parent task | 通常保持 `WORKING`；远端 input-required 时转 `INPUT_REQUIRED` | remote task 未完成期间记录 waiting metadata；input-required 时转 `INPUT_REQUIRED` |
| timeout | `blocking-timeout` | `background-timeout` |

`AUTO` 只能在 `DefaultRemoteAgentInvocationService` 内解析一次，解析后固化为 `BLOCKING` 或 `BACKGROUND`。不要把 `AUTO` 传到 `access.a2a`。

### 3.5 远端 input-required 的用户可见投影

远端 `TASK_STATE_INPUT_REQUIRED` 不只是状态，还携带用户需要看的 `status.message`。必须投影到 parent task：

```text
remote TASK_STATE_INPUT_REQUIRED(status.message + metadata)
  -> A2aRemoteResultMapper maps visible message + remote task/context
  -> RemoteInvocationControl.markInputRequired(...)
  -> parent Task.status = TASK_STATE_INPUT_REQUIRED
  -> parent Task.status.message = projected remote status.message
  -> parent Task.metadata.runtime.waitingTarget = REMOTE_AGENT
  -> EventQueue writes TaskStatusUpdateEvent(INPUT_REQUIRED, message)
```

投影规则：

| 远端内容 | parent 投影 |
| --- | --- |
| `remoteTask.status.message.role` | 保留为 agent/assistant 侧消息；为空时兜底为 agent/assistant。 |
| `remoteTask.status.message.parts` | 原样保留可展示 parts；text 必须支持，file/data 按 A2A part 类型透传或降级为 metadata 引用。 |
| `remoteTask.status.message.metadata` | 合并到 parent status message metadata，并增加 remote agent/task 标识。 |
| remote 协议路由 metadata | 只保存必要字段到 parent `Task.metadata.runtime.*`，不直接展示。 |
| 远端无 message | 生成兜底 text message，例如“远端 agent 需要补充输入”。 |

streaming 用户通过 `TaskStatusUpdateEvent` 立即看到；`tasks/get` 用户通过 parent task status 看到。不能只写 metadata、日志或内部索引。

OpenJiuwen 中断必须走其原生机制。远端 tool name 注册到 `OpenJiuwenRemoteAgentInterruptRail`，由 rail 在 before-tool-call 阶段完成远端调用和中断决策；这样中断发生在 OpenJiuwen `railedExecuteSingleToolCall(...)` 外层能识别的位置，不会被普通 tool invoke 异常包装成 tool error。

```java
protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
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
```

OpenJiuwen `BaseInterruptRail` 会把 `InterruptResult` 转成 `ToolInterruptException`；`AbilityManager` 会把该异常收集为 interrupted tool execution；`ReActAgent` 会提交 `result_type=interrupt` / `InteractionOutput`；OpenJiuwen checkpointer 按同一个 `sessionId` / `conversation_id` 保存现场。远端 completed/failed/timeout 则通过 `RejectResult` / `_skip_tool` 注入 synthetic tool result。rail 不保存状态，也不替代 OpenJiuwen checkpointer。

因此，自定义 rail 只返回 `interrupt(InterruptRequest)`，不要手写 `throw new ToolInterruptException(...)`。异常由 `BaseInterruptRail.applyDecision(...)` 统一抛出，才能保证 OpenJiuwen 的 `_skip_tool`、synthetic tool message、interrupt collection、checkpoint/resume 路径一致。直接在占位 `Tool.invoke(...)` 或自定义 rail 里抛异常，容易被普通 tool error 分支包装，破坏原生 interrupt 语义。

### 3.6 下一轮用户输入分流

用户下一轮输入仍进入本地 A2A ingress。control 读取 parent task：

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

| parent state | waiting target | 行为 |
| --- | --- | --- |
| `TASK_STATE_INPUT_REQUIRED` | `REMOTE_AGENT` | 输入发给同一个 remote task/context。 |
| `TASK_STATE_INPUT_REQUIRED` | `LOCAL_AGENT` 或空 | 恢复本地 engine。 |
| `TASK_STATE_WORKING` | 任意 | 拒绝新输入或返回仍在工作。 |
| final state | 任意 | 拒绝 resume。 |

远端 completed 后，结果作为 tool result 恢复 parent OpenJiuwen agent。parent final answer 才写 parent completed。

恢复 OpenJiuwen 时，engine.openjiuwen 把远端 completed 的结果转换成 OpenJiuwen `InteractiveInput`：

```java
InteractiveInput input = new InteractiveInput();
input.update(toolCallId, remoteToolResultJson);
```

然后用同一个 `sessionId` / `conversation_id` 再次进入 OpenJiuwen。runtime/control 不知道 OpenJiuwen checkpoint 的内部结构，只负责把远端结果交给 engine.openjiuwen，并维护 parent A2A task 的状态闭环。

---

## 4. 目标组件形态

### 4.1 新增文件目录树

> 说明：以下目录树是基于当前 `agent-runtime` 五层扁平包结构的目标实现切片。等 A2A task/event 真正打穿后，部分文件可能会被合并、改名或移入新的 A2A facade 包；目录树用于指导第一轮实现，不作为永久物理结构承诺。

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

### 4.2 现有文件修改方向

| 文件 | 修改方向 |
| --- | --- |
| `A2aJsonRpcHandler.java` | inbound 直接进入 A2A task control facade。 |
| `A2aTaskMapper.java` | 从 `TaskStore/EventQueue` 构造 A2A task 查询结果。 |
| `A2aOutputRegistry.java` | 从 caller-visible 主链路移除；`tasks/get` 和 streaming 以 A2A `TaskStore/EventQueue` 为准。 |
| `DefaultNotificationPort.java` / `EngineOutputSink.java` | 改为调用 `A2aTaskEventPublisher`。 |
| `RuntimeWiringConfiguration.java` | 装配 A2A task/event、remote invocation、OpenJiuwen installer。 |
| `TaskControlService.java` | 被 `A2aTaskControlService` 替代或收敛为兼容 facade，不再持有自定义 `Task` 事实源。 |
| `TaskQueueRegistry.java` | 移出任务事实源路径；如保留，只做 parent task 到 engine command 的临时调度索引。 |
| `QueueManager.java` / `InternalEventQueue.java` | 只保留 engine command 调度能力，不作为 caller-visible event queue 或 task 状态来源。 |
| `InternalEngineCommandGateway.java` | 从 A2A task control 生成 engine command；不从 A2A EventQueue 反推任务事实。 |
| `EngineAutoConfiguration.java` | 装配新的 task control、remote invocation 和可选 engine command 调度队列。 |
| `OpenJiuwenAgentRuntimeHandler.java` | 增加 remote tool installer hook 或模板化 adapter 执行入口。 |

---

## 5. 实现切片

### Phase 1 — A2A Task/EventQueue 底座

目标：先消除双事实源。

1. 装配 A2A SDK `TaskStore` / `EventQueue`。
2. 新增 `A2aTaskControlService`、`A2aTaskEventPublisher`、metadata keys/router。
3. 改造 ingress、tasks/get、streaming 输出，使其从 A2A task/event 读写。
4. engine output 通过 control 写 A2A events。

### Phase 2 — Remote A2A Tool Blocking Call

目标：完成最小远端 agent-as-tool 闭环。

1. 远端 runtime 静态配置与 AgentCard 拉取。
2. `RemoteAgentCatalog` / `RemoteAgentToolProvider`。
3. `OpenJiuwenRemoteToolAdapter` / installer / interrupt rail / handler factory，并固定 ReActAgent v1 的 rail-first 调用路径，避免占位 tool 重复调用远端。
4. `RemoteAgentInvocationService` + `RemoteInvocationControl`。
5. `A2aRemoteAgentOutboundAdapter.invoke(...)`。
6. 覆盖 blocking 下的 terminal success、failure、timeout、input-required；input-required 必须写 parent task status message 并触发 OpenJiuwen 原生 interrupt。

### Phase 3 — Background + Remote Resume

目标：让远端长任务和多轮交互稳定运行。

1. `submit(...)`、remote task reference、watcher。
2. `markSubmitted(...)`；watcher 收到 input-required 时复用 Phase 2 的 `markInputRequired(...)` 投影规则。
3. A2A ingress resume 分流。
4. `resumeRemoteInput(...)`，结果仍映射为 `RemoteAgentResult`。
5. 远端 completed 后恢复 parent agent。
6. parent cancel 到 remote cancel 的传播。

---

## 6. 验证策略

必须覆盖：

- A2A ingress 创建的 task 存在于 SDK `TaskStore`。
- `tasks/get` 返回来自 `TaskStore/EventQueue` 的 A2A task。
- engine output 写入 `TaskStatusUpdateEvent` / `TaskArtifactUpdateEvent`。
- OpenJiuwen remote tool/rail bridge 只调用 `RemoteAgentInvocationService`，不直接调用 A2A client，且不会由 rail 与占位 tool 双重触发远端调用。
- `RemoteAgentOutboundPort` 可以用 fake 实现测试 engine/control；真实 A2A client 只在 `A2aRemoteAgentOutboundAdapter`。
- remote tool call 会写 parent task metadata。
- `TASK_STATE_INPUT_REQUIRED + runtime.waitingTarget=REMOTE_AGENT` 会路由到 `resumeRemoteInput(...)`。
- remote input-required 的 `status.message.parts` 会投影到 parent `TaskStatusUpdateEvent` 和 parent task status。
- remote terminal success 只作为 tool result，不直接写 parent completed。
- parent agent final answer 才写 `TASK_STATE_COMPLETED`。
- control 不依赖 access.a2a JSON-RPC wrapper。
- engine.openjiuwen 不依赖 A2A server task/event 类型。

---

## 7. 评审问题

1. 是否接受 A2A `TaskStore/EventQueue` 成为 `agent-runtime` 内部任务与 caller-visible 事件事实源？
2. 是否接受第一版只支持 OpenJiuwen agent 实例注入，其他框架延后？
3. 是否接受“一远端 runtime 一个 tool”的 v1 暴露方式，而不是按远端 skills 拆多个 tool？
4. 是否接受 blocking/background 由本地 `RemoteInvocationPolicy` 决定，AgentCard 只作为推断输入？
5. 是否接受 `RemoteAgentOutboundPort` 是中性端口，真正 A2A client 只放在 `access.a2a` adapter 内部？
6. 是否接受 ReActAgent v1 采用 `OpenJiuwenRemoteAgentInterruptRail` 作为主桥接点，`OpenJiuwenRemoteTool` 只做 ToolCard/占位 tool 或 fallback？
7. 是否接受目录树为第一轮目标切片，A2A 打穿后允许刷新物理目录？

---

## 8. 最终建议

建议批准该 proposal 作为 `architecture/docs/L2/agent-runtime/REMOTE_A2A_TOOL_INVOCATION_DESIGN.md` 的评审入口，并按 Phase 1 -> Phase 2 -> Phase 3 实施。

不要先做“远端 A2A client 旁路调用”。如果绕过 A2A task/event 打穿，短期可能更快，但会立刻产生三类长期缺陷：

- parent task 与 remote task 状态双事实源；
- remote input-required 无法可靠投影给用户并恢复同一个 remote task；
- cancel、timeout、tasks/get、streaming 输出需要多套并行逻辑。

因此，第一刀应落在 A2A task/event 底座，而不是 OpenJiuwen tool adapter。
